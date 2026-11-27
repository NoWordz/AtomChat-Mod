package com.atom.chat.wallpaper;

import com.atom.chat.AtomChat;
import com.atom.chat.image.ImageBounds;
import com.atom.chat.util.DataMigration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** The active static panel wallpaper and its optional built-in generator marker. */
public final class WallpaperStore {
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"};
    private static final String BASE_NAME = "wallpaper";
    private static final String MARKER = "selected-preset.txt";

    private record Active(Path path, String identity, String presetId, boolean retired, long revision) {}
    private enum Outcome { FAILED, COMMITTED, REPLACED }

    private static Path dir;
    private static volatile Active active = new Active(null, null, "", false, 0);

    private WallpaperStore() {}

    public static long revision() { return active.revision(); }
    public static String presetId() { return active.presetId(); }
    /** Decode provenance is independent of an active preset's resize/regeneration identity. */
    public static boolean isGeneratedImage() {
        Active current = active;
        return current.retired() || !current.presetId().isEmpty();
    }

    /** Must be called from client init, before any UI reads the wallpaper. */
    public static synchronized void init(Path wallpaperDir) {
        WallpaperPresets.cancelPending();
        dir = wallpaperDir;
        scan(true);
    }

    public static boolean isSupportedName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    /** Re-scans the wallpaper directory and refreshes the cached content identity. */
    public static synchronized void refresh() {
        scan(false);
    }

    private static void scan(boolean readMarker) {
        Path path = null;
        String identity = null;
        String presetId = readMarker ? "" : active.presetId();
        boolean retired = !readMarker && active.retired();
        if (DataMigration.allowPersonalPath(dir) && Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                path = files.filter(WallpaperStore::isWallpaperName)
                        .filter(Files::isRegularFile)
                        .filter(DataMigration::allowPersonalPath)
                        .sorted().findFirst().orElse(null);
                if (path != null) identity = validatedIdentity(path);
            } catch (IOException ignored) {
                // A legacy image may need to be cleared even if it cannot be read.
            }
            if (readMarker) {
                Path marker = dir.resolve(MARKER);
                if (DataMigration.allowPersonalPath(marker) && Files.isRegularFile(marker)) {
                    try {
                        String stored = Files.readString(marker).trim();
                        boolean available = Arrays.stream(WallpaperPresets.presets()).anyMatch(p -> p.id().equals(stored));
                        if (path != null && available) presetId = stored;
                        // A retired source keeps its decode size but cannot regenerate.
                        else if (path != null && "graphite".equals(stored)) retired = true;
                        else Files.deleteIfExists(marker);
                    } catch (IOException ignored) { }
                }
            }
        }
        publish(path, identity, presetId, retired);
    }

    private static void publish(Path path, String identity, String presetId, boolean retired) {
        active = new Active(path, identity, presetId, retired, active.revision() + 1);
    }

    /** The active wallpaper file, or null when the default background is in use. */
    public static Path current() { return active.path(); }

    public static boolean isSet() { return active.path() != null; }

    /** Matches the cached content identity, or the absence of a static file for a null identity. */
    public static boolean matchesCurrent(String identity) {
        Active current = active;
        return identity == null ? current.path() == null : identity.equals(current.identity());
    }

    public static boolean set(Path source) {
        return source != null && replaceWithConfig(source, () -> true);
    }

    public static boolean setPng(byte[] bytes) {
        return replacePngWithConfig(bytes, () -> true);
    }

    static synchronized boolean setPresetPng(String id, byte[] bytes, int generation) {
        return setPresetPng(id, bytes, generation, () -> true);
    }

    static synchronized boolean setPresetPng(String id, byte[] bytes, int generation, BooleanSupplier commitConfig) {
        return WallpaperPresets.isCurrentRequest(generation) && ImageBounds.rejection(bytes) == null
                && replace(bytes, ".png", id, commitConfig) != Outcome.FAILED;
    }

    /** Deletes the static wallpaper and reports whether an image was removed. */
    public static synchronized boolean clear() {
        return replace(null, null, null, () -> true) == Outcome.REPLACED;
    }

    /**
     * Stages a validated static copy or clears the static file when source is null.
     * A false or throwing config commit restores the previous files and marker.
     * The callback only commits configuration, and its caller owns any live config rollback.
     */
    public static synchronized boolean replaceWithConfig(Path source, BooleanSupplier commitConfig) {
        if (source == null) return replace(null, null, null, commitConfig) != Outcome.FAILED;
        if (source.getFileName() == null || !isSupportedName(source.getFileName().toString())) return false;
        String name = source.getFileName().toString();
        String extension = name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        try {
            return replace(readValidated(source), extension, null, commitConfig) != Outcome.FAILED;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /** Transaction variant for the cropper's encoded PNG. */
    public static synchronized boolean replacePngWithConfig(byte[] bytes, BooleanSupplier commitConfig) {
        return ImageBounds.rejection(bytes) == null
                && replace(bytes, ".png", null, commitConfig) != Outcome.FAILED;
    }

    private static Outcome replace(byte[] bytes, String extension, String generatedId, BooleanSupplier commitConfig) {
        Path stage = null;
        Path newImage = null;
        Path newMarker = null;
        Map<Path, Path> displaced = new LinkedHashMap<>();
        boolean committed = false;
        try {
            Objects.requireNonNull(commitConfig);
            if (dir == null || Files.notExists(dir, LinkOption.NOFOLLOW_LINKS)) {
                if (bytes == null && active.path() == null) {
                    return commitWithoutFiles(commitConfig);
                }
                if (dir == null || bytes == null) return Outcome.FAILED;
            }
            DataMigration.noLinks(dir);
            if (bytes != null) Files.createDirectories(dir);
            Path destination = bytes == null ? null : dir.resolve(BASE_NAME + extension);
            Path marker = dir.resolve(MARKER);
            var previous = new ArrayList<Path>();
            try (var files = Files.list(dir)) {
                for (Path file : files.filter(WallpaperStore::isWallpaperName).toList()) {
                    DataMigration.noLinks(file);
                    if (Files.isRegularFile(file)) previous.add(file);
                }
            }
            boolean hadWallpaper = !previous.isEmpty();
            if (bytes == null && !hadWallpaper && active.path() == null) {
                return commitWithoutFiles(commitConfig);
            }
            if (checkReplaceable(marker)) previous.add(marker);
            if (destination != null) checkReplaceable(destination);

            String identity = null;
            if (bytes != null || !previous.isEmpty()) {
                stage = Files.createTempDirectory(dir, ".wallpaper-");
                Path stagedImage = stage.resolve("image");
                Path stagedMarker = stage.resolve("marker");
                if (bytes != null) {
                    Files.write(stagedImage, bytes);
                    identity = digest(bytes);
                }
                if (generatedId != null) Files.writeString(stagedMarker, generatedId);
                for (Path file : previous) {
                    Path backup = stage.resolve("previous-" + displaced.size());
                    moveIntoPlace(file, backup);
                    displaced.put(file, backup);
                }
                if (bytes != null) {
                    moveIntoPlace(stagedImage, destination);
                    newImage = destination;
                }
                if (generatedId != null) {
                    moveIntoPlace(stagedMarker, marker);
                    newMarker = marker;
                }
            }

            if (!commitConfig.getAsBoolean()) return Outcome.FAILED;
            committed = true;
            if (generatedId == null) WallpaperPresets.cancelPending();
            publish(destination, identity, generatedId == null ? "" : generatedId, false);
            return hadWallpaper ? Outcome.REPLACED : Outcome.COMMITTED;
        } catch (IOException | RuntimeException failure) {
            return Outcome.FAILED;
        } finally {
            if (!committed && (newImage != null || newMarker != null || !displaced.isEmpty())) {
                boolean restored = restore(displaced, newImage, newMarker);
                if (!restored) stage = null; // Leave recovery copies in place.
                // A background decode may have observed the tentative file.
                Active before = active;
                publish(before.path(), before.identity(), before.presetId(), before.retired());
            }
            discardStage(stage);
        }
    }

    private static Outcome commitWithoutFiles(BooleanSupplier commitConfig) {
        if (!commitConfig.getAsBoolean()) return Outcome.FAILED;
        WallpaperPresets.cancelPending();
        return Outcome.COMMITTED;
    }

    private static boolean isWallpaperName(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith(BASE_NAME) && isSupportedName(name);
    }

    private static boolean checkReplaceable(Path file) throws IOException {
        DataMigration.noLinks(file);
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isRegularFile(file)) throw new IOException("Cannot replace wallpaper file: " + file);
        return true;
    }

    private static boolean restore(Map<Path, Path> displaced, Path image, Path marker) {
        boolean restored = true;
        for (Path installed : new Path[]{image, marker}) {
            if (installed == null || displaced.containsKey(installed)) continue;
            try {
                Files.deleteIfExists(installed);
            } catch (IOException failure) {
                restored = false;
                AtomChat.LOGGER.error("Could not remove tentative wallpaper file {}", installed, failure);
            }
        }
        for (var entry : displaced.entrySet()) {
            try {
                moveIntoPlace(entry.getValue(), entry.getKey());
            } catch (IOException failure) {
                restored = false;
                AtomChat.LOGGER.error("Could not restore wallpaper file {} from {}", entry.getKey(), entry.getValue(), failure);
            }
        }
        return restored;
    }

    private static void discardStage(Path stage) {
        if (stage == null) return;
        try (var files = Files.newDirectoryStream(stage)) {
            for (Path file : files) Files.deleteIfExists(file);
        } catch (IOException failure) {
            AtomChat.LOGGER.warn("Could not clean up wallpaper staging directory {}", stage, failure);
        }
        try {
            Files.deleteIfExists(stage);
        } catch (IOException failure) {
            AtomChat.LOGGER.warn("Could not remove wallpaper staging directory {}", stage, failure);
        }
    }

    /** Copies validated bytes to a new file and returns their content identity. */
    public static String copyValidated(Path source, Path target) throws IOException {
        byte[] bytes = readValidated(source);
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return digest(bytes);
    }

    public static String validatedIdentity(Path image) throws IOException {
        return digest(readValidated(image));
    }

    private static byte[] readValidated(Path image) throws IOException {
        if (!Files.isRegularFile(image)) throw new IOException("Not a wallpaper file: " + image);
        try (var input = Files.newInputStream(image)) {
            byte[] bytes = input.readNBytes(ImageBounds.MAX_BYTES + 1);
            if (ImageBounds.rejection(bytes) != null) throw new IOException("Invalid wallpaper image: " + image);
            return bytes;
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void moveIntoPlace(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
