package com.atom.chat.mixin;

import com.atom.chat.channel.ChannelInbox;
import com.atom.chat.channel.ChannelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class TrChatChannelPacketMixin {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void atomchat$receiveChannel(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (!ChannelNetwork.ID.equals(packet.getIdentifier())) return;
        var play = (ClientPacketListener) (Object) this;
        PacketUtils.ensureRunningOnSameThread(packet, play, Minecraft.getInstance());
        ChannelNetwork.receive(play, packet.getInternalData());
        ci.cancel();
    }

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void atomchat$refreshChannelConnection(ClientboundRespawnPacket packet, CallbackInfo ci) {
        ChannelInbox.INSTANCE.connection(null);
        ChannelNetwork.advertise((ClientPacketListener) (Object) this);
    }
}
