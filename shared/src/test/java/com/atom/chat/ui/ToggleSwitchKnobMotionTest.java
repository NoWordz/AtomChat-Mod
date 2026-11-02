package com.atom.chat.ui;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Motion guards for the toggle knob's spring (unit C: the
 * {@code SpringAnim.tick} language replacing the fixed-duration tween).
 * Three properties are pinned, all through the package-private spring handle
 * with explicit nanos so the clock is a test input, not the wall:
 *
 * <ul>
 *   <li><b>Interruptible retarget</b> — flipping the switch back mid-flight
 *       continues the existing motion (velocity kept, no per-frame jump
 *       beyond what the spring's physics allows). The old eased tween
 *       restarted its full-duration curve from the current position, which
 *       reads as a jerk on a fast double-click.</li>
 *   <li><b>Reference tune</b> — k=380, d=24 (the iOS-switch feel): the flip
 *       overshoots slightly (about 8% of the travel) and lands exactly on
 *       the end. The old tween never overshot.</li>
 *   <li><b>Stall reset</b> — a gap over a quarter second (settings page
 *       closed, tab switched) lands directly on the target instead of
 *       replaying a stale animation on reopen.</li>
 * </ul>
 *
 * <p>Plus an offscreen pixel pass over the real {@code render}: the flip plays
 * through a mid-flight knob position and the settled frame is pixel-identical
 * to a switch snapped straight to the end — the motion language changed, the
 * visual language (geometry, colours, rounding) did not.</p>
 */
class ToggleSwitchKnobMotionTest {

    private static final long FRAME = 16_000_000L; // 16ms in nanos

    @Test
    void midFlightReversalContinuesWithoutJump() {
        ToggleSwitch t = new ToggleSwitch();
        SpringAnim knob = t.knobSpringForTest();
        long now = 0;
        knob.tick(0.0F, now, true); // anchor the clock at the off position
        now += FRAME;
        knob.tick(1.0F, now, true); // the flip starts here

        float maxDeltaBefore = 0.0F;
        boolean reversed = false;
        for (int i = 0; i < 400; i++) {
            now += FRAME;
            float target = reversed ? 0.0F : 1.0F;
            float before = knob.value();
            float v = knob.velocity();
            knob.tick(target, now, true);
            float delta = Math.abs(knob.value() - before);
            if (!reversed) {
                maxDeltaBefore = Math.max(maxDeltaBefore, delta);
                if (knob.value() > 0.45F) {
                    reversed = true;
                    // The step right after the reversal must be bounded by the
                    // motion already in play: a tween restart or a snap would
                    // break this.
                    assertTrue(delta <= Math.abs(v) * (FRAME / 1e9F) * 1.3F + 1e-4F,
                            "reversal must continue the existing motion, jumped " + delta
                                    + " with velocity " + v);
                    assertTrue(knob.velocity() > 0.5F,
                            "reversal keeps the knob moving (velocity not reset), got " + knob.velocity());
                }
            } else {
                assertTrue(delta <= maxDeltaBefore * 1.5F + 1e-3F,
                        "post-reversal steps stay continuous, max " + delta
                                + " vs pre-reversal " + maxDeltaBefore);
            }
        }
        assertTrue(reversed, "precondition: reached the reversal point");
        assertEquals(0.0F, knob.value(), 0.0F, "the reversal lands exactly on off");
        assertTrue(knob.isSettled());
    }

    @Test
    void referenceTuneOvershootsSlightlyAndLandsExactly() {
        ToggleSwitch t = new ToggleSwitch();
        SpringAnim knob = t.knobSpringForTest();
        long now = 0;
        knob.tick(0.0F, now, true);
        float peak = 0.0F;
        long settleNs = -1;
        for (int i = 1; i <= 600 && settleNs < 0; i++) {
            now += FRAME;
            knob.tick(1.0F, now, true);
            peak = Math.max(peak, knob.value());
            if (knob.isSettled()) {
                settleNs = i * FRAME;
            }
        }
        assertTrue(peak > 1.02F,
                "the k=380/d=24 tune must overshoot visibly (about 8%), peak was " + peak);
        assertTrue(peak < 1.15F, "the overshoot must stay restrained, peak was " + peak);
        assertTrue(settleNs > 0 && settleNs <= 600_000_000L,
                "the flip must settle, took " + (settleNs / 1_000_000L) + "ms");
        assertEquals(1.0F, knob.value(), 0.0F, "settles exactly on on");
    }

    @Test
    void rapidFlipsConvergeToTheFinalState() {
        ToggleSwitch t = new ToggleSwitch();
        SpringAnim knob = t.knobSpringForTest();
        long now = 0;
        knob.tick(0.0F, now, true);
        // Four quick taps: 60ms apart, ending on on.
        float[] targets = {1.0F, 0.0F, 1.0F, 0.0F, 1.0F};
        for (float target : targets) {
            now += 60_000_000L;
            knob.tick(target, now, true);
        }
        for (int i = 0; i < 600; i++) {
            now += FRAME;
            knob.tick(1.0F, now, true);
        }
        assertEquals(1.0F, knob.value(), 0.0F, "rapid taps converge exactly on the final state");
        assertTrue(knob.isSettled());
    }

    @Test
    void stallResetLandsDirectlyInsteadOfReplaying() {
        ToggleSwitch t = new ToggleSwitch();
        SpringAnim knob = t.knobSpringForTest();
        long now = 0;
        knob.tick(0.0F, now, true);
        now += FRAME;
        knob.tick(1.0F, now, true);
        // Drive mid-flight, then hide the settings page for a second.
        int guard = 0;
        while (knob.value() < 0.2F && guard++ < 30) {
            now += FRAME;
            knob.tick(1.0F, now, true);
        }
        now += 1_000_000_000L;
        assertEquals(1.0F, knob.tick(1.0F, now, true), 0.0F,
                "after the stall the knob lands this frame");
        assertTrue(knob.isSettled());
        assertEquals(0.0F, knob.velocity(), 0.0F);
    }

    @Test
    void snapAndSettledKnobBehaveDeterministically() {
        ToggleSwitch t = new ToggleSwitch();
        t.snapTo(true);
        assertEquals(1.0F, t.knobSpringForTest().value(), 0.0F, "snap lands on on");
        // A settled knob ignores updates regardless of the wall clock pacing.
        t.update(16.0F, true);
        assertEquals(1.0F, t.knobSpringForTest().value(), 0.0F);
        t.snapTo(false);
        assertEquals(0.0F, t.knobSpringForTest().value(), 0.0F, "snap lands on off");
    }

    @Test
    void offscreenFlipPlaysThroughMidFlightAndSettlesToTheSnappedFrame() throws Exception {
        int w = Math.round(com.atom.lib.design.atomchat.UiTokens.SWITCH_W) + 8;
        int h = Math.round(com.atom.lib.design.atomchat.UiTokens.SWITCH_H) + 8;
        int accent = 0xFF3D7BFF;

        ToggleSwitch animated = new ToggleSwitch();
        animated.snapTo(false);
        try (Surface s = Surface.makeRasterN32Premul(w, h)) {
            Canvas c = s.getCanvas();
            animated.render(c, 4, 4, accent);
            int leftKnobX = knobCenterX(s, true);
            assertTrue(leftKnobX > 0, "off knob found, at " + leftKnobX);

            // Drive the flip to mid-flight (explicit nanos, deterministic).
            SpringAnim knob = animated.knobSpringForTest();
            long now = 0;
            knob.tick(0.0F, now, true);
            int guard = 0;
            while ((knob.value() < 0.45F || knob.value() > 0.55F) && guard++ < 40) {
                now += FRAME;
                knob.tick(1.0F, now, true);
            }
            assertTrue(guard <= 40, "mid-flight reached");
            s.getCanvas().clear(0);
            animated.render(c, 4, 4, accent);
            int midKnobX = knobCenterX(s, true);

            // Settle, then the final frame must equal a switch snapped to on.
            for (int i = 0; i < 600 && !knob.isSettled(); i++) {
                now += FRAME;
                knob.tick(1.0F, now, true);
            }
            s.getCanvas().clear(0);
            animated.render(c, 4, 4, accent);
            int finalKnobX = knobCenterX(s, true);
            // Relational bounds instead of canvas fractions: the knob's right
            // edge at progress 0.5 sits exactly halfway along its travel for
            // any UiTokens value.
            assertTrue(finalKnobX > midKnobX && midKnobX > leftKnobX,
                    "the knob must travel left -> mid -> right, got " + leftKnobX
                            + " -> " + midKnobX + " -> " + finalKnobX);
            assertTrue(Math.abs(midKnobX - (leftKnobX + finalKnobX) / 2.0F)
                            < (finalKnobX - leftKnobX) * 0.35F,
                    "mid-flight knob sits around the middle, at " + midKnobX
                            + " between " + leftKnobX + " and " + finalKnobX);

            ToggleSwitch snapped = new ToggleSwitch();
            snapped.snapTo(true);
            try (Surface ref = Surface.makeRasterN32Premul(w, h)) {
                snapped.render(ref.getCanvas(), 4, 4, accent);
                boolean identical = true;
                for (int y = 0; y < h && identical; y++) {
                    for (int x = 0; x < w; x++) {
                        if (pixel(s, x, y) != pixel(ref, x, y)) {
                            identical = false;
                            break;
                        }
                    }
                }
                assertTrue(identical, "settled frame must be pixel-identical to the snapped end state");
            }
        }
    }

    private static int pixel(Surface surface, int x, int y) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(surface.getWidth(), surface.getHeight());
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.getColor(x, y);
        }
    }

    /**
     * X of the white knob's centre (rightmost near-white pixel on the middle
     * row), or -1 if none. The knob is the only near-white shape on the
     * track, so this tracks its position across the flip.
     */
    private static int knobCenterX(Surface surface, boolean rightmost) {
        int w = surface.getWidth();
        int h = surface.getHeight();
        int y = h / 2;
        int best = -1;
        for (int x = 0; x < w; x++) {
            int c = pixel(surface, x, y);
            int a = (c >>> 24) & 0xFF;
            int r = (c >>> 16) & 0xFF;
            int g = (c >>> 8) & 0xFF;
            int b = c & 0xFF;
            if (a >= 0xFE && r >= 0xFE && g >= 0xFE && b >= 0xFE) {
                // ARGB unpacked; the knob is pure white so near-white is the marker.
                if (rightmost) {
                    best = Math.max(best, x);
                } else {
                    best = best < 0 ? x : Math.min(best, x);
                }
            }
        }
        return best;
    }
}
