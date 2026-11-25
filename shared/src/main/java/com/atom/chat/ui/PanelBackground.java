package com.atom.chat.ui;


import com.atom.chat.render.CornerGeometry;
import com.atom.chat.render.SoftShadow;
import com.atom.chat.render.UiPixelSnap;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;

/**
 * Panel background colour policy, kept pure so it can be unit tested.
 *
 * <p>With no wallpaper set the panel sits on top of the blurred world. When the
 * blur landed, the tint stays translucent so the blur shows through. When the
 * blur was requested but did not land — shaders missing, a GL error, a pass that
 * bailed out — the tint has to be opaque: a 93% dark tint over a broken or black
 * world reads as a full-screen black panel (the 2026-09-11 1.20.1 report), while
 * an opaque panel plainly stays a panel. The translucent tint remains correct
 * when the blur is deliberately off, because then the panel is meant to sit over
 * the live world.</p>
 */
public final class PanelBackground {
    private PanelBackground() {
    }

    public static float outlineInset() {
        var config = com.atom.chat.config.AtomChatConfig.get();
        return config.halfScreenMode ? 0 : UiTokens.s(config.effectivePanelOutlineWidth());
    }

    /** Keep the material's actual stroke width, including fractional device pixels. */
    public static float renderOutlineInset() {
        return outlineInset();
    }

    /** All shell layers use concentric circular corners at their respective insets. */
    static RRect contour(UiLayout.Rect panel, float inset) {
        float halfSide = Math.max(0F, Math.min(panel.w(), panel.h()) / 2F);
        float edge = Math.min(inset, halfSide);
        float radius = Math.min(UiTokens.panelRadius(AtomChatAppearance.theme()), halfSide);
        return RRect.makeXYWH(panel.x() + edge, panel.y() + edge,
                Math.max(0F, panel.w() - 2F * edge), Math.max(0F, panel.h() - 2F * edge),
                Math.max(0F, radius - edge));
    }

    private static RRect backdropContour(UiLayout.Rect panel) {
        // Meet the outline inside its solid band. Carrying the bright tint all the
        // way to the outer AA edge lets it show through the partially covered rim.
        // Translucent outlines deliberately reveal the backplate through the rim.
        boolean opaqueOutline = com.atom.chat.config.AtomChatConfig.get().panelOutlineColor >>> 24 == 255;
        return contour(panel, opaqueOutline ? renderOutlineInset() / 2F : 0F);
    }

    public static void drawShadow(Canvas canvas, UiLayout.Rect panel, boolean settled) {
        SoftShadow.drawPanel(canvas, contour(panel, 0F), settled);
    }

    public static void drawTint(Canvas canvas, UiLayout.Rect panel, int tint) {
        try (var paint = new Paint().setColor(tint).setAntiAlias(CornerGeometry.ANTIALIAS)) {
            canvas.drawRRect(backdropContour(panel), paint);
        }
    }

    public static void clipBackdrop(Canvas canvas, UiLayout.Rect panel) {
        canvas.clipRRect(backdropContour(panel), CornerGeometry.ANTIALIAS);
    }

    public static void clipExterior(Canvas canvas, UiLayout.Rect panel) {
        canvas.clipRRect(contour(panel, 0F), CornerGeometry.ANTIALIAS);
    }

    /** Inset the stroke centre and the corner token to place the rim over the panel tint. */
    public static void drawBezel(io.github.humbleui.skija.Canvas canvas, UiLayout.Rect panel) {
        var config = com.atom.chat.config.AtomChatConfig.get();
        float width = renderOutlineInset();
        if (width <= 0 || panel.w() <= width || panel.h() <= width) return;
        float half = width / 2;
        int base = config.panelOutlineColor;
        int alpha = base & 0xFF000000;
        try (var light = io.github.humbleui.skija.Shader.makeLinearGradient(
                panel.x(), panel.y(), panel.x(), panel.bottom(),
                // The outline is a coloured frame, so reflection must not bleach a dark choice grey.
                new int[]{com.atom.lib.design.atomchat.SkiaDraw.lerpColor(base, alpha | 0x00FFFFFF, .045F),
                        com.atom.lib.design.atomchat.SkiaDraw.lerpColor(base, alpha | 0x00FFFFFF, .015F),
                        base, com.atom.lib.design.atomchat.SkiaDraw.lerpColor(base, alpha, .06F)},
                new float[]{0F, .16F, .48F, 1F});
             var paint = new io.github.humbleui.skija.Paint()
                .setMode(io.github.humbleui.skija.PaintMode.STROKE).setStrokeWidth(width)
                .setAntiAlias(CornerGeometry.ANTIALIAS).setShader(light)) {
            canvas.drawRRect(contour(panel, half), paint);
        }
    }

    /** Clip panel-local overlays to the rendered shell. The caller owns canvas save/restore. */
    public static void clipInterior(io.github.humbleui.skija.Canvas canvas, UiLayout.Rect panel) {
        // Settings details pass unsnapped coordinates, while the shell snaps each outer edge.
        float left = UiPixelSnap.snapToDevice(panel.x());
        float top = UiPixelSnap.snapToDevice(panel.y());
        float right = UiPixelSnap.snapToDevice(panel.right());
        float bottom = UiPixelSnap.snapToDevice(panel.bottom());
        var shell = new UiLayout.Rect(left, top, right - left, bottom - top);
        // Dim the backdrop beneath the fractionally covered inner bezel pixel. The bezel
        // keeps its original geometry and blends over the already dimmed content.
        float density = UiPixelSnap.density();
        float inset = (float) Math.floor(renderOutlineInset() * density) / density;
        canvas.clipRRect(contour(shell, inset), CornerGeometry.ANTIALIAS);
    }

    /** Drops the colour's own alpha; the panel applies its configured opacity instead. */
    public static int opaque(int argb) {
        return 0xFF000000 | (argb & 0x00FFFFFF);
    }

    /** Rewrites a colour's alpha with the configured panel opacity (0..1). */
    public static int withOpacity(int argb, float opacity) {
        float clamped = Math.max(0.0F, Math.min(1.0F, opacity));
        return (Math.round(clamped * 255.0F) << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * The tint for the panel's own background layer when no wallpaper is set.
     *
     * @param blurEnabled whether the config asks for the background blur
     * @param blurDrawn   whether the blur actually reached the framebuffer this frame
     * @param baseColor   configured panel colour (its alpha is ignored)
     * @param opacity     configured panel opacity, 0..1
     */
    public static int tintFor(boolean blurEnabled, boolean blurDrawn, int baseColor, float opacity) {
        if (blurEnabled && blurDrawn) {
            return withOpacity(baseColor, opacity);
        }
        if (blurEnabled) {
            // Requested but not delivered: go opaque so a broken or black world
            // cannot show through as a black panel.
            return opaque(baseColor);
        }
        return withOpacity(baseColor, opacity);
    }
}
