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

    /**
     * The stack is anchored above the composer, so the row must come up from
     * below (positive downward travel that shrinks to zero) and sink back on the
     * way out — the notification banner's drop-in mirrored for a lower anchor.
     */
    @Test
    void rowRisesFromBelowThenSinksOut() {
        ActionFeedback.Entry e = new ActionFeedback.Entry("k", null, ActionFeedback.Outcome.SUCCESS, 1000L);
        assertEquals(ActionFeedback.ENTER_TRAVEL, e.offset(1000L, true), 1e-6F,
                "starts fully below its slot");
        assertEquals(0.0F, e.offset(1000L + ActionFeedback.APPEAR_MS, true), 1e-4F,
                "settles exactly on its slot");
        // Sampled a quarter of the way in, not halfway: easeOutBack is already
        // past 1 by t=0.5 (its peak sits near t~0.75), so only the early part of
        // the curve is unambiguously "still travelling toward the slot".
        float settling = e.offset(1000L + ActionFeedback.APPEAR_MS / 4L, true);
        assertTrue(settling > 0.0F && settling < ActionFeedback.ENTER_TRAVEL,
                "early in the entrance it is still below the slot");
        // easeOutBack overshoots past the slot: the row rises slightly above it
        // before settling, which is the "slight overshoot" the banner has too.
        boolean overshot = false;
        for (long t = 0; t <= ActionFeedback.APPEAR_MS; t++) {
            if (e.offset(1000L + t, true) < 0.0F) {
                overshot = true;
                break;
            }
        }
        assertTrue(overshot, "easeOutBack must carry the row slightly past its slot");
        assertTrue(e.offset(1000L + 2000L + ActionFeedback.EXIT_MS / 2L, true) > 0.0F,
                "the exit sinks it back down while it fades");
    }

    /** With decorative motion off the row is pinned to its slot and stays opaque. */
    @Test
    void motionOffPinsPositionAndOpacity() {
        ActionFeedback.Entry e = new ActionFeedback.Entry("k", null, ActionFeedback.Outcome.SUCCESS, 1000L);
        assertEquals(0.0F, e.offset(1000L, false), 1e-6F);
        assertEquals(0.0F, e.offset(1000L + 100L, false), 1e-6F);
        assertEquals(1.0F, e.alpha(1000L, false), 1e-6F, "no fade-in ramp");
        assertEquals(1.0F, e.alpha(1000L + 1000L, false), 1e-6F, "still fully opaque while held");
        assertEquals(0.0F, e.alpha(1000L + 2000L + 150L, false), 1e-6F, "gone once expired");
    }

    /**
     * The stack is anchored above the composer and climbs: index 0 (the first
     * entry {@link ActionFeedback#snapshot} returns) must sit nearest the anchor,
     * each later one a full row higher.
     */
    @Test
    void stackClimbsUpwardFromTheAnchor() {
        float bottom = 500.0F;
        float rowH = 45.0F;
        float gap = 10.0F;
        float inset = 10.0F;
        float first = ActionFeedback.Entry.rowTop(bottom, 0, rowH, gap, inset);
        float second = ActionFeedback.Entry.rowTop(bottom, 1, rowH, gap, inset);
        float third = ActionFeedback.Entry.rowTop(bottom, 2, rowH, gap, inset);
        assertEquals(bottom - inset - rowH, first, 1e-4F, "the first row sits directly above the anchor inset");
        assertEquals(first - (rowH + gap), second, 1e-4F, "each later row climbs by a row plus the gap");
        assertTrue(third < second && second < first, "indices must be strictly ascending on screen");
    }
}
