package com.atom.chat.render;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.MentionHighlighter;
import com.atom.chat.chat.OwnIdentity;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SamplingMode;

import java.util.UUID;

/** The panel and gameplay HUD share the same message spans and avatar paint. */
public final class MessagePaint {
    private MessagePaint() {}

    public static RichText body(ChatMessage message) {
        RichText content = message.getContentRich();
        if (message.isSystem()) return content;
        String own = OwnIdentity.bareName();
        return own == null || own.isBlank() ? content
                : MentionHighlighter.highlightLocal(content, own, OwnIdentity.displayNameRich());
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
            SkiaDraw.drawRoundedRect(canvas, x, y, size, size, radius, 0xFF788291);
        }
        // The concentric rim covers the edge of either the face or its placeholder.
        SkiaDraw.drawRing(canvas, x + radius, y + radius, com.atom.chat.render.AvatarRing.rimRadius(radius),
                com.atom.chat.render.AvatarRing.RIM_STROKE_PX, UiTokens.rim(AtomChatAppearance.theme()));
    }
}
