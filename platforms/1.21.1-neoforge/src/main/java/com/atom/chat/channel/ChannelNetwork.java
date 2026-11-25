package com.atom.chat.channel;

import com.atom.chat.chat.BlockList;
import com.atom.chat.config.AtomChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;

import java.util.Set;

public final class ChannelNetwork {
    private ChannelNetwork() {}

    private record Payload(byte[] bytes) implements CustomPacketPayload {
        static final Type<Payload> TYPE = new Type<>(ResourceLocation.parse(ChannelWire.PATH));
        static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.bytes), buf -> {
                    int size = buf.readableBytes();
                    if (size > ChannelWire.MAX_BYTES) {
                        buf.skipBytes(size);
                        return new Payload(new byte[0]);
                    }
                    byte[] bytes = new byte[size];
                    buf.readBytes(bytes);
                    return new Payload(bytes);
                });
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playBidirectional(Payload.TYPE, Payload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) receive(payload, ctx.listener());
        });
    }

    public static void advertise(ClientPacketListener play) {
        if (play != null) play.send(new ServerboundCustomPayloadPacket(
                new MinecraftRegisterPayload(Set.of(Payload.TYPE.id()))));
    }

    private static void receive(Payload payload, Object listener) {
        var client = Minecraft.getInstance();
        var play = client.getConnection();
        if (play == null || play != listener || client.level == null) return;
        var config = AtomChatConfig.get();
        ChannelClient.receive(play, payload.bytes(), config.enabled,
                bytes -> {
                    if (play.hasChannel(Payload.TYPE)) play.send(new ServerboundCustomPayloadPacket(new Payload(bytes)));
                }, json -> {
                    try {
                        var component = Component.Serializer.fromJson(json, client.level.registryAccess());
                        return component == null ? null : component.getString();
                    } catch (RuntimeException invalid) {
                        return null;
                    }
                }, message -> client.options.chatVisibility().get() != ChatVisiblity.FULL
                        || !client.getChatStatus().isChatAllowed(client.isLocalServer())
                        || client.isBlocked(message.author()) || client.getPlayerSocialManager().isHidden(message.author())
                        || config.hideBlockedMessages && BlockList.isBlocked(message.name()));
    }
}
