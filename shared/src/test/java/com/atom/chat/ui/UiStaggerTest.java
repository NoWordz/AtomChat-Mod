package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins for the page-entrance stagger (retuned in the
 * field, retuned again after "没有曲线，显得生硬", then split onto one motion axis
 * per nav style): items land over 380ms on a 25ms step capped at 10 slots and
 * carry a style channel on that same progress; the whole-page envelope runs
 * 320ms on easeOutQuad with a 44px SLIDE drift and a 0.92 ZOOM floor, its fade
 * the same curve delayed by 26ms; and — the golden-safety rule — an entrance
 * that was never armed reads fully settled.
 *
 * <p>The axis pins are their own block below: SLIDE is horizontal only, ZOOM is
 * vertical only, and neither may borrow the other's channel. That is what the
 * field's "斜向右上" complaint came from — SLIDE carrying both legs at once.</p>
 */
class UiStaggerTest {

    private static final long T0 = 1_000_000L;
    private static final UiStagger.Axis H = UiStagger.Axis.HORIZONTAL;
    private static final UiStagger.Axis V = UiStagger.Axis.VERTICAL;

    @Test
    void unarmedEntranceIsFullySettled() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        assertEquals(1.0F, UiStagger.itemProgress(e, 0, T0), 0.0F);
        for (UiStagger.Axis axis : UiStagger.Axis.values()) {
            assertEquals(0.0F, UiStagger.itemRise(e, 0, T0, axis), 0.0F);
            assertEquals(0.0F, UiStagger.itemStyleSlidePx(e, 0, T0, axis), 0.0F);
            assertEquals(1.0F, UiStagger.itemStyleZoomScale(e, 0, T0, axis), 0.0F);
            assertEquals(0.0F, UiStagger.tabBarRise(e, T0, axis), 0.0F);
        }
        assertEquals(1.0F, UiStagger.pageProgress(e, T0), 0.0F);
        assertEquals(1.0F, UiStagger.pageAlpha(e, T0), 0.0F);
        assertEquals(0.0F, UiStagger.pageSlidePx(e, T0), 0.0F);
        assertTrue(UiStagger.itemsSettled(e, T0), "an unarmed entrance has no travel left");
        assertFalse(e.armed());
    }

    /** 25ms steps, capped at 10 slots: item 9 and item 50 share the last delay. */
    @Test
    void itemsStaggerByTwentyFiveMsAndCapAtTen() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        // Item 0 starts immediately: easeOutCubic(0.5) at 190ms of 380.
        float first = UiStagger.itemProgress(e, 0, T0 + 190);
        assertTrue(first > 0.8F && first < 0.95F, "item 0 mostly landed at 190ms, got " + first);
        // Item 5 waits 125ms, so at 190ms it is 65/380 in.
        float fifth = UiStagger.itemProgress(e, 5, T0 + 190);
        assertTrue(fifth > 0.0F && fifth < 0.5F, "item 5 still moving at 190ms, got " + fifth);
        assertEquals(UiStagger.itemProgress(e, 9, T0 + 300),
                UiStagger.itemProgress(e, 50, T0 + 300), 0.0F,
                "the stagger caps at 10 slots");
        // Everything lands by 225 + 380 = 605ms.
        for (int i = 0; i < 60; i++) {
            assertEquals(1.0F, UiStagger.itemProgress(e, i, T0 + 610), 0.0F);
        }
        assertFalse(UiStagger.itemsSettled(e, T0 + 590), "the last slot is still owing at 225+365");
        assertTrue(UiStagger.itemsSettled(e, T0 + 610), "and every slot has landed by 225+380");
    }

    /**
     * The retuned numbers: the page amplitudes (44px / 0.92) keep SLIDE and ZOOM
     * readable apart, and the per-item style channel is 24px of drift / 0.97 of
     * scale. The 24 is the SLIDE leg's whole travel now that the rise no longer
     * shares the budget with it.
     */
    @Test
    void theStaggerStepAndCapAreTheRetunedValues() {
        assertEquals(25L, UiStagger.ITEM_STAGGER_MS);
        assertEquals(10, UiStagger.ITEM_STAGGER_CAP);
        assertEquals(380L, UiStagger.ITEM_MS);
        assertEquals(18.0F, UiStagger.ITEM_RISE_PX, 0.0F);
        assertEquals(44.0F, UiStagger.PAGE_SLIDE_PX, 0.0F);
        assertEquals(0.92F, UiStagger.ZOOM_START_SCALE, 0.0F);
        assertEquals(10.0F, UiStagger.TAB_BAR_RISE_PX, 0.0F);
        assertEquals(24.0F, UiStagger.ITEM_STYLE_SLIDE_PX, 0.0F);
        assertEquals(0.97F, UiStagger.ITEM_STYLE_ZOOM_START, 0.0F);
        assertEquals(26L, UiStagger.PAGE_ALPHA_DELAY_MS);
        assertEquals(320L, UiStagger.PAGE_MS);
    }

    /**
     * The axis semantics, the reason the enum exists: each style owns exactly
     * one motion channel, and the other channels are pinned to their rest
     * values (0 lift / 0 drift / scale 1). SLIDE carrying the 18px lift as well
     * was the field's "the cards move diagonally up-right".
     */
    @Test
    void eachAxisOwnsExactlyOneMotionChannel() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);

        // HORIZONTAL (SLIDE): drift only.
        assertEquals(0.0F, UiStagger.itemRise(e, 0, T0, H), 0.0F, "SLIDE must not lift");
        assertEquals(UiStagger.ITEM_STYLE_SLIDE_PX,
                UiStagger.itemStyleSlidePx(e, 0, T0, H), 1e-4F, "SLIDE's drift is the whole gesture");
        assertEquals(24.0F, UiStagger.itemStyleSlidePx(e, 0, T0, H), 1e-4F);
        assertEquals(1.0F, UiStagger.itemStyleZoomScale(e, 0, T0, H), 0.0F, "SLIDE must not scale");
        assertEquals(0.0F, UiStagger.tabBarRise(e, T0, H), 0.0F, "the bar only fades in under SLIDE");

        // VERTICAL (ZOOM): lift and scale, no drift.
        assertEquals(18.0F, UiStagger.itemRise(e, 0, T0, V), 1e-4F);
        assertEquals(0.0F, UiStagger.itemStyleSlidePx(e, 0, T0, V), 0.0F, "ZOOM must not drift");
        assertEquals(0.97F, UiStagger.itemStyleZoomScale(e, 0, T0, V), 1e-4F);
        assertEquals(10.0F, UiStagger.tabBarRise(e, T0, V), 1e-4F);
        assertTrue(UiStagger.itemRise(e, 0, T0 + 210, V) > 0.0F
                && UiStagger.itemRise(e, 0, T0 + 210, V) < 18.0F,
                "the lift decays over the item clock");
        assertEquals(0.0F, UiStagger.itemRise(e, 0, T0 + 700, V), 0.0F);
        assertEquals(1.0F, UiStagger.itemStyleZoomScale(e, 0, T0 + 700, V), 0.0F);
        assertEquals(0.0F, UiStagger.tabBarRise(e, T0 + UiStagger.PAGE_MS, V), 0.0F);
    }

    /**
     * The fade must not lead the motion on either axis: alpha is the item
     * progress, and the moving channel is a decaying function of that same
     * progress, so a row can never be opaque while it is still shifted.
     */
    @Test
    void itemAlphaNeverLeadsTheMotionOnEitherAxis() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        for (int i = 0; i < 12; i++) {
            for (long t = T0; t <= T0 + 700; t += 25) {
                float progress = UiStagger.itemProgress(e, i, t);
                assertEquals(progress, UiStagger.itemAlpha(e, i, t), 0.0F,
                        "alpha is the item progress, not a second curve");
                assertEquals((1.0F - progress) * UiStagger.ITEM_RISE_PX,
                        UiStagger.itemRise(e, i, t, V), 1e-4F);
                assertEquals((1.0F - progress) * UiStagger.ITEM_STYLE_SLIDE_PX,
                        UiStagger.itemStyleSlidePx(e, i, t, H), 1e-4F);
                assertEquals(UiStagger.ITEM_STYLE_ZOOM_START
                                + (1.0F - UiStagger.ITEM_STYLE_ZOOM_START) * progress,
                        UiStagger.itemStyleZoomScale(e, i, t, V), 1e-4F);
            }
        }
    }

    /**
     * The fade owns its own clock inside the page clock: alpha is 0 at t=0,
     * still 0 at the delay boundary, and 1 exactly at {@code PAGE_MS}. It reads
     * the same raw stamp as the motion, so the two cannot disagree about when
     * the entrance began — only about when the fade joins in.
     */
    @Test
    void theFadeIsDelayedByAFractionOfThePageClock() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        assertEquals(0.0F, UiStagger.pageAlpha(e, T0), 0.0F, "alpha 0 on the arrival frame");
        assertEquals(0.0F, UiStagger.pageAlpha(e, T0 + UiStagger.PAGE_ALPHA_DELAY_MS), 0.0F,
                "the fade has not started at the delay boundary");
        assertEquals(1.0F, UiStagger.pageAlpha(e, T0 + UiStagger.PAGE_MS), 0.0F,
                "and it lands exactly on the page clock");
        float previous = -1.0F;
        for (long t = T0; t <= T0 + UiStagger.PAGE_MS; t += 8) {
            float alpha = UiStagger.pageAlpha(e, t);
            assertTrue(alpha >= previous, "the fade never runs backwards at " + t);
            previous = alpha;
        }
    }

    /**
     * The motion is already travelling at 10% of the clock: easeOutQuad has a
     * non-zero start velocity, which is what the earlier ease-in-out cubic did
     * not — cubic sat still for the first frames, and the field called it
     * "a pause then a jump".
     */
    @Test
    void theMotionStartsMovingImmediately() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        float tenth = UiStagger.pageProgress(e, T0 + UiStagger.PAGE_MS / 10);
        assertTrue(tenth > 0.15F, "easeOutQuad is ~19% in at 10% of the clock, got " + tenth);
        assertTrue(tenth < 0.5F, "and not half way yet, got " + tenth);
        assertTrue(UiStagger.pageSlidePx(e, T0 + UiStagger.PAGE_MS / 10)
                        < 0.85F * UiStagger.PAGE_SLIDE_PX,
                "at 10% of the clock the page has visibly drifted");
    }

    /**
     * Both channels hit 0 and 1 at the ends of the page clock. Motion is the
     * style transform's input (drift / scale), fade is the layer's alpha.
     */
    @Test
    void fadeAndMotionBothSpanZeroToOneOverTheClock() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        assertEquals(0.0F, UiStagger.pageProgress(e, T0), 1e-6F);
        assertEquals(1.0F, UiStagger.pageProgress(e, T0 + UiStagger.PAGE_MS), 1e-6F);
        assertEquals(0.0F, UiStagger.pageAlpha(e, T0), 1e-6F);
        assertEquals(1.0F, UiStagger.pageAlpha(e, T0 + UiStagger.PAGE_MS), 1e-6F);
        assertEquals(UiStagger.PAGE_SLIDE_PX, UiStagger.pageSlidePx(e, T0), 1e-4F);
        assertEquals(0.0F, UiStagger.pageSlidePx(e, T0 + UiStagger.PAGE_MS), 1e-4F);
        assertEquals(UiStagger.ZOOM_START_SCALE, UiStagger.pageZoomScale(e, T0), 1e-6F);
        assertEquals(1.0F, UiStagger.pageZoomScale(e, T0 + UiStagger.PAGE_MS), 1e-6F);
    }

    /**
     * One shared raw clock: the fade is the motion's own curve reading the same
     * stamp, so the two can never be at opposite ends of the entrance. They are
     * no longer the same number (that was the ease-in-out design) — the fade
     * simply trails the motion instead.
     */
    @Test
    void theFadeReadsTheSameClockAsTheMotion() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        for (long t = T0; t <= T0 + UiStagger.PAGE_MS; t += 8) {
            float alpha = UiStagger.pageAlpha(e, t);
            float motion = UiStagger.pageProgress(e, t);
            assertTrue(alpha <= motion + 1e-4F,
                    "the delayed fade never leads the motion at " + t);
        }
        e.markEntered(T0 + 200);
        assertEquals(0.0F, UiStagger.pageProgress(e, T0 + 200), 0.0F);
        assertEquals(0.0F, UiStagger.pageAlpha(e, T0 + 200), 0.0F);
    }

    /**
     * The page starts moving before it becomes visible. The delay is what buys
     * it: without one the first frames light up a page that has not moved yet,
     * and the slide gets spent at near-zero alpha.
     */
    @Test
    void thePageStartsMovingBeforeItBecomesVisible() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        long delayEdge = T0 + UiStagger.PAGE_ALPHA_DELAY_MS;
        assertEquals(0.0F, UiStagger.pageAlpha(e, delayEdge), 0.0F,
                "still fully transparent at the delay boundary");
        assertTrue(UiStagger.pageProgress(e, delayEdge) > 0.1F,
                "while the page has already travelled a visible share of its drift");
    }

    /** The ZOOM variant spills the page clock into 0.92 -> 1. */
    @Test
    void theZoomSettlesFromNinetyTwoPercent() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        long half = T0 + UiStagger.PAGE_MS / 2;
        assertTrue(UiStagger.pageZoomScale(e, half) > UiStagger.ZOOM_START_SCALE
                && UiStagger.pageZoomScale(e, half) < 1.0F);
        assertTrue(UiStagger.pageSlidePx(e, half) > 0.0F
                && UiStagger.pageSlidePx(e, half) < UiStagger.PAGE_SLIDE_PX,
                "the drift decays over the same clock");
    }

    /** The bar's return entrance rides the page envelope on the ZOOM axis. */
    @Test
    void theTabBarRisesTenPxOnTheReturnArrivalUnderZoom() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        assertEquals(UiStagger.TAB_BAR_RISE_PX, UiStagger.tabBarRise(e, T0, V), 1e-4F);
        assertEquals(0.0F, UiStagger.tabBarAlpha(e, T0), 0.0F);
        assertEquals(0.0F, UiStagger.tabBarRise(e, T0 + UiStagger.PAGE_MS, V), 0.0F);
        assertEquals(1.0F, UiStagger.tabBarAlpha(e, T0 + UiStagger.PAGE_MS), 0.0F);
    }

    @Test
    void motionOffSettlesEverythingInstantly() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        AtomChatConfig.get().animationEnabled = false;
        try {
            assertEquals(1.0F, UiStagger.itemProgress(e, 0, T0 + 1), 0.0F);
            assertEquals(1.0F, UiStagger.pageProgress(e, T0 + 1), 0.0F);
            assertEquals(1.0F, UiStagger.pageAlpha(e, T0 + 1), 0.0F);
            for (UiStagger.Axis axis : UiStagger.Axis.values()) {
                assertEquals(0.0F, UiStagger.itemRise(e, 0, T0 + 1, axis), 0.0F);
                assertEquals(0.0F, UiStagger.itemStyleSlidePx(e, 0, T0 + 1, axis), 0.0F);
                assertEquals(1.0F, UiStagger.itemStyleZoomScale(e, 0, T0 + 1, axis), 0.0F);
                assertEquals(0.0F, UiStagger.tabBarRise(e, T0 + 1, axis), 0.0F);
            }
            assertTrue(UiStagger.itemsSettled(e, T0 + 1));
        } finally {
            AtomChatConfig.get().animationEnabled = true;
        }
    }

    /** easeOutCubic starts at full speed: an item is visibly moving one frame in. */
    @Test
    void theRiseUsesAnEaseOutCurve() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        float early = UiStagger.itemProgress(e, 0, T0 + 60);
        assertTrue(early > 0.25F, "easeOutCubic front-loads the motion, got " + early);
        assertTrue(early < 0.75F, "and does not land instantly, got " + early);
    }

    /** An interrupt is a re-stamp: the same entrance replays from zero. */
    @Test
    void anInterruptReplaysTheSameEntrance() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        assertTrue(UiStagger.pageAlpha(e, T0 + 200) > 0.3F);
        e.markEntered(T0 + 200);
        assertEquals(0.0F, UiStagger.pageAlpha(e, T0 + 200), 0.0F);
        assertEquals(1.0F, UiStagger.pageAlpha(e, T0 + 200 + UiStagger.PAGE_MS), 0.0F);
    }

    /** The shell envelope's settle test is the raw page clock, not the fade curve. */
    @Test
    void theEnvelopeSettlesAtThePageClock() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        assertFalse(UiStagger.entranceSettled(e, T0 + UiStagger.PAGE_MS - 1));
        assertTrue(UiStagger.entranceSettled(e, T0 + UiStagger.PAGE_MS));
    }

    @Test
    void markSettledPutsAHolderBackToRest() {
        UiStagger.Entrance e = new UiStagger.Entrance();
        e.markEntered(T0);
        assertTrue(e.armed());
        e.markSettled();
        assertFalse(e.armed());
        assertEquals(1.0F, UiStagger.itemAlpha(e, 0, T0 + 1), 0.0F);
        assertEquals(0.0F, UiStagger.itemStyleSlidePx(e, 0, T0 + 1, H), 0.0F);
    }
}
