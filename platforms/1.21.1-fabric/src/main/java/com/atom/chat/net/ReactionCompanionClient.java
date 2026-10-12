package com.atom.chat.net;
import com.atom.chat.reaction.ReactionClient;
import net.minecraft.client.MinecraftClient;
public final class ReactionCompanionClient {
    private ReactionCompanionClient() {}
    public static void init() {
        ReactionClient.supported = ReactionCompanionClient::supported;
        ReactionClient.sender = bytes -> { if (supported()) { net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ReactionPayloads.ActionPayload(bytes)); } };
        ReactionClient.self = () -> { var client = MinecraftClient.getInstance(); return client.player == null ? null : client.player.getUuid(); };
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(ReactionPayloads.DataPayload.ID,
                (payload, context) -> {
                    long generation = ReactionClient.generation();
                    context.client().execute(() -> { if (generation == ReactionClient.generation()) ReactionClient.accept(payload.bytes()); });
                });
    }
    public static boolean supported() {
        var client = MinecraftClient.getInstance();
        return client.player != null && client.getNetworkHandler() != null && (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(ReactionPayloads.ActionPayload.ID));
    }
    public static void onData(ReactionPayloads.DataPayload payload) { ReactionClient.accept(payload.bytes()); }
}
