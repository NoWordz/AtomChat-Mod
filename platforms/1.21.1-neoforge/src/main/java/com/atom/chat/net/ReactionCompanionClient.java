package com.atom.chat.net;
import com.atom.chat.reaction.ReactionClient;
import net.minecraft.client.Minecraft;
public final class ReactionCompanionClient {
    private ReactionCompanionClient() {}
    public static void init() {
        ReactionClient.supported = ReactionCompanionClient::supported;
        ReactionClient.sender = bytes -> { if (supported()) { net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ReactionPayloads.ActionPayload(bytes)); } };
        ReactionClient.self = () -> { var client = Minecraft.getInstance(); return client.player == null ? null : client.player.getUUID(); };

    }
    public static boolean supported() {
        var client = Minecraft.getInstance();
        return client.player != null && client.getConnection() != null && (client.getConnection().hasChannel(ReactionPayloads.ActionPayload.TYPE));
    }
    public static void onData(ReactionPayloads.DataPayload payload) { ReactionClient.accept(payload.bytes()); }
}
