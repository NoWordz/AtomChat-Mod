package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the spring motion language. The panel spring must arrive fast
 * (a surface this large reads as lag if it wanders) and land without a
 * visible bounce — the open/close brief is spatial fast-travel, so the PANEL
 * tokens are near-critically damped. Bounce belongs to hover/press scaling
 * ({@code UiSpring#newBounceSpring}), and everything must collapse to the
 * end state when decorative motion is off.
 */
class SpringAnimTest {

    /** Drives the spring from 0 to 1 and returns [peak value, settle time in ms]. */
    private static Object[] runOpen(SpringAnim spring, long frameMs, int frames) {
        spring.snapTo(0.0F);
        spring.setTarget(1.0F);
        float peak = 0.0F;
        long settleMs = -1;
        long t = 0;
        for (int i = 0; i < frames; i++, t += frameMs) {
            spring.update(frameMs, true);
            peak = Math.max(peak, spring.value());
            if (settleMs < 0 && spring.isSettled()) {
                settleMs = t + frameMs;
            }
        }
        return new Object[]{peak, settleMs};
    }

    @Test
    void panelSpringSettlesWithinBudget() {
        Object[] r = runOpen(UiSpring.newPanelSpring(), 16L, 60);
        long settleMs = (Long) r[1];
        assertTrue(settleMs >= 0 && settleMs <= 120,
                "panel spring must reach a visual stop within 120ms, took " + settleMs + "ms");
        // After settling the value must be exactly the target (snap, no residue).
        SpringAnim s = UiSpring.newPanelSpring();
        s.snapTo(0.0F);
        s.setTarget(1.0F);
        for (int i = 0; i < 120; i++) {
            s.update(16L, true);
        }
        assertEquals(1.0F, s.value(), 0.0F, "spring snaps exactly onto the target");
        assertTrue(s.isSettled());
    }

    @Test
    void panelSpringLandsWithoutBounce() {
        Object[] r = runOpen(UiSpring.newPanelSpring(), 16L, 60);
        float overshoot = (Float) r[0] - 1.0F;
        long settleMs = (Long) r[1];
        assertTrue(overshoot <= 0.01F,
                "panel overshoot must be invisible (<=1%), got " + (overshoot * 100) + "%");
        assertTrue(settleMs >= 0 && settleMs <= 120,
                "critically damped panel must settle within 120ms, took " + settleMs + "ms");
    }

    @Test
    void panelSpringIsFrameRateIndependent() {
        long at60 = (Long) runOpen(UiSpring.newPanelSpring(), 16L, 60)[1];
        long at144 = (Long) runOpen(UiSpring.newPanelSpring(), 7L, 140)[1];
        long at30 = (Long) runOpen(UiSpring.newPanelSpring(), 33L, 30)[1];
        for (long t : new long[]{at144, at30}) {
            assertTrue(t >= 0 && Math.abs(t - at60) <= 80,
                    "settle time should not depend on frame pacing: 60fps=" + at60 + "ms, got " + t + "ms");
        }
    }

    @Test
    void panelSpringSurvivesFrameSpike() {
        SpringAnim s = UiSpring.newPanelSpring();
        s.snapTo(0.0F);
        s.setTarget(1.0F);
        // one long hitch: the clamp (<=50ms) must keep the simulation stable
        s.update(5000.0F, true);
        assertTrue(Float.isFinite(s.value()) && s.value() > -1.0F && s.value() < 2.0F,
                "a frame hitch must not explode the simulation");
        for (int i = 0; i < 60; i++) {
            s.update(16L, true);
        }
        assertEquals(1.0F, s.value(), 0.0F, "spring still lands after a hitch");
    }

    @Test
    void motionDisabledSnapsToTarget() {
        SpringAnim s = UiSpring.newPanelSpring();
        s.snapTo(0.0F);
        s.setTarget(1.0F);
        s.update(16.0F, false);
        assertEquals(1.0F, s.value(), 0.0F, "motion off lands on the target in one frame");
        assertTrue(s.isSettled());
        assertEquals(0.0F, s.velocity(), 0.0F);
        // and it stays there
        for (int i = 0; i < 10; i++) {
            s.update(16.0F, false);
        }
        assertEquals(1.0F, s.value(), 0.0F);
    }

    @Test
    void closePathMirrorsOpenPath() {
        SpringAnim s = UiSpring.newPanelSpring();
        s.snapTo(1.0F);
        s.setTarget(0.0F);
        float min = 1.0F;
        long settleMs = -1;
        long t = 0;
        for (int i = 0; i < 60 && settleMs < 0; i++, t += 16L) {
            s.update(16L, true);
            min = Math.min(min, s.value());
            if (s.isSettled()) {
                settleMs = t + 16L;
            }
        }
        assertTrue(min >= -0.015F,
                "closing must not visibly bounce past the closed edge, got " + (min * 100) + "%");
        assertTrue(settleMs >= 0 && settleMs <= 120, "close path settles within the same no-bounce budget");
        assertEquals(0.0F, s.value(), 0.0F, "close path snaps exactly onto 0");
    }

    @Test
    void messageEaseIsGentleEaseOutBack() {
        float peak = 0.0F;
        for (int i = 0; i <= 1000; i++) {
            float t = i / 1000.0F;
            peak = Math.max(peak, UiSpring.messageEase(t));
        }
        assertEquals(0.0F, UiSpring.messageEase(0.0F), 1e-4F, "curve starts at 0");
        assertEquals(1.0F, UiSpring.messageEase(1.0F), 1e-4F, "curve lands exactly on 1");
        assertTrue(peak > 1.0F && peak <= 1.05F,
                "message entrance overshoot must be at most 5%, got " + (100 * (peak - 1)) + "%");
    }

    /**
     * The bounce spring lives in the 0.92..1.08 scale domain. The overshoot
     * must survive the settle snap (that IS the visible bounce) and the ring
     * tail must still die inside ~350ms instead of wandering.
     */
    @Test
    void bounceSpringFitsTheScaleDomain() {
        SpringAnim s = UiSpring.newBounceSpring();
        s.snapTo(1.0F);
        s.setTarget(1.08F);
        float peak = 0.0F;
        long settleMs = -1;
        long t = 0;
        for (int i = 0; i < 90 && settleMs < 0; i++, t += 16L) {
            s.update(16L, true);
            peak = Math.max(peak, s.value());
            if (s.isSettled()) {
                settleMs = t + 16L;
            }
        }
        assertTrue(peak > 1.085F,
                "hover scale must overshoot visibly, got " + peak);
        assertTrue(settleMs >= 0 && settleMs <= 350,
                "bounce must settle inside 350ms, took " + settleMs + "ms");
        s.setTarget(1.0F);
        for (int i = 0; i < 200; i++) {
            s.update(16L, true);
        }
        assertEquals(1.0F, s.value(), 0.0F, "release lands exactly on 1");
        assertTrue(s.isSettled());
    }
}
