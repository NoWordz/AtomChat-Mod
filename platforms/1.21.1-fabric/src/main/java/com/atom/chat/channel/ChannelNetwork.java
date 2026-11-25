package com.atom.chat.channel;

import com.atom.chat.chat.BlockList;
import com.atom.chat.config.AtomChatConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ChannelNetwork {
    private ChannelNetwork() {}

    private record Payload(byte[] bytes) implements CustomPayload {
        static final Id<Payload> ID = new Id<>(Identifier.of(ChannelWire.PATH));
        static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeBytes(payload.bytes), buf -> {
                    int size = buf.readableBytes();
                    if (size > ChannelWire.MAX_BYTES) {
                        buf.skipBytes(size);
                        return new Payload(new byte[0]);
                    }
                    byte[] bytes = new byte[size];
                    buf.readBytes(bytes);
                    return new Payload(bytes);
                });
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
        PayloadTypeRegistry.playC2S().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (payload, ctx) -> {
            var client = ctx.client();
            var play = client.getNetworkHandler();
            if (play == null || client.world == null || ctx.responseSender() != ClientPlayNetworking.getSender()) return;
            var config = AtomChatConfig.get();
            ChannelClient.receive(play, payload.bytes(), config.enabled,
                    bytes -> ctx.responseSender().sendPacket(new Payload(bytes)), json -> {
                        try {
                            var component = Text.Serialization.fromJson(json, client.world.getRegistryManager());
                            return component == null ? null : component.getString();
                        } catch (RuntimeException invalid) {
                            return null;
                        }
                    }, message -> client.options.getChatVisibility().getValue() != ChatVisibility.FULL
                            || !client.getChatRestriction().allowsChat(client.isInSingleplayer())
                            || client.getSocialInteractionsManager().isPlayerBlocked(message.author())
                            || client.getSocialInteractionsManager().isPlayerHidden(message.author())
                            || config.hideBlockedMessages && BlockList.isBlocked(message.name()));
        });
    }
}
