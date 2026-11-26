package com.atom.chat.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The pack this client currently has for the joined world, as the panel sees it.
 *
 * <p>Nothing is held in memory that is not also on disk: {@link #refresh} re-reads
 * the stored manifest and lists the emote folder, and every accessor is a
 * snapshot taken at that moment. The sync client refreshes on join, refreshes
 * again after installing a download, and clears on disconnect.
 *
 * <p>Everything here is read-only by construction - no accessor hands out a
 * writable emote path - so a server's emotes can never be edited or deleted
 * from the panel, however the user pokes at it.
 */
public final class ServerPackStore {
    /** Emotes taken from one server pack; mirrors the server's default cap. */
    public static final int MAX_SERVER_EMOTES = 32;

    private static final ServerPackStore EMPTY = new ServerPackStore(null, null, null);

    private static volatile ServerPackStore current = EMPTY;

    private final ServerPack pack;
    private final Path dir;
    private final List<Path> emotes;
    private final byte[] publicAvatar;

    private ServerPackStore(ServerPack pack, Path dir, byte[] verifiedAvatar) {
        this.publicAvatar = pack != null && PublicChatAvatar.isPublished(verifiedAvatar) ? verifiedAvatar.clone() : null;
        this.pack = pack;
        this.dir = dir;
        this.emotes = pack == null || dir == null ? List.of() : listEmotes(dir);
    }

    private ServerPackStore(ServerPackStore previous) {
        this.pack = previous.pack;
        this.dir = previous.dir;
        this.emotes = previous.emotes;
        this.publicAvatar = null;
    }

    public static void hidePublicAvatar() {
        var previous = current;
        if (previous.publicAvatar != null) current = new ServerPackStore(previous);
    }

    /** Re-reads the pack installed for {@code key}; a missing/broken one reads as absent. */
    public static void refresh(Path packsRoot, String key) {
        refresh(packsRoot, key, null);
    }

    public static void refresh(Path packsRoot, String key, ServerPack verifiedManifest) {
        if (packsRoot == null || !PackKeys.isKey(key)) {
            current = EMPTY;
            return;
        }
        Path dir = packsRoot.resolve(key);
        var installed = PackManifestFile.read(dir);
        byte[] verifiedAvatar = installed != null && verifiedManifest != null
                && installed.packHash().equals(verifiedManifest.packHash()) ? verifiedManifest.icon() : null;
        current = new ServerPackStore(installed, dir, verifiedAvatar);
    }

    /** Forgets the current pack (disconnect, or a server that turned packs off). */
    public static void clear() {
        current = EMPTY;
    }

    public static ServerPackStore current() {
        return current;
    }

    /** True when a verified pack is installed for the joined world. */
    public boolean isPresent() {
        return pack != null;
    }

    /** Server name to show in the panel; empty when the server offers nothing. */
    public String serverName() {
        return pack == null ? "" : pack.serverName();
    }

    public List<String> phrases() {
        return pack == null ? List.of() : pack.phrases();
    }

    public byte[] icon() {
        return pack == null ? null : pack.icon();
    }

    public byte[] publicAvatar() {
        return publicAvatar == null ? null : publicAvatar.clone();
    }

    public String packHash() {
        return pack == null ? "" : pack.packHash();
    }

    /** Folder holding this pack's emotes, or null when there is no pack. */
    public Path emotesDir() {
        // No pack means no folder: callers point the panel at this and must be
        // able to tell "nothing installed" from "an empty pack".
        return pack == null || dir == null ? null : dir.resolve(PackManifestFile.EMOTES_DIR);
    }

    /** Emote files the server offers, sorted by name, capped at {@value #MAX_SERVER_EMOTES}. */
    public List<Path> emotes() {
        return emotes;
    }

    public int emoteCount() {
        return emotes.size();
    }

    /**
     * True when this pack actually offers something to show. A server with packs
     * enabled but an empty emote folder and no phrases is treated as "not
     * distributing", so the panels stay free of server-only rows.
     */
    public boolean hasContent() {
        return pack != null && (!emotes.isEmpty() || !phrases().isEmpty());
    }

    private static List<Path> listEmotes(Path dir) {
        Path emotes = dir.resolve(PackManifestFile.EMOTES_DIR);
        if (!Files.isDirectory(emotes)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(emotes)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(path) && ServerPack.isSafeName(path.getFileName().toString())) {
                    files.add(path);
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        if (files.size() > MAX_SERVER_EMOTES) {
            files = new ArrayList<>(files.subList(0, MAX_SERVER_EMOTES));
        }
        return List.copyOf(files);
    }
}
