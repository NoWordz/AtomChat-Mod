package com.atom.chat.ui;

import com.atom.chat.render.CornerGeometry;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import java.util.ArrayList;
import java.util.List;

/** Frame-clock click feedback; coordinates and clipping use the control's hit rectangle. */
public final class Ripple {
    public static final int MAX_WAVES = 4;
    public static final float DURATION_MS = 300;
    private static final class Wave {
        final float x, y;
        float elapsed;
        Wave(float x, float y) { this.x = x; this.y = y; }
    }
    private final List<Wave> waves = new ArrayList<>();

    public void start(float x, float y, boolean enabled) {
        if (!enabled) { waves.clear(); return; }
        if (waves.size() == MAX_WAVES) waves.remove(0);
        waves.add(new Wave(x, y));
    }

    public void update(float dtMs, boolean enabled) {
        if (!enabled) { waves.clear(); return; }
        for (Wave wave : waves) wave.elapsed += Math.max(0, dtMs);
        waves.removeIf(wave -> wave.elapsed >= DURATION_MS);
    }

    public int size() { return waves.size(); }
    public float progress(int index) { return waves.get(index).elapsed / DURATION_MS; }

    public void draw(Canvas canvas, UiLayout.Rect bounds, float corner, int color) {
        if (waves.isEmpty() || bounds.w() <= 0 || bounds.h() <= 0) return;
        CornerGeometry.drawClipped(canvas, RRect.makeXYWH(bounds.x(), bounds.y(), bounds.w(), bounds.h(), corner),
                () -> drawWaves(canvas, bounds, color));
    }

    private void drawWaves(Canvas canvas, UiLayout.Rect bounds, int color) {
        try (Paint paint = new Paint().setAntiAlias(true)) {
            for (Wave wave : waves) {
                float t = wave.elapsed / DURATION_MS;
                float dx = Math.max(Math.abs(wave.x - bounds.x()), Math.abs(bounds.right() - wave.x));
                float dy = Math.max(Math.abs(wave.y - bounds.y()), Math.abs(bounds.bottom() - wave.y));
                float radius = (float) Math.hypot(dx, dy) * (1 - (1 - t) * (1 - t));
                paint.setColor((color & 0xFFFFFF) | (Math.round(35 * (1 - t)) << 24));
                canvas.drawCircle(wave.x, wave.y, radius, paint);
            }
        }
    }
}
