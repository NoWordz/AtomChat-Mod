package com.atom.chat.net;
import com.atom.chat.reaction.ReactionClient;
import net.minecraft.client.MinecraftClient;
public final class ReactionCompanionClient {
    private ReactionCompanionClient() {}
    public static void init() {
        ReactionClient.supported = ReactionCompanionClient::supported;
        ReactionClient.expandedSupported = ReactionCompanionClient::expandedSupported;
        ReactionClient.sender = bytes -> { if (supported()) { net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(expandedSupported() ? new ReactionPayloads.ExpandedAction(bytes) : new ReactionPayloads.ActionPayload(bytes)); } };
        ReactionClient.self = () -> { var client = MinecraftClient.getInstance(); return client.player == null ? null : client.player.getUuid(); };
        // Fabric already invokes play payload receivers on the client thread.
        // Apply receipts here so they are available when the next chat packet is delivered.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(ReactionPayloads.DataPayload.ID,
                (payload, context) -> onData(payload, context.responseSender()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(ReactionPayloads.ExpandedData.ID,
                (payload, context) -> onBytes(payload.bytes(), context.responseSender()));
    }
    public static boolean supported() {
        var client = MinecraftClient.getInstance();
        return client.player != null && client.getNetworkHandler() != null && (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(ReactionPayloads.ActionPayload.ID));
    }
    private static boolean expandedSupported() {
        return MinecraftClient.getInstance().getNetworkHandler() != null
                && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(ReactionPayloads.ExpandedAction.ID);
    }
    public static void onData(ReactionPayloads.DataPayload payload,Object origin) {
        onBytes(payload.bytes(), origin);
    }
    static void onBytes(byte[] bytes, Object origin) {
        var client=MinecraftClient.getInstance();
        Object current=client.getNetworkHandler()==null ? null : net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.getSender();
        ReactionClient.acceptFrom(origin,current,bytes);
    }
}
