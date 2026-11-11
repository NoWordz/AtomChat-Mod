package com.atom.chat.render;

import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.FilterMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.IRect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.LinkedHashMap;
import java.util.Map;

/** Outside-only Gaussian shadows. Render-thread confined; cached images own CPU pixels. */
public final class SoftShadow {
    private static final float PANEL_SIGMA = ShadowTokens.PANEL_SIGMA;
    private static final float PANEL_DROP = ShadowTokens.PANEL_DROP;
    private static final int DENSITY = 2;
    private static final int MAX_PATCHES = 12;
    private static final Map<Float, PanelPatch> PANEL_PATCHES = new LinkedHashMap<>(16, .75F, true);

    private record PanelPatch(Image image, IRect center) { }

    private SoftShadow() { }

    /** Already scaled drawing units. Four sigma plus the downward offset and AA fringe. */
    public static float panelPad() {
        return (float) Math.ceil(PANEL_SIGMA * 4F + PANEL_DROP + 2F);
    }

    /** Includes the FAB's press-release overshoot, since its fade layer encloses the scale. */
    public static float jumpPad() {
        return ShadowTokens.FAB_PAD;
    }

    /** Call before the background, outside its clip. Screen clipping leaves only exposed docked edges. */
    public static void drawPanel(Canvas canvas, float x, float y, float width, float height, float radius) {
        if (width <= 0 || height <= 0) return;
        float r = Math.max(0F, Math.min(radius, Math.min(width, height) / 2F));
        PanelPatch patch = PANEL_PATCHES.get(r);
        if (patch == null) {
            patch = makePanelPatch(r);
            if (PANEL_PATCHES.size() >= MAX_PATCHES) {
                var oldest = PANEL_PATCHES.entrySet().iterator();
                oldest.next().getValue().image().close();
                oldest.remove();
            }
            PANEL_PATCHES.put(r, patch);
        }
        canvas.save();
        try {
            // A translucent panel must show its wallpaper, never its own black shadow mask.
            canvas.clipRRect(RRect.makeXYWH(x, y, width, height, r), ClipMode.DIFFERENCE, true);
            float pad = panelPad();
            canvas.translate(x - pad, y - pad);
            canvas.scale(1F / DENSITY, 1F / DENSITY);
            canvas.drawImageNine(patch.image(), patch.center(),
                    Rect.makeWH((width + pad * 2F) * DENSITY, (height + pad * 2F) * DENSITY),
                    FilterMode.LINEAR, null);
        } finally {
            canvas.restore();
        }
    }

    /** Production FAB face: its shadow cannot overpaint either the face or the glyph. */
    public static void drawJumpSurface(Canvas canvas, float x, float y, float size, float radius, int fillArgb) {
        if (size <= 0) return;
        float r = Math.max(0F, Math.min(radius, size / 2F));
        canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(x, y, size, size, r), ClipMode.DIFFERENCE, true);
            SkiaDraw.drawRoundedShadow(canvas, x, y, size, size, r, ShadowTokens.FAB_BLUR, UiTokens.CHROME_SHADOW);
        } finally {
            canvas.restore();
        }
        SkiaDraw.drawRoundedRect(canvas, x, y, size, size, r, fillArgb);
    }

    private static PanelPatch makePanelPatch(float radius) {
        float pad = panelPad();
        // Three sigma between a corner and the stretch seam makes each edge effectively straight.
        int side = (int) Math.ceil(2F * (radius + PANEL_SIGMA * 3F) + 2F);
        int pixels = (int) ((side + pad * 2F) * DENSITY);
        try (Surface surface = Surface.makeRasterN32Premul(pixels, pixels);
             Paint paint = new Paint().setAntiAlias(true);
             MaskFilter wide = MaskFilter.makeBlur(FilterBlurMode.NORMAL, PANEL_SIGMA);
             MaskFilter contact = MaskFilter.makeBlur(FilterBlurMode.NORMAL, ShadowTokens.CONTACT_SIGMA)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(DENSITY, DENSITY);
            canvas.drawRRect(RRect.makeXYWH(pad, pad + PANEL_DROP, side, side, radius),
                    paint.setColor(0x52000000).setMaskFilter(wide));
            canvas.drawRRect(RRect.makeXYWH(pad, pad + ShadowTokens.CONTACT_DROP, side, side, radius),
                    paint.setColor(0x28000000).setMaskFilter(contact));
            int seam = pixels / 2;
            return new PanelPatch(surface.makeImageSnapshot(), IRect.makeXYWH(seam, seam, 1, 1));
        }
    }

    /** Call alongside the renderer's other image-cache teardown, on its render thread. */
    public static void release() {
        PANEL_PATCHES.values().forEach(p -> p.image().close());
        PANEL_PATCHES.clear();
    }
}
