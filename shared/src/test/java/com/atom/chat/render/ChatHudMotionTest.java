package com.atom.chat.render;

import com.atom.chat.ui.UiMotion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatHudMotionTest {
    @Test void expiryFadesLeftWithoutAnArrivalAndAnimationOffClearsTheFade() {
        var motion = new ChatHudMotion<String>();
        motion.begin(0, false, false);
        var expired = motion.place("expired", "expired", 125, 20);
        motion.place("still live", "still live", 160, 20);
        motion.finish();

        motion.begin(1000, true, false);
        motion.place("still live", "still live", 160, 20);
        motion.finish();
        long middle = 1000 + UiMotion.MESSAGE_MS / 2;
        assertTrue(motion.retire(middle));
        assertTrue(expired.xOffset(middle) < 0F);
        assertTrue(expired.alpha(middle) > 0F && expired.alpha(middle) < 1F);

        motion.begin(middle, false, false);
        motion.place("still live", "still live", 160, 20);
        motion.finish();
        assertNull(expired.content());
    }

    @Test void arrivalSlidesInMovesIncumbentUpAndFadesOverflowOutToTheLeft() {
        var motion = new ChatHudMotion<String>();
        motion.begin(0, false, false);
        var previous = motion.place("previous", "previous", 170, 20);
        var oldest = motion.place("oldest", "oldest", 140, -20);
        motion.finish();

        motion.begin(1000, true, true);
        var newest = motion.place("newest", "newest", 170, 20);
        motion.place("previous", "previous", 140, 20);
        motion.finish();
        assertEquals(170F, previous.y(1000), 0.001F);
        assertEquals(20F, newest.xOffset(1000), 0.001F);
        assertEquals(0F, newest.alpha(1000), 0.001F);
        assertEquals(1F, oldest.alpha(1000), 0.001F);

        long middle = 1000 + UiMotion.MESSAGE_MS / 2;
        assertEquals(143.75F, previous.y(middle), 0.001F);
        assertTrue(newest.xOffset(middle) < 0F, "same brief overshoot as the panel");
        assertEquals(0.75F, newest.alpha(middle), 0.001F);
        assertTrue(oldest.xOffset(middle) < 0F);
        assertTrue(oldest.alpha(middle) > 0F && oldest.alpha(middle) < 1F);

        long end = 1000 + UiMotion.MESSAGE_MS;
        motion.begin(end, true, false);
        motion.place("newest", "newest", 170, 20);
        motion.place("previous", "previous", 140, 20);
        motion.finish();
        assertNull(oldest.content());
        assertEquals(140F, previous.y(end), 0.001F);
        assertEquals(1F, newest.alpha(end), 0.001F);
    }

    @Test void snappingClearsDeparturesAndRevealsMessagesReceivedBehindPanelAlreadySettled() {
        var motion = new ChatHudMotion<String>();
        motion.begin(0, false, false);
        var previous = motion.place("previous", "previous", 160, 20);
        motion.finish();
        motion.begin(1000, false, true);
        var newest = motion.place("from panel", "from panel", 160, -20);
        motion.finish();
        assertNull(previous.content());
        assertEquals(160F, newest.y(1000), 0.001F);
        assertEquals(0F, newest.xOffset(1000), 0.001F);
        assertEquals(1F, newest.alpha(1000), 0.001F);
    }

    @Test void rapidArrivalRetargetsAStillMovingRowFromItsPaintedPosition() {
        var motion = new ChatHudMotion<String>();
        motion.begin(0, false, false);
        var previous = motion.place("previous", "previous", 160, -20);
        motion.finish();
        motion.begin(1000, true, true);
        motion.place("new", "new", 160, -20);
        motion.place("previous", "previous", 130, -20);
        motion.finish();

        float moving = previous.y(1040);
        assertTrue(moving < 160 && moving > 130);
        motion.begin(1040, true, true);
        motion.place("newer", "newer", 160, -20);
        motion.place("new", "new", 130, -20);
        motion.place("previous", "previous", 100, -20);
        motion.finish();
        assertEquals(moving, previous.y(1040), 0.001F);
        assertEquals(100F, previous.y(1040 + UiMotion.MESSAGE_MS), 0.001F);
    }
}
