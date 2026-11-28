package com.atom.chat.render;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollEdgeFadeTest {
    private static final Rect BOUNDS = Rect.makeWH(64, 80);

    @Test
    void scrollableContentFadesAtBothEdgesWithoutTouchingTheBackgroundOrCenter() {
        try (var surface = Surface.makeRasterN32Premul(64, 80);
             var paint = new Paint().setColor(0xFFFFFFFF)) {
            Canvas canvas = surface.getCanvas();
            int saves = canvas.getSaveCount();
            canvas.clear(0xFF000000);
            try (var scrollClip = ScrollEdgeFade.clip(canvas, 8, 10, 48, 60, 30, 60)) {
                canvas.translate(0, -2);
                canvas.drawRect(BOUNDS, paint);
            }
            assertEquals(saves, canvas.getSaveCount());
            assertEquals(0, channel(surface, 9));
            assertEquals(0, channel(surface, 70));
            assertTrue(channel(surface, 10) < channel(surface, 13));
            assertTrue(channel(surface, 69) < channel(surface, 66));
            assertEquals(255, channel(surface, 40));
        }
    }

    @Test
    void scrollEndpointsKeepTheFirstAndLastContentEdgesOpaque() {
        try (var surface = Surface.makeRasterN32Premul(64, 80);
             var paint = new Paint().setColor(0xFFFFFFFF)) {
            Canvas canvas = surface.getCanvas();
            try (var scrollClip = ScrollEdgeFade.clip(canvas, 8, 10, 48, 60, 0, 60)) {
                canvas.drawRect(BOUNDS, paint);
            }
            assertEquals(255, alpha(surface, 10));
            assertTrue(alpha(surface, 69) < 128);
            canvas.clear(0);
            try (var scrollClip = ScrollEdgeFade.clip(canvas, 8, 10, 48, 60, 60, 60)) {
                canvas.drawRect(BOUNDS, paint);
            }
            assertTrue(alpha(surface, 10) < 128);
            assertEquals(255, alpha(surface, 69));
        }
    }

    @Test
    void recordedGlassMaskUsesTheSameFadeWithoutErasingSiblingMasks() {
        try (var surface = Surface.makeRasterN32Premul(64, 80);
             var recordingSurface = Surface.makeRasterN32Premul(64, 80)) {
            Canvas canvas = recordingSurface.getCanvas();
            try (var mask = CardBackdrop.recordMasks(BOUNDS, true, () -> {
                CardBackdrop.draw(canvas, 8, 3, 48, 7, 0);
                try (var scrollClip = ScrollEdgeFade.clip(canvas, 8, 10, 48, 60, 30, 60)) {
                    CardBackdrop.draw(canvas, 8, 10, 48, 60, 0);
                }
            })) {
                assertNotNull(mask);
                mask.playback(surface.getCanvas());
                assertEquals(255, alpha(surface, 8));
                assertTrue(alpha(surface, 10) < alpha(surface, 13));
                assertTrue(alpha(surface, 69) < alpha(surface, 66));
                assertEquals(255, alpha(surface, 40));
            }
        }
    }

    @Test
    void lateFeedbackIsClippedAndFadesInTheCardsViewport() {
        try (var surface = Surface.makeRasterN32Premul(64, 80);
             var paint = new Paint().setColor(0xFFFFFFFF)) {
            Canvas canvas = surface.getCanvas();
            ScrollEdgeFade.Window window;
            try (var scrollClip = ScrollEdgeFade.clip(canvas, 8, 10, 48, 60, 25, 50)) {
                window = ScrollEdgeFade.currentWindow();
            }
            assertNull(ScrollEdgeFade.currentWindow());
            var control = new com.atom.chat.ui.UiLayout.Rect(8, 6, 48, 30);
            window.drawFeedback(canvas, control, () -> canvas.drawRect(Rect.makeXYWH(8, 6, 48, 30), paint));
            assertEquals(0, alpha(surface, 9));
            assertTrue(alpha(surface, 10) < alpha(surface, 13));
            assertEquals(255, alpha(surface, 33));
        }
    }

    private static int channel(Surface surface, int y) {
        return pixel(surface, y) & 255;
    }

    private static int alpha(Surface surface, int y) {
        return pixel(surface, y) >>> 24;
    }

    private static int pixel(Surface surface, int y) {
        try (var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(64, 80);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.getColor(32, y);
        }
    }
}
