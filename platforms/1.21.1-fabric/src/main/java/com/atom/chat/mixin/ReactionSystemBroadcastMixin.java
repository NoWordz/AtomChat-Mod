package com.atom.chat.mixin;

import com.atom.chat.net.ReactionCompanionServer;
import com.atom.chat.reaction.ReactionBroadcastFunction;
import java.util.function.Function;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerManager.class)
public class ReactionSystemBroadcastMixin {
    @ModifyVariable(method="broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Z)V",at=@At("HEAD"),argsOnly=true)
    private Function<ServerPlayerEntity,Text> atomchat$broadcastId(Function<ServerPlayerEntity,Text> original) {
        return new ReactionBroadcastFunction<>(original);
    }
    @Redirect(method="broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerPlayerEntity;sendMessageToClient(Lnet/minecraft/text/Text;Z)V"))
    private void atomchat$recipient(ServerPlayerEntity receiver,Text content,boolean overlay,
            Text original,Function<ServerPlayerEntity,Text> function,boolean originalOverlay) {
        var id=((ReactionBroadcastFunction<?,?>)function).id();
        try(var ignored=ReactionCompanionServer.broadcastDelivery(receiver,content,id,overlay)) { receiver.sendMessageToClient(content,overlay); }
    }
    @Redirect(method="broadcast(Lnet/minecraft/network/message/SignedMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/message/SentMessage;of(Lnet/minecraft/network/message/SignedMessage;)Lnet/minecraft/network/message/SentMessage;"))
    private net.minecraft.network.message.SentMessage atomchat$nativeBroadcast(net.minecraft.network.message.SignedMessage message,
            net.minecraft.network.message.SignedMessage original, java.util.function.Predicate<ServerPlayerEntity> filter,
            ServerPlayerEntity sender, net.minecraft.network.message.MessageType.Parameters bound) {
        return new com.atom.chat.net.ReactionBroadcastMessage(net.minecraft.network.message.SentMessage.of(message),
                com.atom.chat.net.CardCompanionServer.claimPlayerShare(sender, original));
    }
}
