package com.atom.chat.mixin;

import com.atom.chat.net.CardCompanionServer;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ShareChatIntakeMixin {
    @Shadow public ServerPlayer player;

    // Only the player-chat decoder reaches this successful return, before async filtering.
    @Inject(method = "getSignedMessage(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;Lnet/minecraft/network/chat/LastSeenMessages;)Lnet/minecraft/network/chat/PlayerChatMessage;", at = @At("RETURN"))
    private void atomchat$bindAcceptedShare(ServerboundChatPacket packet, LastSeenMessages acknowledged,
                                            CallbackInfoReturnable<PlayerChatMessage> result) {
        CardCompanionServer.bindAcceptedPlayerShare(player, result.getReturnValue());
    }
}
