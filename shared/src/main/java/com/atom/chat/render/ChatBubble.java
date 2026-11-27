package com.atom.chat.render;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.config.AtomChatConfig.BubbleStyle;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/** Bubble fills and images share one four-corner outline. */
public final class ChatBubble {
    private static final Paint PAINT = new Paint().setAntiAlias(CornerGeometry.ANTIALIAS);

    public enum Group {
        SINGLE(AppearanceUi.BubbleGroup.SINGLE),
        FIRST(AppearanceUi.BubbleGroup.FIRST),
        MIDDLE(AppearanceUi.BubbleGroup.MIDDLE),
        LAST(AppearanceUi.BubbleGroup.LAST);

        private final AppearanceUi.BubbleGroup addonGroup;

        Group(AppearanceUi.BubbleGroup addonGroup) { this.addonGroup = addonGroup; }

        public static Group of(boolean previous, boolean next) {
            return previous ? (next ? MIDDLE : LAST) : (next ? FIRST : SINGLE);
        }
    }

    private ChatBubble() { }

    public static Custom custom(AppearanceUi.Bubble callback, float x, float y, float w, float h,
                                boolean own, int fillColor, int textColor, Group group) {
        if (callback == null || !(w > 0F && h > 0F)) return null;
        var bounds = new AppearanceUi.Bounds(x, y, w, h);
        var context = new AppearanceUi.BubbleContext(bounds, group.addonGroup,
                own, UiTokens.s(1F), fillColor, textColor);
        Path path = null;
        boolean accepted = false;
        try {
            path = callback.outline(context);
            if (path == null || path.isClosed() || path.isEmpty() || !path.isFinite()
                    || path.isInverseFillType() || !path.isLastContourClosed()) return null;
            var ink = path.computeTightBounds();
            if (ink.getWidth() <= 0 || ink.getHeight() <= 0
                    || ink.getLeft() < x || ink.getTop() < y
                    || ink.getRight() > bounds.right() || ink.getBottom() > bounds.bottom()) return null;
            accepted = true;
            return new Custom(callback, context, path);
        } catch (RuntimeException failure) {
            AddonPaint.report("bubble outline", callback, failure);
            return null;
        } finally {
            if (!accepted && path != null && !path.isClosed()) path.close();
        }
    }

    /** Owns one validated contour for this draw and its associated hit geometry. */
    public static final class Custom implements AutoCloseable {
        private final AppearanceUi.Bubble callback;
        private final AppearanceUi.BubbleContext context;
        private final Path contour;

        private Custom(AppearanceUi.Bubble callback, AppearanceUi.BubbleContext context, Path contour) {
            this.callback = callback;
            this.context = context;
            this.contour = contour;
        }

        public boolean draw(Canvas canvas) {
            var b = context.bounds();
            return AddonPaint.draw(canvas, Rect.makeXYWH(b.x(), b.y(), b.width(), b.height()), contour,
                    output -> callback.draw(output, context, contour), callback, "bubble paint");
        }

        public void drawImage(Canvas canvas, Image image) {
            if (image == null) return;
            var b = context.bounds();
            Image source = ImageQuality.scaledForLogical(image, Math.max(b.width(), b.height()));
            var placement = new Matrix33(b.width() / source.getWidth(), 0F, b.x(),
                    0F, b.height() / source.getHeight(), b.y(), 0F, 0F, 1F);
            try (var shader = source.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, placement);
                 var paint = new Paint().setAntiAlias(CornerGeometry.ANTIALIAS).setShader(shader)) {
                canvas.drawPath(contour, paint);
            }
        }

        public void clip(Canvas canvas) {
            canvas.clipPath(contour, CornerGeometry.ANTIALIAS);
        }

        public boolean contains(float x, float y) { return contour.contains(x, y); }

        /** A separately owned contour for input geometry retained after this draw. */
        public Path copyOutline() { return new Path(contour); }

        public void drawPanelShadow(Canvas canvas) { HudShadow.drawAddon(canvas, contour, ShadowTokens.MESSAGE); }

        public void drawHudShadow(Canvas canvas) { HudShadow.drawAddon(canvas, contour, ShadowTokens.HUD); }

        @Override public void close() { contour.close(); }
    }

    private static RRect outline(float x, float y, float w, float h, BubbleStyle style, boolean own) {
        return outline(x,y,w,h,style,own,1);
    }
    public static RRect outline(float x,float y,float w,float h,BubbleStyle style,boolean own,float scale) {
        return outline(x, y, w, h, style, own, scale, Group.SINGLE);
    }

    public static RRect outline(float x, float y, float w, float h, BubbleStyle style, boolean own,
                                float scale, Group group) {
        float r = Math.min(Math.min(w, h) / 2F,
                (style == null || style == BubbleStyle.CLASSIC ? UiTokens.BUBBLE_RADIUS : UiTokens.BUBBLE_ROUND_RADIUS)*scale);
        if (style == BubbleStyle.CORNER) {
            float join = Math.min(r, 6F * scale);
            float top = group == Group.FIRST ? r : group == Group.MIDDLE ? join : 0F;
            float bottom = group == Group.FIRST ? 0F : group == Group.MIDDLE ? join : r;
            return CornerGeometry.circularRect(x, y, w, h,
                    own ? r : top, own ? top : r, own ? bottom : r, own ? r : bottom);
        }
        return RRect.makeComplexLTRB(x, y, x + w, y + h, new float[]{r, r, r, r, r, r, r, r});
    }

    public static void draw(Canvas canvas, float x, float y, float w, float h,
                            BubbleStyle style, boolean own, int color) {
        draw(canvas, x, y, w, h, style, own, color, Group.SINGLE);
    }

    public static void draw(Canvas canvas, float x, float y, float w, float h,
                            BubbleStyle style, boolean own, int color, Group group) {
        if (w <= 0F || h <= 0F) return;
        CornerGeometry.drawRRect(canvas, outline(x, y, w, h, style, own, 1F, group), PAINT.setColor(color));
    }

    public static void drawImage(Canvas canvas, Image image, float x, float y, float w, float h,
                                 BubbleStyle style, boolean own) {
        drawImage(canvas, image, x, y, w, h, style, own, Group.SINGLE);
    }

    public static void drawImage(Canvas canvas, Image image, float x, float y, float w, float h,
                                 BubbleStyle style, boolean own, Group group) {
        if (image == null || w <= 0F || h <= 0F) return;
        // An inline picture is often a full-resolution photo shown in a ~300 px
        // bubble. Handing the GPU a 2x2 bilinear tap over a 4-8x minification
        // is what made pictures look soft; the pre-halved copy keeps the last
        // step at 2x, where bilinear is exact.
        Image source = ImageQuality.scaledForLogical(image, Math.max(w, h));
        SkiaImages.drawShape(canvas, source, Rect.makeWH(source.getWidth(), source.getHeight()),
                outline(x, y, w, h, style, own, 1F, group), SamplingMode.LINEAR);
    }
}
