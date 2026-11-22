package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig.BubbleStyle;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/** Bubble fills and images share one four-corner outline. */
public final class ChatBubble {
    private static final Paint PAINT = new Paint().setAntiAlias(CornerGeometry.ANTIALIAS);

    private ChatBubble() { }

    private static RRect outline(float x, float y, float w, float h, BubbleStyle style, boolean own) {
        return outline(x,y,w,h,style,own,1);
    }
    public static RRect outline(float x,float y,float w,float h,BubbleStyle style,boolean own,float scale) {
        float r = Math.min(Math.min(w, h) / 2F,
                (style == null || style == BubbleStyle.CLASSIC ? UiTokens.BUBBLE_RADIUS : UiTokens.BUBBLE_ROUND_RADIUS)*scale);
        float tl = style == BubbleStyle.CORNER && !own ? 0F : r;
        float tr = style == BubbleStyle.CORNER && own ? 0F : r;
        return RRect.makeComplexLTRB(x, y, x + w, y + h, new float[]{tl, tl, tr, tr, r, r, r, r});
    }

    public static void draw(Canvas canvas, float x, float y, float w, float h,
                            BubbleStyle style, boolean own, int color) {
        if (w <= 0F || h <= 0F) return;
        CornerGeometry.drawRRect(canvas, outline(x, y, w, h, style, own), PAINT.setColor(color));
    }

    public static void drawImage(Canvas canvas, Image image, float x, float y, float w, float h,
                                 BubbleStyle style, boolean own) {
        if (image == null || w <= 0F || h <= 0F) return;
        // An inline picture is often a full-resolution photo shown in a ~300 px
        // bubble. Handing the GPU a 2x2 bilinear tap over a 4-8x minification
        // is what made pictures look soft; the pre-halved copy keeps the last
        // step at 2x, where bilinear is exact.
        Image source = ImageQuality.scaledForLogical(image, Math.max(w, h));
        SkiaImages.drawShape(canvas, source, Rect.makeWH(source.getWidth(), source.getHeight()),
                outline(x, y, w, h, style, own), SamplingMode.LINEAR);
    }
}
