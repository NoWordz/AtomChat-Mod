package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.pack.PackAssembler;
import com.atom.chat.pack.PackDiff;
import com.atom.chat.pack.PackKeys;
import com.atom.chat.pack.PackManifestFile;
import com.atom.chat.pack.ServerPack;
import com.atom.chat.pack.ServerPackStore;
import com.atom.chat.util.CacheDirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client side of the pack distribution (0.2.9).
 *
 * <p>On join the client asks for the manifest, hashes what it already has on disk
 * and asks only for the files that are missing or different - so deleting one
 * emote costs one file, and a second join costs nothing. The answer arrives in
 * 24 KiB chunks that are reassembled and verified against the manifest, and the
 * whole pack is swapped into place as one directory, so a half-finished sync is
 * never readable as a working pack.
 *
 * <p>Two hard limits protect us from a hostile server: more than
 * {@value #MAX_FILES} files or {@value #MAX_BYTES} bytes is refused outright.
 *
 * <p>This file is mapping- and loader-neutral on purpose: everything that differs
 * between the three targets goes through {@link Net} (sending) and {@link Host}
 * (this machine). Each platform unpacks its own payload into a
 * {@link PackMessage} before calling in here, so the logic exists once instead of
 * once per target - the three copies this replaced had already drifted apart.
 */
public final class PackSyncClient {
    /** Files a pack may contain before this client refuses it. */
    public static final int MAX_FILES = 200;
    /** Bytes a pack may total before this client refuses it. */
    public static final long MAX_BYTES = 16L * 1024L * 1024L;
    private static final long TIMEOUT_MS = 30_000L;
    private static final long HELLO_TIMEOUT_MS = 15_000L;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "atomchat-pack-sync");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * A stand-in message used to ask "do you know this channel?".
     *
     * <p>The capability probe is per payload kind, so it needs an instance of that
     * kind; no real ack exists at the two places that ask, and none is sent - only
     * its type is read.
     */
    private static final PackMessage.Ack ACK_PROBE = new PackMessage.Ack(true, "");

    private static String key;
    private static ServerPack expected;
    private static Set<String> wanted = Set.of();
    private static final Map<String, PackAssembler.FileBuffer> buffers = new LinkedHashMap<>();
    private static final Map<String, byte[]> ready = new LinkedHashMap<>();
    private static long deadline;
    private static boolean active;
    /** Last thing that happened, for the panel. */
    private static volatile String detail = "";

    private PackSyncClient() {
    }

    /** Join hook: ask this server what it offers, unless the player opted out. */
    public static void onJoin() {
        reset();
        if (!AtomChatConfig.get().serverPacksEnabled) {
            detail = "disabled";
            return;
        }
        if (!Net.canSendToServer(new PackMessage.Hello())) {
            // A server without AtomChat never sees this packet, and the panel
            // simply keeps showing the plain world name.
            detail = "no_server_mod";
            return;
        }
        key = worldKey();
        if (key == null) {
            detail = "no_world_key";
            return;
        }
        // Show whatever is already installed while the handshake runs.
        ServerPackStore.refresh(CacheDirs.packsRoot(), key);
        active = true;
        deadline = System.currentTimeMillis() + HELLO_TIMEOUT_MS;
        Net.sendToServer(new PackMessage.Hello());
    }

    /**
     * Re-runs the join handshake for the world we are already in.
     *
     * <p>Needed after a config save: phrases and the server name live in the
     * manifest rather than in files, so a saved change only reaches the panel
     * once a fresh manifest has been fetched.
     */
    public static void resync() {
        onJoin();
    }

    /** Disconnect hook: nothing carries over between worlds. */
    public static void onDisconnect() {
        reset();
    }

    private static void reset() {
        ServerPackStore.clear();
        key = null;
        expected = null;
        wanted = Set.of();
        buffers.clear();
        ready.clear();
        deadline = 0L;
        active = false;
    }

    /** Called every client tick: the only way a stalled sync ever ends. */
    public static void tick() {
        if (active && deadline > 0L && System.currentTimeMillis() > deadline) {
            abort("timeout");
        }
    }

    /** S2C: the manifest (or "this server does not offer a pack"). */
    public static void onManifest(PackMessage.Manifest manifest) {
        if (!active) {
            return;
        }
        if (!manifest.enabled()) {
            detail = "server_disabled";
            active = false;
            ServerPackStore.clear();
            return;
        }
        ServerPack expectedPack;
        try {
            expectedPack = PackMessage.toPack(manifest);
        } catch (RuntimeException e) {
            abort("bad_manifest");
            return;
        }
        if (expectedPack.files().size() > MAX_FILES || expectedPack.totalBytes() > MAX_BYTES) {
            abort("too_large");
            return;
        }
        expected = expectedPack;
        Path dir = CacheDirs.packDir(key);
        ServerPack stored = PackManifestFile.read(dir);
        IO.execute(() -> {
            Map<String, String> hashes = PackManifestFile.localHashes(dir);
            PackDiff.Plan plan = PackDiff.plan(expectedPack, hashes);
            Host.execute(() -> {
                if (!active) {
                    return;
                }
                if (stored != null && stored.packHash().equals(expectedPack.packHash())
                        && plan.isUpToDate()) {
                    detail = "up_to_date";
                    active = false;
                    ServerPackStore.refresh(CacheDirs.packsRoot(), key);
                    send(new PackMessage.Ack(true, "up_to_date"));
                } else {
                    request(plan);
                }
            });
        });
    }

    /** Asks for the diff, or installs straight away when there is nothing to fetch. */
    private static void request(PackDiff.Plan plan) {
        PackManifestFile.deleteStale(CacheDirs.packDir(key), plan.remove());
        if (plan.fetch().isEmpty()) {
            installExisting();
            return;
        }
        wanted = new LinkedHashSet<>();
        for (ServerPack.FileEntry entry : plan.fetch()) {
            wanted.add(entry.name());
        }
        buffers.clear();
        ready.clear();
        deadline = System.currentTimeMillis() + TIMEOUT_MS;
        detail = "fetching";
        Net.sendToServer(new PackMessage.Need(new ArrayList<>(wanted)));
    }

    /** Nothing to download: the files on disk are already right, so store the metadata. */
    private static void installExisting() {
        Path dir = CacheDirs.packDir(key);
        IO.execute(() -> {
            Map<String, byte[]> contents = new LinkedHashMap<>();
            for (ServerPack.FileEntry entry : expected.files()) {
                try {
                    contents.put(entry.name(), Files.readAllBytes(
                            dir.resolve(PackManifestFile.EMOTES_DIR).resolve(entry.name())));
                } catch (IOException e) {
                    Host.execute(() -> abort("read_failed"));
                    return;
                }
            }
            try {
                PackManifestFile.install(CacheDirs.packsRoot(), key, expected, contents);
            } catch (IOException e) {
                Host.execute(() -> abort("write_failed"));
                return;
            }
            Host.execute(() -> {
                detail = "up_to_date";
                active = false;
                ServerPackStore.refresh(CacheDirs.packsRoot(), key);
                send(new PackMessage.Ack(true, "metadata"));
            });
        });
    }

    /** S2C: one slice of one file. */
    public static void onChunk(PackMessage.Chunk chunk) {
        if (!active || expected == null) {
            return;
        }
        String name = chunk.name();
        if (!wanted.contains(name) || chunk.totalBytes() <= 0) {
            abort("unexpected_chunk");
            return;
        }
        ServerPack.FileEntry entry = findByManifest(name);
        if (entry == null || entry.size() != chunk.totalBytes()) {
            abort("size_mismatch");
            return;
        }
        PackAssembler.FileBuffer buffer = buffers.computeIfAbsent(name,
                ignored -> new PackAssembler.FileBuffer(entry));
        if (!buffer.offer(chunk.offset(), chunk.data())) {
            abort("bad_chunk");
            return;
        }
        deadline = System.currentTimeMillis() + TIMEOUT_MS;
        if (!buffer.isComplete()) {
            return;
        }
        byte[] bytes = buffer.verified();
        if (bytes == null) {
            abort("hash_mismatch");
            return;
        }
        ready.put(name, bytes);
        buffers.remove(name);
    }

    /**
     * S2C: the server sent everything that was asked for.
     *
     * <p>The count the server reports is deliberately ignored: what we accept is
     * our own tally of verified files, never the peer's word for it.
     */
    public static void onDone(int fileCount) {
        if (!active || expected == null) {
            return;
        }
        if (ready.size() != wanted.size()) {
            abort("incomplete");
            return;
        }
        Map<String, byte[]> downloaded = new LinkedHashMap<>(ready);
        ServerPack pack = expected;
        String installKey = key;
        Path dir = CacheDirs.packDir(key);
        String hash = pack.packHash().substring(0, 8);
        int count = downloaded.size();
        IO.execute(() -> {
            Map<String, byte[]> contents;
            try {
                // An incremental sync only fetched the files that differed, so the
                // ones we kept have to be folded back in before installing.
                contents = PackManifestFile.completeWithExisting(dir, pack, downloaded);
                PackManifestFile.install(CacheDirs.packsRoot(), installKey, pack, contents);
            } catch (IOException e) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Failed to store the server pack"), e);
                Host.execute(() -> abort("write_failed"));
                return;
            }
            Host.execute(() -> {
                detail = "synced";
                active = false;
                ServerPackStore.refresh(CacheDirs.packsRoot(), installKey);
                send(new PackMessage.Ack(true, count + " file(s) " + hash));
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Server pack {} synced ({} file(s), {} KB)"), hash, count,
                        pack.totalBytes() / 1024);
            });
        });
    }

    private static void abort(String reason) {
        AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Server pack sync failed: {}"), reason);
        detail = reason;
        active = false;
        send(new PackMessage.Ack(false, reason));
    }

    private static void send(PackMessage.Ack ack) {
        if (Net.canSendToServer(ACK_PROBE)) {
            Net.sendToServer(ack);
        }
    }

    private static ServerPack.FileEntry findByManifest(String name) {
        for (ServerPack.FileEntry entry : expected.files()) {
            if (entry.name().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    /** Last sync outcome, for the panel and the logs. */
    public static String detail() {
        return detail;
    }

    /** The pack this client currently has for the joined world, or null. */
    public static ServerPack installed() {
        return key == null ? null : PackManifestFile.read(CacheDirs.packDir(key));
    }

    /** Folder name of the joined world's pack, or null when not in a world. */
    public static String currentKey() {
        return key;
    }

    /** Address first (multiplayer), level name second (single player), null otherwise. */
    private static String worldKey() {
        String address = Host.serverAddress();
        if (address != null && !address.isBlank()) {
            return PackKeys.serverKey(address);
        }
        String level = Host.levelName();
        if (level != null) {
            return PackKeys.levelKey(level);
        }
        return null;
    }
}
