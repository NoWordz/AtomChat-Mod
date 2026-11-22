package com.atom.chat.render;

import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;

/** Shared native-resolution outline painter, confined to the render thread. */
public final class OutlineIcons {
    private static final Paint STROKE = new Paint().setAntiAlias(CornerGeometry.ANTIALIAS).setMode(PaintMode.STROKE)
            .setStrokeCap(PaintStrokeCap.ROUND).setStrokeJoin(PaintStrokeJoin.ROUND);

    private OutlineIcons() { }

    public static void draw(Canvas canvas, Path icon, float centerX, float centerY, float size, int color) {
        var bounds = OutlineIconBounds.of(icon);
        if (bounds == null || bounds.isEmpty()) return;
        float fit = size / Math.max(bounds.getWidth(), bounds.getHeight());
        canvas.save();
        try {
            canvas.translate(centerX - (bounds.getLeft() + bounds.getRight()) / 2F * fit,
                    centerY - (bounds.getTop() + bounds.getBottom()) / 2F * fit);
            canvas.scale(fit, fit);
            canvas.drawPath(icon, STROKE.setColor(color).setStrokeWidth(UiTokens.iconStroke(size) / fit));
        } finally {
            canvas.restore();
        }
    }
}
