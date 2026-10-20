package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig.BubbleStyle;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/** Bubble fill and image clip share one four-corner outline, as in Tuui's canvas helpers. */
public final class ChatBubble {
    private static final Paint PAINT = new Paint().setAntiAlias(true);

    private ChatBubble() { }

    private static RRect outline(float x, float y, float w, float h, BubbleStyle style, boolean own) {
        float r = Math.min(Math.min(w, h) / 2F,
                style == null || style == BubbleStyle.CLASSIC ? UiTokens.BUBBLE_RADIUS : UiTokens.BUBBLE_ROUND_RADIUS);
        float tl = style == BubbleStyle.CORNER && !own ? 0F : r;
        float tr = style == BubbleStyle.CORNER && own ? 0F : r;
        return RRect.makeComplexLTRB(x, y, x + w, y + h, new float[]{tl, tl, tr, tr, r, r, r, r});
    }

    public static void draw(Canvas canvas, float x, float y, float w, float h,
                            BubbleStyle style, boolean own, int color) {
        if (w <= 0F || h <= 0F) return;
        canvas.drawRRect(outline(x, y, w, h, style, own), PAINT.setColor(color));
    }

    public static void drawImage(Canvas canvas, Image image, float x, float y, float w, float h,
                                 BubbleStyle style, boolean own) {
        if (image == null || w <= 0F || h <= 0F) return;
        canvas.save();
        try {
            canvas.clipRRect(outline(x, y, w, h, style, own), ClipMode.INTERSECT, true);
            canvas.drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()),
                    Rect.makeXYWH(x, y, w, h), SamplingMode.LINEAR, PAINT.setColor(0xFF000000), false);
        } finally {
            canvas.restore();
        }
    }
}
