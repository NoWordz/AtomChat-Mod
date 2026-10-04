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
     * Panel open/close. Stiffness 220 with damping ratio 0.66.
     *
     * <p>Why zeta 0.66 and not 0.75: the overshoot of a damped spring is
     * exp(-zeta*pi/sqrt(1-zeta^2)). At 0.75 that is ~2.8% — below the brief's
     * own 3% floor and too subtle to read as a settle; 0.66 lands the intended
     * 5-8% band (~6% simulated). Stiffness 220 keeps the travel inside
     * ~0.4s to a visual stop (settle threshold in {@link SpringAnim}).</p>
     */
    public static final float PANEL_STIFFNESS = 220.0F;
    public static final float PANEL_DAMPING_RATIO = 0.66F;

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
     * New-message entrance slide curve: {@code t} in 0..1, peaks at ~104.7%
     * and lands exactly on 1. The fade half of the entrance stays on
     * {@code Easing.easeOutQuad} — opacity never overshoots.
     */
    public static float messageEase(float t) {
        return com.atom.chat.render.Easing.easeOutBack(t, MESSAGE_EASE_C1);
    }
}
