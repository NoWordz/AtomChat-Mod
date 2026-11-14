package com.atom.chat.diagnostics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** P1 failing-first coverage: page attribution, GPU degradation, red/green mutation. */
class PageGpuDiagnosticsTest {
    @Test void actualMotionSeparatesSteadyAndTransitionSamplesWithoutAFixedFrameWindow() {
        FrameSegments.navigation("chip","SETTINGS_SECTION:appearance:2");
        FrameProfile.pageMotion(true);frames(100);
        assertTrue(FrameProfile.pageSummary().contains("page_phase=transition"));
        FrameProfile.pageMotion(false);frames(1);
        String summary=FrameProfile.pageSummary();
        assertTrue(summary.contains("page_motion=settings_first_transition"),summary);
        assertTrue(summary.contains("page_motion=settings_first_steady"),summary);
        assertTrue(summary.contains("page_phase=steady"),summary);
    }

    @BeforeEach
    void setup() {
        FrameProfile.reset();          // also resets FrameSegments GPU ring
        FrameProfile.setEnabled(true);
    }

    @AfterEach
    void teardown() {
        FrameProfile.setEnabled(false);
        FrameProfile.reset();
    }

    private static void frames(int n) {
        for (int i = 0; i < n; i++) {
            FrameProfile.frame(System.nanoTime() - 1_000_000L, -1L);
        }
    }

    /**
     * OTHER must split into settings / about / profile / nav, and a page's
     * first visit must be distinguishable from a reopen. This is the GREEN side
     * of the mutation: removing the FrameProfile.page() call in
     * FrameSegments.navigation makes "page=about_first" disappear -> RED.
     */
    @Test
    void navigationSplitsOtherIntoPagesAndDistinguishesFirstVsRepeat() {
        FrameSegments.navigation("push", "CHAT_LIST");               frames(3); // NAVIGATION first
        FrameSegments.navigation("push", "SETTINGS_SECTION:about");  frames(3); // ABOUT first
        FrameSegments.navigation("push", "PROFILE");                 frames(3); // PROFILE first
        FrameSegments.navigation("push", "SETTINGS");                frames(3); // SETTINGS first
        FrameSegments.navigation("push", "SETTINGS_SECTION:about");  frames(3); // ABOUT repeat
        String ps = FrameProfile.pageSummary();
        assertTrue(ps.contains("page=navigation_first"), ps);
        assertTrue(ps.contains("page=about_first"), ps);
        assertTrue(ps.contains("page=profile_first"), ps);
        assertTrue(ps.contains("page=settings_first"), ps);
        assertTrue(ps.contains("page=about_repeat"), ps);
        assertTrue(ps.contains("page_phase="), ps);
        assertTrue(ps.contains("page_current=about"), ps);
    }

    /** Without a page key, every frame lands in OTHER and no ABOUT row exists. */
    @Test
    void missingPageKeyNeverProducesAnAboutRow() {
        frames(5);
        String ps = FrameProfile.pageSummary();
        assertFalse(ps.contains("page=about"), ps);
        assertTrue(ps.contains("page=other_"), ps);   // no navigation -> OTHER repeat bucket
    }

    /**
     * Production GL backend in a plain JVM (no GL context): beginFrame is
     * skipped, not stalled; no result read, no dangling query, no exception.
     * This is the "query unavailable / lost context" degradation path.
     */
    @Test
    void productionGlDegradesWithoutContextAndNeverStalls() {
        try (var ring = new GpuTimerQuery(new GlGpuTimer(), 4)) {
            assertFalse(ring.beginFrame(1), "no GL context -> skip, do not stall");
            ring.endFrame();
            assertTrue(ring.poll().isEmpty(), "no context -> no (blocking) result read");
            assertEquals(0, ring.pending(), "no dangling query");
            assertTrue(ring.skipped() >= 1);
        }
    }

    /**
     * The frame wiring itself must not throw or stall when the GPU ring is
     * continuously unavailable (mirrors another mod owning the query target
     * or an unsupported context). No glFinish / blocking read is on this path.
     */
    @Test
    void frameWiringSurvivesUnavailableQueries() {
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 8; i++) {
                long id = FrameSegments.begin(true);
                FrameSegments.finish(id, true);
            }
        });
        String s = FrameSegments.summary();
        assertTrue(s.contains("gpu_panel"), s);
        assertTrue(s.contains("completed=") && s.contains("skipped="), s);
        assertTrue(s.contains("page_phase="), s);
    }

    /** The additive page output must NOT leak into the byte-stable 5-line summary. */
    @Test
    void existingSummaryStaysFiveLines() {
        FrameSegments.navigation("push", "SETTINGS_SECTION:about");
        FrameProfile.frame(System.nanoTime() - 1_000_000L, -1L);
        String s = FrameProfile.summary();
        assertEquals(5, s.split("\n").length, s);
        assertFalse(s.contains("page="), s);
    }
}
