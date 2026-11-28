package com.atom.chat.mixin;

import com.atom.chat.chat.ChatClassifier;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.MessageCapture;
import com.atom.chat.chat.PrivateChatParser;
import com.atom.chat.chat.SenderMeta;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.network.chat.MessageSignature;
import java.util.function.BooleanSupplier;
import java.time.Instant;

import java.util.UUID;

/**
 * Captures identity at actual HUD delivery, preserving private routing and visible filtered bodies.
 * Native reaction identity is carried separately from display content.
 */
@Mixin(value = ChatListener.class, priority = 500)
public class MessageHandlerMixin {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method="handleMessage(Lnet/minecraft/network/chat/MessageSignature;Ljava/util/function/BooleanSupplier;)V",at=@At("HEAD"),argsOnly=true)
    private java.util.function.BooleanSupplier atomchat$reactionDelivery(java.util.function.BooleanSupplier original) {
        return com.atom.chat.reaction.ReactionClient.defer(original);
    }

    @Inject(method = "showMessageToPlayer", at = @At("RETURN"))
    private void atomchat$clearDeliveredCapture(CallbackInfoReturnable<Boolean> ci) {
        MessageCapture.consume();
    }

    /** Capture only when the native listener actually delivers a visible message. */
    @Inject(method = "showMessageToPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V"))
    private void atomchat$capturePlayerChat(ChatType.Bound bound, PlayerChatMessage message, Component decorated,
                                            GameProfile gameProfile, boolean secure, Instant instant, CallbackInfoReturnable<Boolean> ci) {
        UUID uuid = message.sender() != null ? message.sender() : gameProfile.getId();
        String profile = gameProfile.getName();
        // Never restore masked content through the native unsigned/decorated body.
        Component contentComponent = message.filterMask().isEmpty() ? message.decoratedContent()
                : message.filterMask().applyWithFormatting(message.signedContent());
        if (contentComponent == null) return;
        String content = contentComponent.getString();
        Component senderComponent = bound.name();
        Component visible = bound.decorate(contentComponent);
        SenderMeta privateMeta = PrivateChatParser.tryParse(visible);
        if (privateMeta != null) {
            if (privateMeta.system()) { MessageCapture.set(privateMeta); return; }
            var local = Minecraft.getInstance().player;
            boolean outgoing = local != null && uuid.equals(local.getUUID());
            boolean outgoingKey = privateMeta.whisperPartnerUuid() != null;
            if (outgoingKey != outgoing) {
                MessageCapture.set(SenderMeta.systemMeta().withEvidence(com.atom.chat.chat.IdentityEvidence.CONFLICT));
                return;
            }
            // Incoming identity comes from the native message, not the nickname lookup.
            String partner = outgoing ? privateMeta.whisperPartner() : profile;
            MessageCapture.set(new SenderMeta(uuid, privateMeta.senderName(), profile, content, false, true,
                    partner, privateMeta.senderComponent(), contentComponent, com.atom.chat.chat.IdentityEvidence.NATIVE,
                    message.signedContent(), outgoing ? privateMeta.whisperPartnerUuid() : uuid));
            return;
        }
        MessageCapture.set(ChatPipeline.nativePublicLine(visible, senderComponent, contentComponent, uuid, profile).withOriginalContent(message.signedContent()));
    }

    @Shadow private void handleMessage(MessageSignature signature, BooleanSupplier callback) { throw new AssertionError(); }

    @Redirect(method = "handlePlayerChatMessage", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleMessage(Lnet/minecraft/network/chat/MessageSignature;Ljava/util/function/BooleanSupplier;)V"))
    private void atomchat$deferShareCard(ChatListener listener, MessageSignature signature, BooleanSupplier callback,
                                         PlayerChatMessage message, GameProfile profile, ChatType.Bound bound) {
        handleMessage(signature, com.atom.chat.net.CardCompanionClient.deferNativeShare(
                message.sender(), message.signedContent(), message.filterMask().isEmpty(), callback));
    }

    @Redirect(method = "handleDisguisedChatMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleMessage(Lnet/minecraft/network/chat/MessageSignature;Ljava/util/function/BooleanSupplier;)V"))
    private void atomchat$deferDisguised(ChatListener listener, MessageSignature signature, BooleanSupplier callback,
                                        Component message, ChatType.Bound bound) {
        handleMessage(signature, () -> {
            atomchat$captureDisguised(message, bound);
            try { return callback.getAsBoolean(); }
            finally { MessageCapture.consume(); }
        });
    }

    @Unique
    private void atomchat$captureDisguised(Component message, ChatType.Bound bound) {
        Component decorated = bound.decorate(message);
        SenderMeta privateMeta = PrivateChatParser.tryParse(decorated);
        if (privateMeta != null) { MessageCapture.set(privateMeta); return; }
        if (ChatClassifier.isVanillaBroadcast(decorated)) return;
        // The final HUD pass can apply templates to the complete rendered envelope.
        // A profileless bound nickname alone cannot identify a player across servers.
        // Identity-less delivery is classified once, at the final HUD component.
    }


}
