package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timing guards. The UI once used {@code v += (target - v) * dt / D}, whose
 * asymptotic tail kept a hover highlight lit for ~550ms after the pointer
 * left. These tests pin the two properties that fix it: a transition finishes
 * within its declared duration, and it lands exactly on the target.
 */
class UiMotionTest {
    private static final float EPS = 0.0001F;

    /** Simulates frames of frameMs and returns the value after durationMs elapses. */
    private static float run(float from, float to, long durationMs, long frameMs) {
        float value = from;
        for (long elapsed = 0; elapsed < durationMs; elapsed += frameMs) {
            value = UiMotion.approach(value, to, frameMs, durationMs);
        }
        return value;
    }

    @Test
    void hoverClearsWithinDeclaredDuration() {
        // Regression: the pointer left the button, the highlight must be gone.
        assertEquals(0.0F, run(1.0F, 0.0F, UiMotion.HOVER_MS, 16L), EPS,
                "hover fully clears within HOVER_MS");
    }

    @Test
    void hoverClearingIsFrameRateIndependent() {
        assertEquals(0.0F, run(1.0F, 0.0F, UiMotion.HOVER_MS, 5L), EPS, "200fps clears in time");
        assertEquals(0.0F, run(1.0F, 0.0F, UiMotion.HOVER_MS, 33L), EPS, "30fps clears in time");
        assertEquals(0.0F, run(1.0F, 0.0F, UiMotion.HOVER_MS, 50L), EPS, "frame spike clears in time");
    }

    @Test
    void approachSnapsExactlyToTarget() {
        // No asymptotic residue: scrollbar alpha / popup fade must hit 0 or 1.
        float v = 1.0F;
        for (int i = 0; i < 1000; i++) {
            v = UiMotion.approach(v, 0.0F, 16L, UiMotion.SCROLLBAR_FADE_MS);
        }
        assertEquals(0.0F, v, 0.0F, "no residual value left behind");
        assertEquals(1.0F, UiMotion.approach(0.0F, 1.0F, 10_000L, UiMotion.POPUP_MS), 0.0F);
    }

    @Test
    void approachNeverOvershoots() {
        float v = 0.0F;
        for (int i = 0; i < 60; i++) {
            v = UiMotion.approach(v, 1.0F, 16L, UiMotion.HOVER_MS);
            assertTrue(v >= 0.0F && v <= 1.0F, "value stays inside [0,1]");
        }
    }

    @Test
    void durationsStaySnappy() {
        // Any transition slower than this reads as sticky on screen. This guard
        // covers *responses to input* only — MESSAGE_MS is a content reveal and
        // is asserted separately below. PANEL_MS is deliberately absent: the
        // panel open/close moved to the spring language (UiSpring), which has
        // its own arrival budget in SpringAnimTest.
        long[] all = {
                UiMotion.SCROLL_SNAP_MS,
                UiMotion.SCROLL_WHEEL_MS, UiMotion.HOVER_MS, UiMotion.SCROLLBAR_FADE_MS,
                UiMotion.SCROLLBAR_EMPHASIS_MS, UiMotion.POPUP_MS, UiMotion.INPUT_GROW_MS
        };
        for (long ms : all) {
            assertTrue(ms > 0 && ms <= 200, "duration " + ms + "ms must stay at or under 200ms");
        }
    }

    @Test
    void messageEntryIsSlowEnoughToReadAsAFade() {
        // An opacity ramp needs roughly 200ms before the eye reads it as a fade
        // rather than a pop. Anything below that and the same easeOutCubic that
        // looks great on the slide finishes the fade in ~70ms, so the entrance
        // reads as "it only slid" — which is exactly the complaint we got.
        assertTrue(UiMotion.MESSAGE_MS >= 200,
                "message entrance needs >= 200ms to read as a fade, got " + UiMotion.MESSAGE_MS);
        assertTrue(UiMotion.MESSAGE_MS <= 300,
                "message entrance must not drag, got " + UiMotion.MESSAGE_MS);
    }

    @Test
    void pageNavTimingFollowsTheStyle() {
        // The slide keeps the snappy 140ms tween window; the zoom is the
        // Melodify serial two-phase settle driven by expApproach at a 50ms
        // tau per phase (no tween window at all).
        assertEquals(140L, UiMotion.PAGE_NAV_MS,
                "slide nav keeps the 140ms window");
        assertEquals(50.0F, UiMotion.PAGE_NAV_TAU_MS,
                "zoom nav runs each phase at the 50ms Melodify tau");
    }

    @Test
    void expApproachIsExponentialAndFrameRateIndependent() {
        // tau 0 snaps (the decorative-motion-off path).
        assertEquals(1.0F, UiMotion.expApproach(0.0F, 1.0F, 16.0F, 0.0F),
                "tau 0 snaps to the target");
        // Two 8ms steps close exactly as much as one 16ms step.
        float one = UiMotion.expApproach(0.0F, 1.0F, 16.0F, 50.0F);
        float two = UiMotion.expApproach(0.0F, 1.0F, 8.0F, 50.0F);
        two = UiMotion.expApproach(two, 1.0F, 8.0F, 50.0F);
        assertEquals(one, two, 1e-6F,
                "frame-rate independent: 16ms once == 8ms twice");
        // Peak speed at the start, decaying forever: the first step covers
        // more distance than the second.
        float first = UiMotion.expApproach(0.0F, 1.0F, 16.0F, 50.0F);
        float second = UiMotion.expApproach(first, 1.0F, 16.0F, 50.0F);
        assertTrue(first > second - first + 1e-6F,
                "first step travels more than the second");
        // Snaps once within 0.001 of the target.
        float nearly = 0.9995F;
        assertEquals(1.0F, UiMotion.expApproach(nearly, 1.0F, 16.0F, 50.0F),
                "converges by snapping inside the 0.001 epsilon");
    }
}
