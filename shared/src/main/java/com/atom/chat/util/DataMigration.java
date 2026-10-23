package com.atom.chat.util;

import com.atom.chat.AtomChat;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** Verified migration: current files win; every retired source has a backup outside config. */
public final class DataMigration {
    private static boolean warning;
    private static final java.util.Set<Path> REJECTED = new java.util.HashSet<>();
    private DataMigration() { }
    public static synchronized void noteWarning() { warning = true; }
    public static synchronized boolean takeWarning() { boolean result = warning; warning = false; return result; }

    public static void migrate(Path source, Path target, Path backups) {
        migrate(source, target, backups, backups.getParent());
    }

    /** Explicit personal root keeps image-slot protection independent of the backup layout. */
    public static void migrate(Path source, Path target, Path backups, Path root) {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        Path from = source.toAbsolutePath().normalize(), to = target.toAbsolutePath().normalize();
        if (from.equals(to)) return;
        Path backup = backups.resolve(UUID.randomUUID().toString());
        try {
            if (to.startsWith(from) || backup.toAbsolutePath().normalize().startsWith(from)) {
                throw new IOException("Migration destination is inside its source: " + source);
            }
            noLinks(source);
            noLinks(target);
            noLinks(backups);
            boolean archivedTarget = to.startsWith(backups.toAbsolutePath().normalize());
            if (!Files.isDirectory(source)) {
                if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Refusing non-regular legacy data: " + source);
                moveFile(source, target, backup.resolve(source.getFileName()), root, archivedTarget);
                return;
            }
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    noLinks(dir);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new IOException("Refusing non-regular legacy data: " + file);
                    Path rel = source.relativize(file);
                    moveFile(file, target.resolve(rel), backup.resolve(rel), root, archivedTarget);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                    if (error != null) throw error;
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Migrated AtomChat data {} -> {}; backups at {}"), source, target, backup);
        } catch (IOException | SecurityException e) {
            noteWarning();
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "AtomChat data migration incomplete at {}; remaining originals are retained, check before sharing config"), source, e);
        }
    }

    private static void moveFile(Path source, Path target, Path backup, Path root, boolean archivedTarget) throws IOException {
        // Validate destination ancestry before writing, including the backup path.
        noLinks(target);
        noLinks(backup);
        Path verifiedCopy = backup;
        if (archivedTarget && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            // Relocating an existing backup needs one verified copy, never a backup of that backup.
            copyVerified(source, target);
            verifiedCopy = target;
        } else if (archivedTarget && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                && Files.mismatch(source, target) == -1) verifiedCopy = target;
        else copyVerified(source, backup);
        if (imageSlotOccupied(target, root)) {
            noteWarning();
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Current AtomChat image slot retained; legacy image archived at {}"), backup);
        } else if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) copyVerified(source, target);
        else if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Destination is not a regular file: " + target);
        else if (Files.mismatch(source, target) != -1) {
            noteWarning();
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "AtomChat migration conflict at {}; current file retained, legacy copy preserved at {}"), target, backup);
        }
        // Check again immediately before removal: a changed source is retained.
        if (Files.mismatch(source, verifiedCopy) != -1) throw new IOException("Legacy data changed during migration: " + source);
        Files.delete(source);
    }

    private static boolean imageSlotOccupied(Path target, Path root) throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        Path base = root.toAbsolutePath().normalize();
        Path client = base.resolve("client");
        String slot = parent.equals(client.resolve("banner")) ? "banner"
                : parent.equals(client.resolve("avatar")) ? "avatar"
                : parent.equals(client.resolve("wallpaper")) ? "wallpaper" : null;
        if (slot == null || !slotImage(target, slot) || !Files.isDirectory(parent)) return false;
        try (var files = Files.list(parent)) {
            return files.anyMatch(p -> !p.equals(target.toAbsolutePath().normalize())
                    && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && slotImage(p, slot));
        }
    }
    private static boolean slotImage(Path path, String slot) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.startsWith(slot) && (name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".webp"));
    }

    /** Runtime stores must use the same boundary as migration and personal JSON. */
    public static boolean allowPersonalPath(Path path) {
        if (path == null) return false;
        try { noLinks(path); return true; }
        catch (IOException | SecurityException e) {
            noteWarning();
            synchronized (REJECTED) {
                if (REJECTED.add(path.toAbsolutePath().normalize())) {
                    AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "AtomChat personal path rejected: {}"), path, e);
                }
            }
            return false;
        }
    }

    /** Copies without replacing anything; read-back comparison must pass before retirement. */
    public static void copyVerified(Path source, Path target) throws IOException {
        noLinks(source);
        noLinks(target);
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), "migration-", ".tmp");
        try {
            Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
            if (Files.mismatch(source, staged) != -1) throw new IOException("Migration verification failed: " + target);
            Files.move(staged, target);
        } finally { Files.deleteIfExists(staged); }
    }

    public static void noLinks(Path path) throws IOException {
        for (Path p = path.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) continue;
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attrs.isSymbolicLink() || attrs.isOther()) throw new IOException("Refusing linked path: " + p);
        }
    }

    /** Reuses a verified backup for identical config bytes, even if the original is locked. */
    public static void backupConfig(Path source, Path directory) throws IOException {
        noLinks(source);
        java.security.MessageDigest digest;
        try { digest = java.security.MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
        try (var input = new java.security.DigestInputStream(Files.newInputStream(source), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        Path backup = directory.resolve("client-config-" + java.util.HexFormat.of().formatHex(digest.digest()) + ".json");
        noLinks(backup);
        if (!Files.exists(backup)) copyVerified(source, backup);
        if (Files.mismatch(source, backup) != -1) throw new IOException("Config backup verification failed: " + backup);
    }
}
