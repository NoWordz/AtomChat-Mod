package com.atom.chat.page;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fold arithmetic: which rows are visible, how many are still hidden, and
 * when there is nothing left to load.
 */
class HistoryWindowTest {

    @Test
    void startsAtNewestBatch() {
        HistoryWindow w = new HistoryWindow(500, 100);
        assertEquals(400, w.visibleStart());
        assertEquals(100, w.visibleCount());
        assertEquals(400, w.remaining());
        assertTrue(w.canLoadEarlier());
    }

    @Test
    void eachLoadRevealsOneBatch() {
        HistoryWindow w = new HistoryWindow(500, 100);
        assertTrue(w.loadEarlier());
        assertEquals(300, w.visibleStart());
        assertEquals(200, w.visibleCount());
    }

    @Test
    void clampsAtZeroAndStopsOffering() {
        HistoryWindow w = new HistoryWindow(120, 100);
        assertTrue(w.loadEarlier());          // 20 -> 0, partial last batch
        assertEquals(0, w.visibleStart());
        assertEquals(120, w.visibleCount());
        assertFalse(w.canLoadEarlier());
        assertFalse(w.loadEarlier(), "already at the top: nothing to reveal");
    }

    @Test
    void shortConversationNeverOffers() {
        HistoryWindow w = new HistoryWindow(30, 100);
        assertEquals(0, w.visibleStart());
        assertEquals(30, w.visibleCount());
        assertFalse(w.canLoadEarlier());
    }

    @Test
    void resetFollowsNewTotal() {
        HistoryWindow w = new HistoryWindow(500, 100);
        w.loadEarlier();
        w.reset(80);
        assertEquals(0, w.visibleStart());
        assertEquals(80, w.visibleCount());
        assertFalse(w.canLoadEarlier());
    }

    @Test
    void negativeTotalClampsToEmpty() {
        HistoryWindow w = new HistoryWindow(-5, 100);
        assertEquals(0, w.visibleStart());
        assertEquals(0, w.visibleCount());
        assertFalse(w.canLoadEarlier());
    }

    @Test
    void nonPositiveBatchIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HistoryWindow(10, 0));
    }
}
