package com.atom.chat.render;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.MentionHighlighter;
import com.atom.chat.chat.OwnIdentity;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.text.RichText;
import com.atom.chat.text.RichTextLayout.RichLine;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SamplingMode;

import java.util.List;
import java.util.UUID;

/** The panel and gameplay HUD share the same message spans and avatar paint. */
public final class MessagePaint {
    private static final float QUOTE_MARKER_WIDTH = 3F;
    private static final float QUOTE_MARKER_SPACING = 4F;
    private static final float QUOTE_RADIUS = 6F;

    private MessagePaint() {}

    /** Read-only name paint keeps explicit wire decorations, without exposing click or hover actions. */
    public static RichText name(RichText text) {
        return text.withoutEvents();
    }

    public static String deliveryStatusKey(ChatMessage message) {
        if(!message.isOwn())return null;
        return switch(message.deliveryState()) {
            case PENDING -> "atomchat.message.pending";
            case UNCONFIRMED -> "atomchat.message.unconfirmed";
            case FAILED -> "atomchat.message.failed";
            default -> null;
        };
    }
    /** One name source for both preUi requests and the actual painted name layout. */
    public static RichText displayName(ChatMessage message,String fallback,String status) {
        RichText sender=name(message.getSenderRich());
        if(sender.isEmpty())sender=RichText.literal(java.util.Objects.toString(fallback,""));
        return status==null?sender:RichText.concat(sender,RichText.literal(" · "+status));
    }

    public static void requestFonts(java.util.List<ChatMessage> messages) {requestFonts(messages,ChatMessage::getSenderName);}
    public static void requestFonts(java.util.List<ChatMessage> messages,java.util.function.Function<ChatMessage,String> senderName) {
        for (ChatMessage message : messages) {
            NativeFontRenderer.request(name(message.getSenderRich()));
            if(message.getSenderRich().isEmpty()) {
                NativeFontRenderer.request(RichText.literal(java.util.Objects.toString(senderName.apply(message),"")));
                NativeFontRenderer.request(RichText.literal(java.util.Objects.toString(message.getSenderName(),"")));
                NativeFontRenderer.request(RichText.literal(java.util.Objects.toString(message.getProfileName(),"")));
            }
            NativeFontRenderer.request(body(message));
            RichText quote = richQuote(message);
            if (quote != null) NativeFontRenderer.request(quote);
            else if(message.getQuoteName()!=null)NativeFontRenderer.request(RichText.literal(message.getQuoteName()+": "+java.util.Objects.toString(message.getQuoteText(),"")));
        }
    }

    public static RichText body(ChatMessage message) {
        RichText content = message.getContentRich();
        if (message.isSystem()) return content;
        String own = OwnIdentity.bareName();
        return own == null || own.isBlank() ? content
                : MentionHighlighter.highlightLocal(content, own, OwnIdentity.displayNameRich());
    }

    public static RichText richQuote(ChatMessage message) {
        RichText name = message.getQuoteNameRich();
        RichText text = message.getQuoteTextRich();
        if (name.isEmpty() || text.isEmpty()) return null;
        String prefix = name.getString().startsWith("@") ? "" : "@";
        return RichText.concat(RichText.concat(RichText.literal(prefix), name),
                RichText.concat(RichText.literal(": "), text));
    }

    public static List<RichLine> oneLine(RichText text, Font font, float width) {
        String content = text.getString();
        int newline = content.indexOf('\n');
        int carriageReturn = content.indexOf('\r');
        if (carriageReturn >= 0 && (newline == -1 || carriageReturn < newline)) newline = carriageReturn;
        if (newline >= 0) text = RichText.concat(text.slice(0, newline), RichText.literal("…"));
        return RichTextRenderer.wrapFor(RichTextRenderer.truncate(font, text, width), font, Float.MAX_VALUE);
    }

    public static List<RichLine> hudLine(RichText text, Font font, float width) {
        var lines = oneLine(text, font, width);
        // The HUD must fit even when its remaining text lane cannot hold an ellipsis.
        return !lines.isEmpty() && RichTextRenderer.width(font, lines.get(0)) > width ? List.of() : lines;
    }

    public static float quoteTextInset() {
        return UiTokens.QUOTE_PAD_X + UiTokens.s(QUOTE_MARKER_WIDTH) + UiTokens.s(QUOTE_MARKER_SPACING);
    }

    public static void quoteCapsule(Canvas canvas, float x, float y, float width, int background, int accent) {
        float barWidth = UiTokens.s(QUOTE_MARKER_WIDTH);
        CornerGeometry.drawRoundedRect(canvas, x, y, width, UiTokens.QUOTE_HEIGHT, quoteRadius(), background);
        CornerGeometry.drawRoundedRect(canvas, x + UiTokens.QUOTE_PAD_X, y + barWidth, barWidth,
                UiTokens.QUOTE_HEIGHT - 2F * barWidth, barWidth / 2F, accent);
    }

    public static float quoteRadius() {
        return UiTokens.radius(QUOTE_RADIUS, AtomChatAppearance.theme());
    }

    public static void avatar(Canvas canvas, ChatMessage message, UUID uuid, String name, float x, float y) {
        float size = UiTokens.AVATAR_SIZE;
        float radius = size / 2F;
        boolean unknown = message.isIdentityUnknown();
        if (!unknown && PlayerAvatar.drawE33Chat(canvas, uuid, name, x, y, size)) return;
        Image face = unknown ? null : PlayerAvatar.face(uuid, name);
        if (face != null) {
            SkiaImages.drawRoundedImage(canvas, face, x, y, size, size, radius, SamplingMode.LINEAR);
        } else {
            CornerGeometry.drawRoundedRect(canvas, x, y, size, size, radius, 0xFF788291);
        }
        // The concentric rim covers the edge of either the face or its placeholder.
        com.atom.chat.render.AvatarRing.draw(canvas, x + radius, y + radius, radius, UiTokens.rim(AtomChatAppearance.theme()));
    }
}
