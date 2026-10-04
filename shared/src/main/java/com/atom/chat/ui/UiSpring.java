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
     * Panel open/close. Stiffness 1200 with damping ratio 1.0 (critically
     * damped).
     *
     * <p>Panel open/close is spatial fast-travel: the brief is "get out of the
     * way fast, arrive dead" — so critical damping, no overshoot at all
     * (exp(-zeta*pi/sqrt(1-zeta^2)) is exactly 0% at zeta 1). The earlier
     * 520/0.98 tune landed in ~130ms and still read as a beat too slow for an
     * open that should feel near-instant; 1200 puts the visual stop inside
     * ~0.09s at 60fps (simulated 80ms with the rescaled thresholds below,
     * 84ms at 144fps).</p>
     *
     * <p>Bounce stays a language for small things only: hover/press scaling
     * ({@link #newBounceSpring()}) and the message entrance
     * ({@link #messageEase(float)}).</p>
     */
    public static final float PANEL_STIFFNESS = 1200.0F;
    public static final float PANEL_DAMPING_RATIO = 1.0F;

    /**
     * Overshoot constant of the message-entrance ease. Standard easeOutBack
     * (1.70158) peaks at ~10%, which is a bounce for a content reveal; the
     * peak overshoot of the curve is (4/27)*c1^3/(c1+1)^2, and c1 1.15 lands
     * at ~4.9% — inside the "restrained, at most 5%" budget.
     */
    public static final float MESSAGE_EASE_C1 = 1.15F;

    private UiSpring() {
    }

    /**
     * A fresh panel spring at value 0. Callers drive it with setTarget/update.
     *
     * <p>Sets its own settle thresholds, rescaled to the faster spring and the
     * 10px slide: position within 25% of unit travel (2.5px on the panel
     * slide, masked by the open fade) and velocity under 6.0 units/s. With the
     * rescaled pair the spring reports settled at ~80ms/60fps; the looser
     * thresholds are what buy the sub-90ms stop without a visible jump.</p>
     */
    public static SpringAnim newPanelSpring() {
        return new SpringAnim(PANEL_STIFFNESS, PANEL_DAMPING_RATIO, 0.25F, 6.0F);
    }

    /**
     * Hover/press scaling constants — the bounce language of this UI.
     * Stiffness 400 with damping ratio 0.50: the overshoot term
     * exp(-zeta*pi/sqrt(1-zeta^2)) lands near 16% of the travelled distance,
     * so a control released from 0.92 springs back through its 1.08 hover
     * target with a ~2.5%-of-scale overshoot — a bounce the eye catches at
     * arm's length (the earlier 0.58 tune, ~11% of a 0.06 move, snapped
     * itself flat against the settle thresholds and read as no bounce at
     * all). Bounce lives ONLY on hover/press scaling and the message
     * entrance; large surfaces (the panel) travel critically damped (see
     * {@link #PANEL_STIFFNESS}).
     *
     * <p>The scale domain is 0.92..1.08, far narrower than the 0..1 progress
     * range {@link SpringAnim}'s default settle thresholds assume, so
     * {@link #newBounceSpring()} passes thresholds rescaled to the travel:
     * 0.0015 position (~2% of an 8% move — tight enough to let the 16%
     * overshoot peak through), 0.3 velocity. Simulated at 60fps the visible
     * bounce lands inside ~150ms and the sub-0.5% tail is done in ~290ms,
     * frame-rate independent.</p>
     */
    public static final float BOUNCE_STIFFNESS = 400.0F;
    public static final float BOUNCE_DAMPING_RATIO = 0.50F;

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
