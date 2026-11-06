package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * II-1: bright/dark tint A/B. GoldenFrameTest pins a single ACCENT tint, so it
 * cannot cover the theme axis. This test renders the dot field with several
 * bright, dark, translucent and accent tints and requires the cached path to
 * be pixel-identical to the legacy path for every one of them. Runs on all
 * three platform test suites; the system-out TINT_GOLDEN lines are the evidence.
 */
class DotTextureTintContrastTest {
    private static final int W = 240, H = 180;
    private static final UiLayout.Rect BAR = new UiLayout.Rect(20, 90, 200, 65);
    private static final UiLayout.Rect BOX = new UiLayout.Rect(20, 20, 200, 60);
    private static final int[] TINTS = {
            0xFFFFFFFF, // bright (white)
            0xFF101820, // dark (near-black)
            0xFF3B82F6, // accent blue (GoldenFrameTest ACCENT)
            0x80FFFFFF, // translucent bright
            0xFFFF0000  // saturated red
    };

    @AfterEach
    void restore() {
        DotTexture.setDotsCached(!"false".equalsIgnoreCase(
                System.getProperty("atomchat.dots.cached", "true")));
        DotTexture.clearCache();
    }

    @Test
    void brightAndDarkTintsStayWithinOneLsbBetweenOffAndOn() {
        for (int tint : TINTS) {
            byte[] off = frame(tint, false);
            byte[] on = frame(tint, true);
            boolean identical = Arrays.equals(off, on);
            System.out.println("TINT_GOLDEN tint=0x" + Integer.toHexString(tint)
                    + " bytes=" + off.length + " identical=" + identical);
            assertArrayEquals(off, on,
                    "tint 0x" + Integer.toHexString(tint)
                            + ": small cached halo must stay byte-identical");
        }
    }


    private static byte[] frame(int tint, boolean flag) {
        DotTexture.setDotsCached(flag);
        DotTexture.clearCache();
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFF1F2937);
            DotTexture.drawEdge(canvas, BAR, 25, true, tint);
            DotTexture.drawEdge(canvas, BAR, 25, false, tint);
            DotTexture.drawTrail(canvas, BOX, tint);
            DotTexture.drawHalo(canvas, BOX, tint);
            try (Bitmap bitmap = new Bitmap()) {
                bitmap.allocN32Pixels(W, H);
                assertTrue(surface.readPixels(bitmap, 0, 0), "cannot read back raster pixels");
                return bitmap.readPixels();
            }
        }
    }
}
