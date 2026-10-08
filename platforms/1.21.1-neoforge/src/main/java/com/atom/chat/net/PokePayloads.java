package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Poke-companion payloads. Same optional-channel shape as the avatar
 * companion: a vanilla or companion-less server never receives an unknown
 * payload, and the client silently degrades to a local-only poke.
 */
public final class PokePayloads {
    private PokePayloads() {
    }

    public record PokeC2SPayload(UUID target) implements CustomPacketPayload {
        public static final Type<PokeC2SPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "poke"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PokeC2SPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public PokeC2SPayload decode(RegistryFriendlyByteBuf buf) {
                        return new PokeC2SPayload(buf.readUUID());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, PokeC2SPayload payload) {
                        buf.writeUUID(payload.target());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PokeS2CPayload(UUID from) implements CustomPacketPayload {
        public static final Type<PokeS2CPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "poked"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PokeS2CPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public PokeS2CPayload decode(RegistryFriendlyByteBuf buf) {
                        return new PokeS2CPayload(buf.readUUID());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, PokeS2CPayload payload) {
                        buf.writeUUID(payload.from());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    /** Registers both payloads through the optional registrar. */
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(PokeC2SPayload.TYPE, PokeC2SPayload.STREAM_CODEC,
                PokeCompanionServer::handlePoke);
        // The S2C receiver touches client-only classes; keep it dist-guarded so
        // a dedicated server never loads them.
        registrar.playToClient(PokeS2CPayload.TYPE, PokeS2CPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                        ctx.enqueueWork(() -> PokeCompanionClient.onPoked(payload));
                    }
                });
    }
}
