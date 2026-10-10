package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The D25 contract: the panel shell's own arithmetic, by value.
 *
 * <p><b>Why these numbers and not a frame.</b> The golden scenes render page
 * objects; the panel frame — its open/close transform, its blur capture rect,
 * its fade layer — is not in them at all, so a clean golden diff proves nothing
 * about any of this. What can be pinned is the arithmetic: the two frame clocks
 * (whose floors differ on purpose: 0 ms for the render clock, 1 ms for the
 * domain clock), the open/close tau split, the 6% close cut-off at its exact
 * boundary, the open transform's endpoints, the fade layer's covering rect, the
 * cadence gate's 0.01 px epsilon — and, the one that carries a real accident,
 * that the blur capture rect <em>is</em> the rect the open transform paints,
 * carried through the density hop.
 */
class PanelOpenMotionTest {

    private boolean animations;
    private AtomChatConfig.PageNavStyle style;

    /**
     * The config is read through {@code Platform}, so this class installs its own
     * temporary one: a filtered run ({@code --tests}) would otherwise fail here
     * with "Platform not installed" rather than with a number.
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

    /** One 16 ms frame on each clock, as numbers. */
    private static final long FRAME_NANOS = 16L * 1_000_000L;

    // ---------------------------------------------------------- frame clocks

    /** The render clock: real nanos, clamped to [0, 50] ms, never negative. */
    @Test
    void theRenderClockClampsToFiftyMsAndNeverGoesBackwards() {
        assertEquals(0.0F, PanelOpenMotion.frameDtMs(1_000L, 1_000L), 0.0F);
        assertEquals(16.0F, PanelOpenMotion.frameDtMs(0L, -FRAME_NANOS), 1.0e-4F);
        assertEquals(50.0F, PanelOpenMotion.frameDtMs(FRAME_NANOS * 100, 0L), 0.0F,
                "a stalled frame must not hand the animation more than 50 ms");
        assertEquals(0.0F, PanelOpenMotion.frameDtMs(0L, FRAME_NANOS), 0.0F,
                "a clock that went backwards (pause, debugger) must not run an animation backwards");
    }

    /**
     * The domain clock clamps to [1, 50] ms. The floor is 1, not 0, on purpose:
     * every hover and spring stepper divides by this value, so the first frame
     * and a same-millisecond frame must still read as one millisecond.
     */
    @Test
    void theDomainClockFloorsAtOneMillisecond() {
        assertEquals(1L, PanelOpenMotion.domainFrameDtMs(1_000L, 1_000L));
        assertEquals(1L, PanelOpenMotion.domainFrameDtMs(0L, FRAME_NANOS),
                "the two clocks disagree here by design -- do not make them match");
        assertEquals(16L, PanelOpenMotion.domainFrameDtMs(16L, 0L));
        assertEquals(50L, PanelOpenMotion.domainFrameDtMs(10_000L, 0L));
    }

    // ------------------------------------------------------- open and close

    /** Opening rides the open tau, closing the shorter close tau. */
    @Test
    void theProgressTauDependsOnTheDirection() {
        float open = PanelOpenMotion.stepProgress(0.0F, false, 50.0F, true);
        assertEquals(1.0F - (float) Math.exp(-50.0 / 70.0), open, 1.0e-6F,
                "opening rides PANEL_OPEN_TAU_MS = 70");

        float closing = PanelOpenMotion.stepProgress(1.0F, true, 50.0F, true);
        assertEquals((float) Math.exp(-50.0 / 40.0), closing, 1.0e-6F,
                "closing rides the shorter PANEL_CLOSE_TAU_MS = 40");
    }

    /** Decorative motion off lands the panel on the frame it was asked to. */
    @Test
    void motionOffSnapsThePanelInOneFrame() {
        assertEquals(1.0F, PanelOpenMotion.stepProgress(0.0F, false, 16.0F, false), 0.0F);
        assertEquals(0.0F, PanelOpenMotion.stepProgress(1.0F, true, 16.0F, false), 0.0F);
    }

    /** Settled is the exact landing; animating is its inverse (the input gate's question). */
    @Test
    void settledIsTheExactLanding() {
        assertTrue(PanelOpenMotion.settled(1.0F, false));
        assertTrue(PanelOpenMotion.settled(0.0F, true));
        assertFalse(PanelOpenMotion.settled(0.999F, false));
        assertFalse(PanelOpenMotion.settled(0.001F, true));
        assertTrue(PanelOpenMotion.animating(0.999F, false));
        assertFalse(PanelOpenMotion.animating(1.0F, false));
    }

    /** The close is cut at exactly 6% left, and a settled close is due as well. */
    @Test
    void theCloseIsDueAtSixPercent() {
        assertTrue(PanelOpenMotion.closeDue(0.0F, true), "a settled close is due");
        assertTrue(PanelOpenMotion.closeDue(0.0599F, true));
        assertFalse(PanelOpenMotion.closeDue(0.06F, true), "the threshold is strict");
        assertFalse(PanelOpenMotion.closeDue(0.5F, true));
        assertFalse(PanelOpenMotion.closeDue(0.0F, false), "an opening panel never closes");
        assertFalse(PanelOpenMotion.closeDue(1.0F, false));
    }

    /** The fade is the progress, clamped -- never anything else. */
    @Test
    void theFadeIsTheClampedProgress() {
        assertEquals(0.0F, PanelOpenMotion.fade(-0.5F), 0.0F);
        assertEquals(0.25F, PanelOpenMotion.fade(0.25F), 0.0F);
        assertEquals(1.0F, PanelOpenMotion.fade(1.5F), 0.0F);
    }

    // --------------------------------------------------- the open transform

    /** ZOOM grows the panel from the shape floor to 1; SLIDE never scales it. */
    @Test
    void theOpenScaleFollowsTheStyle() {
        assertEquals(PanelOpenMotion.OPEN_MIN_SCALE,
                PanelOpenMotion.openScale(0.0F, AtomChatConfig.PageNavStyle.ZOOM), 0.0F);
        assertEquals(0.97F, PanelOpenMotion.openScale(0.5F, AtomChatConfig.PageNavStyle.ZOOM), 1.0e-6F);
        assertEquals(1.0F, PanelOpenMotion.openScale(1.0F, AtomChatConfig.PageNavStyle.ZOOM), 1.0e-6F);
        assertEquals(1.0F, PanelOpenMotion.openScale(0.0F, AtomChatConfig.PageNavStyle.SLIDE), 0.0F);
        assertEquals(1.0F, PanelOpenMotion.openScale(0.5F, AtomChatConfig.PageNavStyle.SLIDE), 0.0F);
    }

    /** SLIDE pushes in from the left by one slide unit; ZOOM never slides. */
    @Test
    void theOpenSlideFollowsTheStyle() {
        assertEquals(-UiTokens.s(24), PanelOpenMotion.openSlidePx(0.0F, AtomChatConfig.PageNavStyle.SLIDE), 0.0F);
        assertEquals(-UiTokens.s(24) / 2.0F,
                PanelOpenMotion.openSlidePx(0.5F, AtomChatConfig.PageNavStyle.SLIDE), 1.0e-5F);
        assertEquals(0.0F, PanelOpenMotion.openSlidePx(1.0F, AtomChatConfig.PageNavStyle.SLIDE), 0.0F);
        assertEquals(0.0F, PanelOpenMotion.openSlidePx(0.0F, AtomChatConfig.PageNavStyle.ZOOM), 0.0F);
        assertNotEquals(0.0F, UiTokens.s(24), "the slide unit must be a real distance");
    }

    // ------------------------------------------------------------- geometry

    /**
     * The capture rect is the painted rect: the same derivation, carried through
     * the density hop. This is the contract the comment in the shell warns about
     * -- two copies of this arithmetic drift apart and the blurred region leaves
     * the panel mid-flight.
     */
    @Test
    void theBlurCaptureRectIsThePaintedRect() {
        float x = 10.0F;
        float y = 20.0F;
        float w = 200.0F;
        float h = 400.0F;
        float stroke = 3.0F;

        for (AtomChatConfig.PageNavStyle configured : AtomChatConfig.PageNavStyle.values()) {
            for (float progress : new float[]{0.0F, 0.5F, 1.0F}) {
                PanelOpenMotion.PanelRect painted =
                        PanelOpenMotion.paintedPanelRect(progress, configured, x, y, w, h, stroke);
                PanelOpenMotion.PanelRect capture =
                        PanelOpenMotion.blurCapture(progress, configured, x, y, w, h, stroke, 1.0, 1.0);
                assertEquals(painted.x(), capture.x(), 1.0e-4F);
                assertEquals(painted.y(), capture.y(), 1.0e-4F);
                assertEquals(painted.w(), capture.w(), 1.0e-4F);
                assertEquals(painted.h(), capture.h(), 1.0e-4F);
                assertEquals(painted.radius(), capture.radius(), 1.0e-4F);
            }
        }
    }

    /** Settled, the painted rect is the panel inset by the stroke -- nothing else. */
    @Test
    void theSettledPaintedRectIsTheInsetPanel() {
        for (AtomChatConfig.PageNavStyle configured : AtomChatConfig.PageNavStyle.values()) {
            PanelOpenMotion.PanelRect settled =
                    PanelOpenMotion.paintedPanelRect(1.0F, configured, 10.0F, 20.0F, 200.0F, 400.0F, 3.0F);
            assertEquals(13.0F, settled.x(), 1.0e-4F);
            assertEquals(23.0F, settled.y(), 1.0e-4F);
            assertEquals(194.0F, settled.w(), 1.0e-4F);
            assertEquals(394.0F, settled.h(), 1.0e-4F);
            assertEquals(UiTokens.panelRadius() - 3.0F, settled.radius(), 1.0e-4F);
        }
    }

    /** The capture hop is the density over the GUI scale, on every component. */
    @Test
    void theCaptureHopIsDensityOverGuiScale() {
        PanelOpenMotion.PanelRect painted =
                PanelOpenMotion.paintedPanelRect(1.0F, AtomChatConfig.PageNavStyle.SLIDE,
                        0.0F, 0.0F, 100.0F, 50.0F, 2.0F);
        PanelOpenMotion.PanelRect capture =
                PanelOpenMotion.blurCapture(1.0F, AtomChatConfig.PageNavStyle.SLIDE,
                        0.0F, 0.0F, 100.0F, 50.0F, 2.0F, 2.0, 4.0);
        assertEquals(painted.x() * 2.0F / 4.0, capture.x(), 1.0e-4F);
        assertEquals(painted.w() * 2.0F / 4.0, capture.w(), 1.0e-4F);
        assertEquals(painted.radius() * 2.0F / 4.0, capture.radius(), 1.0e-4F);
    }

    /** The fade layer covers the scaled panel plus its slide excursion and slack. */
    @Test
    void theFadeLayerCoversTheScaledPanelAndItsSlide() {
        PanelOpenMotion.LayerRect zoom = PanelOpenMotion.fadeLayer(1.0F, AtomChatConfig.PageNavStyle.ZOOM,
                10.0F, 20.0F, 100.0F, 50.0F, 32.0F);
        assertEquals(10.0F - 32.0F, zoom.x(), 1.0e-4F);
        assertEquals(20.0F - 32.0F, zoom.y(), 1.0e-4F);
        assertEquals(100.0F + 64.0F, zoom.w(), 1.0e-4F);
        assertEquals(50.0F + 64.0F, zoom.h(), 1.0e-4F);

        PanelOpenMotion.LayerRect slide = PanelOpenMotion.fadeLayer(0.0F, AtomChatConfig.PageNavStyle.SLIDE,
                10.0F, 20.0F, 100.0F, 50.0F, 32.0F);
        float unit = UiTokens.s(24);
        assertEquals(10.0F - unit - 32.0F, slide.x(), 1.0e-3F,
                "the layer must reach a full slide to the left");
        assertEquals(100.0F + 64.0F + unit, slide.w(), 1.0e-3F);
    }

    /** The cadence gate: motion of either kind keeps the fast refresh due. */
    @Test
    void theCadenceGateReadsPanelAndChromeMotion() {
        assertTrue(PanelOpenMotion.chromeAnimating(false, 10.0F, 10.0F), "a moving panel is motion");
        assertFalse(PanelOpenMotion.chromeAnimating(true, 10.0F, 10.0F));
        assertTrue(PanelOpenMotion.chromeAnimating(true, 10.02F, 10.0F));
        assertFalse(PanelOpenMotion.chromeAnimating(true, 10.005F, 10.0F),
                "sub-epsilon composer growth is layout noise, not chrome motion");
        assertFalse(PanelOpenMotion.chromeAnimating(true, 10.0F, 9.995F),
                "and it is symmetric: a shrink is noise too");
        assertTrue(PanelOpenMotion.chromeAnimating(true, 10.25F, 10.0F));
    }
}
