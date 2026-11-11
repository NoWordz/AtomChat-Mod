package com.atom.chat.nav;

import com.atom.chat.render.Easing;

/** Navigation geometry only. The composite owns the frozen outgoing pixels. */
public final class InterruptiblePageMotion {
    public static final long DURATION_MS = 240;
    private long started = Long.MIN_VALUE / 2;
    private float fromDx, fromScale = 1;
    private boolean zoom;
    private int direction = 1;

    public record Frame(float alpha, float dx, float scale, float exitDx,
                        float exitScale, boolean settled) {
        public float forwardX(float x, float cx) { return cx + (x - cx) * scale + dx; }
        public float forwardY(float y, float cy) { return cy + (y - cy) * scale; }
        public float inverseX(float x, float cx) { return cx + (x - cx - dx) / scale; }
        public float inverseY(float y, float cy) { return cy + (y - cy) / scale; }
    }

    public void retarget(long now, boolean backwards, boolean zoom, boolean enabled) {
        Frame current = sample(now);
        direction = backwards ? -1 : 1;
        this.zoom = zoom;
        fromDx = current.settled ? (zoom ? 0 : direction * 44) : current.dx;
        fromScale = current.settled ? (zoom ? .94f : 1) : current.scale;
        started = enabled ? now : Long.MIN_VALUE / 2;
    }

    public void finish() { started = Long.MIN_VALUE / 2; }

    public Frame sample(long now) {
        float t = Math.max(0, Math.min(1, (now - started) / (float) DURATION_MS));
        float p = Easing.easeOutCubic(t);
        return new Frame(p, fromDx * (1 - p), fromScale + (1 - fromScale) * p,
                zoom ? 0 : -direction * 44 * p, zoom ? 1 - .04f * p : 1, t >= 1);
    }
}
