package com.atom.chat.net;
import com.atom.chat.reaction.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

/**
 * Server side of the reaction transport.
 *
 * <p>It no longer observes native chat delivery: the reacting client supplies
 * the content key it computed for the row, and the server stores and broadcasts
 * state under that key. Delivery is a plain broadcast to every connection that
 * negotiated the channel; a client with no row for a key renders nothing, which
 * is what keeps private-message reactions scoped without any per-message
 * authorization. Only the emoji allowlist, rate limit and caps are enforced
 * here (in {@link ReactionLedger}).</p>
 */
public final class ReactionCompanionServer {
    private ReactionCompanionServer() {}
    private static final Map<MinecraftServer, ReactionLedger> LEDGERS = new WeakHashMap<>();
    private static ReactionLedger ledger(MinecraftServer server) { return LEDGERS.computeIfAbsent(server, ignored -> new ReactionLedger()); }
    private static boolean available(ServerPlayer receiver) { return ReactionPayloads.CHANNEL.isRemotePresent(receiver.connection.connection); }
    private static void send(ServerPlayer receiver, byte[] bytes) { if (available(receiver)) { ReactionPayloads.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> receiver), new ReactionPayloads.DataPayload(bytes)); } }
    private static void action(ServerPlayer sender, byte[] bytes) {
        MinecraftServer server = sender.server;
        if (server == null || !com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) return;
        ReactionWire.Action action;
        try { action = ReactionWire.readAction(bytes); }
        catch (IllegalArgumentException e) { return; }
        ReactionLedger ledger = ledger(server);
        long before=ledger.revision(action.id());
        String reason = ledger.apply(sender.getUUID(), sender, sender.getGameProfile().getName(), action, System.nanoTime()/1_000_000L);
        if (!reason.isEmpty()) { send(sender, ReactionWire.rejection(reason)); return; }
        ReactionWire.Snapshot snapshot = ledger.snapshot(action.id());
        if (snapshot == null) return;
        byte[] response = ReactionWire.snapshot(snapshot);
        // The reacting client is included: it keeps no optimistic copy of its own
        // reaction, so this broadcast is the only thing that can make its chip
        // appear. A repeat that changed nothing goes to the sender alone.
        if (before==snapshot.revision()) { send(sender,response); return; }
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            send(receiver, response);
        }
    }
    public static void handleAction(ReactionPayloads.ActionPayload payload, net.minecraftforge.network.NetworkEvent.Context ctx) {
        if (ctx.getSender() != null) action(ctx.getSender(), payload.bytes());
        ctx.setPacketHandled(true);
    }
}
