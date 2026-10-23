package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.util.CacheDirs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Server side of the avatar companion. Stateless beyond the avatar files
 * themselves: uploads land in {@code <gameDir>/atomchat/server/avatars/<uuid>.png}
 * and requests are answered from disk. Registered through the common channel so
 * a dedicated server and the integrated server of a double-open client both run
 * it.
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

    /** C2S upload handler; runs on the server thread. */
    public static void handleUpload(AvatarPayloads.AvatarUploadPayload payload, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ctx.get().enqueueWork(() -> {
                // Anti-spoof: a client may only push its own avatar.
                if (payload.uuid() == null || !payload.uuid().equals(player.getUUID())) {
                    return;
                }
                handleUpload(player, payload.uuid(), payload.data());
            });
        }
        ctx.get().setPacketHandled(true);
    }

    /** C2S request handler; runs on the server thread. */
    public static void handleRequest(AvatarPayloads.AvatarRequestPayload payload, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ctx.get().enqueueWork(() -> {
                if (payload.uuid() == null) {
                    return;
                }
                handleRequest(player, payload.uuid());
            });
        }
        ctx.get().setPacketHandled(true);
    }

    private static void handleUpload(ServerPlayer player, UUID uuid, byte[] data) {
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
        try {
            Path dir = storageDir();
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
    }

    /**
     * Announces the new avatar to every companion-capable client so their
     * cached (or negative-cached) copy is dropped and re-requested this
     * session — a cache wipe otherwise waits for the next join.
     */
    private static void broadcastChanged(ServerPlayer uploader, UUID uuid) {
        var server = uploader.getServer();
        if (server == null) {
            return;
        }
        AvatarPayloads.AvatarChangedPayload payload = new AvatarPayloads.AvatarChangedPayload(uuid);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                if (p.connection.connection != null
                        && AvatarPayloads.CHANNEL.isRemotePresent(p.connection.connection)) {
                    AvatarPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), payload);
                }
            } catch (Throwable t) {
                // A player that cannot accept the notification keeps the old copy.
            }
        }
    }

    private static void handleRequest(ServerPlayer player, UUID uuid) {
        if (!com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) {
            // Master switch off: answer the normal "no custom avatar" shape so
            // the client silently keeps showing skins.
            AvatarPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new AvatarPayloads.AvatarDataPayload(uuid, new byte[0]));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastRequestMs.put(uuid, now);
        if (last != null && now - last < REQUEST_INTERVAL_MS) {
            return;
        }
        byte[] data = new byte[0];
        try {
            Path file = storageDir().resolve(uuid + ".png");
            if (Files.exists(file)) {
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length > 0 && bytes.length <= MAX_BYTES) {
                    data = bytes;
                }
            }
        } catch (IOException e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "Failed to read avatar for {}"), uuid, e);
        }
        AvatarPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new AvatarPayloads.AvatarDataPayload(uuid, data));
    }

    /** Drops the per-player rate-limit bookkeeping (called when a player leaves). */
    static void forgetPlayer(UUID player) {
        if (player == null) {
            return;
        }
        lastUploadMs.remove(player);
        lastRequestMs.remove(player);
    }

    private static Path storageDir() {
        Path dir = storageDir;
        if (dir == null) {
            CacheDirs.migrateFromOldConfigPaths();
            dir = CacheDirs.avatarDataDir();
            storageDir = dir;
        }
        return dir;
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
