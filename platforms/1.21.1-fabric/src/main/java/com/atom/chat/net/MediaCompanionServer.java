package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.util.CacheDirs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the media companion (0.2.7): hosts chat images / GIFs in
 * {@code <gameDir>/atomchat-data/media/} and streams them back over the game
 * connection. No HTTP port, no public exposure — bytes only ever reach a
 * connected player.
 *
 * <p>Hardening: uploads are content-addressed (sha256), size capped and
 * magic-sniffed (only png/jpg/gif/webp/bmp); the client name is ignored so it
 * can never influence a path. Uploads are rate limited per player and the
 * store is trimmed by total size / age - see {@link CompanionMaintenance}.
 * {@code hostingEnabled} is the master switch shared with the avatar
 * companion.
 */
public final class MediaCompanionServer {
    private MediaCompanionServer() {
    }

    private static final class Pending {
        final MediaUploadBuffer buffer;
        final long startedAtMs;

        Pending(MediaUploadBuffer buffer, long startedAtMs) {
            this.buffer = buffer;
            this.startedAtMs = startedAtMs;
        }
    }

    private static final int MAX_PENDING_PER_PLAYER = 4;

    private static final Map<UUID, Long> lastUploadMs = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Pending>> uploads = new ConcurrentHashMap<>();
    private static volatile Path mediaDir;

    /** Registers the server receivers; safe to call on both logical sides. */
    public static void register() {
        mediaDir = CacheDirs.mediaDataDir();
        ServerPlayNetworking.registerGlobalReceiver(MediaPayloads.StateRequest.ID, (payload, context) -> {
            // Pick up hand-edits to the server config on the next join.
            AtomChatServerConfig.reload();
            sendState(context.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(MediaPayloads.UploadStart.ID,
                (payload, context) -> onStart(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(MediaPayloads.UploadChunk.ID,
                (payload, context) -> onChunk(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(MediaPayloads.UploadFinish.ID,
                (payload, context) -> onFinish(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(MediaPayloads.Fetch.ID,
                (payload, context) -> onFetch(context.player(), payload));
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("media", "AtomChat media companion registered (server side)"));
    }

    private static void sendState(ServerPlayerEntity player) {
        AtomChatServerConfig config = AtomChatServerConfig.get();
        ServerPlayNetworking.send(player, new MediaPayloads.MediaState(config.hostingEnabled, config.maxFileBytes()));
    }

    private static void onStart(ServerPlayerEntity player, MediaPayloads.UploadStart payload) {
        AtomChatServerConfig config = AtomChatServerConfig.get();
        if (!config.hostingEnabled) {
            result(player, payload.uploadId(), "", "disabled");
            return;
        }
        if (payload.uploadId() == null) {
            return;
        }
        Map<UUID, Pending> existing = uploads.get(player.getUuid());
        if (existing != null && existing.size() >= MAX_PENDING_PER_PLAYER) {
            result(player, payload.uploadId(), "", "busy");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastUploadMs.get(player.getUuid());
        if (last != null && now - last < Math.max(0, config.uploadCooldownMs)) {
            result(player, payload.uploadId(), "", "rate_limited");
            return;
        }
        if (payload.totalBytes() <= 0 || payload.totalBytes() > config.maxFileBytes()) {
            result(player, payload.uploadId(), "", "too_large");
            return;
        }
        lastUploadMs.put(player.getUuid(), now);
        uploads.computeIfAbsent(player.getUuid(), key -> new ConcurrentHashMap<>())
                .put(payload.uploadId(), new Pending(
                        new MediaUploadBuffer(payload.totalBytes(), config.maxFileBytes()), now));
    }

    private static void onChunk(ServerPlayerEntity player, MediaPayloads.UploadChunk payload) {
        Pending pending = pending(player.getUuid(), payload.uploadId());
        if (pending == null) {
            return;
        }
        if (!pending.buffer.write(payload.offset(), payload.data())) {
            remove(player.getUuid(), payload.uploadId());
            result(player, payload.uploadId(), "", "invalid");
        }
    }

    private static void onFinish(ServerPlayerEntity player, MediaPayloads.UploadFinish payload) {
        Pending pending = remove(player.getUuid(), payload.uploadId());
        if (pending == null) {
            return;
        }
        if (!pending.buffer.isComplete()) {
            result(player, payload.uploadId(), "", "incomplete");
            return;
        }
        byte[] content = pending.buffer.bytes();
        var server = player.getServer();
        if (server == null) {
            return;
        }
        // File IO belongs off the network thread.
        server.execute(() -> store(player, payload.uploadId(), content));
    }

    private static void store(ServerPlayerEntity player, UUID uploadId, byte[] content) {
        String ext = MediaIds.extensionOf(content);
        if (ext == null) {
            result(player, uploadId, "", "unsupported");
            return;
        }
        String mediaId = MediaIds.idFor(content, ext);
        try {
            Path dir = mediaDir;
            Files.createDirectories(dir);
            Path file = dir.resolve(mediaId);
            if (!Files.exists(file)) {
                Path tmp = dir.resolve(mediaId + ".tmp");
                Files.write(tmp, content);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            CompanionMaintenance.pruneMedia();
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("media", "Stored hosted media {} ({} bytes)"), mediaId, content.length);
            result(player, uploadId, mediaId, "");
        } catch (IOException e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("media", "Failed to store hosted media"), e);
            result(player, uploadId, "", "io");
        }
    }

    private static void onFetch(ServerPlayerEntity player, MediaPayloads.Fetch payload) {
        String mediaId = payload.mediaId();
        AtomChatServerConfig config = AtomChatServerConfig.get();
        if (!config.hostingEnabled || !MediaIds.isSafeId(mediaId)) {
            missing(player, mediaId);
            return;
        }
        var server = player.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> stream(player, mediaId));
    }

    private static void stream(ServerPlayerEntity player, String mediaId) {
        Path dir = mediaDir;
        try {
            Path file = dir == null ? null : dir.resolve(mediaId);
            if (file == null || !Files.isRegularFile(file)) file = ProfileCompanionServer.media(player, mediaId);
            if (file == null || !Files.isRegularFile(file)) {
                missing(player, mediaId);
                return;
            }
            int maxBytes = AtomChatServerConfig.get().maxFileBytes();
            if (Files.size(file) > maxBytes) {
                missing(player, mediaId);
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            int total = bytes.length;
            if (total == 0) {
                missing(player, mediaId);
                return;
            }
            for (int offset = 0; offset < total; offset += MediaIds.CHUNK_BYTES) {
                int len = Math.min(MediaIds.CHUNK_BYTES, total - offset);
                ServerPlayNetworking.send(player, new MediaPayloads.Data(
                        mediaId, offset, total, Arrays.copyOfRange(bytes, offset, offset + len)));
            }
        } catch (IOException e) {
            missing(player, mediaId);
        }
    }

    /**
     * Drops everything remembered about one player: their pending upload
     * buffers (a disconnect mid-transfer would otherwise pin those bytes until
     * the server stops) and their upload cooldown entry.
     */
    static void forgetPlayer(UUID player) {
        if (player == null) {
            return;
        }
        uploads.remove(player);
        lastUploadMs.remove(player);
    }

    /**
     * Drops pending uploads that were started but never finished. A client that
     * goes away mid-transfer leaves one buffer behind, and one that simply
     * stops sending never touches the logout path at all.
     */
    static void sweepStaleUploads(long now) {
        for (Map.Entry<UUID, Map<UUID, Pending>> player : uploads.entrySet()) {
            Map<UUID, Pending> pending = player.getValue();
            pending.entrySet().removeIf(entry -> now - entry.getValue().startedAtMs
                    > CompanionMaintenance.STALE_UPLOAD_MS);
            if (pending.isEmpty()) {
                uploads.remove(player.getKey(), pending);
            }
        }
    }

    private static Pending pending(UUID player, UUID uploadId) {
        if (player == null || uploadId == null) {
            return null;
        }
        Map<UUID, Pending> map = uploads.get(player);
        return map == null ? null : map.get(uploadId);
    }

    private static Pending remove(UUID player, UUID uploadId) {
        if (player == null || uploadId == null) {
            return null;
        }
        Map<UUID, Pending> map = uploads.get(player);
        if (map == null) {
            return null;
        }
        Pending pending = map.remove(uploadId);
        if (map.isEmpty()) {
            uploads.remove(player, map);
        }
        return pending;
    }

    private static void result(ServerPlayerEntity player, UUID uploadId, String mediaId, String error) {
        if (uploadId == null) {
            return;
        }
        ServerPlayNetworking.send(player, new MediaPayloads.UploadResult(uploadId, mediaId, error));
    }

    private static void missing(ServerPlayerEntity player, String mediaId) {
        ServerPlayNetworking.send(player, new MediaPayloads.Missing(mediaId == null ? "" : mediaId));
    }
}
