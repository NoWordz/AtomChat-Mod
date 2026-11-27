package com.niuqu.atomchat_harbor;

import com.atom.chat.api.ui.AppearanceUi.Bounds;
import com.atom.chat.api.ui.AppearanceUi.WallpaperFrame;
import com.atom.chat.api.ui.AppearanceUi.WallpaperSession;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.Rect;

/** A procedural harbor drawn from phase and bounds alone. Each frame owns its temporary Skija objects. */
final class HarborWallpaper implements WallpaperSession {
    @Override
    public void draw(Canvas canvas, WallpaperFrame frame) {
        Bounds b = frame.bounds();
        float unit = Math.min(b.width(), b.height());
        if (unit <= 0) return;

        // Loop on the host's elapsed time so a paused sample keeps the same picture.
        double phase = 2 * Math.PI * (frame.timeNanos() % 12_000_000_000L) / 12_000_000_000L;
        int starCount = switch (frame.quality()) {
            case PERFORMANCE -> 12;
            case BALANCED -> 24;
            case ULTRA -> 40;
        };
        try (var paint = new Paint().setAntiAlias(true)) {
            canvas.drawRect(Rect.makeXYWH(b.x(), b.y(), b.width(), b.height()), paint.setColor(0xFF0A2339));
            for (int i = 0; i < starCount; i++) {
                float x = b.x() + b.width() * (0.03f + 0.94f * ((i * 37 % 101) / 101f));
                float y = b.y() + b.height() * (0.04f + 0.47f * ((i * 53 % 103) / 103f));
                int alpha = (int) (146 + 74 * Math.sin(phase * 2 + i));
                canvas.drawCircle(x, y, unit * (i % 5 == 0 ? 0.0045f : 0.0028f),
                        paint.setColor(alpha << 24 | 0xB6F1F0));
            }
            float moonX = b.x() + b.width() * (0.71f + 0.10f * (float) Math.sin(phase));
            float moonY = b.y() + b.height() * (0.25f + 0.04f * (float) Math.cos(phase));
            canvas.drawCircle(moonX, moonY, unit * 0.079f, paint.setColor(0x2943DACA));
            canvas.drawCircle(moonX, moonY, unit * 0.055f, paint.setColor(0xFF9EEDDC));

            wave(canvas, paint, b, 0.62f, 0.044f, phase, 0xFF164B64);
            wave(canvas, paint, b, 0.74f, 0.060f, -phase + 1.2, 0xFF186478);
            wave(canvas, paint, b, 0.87f, 0.078f, phase + 2.7, 0xFF238589);
        }
    }

    private static void wave(Canvas canvas, Paint paint, Bounds b, float horizon, float amplitude,
                             double phase, int color) {
        int samples = 32;
        try (var builder = new PathBuilder()) {
            float y = b.y() + b.height() * (horizon + amplitude * (float) Math.sin(phase));
            builder.moveTo(b.x(), y);
            for (int i = 1; i <= samples; i++) {
                float t = (float) i / samples;
                y = b.y() + b.height() * (horizon + amplitude * (float) Math.sin(phase + t * 4 * Math.PI));
                builder.lineTo(b.x() + b.width() * t, y);
            }
            builder.lineTo(b.right(), b.bottom()).lineTo(b.x(), b.bottom()).closePath();
            try (var path = builder.detach()) {
                canvas.drawPath(path, paint.setColor(color));
            }
        }
    }
}
