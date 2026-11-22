package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;

/** Low-contrast interior light, reserved for the header, composer and navigation bar. */
public final class ChromeMaterial {
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
        if (requested <= 0F) return 0F;
        return CornerGeometry.fitRadius(requested, maximumRadius(requested), bar.w(), bar.h());
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
        HudShadow.drawSurface(canvas, x, y, width, height, radius, ShadowTokens.CHROME, cardOpacity());
    }

    public static void drawBackdrop(Canvas canvas, float x, float y, float width, float height, float radius, float opacity) {
        if (cardOpacity() < 1F) CardBackdrop.draw(canvas, x, y, width, height, radius, opacity);
    }

    private static float cardOpacity() {
        return (UiTokens.cardFill(AtomChatAppearance.theme()) >>> 24) / 255F;
    }

    public static void drawDetails(Canvas canvas, UiLayout.Rect bar, float radius) {
        if (bar != null) drawDetails(canvas, bar.x(), bar.y(), bar.w(), bar.h(), radius, cardOpacity());
    }

    static void drawDetails(Canvas canvas, float x, float y, float width, float height,
                            float radius, float opacity) {
        if (width <= 0F || height <= 0F || opacity <= 0F) return;
        canvas.save();
        try {
            // A stroke around an inset that turns square can extend beyond the tiny face corner.
            if (radius > 0F && radius < 1F + UiTokens.CHROME_INNER_SHADE / 2F) {
                CornerGeometry.clip(canvas, x, y, width, height, radius);
            }
            canvas.translate(x, y);
            float inset = UiTokens.CHROME_HAIRLINE / 2F;
            CornerGeometry.drawRRect(canvas, RRect.makeXYWH(inset, inset, Math.max(0F, width - inset * 2F),
                    Math.max(0F, height - inset * 2F), Math.max(0F, radius - inset)),
                    LIGHT_PAINT.setAlphaf(opacity));
            canvas.translate(0F, height);
            inset = UiTokens.CHROME_INNER_SHADE / 2F;
            CornerGeometry.drawRRect(canvas, RRect.makeXYWH(inset, -height + inset,
                    Math.max(0F, width - inset * 2F), Math.max(0F, height - inset * 2F),
                    Math.max(0F, radius - inset)), SHADE_PAINT.setAlphaf(opacity));
        } finally {
            canvas.restore();
        }
    }
}
