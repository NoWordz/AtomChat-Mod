package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;



public final class ReactionPayloads {
    private ReactionPayloads() {
    }

    
    private static final String PROTOCOL = "1";

    
    public static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "reaction_action");
    public static final int ID_POKE = 0;
    public static final int ID_POKED = 1;

    public static SimpleChannel CHANNEL;

    public record ActionPayload(byte[] bytes) {
        public static void encode(ActionPayload payload, FriendlyByteBuf buf) {
            buf.writeByteArray(payload.bytes());
        }

        public static ActionPayload decode(FriendlyByteBuf buf) {
            return new ActionPayload(buf.readByteArray(128));
        }
    }

    public record DataPayload(byte[] bytes) {
        public static void encode(DataPayload payload, FriendlyByteBuf buf) {
            buf.writeByteArray(payload.bytes());
        }

        public static DataPayload decode(FriendlyByteBuf buf) {
            return new DataPayload(buf.readByteArray(com.atom.chat.reaction.ReactionWire.MAX_BYTES));
        }
    }

    
    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL));

        CHANNEL.messageBuilder(ActionPayload.class, ID_POKE, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(ActionPayload::encode)
                .decoder(ActionPayload::decode)
                .consumerMainThread((payload, ctx) -> ReactionCompanionServer.handleAction(payload, ctx.get()))
                .add();
        CHANNEL.messageBuilder(DataPayload.class, ID_POKED, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DataPayload::encode)
                .decoder(DataPayload::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (net.minecraftforge.fml.loading.FMLEnvironment.dist
                            == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                        ctx.get().enqueueWork(() -> ReactionCompanionClient.onData(payload));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }
}
