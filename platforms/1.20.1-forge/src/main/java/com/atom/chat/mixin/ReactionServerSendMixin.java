package com.atom.chat.mixin;
import com.atom.chat.reaction.ReactionTransport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(net.minecraft.server.network.ServerGamePacketListenerImpl.class)
public class ReactionServerSendMixin {
    @org.spongepowered.asm.mixin.Shadow public net.minecraft.server.level.ServerPlayer player;
    @Redirect(method="send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V"))
    private void atomchat$orderedDelivery(net.minecraft.network.Connection connection,net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener callback) {
        ReactionTransport.sendPair(connection, () -> {
            com.atom.chat.net.CardCompanionServer.onOutboundPlayerShare(player, packet);
            com.atom.chat.net.ReactionCompanionServer.onOutbound(player, packet);
        }, () -> connection.send(packet, callback));
    }
}
