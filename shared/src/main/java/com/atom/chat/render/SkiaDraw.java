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
        try (Paint paint = new Paint().setColor(color).setAntiAlias(true)) {
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    public static void drawRoundedShadow(Canvas canvas, float x, float y, float width, float height, float radius, float blur, int color) {
        try (Paint shadow = new Paint().setColor(color).setAntiAlias(true)
                .setMaskFilter(MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur))) {
            canvas.drawRRect(RRect.makeXYWH(x + blur * 0.5F, y + blur * 0.5F, width, height, radius), shadow);
        }
    }

    /**
     * Stroke-only circle centred on (cx, cy) — the outline-ring primitive the
     * slider knob's cut-out trace and the colour-swatch hairlines share.
     */
    public static void drawRing(Canvas canvas, float cx, float cy, float radius, float strokeWidth, int color) {
        try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                .setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth)) {
            canvas.drawOval(Rect.makeXYWH(cx - radius, cy - radius, radius * 2.0F, radius * 2.0F), paint);
        }
    }

    /**
     * 1px-class inner edge highlight: a low-alpha stroke hugging the card
     * border, read as the lit edge an elevated surface catches. Draw right
     * after the card fill; the intensity contrast against the fill does the
     * work, so a fixed subtle white works on both frosted and opaque cards.
     */
    public static void drawEdgeHighlight(Canvas canvas, float x, float y, float width, float height,
                                         float radius, float strokeWidth, int color) {
        try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                .setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth)) {
            float inset = strokeWidth * 0.5F;
            canvas.drawRRect(RRect.makeXYWH(x + inset, y + inset,
                    Math.max(0.0F, width - strokeWidth), Math.max(0.0F, height - strokeWidth),
                    Math.max(0.0F, radius - inset)), paint);
        }
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
