package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BottomChromeFeedbackTest {
    @Test void morphHasVisibleDurationAndSmallSettlingOvershoot() {
        var motion = new BottomChromeMotion();
        var from = new BottomChromeMotion.Shape(0, 0, 100, 80, 1);
        var to = new BottomChromeMotion.Shape(0, 20, 100, 60, 1);
        motion.target(from, 0, true);
        motion.target(to, 10, true);
        assertTrue(motion.sample(210).y() < 20, "200ms cannot already finish the morph");
        float peak = 20;
        for (int t=211; t<600; t++) peak=Math.max(peak,motion.sample(t).y());
        assertTrue(peak > 20.05F && peak < 21.2F, "small physical overshoot must actually be drawn");
        assertEquals(to, motion.sample(1000));
    }

    @Test void collapseNeverCreatesNegativeWidthOrInvalidAlphaAndReversesContinuously() {
        var motion = new BottomChromeMotion();
        var open = new BottomChromeMotion.Shape(0, 200, 300, 60, 1);
        var shut = new BottomChromeMotion.Shape(150, 200, 0, 60, 0);
        motion.target(open, 0, true); motion.target(shut, 10, true);
        assertTrue(motion.sample(170).w() > 0, "collapse needs readable duration");
        for(int t=10;t<600;t++) {
            var s=motion.sample(t);
            assertTrue(s.w()>=0 && s.alpha()>=0 && s.alpha()<=1);
        }
        var displayed=motion.sample(150);
        motion.target(open,150,true);
        assertEquals(displayed,motion.sample(150));
        assertEquals(open,motion.sample(1000));
    }
}
