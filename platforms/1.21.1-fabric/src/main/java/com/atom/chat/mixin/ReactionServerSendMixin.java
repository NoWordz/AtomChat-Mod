package com.atom.chat.mixin;
import com.atom.chat.reaction.ReactionTransport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(net.minecraft.server.network.ServerCommonNetworkHandler.class)
public class ReactionServerSendMixin {
    @Redirect(method="send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V"))
    private void atomchat$orderedDelivery(net.minecraft.network.ClientConnection connection,net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callback,boolean flush) {
        ReactionTransport.sendPair(connection,()->{ if((Object)this instanceof net.minecraft.server.network.ServerPlayNetworkHandler play) com.atom.chat.net.ReactionCompanionServer.onOutbound(play.player,packet); },()->connection.send(packet,callback,flush));
    }
}
