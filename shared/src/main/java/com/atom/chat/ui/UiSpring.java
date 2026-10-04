package com.atom.chat.ui;

/**
 * Tuned spring/ease tokens for the UI's <em>spatial</em> motion language.
 *
 * <p>Two motion languages live side by side, and which one a transition uses
 * is decided by what the transition moves:</p>
 * <ul>
 *   <li><b>Springs ({@link SpringAnim}, tokens here)</b> — spatial
 *       displacement of large surfaces. Physics-shaped: a critically damped
 *       arrival reads as "slammed into place", an uncontrolled one as a toy.
 *       The small overshoot below is the whole point: mass with restraint.</li>
 *   <li><b>Durations ({@link UiMotion})</b> — opacity, emphasis and anything
 *       that must never overshoot: fades, hover tints, scrollbar alpha,
 *       toggle knobs. A value >1 or <0 is meaningless there, so a spring
 *       would be the wrong tool, not a nicer one.</li>
 * </ul>
 *
 * <p>Every spring in this UI is created through the factories here so the
 * feel stays reviewable in one place. Springs are gated by
 * {@link Animations#enabled()} at their update call: with decorative motion
 * off they snap to the target (see {@link SpringAnim#update}).</p>
 */
public final class UiSpring {
    /**
     * Panel open/close. Stiffness 520 with damping ratio 0.98.
     *
     * <p>Panel open/close is spatial fast-travel: the brief is "get out of the
     * way fast, arrive dead" — so near-critical damping. The overshoot of a
     * damped spring is exp(-zeta*pi/sqrt(1-zeta^2)); at 0.98 that is ~0.06%,
     * numerically zero: the panel lands and stays, no visible bounce. The
     * earlier 220/0.66 tune (~6% overshoot, ~0.4s) read as sluggish and
     * springy for a surface this large. Stiffness 520 puts the visual stop
     * inside ~0.13s (settle threshold in {@link SpringAnim}).</p>
     *
     * <p>Bounce stays a language for small things only: hover/press scaling
     * ({@link #newBounceSpring()}) and the message entrance
     * ({@link #messageEase(float)}).</p>
     */
    public static final float PANEL_STIFFNESS = 520.0F;
    public static final float PANEL_DAMPING_RATIO = 0.98F;

    /**
     * Overshoot constant of the message-entrance ease. Standard easeOutBack
     * (1.70158) peaks at ~10%, which is a bounce for a content reveal; the
     * peak overshoot of the curve is (4/27)*c1^3/(c1+1)^2, and c1 1.15 lands
     * at ~4.9% — inside the "restrained, at most 5%" budget.
     */
    public static final float MESSAGE_EASE_C1 = 1.15F;

    private UiSpring() {
    }

    /** A fresh panel spring at value 0. Callers drive it with setTarget/update. */
    public static SpringAnim newPanelSpring() {
        return new SpringAnim(PANEL_STIFFNESS, PANEL_DAMPING_RATIO);
    }

    /**
     * Hover/press scaling constants — the bounce language of this UI.
     * Stiffness 400 with damping ratio 0.58: the overshoot term
     * exp(-zeta*pi/sqrt(1-zeta^2)) lands near 11% of the travelled distance,
     * so a control pushed to 0.97 springs back through 1.00 with a soft
     * overshoot instead of halting — brisk but visible. Bounce lives ONLY on
     * hover/press scaling and the message entrance; large surfaces (the
     * panel) travel near-critically damped (see {@link #PANEL_STIFFNESS}).
     *
     * <p>The scale domain is 0.97..1.03, far narrower than the 0..1 progress
     * range {@link SpringAnim}'s default settle thresholds assume (9% of a
     * 0.03 move is invisible), so {@link #newBounceSpring()} passes
     * thresholds rescaled to the travel: 0.0015 position (5% of a 3% move),
     * 0.3 velocity. Simulated at 60fps that settles in ~130ms, frame-rate
     * independent.</p>
     */
    public static final float BOUNCE_STIFFNESS = 400.0F;
    public static final float BOUNCE_DAMPING_RATIO = 0.58F;

    /** A fresh hover/press scale spring at value 1 (no scale). */
    public static SpringAnim newBounceSpring() {
        return new SpringAnim(BOUNCE_STIFFNESS, BOUNCE_DAMPING_RATIO, 0.0015F, 0.3F);
    }

    /**
     * New-message entrance slide curve: {@code t} in 0..1, peaks at ~104.7%
     * and lands exactly on 1. The fade half of the entrance stays on
     * {@code Easing.easeOutQuad} — opacity never overshoots.
     */
    public static float messageEase(float t) {
        return com.atom.chat.render.Easing.easeOutBack(t, MESSAGE_EASE_C1);
    }
}
