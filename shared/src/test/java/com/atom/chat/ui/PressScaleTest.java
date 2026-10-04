package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The press/hover scale contract: controls breathe (hover up, press down,
 * spring back), rows only press, and decorative-motion-off pins the scale
 * to exactly 1 — the draw must never wobble when animation is disabled.
 */
class PressScaleTest {

    @Test
    void controlMovesTowardHoverAndBack() {
        PressScale ps = PressScale.control();
        ps.update(true, false, 16.0F, true);
        assertTrue(ps.scale() > 1.0F, "hover must scale up, got " + ps.scale());
        ps.update(false, false, 16.0F, true);
        for (int i = 0; i < 120; i++) {
            ps.update(false, false, 16.0F, true);
        }
        assertEquals(1.0F, ps.scale(), 0.0F, "release lands exactly on 1");
        assertTrue(ps.isResting());
    }

    @Test
    void rowNeverScalesOnHover() {
        PressScale ps = PressScale.row();
        for (int i = 0; i < 30; i++) {
            ps.update(true, false, 16.0F, true);
        }
        assertEquals(1.0F, ps.scale(), 0.0F, "row hover must not scale");
        ps.update(true, true, 16.0F, true);
        assertTrue(ps.scale() < 1.0F, "row press must scale down, got " + ps.scale());
    }

    @Test
    void motionOffPinsScaleToOne() {
        PressScale control = PressScale.control();
        control.update(true, true, 16.0F, false);
        assertEquals(1.0F, control.scale(), 0.0F, "motion off must not scale even mid-press");
        for (int i = 0; i < 10; i++) {
            control.update(true, true, 16.0F, false);
        }
        assertEquals(1.0F, control.scale(), 0.0F);
        assertTrue(control.isResting());

        PressScale row = PressScale.row();
        row.update(false, true, 16.0F, false);
        assertEquals(1.0F, row.scale(), 0.0F);
    }
}
