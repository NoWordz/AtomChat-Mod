package com.atom.chat.mixin;

import com.atom.chat.net.ReactionCompanionServer;
import com.atom.chat.reaction.ReactionMessageTargets;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType.Bound;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MsgCommand.class)
public class ReactionPrivateMessageMixin {
    @ModifyVariable(method="sendMessage(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;Lnet/minecraft/network/chat/PlayerChatMessage;)V",at=@At("HEAD"),argsOnly=true)
    private static Collection<ServerPlayer> atomchat$targetIds(Collection<ServerPlayer> original) { return new ReactionMessageTargets<>(original); }
    @Redirect(method="sendMessage(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;Lnet/minecraft/network/chat/PlayerChatMessage;)V",at=@At(value="INVOKE",target="Lnet/minecraft/commands/CommandSourceStack;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"))
    private static void atomchat$outgoing(CommandSourceStack receiver,OutgoingChatMessage sent,boolean filtered,Bound bound,
            CommandSourceStack original,Collection<ServerPlayer> targets,PlayerChatMessage message) {
        var player=receiver.getPlayer();
        if(player==null) { receiver.sendChatMessage(sent,filtered,bound); return; }
        try(var scope=ReactionCompanionServer.broadcastDelivery(player,null,((ReactionMessageTargets<?>)targets).currentId())) { receiver.sendChatMessage(sent,filtered,bound); }
    }
    @Redirect(method="sendMessage(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;Lnet/minecraft/network/chat/PlayerChatMessage;)V",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"))
    private static void atomchat$incoming(ServerPlayer receiver,OutgoingChatMessage sent,boolean filtered,Bound bound,
            CommandSourceStack original,Collection<ServerPlayer> targets,PlayerChatMessage message) {
        try(var scope=ReactionCompanionServer.broadcastDelivery(receiver,null,((ReactionMessageTargets<?>)targets).currentId())) { receiver.sendChatMessage(sent,filtered,bound); }
    }
}
