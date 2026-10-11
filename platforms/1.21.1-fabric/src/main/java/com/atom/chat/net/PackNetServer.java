package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * The server half of the pack seam: where the shared logic meets Fabric.
 *
 * <p>Two jobs, both mechanical: the send port ({@link Net.ServerSide}) and the
 * server facts the shared logic asks for ({@link Host.ServerSide}) - plus the
 * receivers, which hand a payload's neutral form to the shared logic (Fabric
 * registers those here, not in the payload types).
 *
 * <p>Loaded on a dedicated server too, so it must not name a client type - the
 * dist guard scans this file. The client half lives in {@link PackNetClient}.
 */
public final class PackNetServer implements Net.ServerSide, Host.ServerSide {

    /** Installed from the common entrypoint, before any payload can arrive. */
    public static void install() {
        PackNetServer impl = new PackNetServer();
        Net.installServerSide(impl);
        Host.installServerSide(impl);
        ServerPlayNetworking.registerGlobalReceiver(PackPayloads.Hello.ID,
                (payload, context) -> onServer(context.player(),
                        () -> PackSyncServer.onHello(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(PackPayloads.Need.ID,
                (payload, context) -> onServer(context.player(),
                        () -> PackSyncServer.onNeed(context.player(), payload.names())));
        ServerPlayNetworking.registerGlobalReceiver(PackPayloads.Ack.ID,
                (payload, context) -> onServer(context.player(),
                        () -> PackSyncServer.onAck(context.player(), PackPayloads.toMessage(payload))));
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "AtomChat pack distribution registered (server side)"));
    }

    private PackNetServer() {
    }

    /**
     * File reads and hashing belong off the network thread.
     *
     * <p>The tick hook in the entrypoint drains the queues, so the work handed to
     * the server thread here only builds the queue.
     */
    private static void onServer(ServerPlayerEntity player, Runnable work) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server != null) {
            server.execute(work);
        }
    }

    @Override
    public void sendToPlayer(Object player, PackMessage.S2C message) {
        if (player instanceof ServerPlayerEntity sender) {
            ServerPlayNetworking.send(sender, PackPayloads.fromMessage(message));
        }
    }

    @Override
    public UUID playerId(Object player) {
        return player instanceof ServerPlayerEntity sender ? sender.getUuid() : null;
    }

    @Override
    public String playerName(Object player) {
        return player instanceof ServerPlayerEntity sender ? sender.getName().getString() : "";
    }

    @Override
    public Object playerById(Object server, UUID id) {
        return server instanceof MinecraftServer minecraftServer
                ? minecraftServer.getPlayerManager().getPlayer(id)
                : null;
    }

    @Override
    public Object serverOf(Object player) {
        return player instanceof ServerPlayerEntity sender ? sender.getServer() : null;
    }

    @Override
    public String motd(Object server) {
        return server instanceof MinecraftServer minecraftServer
                ? minecraftServer.getServerMotd()
                : "";
    }

    @Override
    public boolean dedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }
}
