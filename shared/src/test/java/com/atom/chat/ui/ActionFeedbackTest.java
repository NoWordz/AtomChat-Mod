package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toast queue semantics: hold durations by outcome, refresh-instead-of-stack,
 * error priority, and the visible cap.
 */
class ActionFeedbackTest {

    @Test
    void successHoldsTwoSecondsThenExpires() {
        ActionFeedback f = new ActionFeedback();
        f.show("cache", ActionFeedback.Outcome.SUCCESS, 1000L);
        assertTrue(f.hasVisible(1500L), "still held at 0.5s");
        assertFalse(f.hasVisible(1000L + 2000L + 150L), "gone once hold + exit elapse");
    }

    @Test
    void errorHoldsFourSeconds() {
        ActionFeedback f = new ActionFeedback();
        f.show("save", ActionFeedback.Outcome.ERROR, 1000L);
        assertTrue(f.hasVisible(4500L), "a failure lingers past the success window");
        assertFalse(f.hasVisible(1000L + 4000L + 150L), "but not forever");
    }

    @Test
    void identicalKeyRefreshesInsteadOfStacking() {
        ActionFeedback f = new ActionFeedback();
        f.show("copy", ActionFeedback.Outcome.SUCCESS, 1000L);
        f.show("copy", ActionFeedback.Outcome.SUCCESS, 1500L);
        assertEquals(1, f.snapshot(1600L).size());
    }

    @Test
    void errorsTakePriorityInTheQueue() {
        ActionFeedback f = new ActionFeedback();
        for (int i = 0; i < 3; i++) {
            f.show("ok" + i, ActionFeedback.Outcome.SUCCESS, 1000L + i);
        }
        f.show("boom", ActionFeedback.Outcome.ERROR, 1010L);
        List<ActionFeedback.Entry> visible = f.snapshot(1020L);
        assertEquals("boom", visible.get(0).key());
    }

    @Test
    void queueCapsAtThreeKeepingErrors() {
        ActionFeedback f = new ActionFeedback();
        for (int i = 0; i < 4; i++) {
            f.show("ok" + i, ActionFeedback.Outcome.SUCCESS, 1000L + i);
        }
        f.show("boom", ActionFeedback.Outcome.ERROR, 1010L);
        List<ActionFeedback.Entry> visible = f.snapshot(1020L);
        assertEquals(ActionFeedback.MAX_VISIBLE, visible.size());
        assertEquals("boom", visible.get(0).key());
    }

    @Test
    void clearDropsEverything() {
        ActionFeedback f = new ActionFeedback();
        f.show("cache", ActionFeedback.Outcome.SUCCESS, 1000L);
        f.clear();
        assertFalse(f.hasVisible(1000L));
    }

    @Test
    void alphaRampsInAndFadesOut() {
        ActionFeedback.Entry e = new ActionFeedback.Entry("k", null, ActionFeedback.Outcome.SUCCESS, 1000L);
        assertEquals(0.0F, e.alpha(1000L), 1e-6F, "starts transparent");
        assertTrue(e.alpha(1000L + ActionFeedback.APPEAR_MS) > 0.9F, "opaque after appearing");
        assertTrue(e.alpha(1000L + 2000L + 100L) < 0.5F, "fading out in the exit window");
    }
}
