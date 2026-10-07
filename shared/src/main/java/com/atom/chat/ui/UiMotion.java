package com.atom.chat.ui;

/**
 * Single source of truth for transition timing.
 *
 * Two rules every transition must obey:
 *
 * 1. A duration here is the *total* time to reach the target, not a time
 *    constant. The old per-frame formula {@code v += (target - v) * dt / D}
 *    decays asymptotically, so a 120ms hover actually stayed visible for
 *    ~550ms and never reached 0 — that is the sticky/laggy feel we are fixing.
 * 2. Transitions snap to the target on the frame that gets there, so state
 *    (hover highlight, scrollbar alpha, popup fade) always lands exactly on
 *    0 or 1 instead of hovering just above it forever.
 *
 * <p>Division of labour with the spring language ({@link UiSpring} /
 * {@link SpringAnim}): durations drive opacity, emphasis and every value that
 * must never overshoot; springs drive spatial displacement of small surfaces
 * (hover/press bounce), where a small physical overshoot reads as mass. The
 * panel open/close briefly lived on the spring language and came back: the
 * exponential decay below lands inside the same visual window without the
 * spring's velocity bookkeeping (see {@link #PANEL_OPEN_TAU_MS}).</p>
 */
public final class UiMotion {
    /**
     * New message entry. Deliberately the slowest transition in the UI: this is
     * a content reveal, not a response to input, and an opacity ramp under
     * ~200ms is over before the eye registers it as a fade. The slide half of
     * the entrance rides {@link UiSpring#messageEase} on this same timeline.
     */
    public static final long MESSAGE_MS = 220;
    /** Snap back to the bottom after sending. */
    public static final long SCROLL_SNAP_MS = 110;
    /** Wheel scroll glide. */
    public static final long SCROLL_WHEEL_MS = 180;
    /** Button hover / press tint. */
    public static final long HOVER_MS = 90;
    /** Scrollbar fade in/out. */
    public static final long SCROLLBAR_FADE_MS = 140;
    /** Scrollbar hover emphasis. */
    public static final long SCROLLBAR_EMPHASIS_MS = 100;
    /** Emoji panel / context menu pop. */
    public static final long POPUP_MS = 110;
    /** Tab content push + indicator travel when switching emoji tabs. */
    public static final long TAB_MS = 200;
    /**
     * Retired tween duration of the page push/pop slide. The {@code SLIDE}
     * style no longer tweens — it rides {@link #PAGE_SLIDE_TAU_MS} — so this
     * value now only documents the visual window the exponential tau was
     * tuned against (and the test pins it as that reference).
     */
    public static final long PAGE_NAV_MS = 140;
    /**
     * Time constant of the ZOOM page-nav style (Melodify's M3 step): each of
     * the two serial phases — the leaving page shrinking out, the arriving
     * page settling in — closes its remaining distance exponentially at this
     * tau, so the motion starts at full speed and never hard-stops. 50ms per
     * phase reads as one continuous settle (~170ms to visually land).
     */
    public static final float PAGE_NAV_TAU_MS = 50.0F;
    /**
     * Time constant of the SLIDE page-nav style: the horizontal push covers
     * roughly the old 140ms eased tween's visible window (70ms ≈ where the
     * cubic read peaked) but rides the same exponential family as the zoom,
     * so the motion starts at full speed and decays — no acceleration notch,
     * no hard stop.
     */
    public static final float PAGE_SLIDE_TAU_MS = 70.0F;
    /**
     * Time constant of the panel open/close progress (the shell screen's
     * {@code panelProgress}). Shares the SLIDE page-nav tau: at 70ms the
     * panel visually lands in ~200ms — slower than the old critically
     * damped spring's ~90ms stop, which read as a slam — and the decay
     * tail doubles as the fade, so shape and alpha resolve together. tau 0
     * (decorative motion off) snaps straight to the target.
     */
    public static final float PANEL_OPEN_TAU_MS = 70.0F;
    /**
     * Time constant of the panel <em>close</em> progress — deliberately
     * shorter than the open. Opening plays its exponential tail over content
     * that has already settled, so the last few percent are imperceptible;
     * closing puts the whole decay on stage — the panel visibly shrinks and
     * fades every frame — and the long tail makes the shutdown drag. At 40ms
     * the shell screen cuts itself once {@code panelProgress} drops under
     * 0.06, landing in roughly half the open's visual window.
     */
    public static final float PANEL_CLOSE_TAU_MS = 40.0F;
    /** Input bar growing/shrinking by one line. */
    public static final long INPUT_GROW_MS = 110;
    /**
     * Toggle-switch knob travel. Sits between hover (90) and tab push (200):
     * this is a direct-manipulation control, so it has to read as a slide
     * rather than a jump while still landing before the finger lifts.
     */
    public static final long TOGGLE_MS = 140;

    private UiMotion() {
    }

    /**
     * Frame-rate-independent exponential approach (Melodify M3.Motion.step):
     * closes a {@code 1 - exp(-dt/tau)} fraction of the remaining distance
     * per frame, so the speed starts at its peak and decays forever — no
     * acceleration notch, no hard stop. {@code tauMs <= 0} snaps to the
     * target. Converges by snapping once within 0.001.
     */
    public static float expApproach(float value, float target, float dtMs, float tauMs) {
        if (tauMs <= 0.0F) {
            return target;
        }
        if (dtMs <= 0.0F) {
            return value;
        }
        if (dtMs > 50.0F) {
            dtMs = 50.0F;
        }
        float k = 1.0F - (float) Math.exp(-dtMs / tauMs);
        float next = value + (target - value) * k;
        return Math.abs(target - next) < 0.001F ? target : next;
    }

    /**
     * Moves {@code value} toward {@code target} by the fraction of
     * {@code durationMs} that {@code elapsedMs} covers, and returns
     * {@code target} once the remaining distance fits in this frame's step.
     *
     * <p>Frame-rate independent: a 60fps and a 200fps client clear the same
     * highlight in the same wall-clock time, and both end at exactly 0.</p>
     */
    public static float approach(float value, float target, float elapsedMs, long durationMs) {
        if (durationMs <= 0L) {
            return target;
        }
        float step = elapsedMs / (float) durationMs;
        if (step >= 1.0F) {
            return target;
        }
        float diff = target - value;
        if (Math.abs(diff) <= step) {
            return target;
        }
        return value + Math.signum(diff) * step;
    }
}
