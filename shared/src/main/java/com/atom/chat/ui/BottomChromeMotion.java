package com.atom.chat.ui;

import com.atom.chat.render.Easing;

/** A single retargetable shape shared by composer/tab rendering and hit tests. */
public final class BottomChromeMotion {
    public static final long MORPH_MS = 420;
    public static final long COLLAPSE_MS = 320;
    public record Shape(float x, float y, float w, float h, float alpha) {
        public boolean contains(float px, float py) {
            return w > 0 && h > 0 && px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }
    private Shape from, to;
    private long started;
    private long duration;

    public void target(Shape target, long now, boolean enabled) {
        if (!enabled || to == null) {
            from = to = target;
            duration = 0;
        } else if (!target.equals(to)) {
            from = sample(now);
            to = target;
            duration = target.alpha < 1 || from.alpha < 1 ? COLLAPSE_MS : MORPH_MS;
            started = now;
        }
    }

    public Shape sample(long now) {
        if (to == null) return new Shape(0, 0, 0, 0, 0);
        if (duration == 0 || now - started >= duration) return to;
        float t = Math.max(0, (now - started) / (float) duration);
        // A damped response with a soft launch and ~4% settling overshoot.
        float springTime = (float) Math.pow(t, 1.65);
        float p = (float) (1 - Math.exp(-6.48 * springTime)
                * (Math.cos(6.246 * springTime) + 1.03746 * Math.sin(6.246 * springTime)));
        // A vanishing surface stops at zero width; opacity never overshoots.
        if (to.alpha == 0) p = Easing.easeInOutCubic(t);
        return new Shape(lerp(from.x, to.x, p), lerp(from.y, to.y, p),
                Math.max(0, lerp(from.w, to.w, p)), Math.max(0, lerp(from.h, to.h, p)),
                lerp(from.alpha, to.alpha, Easing.easeInOutCubic(t)));
    }
    /** Layout has its own height animation; carry its delta without starting a second spring. */
    public void follow(Shape target, long now, boolean enabled) {
        if (to == null || !enabled) { target(target, now, false); return; }
        from = new Shape(from.x + target.x - to.x, from.y + target.y - to.y,
                from.w + target.w - to.w, from.h + target.h - to.h, from.alpha);
        to = target;
    }
    public boolean settled(long now) { return duration == 0 || now - started >= duration; }
    public void reset() { from = to = null; duration = 0; }
    private static float lerp(float a, float b, float p) { return a + (b - a) * p; }
}
