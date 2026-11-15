package com.atom.chat.mixin;

import com.atom.chat.net.ReactionCompanionServer;
import com.atom.chat.reaction.ReactionMessageTargets;
import java.util.Collection;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.network.message.SentMessage;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.command.MessageCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MessageCommand.class)
public class ReactionPrivateMessageMixin {
    @ModifyVariable(method="execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/Collection;Lnet/minecraft/network/message/SignedMessage;)V",at=@At("HEAD"),argsOnly=true)
    private static Collection<ServerPlayerEntity> atomchat$targetIds(Collection<ServerPlayerEntity> original) { return new ReactionMessageTargets<>(original); }
    @Redirect(method="execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/Collection;Lnet/minecraft/network/message/SignedMessage;)V",at=@At(value="INVOKE",target="Lnet/minecraft/server/command/ServerCommandSource;sendChatMessage(Lnet/minecraft/network/message/SentMessage;ZLnet/minecraft/network/message/MessageType$Parameters;)V"))
    private static void atomchat$outgoing(ServerCommandSource receiver,SentMessage sent,boolean filtered,Parameters bound,
            ServerCommandSource original,Collection<ServerPlayerEntity> targets,SignedMessage message) {
        var player=receiver.getPlayer();
        if(player==null) { receiver.sendChatMessage(sent,filtered,bound); return; }
        try(var scope=ReactionCompanionServer.broadcastDelivery(player,null,((ReactionMessageTargets<?>)targets).currentId())) { receiver.sendChatMessage(sent,filtered,bound); }
    }
    @Redirect(method="execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/Collection;Lnet/minecraft/network/message/SignedMessage;)V",at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerPlayerEntity;sendChatMessage(Lnet/minecraft/network/message/SentMessage;ZLnet/minecraft/network/message/MessageType$Parameters;)V"))
    private static void atomchat$incoming(ServerPlayerEntity receiver,SentMessage sent,boolean filtered,Parameters bound,
            ServerCommandSource original,Collection<ServerPlayerEntity> targets,SignedMessage message) {
        try(var scope=ReactionCompanionServer.broadcastDelivery(receiver,null,((ReactionMessageTargets<?>)targets).currentId())) { receiver.sendChatMessage(sent,filtered,bound); }
    }
}
