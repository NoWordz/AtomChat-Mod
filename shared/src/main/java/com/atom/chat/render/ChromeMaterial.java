package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
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

    public static void drawDetails(Canvas canvas, UiLayout.Rect bar, float radius) {
        if (bar == null || bar.w() <= 0F || bar.h() <= 0F) return;
        float opacity = (UiTokens.cardFill() >>> 24) / 255F;
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
