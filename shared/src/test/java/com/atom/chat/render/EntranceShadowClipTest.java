package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsHomePage;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Raster guard for the entrance layer's extent: a card's soft shadow has to
 * travel with the card, not be sawn off by the layer that carries the fade.
 *
 * <p><b>What this measures.</b> Not the shadow's colour or weight — those are
 * the card language's business. It measures the <em>distance from the card's
 * top edge up to the shadow's leading edge</em> on every frame of an entrance.
 * That distance is a property of the blur alone, so it must be the same on
 * every frame as it is at rest. The reported defect was exactly the opposite:
 * the layer's top edge sat at the item's slot, so from the second frame on the
 * shadow was cut into a dead-straight horizontal line and the measured lead
 * collapsed; the frame the layer was skipped the shadow sprang back to its
 * full reach ("the shadow does not move with the card").</p>
 *
 * <p><b>Why the threshold is relative.</b> The layer fades the whole card in,
 * so an absolute "differs from the panel by N" test would measure a shorter
 * lead on the faint early frames for reasons that have nothing to do with
 * clipping. The lead is therefore taken at a fixed fraction of that frame's own
 * darkest shadow pixel: a fraction of peak is a geometric contour, so it is
 * invariant to the layer alpha and comparable across frames.</p>
 */
class EntranceShadowClipTest {

    private static final int W = 480;
    private static final int H = 900;
    /** The frames the defect was reported on, plus the settled one as reference. */
    private static final long[] FRAMES = {40, 120, 200, 280, 400};

    @TempDir
    static Path tmp;

    private boolean oldMotion;
    private AtomChatConfig.PageNavStyle oldStyle;
    private int oldPanelBg;
    private int oldCardColor;
    private float oldCardTint;

    @BeforeAll
    static void installPlatform() {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp.resolve("config"); }
            public Path gameDir() { return tmp.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @BeforeEach
    void setUp() {
        AtomChatConfig config = AtomChatConfig.get();
        oldMotion = config.animationEnabled;
        oldStyle = config.pageNavStyle;
        oldPanelBg = config.panelBgColor;
        oldCardColor = config.cardColor;
        oldCardTint = config.cardTint;
        config.animationEnabled = true;
        // ZOOM is the axis whose cards actually rise; under SLIDE there is no
        // vertical travel, so a vertical shadow lead could not regress there.
        config.pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        // An opaque dark card on a white panel: the geometry measured here is
        // theme-independent, and the default frosted-white-on-dark pair leaves
        // the shadow at one or two 8-bit levels, which is below what a contour
        // threshold can resolve frame to frame.
        config.panelBgColor = 0xFFFFFFFF;
        config.cardColor = 0xFF101010;
        config.cardTint = 1.0F;
    }

    @AfterEach
    void tearDown() {
        AtomChatConfig config = AtomChatConfig.get();
        config.animationEnabled = oldMotion;
        config.pageNavStyle = oldStyle;
        config.panelBgColor = oldPanelBg;
        config.cardColor = oldCardColor;
        config.cardTint = oldCardTint;
    }

    @Test
    void theShadowLeadStaysPutWhileTheTileEnters() throws Exception {
        UiLayout layout = UiLayout.ofRoot(0, 0, W, H);
        UiLayout.Rect tile = SettingsHomePage.tileRect(layout, 0, 0.0F);
        int cx = Math.round(tile.x() + tile.w() / 2.0F);
        int bg = AtomChatConfig.get().panelBgColor;

        int[] leads = new int[FRAMES.length];
        for (int f = 0; f < FRAMES.length; f++) {
            long t = FRAMES[f];
            Surface surface = Surface.makeRasterN32Premul(W, H);
            try {
                SettingsHomePage page = new SettingsHomePage();
                Field clock = SettingsHomePage.class.getDeclaredField("lastFrameMs");
                clock.setAccessible(true);
                clock.setLong(page, System.currentTimeMillis() - 16);
                Field entranceField = SettingsHomePage.class.getDeclaredField("entrance");
                entranceField.setAccessible(true);
                UiStagger.Entrance entrance = (UiStagger.Entrance) entranceField.get(page);
                entrance.markEntered(System.currentTimeMillis() - t);

                Canvas canvas = surface.getCanvas();
                canvas.clear(bg);
                page.render(canvas, layout, -1.0F, -1.0F, 0.0F);
                // The stamp the page actually drew with: the entrance alpha has
                // to be evaluated on it, not on the one the test armed.
                float alpha = UiStagger.itemAlpha(entrance, 0, clock.getLong(page));

                try (Bitmap bmp = new Bitmap()) {
                    bmp.allocN32Pixels(W, H);
                    assertTrue(surface.readPixels(bmp, 0, 0));
                    // A tile-interior point off the centre line: the glyph and
                    // the label both sit on the centre, so this reads plain
                    // card fill at every frame of the entrance.
                    int sampleX = Math.round(tile.x() + tile.w() * 0.2F);
                    int sampleY = Math.round(tile.y() + tile.h() * 0.5F);
                    int layerFill = unblend(bmp.getColor(sampleX, sampleY), bg, alpha);
                    int cardTop = firstRowAt(cx, bmp, bg, layerFill, alpha, tile.y(), layout.list.y());
                    int shadowTop = firstShadowRow(cx, bmp, bg, cardTop);
                    leads[f] = cardTop - shadowTop;
                    System.out.printf(
                            "[shadow-clip] t=%3dms alpha=%.3f slotTop=%.1f cardTop=%d shadowTop=%d lead=%d%n",
                            t, alpha, tile.y(), cardTop, shadowTop, leads[f]);
                }
            } finally {
                surface.close();
            }
        }

        int settled = leads[leads.length - 1];
        assertTrue(settled >= 5, "the settled frame must show a real shadow tail, got " + settled);
        for (int f = 0; f < leads.length; f++) {
            assertEquals(settled, leads[f], 2,
                    "t=" + FRAMES[f] + "ms: the shadow lead changed from " + settled
                            + "px to " + leads[f] + "px — the entrance layer clipped the tail");
        }
    }

    /**
     * First row from above at which the card fill starts and stays: four
     * consecutive rows must match, so the card's AA edge and its hairline
     * highlight are both skipped and the result is a point safely inside the
     * card. The rows above it are then pure shadow.
     */
    private static int firstRowAt(int cx, Bitmap bmp, int bg, int layerFill, float alpha,
                                  float slotTop, float listTop) {
        // 40px of head room above the slot (the lifted card plus its shadow), but
        // never above the list itself: the header chrome lives up there and its
        // own fill would be mistaken for the card.
        int from = Math.max(Math.round(listTop) + 1, Math.round(slotTop) - 40);
        int expected = blend(layerFill, bg, alpha);
        for (int y = from; y + 3 < bmp.getHeight(); y++) {
            if (near(bmp.getColor(cx, y), expected, 4)
                    && near(bmp.getColor(cx, y + 1), expected, 4)
                    && near(bmp.getColor(cx, y + 2), expected, 4)
                    && near(bmp.getColor(cx, y + 3), expected, 4)) {
                return y;
            }
        }
        throw new AssertionError("the card fill was never found on the centre column");
    }

    /**
     * First row from above that is noticeably darker than the panel — the
     * contour at 30% of the strongest darkening above the card. A blur is linear,
     * so scaling the whole shadow by the layer alpha scales every level equally
     * and the half-maximum contour keeps its geometric position; that is what
     * makes the lead comparable across frames of different alpha.
     */
    private static int firstShadowRow(int cx, Bitmap bmp, int bg, int cardTop) {
        // The peak is taken a few rows clear of the card so a card pixel can
        // never masquerade as the shadow's darkest point. Every level is a
        // horizontal average across the tile's flat top edge: the shadow is
        // uniform there, so averaging cancels the 8-bit quantisation that would
        // otherwise move the contour by a pixel from frame to frame.
        float peak = 0.0F;
        for (int y = Math.max(0, cardTop - 16); y <= cardTop - 4; y++) {
            peak = Math.max(peak, rowDarkening(bmp, cx, y, bg));
        }
        if (peak <= 1.0F) {
            // Not a probe problem: a frame whose whole shadow band above the
            // card reads as panel is the clipped-layer signature itself.
            throw new AssertionError("cardTop=" + cardTop + " has no shadow tail above it "
                    + "(peak darkening over the " + (cardTop - Math.max(0, cardTop - 16))
                    + "px band: " + peak + ") — the entrance layer cut the shadow off");
        }
        // 30% of peak: high enough that 8-bit quantisation of a faint early
        // frame cannot move the contour, low enough that the contour sits where
        // the tail is still visibly travelling with the card.
        float threshold = Math.max(0.5F, peak * 0.3F);
        for (int y = Math.max(0, cardTop - 40); y <= cardTop - 4; y++) {
            if (rowDarkening(bmp, cx, y, bg) >= threshold) {
                return y;
            }
        }
        throw new AssertionError("the shadow's leading edge was never found");
    }

    /** Mean darkening across a 41px band centred on {@code cx}, in one row. */
    private static float rowDarkening(Bitmap bmp, int cx, int y, int bg) {
        int x0 = Math.max(0, cx - 20);
        int x1 = Math.min(bmp.getWidth() - 1, cx + 20);
        int sum = 0;
        for (int x = x0; x <= x1; x++) {
            sum += darkening(bmp.getColor(x, y), bg);
        }
        return sum / (float) (x1 - x0 + 1);
    }

    private static int darkening(int pixel, int bg) {
        return (Math.abs(((pixel >> 16) & 0xFF) - ((bg >> 16) & 0xFF))
                + Math.abs(((pixel >> 8) & 0xFF) - ((bg >> 8) & 0xFF))
                + Math.abs((pixel & 0xFF) - (bg & 0xFF))) / 3;
    }

    private static int blend(int over, int bg, float alpha) {
        return channel(over, bg, alpha, 16) | channel(over, bg, alpha, 8) | channel(over, bg, alpha, 0);
    }

    private static int channel(int over, int bg, float alpha, int shift) {
        float o = (over >> shift) & 0xFF;
        float b = (bg >> shift) & 0xFF;
        return Math.round(b + (o - b) * alpha) << shift;
    }

    /** Divides this frame's layer alpha back out of a composited pixel. */
    private static int unblend(int shown, int bg, float alpha) {
        if (alpha <= 0.01F) {
            return shown;
        }
        int r = clamp(Math.round((((shown >> 16) & 0xFF) - ((bg >> 16) & 0xFF)) / alpha + ((bg >> 16) & 0xFF)));
        int g = clamp(Math.round((((shown >> 8) & 0xFF) - ((bg >> 8) & 0xFF)) / alpha + ((bg >> 8) & 0xFF)));
        int b = clamp(Math.round(((shown & 0xFF) - (bg & 0xFF)) / alpha + (bg & 0xFF)));
        return r << 16 | g << 8 | b;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static boolean near(int a, int b, int tol) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) <= tol
                && Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) <= tol
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= tol;
    }
}
