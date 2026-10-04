package com.atom.chat.render;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the blur cadence decision: motion keeps the every-2-frames refresh,
 * stillness drops it to the 500ms fallback, and any motion resumes instantly.
 */
class BlurMotionGateTest {
    private static final boolean DUE = true;
    private static final boolean NOT_DUE = false;

    @BeforeEach
    void reset() {
        BlurMotionGate.resetForTest();
    }

    @Test
    void movingSceneRefreshesOnEveryDueSlot() {
        long t = 0;
        BlurMotionGate.noteFrame(t++, 10.0F, 0.0F, false);
        BlurMotionGate.noteFrame(t++, 30.0F, 5.0F, false); // camera swung
        boolean firstSlot = BlurMotionGate.allowFullRefresh(t, DUE, false);
        BlurMotionGate.noteFrame(t++, 50.0F, 10.0F, false);
        boolean secondSlot = BlurMotionGate.allowFullRefresh(t, DUE, false);
        assertTrue(firstSlot, "moving camera: due slot refreshes");
        assertTrue(secondSlot, "moving camera keeps refreshing on due slots");
    }

    @Test
    void stillSceneDropsToSlowFallback() {
        long t = 0;
        BlurMotionGate.noteFrame(t, 10.0F, 0.0F, false); // primed, counts as motion once
        BlurMotionGate.noteFrame(++t, 10.0F, 0.0F, false);
        BlurMotionGate.noteFrame(++t, 10.0F, 0.0F, false);
        assertTrue(BlurMotionGate.allowFullRefresh(++t, DUE, false), "last moving frame still inside hold window");
        // advance well past the hold window, camera never moved again
        t += 1000;
        BlurMotionGate.noteFrame(t, 10.0F, 0.0F, false);
        assertTrue(BlurMotionGate.allowFullRefresh(t, DUE, false), "fallback fires once the interval elapsed");
        // immediately after a fallback refresh, the next slots are denied
        t += 16;
        BlurMotionGate.noteFrame(t, 10.0F, 0.0F, false);
        assertFalse(BlurMotionGate.allowFullRefresh(t, DUE, false), "static scene must not refresh every 2 frames");
        t += 100;
        assertFalse(BlurMotionGate.allowFullRefresh(t, DUE, false), "still under the fallback interval");
        t += 500;
        assertTrue(BlurMotionGate.allowFullRefresh(t, DUE, false), "fallback fires again after ~500ms");
    }

    @Test
    void chromeAnimationCountsAsMotion() {
        long t = 0;
        BlurMotionGate.noteFrame(t, 10.0F, 0.0F, true); // panel spring running, camera still
        assertTrue(BlurMotionGate.allowFullRefresh(++t, DUE, false), "panel animation keeps the fast cadence");
        t += 1000;
        BlurMotionGate.noteFrame(t, 10.0F, 0.0F, false); // animation settled
        assertTrue(BlurMotionGate.allowFullRefresh(t, DUE, false));
        t += 16;
        BlurMotionGate.noteFrame(t, 10.0F, 0.0F, false);
        assertFalse(BlurMotionGate.allowFullRefresh(t, DUE, false), "settled panel goes back to the slow cadence");
    }

    @Test
    void forceRefreshAlwaysRuns() {
        assertFalse(BlurMotionGate.allowFullRefresh(0L, NOT_DUE, false), "non-due slot never runs");
        assertTrue(BlurMotionGate.allowFullRefresh(0L, NOT_DUE, true), "forced refresh (resize) always runs");
    }

    @Test
    void tinyCameraDriftDoesNotBlockFallback() {
        long t = 0;
        BlurMotionGate.noteFrame(t, 10.0F, 0.0F, false);
        BlurMotionGate.noteFrame(++t, 10.05F, 0.0F, false); // sub-threshold jitter
        t += 1000;
        BlurMotionGate.noteFrame(t, 10.05F, 0.0F, false);
        assertTrue(BlurMotionGate.allowFullRefresh(t, DUE, false), "jitter below the threshold reads as still");
    }

    @Test
    void yawWrapCountsAsSmallMotion() {
        long t = 0;
        BlurMotionGate.noteFrame(t, 179.9F, 0.0F, false);
        BlurMotionGate.noteFrame(++t, -179.9F, 0.0F, false); // crossed the ±180 seam: 0.2deg real delta
        t += 1000;
        BlurMotionGate.noteFrame(t, -179.9F, 0.0F, false);
        assertTrue(BlurMotionGate.allowFullRefresh(t, DUE, false),
                "a seam crossing is 0.2deg of motion, not 359.8deg");
    }
}
