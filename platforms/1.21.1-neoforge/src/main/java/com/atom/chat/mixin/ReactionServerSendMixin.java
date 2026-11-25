package com.atom.chat.mixin;
import com.atom.chat.reaction.ReactionTransport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(net.minecraft.server.network.ServerCommonPacketListenerImpl.class)
public class ReactionServerSendMixin {
    @Redirect(method="send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V"))
    private void atomchat$orderedDelivery(net.minecraft.network.Connection connection,net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener callback,boolean flush) {
        ReactionTransport.sendPair(connection, () -> {
            if ((Object) this instanceof net.minecraft.server.network.ServerGamePacketListenerImpl play) {
                com.atom.chat.net.CardCompanionServer.onOutboundPlayerShare(play.player, packet);
                com.atom.chat.net.ReactionCompanionServer.onOutbound(play.player, packet);
            }
        }, () -> connection.send(packet, callback, flush));
    }
}
