package com.atom.chat.net;
import com.atom.chat.reaction.*;
import java.util.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

/** Server-issued delivery IDs and audience-scoped reaction transport. */
public final class ReactionCompanionServer {
    private ReactionCompanionServer() {}
    private static final Map<MinecraftServer,ReactionDeliveryRegistry> DELIVERIES=Collections.synchronizedMap(new WeakHashMap<>());
    private static ReactionDeliveryRegistry deliveries(MinecraftServer server) { return DELIVERIES.computeIfAbsent(server,ignored->new ReactionDeliveryRegistry()); }
    public static ReactionDeliveryRegistry.Scope broadcastDelivery(ServerPlayerEntity receiver,Object component,UUID id) {
        return broadcastDelivery(receiver,component,id,false);
    }
    public static ReactionDeliveryRegistry.Scope broadcastDelivery(ServerPlayerEntity receiver,Object component,UUID id,boolean overlay) {
        return deliveries(receiver.getServer()).scope(receiver,component,id,overlay);
    }
    public static void onOutbound(ServerPlayerEntity receiver,net.minecraft.network.packet.Packet<?> packet) {
        MinecraftServer server=receiver.getServer();
        if(server==null || !com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) return;
        UUID fingerprint=ReactionPacketIdentity.of(packet,server.getRegistryManager());
        if(fingerprint==null) return;
        ReactionDeliveryRegistry registry=deliveries(server);
        Object source=ReactionPacketIdentity.source(packet);
        UUID id=registry.take(receiver,source);
        if(id==null && source==null) id=registry.nativeId(fingerprint,receiver.getUuid());
        if(id==null) id=UUID.randomUUID();
        if(!available(receiver)) return;
        registry.register(id,receiver.getUuid());
        send(receiver,ReactionWire.receipt(new ReactionWire.Receipt(id,fingerprint)));
    }
    private static final Map<MinecraftServer, ReactionLedger> LEDGERS = new WeakHashMap<>();
    private static ReactionLedger ledger(MinecraftServer server) { return LEDGERS.computeIfAbsent(server, ignored -> new ReactionLedger()); }
    private static boolean available(ServerPlayerEntity receiver) { return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(receiver, ReactionPayloads.DataPayload.ID); }
    private static void send(ServerPlayerEntity receiver, byte[] bytes) { if (available(receiver)) { net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(receiver, new ReactionPayloads.DataPayload(bytes)); } }
    private static void action(ServerPlayerEntity sender, byte[] bytes) {
        MinecraftServer server = sender.getServer();
        if (server == null || !com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) return;
        ReactionWire.Action action;
        try { action = ReactionWire.readAction(bytes); }
        catch (IllegalArgumentException e) { return; }
        if(!deliveries(server).received(action.id(),sender.getUuid())) { send(sender,ReactionWire.rejection("unknown_message")); return; }
        ReactionLedger ledger = ledger(server);
        long before=ledger.revision(action.id());
        String reason = ledger.apply(sender.getUuid(), sender, sender.getGameProfile().getName(), action, System.nanoTime()/1_000_000L);
        if (!reason.isEmpty()) { send(sender, ReactionWire.rejection(reason)); return; }
        ReactionWire.Snapshot snapshot = ledger.snapshot(action.id());
        if (snapshot == null) return;
        byte[] response = ReactionWire.snapshot(snapshot);
        // The reacting client is included: it keeps no optimistic copy of its own
        // reaction, so this broadcast is the only thing that can make its chip
        // appear. A repeat that changed nothing goes to the sender alone.
        if (before==snapshot.revision()) { send(sender,response); return; }
        for (ServerPlayerEntity receiver : server.getPlayerManager().getPlayerList()) {
            if(deliveries(server).received(action.id(),receiver.getUuid())) send(receiver, response);
        }
    }
    public static void register() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(ReactionPayloads.ActionPayload.ID,
                (payload, context) -> context.server().execute(() -> action(context.player(), payload.bytes())));
    }
}
