package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arrival composite's contract, rewritten to the Dango semantics.
 *
 * <p><b>What was inverted, and why (the old six-path suite is gone).</b> The
 * previous composite choreographed <em>two</em> pages per frame — a leaving
 * page and an arriving one, each with its own layer, offset and alpha, over a
 * per-style state machine (SLIDE progress, serial ZOOM phases, a fast/interrupt
 * clock, a root-tab clock beside it). Users rejected the consequences: back
 * clicks during the animation landed with no transition, the global ZOOM style
 * still slid the tab content hard, the SLIDE entrance jumped, a leaving page's
 * back arrow outlived its page, and a re-opened section could not interrupt.
 * The Dango reference has none of that: the old page simply stops drawing, the
 * arriving page draws whole and interactive from its first frame, and the only
 * transition is its entrance overlay. This suite pins exactly that:</p>
 *
 * <ul>
 *   <li>the stamp: a page-key change arms the arrival once, first frame arms
 *       nothing (a fresh screen is settled by default);</li>
 *   <li>the envelope numbers: SLIDE drifts in 44px while fading on the 320ms
 *       envelope, ZOOM scales 0.92 -&gt; 1 — both style variants read the one
 *       config field (no per-pair exception is possible any more). Both run
 *       easeOutQuad, so the travel is front-loaded and the fade trails it by
 *       a fraction of the clock;</li>
 *   <li>the interrupt: a key change while an entrance is still in flight
 *       replays the same 320ms envelope; after it settled, the same envelope
 *       plays;</li>
 *   <li>no exit phase: only the arriving page's draws happen, once per
 *       frame;</li>
 *   <li>the settings section switch re-stamps — the deliberate inversion of
 *       Dango's non-resetting settings tabs (its {@code MusicScreen} keeps tag
 *       switches out of the page key to avoid a re-fade flash; AtomChat's
 *       sections are pushed pages and the user asked for an interruptible,
 *       resetting entrance there);</li>
 *   <li>canvas discipline: the envelope's save points balance, and a settled
 *       frame adds no canvas state at all.</li>
 * </ul>
 *
 * <p>Numbers are asserted on a real Skija raster surface through the recording
 * host's landing matrix, and the fade is asserted on pixels, not just floats —
 * the same evidence standard the old suite set.</p>
 */
class PageTransitionCompositeTest {

    // ------------------------------------------------------------ the surface

    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;
    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;

    /** Inside the panel, inside every transform used here. */
    private static final int BAND_Y = 400;

    private static final UiLayout LAYOUT = UiLayout.ofDetail(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

    /** Opaque and alone in its channel, so a moved or faded block changes pixels. */
    private static final int RED = 0xFFE00000;
    private static final int BG = 0xFF101010;

    /** The body block's rest rect (inside the panel, well away from the edges). */
    private static final float BLOCK_X = 100.0F;
    private static final float BLOCK_Y = BAND_Y - 20;
    private static final float BLOCK_W = 80.0F;
    private static final float BLOCK_H = 40.0F;

    private static final NavPage WORLD = NavPage.of(AppPage.WORLD_CHAT);
    private static final NavPage PROFILE_DETAIL = NavPage.of(AppPage.PROFILE_DETAIL);
    private static final NavPage APPEARANCE = NavPage.settingsSection(SettingsSection.APPEARANCE);
    private static final NavPage CHAT_SECTION = NavPage.settingsSection(SettingsSection.CHAT);

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

    @TempDir
    Path tmp;

    private boolean animations;
    private AtomChatConfig.PageNavStyle style;

    @BeforeEach
    void setUp() {
        AtomChatConfig config = AtomChatConfig.get();
        animations = config.animationEnabled;
        style = config.pageNavStyle;
        config.animationEnabled = true;
        config.pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
    }

    @AfterEach
    void tearDown() {
        AtomChatConfig config = AtomChatConfig.get();
        config.animationEnabled = animations;
        config.pageNavStyle = style;
    }

    // ----------------------------------------------------------- the fixtures

    /** Where and how big the arriving body actually drew (device matrix). */
    private record Landing(float dx, float dy, float scaleX) {
    }

    /**
     * The recording host: two one-line forwards, exactly as on the screen. The
     * body paints one flat block and remembers its matrix; the header also
     * records its matrix, because the header is chrome and must draw outside
     * the envelope (the reported "switching pages moves the top bar").
     */
    private static final class ProbeHost implements PageTransitionComposite.Host {
        NavPage top = WORLD;
        final List<String> calls = new ArrayList<>();
        final List<Landing> landings = new ArrayList<>();
        /** The matrix the header drew under — must always be the identity. */
        final List<Landing> headerLandings = new ArrayList<>();
        /** Whether the body runs its own per-item stagger (skips the envelope). */
        boolean staggersSurfaces;
        /** Whether the body paints its probe colour (offset-only reads skip it). */
        boolean paint = true;

        @Override
        public NavPage topNav() {
            return top;
        }

        @Override
        public void drawArrivingBody(Canvas canvas, UiLayout layout, int mouseX, int mouseY) {
            calls.add("body@" + top.page());
            float[] m = canvas.getLocalToDeviceAsMatrix33().getMat();
            landings.add(new Landing(m[2], m[5], m[0]));
            if (paint) {
                canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(
                        BLOCK_X, BLOCK_Y, BLOCK_W, BLOCK_H),
                        new io.github.humbleui.skija.Paint().setColor(RED));
            }
        }

        @Override
        public void drawArrivingHeader(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                       float vmx, float vmy) {
            calls.add("header@" + top.page());
            float[] m = canvas.getLocalToDeviceAsMatrix33().getMat();
            headerLandings.add(new Landing(m[2], m[5], m[0]));
        }

        @Override
        public boolean arrivalStaggersSurfaces() {
            return staggersSurfaces;
        }
    }

    private final ProbeHost host = new ProbeHost();
    private final PageTransitionComposite composite = new PageTransitionComposite(host);

    private static Surface surface() {
        return Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
    }

    /** The whole raster surface, read back through a bitmap (the repo's idiom). */
    private static byte[] readPixels(Surface surface) {
        io.github.humbleui.skija.Bitmap bitmap = new io.github.humbleui.skija.Bitmap();
        try {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            return bitmap.readPixels();
        } finally {
            bitmap.close();
        }
    }

    /** One pixel as {r, g, b} (bitmap rows are B-G-R-A ordered). */
    private static int[] pixelAt(Surface s, int x, int y) {
        byte[] px = readPixels(s);
        int at = (y * SURFACE_W + x) * 4;
        return new int[]{px[at + 2] & 0xFF, px[at + 1] & 0xFF, px[at] & 0xFF};
    }

    private static boolean isRed(int[] rgb) {
        return rgb[0] > 0xC0 && rgb[1] < 0x40 && rgb[2] < 0x40;
    }

    /**
     * A red block composited at partial opacity: still red-dominant, but its
     * red channel scales with the fade. The full-opacity {@link #isRed} can't
     * judge a mid-entrance frame — with Dango's 320ms clock the fade is only
     * ~67% at 100ms, which is the correct behaviour, not a missing draw.
     */
    private static boolean isRedDominant(int[] rgb) {
        return rgb[0] > 0x50 && rgb[0] > rgb[1] + 0x28 && rgb[0] > rgb[2] + 0x28;
    }

    /** One full frame: stamp, then draw the pushed page. */
    private void frame(Surface s, long nowMs) {
        composite.beginFrame(nowMs);
        composite.consumeArrivalEvent();
        try (Canvas canvas = s.getCanvas()) {
            canvas.clear(BG);
            composite.drawArrivingFrame(canvas, LAYOUT, 0, 0, 0, 0, nowMs);
        }
    }

    // ------------------------------------------------------------- the stamp

    /** A fresh screen's first frame establishes the key and plays nothing. */
    @Test
    void firstFrameEstablishesTheKeySilently() {
        long t0 = 1_000_000L;
        composite.beginFrame(t0);
        assertFalse(composite.consumeArrivalEvent(), "the establishing frame is not an arrival");
        assertTrue(composite.overlaySettled(t0), "a fresh screen is settled by default");
    }

    /** A page-key change arms the arrival exactly once, and never while settled. */
    @Test
    void keyChangeStampsAnArrivalOnce() {
        long t0 = 1_000_000L;
        composite.beginFrame(t0);
        composite.consumeArrivalEvent();

        host.top = PROFILE_DETAIL;
        composite.beginFrame(t0 + 1);
        assertTrue(composite.consumeArrivalEvent(), "the key change is an arrival");
        assertFalse(composite.consumeArrivalEvent(), "the arrival flag is one-shot");
    }

    /**
     * The settings section switch re-stamps: same page kind, different section —
     * a NavPage equality, so the section id is part of the key. This is the
     * deliberate inversion of Dango's settings tabs (see the class note), and it
     * is what makes "exit appearance, tap appearance again" replay the entrance.
     */
    @Test
    void settingsSectionSwitchReStampsTheKey() {
        long t0 = 1_000_000L;
        host.top = APPEARANCE;
        composite.beginFrame(t0);
        composite.consumeArrivalEvent();

        host.top = CHAT_SECTION;
        composite.beginFrame(t0 + 1);
        assertTrue(composite.consumeArrivalEvent(),
                "a section switch is a key change and re-stamps the entrance");
    }

    // ------------------------------------------------------ the SLIDE overlay

    /**
     * The SLIDE variant: the arriving page drifts in {@code PAGE_SLIDE_PX} from
     * the right while fading, over the 320ms page clock. At the arrival frame
     * the drift is the full 44px and the fade is 0 (no jump: the page never
     * draws at rest and fully opaque on its first frame — that was the
     * "SLIDE 进页跳一下" bug); at rest the drift is 0 and the page is fully there.
     *
     * <p>The mid-frame numbers moved when the motion curve became easeOutQuad:
     * half the clock is already 75% of the travel (0.25 * 44 = 11px left), so
     * the assertion pins "front-loaded but still moving", not "half way". The
     * fade trails the motion by {@code PAGE_ALPHA_DELAY_MS}, so at that point
     * it is ~0.71 rather than 0.5 — the page is readable while it still
     * drifts, which is the whole point of the delay.</p>
     */
    @Test
    void slideOverlayEntersFromFortyFourPxWithAFade() {
        try (Surface s = surface()) {
            long t0 = 1_000_000L;
            frame(s, t0);                       // establish WORLD

            host.top = PROFILE_DETAIL;
            frame(s, t0 + 1);                   // the arrival frame
            Landing arrival = host.landings.get(host.landings.size() - 1);
            assertEquals(UiStagger.PAGE_SLIDE_PX, arrival.dx(), 1.0e-3F,
                    "the entrance starts at the full page drift (UiStagger.PAGE_SLIDE_PX)");
            assertEquals(0.0F, composite.overlayAlpha(t0 + 1), 1.0e-6F,
                    "the entrance starts fully transparent");
            assertFalse(isRed(pixelAt(s, (int) BLOCK_X + 10, BAND_Y)),
                    "alpha 0 paints nothing (and the old page paints nothing either)");

            long mid = t0 + 1 + 160;            // half the 320ms page clock
            frame(s, mid);
            Landing drifting = host.landings.get(host.landings.size() - 1);
            assertTrue(drifting.dx() > 0.0F && drifting.dx() < UiStagger.PAGE_SLIDE_PX,
                    "mid-entrance the drift is decaying: " + drifting.dx());
            assertTrue(composite.overlayAlpha(mid) > 0.6F,
                    "the fade is well up by half the clock: " + composite.overlayAlpha(mid));
            assertEquals(UiStagger.PAGE_SLIDE_PX * 0.25F, drifting.dx(), 0.8F,
                    "easeOutQuad front-loads the travel: a quarter of the drift is left, not half");
            assertTrue(isRedDominant(pixelAt(s, (int) (BLOCK_X + drifting.dx()) + 10, BAND_Y)),
                    "the arriving page is visible at its offset, fading in");

            frame(s, t0 + 1 + 330);
            Landing settled = host.landings.get(host.landings.size() - 1);
            assertEquals(0.0F, settled.dx(), 1.0e-3F, "at rest the drift is 0");
            assertTrue(composite.overlaySettled(t0 + 1 + 330), "the page clock landed");
            assertTrue(isRed(pixelAt(s, (int) BLOCK_X + 10, BAND_Y)),
                    "the settled page draws at its rest position");
        }
    }

    /**
     * The header is chrome: it draws outside the envelope at its final
     * position, on every entrance frame, whatever the style.
     */
    @Test
    void theHeaderNeverMovesWithThePage() {
        try (Surface s = surface()) {
            long t0 = 7_000_000L;
            frame(s, t0);

            host.top = PROFILE_DETAIL;
            frame(s, t0 + 1);
            frame(s, t0 + 1 + 60);
            for (Landing header : host.headerLandings) {
                assertEquals(0.0F, header.dx(), 0.0F, "the header never drifts");
                assertEquals(0.0F, header.dy(), 0.0F, "the header never rises");
                assertEquals(1.0F, header.scaleX(), 0.0F, "the header never scales");
            }
            Landing body = host.landings.get(host.landings.size() - 1);
            assertTrue(body.dx() > 0.0F, "while the page body is still moving");
        }
    }

    /**
     * A list page that staggers its own rows draws without the envelope — its
     * body sees the identity matrix on the arrival frame, so the page never
     * drifts while its rows rise (one transition, one motion).
     */
    @Test
    void aStaggeringSurfaceSkipsTheWholePageEnvelope() {
        try (Surface s = surface()) {
            long t0 = 8_000_000L;
            frame(s, t0);

            host.top = APPEARANCE;
            host.staggersSurfaces = true;
            frame(s, t0 + 1);
            Landing body = host.landings.get(host.landings.size() - 1);
            assertEquals(0.0F, body.dx(), 0.0F,
                    "a staggering surface is not drifted by the shell");
            assertTrue(isRed(pixelAt(s, (int) BLOCK_X + 10, BAND_Y)),
                    "and it is drawn at full opacity from the first frame");
        }
    }

    // ------------------------------------------------------- the ZOOM overlay

    /**
     * The ZOOM variant: the arriving page settles 0.92 -&gt; 1 while fading, and
     * there is no horizontal drift. Both variants read the one config field, so
     * a style switch changes the motion, not just a flag.
     */
    @Test
    void zoomOverlayScalesFromNinetyTwoPercent() {
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        try (Surface s = surface()) {
            long t0 = 2_000_000L;
            frame(s, t0);

            host.top = PROFILE_DETAIL;
            frame(s, t0 + 1);
            Landing arrival = host.landings.get(host.landings.size() - 1);
            assertEquals(UiStagger.ZOOM_START_SCALE, arrival.scaleX(), 1.0e-3F,
                    "the entrance starts at the zoom floor (UiStagger.ZOOM_START_SCALE)");
            assertEquals(0.0F, composite.overlayAlpha(t0 + 1), 1.0e-6F);

            long mid = t0 + 1 + 100;
            frame(s, mid);
            Landing settling = host.landings.get(host.landings.size() - 1);
            assertTrue(settling.scaleX() > UiStagger.ZOOM_START_SCALE && settling.scaleX() < 1.0F,
                    "mid-entrance the scale is settling: " + settling.scaleX());

            frame(s, t0 + 1 + 330);
            assertTrue(composite.overlaySettled(t0 + 1 + 330));
            Landing settled = host.landings.get(host.landings.size() - 1);
            assertEquals(1.0F, settled.scaleX(), 1.0e-3F);
        }
    }

    // ------------------------------------------------------------- interrupts

    /**
     * An interrupt replays the normal entrance: a key change at any point
     * restarts the same 320ms envelope — the shortened 120ms interrupt clock
     * was deleted (in the field it read as "no fade, it jumps").
     */
    @Test
    void interruptReplaysTheNormalEnvelope() {
        try (Surface s = surface()) {
            long t0 = 3_000_000L;
            frame(s, t0);

            host.top = PROFILE_DETAIL;
            frame(s, t0 + 1);                   // normal arrival, envelope in flight
    
            host.top = WORLD;
            composite.beginFrame(t0 + 51);      // 50ms into the page clock
            assertTrue(composite.consumeArrivalEvent());
            // The interrupt replays the SAME 320ms entrance (no fast clock):
            // still in flight 121ms in, settled only on the page clock.
            assertFalse(composite.overlaySettled(t0 + 51 + 121),
                    "a re-arm restarts the entrance, not a shortened one");
            frame(s, t0 + 51 + 320);
            assertTrue(composite.overlaySettled(t0 + 51 + 320),
                    "the re-armed entrance lands on the 320ms page clock");
        }
    }

    /** After the entrance settled, the next arrival plays the normal envelope. */
    @Test
    void settledArrivalPlaysTheNormalEnvelope() {
        long t0 = 4_000_000L;
        host.top = PROFILE_DETAIL;
        composite.beginFrame(t0);
        composite.consumeArrivalEvent();

        host.top = WORLD;
        composite.beginFrame(t0 + 300);         // long after the 200ms envelope
        assertTrue(composite.consumeArrivalEvent());
        assertFalse(composite.overlaySettled(t0 + 301),
                "the entrance is playing");
        assertTrue(composite.overlaySettled(t0 + 301 + 320),
                "and lands on the 320ms page clock");
    }

    // ---------------------------------------------------------- the no-exit pin

    /**
     * No exit phase: after a key change the ONLY draws are the arriving page's —
     * one body and one header, both for the new page. The old page has no layer,
     * no offset and no alpha anywhere in the frame.
     */
    @Test
    void onlyTheArrivingPageDraws() {
        try (Surface s = surface()) {
            long t0 = 5_000_000L;
            frame(s, t0);

            host.top = PROFILE_DETAIL;
            host.calls.clear();
            frame(s, t0 + 1);

            assertEquals(List.of("body@PROFILE_DETAIL", "header@PROFILE_DETAIL"), host.calls,
                    "one body and one header, both the arriving page's");
        }
    }

    // ------------------------------------------------------- canvas discipline

    /**
     * The envelope's save points balance on an entrance frame, and a settled
     * frame adds no canvas state at all (no layer, no clip, no transform — the
     * plain draws the settled path always had).
     */
    @Test
    void theEnvelopeIsCanvasNeutral() {
        try (Surface s = surface()) {
            long t0 = 6_000_000L;
            frame(s, t0);
            int baseline;
            try (Canvas canvas = s.getCanvas()) {
                baseline = canvas.getSaveCount();
            }

            host.top = PROFILE_DETAIL;
            frame(s, t0 + 1);                   // entrance frame: envelope open and closed
            try (Canvas canvas = s.getCanvas()) {
                assertEquals(baseline, canvas.getSaveCount(),
                        "the envelope's saves balance after the frame");
            }

            frame(s, t0 + 400);                 // settled frame
            try (Canvas canvas = s.getCanvas()) {
                assertEquals(baseline, canvas.getSaveCount(),
                        "a settled frame adds no canvas state");
            }
        }
    }

    /** The style is the one config read, with no per-pair exception. */
    @Test
    void theStyleFollowsTheConfig() {
        assertEquals(AtomChatConfig.PageNavStyle.SLIDE, composite.style());
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        assertEquals(AtomChatConfig.PageNavStyle.ZOOM, composite.style());
    }
}
