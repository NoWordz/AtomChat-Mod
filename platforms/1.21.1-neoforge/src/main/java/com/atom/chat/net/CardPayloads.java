package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.api.card.CardWireCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

/**
 * Optional network channels for rich {@code ShareCard} broadcast, live state updates
 * and client-initiated player sharing.
 *
 * <p>Registered as {@code optional()} so vanilla clients and servers without AtomChat
 * connect seamlessly and automatically receive fallback rich-text components.
 */
public final class CardPayloads {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private CardPayloads() {
    }

    /** S2C: authoritative server-sent share card with sender identity and plain fallback. */
    public record CardBroadcastPayload(UUID senderUuid, String senderName, boolean system,
                                       byte[] cardBytes, String fallbackJson) implements CustomPacketPayload {
        public static final Type<CardBroadcastPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "share_card_broadcast_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CardBroadcastPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public CardBroadcastPayload decode(RegistryFriendlyByteBuf buf) {
                        boolean hasUuid = buf.readBoolean();
                        UUID uuid = hasUuid ? buf.readUUID() : null;
                        String name = buf.readUtf(128);
                        boolean system = buf.readBoolean();
                        byte[] bytes = buf.readByteArray(CardWireCodec.MAX_CARD_BYTES);
                        String fallback = buf.readUtf(16384);
                        return new CardBroadcastPayload(uuid, name, system, bytes, fallback);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, CardBroadcastPayload payload) {
                        boolean hasUuid = payload.senderUuid() != null && !ZERO_UUID.equals(payload.senderUuid());
                        buf.writeBoolean(hasUuid);
                        if (hasUuid) buf.writeUUID(payload.senderUuid());
                        buf.writeUtf(payload.senderName() == null ? "" : payload.senderName(), 128);
                        buf.writeBoolean(payload.system());
                        buf.writeByteArray(payload.cardBytes() == null ? new byte[0] : payload.cardBytes());
                        buf.writeUtf(payload.fallbackJson() == null ? "" : payload.fallbackJson(), 16384);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: compact live state update (e.g. red packet claimed/finished/expired) keyed by cardId. */
    public record CardStateUpdatePayload(byte[] stateBytes) implements CustomPacketPayload {
        public static final Type<CardStateUpdatePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "share_card_state_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CardStateUpdatePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public CardStateUpdatePayload decode(RegistryFriendlyByteBuf buf) {
                        return new CardStateUpdatePayload(buf.readByteArray(CardWireCodec.MAX_STATE_BYTES));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, CardStateUpdatePayload payload) {
                        buf.writeByteArray(payload.stateBytes() == null ? new byte[0] : payload.stateBytes());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: client-initiated safe share (current location or held equipment/item). */
    public record ClientCardSharePayload(byte[] cardBytes) implements CustomPacketPayload {
        public static final Type<ClientCardSharePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "share_card_client_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ClientCardSharePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public ClientCardSharePayload decode(RegistryFriendlyByteBuf buf) {
                        return new ClientCardSharePayload(buf.readByteArray(CardWireCodec.MAX_CARD_BYTES));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, ClientCardSharePayload payload) {
                        buf.writeByteArray(payload.cardBytes() == null ? new byte[0] : payload.cardBytes());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(CardBroadcastPayload.TYPE, CardBroadcastPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ctx.enqueueWork(() -> CardCompanionClient.onCardBroadcast(payload));
                    }
                });
        registrar.playToClient(CardStateUpdatePayload.TYPE, CardStateUpdatePayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ctx.enqueueWork(() -> CardCompanionClient.onCardStateUpdate(payload));
                    }
                });
        registrar.playToServer(ClientCardSharePayload.TYPE, ClientCardSharePayload.STREAM_CODEC,
                CardCompanionServer::handleClientShare);
    }
}
