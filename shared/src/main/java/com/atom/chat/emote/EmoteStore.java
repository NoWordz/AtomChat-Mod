package com.atom.chat.emote;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Emotes in two sources: the player's own folder at
 * {@code <configDir>/atomchat/emotes/} (png/jpg/jpeg/gif; gif plays in the grid)
 * and, since 0.2.9, the emotes of a downloaded server pack.
 *
 * <p>The local half keeps the original rules — adding a file copies it in (the
 * source is kept), duplicates overwrite by name, sorted by name, capped at a
 * configurable maximum (0 keeps every sticker). The server half is a read-only
 * mirror of whatever the joined server offered, capped at {@link #SERVER_MAX}:
 * {@link #remove} refuses anything outside the local folder, so a server's
 * emote can never be deleted from the panel.
 *
 * <p>This class is deliberately pure {@code java.nio}: no Skia and no
 * Minecraft/Fabric imports, so the scan/sort/cap/add/remove logic is
 * unit-testable offline. Decoding files into Skia {@link io.github.humbleui.skija.Image}s
 * is the separate {@link EmoteImageCache} concern.
 */
public final class EmoteStore {
    /** A local cap at or below this keeps every sticker the player adds. */
    public static final int UNLIMITED = 0;
    /** Emotes a downloaded server pack may contribute; read-only. */
    public static final int SERVER_MAX = 32;

    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif"};

    private final Path dir;
    /**
     * Read at every scan, not captured once: a hand-edited config takes effect
     * the next time the panel rescans, even though the store outlives a
     * config reload.
     */
    private final IntSupplier localMax;
    private List<File> cached = Collections.emptyList();
    private Path serverDir;
    private List<File> serverCached = Collections.emptyList();

    public EmoteStore(Path dir) {
        this(dir, () -> UNLIMITED);
    }

    public EmoteStore(Path dir, IntSupplier localMax) {
        this.dir = dir;
        this.localMax = localMax == null ? () -> UNLIMITED : localMax;
        refresh();
    }

    public Path dir() {
        return dir;
    }

    /**
     * Points the read-only half at a downloaded server pack, or null for none.
     * The next scan picks it up.
     */
    public void setServerDir(Path dir) {
        this.serverDir = dir;
        refresh();
    }

    public Path serverDir() {
        return serverDir;
    }

    /** Server emotes, sorted by name and capped at {@value #SERVER_MAX}. Never null. */
    public List<File> serverList() {
        return serverCached;
    }

    public int serverCount() {
        return serverCached.size();
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

    /** Re-scans both folders, sorts by name and applies the two caps. */
    public void refresh() {
        int cap = localMax.getAsInt();
        cached = Collections.unmodifiableList(
                scan(dir, cap > UNLIMITED ? cap : Integer.MAX_VALUE));
        serverCached = serverDir == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(scan(serverDir, SERVER_MAX));
    }

    private static List<File> scan(Path source, int cap) {
        List<File> files = new ArrayList<>();
        if (source == null) {
            return files;
        }
        File[] listed = source.toFile().listFiles();
        if (listed == null) {
            return files;
        }
        for (File f : listed) {
            if (f.isFile() && isSupportedName(f.getName())) {
                files.add(f);
            }
        }
        files.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        while (files.size() > cap) {
            files.remove(files.size() - 1);
        }
        return files;
    }

    /** Current emotes, sorted by name. Never null. */
    public List<File> list() {
        return cached;
    }

    public int count() {
        return cached.size();
    }

    public boolean isFull() {
        int cap = localMax.getAsInt();
        return cap > UNLIMITED && cached.size() >= cap;
    }

    /** Copies {@code source} into the emote dir. False when full or invalid. */
    public boolean add(File source) {
        if (source == null || !source.isFile() || !isSupportedName(source.getName())) {
            return false;
        }
        if (isFull()) {
            return false;
        }
        try {
            Files.createDirectories(dir);
            Path dest = dir.resolve(source.getName());
            // Re-picking a file that already lives in the emote dir is a no-op.
            if (source.toPath().toAbsolutePath().normalize()
                    .equals(dest.toAbsolutePath().normalize())) {
                refresh();
                return true;
            }
            Files.copy(source.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Deletes one of the player's own emote files. Refuses anything outside the
     * local folder — which is what keeps a server's emotes read-only.
     */
    public boolean remove(File emote) {
        if (emote == null) {
            return false;
        }
        Path emotePath = emote.toPath().toAbsolutePath().normalize();
        if (!emotePath.startsWith(dir.toAbsolutePath().normalize())) {
            return false;
        }
        try {
            boolean deleted = emote.delete();
            if (deleted) {
                refresh();
            }
            return deleted;
        } catch (Exception e) {
            return false;
        }
    }
}
