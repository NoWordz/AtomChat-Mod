package com.atom.chat.ui;

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

    /** Clip panel-local overlays to the same inner contour as the wallpaper.
     * The caller owns the enclosing canvas save/restore. */
    public static void clipInterior(io.github.humbleui.skija.Canvas canvas, UiLayout.Rect panel) {
        float inset = UiTokens.PANEL_BEZEL_WIDTH;
        com.atom.chat.render.SkiaDraw.clip(canvas,
                panel.x() + inset, panel.y() + inset,
                Math.max(0F, panel.w() - 2F * inset), Math.max(0F, panel.h() - 2F * inset),
                Math.max(0F, UiTokens.panelRadius() - inset));
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
