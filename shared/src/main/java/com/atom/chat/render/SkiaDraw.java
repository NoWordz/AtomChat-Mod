package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

public final class SkiaDraw {
    private SkiaDraw() {
    }

    public static void drawRoundedRect(Canvas canvas, float x, float y, float width, float height, float radius, int color) {
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius),
                SHARED_PAINT.setColor(color).setMode(PaintMode.FILL));
    }

    /**
     * Blur-radius -> MaskFilter cache. drawRoundedShadow used to allocate a
     * fresh native MaskFilter on every call (and leave it to the GC); radii
     * only vary with the ui scale, so a tiny cache keeps the draw path
     * allocation-free. Cleared wholesale when a new scale shows up — the
     * handful of orphans go the same GC route the old code took every frame.
     * Render-thread only, like the rest of the draw path.
     */
    private static final java.util.Map<Float, MaskFilter> BLUR_FILTERS = new java.util.HashMap<>();
    private static final Paint SHADOW_PAINT = new Paint().setAntiAlias(true);

    /**
     * Shared mutable paint for the rounded-rect / ring / edge / image
     * primitives: each used to build (and close) a fresh native Paint per
     * call, dozens per frame across the whole UI. Every property a draw needs
     * is set immediately before it — mode and stroke width ride over from the
     * previous call, so fill paths must set FILL back explicitly. Same
     * one-native-handle-for-life, render-thread-only trade as SHADOW_PAINT.
     * The RRect/Rect arguments stay per-call factories on purpose: the pinned
     * types artifact (io.github.humbleui:types:0.1.1) declares Rect/RRect
     * fields final with no setters, so those are not reusable.
     */
    private static final Paint SHARED_PAINT = new Paint().setAntiAlias(true);

    public static void drawRoundedShadow(Canvas canvas, float x, float y, float width, float height, float radius, float blur, int color) {
        MaskFilter filter = BLUR_FILTERS.get(blur);
        if (filter == null) {
            if (BLUR_FILTERS.size() >= 16) {
                BLUR_FILTERS.clear();
            }
            filter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur);
            BLUR_FILTERS.put(blur, filter);
        }
        canvas.drawRRect(RRect.makeXYWH(x + blur * 0.5F, y + blur * 0.5F, width, height, radius),
                SHADOW_PAINT.setColor(color).setMaskFilter(filter));
    }

    /**
     * Two-tier shadow for the floating panel chrome (shell header, composer,
     * bottom tab bar): a tight inner pass that hugs the surface plus a wider,
     * fainter outer spread — the pair reads as hovering where a single blur
     * reads as pasted on. One helper so the two passes can never drift apart
     * between call sites; colours and blurs come from the UiTokens chrome
     * pair. Render-thread only, like the rest of the draw path.
     */
    public static void drawChromeShadow(Canvas canvas, float x, float y, float width, float height, float radius) {
        drawRoundedShadow(canvas, x, y, width, height, radius,
                com.atom.chat.ui.UiTokens.s(6), com.atom.chat.ui.UiTokens.CHROME_SHADOW_INNER);
        drawRoundedShadow(canvas, x, y, width, height, radius,
                com.atom.chat.ui.UiTokens.s(12), com.atom.chat.ui.UiTokens.CHROME_SHADOW_OUTER);
    }

    /**
     * Stroke-only circle centred on (cx, cy) — the outline-ring primitive the
     * slider knob's cut-out trace and the colour-swatch hairlines share.
     */
    public static void drawRing(Canvas canvas, float cx, float cy, float radius, float strokeWidth, int color) {
        canvas.drawOval(Rect.makeXYWH(cx - radius, cy - radius, radius * 2.0F, radius * 2.0F),
                SHARED_PAINT.setColor(color).setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth));
    }

    /**
     * 1px-class inner edge highlight: a low-alpha stroke hugging the card
     * border, read as the lit edge an elevated surface catches. Draw right
     * after the card fill; the intensity contrast against the fill does the
     * work, so a fixed subtle white works on both frosted and opaque cards.
     */
    public static void drawEdgeHighlight(Canvas canvas, float x, float y, float width, float height,
                                         float radius, float strokeWidth, int color) {
        float inset = strokeWidth * 0.5F;
        canvas.drawRRect(RRect.makeXYWH(x + inset, y + inset,
                        Math.max(0.0F, width - strokeWidth), Math.max(0.0F, height - strokeWidth),
                        Math.max(0.0F, radius - inset)),
                SHARED_PAINT.setColor(color).setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth));
    }

    public static void drawRoundedImage(Canvas canvas, Image image, float x, float y, float width, float height, float radius) {
        drawRoundedImage(canvas, image, x, y, width, height, radius, SamplingMode.LINEAR);
    }

    public static void drawRoundedImage(Canvas canvas, Image image, float x, float y, float width, float height, float radius, SamplingMode mode) {
        if (image == null) {
            return;
        }
        canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(x, y, width, height, radius), ClipMode.INTERSECT, true);
            Rect src = Rect.makeXYWH(0, 0, image.getWidth(), image.getHeight());
            Rect dst = Rect.makeXYWH(x, y, width, height);
            // drawImageRect folds the paint's colour/alpha into the image, so
            // the shared paint must go back to the fresh-Paint default
            // (opaque black) instead of inheriting the last fill's colour.
            canvas.drawImageRect(image, src, dst, mode,
                    SHARED_PAINT.setColor(0xFF000000).setMode(PaintMode.FILL), false);
        } finally {
            canvas.restore();
        }
    }

    /**
     * Draws an image into a rounded rect with a centred cover crop (scale to
     * cover, never stretch): the source rect is the largest centred window
     * with the destination's aspect, so a photo fills the whole surface and
     * the overflow is cropped evenly on both axes. Used by the profile banner,
     * whose aspect depends on the panel width and is therefore unknown to the
     * decode cache.
     */
    public static void drawImageCover(Canvas canvas, Image image, float x, float y, float width, float height,
                                      float radius, SamplingMode mode) {
        if (image == null || width <= 0.0F || height <= 0.0F) {
            return;
        }
        canvas.save();
        try {
            clip(canvas, x, y, width, height, radius);
            float srcW;
            float srcH;
            if (image.getWidth() * height > image.getHeight() * width) {
                // Image is proportionally wider: crop the sides.
                srcH = image.getHeight();
                srcW = srcH * width / height;
            } else {
                // Image is proportionally taller (or exact): crop top/bottom.
                srcW = image.getWidth();
                srcH = srcW * height / width;
            }
            Rect src = Rect.makeXYWH((image.getWidth() - srcW) / 2.0F,
                    (image.getHeight() - srcH) / 2.0F, srcW, srcH);
            Rect dst = Rect.makeXYWH(x, y, width, height);
            try (Paint paint = new Paint().setAntiAlias(true)) {
                canvas.drawImageRect(image, src, dst, mode, paint, false);
            }
        } finally {
            canvas.restore();
        }
    }

    public static void drawBlurredBackground(Canvas canvas, Image snapshot, float x, float y, float width, float height, float radius, float blur) {
        if (snapshot == null) {
            return;
        }
        canvas.save();
        try {
            clip(canvas, x, y, width, height, radius);
            Rect src = Rect.makeXYWH(0, 0, snapshot.getWidth(), snapshot.getHeight());
            Rect dst = Rect.makeXYWH(x, y, width, height);
            try (Paint paint = new Paint().setAntiAlias(true)
                    .setImageFilter(ImageFilter.makeBlur(blur, blur, FilterTileMode.CLAMP))) {
                canvas.drawImageRect(snapshot, src, dst, SamplingMode.LINEAR, paint, false);
            }
        } finally {
            canvas.restore();
        }
    }

    public static void clip(Canvas canvas, float x, float y, float width, float height, float radius) {
        canvas.clipRRect(RRect.makeXYWH(x, y, width, height, radius), ClipMode.INTERSECT, true);
    }

    /**
     * Per-channel ARGB interpolation. Used where a control has to move between
     * two colours instead of cross-fading two whole shapes — the toggle track
     * warms from neutral white to the accent as the knob travels.
     */
    public static int lerpColor(int from, int to, float t) {
        float c = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round(Color.getA(from) + (Color.getA(to) - Color.getA(from)) * c);
        int r = Math.round(Color.getR(from) + (Color.getR(to) - Color.getR(from)) * c);
        int g = Math.round(Color.getG(from) + (Color.getG(to) - Color.getG(from)) * c);
        int b = Math.round(Color.getB(from) + (Color.getB(to) - Color.getB(from)) * c);
        return Color.makeARGB(a, r, g, b);
    }

    /**
     * Cheap vertical white gradient used for hover capsules: one rounded rect
     * filled with a linear gradient from {@code topColor} at the top edge to
     * {@code bottomColor} at the bottom edge.
     */
    public static void drawVerticalGradient(Canvas canvas, float x, float y, float width, float height,
                                            float radius, int topColor, int bottomColor) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        try (Shader shader = Shader.makeLinearGradient(x, y, x, y + height,
                new int[]{topColor, bottomColor}, new float[]{0.0F, 1.0F});
             Paint paint = new Paint().setAntiAlias(true).setShader(shader)) {
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }
}
