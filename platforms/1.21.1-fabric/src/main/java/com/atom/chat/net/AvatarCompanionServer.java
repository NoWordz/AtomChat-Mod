package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.util.CacheDirs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the avatar companion. Stateless beyond the avatar files
 * themselves: uploads land in {@code <gameDir>/atomchat-data/avatars/<uuid>.png}
 * and requests are answered from disk. Loaded lazily in the common entrypoint
 * so a dedicated server and the integrated server of a double-open client both
 * run it.
 *
 * <p>Hardening: uploads must carry the sender's own uuid (no spoofing), are
 * size-capped ({@value #MAX_BYTES} bytes) and PNG-magic-checked, and rate
 * limited per player. Requests are throttled lightly to stop spam loops.
 */
public final class AvatarCompanionServer {
    private AvatarCompanionServer() {
    }

    static final int MAX_BYTES = AvatarPayloads.MAX_AVATAR_BYTES;
    private static final long UPLOAD_INTERVAL_MS = 60_000L;
    private static final long REQUEST_INTERVAL_MS = 1_000L;
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final Map<UUID, Long> lastUploadMs = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastRequestMs = new ConcurrentHashMap<>();
    private static volatile Path storageDir;

    /** Registers the server receivers; safe to call on both logical sides. */
    public static void register() {
        CacheDirs.migrateFromOldConfigPaths();
        storageDir = CacheDirs.avatarDataDir();
        ServerPlayNetworking.registerGlobalReceiver(AvatarPayloads.AvatarUploadPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    // Anti-spoof: a client may only push its own avatar.
                    if (payload.uuid() == null || !payload.uuid().equals(player.getUuid())) {
                        return;
                    }
                    handleUpload(player, payload.uuid(), payload.data());
                });
        ServerPlayNetworking.registerGlobalReceiver(AvatarPayloads.AvatarRequestPayload.ID,
                (payload, context) -> {
                    if (payload.uuid() == null) {
                        return;
                    }
                    handleRequest(context.player(), payload.uuid());
                });
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "AtomChat avatar companion registered (server side)"));
    }

    private static void handleUpload(ServerPlayerEntity player, UUID uuid, byte[] data) {
        if (!com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastUploadMs.get(uuid);
        if (last != null && now - last < UPLOAD_INTERVAL_MS) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "Rejected avatar upload from {}: rate limited"), player.getName().getString());
            return;
        }
        if (data == null || data.length == 0 || data.length > MAX_BYTES || !isPng(data)) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "Rejected avatar upload from {}: invalid payload"), player.getName().getString());
            return;
        }
        lastUploadMs.put(uuid, now);
        // File IO belongs off the netty thread.
        player.getServer().execute(() -> {
            try {
                Path dir = storageDir;
                Files.createDirectories(dir);
                Path target = dir.resolve(uuid + ".png");
                Path tmp = dir.resolve(uuid + ".png.tmp");
                Files.write(tmp, data);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "Stored avatar for {} ({} bytes)"), player.getName().getString(), data.length);
                broadcastChanged(player, uuid);
            } catch (IOException e) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "Failed to store avatar for {}"), uuid, e);
            }
        });
    }

    /**
     * Announces the new avatar to every client so their cached (or
     * negative-cached) copy is dropped and re-requested this session — a cache
     * wipe otherwise waits for the next join. Fabric's {@code send} silently
     * skips receivers that did not register the channel.
     */
    private static void broadcastChanged(ServerPlayerEntity uploader, UUID uuid) {
        var server = uploader.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new AvatarPayloads.AvatarChangedPayload(uuid));
        }
    }

    private static void handleRequest(ServerPlayerEntity player, UUID uuid) {
        if (!com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) {
            // Master switch off: answer the normal "no custom avatar" shape so
            // the client silently keeps showing skins.
            ServerPlayNetworking.send(player, new AvatarPayloads.AvatarDataPayload(uuid, new byte[0]));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastRequestMs.put(uuid, now);
        if (last != null && now - last < REQUEST_INTERVAL_MS) {
            return;
        }
        player.getServer().execute(() -> {
            byte[] data = new byte[0];
            try {
                Path file = storageDir.resolve(uuid + ".png");
                if (Files.exists(file)) {
                    byte[] bytes = Files.readAllBytes(file);
                    if (bytes.length > 0 && bytes.length <= MAX_BYTES) {
                        data = bytes;
                    }
                }
            } catch (IOException e) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "Failed to read avatar for {}"), uuid, e);
            }
            ServerPlayNetworking.send(player, new AvatarPayloads.AvatarDataPayload(uuid, data));
        });
    }

    /** Drops the per-player rate-limit bookkeeping (called when a player leaves). */
    static void forgetPlayer(UUID player) {
        if (player == null) {
            return;
        }
        lastUploadMs.remove(player);
        lastRequestMs.remove(player);
    }

    static boolean isPng(byte[] data) {
        if (!com.atom.chat.image.PngDimensions.within(data, 32_000_000L)) return false;
        if (data.length < PNG_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (data[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
