package com.atom.chat.mixin;

import com.atom.chat.net.ReactionPacketIdentity;
import com.atom.chat.reaction.ReactionClient;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType.Bound;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public class ReactionClientPacketMixin {
    @Redirect(method="handleSystemChat",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/chat/ChatListener;handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void atomchat$delivery0(ChatListener handler,Component content, boolean overlay,ClientboundSystemChatPacket packet) {
        try(var ignored=ReactionClient.incoming(ReactionPacketIdentity.of(packet,Minecraft.getInstance().level.registryAccess()))) { handler.handleSystemMessage(content,overlay); }
    }
    @Redirect(method="handlePlayerChat",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/chat/ChatListener;handlePlayerChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/network/chat/ChatType$Bound;)V"))
    private void atomchat$delivery1(ChatListener handler,PlayerChatMessage message, GameProfile profile, Bound bound,ClientboundPlayerChatPacket packet) {
        try(var ignored=ReactionClient.incoming(ReactionPacketIdentity.of(packet,Minecraft.getInstance().level.registryAccess()))) { handler.handlePlayerChatMessage(message,profile,bound); }
    }
    @Redirect(method="handleDisguisedChat",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/chat/ChatListener;handleDisguisedChatMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType$Bound;)V"))
    private void atomchat$delivery2(ChatListener handler,Component content, Bound bound,ClientboundDisguisedChatPacket packet) {
        try(var ignored=ReactionClient.incoming(ReactionPacketIdentity.of(packet,Minecraft.getInstance().level.registryAccess()))) { handler.handleDisguisedChatMessage(content,bound); }
    }
}
