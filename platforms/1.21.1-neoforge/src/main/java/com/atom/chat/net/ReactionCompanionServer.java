package com.atom.chat.net;
import com.atom.chat.reaction.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

/** Server-issued delivery IDs and audience-scoped reaction transport. */
public final class ReactionCompanionServer {
    private ReactionCompanionServer() {}
    private static final Map<MinecraftServer,ReactionDeliveryRegistry> DELIVERIES=Collections.synchronizedMap(new WeakHashMap<>());
    private static ReactionDeliveryRegistry deliveries(MinecraftServer server) { return DELIVERIES.computeIfAbsent(server,ignored->new ReactionDeliveryRegistry()); }
    public static ReactionDeliveryRegistry.Scope broadcastDelivery(ServerPlayer receiver,Object component,UUID id) {
        return broadcastDelivery(receiver,component,id,false);
    }
    public static ReactionDeliveryRegistry.Scope broadcastDelivery(ServerPlayer receiver,Object component,UUID id,boolean overlay) {
        return deliveries(receiver.server).scope(receiver,component,id,overlay);
    }
    public static void onOutbound(ServerPlayer receiver,net.minecraft.network.protocol.Packet<?> packet) {
        MinecraftServer server=receiver.server;
        if(server==null || !com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) return;
        UUID fingerprint=ReactionPacketIdentity.of(packet,server.registryAccess());
        if(fingerprint==null) return;
        ReactionDeliveryRegistry registry=deliveries(server);
        Object source=ReactionPacketIdentity.source(packet);
        UUID id=registry.take(receiver,source);
        if(id==null && source==null) id=registry.nativeId(fingerprint,receiver.getUUID());
        if(id==null) id=UUID.randomUUID();
        if(!available(receiver)) return;
        registry.register(id,receiver.getUUID());
        send(receiver,ReactionWire.receipt(new ReactionWire.Receipt(id,fingerprint)));
    }
    private static final Map<MinecraftServer, ReactionLedger> LEDGERS = new WeakHashMap<>();
    private static ReactionLedger ledger(MinecraftServer server) { return LEDGERS.computeIfAbsent(server, ignored -> new ReactionLedger()); }
    private static boolean available(ServerPlayer receiver) { return receiver.connection.hasChannel(ReactionPayloads.DataPayload.TYPE); }
    private static void send(ServerPlayer receiver, byte[] bytes) { if (available(receiver)) { net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(receiver, new ReactionPayloads.DataPayload(bytes)); } }
    private static void action(ServerPlayer sender, byte[] bytes) {
        MinecraftServer server = sender.server;
        if (server == null || !com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) return;
        ReactionWire.Action action;
        try { action = ReactionWire.readAction(bytes); }
        catch (IllegalArgumentException e) { return; }
        if(!deliveries(server).received(action.id(),sender.getUUID())) { send(sender,ReactionWire.rejection("unknown_message")); return; }
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
            if(deliveries(server).received(action.id(),receiver.getUUID())) send(receiver, response);
        }
    }
    public static void handleAction(ReactionPayloads.ActionPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sender) action(sender, payload.bytes()); });
    }
}
