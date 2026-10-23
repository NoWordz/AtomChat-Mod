package com.atom.chat.avatar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * The local custom avatar: at most one image, living in
 * {@code <gameDir>/atomchat/client/avatar/}. Setting a new one copies it in (the
 * source is kept) and drops any previous {@code avatar.*}; clearing removes
 * it, which falls the profile back to the real skin.
 *
 * <p>Deliberately pure {@code java.nio} like {@code EmoteStore}: no Skia and
 * no Minecraft imports, so the set/clear/scan rules are testable offline.
 * Decoding into a Skija image is the separate {@link AvatarImage} concern.
 *
 * <p>Scope (grilled 2026-09-05): this avatar is local-only for now — it shows
 * on the profile page and on own bubbles. Cross-client sync is planned as a
 * server-companion feature; when the companion is absent the mod degrades
 * silently to the skin, per the e33chat philosophy.
 */
public final class AvatarStore {
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"};
    private static final String BASE_NAME = "avatar";

    private final Path dir;
    private Path current;

    public AvatarStore(Path avatarDir) {
        this.dir = avatarDir;
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

    /** Re-scans the avatar dir for an {@code avatar.*} file. */
    public final void refresh() {
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

    /** The active avatar file, or null when the skin fallback is in use. */
    public Path current() {
        return current;
    }

    public boolean isSet() {
        return current != null;
    }

    /** Copies {@code source} in as the avatar, replacing any previous one. */
    public boolean set(Path source) {
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
            removeExisting();
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Stores raw PNG bytes (the cropper's output) as {@code avatar.png},
     * replacing any previous avatar.
     */
    public boolean setPng(byte[] bytes) {
        if (dir == null || bytes == null || bytes.length == 0) {
            return false;
        }
        try {
            if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir)) return false;
            Files.createDirectories(dir);
            if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir.resolve(BASE_NAME + ".png"))) return false;
            removeExisting();
            Files.write(dir.resolve(BASE_NAME + ".png"), bytes);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Deletes the avatar; the profile falls back to the real skin. */
    public boolean clear() {
        boolean removed = removeExisting();
        refresh();
        return removed;
    }

    private boolean removeExisting() {
        boolean removed = false;
        if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir) || !Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(Files::isRegularFile)
                    .filter(com.atom.chat.util.DataMigration::allowPersonalPath).toList()) {
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
