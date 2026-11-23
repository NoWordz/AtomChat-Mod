package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.api.card.CardWireCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/**
 * Optional Forge 1.20.1 SimpleChannel for rich {@code ShareCard} broadcast,
 * live state updates and client-initiated player sharing.
 *
 * <p>Uses {@link NetworkRegistry#acceptMissingOr(String)} so vanilla clients and
 * servers without AtomChat connect seamlessly and automatically receive fallback
 * rich-text components.
 */
public final class CardPayloads {
    private static final String PROTOCOL = "1";
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "share_card_v1");
    public static final int ID_BROADCAST = 0;
    public static final int ID_STATE = 1;
    public static final int ID_CLIENT_SHARE = 2;

    public static SimpleChannel CHANNEL;

    private CardPayloads() {
    }

    /** S2C: authoritative server-sent share card with sender identity and plain fallback. */
    public record CardBroadcastPayload(UUID senderUuid, String senderName, boolean system,
                                       byte[] cardBytes, String fallbackJson) {
        public static void encode(CardBroadcastPayload payload, FriendlyByteBuf buf) {
            boolean hasUuid = payload.senderUuid() != null && !ZERO_UUID.equals(payload.senderUuid());
            buf.writeBoolean(hasUuid);
            if (hasUuid) buf.writeUUID(payload.senderUuid());
            buf.writeUtf(payload.senderName() == null ? "" : payload.senderName(), 128);
            buf.writeBoolean(payload.system());
            buf.writeByteArray(payload.cardBytes() == null ? new byte[0] : payload.cardBytes());
            buf.writeUtf(payload.fallbackJson() == null ? "" : payload.fallbackJson(), 16384);
        }

        public static CardBroadcastPayload decode(FriendlyByteBuf buf) {
            boolean hasUuid = buf.readBoolean();
            UUID uuid = hasUuid ? buf.readUUID() : null;
            String name = buf.readUtf(128);
            boolean system = buf.readBoolean();
            byte[] bytes = buf.readByteArray(CardWireCodec.MAX_CARD_BYTES);
            String fallback = buf.readUtf(16384);
            return new CardBroadcastPayload(uuid, name, system, bytes, fallback);
        }
    }

    /** S2C: compact live state update (e.g. red packet claimed/finished/expired) keyed by cardId. */
    public record CardStateUpdatePayload(byte[] stateBytes) {
        public static void encode(CardStateUpdatePayload payload, FriendlyByteBuf buf) {
            buf.writeByteArray(payload.stateBytes() == null ? new byte[0] : payload.stateBytes());
        }

        public static CardStateUpdatePayload decode(FriendlyByteBuf buf) {
            return new CardStateUpdatePayload(buf.readByteArray(CardWireCodec.MAX_STATE_BYTES));
        }
    }

    /** C2S: client-initiated safe share (current location or held equipment/item). */
    public record ClientCardSharePayload(byte[] cardBytes) {
        public static void encode(ClientCardSharePayload payload, FriendlyByteBuf buf) {
            buf.writeByteArray(payload.cardBytes() == null ? new byte[0] : payload.cardBytes());
        }

        public static ClientCardSharePayload decode(FriendlyByteBuf buf) {
            return new ClientCardSharePayload(buf.readByteArray(CardWireCodec.MAX_CARD_BYTES));
        }
    }

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL));

        CHANNEL.messageBuilder(CardBroadcastPayload.class, ID_BROADCAST)
                .encoder(CardBroadcastPayload::encode)
                .decoder(CardBroadcastPayload::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ctx.get().enqueueWork(() -> CardCompanionClient.onCardBroadcast(payload));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(CardStateUpdatePayload.class, ID_STATE)
                .encoder(CardStateUpdatePayload::encode)
                .decoder(CardStateUpdatePayload::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ctx.get().enqueueWork(() -> CardCompanionClient.onCardStateUpdate(payload));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(ClientCardSharePayload.class, ID_CLIENT_SHARE)
                .encoder(ClientCardSharePayload::encode)
                .decoder(ClientCardSharePayload::decode)
                .consumerMainThread((payload, ctx) -> CardCompanionServer.handleClientShare(payload, ctx.get()))
                .add();
    }
}
