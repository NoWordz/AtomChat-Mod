package com.atom.chat.mixin;

import com.atom.chat.net.ReactionPacketIdentity;
import com.atom.chat.reaction.ReactionClient;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayNetworkHandler.class)
public class ReactionClientPacketMixin {
    @Redirect(method="onGameMessage",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/message/MessageHandler;onGameMessage(Lnet/minecraft/text/Text;Z)V"))
    private void atomchat$delivery0(MessageHandler handler,Text content, boolean overlay,GameMessageS2CPacket packet) {
        var fingerprint = ReactionPacketIdentity.of(packet, MinecraftClient.getInstance().world.getRegistryManager());
        try (var ignored = ReactionClient.incoming(fingerprint)) {
            com.atom.chat.net.CardCompanionClient.deliverConvertedShare(fingerprint, content,
                    () -> handler.onGameMessage(content, overlay));
        }
    }
    @Redirect(method="onChatMessage",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/message/MessageHandler;onChatMessage(Lnet/minecraft/network/message/SignedMessage;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/network/message/MessageType$Parameters;)V"))
    private void atomchat$delivery1(MessageHandler handler,SignedMessage message, GameProfile profile, Parameters bound,ChatMessageS2CPacket packet) {
        com.atom.chat.net.CardCompanionClient.discardConvertedShare();
        try(var ignored=ReactionClient.incoming(ReactionPacketIdentity.of(packet,MinecraftClient.getInstance().world.getRegistryManager()))) { handler.onChatMessage(message,profile,bound); }
    }
    @Redirect(method="onProfilelessChatMessage",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/message/MessageHandler;onProfilelessMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageType$Parameters;)V"))
    private void atomchat$delivery2(MessageHandler handler,Text content, Parameters bound,ProfilelessChatMessageS2CPacket packet) {
        com.atom.chat.net.CardCompanionClient.discardConvertedShare();
        try(var ignored=ReactionClient.incoming(ReactionPacketIdentity.of(packet,MinecraftClient.getInstance().world.getRegistryManager()))) { handler.onProfilelessMessage(content,bound); }
    }
}
