package com.atom.chat.banner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * The custom profile banner: at most one image, living directly in
 * {@code <config>/atomchat/} as {@code banner.png} (or {@code banner.jpg} /
 * {@code .jpeg} / {@code .webp} when the picked source carries that
 * extension). Setting a new one copies it in (the source is kept) and drops
 * any previous {@code banner.*}; clearing deletes the file, which falls the
 * profile banner back to its accent gradient — delete image = off.
 *
 * <p>Deliberately pure {@code java.nio} like {@code WallpaperStore}: no Skia
 * and no Minecraft imports, so the set/clear/scan rules are testable offline.
 * Decoding into a Skia image is the separate {@link BannerImage} concern, and
 * {@code AtomChatConfig.customBannerEnabled} is the master switch on top of
 * the file's existence.
 */
public final class BannerStore {
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"};
    private static final String BASE_NAME = "banner";

    private static Path dir;
    private static Path current;

    private BannerStore() {
    }

    /** Must be called once from client init, before any UI reads the banner. */
    public static void init(Path bannerDir) {
        dir = bannerDir;
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

    /** Re-scans the banner dir for a {@code banner.*} file. */
    public static void refresh() {
        current = null;
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.startsWith(BASE_NAME) && isSupportedName(n);
                    })
                    .findFirst()
                    .ifPresent(p -> current = p);
        } catch (IOException e) {
            current = null;
        }
    }

    /** The active banner file, or null when the gradient fallback is in use. */
    public static Path current() {
        return current;
    }

    public static boolean isSet() {
        return current != null;
    }

    /**
     * Stores raw PNG bytes (the cropper's output) as {@code banner.png},
     * replacing any previous banner. Writes a {@code .tmp} file and atomically
     * moves it into place first; the old-format cleanup runs only after the
     * move has landed, so a failed write leaves the previous banner intact.
     */
    public static boolean setPng(byte[] bytes) {
        if (dir == null || bytes == null || bytes.length == 0) {
            return false;
        }
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(BASE_NAME + ".png.tmp");
            Files.write(tmp, bytes);
            Path png = dir.resolve(BASE_NAME + ".png");
            Files.move(tmp, png,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            removeExisting(png);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Deletes the banner; the profile falls back to its accent gradient
     * ("delete image = off").
     */
    public static boolean clear() {
        boolean removed = removeExisting(null);
        refresh();
        return removed;
    }

    /**
     * Deletes every {@code banner.*} except {@code keep} (the freshly moved
     * png passes itself so the old-format sweep cannot eat the new file).
     */
    private static boolean removeExisting(Path keep) {
        boolean removed = false;
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                if (p.equals(keep)) {
                    continue;
                }
                String n = p.getFileName().toString().toLowerCase();
                if (n.startsWith(BASE_NAME) && isSupportedName(n)) {
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
