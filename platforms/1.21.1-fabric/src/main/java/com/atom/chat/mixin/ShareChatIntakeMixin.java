package com.atom.chat.mixin;

import com.atom.chat.net.CardCompanionServer;
import net.minecraft.network.message.LastSeenMessageList;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ShareChatIntakeMixin {
    @Shadow public ServerPlayerEntity player;

    // Only the player-chat decoder reaches this successful return, before async filtering.
    @Inject(method = "getSignedMessage(Lnet/minecraft/network/packet/c2s/play/ChatMessageC2SPacket;Lnet/minecraft/network/message/LastSeenMessageList;)Lnet/minecraft/network/message/SignedMessage;", at = @At("RETURN"))
    private void atomchat$bindAcceptedShare(ChatMessageC2SPacket packet, LastSeenMessageList acknowledged,
                                            CallbackInfoReturnable<SignedMessage> result) {
        CardCompanionServer.bindAcceptedPlayerShare(player, result.getReturnValue());
    }
}
