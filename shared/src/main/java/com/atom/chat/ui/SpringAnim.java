package com.atom.chat.ui;

/**
 * Frame-rate independent damped-spring primitive for spatial transitions
 * (panel open/close travel). Rebuilt from the classic mass-spring-damper
 * idea — the same physics other UIs use for "arrive with a slight settle" —
 * but written for AtomChat's progress domain rather than copied from
 * anywhere: semi-implicit Euler on {@code (value, velocity)} with a fixed
 * substep, a wall-clock delta consumed per frame, and a hard snap once the
 * motion is too close to the target to be visible.
 *
 * <p>Division of labour with the duration language in {@link UiMotion}:
 * springs are for <em>spatial displacement</em> where a small overshoot reads
 * as physical mass (panel slide); durations are for opacity, emphasis and
 * anything that must never overshoot (fades, hover tints, scrollbar alpha).
 * See {@link UiSpring} for the tuned tokens.</p>
 *
 * <p>Domain note: the settle thresholds are tuned for the 0..1 progress
 * values this UI feeds it (9% of travel ≈ 1.6px on the 18px panel slide —
 * past the eye's resolution mid-fade). Do not reuse for other domains without
 * rescaling the thresholds; the bounce spring in {@link UiSpring} passes its
 * own, much tighter pair for the 0.97..1.03 scale range.</p>
 */
public final class SpringAnim {
    /** Largest wall-clock delta accepted per frame; anything longer is a hitch, not motion. */
    private static final float MAX_DT_MS = 50.0F;
    /** Fixed integration substep. Small enough that semi-implicit Euler adds no visible numerical damping at k=220. */
    private static final float SUBSTEP_MS = 4.0F;
    /**
     * Settle thresholds: position within 9% of unit travel AND velocity under
     * 2.0 units/s. The pixel meaning is unchanged from the original 0.03/1.0
     * pair — those were picked when the panel slide was 36px (3% ≈ 1px,
     * 1.0 ≈ 36px/s); with the slide halved to 18px the same visual judgement
     * is 9% ≈ 1.6px and 2.0 ≈ 36px/s, and the panel still stops inside the
     * 200ms budget (176ms at 60fps simulated) instead of haunting to 240ms.
     */
    private static final float SETTLE_EPS_POS = 0.09F;
    private static final float SETTLE_EPS_VEL = 2.0F;

    private final float stiffness;
    private final float damping;
    private float value;
    private float velocity;
    private float target;
    private boolean settled = true;

    /**
     * @param stiffness    spring constant k (1/s², unit mass)
     * @param dampingRatio zeta: 1 = critically damped (no overshoot), below 1
     *                     overshoots by exp(-zeta*pi/sqrt(1-zeta^2))
     */
    public SpringAnim(float stiffness, float dampingRatio) {
        this.stiffness = stiffness;
        this.damping = 2.0F * dampingRatio * (float) Math.sqrt(stiffness);
    }

    /** Current animated value. */
    public float value() {
        return value;
    }

    /** Current velocity in units/s (exposed for settle diagnostics in tests). */
    public float velocity() {
        return velocity;
    }

    public float target() {
        return target;
    }

    /** True once the value is close enough to the target that motion is invisible. */
    public boolean isSettled() {
        return settled;
    }

    /** Sets the destination. Re-arms the spring even if it had settled. */
    public void setTarget(float target) {
        if (this.target != target) {
            this.target = target;
            this.settled = false;
        }
    }

    /** Hard-jumps to {@code value} with zero velocity (the motion-off path). */
    public void snapTo(float value) {
        this.value = value;
        this.velocity = 0.0F;
        this.target = value;
        this.settled = true;
    }

    /**
     * Advances the simulation by {@code dtMs} of wall clock. With
     * {@code motionEnabled} false (the {@code AtomChatConfig.animationEnabled}
     * gate) the spring snaps straight to its target — decorative motion never
     * plays, the end state always lands this frame.
     */
    public void update(float dtMs, boolean motionEnabled) {
        if (!motionEnabled) {
            snapTo(target);
            return;
        }
        if (settled) {
            return;
        }
        float dt = Math.max(0.0F, Math.min(dtMs, MAX_DT_MS)) / 1000.0F;
        int substeps = Math.max(1, (int) Math.ceil(dt * 1000.0F / SUBSTEP_MS));
        float h = dt / substeps;
        for (int i = 0; i < substeps; i++) {
            // Semi-implicit Euler: velocity first, then position — stable for
            // k*dt^2 well past our worst-case substep.
            velocity += (-stiffness * (value - target) - damping * velocity) * h;
            value += velocity * h;
        }
        if (Math.abs(value - target) <= SETTLE_EPS_POS && Math.abs(velocity) <= SETTLE_EPS_VEL) {
            value = target;
            velocity = 0.0F;
            settled = true;
        }
    }
}
