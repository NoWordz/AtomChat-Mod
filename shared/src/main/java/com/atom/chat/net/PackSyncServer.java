package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.pack.PackBuilder;
import com.atom.chat.pack.ServerEmoteMigration;
import com.atom.chat.pack.ServerPack;
import com.atom.chat.util.CacheDirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server side of the pack distribution (0.2.9): answers a client's Hello with the
 * manifest and streams whatever that client says it is missing.
 *
 * <p>The pack is built from the admin's own content - the emote folder, the
 * phrase list and {@code server-icon.png} plus the MOTD - and cached for
 * {@value #CACHE_MS} ms so a busy join minute does not hash the same files for
 * every player. Only one download per player runs at a time and each tick gives
 * every waiting player at most {@value #CHUNKS_PER_TICK} chunks.
 *
 * <p>This file is mapping- and loader-neutral on purpose: the server handle and
 * the player handles travel as opaque objects, and everything that differs
 * between the three targets goes through {@link Net} (sending) and {@link Host}
 * (player identity, MOTD, environment). Each platform unpacks its own payload
 * into a {@link PackMessage} before calling in here.
 */
public final class PackSyncServer {
    private static final int CHUNKS_PER_TICK = 4;
    private static final long CACHE_MS = 60_000L;

    private static final Map<UUID, Download> DOWNLOADS = new ConcurrentHashMap<>();
    /** The pack each player was shown last, so a rebuild cannot change what we send mid-sync. */
    private static final Map<UUID, ServerPack> OFFERED = new ConcurrentHashMap<>();
    private static volatile Cached cached;
    /** The legacy emote carry-over runs once per JVM, on the first pack build. */
    private static final AtomicBoolean LEGACY_CHECKED = new AtomicBoolean();

    private PackSyncServer() {
    }

    private record Cached(ServerPack pack, long builtAtMs) {
    }

    /** One player's remaining chunks for one request. */
    private static final class Download {
        final Queue<PackMessage.Chunk> chunks = new ArrayDeque<>();
        int files;

        void add(String name, byte[] bytes) {
            for (int offset = 0; offset < bytes.length; offset += MediaIds.CHUNK_BYTES) {
                int length = Math.min(MediaIds.CHUNK_BYTES, bytes.length - offset);
                chunks.add(new PackMessage.Chunk(name, offset, bytes.length,
                        Arrays.copyOfRange(bytes, offset, offset + length)));
            }
            files++;
        }
    }

    /** C2S: "send me your manifest" (runs on the server thread). */
    public static void onHello(Object player) {
        UUID id = Host.playerId(player);
        if (id == null) {
            return;
        }
        DOWNLOADS.remove(id);
        AtomChatServerConfig config = AtomChatServerConfig.get();
        if (!config.packEnabled) {
            // Answering beats staying silent: the client shows "this server does
            // not offer a pack" instead of waiting for a timeout.
            Net.sendToPlayer(player, PackMessage.disabledManifest());
            return;
        }
        ServerPack pack = pack(serverOf(player));
        OFFERED.put(id, pack);
        Net.sendToPlayer(player, PackMessage.manifestOf(pack));
    }

    /** C2S: "these files differ from mine" - only names the manifest listed. */
    public static void onNeed(Object player, List<String> names) {
        UUID id = Host.playerId(player);
        if (id == null) {
            return;
        }
        AtomChatServerConfig config = AtomChatServerConfig.get();
        // Send exactly the pack this player was shown: a rebuild between the
        // manifest and the download would otherwise ship bodies whose hashes
        // contradict the manifest the client verifies against.
        ServerPack pack = OFFERED.get(id);
        if (pack == null) {
            pack = pack(serverOf(player));
        }
        if (!config.packEnabled || names == null || names.isEmpty()) {
            DOWNLOADS.remove(id);
            Net.sendToPlayer(player, new PackMessage.Done(0));
            return;
        }
        Map<String, ServerPack.FileEntry> offered = new HashMap<>();
        for (ServerPack.FileEntry entry : pack.files()) {
            offered.put(entry.name(), entry);
        }
        Download download = new Download();
        Path dir = CacheDirs.serverEmotesDir();
        for (String name : names) {
            ServerPack.FileEntry entry = offered.get(name);
            if (entry == null) {
                continue;
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(dir.resolve(entry.name()));
            } catch (IOException e) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Server pack file {} vanished before it could be sent"), name);
                continue;
            }
            if (bytes.length != entry.size()
                    || !ServerPack.sha256Hex(bytes).equals(entry.sha256Hex())) {
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Server pack file {} changed on disk mid-sync; skipped"), name);
                continue;
            }
            download.add(entry.name(), bytes);
        }
        if (download.files == 0) {
            Net.sendToPlayer(player, new PackMessage.Done(0));
            return;
        }
        DOWNLOADS.put(id, download);
    }

    /** C2S: the client's verdict, so the outcome lands in the server log. */
    public static void onAck(Object player, PackMessage.Ack ack) {
        UUID id = Host.playerId(player);
        if (id == null) {
            return;
        }
        OFFERED.remove(id);
        Cached current = cached;
        String hash = current == null ? "-" : current.pack().packHash().substring(0, 8);
        String name = Host.playerName(player);
        if (ack.ok()) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Player {} verified the server pack {} ({})"), name, hash,
                    ack.detail().isEmpty() ? "up to date" : ack.detail());
        } else {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Player {} could not sync the server pack {}: {}"), name, hash,
                    ack.detail());
        }
    }

    /** Server tick (hooked from the common entrypoint): drains the queues. */
    public static void tick(Object server) {
        if (server == null || DOWNLOADS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Download>> iterator = DOWNLOADS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Download> entry = iterator.next();
            Object player = Host.playerById(server, entry.getKey());
            if (player == null) {
                iterator.remove();
                OFFERED.remove(entry.getKey());
                continue;
            }
            Download download = entry.getValue();
            int sent = 0;
            while (sent < CHUNKS_PER_TICK && !download.chunks.isEmpty()) {
                Net.sendToPlayer(player, download.chunks.poll());
                sent++;
            }
            if (download.chunks.isEmpty()) {
                Net.sendToPlayer(player, new PackMessage.Done(download.files));
                iterator.remove();
            }
        }
    }

    /** The pack this server is currently offering (for the config screen). */
    public static ServerPack peek(Object server) {
        return pack(server);
    }

    /** Drops the cached pack so the next request re-scans the emote folder. */
    public static void invalidate() {
        cached = null;
    }

    /**
     * The server handle a player belongs to.
     *
     * <p>The port exposes no such lookup because every call site already has the
     * player and its server comes with it - the platform adapter resolves this
     * when it unpacks the payload.
     */
    private static Object serverOf(Object player) {
        return player == null ? null : Host.serverOf(player);
    }

    private static ServerPack pack(Object server) {
        Cached current = cached;
        long now = System.currentTimeMillis();
        if (current != null && now - current.builtAtMs() < CACHE_MS) {
            return current.pack();
        }
        carryOverLegacyServerEmotes();
        AtomChatServerConfig config = AtomChatServerConfig.get();
        ServerPack pack = PackBuilder.build(CacheDirs.serverEmotesDir(), config.phrases, config.packName,
                Host.motd(server), readIcon(), config.packMaxFiles, config.packMaxBytes());
        cached = new Cached(pack, now);
        return pack;
    }

    /** Only an explicit legacy server-emotes folder qualifies; never infer from personal stickers. */
    private static void carryOverLegacyServerEmotes() {
        if (!LEGACY_CHECKED.compareAndSet(false, true)) {
            return;
        }
        ServerEmoteMigration.migrate(com.atom.chat.platform.Platform.configDir().resolve("atomchat/server-emotes"), CacheDirs.serverEmotesDir(),
                Host.dedicatedServer());
    }

    private static byte[] readIcon() {
        Path icon = CacheDirs.gameDir().resolve("server-icon.png");
        try {
            return Files.isRegularFile(icon) ? Files.readAllBytes(icon) : null;
        } catch (IOException e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Failed to read server-icon.png"), e);
            return null;
        }
    }
}
