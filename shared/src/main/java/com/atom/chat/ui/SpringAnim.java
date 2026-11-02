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
 * <p>Domain note: the default settle thresholds are tuned for the 0..1
 * progress values this UI feeds it. Callers in other domains (the panel
 * spring, the bounce scale spring) pass their own rescaled pair. Do not reuse
 * rescaling the thresholds; the bounce spring in {@link UiSpring} passes its
 * own, much tighter pair for the 0.97..1.03 scale range.</p>
 */
public final class SpringAnim {
    /** Largest wall-clock delta accepted per frame; anything longer is a hitch, not motion. */
    private static final float MAX_DT_MS = 50.0F;
    /** Fixed integration substep. Small enough that semi-implicit Euler adds no visible numerical damping at k=220. */
    private static final float SUBSTEP_MS = 4.0F;
    /**
     * Default settle thresholds: position within 9% of unit travel AND
     * velocity under 2.0 units/s. Callers far from this domain (the panel
     * spring, the bounce scale spring) pass their own rescaled pair through
     * the full constructor instead — see {@code UiSpring.newPanelSpring()}.
     */
    private static final float SETTLE_EPS_POS = 0.09F;
    private static final float SETTLE_EPS_VEL = 2.0F;

    private final float stiffness;
    private final float damping;
    private final float settleEpsPos;
    private final float settleEpsVel;
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
        this(stiffness, dampingRatio, SETTLE_EPS_POS, SETTLE_EPS_VEL);
    }

    /**
     * Full form for domains far from the 0..1 progress range: the settle
     * thresholds must be rescaled to the travelled distance, or the spring
     * either snaps before the motion reads or wanders forever. The bounce
     * scale spring in {@link UiSpring#newBounceSpring()} travels only
     * 0.02-0.06 units and passes its own, much tighter pair.
     */
    public SpringAnim(float stiffness, float dampingRatio, float settleEpsPos, float settleEpsVel) {
        this.stiffness = stiffness;
        this.damping = 2.0F * dampingRatio * (float) Math.sqrt(stiffness);
        this.settleEpsPos = settleEpsPos;
        this.settleEpsVel = settleEpsVel;
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
     * Stall threshold of the self-clocking {@link #tick} entry: a gap longer
     * than a quarter second since the previous tick means the surface was not
     * on screen (screen closed, tab switched, debugger pause) — the motion
     * left in flight is stale, so the next tick lands directly on the target
     * instead of replaying a seconds-old animation at the hitch clamp's pace.
     * Below it, the existing 50ms hitch clamp still owns the frame.
     */
    public static final long TICK_STALL_NANOS = 250_000_000L;

    /**
     * Nanos of the previous {@link #tick} call. A boolean flag, not a zero
     * sentinel, marks "never ticked": a zero sentinel would collide with a
     * caller (or a test) whose first timestamp genuinely is 0, turning every
     * tick into a first paint.
     */
    private long lastTickNanos = 0L;
    private boolean everTicked = false;

    /**
     * Self-clocking entry (adopted from DangoMusic's musicui {@code Spring}):
     * the spring measures the wall clock itself, so a caller only supplies the
     * target each frame — no per-callsite clock bookkeeping like
     * {@code PressScale}'s manual {@code update(dtMs, ...)} feed.
     *
     * <p>Three rules, in order:</p>
     * <ul>
     *   <li>First tick ever: there is no previous frame to animate from, so
     *       the value lands on the target immediately.</li>
     *   <li>Gap over {@link #TICK_STALL_NANOS}: the in-flight motion is stale
     *       — land on the target now (the stall reset). This is what keeps a
     *       reopened surface from playing back an old animation.</li>
     *   <li>Otherwise: advance by the real elapsed time through
     *       {@link #update}, so an interrupting retarget keeps the velocity —
     *       the spring continues from where the motion was, which is why a
     *       target that changes every frame still reads as smooth.</li>
     * </ul>
     *
     * <p>A backwards clock (paused frame, debugger) advances nothing and
     * resets nothing — the motion resumes on the next forward tick. The
     * {@code motionEnabled} gate rides {@link #update}: with decorative motion
     * off every tick lands on the target this frame.</p>
     *
     * @param target     destination value for this frame
     * @param nowNanos   wall clock now; pass explicitly (the
     *                   {@code PanelOpenMotion.frameDtMs} testability shape)
     *                   so the clock is a test input, not a hidden global
     * @return the animated value after this frame's step
     */
    public float tick(float target, long nowNanos, boolean motionEnabled) {
        if (this.target != target) {
            setTarget(target);
        }
        if (!everTicked) {
            everTicked = true;
            snapTo(target);
        } else {
            long dtNanos = nowNanos - lastTickNanos;
            if (dtNanos > TICK_STALL_NANOS) {
                snapTo(target);
            } else if (dtNanos > 0L) {
                update(dtNanos / 1_000_000.0F, motionEnabled);
            }
        }
        lastTickNanos = nowNanos;
        return value;
    }

    /**
     * {@link #tick(float, long, boolean)} against the real wall clock, for
     * callsites that own no clock of their own.
     */
    public float tick(float target, boolean motionEnabled) {
        return tick(target, System.nanoTime(), motionEnabled);
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
        if (Math.abs(value - target) <= settleEpsPos && Math.abs(velocity) <= settleEpsVel) {
            value = target;
            velocity = 0.0F;
            settled = true;
        }
    }
}
