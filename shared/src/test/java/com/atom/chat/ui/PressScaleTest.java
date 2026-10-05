package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The press/hover scale contract: one pixel budget for every control. The
 * hover target is {@code u = 1 + min(BUDGET_PX / (w / 2), BUDGET_PCT)}, the
 * pressed target its mirror {@code 2 - u}, and decorative-motion-off pins
 * the target to exactly 1 — the draw must never wobble when animation is
 * disabled. Reference widths and their targets are spelled out below so a
 * regression in the formula shows up as a named row, not a mystery delta.
 */
class PressScaleTest {

    private static final float DT_MS = 16.0F;

    @Test
    void hoverTargetsFollowThePixelBudgetFormula() {
        // 490px conversation row: 4px on a 245px half-width.
        assertHoverTarget(490.0F, 1.0F + 4.0F / 245.0F);
        // 520px settings card.
        assertHoverTarget(520.0F, 1.0F + 4.0F / 260.0F);
        // 255px settings tile.
        assertHoverTarget(255.0F, 1.0F + 4.0F / 127.5F);
        // 173.33px composer row.
        assertHoverTarget(173.33F, 1.0F + 4.0F / (173.33F / 2.0F));
    }

    @Test
    void narrowControlsAllHitTheSixPercentCap() {
        // 120px is already past the crossover (4px > 6% of the half-width
        // from ~133px down), so every narrower width lands on the same cap.
        float[] widths = {120.0F, 100.5F, 50.0F, 45.0F, 22.5F};
        for (float w : widths) {
            PressScale ps = PressScale.bounce();
            ps.update(true, false, DT_MS, true, w);
            assertEquals(1.06F, ps.target(), 1e-4F, "width " + w + " must ride the cap");
        }
    }

    @Test
    void pressedTargetMirrorsHoverAroundOne() {
        float[] widths = {490.0F, 255.0F, 120.0F, 22.5F};
        for (float w : widths) {
            PressScale hover = PressScale.bounce();
            hover.update(true, false, DT_MS, true, w);
            PressScale pressed = PressScale.bounce();
            pressed.update(true, true, DT_MS, true, w);
            assertEquals(2.0F - hover.target(), pressed.target(), 1e-4F,
                    "press must spend the same budget inward at width " + w);
        }
    }

    @Test
    void motionOffPinsTargetToOne() {
        PressScale ps = PressScale.bounce();
        ps.update(true, true, DT_MS, false, 22.0F);
        assertEquals(1.0F, ps.target(), 0.0F, "motion off must not scale even mid-press");
        for (int i = 0; i < 10; i++) {
            ps.update(true, true, DT_MS, false, 520.0F);
        }
        assertEquals(1.0F, ps.target(), 0.0F);
        assertEquals(1.0F, ps.scale(), 0.0F);
        assertTrue(ps.isResting());
    }

    @Test
    void veryWideControlsStaySane() {
        PressScale ps = PressScale.bounce();
        ps.update(true, false, DT_MS, true, 10_000.0F);
        // 4px on a 5000px half-width: the budget simply thins out.
        assertEquals(1.0008F, ps.target(), 1e-4F);
    }

    @Test
    void releaseLandsBackOnOne() {
        PressScale ps = PressScale.bounce();
        for (int i = 0; i < 30; i++) {
            ps.update(true, false, DT_MS, true, 490.0F);
        }
        assertTrue(ps.scale() > 1.0F, "hover must scale up visibly, got " + ps.scale());
        for (int i = 0; i < 120; i++) {
            ps.update(false, false, DT_MS, true, 490.0F);
        }
        assertEquals(1.0F, ps.scale(), 0.0F, "release lands exactly on 1");
        assertTrue(ps.isResting());
    }

    private static void assertHoverTarget(float widthPx, float expected) {
        PressScale ps = PressScale.bounce();
        ps.update(true, false, DT_MS, true, widthPx);
        assertEquals(expected, ps.target(), 1e-4F, "hover target at width " + widthPx);
    }
}
