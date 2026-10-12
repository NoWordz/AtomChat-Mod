package com.atom.chat.mixin;

import com.atom.chat.chat.ChatClassifier;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.MessageCapture;
import com.atom.chat.chat.PrivateChatParser;
import com.atom.chat.chat.SenderMeta;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;
import java.util.UUID;

/**
 * Captures identity at actual HUD delivery, preserving private routing and visible filtered bodies.
 * Native reaction identity is carried separately from display content.
 */
@Mixin(value = MessageHandler.class, priority = 500)
public class MessageHandlerMixin {
    /** Capture only when the native listener actually delivers a visible message. */
    @Inject(method = "processChatMessageInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"))
    private void atomchat$captureSignedBeforeAdd(MessageType.Parameters bound, SignedMessage message, Text decorated,
                                            GameProfile gameProfile, boolean secure, Instant instant, CallbackInfoReturnable<Boolean> ci) {
        UUID uuid = message.getSender() != null ? message.getSender() : gameProfile.getId();
        String profile = gameProfile.getName();
        // Never restore masked content through the native unsigned/decorated body.
        Text contentComponent = message.filterMask().isPassThrough() ? message.getContent()
                : message.filterMask().getFilteredText(message.getSignedContent());
        if (contentComponent == null) return;
        String content = contentComponent.getString();
        Text senderComponent = bound.name();
        Text visible = bound.applyChatDecoration(contentComponent);
        SenderMeta privateMeta = PrivateChatParser.tryParse(visible);
        if (privateMeta != null) {
            if (privateMeta.system()) { MessageCapture.set(privateMeta); return; }
            var local = MinecraftClient.getInstance().player;
            boolean outgoing = local != null && uuid.equals(local.getUuid());
            // Incoming identity comes from the native message, not the nickname lookup.
            String partner = outgoing ? privateMeta.whisperPartner() : profile;
            MessageCapture.set(new SenderMeta(uuid, privateMeta.senderName(), profile, content, false, true,
                    partner, privateMeta.senderComponent(), contentComponent));
            return;
        }
        MessageCapture.set(new SenderMeta(uuid, profile, profile, content, false,
                senderComponent, contentComponent));
    }

    /**
     * Profileless / disguised chat is decorated by {@code method_45745} just
     * before it reaches {@code ChatHud.addMessage}. Injecting there lets us run
     * the text-level parser on the decorated line instead of the raw body.
     */
    @Inject(method = "method_45745", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;)V"))
    private void atomchat$captureProfilelessBeforeAdd(MessageType.Parameters bound, Text message,
                                                      Instant instant,
                                                      CallbackInfoReturnable<Boolean> ci) {
        Text decorated = bound.applyChatDecoration(message);
        SenderMeta privateMeta = PrivateChatParser.tryParse(decorated);
        if (privateMeta != null) { MessageCapture.set(privateMeta); return; }
        String decoratedStr = decorated.getString();
        Text senderComponent = bound.name();
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

    /**
     * System/game messages. The inject sits on the non-overlay branch that
     * actually calls {@code ChatHud.addMessage}, after vanilla's
     * hide-matched-names and blocked-sender early returns.
     */
    @Inject(method = "onGameMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;)V"))
    private void atomchat$captureGameBeforeAdd(Text message, boolean overlay, CallbackInfo ci) {
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
