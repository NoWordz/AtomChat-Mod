package com.niuqu.atomchat_signal;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.api.ui.AppearanceUi.AvatarContext;
import com.atom.chat.api.ui.AppearanceUi.Bounds;
import com.atom.chat.api.ui.AppearanceUi.BubbleContext;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.RRect;

final class SignalAppearance {
    static final String MOD_ID = "atomchat_signal";

    private SignalAppearance() {}

    static void register() {
        // A panel-only bubble demonstrates the host's built-in HUD fallback.
        AppearanceUi.registerBubble(MOD_ID + ":capsule", MOD_ID + ".bubble.capsule",
                SignalAppearance::bubble, null);
        AppearanceUi.registerAvatarFrame(MOD_ID + ":brackets", MOD_ID + ".avatar.brackets",
                SignalAppearance::avatar, SignalAppearance::avatar);
    }

    private static Path bubble(BubbleContext context) {
        Bounds b = context.bounds();
        float radius = Math.min(b.width(), b.height()) / 2;
        try (var path = new PathBuilder()) {
            return path.addRRect(RRect.makeXYWH(b.x(), b.y(), b.width(), b.height(), radius)).detach();
        }
    }

    private static void avatar(Canvas canvas, AvatarContext context) {
        Bounds b = context.frameBounds();
        float edge = Math.min(b.width(), b.height());
        if (edge <= 0) return;

        float stroke = Math.min(2 * context.scale(), edge / 8);
        float arm = edge / 4;
        float left = b.x() + stroke;
        float top = b.y() + stroke;
        float right = b.right() - stroke;
        float bottom = b.bottom() - stroke;
        try (var builder = new PathBuilder();
             var paint = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE)
                     .setStrokeWidth(stroke).setColor(0xFFFFBD56)) {
            builder.moveTo(left, top + arm).lineTo(left, top).lineTo(left + arm, top);
            builder.moveTo(right - arm, top).lineTo(right, top).lineTo(right, top + arm);
            builder.moveTo(right, bottom - arm).lineTo(right, bottom).lineTo(right - arm, bottom);
            builder.moveTo(left + arm, bottom).lineTo(left, bottom).lineTo(left, bottom - arm);
            try (var path = builder.detach()) {
                canvas.drawPath(path, paint);
            }
        }
    }
}
