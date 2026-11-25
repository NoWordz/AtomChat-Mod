package com.atom.chat.mixin;

import com.atom.chat.net.ReactionCompanionServer;
import com.atom.chat.reaction.ReactionBroadcastFunction;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public class ReactionSystemBroadcastMixin {
    @ModifyVariable(method="broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",at=@At("HEAD"),argsOnly=true)
    private Function<ServerPlayer,Component> atomchat$broadcastId(Function<ServerPlayer,Component> original) {
        return new ReactionBroadcastFunction<>(original);
    }
    @Redirect(method="broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void atomchat$recipient(ServerPlayer receiver,Component content,boolean overlay,
            Component original,Function<ServerPlayer,Component> function,boolean originalOverlay) {
        var id=((ReactionBroadcastFunction<?,?>)function).id();
        try(var ignored=ReactionCompanionServer.broadcastDelivery(receiver,content,id,overlay)) { receiver.sendSystemMessage(content,overlay); }
    }
    @Redirect(method="broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/chat/OutgoingChatMessage;create(Lnet/minecraft/network/chat/PlayerChatMessage;)Lnet/minecraft/network/chat/OutgoingChatMessage;"))
    private net.minecraft.network.chat.OutgoingChatMessage atomchat$nativeBroadcast(net.minecraft.network.chat.PlayerChatMessage message,
            net.minecraft.network.chat.PlayerChatMessage original, java.util.function.Predicate<ServerPlayer> filter,
            ServerPlayer sender, net.minecraft.network.chat.ChatType.Bound bound) {
        return new com.atom.chat.net.ReactionBroadcastMessage(net.minecraft.network.chat.OutgoingChatMessage.create(message),
                com.atom.chat.net.CardCompanionServer.claimPlayerShare(sender, original));
    }
}
