package com.atom.chat.channel;

import com.atom.chat.chat.BlockList;
import com.atom.chat.config.AtomChatConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.nio.charset.StandardCharsets;

public final class ChannelNetwork {
    public static final ResourceLocation ID = ResourceLocation.tryParse(ChannelWire.PATH);
    private static final ResourceLocation REGISTER = ResourceLocation.tryParse("minecraft:register");

    private ChannelNetwork() {}

    public static void advertise(ClientPacketListener play) {
        if (play == null) return;
        play.send(new ServerboundCustomPayloadPacket(REGISTER, new FriendlyByteBuf(
                Unpooled.wrappedBuffer(ChannelWire.PATH.getBytes(StandardCharsets.UTF_8)))));
    }

    public static void receive(ClientPacketListener play, FriendlyByteBuf buf) {
        var client = Minecraft.getInstance();
        if (client.getConnection() != play || client.level == null || buf.readableBytes() > ChannelWire.MAX_BYTES) return;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        var config = AtomChatConfig.get();
        ChannelClient.receive(play, bytes, config.enabled, reply -> play.send(new ServerboundCustomPayloadPacket(
                ID, new FriendlyByteBuf(Unpooled.wrappedBuffer(reply)))), json -> {
                    try {
                        var component = Component.Serializer.fromJson(json);
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
