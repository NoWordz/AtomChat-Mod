package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the self-clocking {@code tick} entry of the spring language
 * (the DangoMusic musicui adoption): the spring owns the wall clock, so a
 * caller only supplies the target each frame. Three properties are pinned:
 *
 * <ul>
 *   <li><b>First paint and stall reset</b> — the first tick ever, and any
 *       gap longer than a quarter second (screen closed, tab switched,
 *       debugger pause), lands directly on the target. Without the reset the
 *       surface replays a stale, seconds-old animation at the hitch clamp's
 *       pace when it reappears.</li>
 *   <li><b>Interruptible retarget</b> — changing the target mid-flight keeps
 *       the velocity: the next step continues from where the motion was, with
 *       no discontinuous jump. This is what makes the spring read as smooth
 *       when a drag or a hover chain retargets it every frame.</li>
 *   <li><b>Substep stability</b> — a long single frame below the stall
 *       threshold must not explode the integrator, and the spring still lands
 *       exactly on its target afterwards.</li>
 * </ul>
 *
 * <p>Time is passed explicitly in nanos (the same testability shape
 * {@code PanelOpenMotion.frameDtMs} uses) so no test depends on real wall
 * clock. The Dango reference tune k=380, d=24 (slightly underdamped, the
 * iOS-switch feel) drives the retarget case.</p>
 */
class SpringAnimTickTest {

    /** The Dango reference tune: k=380 with damping constant d=24 (zeta ~0.62). */
    private static SpringAnim dangoTuned() {
        float zeta = 24.0F / (2.0F * (float) Math.sqrt(380.0F));
        return new SpringAnim(380.0F, zeta);
    }

    private static final long FRAME = 16_000_000L; // 16ms in nanos

    @Test
    void firstTickSnapsToTarget() {
        SpringAnim s = dangoTuned();
        // Never ticked before: there is no "previous frame" to animate from,
        // so the value must be the target on the very first paint.
        assertEquals(1.0F, s.tick(1.0F, 1_000L, true), 0.0F, "first paint lands on the target");
        assertTrue(s.isSettled());
        assertEquals(0.0F, s.velocity(), 0.0F);
    }

    @Test
    void stallResetLandsInsteadOfReplayingStaleMotion() {
        SpringAnim s = dangoTuned();
        long t = 0;
        s.tick(0.0F, t, true); // anchor the clock at the resting position
        t += FRAME;
        s.tick(1.0F, t, true);
        // Drive to mid-flight.
        int guard = 0;
        while ((s.value() < 0.05F || s.value() > 0.95F) && guard++ < 60) {
            t += FRAME;
            s.tick(1.0F, t, true);
        }
        assertTrue(guard <= 60, "spring should reach mid-flight within 60 frames");
        float mid = s.value();
        assertTrue(mid > 0.0F && mid < 1.0F, "precondition: actually mid-flight, got " + mid);

        // A quarter-second stall (screen closed / tab switched): the next tick
        // must land directly, not resume a seconds-old animation.
        long stalled = t + 1_000_000_000L;
        assertEquals(1.0F, s.tick(1.0F, stalled, true), 0.0F,
                "after a stall the spring lands on the target this frame");
        assertTrue(s.isSettled());
        assertEquals(0.0F, s.velocity(), 0.0F, "stall reset kills the stale velocity");
    }

    @Test
    void retargetMidFlightKeepsVelocityAndNeverJumps() {
        SpringAnim s = dangoTuned();
        long t = 0;
        s.tick(0.0F, t, true);
        t += FRAME;
        s.tick(1.0F, t, true);

        float prev = s.value();
        float prevV = 0.0F;
        float maxDeltaBefore = 0.0F;
        boolean retargeted = false;
        float maxDeltaAfter = 0.0F;
        float firstVAfterRetarget = -1.0F;

        for (int i = 0; i < 400; i++) {
            t += FRAME;
            float target = retargeted ? 0.5F : 1.0F;
            float before = s.value();
            float v = s.velocity();
            s.tick(target, t, true);
            float delta = Math.abs(s.value() - before);
            if (!retargeted) {
                maxDeltaBefore = Math.max(maxDeltaBefore, delta);
                prevV = v;
                // Retarget once mid-flight, while clearly moving toward 1.
                if (s.value() > 0.25F) {
                    retargeted = true;
                    // Velocity continuity: the very next step must be bounded
                    // by the physics that was already in play (a snap-to or a
                    // velocity reset would break this).
                    assertTrue(delta <= Math.abs(prevV) * (FRAME / 1e9F) * 1.25F + 1e-4F,
                            "retarget step must continue the existing motion, jumped " + delta
                                    + " with velocity " + prevV);
                    firstVAfterRetarget = s.velocity();
                }
            } else {
                maxDeltaAfter = Math.max(maxDeltaAfter, delta);
            }
        }
        assertTrue(retargeted, "precondition: reached the retarget point");
        assertTrue(firstVAfterRetarget > 1.0F,
                "retarget must keep the spring moving (velocity not reset), got " + firstVAfterRetarget);
        // No discontinuity anywhere after the retarget: every step stays inside
        // what the pre-retarget physics allowed (1.5x headroom for the new
        // target pulling harder).
        assertTrue(maxDeltaAfter <= maxDeltaBefore * 1.5F + 1e-3F,
                "post-retarget steps must stay continuous, max step " + maxDeltaAfter
                        + " vs pre-retarget " + maxDeltaBefore);
        assertEquals(0.5F, s.value(), 0.0F, "retargeted spring lands exactly on the new target");
        assertTrue(s.isSettled());
    }

    @Test
    void longFrameBelowStallStaysStableAndLands() {
        SpringAnim s = dangoTuned();
        long t = 0;
        s.tick(0.0F, t, true);
        t += FRAME;
        s.tick(1.0F, t, true);
        t += FRAME;
        s.tick(1.0F, t, true);
        // One 200ms frame: below the 250ms stall, so it is motion (clamped by
        // the integrator), not a reset — must stay finite and keep converging.
        t += 200_000_000L;
        float v = s.tick(1.0F, t, true);
        assertTrue(Float.isFinite(v) && v > -1.0F && v < 2.0F, "long frame must not explode");
        for (int i = 0; i < 90; i++) {
            t += FRAME;
            s.tick(1.0F, t, true);
        }
        assertEquals(1.0F, s.value(), 0.0F, "spring still lands exactly after a long frame");
        assertTrue(s.isSettled());
    }

    @Test
    void backwardsClockDoesNotMoveOrReset() {
        SpringAnim s = dangoTuned();
        long t = 0;
        s.tick(0.0F, t, true);
        t += FRAME;
        s.tick(1.0F, t, true);
        t += FRAME;
        s.tick(1.0F, t, true);
        float before = s.value();
        float vBefore = s.velocity();
        // A backwards clock (paused frame, debugger): no motion, no snap — the
        // animation resumes on the next forward tick.
        s.tick(1.0F, t - 1_000_000L, true);
        assertEquals(before, s.value(), 0.0F, "backwards clock advances nothing");
        assertEquals(vBefore, s.velocity(), 1e-6F, "backwards clock keeps the state");
        t += FRAME;
        s.tick(1.0F, t, true);
        assertTrue(s.value() >= before, "motion resumes on the next forward tick");
    }

    @Test
    void motionOffSnapsThroughTick() {
        SpringAnim s = dangoTuned();
        long t = 0;
        s.tick(0.0F, t, true);
        t += FRAME;
        assertEquals(1.0F, s.tick(1.0F, t, false), 0.0F,
                "decorative motion off lands on the target this frame");
        assertTrue(s.isSettled());
    }

    @Test
    void tickSettleIsFrameRateIndependent() {
        assertEquals(1.0F, driveToSettle(dangoTuned(), 16_000_000L), 0.0F, "60fps lands exactly");
        assertEquals(1.0F, driveToSettle(dangoTuned(), 7_000_000L), 0.0F, "144fps lands exactly");
        assertEquals(1.0F, driveToSettle(dangoTuned(), 33_000_000L), 0.0F, "30fps lands exactly");
    }

    /** Ticks 0->1 at the given frame pacing and returns the value once settled. */
    private static float driveToSettle(SpringAnim s, long frameNanos) {
        long t = 0;
        s.tick(0.0F, t, true);
        for (int i = 0; i < 400; i++) {
            t += frameNanos;
            s.tick(1.0F, t, true);
            if (s.isSettled()) {
                return s.value();
            }
        }
        return s.value();
    }
}
