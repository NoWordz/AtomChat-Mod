package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;



public final class ReactionPayloads {
    private ReactionPayloads() {
    }

    public record ActionPayload(byte[] bytes) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "reaction_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public ActionPayload decode(RegistryFriendlyByteBuf buf) {
                        return new ActionPayload(buf.readByteArray(128));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, ActionPayload payload) {
                        buf.writeByteArray(payload.bytes());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DataPayload(byte[] bytes) implements CustomPacketPayload {
        public static final Type<DataPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "reaction_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DataPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public DataPayload decode(RegistryFriendlyByteBuf buf) {
                        return new DataPayload(buf.readByteArray(com.atom.chat.reaction.ReactionWire.MAX_BYTES));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, DataPayload payload) {
                        buf.writeByteArray(payload.bytes());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC,
                ReactionCompanionServer::handleAction);
        registrar.playToClient(DataPayload.TYPE, DataPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                        ctx.enqueueWork(() -> ReactionCompanionClient.onData(payload));
                    }
                });
    }
}
