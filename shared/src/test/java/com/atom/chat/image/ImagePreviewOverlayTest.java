package com.atom.chat.image;

import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The picture preview modal: where the picture lands, where the close key sits
 * and what a click does.
 *
 * <p>Two things are covered here, and both are the parts a screenshot cannot
 * check. First the geometry: the picture keeps its own aspect ratio, is never
 * enlarged past its own pixels and never reaches under the close key — all of it
 * asserted from the published rects, so the drawn picture and the rect a click
 * is tested against are the same one. Second the click rule: the picture keeps
 * the preview open, everything else (the dim backdrop and the close key) closes
 * it — "click the background to dismiss, click the picture and it stays" is a
 * one-line rule, which is exactly why it needs a test.
 *
 * <p>The frames themselves are drawn on a CPU raster surface: nothing here needs
 * a window, a GPU or a client.
 */
class ImagePreviewOverlayTest {

    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;
    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;

    @TempDir
    static Path platformRoot;

    @BeforeAll
    static void installTemporaryPlatform() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return platformRoot.resolve("config");
            }

            @Override
            public Path gameDir() {
                return platformRoot.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    private static UiLayout.Rect panel() {
        return UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H).rect();
    }

    // ------------------------------------------------------------- geometry

    /** The picture is drawn at its own proportions, whatever they are. */
    @Test
    void thePictureKeepsItsOwnAspectRatio() {
        UiLayout.Rect panel = panel();
        for (int[] size : new int[][]{{4000, 2000}, {2000, 4000}, {768, 768}, {300, 200}}) {
            UiLayout.Rect rect = ImagePreviewOverlay.imageRect(panel, size[0], size[1]);
            float want = size[0] / (float) size[1];
            assertEquals(want, rect.w() / rect.h(), 1e-3F,
                    "the drawn rect must keep the bitmap's aspect for " + size[0] + "x" + size[1]);
        }
    }

    /** Whole picture, inside the panel — no crop, no overflow. */
    @Test
    void thePictureStaysInsideThePanelAndIsCentred() {
        UiLayout.Rect panel = panel();
        for (int[] size : new int[][]{{4000, 2000}, {2000, 4000}, {100, 100}, {768, 384}}) {
            UiLayout.Rect rect = ImagePreviewOverlay.imageRect(panel, size[0], size[1]);
            assertTrue(rect.x() > panel.x() && rect.right() < panel.right(),
                    "the picture must sit inside the panel's own edges: " + rect);
            assertTrue(rect.y() > panel.y() && rect.bottom() < panel.bottom(),
                    "the picture must sit inside the panel's own edges: " + rect);
            assertEquals(panel.x() + panel.w() / 2.0F, rect.x() + rect.w() / 2.0F, 0.01F,
                    "the picture is centred horizontally in the panel");
        }
    }

    /**
     * A small picture is shown at its own size — blowing a 4x4 bitmap up to the
     * panel would only show mush. Same rule the message bubble follows.
     */
    @Test
    void aSmallPictureIsNotEnlarged() {
        UiLayout.Rect panel = panel();
        UiLayout.Rect small = ImagePreviewOverlay.imageRect(panel, 4, 3);
        assertEquals(4.0F, small.w(), 0.001F, "a 4px-wide bitmap is displayed 4px wide");
        assertEquals(3.0F, small.h(), 0.001F, "a 3px-tall bitmap is displayed 3px tall");
    }

    /** The visible size grows with the source, and with the panel. */
    @Test
    void aBiggerSourceAndABiggerPanelBothGiveMorePicture() {
        UiLayout.Rect panel = panel();
        UiLayout.Rect smaller = ImagePreviewOverlay.imageRect(panel, 800, 400);
        UiLayout.Rect bigger = ImagePreviewOverlay.imageRect(panel, 3200, 1600);
        assertTrue(bigger.w() >= smaller.w() && bigger.h() >= smaller.h(),
                "more source pixels may never give a smaller picture");
        UiLayout.Rect tallPanel = new UiLayout.Rect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H * 2.0F);
        UiLayout.Rect tallRect = ImagePreviewOverlay.imageRect(tallPanel, 800, 400);
        assertTrue(tallRect.w() >= smaller.w(),
                "a wider/taller panel must be able to give the picture more room");
    }

    /**
     * The close key sits centred under the content, and under it for every
     * aspect ratio — including the tallest one the fit ever produces, which is
     * the case a key placed relative to the picture's own bottom edge would put
     * outside the panel.
     */
    @Test
    void theCloseKeySitsCentredUnderThePicture() {
        UiLayout.Rect panel = panel();
        UiLayout.Rect key = ImagePreviewOverlay.closeRect(panel);
        assertEquals(panel.x() + panel.w() / 2.0F, key.x() + key.w() / 2.0F, 0.01F,
                "the close key is centred horizontally");
        assertTrue(key.y() > panel.y() && key.bottom() < panel.bottom(),
                "the close key is inside the panel: " + key);
        for (int[] size : new int[][]{{4000, 2000}, {2000, 4000}, {768, 768}, {1, 8000}}) {
            UiLayout.Rect picture = ImagePreviewOverlay.imageRect(panel, size[0], size[1]);
            assertTrue(key.y() >= picture.bottom(),
                    "the close key must not overlap the picture " + size[0] + "x" + size[1]);
        }
    }

    // ---------------------------------------------------------------- clicks

    /**
     * The rule in one test: a click on the picture leaves the preview standing,
     * a click anywhere else — the dim backdrop, and the close key itself —
     * dismisses it.
     */
    @Test
    void thePictureHoldsThePreviewOpenAndEverythingElseClosesIt() {
        try (Surface bitmapSurface = Surface.makeRasterN32Premul(40, 30)) {
            Image bitmap = bitmapSurface.makeImageSnapshot();
            UiLayout.Rect panel = panel();
            ImagePreviewOverlay overlay = new ImagePreviewOverlay();
            overlay.open(bitmap);
            assertTrue(overlay.isActive(), "an opened preview is active");
            assertFalse(overlay.isClosing(), "an opened preview starts visible");

            UiLayout.Rect picture = ImagePreviewOverlay.imageRect(panel, 40, 30);
            overlay.onClick(picture.x() + picture.w() / 2.0F, picture.y() + picture.h() / 2.0F, panel);
            assertFalse(overlay.isClosing(), "clicking the picture must not dismiss the preview");
            assertTrue(overlay.isActive(), "and it stays open");

            UiLayout.Rect key = ImagePreviewOverlay.closeRect(panel);
            overlay.onClick(key.x() + key.w() / 2.0F, key.y() + key.h() / 2.0F, panel);
            assertTrue(overlay.isClosing(), "the close key dismisses the preview");
        }
    }

    /** The dim backdrop — any point off the picture — dismisses the preview. */
    @Test
    void theDimBackdropClosesThePreview() {
        try (Surface bitmapSurface = Surface.makeRasterN32Premul(40, 30)) {
            Image bitmap = bitmapSurface.makeImageSnapshot();
            UiLayout.Rect panel = panel();
            ImagePreviewOverlay overlay = new ImagePreviewOverlay();
            overlay.open(bitmap);
            overlay.onClick(panel.x() + 1.0F, panel.y() + 1.0F, panel);
            assertTrue(overlay.isClosing(), "a click on the backdrop dismisses the preview");
        }
    }

    /**
     * Opening with nothing to show is not a state the screen can reach (the
     * click only opens a picture the frame actually drew), and it must not
     * become one: an empty preview would be a dim plate with a close key and
     * nothing behind it.
     */
    @Test
    void openingWithoutABitmapShowsNothing() {
        ImagePreviewOverlay overlay = new ImagePreviewOverlay();
        overlay.open(null);
        assertFalse(overlay.isActive(), "there is no preview without a picture");
    }

    // ------------------------------------------------------------- lifecycle

    /**
     * The fade-out is symmetric to the fade-in and the overlay keeps swallowing
     * input until it has played — the same lifecycle the cropper and the colour
     * picker use. Every frame of it must leave the canvas save stack balanced,
     * which is where a leaked save would surface.
     */
    @Test
    void theFadeOutPlaysToTheEndAndBalancesEveryFrame() {
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try (Surface bitmapSurface = Surface.makeRasterN32Premul(4000, 2000)) {
            Image bitmap = bitmapSurface.makeImageSnapshot();
            Canvas canvas = surface.getCanvas();
            UiLayout.Rect panel = panel();
            ImagePreviewOverlay overlay = new ImagePreviewOverlay();
            overlay.open(bitmap);
            int baseline = canvas.getSaveCount();

            // The entrance starts at zero opacity (the same curve the cropper and
            // the picker open on), so the earliest frames are deliberately blank;
            // once it has landed the preview is on screen.
            settleEntrance(overlay, canvas, panel);
            assertEquals(baseline, canvas.getSaveCount(), "the entrance left the stack unbalanced");
            canvas.clear(0);
            overlay.render(canvas, panel, OFF_PANEL_X, OFF_PANEL_Y);
            assertEquals(baseline, canvas.getSaveCount(), "the settled frame left the stack unbalanced");
            assertTrue(drawnBytes(surface) > 0, "the settled preview painted nothing at all");

            overlay.close();
            assertTrue(overlay.isActive(),
                    "the overlay stays active through its fade so it keeps owning input");
            for (int frame = 0; frame < 120 && overlay.isActive(); frame++) {
                canvas.clear(0);
                overlay.render(canvas, panel, OFF_PANEL_X, OFF_PANEL_Y);
                assertEquals(baseline, canvas.getSaveCount(), "fade frame " + frame + " unbalanced the stack");
                sleepFrame();
            }
            assertFalse(overlay.isActive(), "the fade must end, or the modal never releases input");
            // And a dismissed preview stops painting: nothing is drawn from the
            // picture that was handed to it.
            canvas.clear(0);
            overlay.render(canvas, panel, OFF_PANEL_X, OFF_PANEL_Y);
            assertEquals(0, drawnBytes(surface), "a closed preview must paint nothing");
        } finally {
            surface.close();
        }
    }

    /** Off-panel hover coordinates, so no control reads as hovered. */
    private static final float OFF_PANEL_X = -10_000.0F;
    private static final float OFF_PANEL_Y = -10_000.0F;

    /**
     * Pumps frames until the entrance has landed. A single call cannot get
     * there: {@code UiMotion.approach} clamps its step to 50ms, so one frame
     * covers at most part of the fade whatever the wall clock says.
     */
    private static void settleEntrance(ImagePreviewOverlay overlay, Canvas canvas, UiLayout.Rect panel) {
        for (int frame = 0; frame < 60; frame++) {
            canvas.clear(0);
            overlay.render(canvas, panel, OFF_PANEL_X, OFF_PANEL_Y);
            sleepFrame();
        }
    }

    private static void sleepFrame() {
        try {
            Thread.sleep(16L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting out the fade", e);
        }
    }

    /** Non-zero bytes in the surface's pixels, cleared before the frame under test. */
    private static int drawnBytes(Surface surface) {
        Bitmap bitmap = new Bitmap();
        try {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            byte[] pixels = bitmap.readPixels();
            int nonZero = 0;
            for (byte b : pixels) {
                if (b != 0) {
                    nonZero++;
                }
            }
            return nonZero;
        } finally {
            bitmap.close();
        }
    }
}
