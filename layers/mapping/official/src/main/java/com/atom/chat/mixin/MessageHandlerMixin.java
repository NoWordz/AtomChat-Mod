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
            // Incoming identity comes from the native message, not the nickname lookup.
            String partner = outgoing ? privateMeta.whisperPartner() : profile;
            MessageCapture.set(new SenderMeta(uuid, privateMeta.senderName(), profile, content, false, true,
                    partner, privateMeta.senderComponent(), contentComponent));
            return;
        }
        MessageCapture.set(new SenderMeta(uuid, profile, profile, content, false,
                senderComponent, contentComponent));
    }

    @Shadow private void handleMessage(MessageSignature signature, BooleanSupplier callback) { throw new AssertionError(); }

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
        String decoratedStr = decorated.getString();
        Component senderComponent = bound.name();
        if (senderComponent != null && !senderComponent.getString().isBlank()) {
            String display = senderComponent.getString();
            String profile = display;
            UUID uuid = ChatClassifier.resolveUuid(display);
            if (uuid != null) {
                var info = ChatClassifier.resolveOnlinePlayer(display);
                if (info != null) {
                    profile = info.getProfile().getName();
                }
            }
            MessageCapture.set(new SenderMeta(uuid, display, profile, null, false,
                    senderComponent, message));
            return;
        }
        SenderMeta parsed = ChatPipeline.tryParsePlayerLine(decoratedStr);
        if (parsed != null) {
            MessageCapture.set(parsed);
        }
    }

    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void atomchat$captureSystem(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) {
            return;
        }
        SenderMeta privateMeta = PrivateChatParser.tryParse(message);
        if (privateMeta != null) {
            MessageCapture.set(privateMeta);
            return;
        }
        if (ChatClassifier.isVanillaBroadcast(message)) {
            return;
        }
        SenderMeta parsed = ChatPipeline.tryParsePlayerLine(message.getString());
        if (parsed != null) {
            MessageCapture.set(parsed);
        }
    }
}
