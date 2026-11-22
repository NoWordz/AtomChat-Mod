package com.atom.chat.render;

import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.FilterMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.IRect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.LinkedHashMap;

/** Cached shape shadows for HUD messages and panel surfaces. Render-thread confined. */
public final class HudShadow {
    private static final int DENSITY = 2;
    private static final int MAX_PATCHES = 72;
    public static final float PAD = ShadowTokens.HUD.pad();
    public static final float PANEL_PAD = ShadowTokens.MESSAGE.pad();
    private static final LinkedHashMap<Shape, Patch> PATCHES = new LinkedHashMap<>(32, .75F, true);
    private static final Paint OPACITY = new Paint();

    private HudShadow() { }

    private record Shape(int width, int height, float tl, float tr, float br, float bl,
                         ShadowTokens.Face profile, boolean hollow, boolean circularCorners) { }
    private record Patch(Image image, IRect center) { }
    static void draw(Canvas canvas, RRect outline) {
        draw(canvas, outline, ShadowTokens.HUD, null);
    }

    static void drawAvatar(Canvas canvas, RRect outline) {
        draw(canvas, outline, ShadowTokens.HUD_AVATAR, null);
    }

    private static void draw(Canvas canvas, RRect outline, ShadowTokens.Face profile, Paint opacity) {
        draw(canvas, outline, profile, opacity, true);
    }

    private static void draw(Canvas canvas, RRect outline, ShadowTokens.Face profile, Paint opacity, boolean hollow) {
        float width = outline.getWidth(), height = outline.getHeight();
        if (width <= 0F || height <= 0F) return;
        float[] radii = outline._radii;
        float tl = radii[0], tr = radii.length == 1 ? tl : radii[2];
        float br = radii.length == 1 ? tl : radii[4], bl = radii.length == 1 ? tl : radii[6];
        float radius = Math.max(Math.max(tl, tr), Math.max(br, bl));
        boolean circularCorners = CornerGeometry.isSingleTip(tl, tr, br, bl)
                || (tl == tr && tl == br && tl == bl && radius >= Math.min(width, height) / 2F - .001F);
        float reach = circularCorners ? radius : CornerGeometry.maxReach(radius);
        int span = (int) Math.ceil((2F * reach + 6F * profile.wideSigma() + 2F) * DENSITY);
        int pixelsWide = (int) Math.ceil(width * DENSITY);
        int pixelsHigh = (int) Math.ceil(height * DENSITY);
        var key = new Shape(Math.min(span, pixelsWide), Math.min(span, pixelsHigh), tl, tr, br, bl, profile, hollow, circularCorners);
        Patch patch = PATCHES.get(key);
        if (patch == null) {
            patch = bake(key);
            if (PATCHES.size() >= MAX_PATCHES) {
                var oldest = PATCHES.entrySet().iterator();
                oldest.next().getValue().image().close();
                oldest.remove();
            }
            PATCHES.put(key, patch);
        }
        float pad = profile.pad();
        if (pixelsWide <= span && pixelsHigh <= span) {
            canvas.drawImageRect(patch.image(), Rect.makeWH(patch.image().getWidth(), patch.image().getHeight()),
                    Rect.makeXYWH(outline.getLeft() - pad, outline.getTop() - pad,
                    width + 2F * pad, height + 2F * pad), SamplingMode.LINEAR, opacity, false);
            return;
        }
        canvas.save();
        try {
            canvas.translate(outline.getLeft() - pad, outline.getTop() - pad);
            canvas.scale(1F / DENSITY, 1F / DENSITY);
            canvas.drawImageNine(patch.image(), patch.center(),
                    Rect.makeWH((width + 2F * pad) * DENSITY, (height + 2F * pad) * DENSITY), FilterMode.LINEAR, opacity);
        } finally {
            canvas.restore();
        }
    }

    public static void drawPanel(Canvas canvas, RRect outline) {
        if (SoftShadow.enabled()) draw(canvas, outline, ShadowTokens.MESSAGE, null);
    }

    public static void drawPanel(Canvas canvas, float x, float y, float width, float height, float radius) {
        drawPanel(canvas, x, y, width, height, radius, 1F);
    }

    public static void drawPanel(Canvas canvas, float x, float y, float width, float height, float radius, float alpha) {
        drawSurface(canvas, x, y, width, height, radius, ShadowTokens.MESSAGE, alpha);
    }

    static void drawSurface(Canvas canvas, float x, float y, float width, float height, float radius,
                            ShadowTokens.Face profile, float alpha) {
        if (!SoftShadow.enabled() || alpha <= 0F || width <= 0F || height <= 0F) return;
        float corner = Math.max(0F, Math.min(radius, Math.min(width, height) / 2F));
        var outline = RRect.makeXYWH(x, y, width, height, corner);
        draw(canvas, outline, profile, alpha < 1F ? OPACITY.setAlphaf(alpha) : null);
    }

    /** Popup material deliberately keeps its darkening pass inside the face. */
    static void drawMaterialShade(Canvas canvas, float x, float y, float width, float height, float radius,
                                  float blur, int color) {
        var layer = new ShadowTokens.Layer(blur, blur / 2F, color);
        var contour = RRect.makeXYWH(x + blur / 2F, y, width, height, radius);
        draw(canvas, contour, new ShadowTokens.Face(layer, null), null, false);
    }

    private static Patch bake(Shape key) {
        float pad = key.profile().pad();
        int w = key.width() + (int) (2F * pad * DENSITY);
        int h = key.height() + (int) (2F * pad * DENSITY);
        try (var surface = Surface.makeRasterN32Premul(w, h);
             var paint = new Paint().setAntiAlias(true)) {
            var canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(DENSITY, DENSITY);
            var face = RRect.makeComplexXYWH(pad, pad, (float) key.width() / DENSITY, (float) key.height() / DENSITY,
                    new float[]{key.tl(), key.tl(), key.tr(), key.tr(), key.br(), key.br(), key.bl(), key.bl()});
            if (key.profile().ambient() != null) paintLayer(canvas, face, paint, key.profile().ambient(), key.circularCorners());
            paintLayer(canvas, face, paint, key.profile().contact(), key.circularCorners());
            if (key.hollow()) {
                // Keep tinted capsules and transparent picture pixels free of their own shadow.
                drawContour(canvas, face, paint.setMaskFilter(null).setColor(0xFF000000).setBlendMode(BlendMode.DST_OUT), key.circularCorners());
            }
            return new Patch(surface.makeImageSnapshot(), IRect.makeXYWH(w / 2, h / 2, 1, 1));
        }
    }

    static void paintLayer(Canvas canvas, RRect face, Paint paint, ShadowTokens.Layer layer) {
        paintLayer(canvas, face, paint, layer, false);
    }

    static void paintLayer(Canvas canvas, RRect face, Paint paint, ShadowTokens.Layer layer, boolean circular) {
        try (var blur = MaskFilter.makeBlur(FilterBlurMode.NORMAL, layer.sigma())) {
            drawContour(canvas, face.offset(0F, layer.drop()), paint.setColor(layer.color()).setMaskFilter(blur), circular);
            paint.setMaskFilter(null);
        }
    }

    private static void drawContour(Canvas canvas, RRect contour, Paint paint, boolean circular) {
        if (circular) canvas.drawRRect(contour, paint);
        else CornerGeometry.drawRRect(canvas, contour, paint);
    }

    static void release() {
        PATCHES.values().forEach(patch -> patch.image().close());
        PATCHES.clear();
    }
}
