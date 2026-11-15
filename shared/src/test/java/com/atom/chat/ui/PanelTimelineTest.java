package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelTimelineTest {
    @Test void openingAndClosingReachTheirEndpointWithoutAnEarlySnap() {
        PanelTimeline motion = new PanelTimeline();
        motion.target(1, 0, true);
        assertEquals(0, motion.value(0));
        float previous=0;
        for (int ms=1;ms<240;ms++) {
            float value=motion.value(ms*1_000_000L);
            assertTrue(value>=previous && value<1, "continuous, monotonic entrance");
            previous=value;
        }
        assertEquals(1, motion.value(240_000_000L));
        motion.target(0,240_000_000L,true);
        assertTrue(motion.value(419_000_000L)>0,"no six-percent exit cut");
        assertEquals(0,motion.value(420_000_000L));
    }

    @Test void reversingAndRepeatedTargetsKeepTheCurrentPosition() {
        PanelTimeline motion=new PanelTimeline();
        motion.target(1,0,true);
        float partial=motion.value(80_000_000L);
        motion.target(0,80_000_000L,true);
        assertEquals(partial,motion.value(80_000_000L));
        float closing=motion.value(110_000_000L);
        motion.target(0,110_000_000L,true);
        assertEquals(closing,motion.value(110_000_000L));
        motion.target(1,110_000_000L,true);
        assertEquals(closing,motion.value(110_000_000L));
        assertEquals(1,motion.value(350_000_000L));
    }

    @Test void samplesDependOnElapsedTimeAndMotionOffLandsImmediately() {
        PanelTimeline sparse=new PanelTimeline(), frequent=new PanelTimeline();
        sparse.target(1,0,true);frequent.target(1,0,true);
        for(int ms=0;ms<=120;ms++) frequent.value(ms*1_000_000L);
        assertEquals(sparse.value(120_000_000L),frequent.value(120_000_000L));
        frequent.target(0,120_000_000L,false);
        assertEquals(0,frequent.value(120_000_000L));
    }
}
