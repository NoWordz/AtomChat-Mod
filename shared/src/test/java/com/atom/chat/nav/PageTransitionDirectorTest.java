package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.Animator;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The D24b-1 contract: both transition clocks, by value.
 *
 * <p><b>Why numeric cases and not pixel frames.</b> The golden scenes render
 * page objects and never composite a transition, so the one thing a refactor of
 * these machines cannot lean on is a clean golden diff. What can be pinned is
 * the arithmetic itself: the exponential law the clocks ride, the 0.02 hand-over
 * from EXIT to ENTER (including the same-frame hand-over the sequential ifs
 * exist for), the exact frame each machine settles on, the push/pop sign of
 * {@code pageNavDx}, the panel-centred scale matrix, and the four style
 * combinations of the one resolver. Every assertion below is a number, so a
 * swapped arm or a dropped snap turns it red — which the mutations in the
 * report demonstrate.
 *
 * <p>The machines are still visual: what the numbers <em>look</em> like on
 * screen (the slide's direction, the zoom's depth, the capsule landing with its
 * pages) is a real-machine probe, and the report says so.
 */
class PageTransitionDirectorTest {

    /**
     * One 50 ms frame on each clock. The taus are the tuned constants
     * ({@code UiMotion.PAGE_NAV_TAU_MS} = 50, {@code PAGE_SLIDE_TAU_MS} = 70)
     * written out as numbers on purpose: this is the arithmetic the motion is
     * made of, so a retune must show up here rather than be absorbed.
     */
    private static final float ZOOM_FRACTION = 1.0F - (float) Math.exp(-50.0 / 50.0);
    private static final float SLIDE_FRACTION = 1.0F - (float) Math.exp(-50.0 / 70.0);

    private boolean animations;
    private AtomChatConfig.PageNavStyle style;

    /**
     * The config is read through {@code Platform}, so this class installs its own
     * temporary platform instead of riding on whichever test class happened to run
     * first: a filtered run ({@code --tests}) would otherwise fail here with
     * "Platform not installed" rather than with a real number.
     */
    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return root.resolve("config");
            }

            @Override
            public Path gameDir() {
                return root.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @BeforeEach
    void setUp() {
        AtomChatConfig config = AtomChatConfig.get();
        animations = config.animationEnabled;
        style = config.pageNavStyle;
        config.animationEnabled = true;
        config.pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
    }

    @AfterEach
    void tearDown() {
        AtomChatConfig config = AtomChatConfig.get();
        config.animationEnabled = animations;
        config.pageNavStyle = style;
    }

    // ------------------------------------------------------------- fixtures

    /** Records the three steps the director cannot take itself, in order. */
    private static final class Recorder implements PageTransitionDirector.Host {
        final List<String> calls = new ArrayList<>();

        @Override
        public void persistCurrentDraft() {
            calls.add("persistCurrentDraft");
        }

        @Override
        public void captureReplySlot() {
            calls.add("captureReplySlot");
        }

        @Override
        public void applyPendingPop() {
            calls.add("applyPendingPop");
        }
    }

    private final Recorder host = new Recorder();
    private final Animator capsule = new Animator(t -> t);

    private PageTransitionDirector director() {
        return new PageTransitionDirector(host, capsule);
    }

    private static NavPage root(AppPage page) {
        return NavPage.of(page);
    }

    // ------------------------------------------------------- the page clock

    /** The EXIT phase drains on the exponential law and only hands over at 0.02. */
    @Test
    void theZoomExitDrainsToTheHandOverPoint() {
        PageTransitionDirector director = director();
        director.startPageNav(root(AppPage.CHAT_LIST), root(AppPage.WORLD_CHAT), false);
        assertTrue(director.pageNavZoom(), "the ZOOM style must arm the serial zoom");
        assertTrue(director.zoomExiting(), "a nav starts in the leaving page's phase");
        assertEquals(1.0F, director.navExitAlpha(), 0.0F);
        assertEquals(0.0F, director.navEnterAlpha(), 0.0F);

        director.stepZoomNav(50.0F);
        assertEquals((float) Math.exp(-1.0), director.navExitAlpha(), 1.0e-6F,
                "one 50 ms frame at tau 50 leaves e^-1 of the distance");
        assertTrue(director.zoomExiting(), "0.368 is nowhere near the hand-over point");
        assertEquals(0.0F, director.navEnterAlpha(),
                "the arriving page must not start before the leaving one is gone");

        director.stepZoomNav(50.0F);
        director.stepZoomNav(50.0F);
        assertEquals((float) Math.exp(-3.0), director.navExitAlpha(), 1.0e-6F);
        assertTrue(director.zoomExiting(), "e^-3 = 0.0498 is still above 0.02");
    }

    /**
     * At 0.02 the EXIT phase snaps to zero and the ENTER phase advances in the
     * same step — the sequential ifs exist for exactly this, and a fast frame
     * (or decorative motion off) must not need an extra frame to start arriving.
     */
    @Test
    void theZoomHandsOverToEnterWithinTheSameStep() {
        PageTransitionDirector director = director();
        director.startPageNav(root(AppPage.CHAT_LIST), root(AppPage.WORLD_CHAT), false);
        for (int i = 0; i < 3; i++) {
            director.stepZoomNav(50.0F);
        }
        assertTrue(director.zoomExiting());

        director.stepZoomNav(50.0F);
        assertEquals(0.0F, director.navExitAlpha(), 0.0F, "the leaving page snaps to fully drained");
        assertFalse(director.zoomExiting(), "the hand-over happens inside this step");
        assertEquals(ZOOM_FRACTION, director.navEnterAlpha(), 1.0e-6F,
                "and the arriving page advances in the same step, not the next one");
    }

    /** Decorative motion off drains and settles in one frame, both phases. */
    @Test
    void motionOffSnapsTheZoomInOneFrame() {
        AtomChatConfig.get().animationEnabled = false;
        PageTransitionDirector director = director();
        director.startPageNav(root(AppPage.CHAT_LIST), root(AppPage.WORLD_CHAT), false);
        assertTrue(director.pageNavActive());

        director.stepZoomNav(16.0F);

        assertEquals(0.0F, director.navExitAlpha(), 0.0F);
        assertEquals(1.0F, director.navEnterAlpha(), 0.0F);
        assertFalse(director.pageNavActive(), "the nav lands on the frame it was armed");
    }

    /** The slide rides the same law, with the same dt clamp and the same snap. */
    @Test
    void theSlideProgressRidesTheSameExponentialLaw() {
        PageTransitionDirector director = director();
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
        director.startPageNav(root(AppPage.CHAT_LIST), root(AppPage.WORLD_CHAT), false);
        assertFalse(director.pageNavZoom());
        assertEquals(0.0F, director.navSlideProgress(), 0.0F);

        director.stepSlideNav(50.0F);
        assertEquals(SLIDE_FRACTION, director.navSlideProgress(), 1.0e-6F);

        // The clock clamps a long frame to 50 ms, so a stalled frame cannot overshoot.
        float before = director.navSlideProgress();
        director.stepSlideNav(5_000.0F);
        assertEquals(before + (1.0F - before) * SLIDE_FRACTION, director.navSlideProgress(), 1.0e-6F,
                "a frame longer than 50 ms is clamped to 50 ms");
    }

    /** The push enters from the right; the pop leaves to the right. */
    @Test
    void pageNavDxHasThePushAndPopSigns() {
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;

        PageTransitionDirector push = director();
        push.startPageNav(root(AppPage.CHAT_LIST), root(AppPage.WORLD_CHAT), false);
        assertEquals(100.0F, push.pageNavDx(100.0F), 0.0F,
                "a push starts with the arriving page a full width to the right");
        push.stepSlideNav(50.0F);
        assertEquals(100.0F * (1.0F - SLIDE_FRACTION), push.pageNavDx(100.0F), 1.0e-4F);

        PageTransitionDirector pop = director();
        pop.startPageNav(root(AppPage.WORLD_CHAT), root(AppPage.CHAT_LIST), true);
        assertEquals(0.0F, pop.pageNavDx(100.0F), 0.0F,
                "a pop starts with the leaving page in place");
        pop.stepSlideNav(50.0F);
        assertEquals(100.0F * SLIDE_FRACTION, pop.pageNavDx(100.0F), 1.0e-4F,
                "and slides out to the right — the sign is the role's, not the page kind's");
    }

    /** The settled judgement of both arms, and the field clearing after a finish. */
    @Test
    void thePageClockSettlesAndFinishes() {
        PageTransitionDirector director = director();
        assertFalse(director.pageNavActive(), "an idle clock is not running");

        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
        director.startPageNav(root(AppPage.CHAT_LIST), root(AppPage.WORLD_CHAT), false);
        assertTrue(director.pageNavActive());
        for (int i = 0; i < 9; i++) {
            director.stepSlideNav(50.0F);
        }
        assertTrue(director.pageNavActive(), "1 - e^-6.43 = 0.9984 is still short of settled");
        director.stepSlideNav(50.0F);
        assertEquals(1.0F, director.navSlideProgress(), 0.0F, "the tenth step snaps to settled");
        assertFalse(director.pageNavActive());
    }

    /** A finished pop applies the pending pop, once, before the state is cleared. */
    @Test
    void finishPageNavAppliesThePendinPopThenClearsTheState() {
        PageTransitionDirector director = director();
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
        director.startPageNav(root(AppPage.WORLD_CHAT), root(AppPage.CHAT_LIST), true);

        director.finishPageNav();

        assertEquals(List.of("persistCurrentDraft", "captureReplySlot", "applyPendingPop"), host.calls,
                "the pop is applied once, and only after the draft and reply were captured");
        assertNull(director.pageNavFrom());
        assertNull(director.pageNavTo());
        assertFalse(director.pageNavPopPending());
        assertEquals(0.0F, director.navSlideProgress(), 0.0F);
        assertFalse(director.pageNavActive());
    }

    /** A finished push has nothing to pop. */
    @Test
    void finishPageNavOnAPushAppliesNothing() {
        PageTransitionDirector director = director();
        director.startPageNav(root(AppPage.CHAT_LIST), root(AppPage.WORLD_CHAT), false);

        director.finishPageNav();

        assertEquals(List.of("persistCurrentDraft", "captureReplySlot"), host.calls);
        assertFalse(director.pageNavActive());
    }

    /** Disarming clears the same state without running anything. */
    @Test
    void abandonPageNavClearsWithoutRunningThePop() {
        PageTransitionDirector director = director();
        director.startPageNav(root(AppPage.WORLD_CHAT), root(AppPage.CHAT_LIST), true);

        director.abandonPageNav();

        assertEquals(List.of("persistCurrentDraft", "captureReplySlot"), host.calls);
        assertNull(director.pageNavFrom());
        assertNull(director.pageNavTo());
        assertFalse(director.pageNavPopPending());
        assertEquals(0.0F, director.navSlideProgress(), 0.0F);
    }

    /** The style is resolved once per nav and both arms follow the config. */
    @Test
    void everyNavResolvesTheStyleOncePerPair() {
        AtomChatConfig.PageNavStyle[] styles = {
                AtomChatConfig.PageNavStyle.ZOOM, AtomChatConfig.PageNavStyle.SLIDE};
        for (AtomChatConfig.PageNavStyle configured : styles) {
            AtomChatConfig.get().pageNavStyle = configured;
            for (boolean popPending : new boolean[]{false, true}) {
                PageTransitionDirector director = director();
                director.startPageNav(root(AppPage.CHAT_LIST), root(AppPage.WORLD_CHAT), popPending);
                assertEquals(configured == AtomChatConfig.PageNavStyle.ZOOM, director.pageNavZoom(),
                        "the style is whatever the config says, for a push and for a pop");
                assertEquals(configured, PageTransitionDirector.pageNavStyle());
                assertEquals(configured == AtomChatConfig.PageNavStyle.ZOOM, director.navStyleIsZoom());
                if (director.pageNavZoom()) {
                    assertTrue(director.zoomExiting());
                    assertEquals(1.0F, director.navExitAlpha(), 0.0F);
                    assertEquals(0.0F, director.navEnterAlpha(), 0.0F);
                } else {
                    assertEquals(0.0F, director.navSlideProgress(), 0.0F);
                }
            }
        }
    }

    // -------------------------------------------------------- the tab clock

    /** The slide arm moves the capsule and the content on one clock, then clears. */
    @Test
    void theTabSlideMovesTheCapsuleWithTheContentAndSettles() {
        PageTransitionDirector director = director();
        director.markTabSpan(AppPage.CHAT_LIST, AppPage.PROFILE);
        director.pinTabCapsule(AppPage.CHAT_LIST);
        assertEquals(0.0F, capsule.getValue(), 0.0F, "the capsule starts on the source tab");
        director.applyTabMotion(false);
        director.resetTabCapsule();
        assertEquals(0.0F, capsule.getValue(), 0.0F);

        director.stepRootNav(50.0F);

        assertEquals(SLIDE_FRACTION, director.rootTabProgress(), 1.0e-6F);
        assertEquals(SLIDE_FRACTION, capsule.getValue(), 1.0e-6F,
                "the capsule is the same progress mapped across the from->to cells");

        int steps = 1;
        while (director.rootTransitionActive() && steps < 40) {
            director.stepRootNav(50.0F);
            steps++;
        }
        assertEquals(10, steps, "1 - e^(-0.714n) enters the 0.001 snap band on the tenth frame");
        assertEquals(1.0F, director.rootTabProgress(), 0.0F);
        assertEquals(1.0F, capsule.getValue(), 1.0e-6F, "the capsule lands on the target cell");
        assertFalse(director.rootTransitionActive(), "a completed transition is cleared");
        assertEquals(-1, director.rootTabFrom());
        assertEquals(-1, director.rootTabTo());
    }

    /** The zoom arm keeps its own capsule travel and its own two-phase clock. */
    @Test
    void theTabZoomRunsTheSerialClockAndTheCapsuleLandsWithIt() {
        PageTransitionDirector director = director();
        director.markTabSpan(AppPage.CHAT_LIST, AppPage.SETTINGS);
        director.pinTabCapsule(AppPage.CHAT_LIST);
        director.applyTabMotion(true);
        director.resetTabCapsule();
        assertTrue(director.rootTabZoom());
        assertTrue(director.rootZoomExiting(), "the tab zoom starts on the leaving tab");
        assertEquals(1.0F, director.rootZoomExitAlpha(), 0.0F);

        director.stepRootNav(50.0F);

        assertEquals((float) Math.exp(-1.0), director.rootZoomExitAlpha(), 1.0e-6F);
        assertEquals(2.0F * ZOOM_FRACTION, capsule.getValue(), 1.0e-6F,
                "on a zoom the capsule keeps its own exponential travel toward the target cell");

        director.stepRootNav(50.0F);
        director.stepRootNav(50.0F);
        assertTrue(director.rootZoomExiting());
        director.stepRootNav(50.0F);
        assertEquals(0.0F, director.rootZoomExitAlpha(), 0.0F);
        assertFalse(director.rootZoomExiting(), "the tab zoom hands over at 0.02 like the page zoom");

        int steps = 4;
        while (director.rootTransitionActive() && steps < 40) {
            director.stepRootNav(50.0F);
            steps++;
        }
        assertEquals(10, steps, "the enter phase snaps on the tenth frame");
        assertFalse(director.rootTransitionActive());
        assertEquals(-1, director.rootTabFrom());
        assertEquals(-1, director.rootTabTo());
    }

    /** Without motion the tab lands on the frame it was armed. */
    @Test
    void motionOffSnapsTheTabInOneFrame() {
        AtomChatConfig.get().animationEnabled = false;
        PageTransitionDirector director = director();
        director.markTabSpan(AppPage.CHAT_LIST, AppPage.SETTINGS);
        director.applyTabMotion(false);
        director.resetTabCapsule();

        director.stepRootNav(16.0F);

        assertFalse(director.rootTransitionActive());
        assertEquals(1.0F, director.rootTabProgress(), 0.0F);
        assertEquals(2.0F, capsule.getValue(), 1.0e-6F);
    }

    /** An idle or single-tab transition is never active. */
    @Test
    void theTabClockIsIdleUntilASpanIsArmed() {
        PageTransitionDirector director = director();
        assertFalse(director.rootTransitionActive());

        director.markTabSpan(AppPage.PROFILE, AppPage.PROFILE);
        assertFalse(director.rootTransitionActive(), "a switch to the same tab animates nothing");
        director.stepRootNav(50.0F);
        assertEquals(1, director.rootTabFrom(),
                "the clock never steps an inactive span, so the span stays as armed -- harmless,"
                        + " because every reader tests rootTransitionActive() first");

        director.markTabSpan(AppPage.CHAT_LIST, AppPage.PROFILE);
        assertTrue(director.rootTransitionActive());
        director.clearRootTransition();
        assertFalse(director.rootTransitionActive());
        assertEquals(-1, director.rootTabTo());
    }

    /** Both motions write the capsule, whichever style is armed. */
    @Test
    void theCapsuleIsWrittenOnlyByTheTabClock() {
        PageTransitionDirector director = director();
        director.markTabSpan(AppPage.CHAT_LIST, AppPage.PROFILE);
        director.applyTabMotion(false);
        director.resetTabCapsule();
        director.stepRootNav(50.0F);
        float slideCapsule = capsule.getValue();

        PageTransitionDirector other = new PageTransitionDirector(host, new Animator(t -> t));
        other.markTabSpan(AppPage.CHAT_LIST, AppPage.PROFILE);
        other.applyTabMotion(true);
        other.resetTabCapsule();
        other.stepRootNav(50.0F);

        assertTrue(slideCapsule > 0.0F && slideCapsule < 1.0F);
    }

    // ----------------------------------------------------------- the tables

    /** The index tables, both ways, and the pushed pages that have no cell. */
    @Test
    void theTabTablesMapBothWays() {
        PageTransitionDirector director = director();
        assertEquals(AppPage.CHAT_LIST, director.rootPageForIndex(0));
        assertEquals(AppPage.PROFILE, director.rootPageForIndex(1));
        assertEquals(AppPage.SETTINGS, director.rootPageForIndex(2));
        assertEquals(0, director.rootIndex(AppPage.CHAT_LIST));
        assertEquals(1, director.rootIndex(AppPage.PROFILE));
        assertEquals(2, director.rootIndex(AppPage.SETTINGS));

        assertThrows(IllegalStateException.class, () -> director.rootPageForIndex(3));
        for (AppPage pushed : List.of(AppPage.WORLD_CHAT, AppPage.PRIVATE_CHAT,
                AppPage.SETTINGS_SECTION, AppPage.PROFILE_DETAIL)) {
            assertThrows(IllegalStateException.class, () -> director.rootIndex(pushed),
                    pushed + " has no bottom tab cell");
        }
    }

    /** The pure-overdraw judgement, at its boundaries. */
    @Test
    void theSettledLayerIsSkippable() {
        assertTrue(PageTransitionDirector.navLayerSkippable(1.0F, 1.0F));
        assertTrue(PageTransitionDirector.navLayerSkippable(0.999F, 1.0F));
        assertFalse(PageTransitionDirector.navLayerSkippable(0.998F, 1.0F));
        assertTrue(PageTransitionDirector.navLayerSkippable(1.0F, 1.0005F));
        assertTrue(PageTransitionDirector.navLayerSkippable(1.0F, 0.9995F));
        assertFalse(PageTransitionDirector.navLayerSkippable(1.0F, 0.998F));
        assertFalse(PageTransitionDirector.navLayerSkippable(0.5F, 1.0F));
    }

    /**
     * The nav scale is centred on the panel and reaches the canvas as a matrix:
     * at scale 1 it composes nothing at all (the pure-overdraw case the settle
     * judgement pairs with), and at scale s it is {@code translate(c).scale(s).
     * translate(-c)} about the panel middle.
     */
    @Test
    void theNavScaleIsCentredOnThePanel() {
        UiLayout.Rect panel = new UiLayout.Rect(10.0F, 20.0F, 100.0F, 60.0F);
        float cx = 10.0F + 100.0F / 2.0F;
        float cy = 20.0F + 60.0F / 2.0F;

        try (Surface surface = Surface.makeRasterN32Premul(160, 120)) {
            Canvas canvas = surface.getCanvas();

            PageTransitionDirector.applyNavScale(canvas, panel, 1.0F);
            float[] identity = canvas.getLocalToDeviceAsMatrix33().getMat();
            assertEquals(1.0F, identity[0], 1.0e-6F, "scale 1 must not touch the canvas");
            assertEquals(1.0F, identity[4], 1.0e-6F);
            assertEquals(0.0F, identity[2], 1.0e-6F);
            assertEquals(0.0F, identity[5], 1.0e-6F);

            PageTransitionDirector.applyNavScale(canvas, panel, 0.5F);
            float[] scaled = canvas.getLocalToDeviceAsMatrix33().getMat();
            assertEquals(0.5F, scaled[0], 1.0e-6F);
            assertEquals(0.5F, scaled[4], 1.0e-6F);
            assertEquals(cx * (1.0F - 0.5F), scaled[2], 1.0e-4F,
                    "the panel middle is the pivot, so it is the only fixed point");
            assertEquals(cy * (1.0F - 0.5F), scaled[5], 1.0e-4F);
        }
    }

    /** The style resolver is one line over the config, and nothing else. */
    @Test
    void theStyleResolverReadsTheConfig() {
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
        assertEquals(AtomChatConfig.PageNavStyle.SLIDE, PageTransitionDirector.pageNavStyle());
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        assertEquals(AtomChatConfig.PageNavStyle.ZOOM, PageTransitionDirector.pageNavStyle());
    }

    /** A nav captures the draft and the reply slot before it arms anything. */
    @Test
    void startingANavCapturesTheDraftAndTheReplyFirst() {
        PageTransitionDirector director = director();
        director.startPageNav(root(AppPage.CHAT_LIST), root(AppPage.WORLD_CHAT), false);

        assertEquals(List.of("persistCurrentDraft", "captureReplySlot"), host.calls,
                "the reply bar is captured with the draft, before the pages change");

        // The capture happens before the state is armed, not after: a nav that
        // threw here would leave no half-armed transition behind.
        assertEquals(root(AppPage.CHAT_LIST), director.pageNavFrom());
        assertEquals(root(AppPage.WORLD_CHAT), director.pageNavTo());
    }

    /** UiMotion is the law both clocks are asserted against. */
    @Test
    void theAssertedFractionIsTheSharedLaw() {
        assertEquals(ZOOM_FRACTION, UiMotion.expApproach(0.0F, 1.0F, 50.0F, 50.0F), 1.0e-6F);
        assertEquals(1.0F, UiMotion.expApproach(0.0F, 1.0F, 50.0F, 0.0F), 0.0F,
                "a zero tau snaps; that is what decorative-motion-off rides on");
    }
}
