package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import io.github.humbleui.skija.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client side of the avatar companion: lazy loading with a memory cache.
 *
 * <p>Flow: rendering asks {@link #currentAvatar(UUID)} for a player with no
 * cached avatar → a request payload goes out (deduplicated) → the companion
 * answers with the PNG bytes (or an empty array = no avatar) → the image is
 * decoded on a daemon thread and cached by uuid.
 *
 * <p>Cache policy (grilled 0.1.10): the cache lives for one server session
 * and is wiped on join, so a changed avatar shows up after re-entering and
 * the server keeps no per-viewer state. No disk layer — with a wipe-on-join
 * policy it could never serve a hit.
 *
 * <p>Companion presence: the client checks the negotiated network channels
 * before sending anything. When the server did not register AtomChat's avatar
 * channels it is marked companion-less and all traffic stops (silent
 * degradation to skins).
 */
public final class AvatarCompanionClient {
    private AvatarCompanionClient() {
    }

    private static final long PROBE_TIMEOUT_MS = 3_000L;
    private static final long NO_AVATAR_TTL_MS = 30_000L;
    /** Cooldown before a lost request is re-sent (see {@link #currentAvatar}). */
    private static final long RETRY_BACKOFF_MS = 5_000L;

    private enum Presence { UNKNOWN, YES, NO }

    private static volatile Presence presence = Presence.UNKNOWN;
    private static final AtomicInteger GENERATION = new AtomicInteger();

    /** Decoded images keyed by uuid; Skija finalises evicted native memory. */
    private static final Map<UUID, Image> decoded = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> requestedAt = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> noAvatarUntil = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> decoding = new ConcurrentHashMap<>();

    /**
     * Client init hook. NeoForge registers the S2C receiver centrally in
     * {@link AvatarPayloads#register}; this only keeps the same call shape as
     * the Fabric client entrypoint.
     */
    public static void init() {
    }

    /** Reset on join: avatars are re-fetched lazily after the join. */
    public static void onJoin() {
        presence = Presence.UNKNOWN;
        requestedAt.clear();
        noAvatarUntil.clear();
        decoding.clear();
        int generation = GENERATION.incrementAndGet();
        for (Map.Entry<UUID, Image> entry : decoded.entrySet()) {
            decoded.remove(entry.getKey());
            entry.getValue().close();
        }
        if (generation != GENERATION.get()) {
            // Another join raced us; the later wipe wins.
            return;
        }
    }

    /**
     * Returns the decoded companion avatar for the uuid, or null while it is
     * absent/loading. Calling this may start a lazy request.
     */
    public static Image currentAvatar(UUID uuid) {
        if (uuid == null || presence == Presence.NO) {
            return null;
        }
        Image cached = decoded.get(uuid);
        if (cached != null) {
            return cached;
        }
        Long noUntil = noAvatarUntil.get(uuid);
        long now = System.currentTimeMillis();
        if (noUntil != null && now < noUntil) {
            return null;
        }
        Long sentAt = requestedAt.get(uuid);
        if (sentAt != null) {
            if (now - sentAt > PROBE_TIMEOUT_MS) {
                // Lost answer — typically the integrated server still chewing
                // through the join of the very player whose card fired this
                // request. Never latch "no companion" from a timeout: a busy
                // join used to kill the whole session's sync here (0.2.5 hunt).
                // Forget the attempt and re-send after a cooldown instead.
                debug("request for " + uuid + " unanswered after " + PROBE_TIMEOUT_MS + "ms, retrying in " + RETRY_BACKOFF_MS + "ms");
                requestedAt.remove(uuid);
                noAvatarUntil.put(uuid, now + RETRY_BACKOFF_MS);
            }
            return null;
        }
        if (!serverSupportsCompanion()) {
            // The server did not negotiate AtomChat's avatar channels. Do not
            // send an unknown C2S payload; mark the server companion-less so
            // the rest of the session silently degrades to skins. This is the
            // only authoritative "no companion" signal — negotiation, not a
            // timeout.
            presence = Presence.NO;
            requestedAt.clear();
            debug("server has no companion channels, degrading to skins");
            return null;
        }
        requestedAt.put(uuid, now);
        debug("requesting avatar for " + uuid);
        PacketDistributor.sendToServer(new AvatarPayloads.AvatarRequestPayload(uuid));
        return null;
    }

    /** True when the connected server actually registered the avatar C2S channel. */
    private static boolean serverSupportsCompanion() {
        try {
            // The master hosting switch also governs avatars. Unknown state
            // keeps the legacy "assume yes" so a join that races the state
            // reply still syncs; the server enforces the switch regardless.
            if (!MediaCompanionClient.avatarHostingAllowed()) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            ClientPacketListener connection = mc.getConnection();
            return connection != null && NetworkRegistry.hasChannel(
                    connection, AvatarPayloads.AvatarRequestPayload.TYPE.id());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Pushes the local avatar to the server; skipped only when the server has
     *  no companion channels. UNKNOWN counts as supported: the negotiation is
     *  authoritative and the very first upload of a session must go through
     *  even before any response has come back. */
    public static void uploadOwnAvatar(UUID uuid, byte[] pngBytes) {
        if (presence == Presence.NO) {
            debug("upload dropped: server has no companion channels");
            return;
        }
        if (uuid == null || pngBytes == null
                || pngBytes.length == 0 || pngBytes.length > AvatarPayloads.MAX_AVATAR_BYTES) {
            debug("upload dropped: invalid payload");
            return;
        }
        debug("uploading own avatar (" + pngBytes.length + " bytes)");
        PacketDistributor.sendToServer(new AvatarPayloads.AvatarUploadPayload(uuid, pngBytes));
    }

    /** S2C push: another player's stored avatar changed mid-session; drop the
     *  stale decoded copy (and any pending state) so the next frame re-requests
     *  the fresh bytes. */
    static void onAvatarChanged(UUID uuid) {
        if (uuid == null) {
            return;
        }
        int generation = GENERATION.get();
        requestedAt.remove(uuid);
        noAvatarUntil.remove(uuid);
        Image stale = decoded.remove(uuid);
        if (stale != null && generation == GENERATION.get()) {
            stale.close();
        }
        debug("companion: avatar changed, cache dropped for " + uuid);
    }

    /** S2C receiver; runs on the render thread via the payload context. */
    static void onAvatarData(UUID uuid, byte[] data) {
        if (uuid == null) {
            return;
        }
        int generation = GENERATION.get();
        requestedAt.remove(uuid);
        noAvatarUntil.remove(uuid);
        if (presence == Presence.UNKNOWN) {
            presence = Presence.YES;
            debug("companion answered, marking server as supported");
        }
        if (data == null || data.length == 0) {
            // No avatar on the server; back off before asking again.
            debug("companion: no avatar stored for " + uuid);
            noAvatarUntil.put(uuid, System.currentTimeMillis() + NO_AVATAR_TTL_MS);
            return;
        }
        debug("companion: decoding avatar for " + uuid + " (" + data.length + " bytes)");
        if (decoding.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                Image image = Image.makeFromEncoded(data);
                if (image != null && generation == GENERATION.get()) {
                    decoded.put(uuid, image);
                    debug("companion: avatar for " + uuid + " ready (" + image.getWidth() + "x" + image.getHeight() + ")");
                } else if (image != null) {
                    image.close();
                } else {
                    debug("companion: decode returned null for " + uuid + " (" + data.length + " bytes)");
                }
            } catch (Throwable t) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "Failed to decode companion avatar for {}"), uuid, t);
            } finally {
                decoding.remove(uuid);
            }
        }, "AtomChat-CompanionAvatarDecode");
        worker.setDaemon(true);
        worker.start();
    }

    /** Debug-only diagnostics; silent unless the About-page debug switch is on. */
    private static void debug(String message) {
        if (AtomChatConfig.get().debug) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "[avatar] {}"), message);
        }
    }
}
