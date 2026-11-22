package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;

/** Low-contrast interior light, reserved for the header, composer and navigation bar. */
public final class ChromeMaterial {
    /** AtomLib 0.7.0 capped AtomChat 0.9.3's bar radius multiplier at 1.25. */
    private static final float LEGACY_BAR_CORNER_LIMIT = 25F;
    private static final Shader LIGHT = Shader.makeLinearGradient(0F, 0F, 0F, UiTokens.CHROME_DETAIL_DEPTH,
            new int[]{0x16FFFFFF, 0x00FFFFFF}, new float[]{0F, 1F});
    private static final Shader SHADE = Shader.makeLinearGradient(0F, -UiTokens.CHROME_DETAIL_DEPTH, 0F, 0F,
            new int[]{0x00000000, 0x16000000}, new float[]{0F, 1F});
    private static final Paint LIGHT_PAINT = new Paint().setAntiAlias(CornerGeometry.ANTIALIAS).setMode(PaintMode.STROKE)
            .setStrokeWidth(UiTokens.CHROME_HAIRLINE).setShader(LIGHT);
    private static final Paint SHADE_PAINT = new Paint().setAntiAlias(CornerGeometry.ANTIALIAS).setMode(PaintMode.STROKE)
            .setStrokeWidth(UiTokens.CHROME_INNER_SHADE).setShader(SHADE);

    private ChromeMaterial() { }

    public static float cornerRadius(UiLayout.Rect bar, float requested) {
        return UiTokens.insetRadius(legacyBarRadius(requested), 0F, bar.w(), bar.h());
    }

    /** Circular inset controls retain the 0.9.3 radius before the short-side cap. */
    public static float circularInsetCornerRadius(float width, float height, float outerRadius, float inset) {
        return UiTokens.insetRadius(legacyBarRadius(outerRadius), inset, width, height);
    }

    private static float legacyBarRadius(float requested) {
        float slider = AtomChatAppearance.theme().cornerRadius();
        return slider > LEGACY_BAR_CORNER_LIMIT ? requested * (LEGACY_BAR_CORNER_LIMIT / slider) : requested;
    }

    public static float insetCornerRadius(float width, float height, float outerRadius, float inset) {
        // Fit the unclamped inner radius. Capping at half the height first loses the slider range.
        if (outerRadius <= inset) return 0F;
        return CornerGeometry.fitRadius(outerRadius - inset, maximumRadius(outerRadius) - inset, width, height);
    }

    private static float maximumRadius(float requested) {
        float slider = AtomChatAppearance.theme().cornerRadius();
        return requested / slider * AtomChatConfig.MAX_CORNER_RADIUS;
    }

    public static void drawShadow(Canvas canvas, float x, float y, float width, float height, float radius) {
        if (!SoftShadow.enabled() || width <= 0F || height <= 0F) return;
        float opacity = cardOpacity();
        if (opacity <= 0F) return;
        // Reuse the 0.9.3 cached two-pass shadow, including its soft falloff under a translucent face.
        SkiaDraw.drawChromeShadow(canvas, x, y, width, height, radius,
                withAlpha(UiTokens.CHROME_SHADOW_INNER, opacity), withAlpha(UiTokens.CHROME_SHADOW_OUTER, opacity));
    }

    private static int withAlpha(int color, float opacity) {
        return (Math.round((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
    }

    public static void drawBackdrop(Canvas canvas, float x, float y, float width, float height, float radius, float opacity) {
        if (cardOpacity() < 1F) CardBackdrop.draw(canvas, CornerGeometry.circularRect(x, y, width, height, radius), opacity);
    }

    private static float cardOpacity() {
        return (UiTokens.cardFill(AtomChatAppearance.theme()) >>> 24) / 255F;
    }

    public static void drawDetails(Canvas canvas, UiLayout.Rect bar, float radius) {
        if (bar != null) drawDetails(canvas, bar.x(), bar.y(), bar.w(), bar.h(), radius, cardOpacity(), true);
    }

    static void drawDetails(Canvas canvas, float x, float y, float width, float height,
                            float radius, float opacity) {
        drawDetails(canvas, x, y, width, height, radius, opacity, false);
    }

    private static void drawDetails(Canvas canvas, float x, float y, float width, float height,
                                    float radius, float opacity, boolean circular) {
        if (width <= 0F || height <= 0F || opacity <= 0F) return;
        canvas.save();
        try {
            // A stroke around an inset that turns square can extend beyond the tiny face corner.
            if (radius > 0F && radius < 1F + UiTokens.CHROME_INNER_SHADE / 2F) {
                CornerGeometry.clipRRect(canvas, detailOutline(x, y, width, height, radius, circular));
            }
            canvas.translate(x, y);
            float inset = UiTokens.CHROME_HAIRLINE / 2F;
            CornerGeometry.drawRRect(canvas, detailOutline(inset, inset, Math.max(0F, width - inset * 2F),
                    Math.max(0F, height - inset * 2F), Math.max(0F, radius - inset), circular),
                    LIGHT_PAINT.setAlphaf(opacity));
            canvas.translate(0F, height);
            inset = UiTokens.CHROME_INNER_SHADE / 2F;
            CornerGeometry.drawRRect(canvas, detailOutline(inset, -height + inset,
                    Math.max(0F, width - inset * 2F), Math.max(0F, height - inset * 2F),
                    Math.max(0F, radius - inset), circular), SHADE_PAINT.setAlphaf(opacity));
        } finally {
            canvas.restore();
        }
    }

    private static RRect detailOutline(float x, float y, float width, float height, float radius, boolean circular) {
        return circular ? CornerGeometry.circularRect(x, y, width, height, radius)
                : RRect.makeXYWH(x, y, width, height, radius);
    }
}
