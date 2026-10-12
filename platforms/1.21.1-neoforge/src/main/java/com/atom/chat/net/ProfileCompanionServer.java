package com.atom.chat.net;
import com.atom.chat.profile.ProfileRecord;
import com.atom.chat.profile.ProfileRepository;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
/** Authenticated profile protocol handler; storage is world-scoped. */
public final class ProfileCompanionServer {
    public static void handleUpdate(ProfilePayloads.ProfileUpdatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer player) update(player, payload); });
    }
    public static void handleRequest(ProfilePayloads.ProfileRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer player) request(player, payload); });
    }

    private static final Map<Object, ProfileRepository> REPOSITORIES = new WeakHashMap<>();
    private static final Map<Object, Map<UUID, Window>> WINDOWS = new WeakHashMap<>();
    private static final class Window { long start; int updates; int requests; }
    private static boolean allow(ServerPlayer player, boolean update) {
        var server = player.getServer();
        if (server == null) return false;
        Map<UUID, Window> windows = WINDOWS.computeIfAbsent(server, ignored -> new java.util.LinkedHashMap<>());
        if (windows.size() > 512) windows.remove(windows.keySet().iterator().next());
        Window window = windows.computeIfAbsent(player.getUUID(), ignored -> new Window());
        long now = System.nanoTime() / 1_000_000L;
        if (now - window.start >= 1000 || now < window.start) {
            window.start = now; window.updates = 0; window.requests = 0;
        }
        return update ? ++window.updates <= 8 : ++window.requests <= 64;
    }
    private static ProfileRepository repository(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) throw new IllegalStateException("disconnected");
        ProfileRepository repository = REPOSITORIES.computeIfAbsent(server, ignored -> new ProfileRepository(
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("atomchat/profiles"), com.atom.chat.util.CacheDirs.mediaDataDir(),
                com.atom.chat.config.AtomChatServerConfig.get().maxProfileTotalBytes()));
        repository.setMaxBytes(com.atom.chat.config.AtomChatServerConfig.get().maxProfileTotalBytes());
        return repository;
    }
    /** Lookup is scoped to the requester's world, never a global profile cache. */
    static java.nio.file.Path media(ServerPlayer player, String id) {
        return repository(player).media(id);
    }
    private static void update(ServerPlayer player, ProfilePayloads.ProfileUpdatePayload payload) {
        if (payload.token() <= 0) return;
        UUID uuid = player.getUUID();
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
    private static void request(ServerPlayer player, ProfilePayloads.ProfileRequestPayload payload) {
        if (payload.token() >= 0 || !allow(player, false)) return;
        try { answer(player, repository(player).get(payload.uuid()), payload.token(), ""); }
        catch (java.io.IOException | RuntimeException e) {
            com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                    "profile", "Failed to read profile uuid={} reason={}"), payload.uuid(), e.getClass().getSimpleName());
            answer(player, ProfileRecord.empty(payload.uuid()), payload.token(), "sync_failed");
        }
    }
    private static void answer(ServerPlayer player, ProfileRecord record, long token, String error) {
        ProfilePayloads.ProfileDataPayload payload = new ProfilePayloads.ProfileDataPayload(record.uuid(), token,
                record.signature(), record.avatar(), record.banner(), record.revision(), record.imageFields(), error);
        if (NetworkRegistry.hasChannel(player.connection, ProfilePayloads.ProfileDataPayload.TYPE.id())) PacketDistributor.sendToPlayer(player, payload);
    }
    private static void changed(ServerPlayer player, UUID uuid) {
        var server = player.getServer();
        if (server == null) return;
        ProfilePayloads.ProfileChangedPayload payload = new ProfilePayloads.ProfileChangedPayload(uuid);
        for (ServerPlayer peer : server.getPlayerList().getPlayers()) {
            try { if (NetworkRegistry.hasChannel(peer.connection, ProfilePayloads.ProfileChangedPayload.TYPE.id())) PacketDistributor.sendToPlayer(peer, payload); }
            catch (RuntimeException e) {
                com.atom.chat.AtomChat.LOGGER.debug(com.atom.chat.diagnostics.AtomChatLog.message(
                        "profile", "Skipped profile notice for an unavailable connection"));
            }
        }
    }

}
