package com.atom.chat.wallpaper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * The custom panel wallpaper: at most one image, living in
 * {@code <gameDir>/atomchat/client/wallpaper/}. Setting a new one copies it in (the
 * source is kept) and drops any previous {@code wallpaper.*}; clearing removes
 * it, which falls the panel back to the blur/solid background.
 *
 * <p>Deliberately pure {@code java.nio} like {@code EmoteStore}: no Skia and
 * no Minecraft imports, so the set/clear/scan rules are testable offline.
 * Decoding into a Skia image is the separate {@link WallpaperImage} concern.</p>
 */
public final class WallpaperStore {
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"};
    private static final String BASE_NAME = "wallpaper";

    private static Path dir;
    private static Path current;

    private WallpaperStore() {
    }

    /** Must be called once from client init, before any UI reads the wallpaper. */
    public static void init(Path wallpaperDir) {
        dir = wallpaperDir;
        refresh();
    }

    public static boolean isSupportedName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** Re-scans the wallpaper dir for a {@code wallpaper.*} file. */
    public static void refresh() {
        current = null;
        if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir) || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(com.atom.chat.util.DataMigration::allowPersonalPath)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.startsWith(BASE_NAME) && isSupportedName(n);
                    })
                    .sorted()
                    .findFirst()
                    .ifPresent(p -> current = p);
        } catch (IOException e) {
            current = null;
        }
    }

    /** The active wallpaper file, or null when the default background is in use. */
    public static Path current() {
        return current;
    }

    public static boolean isSet() {
        return current != null;
    }

    /** Copies {@code source} in as the wallpaper, replacing any previous one.
     *
     * <p>Staged: the new file is copied to a temporary name and atomically
     * moved over {@code wallpaper.<ext>} first; the old formats are deleted
     * only after the move landed. A failed copy therefore leaves the previous
     * image in place instead of the old {@code removeExisting() -> copy}
     * order's blank window.</p> */
    public static boolean set(Path source) {
        if (dir == null || source == null || !Files.isRegularFile(source)) {
            return false;
        }
        String name = source.getFileName() != null ? source.getFileName().toString() : "";
        if (!isSupportedName(name)) {
            return false;
        }
        String ext = name.substring(name.lastIndexOf('.')).toLowerCase();
        try {
            if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir)) return false;
            Files.createDirectories(dir);
            Path dest = dir.resolve(BASE_NAME + ext);
            if (!com.atom.chat.util.DataMigration.allowPersonalPath(dest)) return false;
            if (source.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                refresh();
                return true;
            }
            Path tmp = dir.resolve(BASE_NAME + ".tmp");
            if (!com.atom.chat.util.DataMigration.allowPersonalPath(tmp)) return false;
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
            moveIntoPlace(tmp, dest);
            removeExistingExcept(dest);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Stores raw PNG bytes (the cropper's output) as {@code wallpaper.png},
     * replacing any previous wallpaper. Same staged order as {@link #set}.
     */
    public static boolean setPng(byte[] bytes) {
        if (dir == null || bytes == null || bytes.length == 0) {
            return false;
        }
        try {
            if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir)) return false;
            Files.createDirectories(dir);
            Path dest = dir.resolve(BASE_NAME + ".png");
            Path tmp = dir.resolve(BASE_NAME + ".tmp");
            if (!com.atom.chat.util.DataMigration.allowPersonalPath(tmp)) return false;
            Files.write(tmp, bytes);
            moveIntoPlace(tmp, dest);
            removeExistingExcept(dest);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void moveIntoPlace(Path tmp, Path dest) throws IOException {
        try {
            Files.move(tmp, dest,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes the wallpaper; the panel falls back to blur/solid. */
    public static boolean clear() {
        if (dir == null) {
            return false;
        }
        boolean removed = removeExisting();
        refresh();
        return removed;
    }

    private static boolean removeExisting() {
        return removeExistingExcept(null);
    }

    /** Removes every {@code wallpaper.*} except {@code keep} (the just-moved file). */
    private static boolean removeExistingExcept(Path keep) {
        boolean removed = false;
        if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir) || !Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(Files::isRegularFile)
                    .filter(com.atom.chat.util.DataMigration::allowPersonalPath).toList()) {
                String n = p.getFileName().toString().toLowerCase();
                if (n.startsWith(BASE_NAME) && isSupportedName(n)
                        && (keep == null || !p.toAbsolutePath().normalize()
                        .equals(keep.toAbsolutePath().normalize()))) {
                    try {
                        Files.deleteIfExists(p);
                        removed = true;
                    } catch (IOException ignored) {
                        // Leave the file; refresh() will still report it.
                    }
                }
            }
        } catch (IOException ignored) {
            // Treated as "nothing to remove".
        }
        return removed;
    }
}
