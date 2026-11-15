package com.atom.chat.ui;

import com.atom.chat.render.Easing;

/** Wall-clock panel motion: no frame-step integration or early distance snap. */
public final class PanelTimeline {
    private float from, target;
    private long started, duration;

    public float value(long now) {
        if (duration == 0 || now - started >= duration) return target;
        float t = Math.max(0F, (now - started) / (float) duration);
        return from + (target - from) * Easing.easeOutCubic(t);
    }

    public void target(float next, long now, boolean enabled) {
        if (!enabled) {
            snapTo(next);
            return;
        }
        if (next == target) return;
        from = value(now);
        target = next;
        started = now;
        duration = (next > from ? UiMotion.PANEL_OPEN_MS : UiMotion.PANEL_CLOSE_MS) * 1_000_000L;
    }

    public void snapTo(float value) {
        from = target = value;
        duration = 0;
    }
}
