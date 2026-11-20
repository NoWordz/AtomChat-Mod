package com.atom.chat.render;


import com.atom.chat.ui.AtomChatAppearance;
import com.atom.lib.design.atomchat.SkiaDraw;
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
    private static final Paint LIGHT_PAINT = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE)
            .setStrokeWidth(UiTokens.CHROME_HAIRLINE).setShader(LIGHT);
    private static final Paint SHADE_PAINT = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE)
            .setStrokeWidth(UiTokens.CHROME_INNER_SHADE).setShader(SHADE);

    private ChromeMaterial() { }

    public static void drawShadow(Canvas canvas, float x, float y, float width, float height, float radius) {
        float opacity = cardOpacity();
        if (opacity <= 0F) return;
        SkiaDraw.drawChromeShadow(canvas, x, y, width, height, radius,
                withAlpha(UiTokens.CHROME_SHADOW_INNER, opacity), withAlpha(UiTokens.CHROME_SHADOW_OUTER, opacity));
    }

    public static void drawBackdrop(Canvas canvas, float x, float y, float width, float height, float radius, float opacity) {
        if (cardOpacity() < 1F) CardBackdrop.draw(canvas, x, y, width, height, radius, opacity);
    }

    private static int withAlpha(int color, float opacity) {
        return Math.round((color >>> 24) * opacity) << 24 | (color & 0xFFFFFF);
    }

    private static float cardOpacity() {
        return (UiTokens.cardFill(AtomChatAppearance.theme()) >>> 24) / 255F;
    }

    public static void drawDetails(Canvas canvas, UiLayout.Rect bar, float radius) {
        if (bar == null || bar.w() <= 0F || bar.h() <= 0F) return;
        float opacity = cardOpacity();
        if (opacity <= 0F) return;
        canvas.save();
        try {
            SkiaDraw.clip(canvas, bar.x(), bar.y(), bar.w(), bar.h(), radius);
            canvas.translate(bar.x(), bar.y());
            float inset = UiTokens.CHROME_HAIRLINE / 2F;
            canvas.drawRRect(RRect.makeXYWH(inset, inset, Math.max(0F, bar.w() - inset * 2F),
                    Math.max(0F, bar.h() - inset * 2F), Math.max(0F, radius - inset)),
                    LIGHT_PAINT.setAlphaf(opacity));
            canvas.translate(0F, bar.h());
            inset = UiTokens.CHROME_INNER_SHADE / 2F;
            canvas.drawRRect(RRect.makeXYWH(inset, -bar.h() + inset,
                    Math.max(0F, bar.w() - inset * 2F), Math.max(0F, bar.h() - inset * 2F),
                    Math.max(0F, radius - inset)), SHADE_PAINT.setAlphaf(opacity));
        } finally {
            canvas.restore();
        }
    }
}
