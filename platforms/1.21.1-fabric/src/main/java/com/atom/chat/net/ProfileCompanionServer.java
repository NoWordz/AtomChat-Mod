package com.atom.chat.net;
import com.atom.chat.profile.ProfileRecord;
import com.atom.chat.profile.ProfileRepository;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
/** Authenticated profile protocol handler; storage is world-scoped. */
public final class ProfileCompanionServer {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ProfilePayloads.ProfileUpdatePayload.ID, (payload, context) -> {
            var player = context.player(); var server = player.getServer();
            if (server != null) server.execute(() -> update(player, payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(ProfilePayloads.ProfileRequestPayload.ID, (payload, context) -> {
            var player = context.player(); var server = player.getServer();
            if (server != null) server.execute(() -> request(player, payload));
        });
    }

    private static final Map<Object, ProfileRepository> REPOSITORIES = new WeakHashMap<>();
    private static final Map<Object, Map<UUID, Window>> WINDOWS = new WeakHashMap<>();
    private static final class Window { long start; int updates; int requests; }
    private static boolean allow(ServerPlayerEntity player, boolean update) {
        var server = player.getServer();
        if (server == null) return false;
        Map<UUID, Window> windows = WINDOWS.computeIfAbsent(server, ignored -> new java.util.LinkedHashMap<>());
        if (windows.size() > 512) windows.remove(windows.keySet().iterator().next());
        Window window = windows.computeIfAbsent(player.getUuid(), ignored -> new Window());
        long now = System.nanoTime() / 1_000_000L;
        if (now - window.start >= 1000 || now < window.start) {
            window.start = now; window.updates = 0; window.requests = 0;
        }
        return update ? ++window.updates <= 8 : ++window.requests <= 64;
    }
    private static ProfileRepository repository(ServerPlayerEntity player) {
        var server = player.getServer();
        if (server == null) throw new IllegalStateException("disconnected");
        ProfileRepository repository = REPOSITORIES.computeIfAbsent(server, ignored -> new ProfileRepository(
                server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("atomchat/profiles"), com.atom.chat.util.CacheDirs.mediaDataDir(),
                com.atom.chat.config.AtomChatServerConfig.get().maxProfileTotalBytes()));
        repository.setMaxBytes(com.atom.chat.config.AtomChatServerConfig.get().maxProfileTotalBytes());
        return repository;
    }
    /** Lookup is scoped to the requester's world, never a global profile cache. */
    static java.nio.file.Path media(ServerPlayerEntity player, String id) {
        return repository(player).media(id);
    }
    private static void update(ServerPlayerEntity player, ProfilePayloads.ProfileUpdatePayload payload) {
        if (payload.token() <= 0) return;
        UUID uuid = player.getUuid();
        if (!allow(player, true)) { answer(player, ProfileRecord.empty(uuid), payload.token(), "rate_limited"); return; }
        try {
            ProfileRepository repo = repository(player);
            ProfileRecord before = repo.get(uuid);
            // Update packets carry no claimed player UUID: authenticated sender
            // identity is the only way to choose the document being changed.
            ProfileRecord next = repo.update(uuid, payload.patch());
            answer(player, next, payload.token(), "");
            if (next.revision() != before.revision()) changed(player, uuid);
        } catch (java.io.IOException | RuntimeException e) {
            com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                    "profile", "Rejected profile update uuid={} reason={}"), uuid, e.getClass().getSimpleName());
            answer(player, ProfileRecord.empty(uuid), payload.token(), "sync_failed");
        }
    }
    private static void request(ServerPlayerEntity player, ProfilePayloads.ProfileRequestPayload payload) {
        if (payload.token() >= 0 || !allow(player, false)) return;
        try { answer(player, repository(player).get(payload.uuid()), payload.token(), ""); }
        catch (java.io.IOException | RuntimeException e) {
            com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                    "profile", "Failed to read profile uuid={} reason={}"), payload.uuid(), e.getClass().getSimpleName());
            answer(player, ProfileRecord.empty(payload.uuid()), payload.token(), "sync_failed");
        }
    }
    private static void answer(ServerPlayerEntity player, ProfileRecord record, long token, String error) {
        ProfilePayloads.ProfileDataPayload payload = new ProfilePayloads.ProfileDataPayload(record.uuid(), token,
                record.signature(), record.avatar(), record.banner(), record.revision(), record.imageFields(), error);
        if (ServerPlayNetworking.canSend(player, ProfilePayloads.ProfileDataPayload.ID)) ServerPlayNetworking.send(player, payload);
    }
    private static void changed(ServerPlayerEntity player, UUID uuid) {
        var server = player.getServer();
        if (server == null) return;
        ProfilePayloads.ProfileChangedPayload payload = new ProfilePayloads.ProfileChangedPayload(uuid);
        for (ServerPlayerEntity peer : server.getPlayerManager().getPlayerList()) {
            try { if (ServerPlayNetworking.canSend(peer, ProfilePayloads.ProfileChangedPayload.ID)) ServerPlayNetworking.send(peer, payload); }
            catch (RuntimeException e) {
                com.atom.chat.AtomChat.LOGGER.debug(com.atom.chat.diagnostics.AtomChatLog.message(
                        "profile", "Skipped profile notice for an unavailable connection"));
            }
        }
    }

}
