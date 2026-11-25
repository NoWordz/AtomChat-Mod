package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.api.card.CardWireCodec;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Optional network channels for rich {@code ShareCard} broadcast, live state updates
 * and client-initiated player sharing.
 *
 * <p>Negotiated dynamically via Fabric's {@code canSend} checks so vanilla clients and
 * servers without AtomChat connect seamlessly and automatically receive fallback rich-text.</p>
 */
public final class CardPayloads {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private CardPayloads() {
    }

    /** S2C: authoritative server-sent share card with sender identity and plain fallback. */
    public record CardBroadcastPayload(UUID senderUuid, String senderName, boolean system,
                                       byte[] cardBytes, String fallbackJson) implements CustomPayload {
        public static final CustomPayload.Id<CardBroadcastPayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "share_card_broadcast_v1"));

        public static final PacketCodec<RegistryByteBuf, CardBroadcastPayload> CODEC =
                PacketCodec.of(CardBroadcastPayload::write, CardBroadcastPayload::read);

        private static void write(CardBroadcastPayload payload, RegistryByteBuf buf) {
            boolean hasUuid = payload.senderUuid() != null && !ZERO_UUID.equals(payload.senderUuid());
            buf.writeBoolean(hasUuid);
            if (hasUuid) buf.writeUuid(payload.senderUuid());
            buf.writeString(payload.senderName() == null ? "" : payload.senderName(), 128);
            buf.writeBoolean(payload.system());
            buf.writeByteArray(payload.cardBytes() == null ? new byte[0] : payload.cardBytes());
            buf.writeString(payload.fallbackJson() == null ? "" : payload.fallbackJson(), 16384);
        }

        private static CardBroadcastPayload read(RegistryByteBuf buf) {
            boolean hasUuid = buf.readBoolean();
            UUID uuid = hasUuid ? buf.readUuid() : null;
            String name = buf.readString(128);
            boolean system = buf.readBoolean();
            byte[] bytes = buf.readByteArray(CardWireCodec.MAX_CARD_BYTES);
            String fallback = buf.readString(16384);
            return new CardBroadcastPayload(uuid, name, system, bytes, fallback);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Optional receipt for a player share carried as a system message. */
    public record ConvertedCardPayload(CardBroadcastPayload card) implements CustomPayload {
        public static final Id<ConvertedCardPayload> ID =
                new Id<>(Identifier.of(AtomChat.MOD_ID, "share_card_converted_v1"));
        public static final PacketCodec<RegistryByteBuf, ConvertedCardPayload> CODEC = PacketCodec.of(
                (payload, buf) -> CardBroadcastPayload.CODEC.encode(buf, payload.card()),
                buf -> new ConvertedCardPayload(CardBroadcastPayload.CODEC.decode(buf)));

        @Override public Id<ConvertedCardPayload> getId() { return ID; }
    }

    /** S2C: compact live state update (e.g. red packet claimed/finished/expired) keyed by cardId. */
    public record CardStateUpdatePayload(byte[] stateBytes) implements CustomPayload {
        public static final CustomPayload.Id<CardStateUpdatePayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "share_card_state_v1"));

        public static final PacketCodec<RegistryByteBuf, CardStateUpdatePayload> CODEC =
                PacketCodec.of(CardStateUpdatePayload::write, CardStateUpdatePayload::read);

        private static void write(CardStateUpdatePayload payload, RegistryByteBuf buf) {
            buf.writeByteArray(payload.stateBytes() == null ? new byte[0] : payload.stateBytes());
        }

        private static CardStateUpdatePayload read(RegistryByteBuf buf) {
            return new CardStateUpdatePayload(buf.readByteArray(CardWireCodec.MAX_STATE_BYTES));
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** C2S: client-initiated safe share (current location or held equipment/item). */
    public record ClientCardSharePayload(byte[] cardBytes) implements CustomPayload {
        public static final CustomPayload.Id<ClientCardSharePayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "share_card_client_v1"));

        public static final PacketCodec<RegistryByteBuf, ClientCardSharePayload> CODEC =
                PacketCodec.of(ClientCardSharePayload::write, ClientCardSharePayload::read);

        private static void write(ClientCardSharePayload payload, RegistryByteBuf buf) {
            buf.writeByteArray(payload.cardBytes() == null ? new byte[0] : payload.cardBytes());
        }

        private static ClientCardSharePayload read(RegistryByteBuf buf) {
            return new ClientCardSharePayload(buf.readByteArray(CardWireCodec.MAX_CARD_BYTES));
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Registers all codecs; must run on both logical sides (common init). */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(CardBroadcastPayload.ID, CardBroadcastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConvertedCardPayload.ID, ConvertedCardPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CardStateUpdatePayload.ID, CardStateUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientCardSharePayload.ID, ClientCardSharePayload.CODEC);
    }
}
