package com.atom.chat.render;

import com.atom.chat.font.FontManager;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkiaFontRendererShapeCacheTest {
    @BeforeEach
    void clearCaches() {
        SkiaFontRenderer.clearTextShapeCache();
    }

    @Test
    void repeatedDrawReusesShapeAndPixels() {
        Font font = FontManager.font(18.0F);
        try (Surface surface = Surface.makeRasterN32Premul(360, 64)) {
            Canvas canvas = surface.getCanvas();
            int saves = canvas.getSaveCount();
            SkiaFontRenderer.drawText(canvas, font, "Repeated settings label", 12.0F, 40.0F,
                    0xFFE8EDF5);
            long builds = SkiaFontRenderer.shapedPlanBuilds();
            byte[] first = pixels(surface);

            canvas.clear(0);
            SkiaFontRenderer.drawText(canvas, font, "Repeated settings label", 12.0F, 40.0F,
                    0xFFE8EDF5);

            assertEquals(builds, SkiaFontRenderer.shapedPlanBuilds(),
                    "a repeated frame must reuse the shaped plan");
            assertEquals(saves, canvas.getSaveCount());
            assertArrayEquals(first, pixels(surface), "the cached blob must preserve pixels");
        }
    }

    @Test
    void shapedBlobMatchesCanvasDrawString() {
        Font font = FontManager.font(20.0F);
        try (Surface cached = Surface.makeRasterN32Premul(320, 64);
             Surface direct = Surface.makeRasterN32Premul(320, 64)) {
            SkiaFontRenderer.drawText(cached.getCanvas(), font, "AtomChat 123", 16.0F, 42.0F,
                    0xFFF3F4F6);
            try (Paint paint = new Paint().setColor(0xFFF3F4F6)) {
                direct.getCanvas().drawString("AtomChat 123", 16.0F, 42.0F, font, paint);
            }
            assertArrayEquals(pixels(direct), pixels(cached),
                    "cached TextBlob output must match the previous Canvas.drawString path");

            cached.getCanvas().clear(0);
            direct.getCanvas().clear(0);
            SkiaFontRenderer.drawText(cached.getCanvas(), font, "设置页面性能", 16.0F, 42.0F,
                    0xFFF3F4F6);
            try (Paint paint = new Paint().setColor(0xFFF3F4F6)) {
                direct.getCanvas().drawString("设置页面性能", 16.0F, 42.0F, font, paint);
            }
            assertArrayEquals(pixels(direct), pixels(cached),
                    "bundled CJK glyphs must keep the previous Canvas.drawString output");
        }
    }

    @Test
    void cacheInvalidationClosesAndRebuildsThePlan() {
        Font font = FontManager.font(16.0F);
        try (Surface surface = Surface.makeRasterN32Premul(220, 48)) {
            SkiaFontRenderer.drawText(surface.getCanvas(), font, "invalidate", 8.0F, 30.0F,
                    0xFFFFFFFF);
            long before = SkiaFontRenderer.shapedPlanBuilds();
            SkiaFontRenderer.clearTextShapeCache();
            SkiaFontRenderer.drawText(surface.getCanvas(), font, "invalidate", 8.0F, 30.0F,
                    0xFFFFFFFF);
            assertEquals(before + 1, SkiaFontRenderer.shapedPlanBuilds());
        }
    }

    @Test
    void repeatedWrapReusesTheMeasuredLines() {
        Font font = FontManager.font(16.0F);
        String text = "settings description that wraps over several lines";
        List<String> first = SkiaFontRenderer.wrap(font, text, 120.0F);
        long builds = SkiaFontRenderer.wrapCacheBuilds();

        List<String> second = SkiaFontRenderer.wrap(font, text, 120.0F);

        assertSame(first, second, "an unchanged row must reuse its wrapped lines");
        assertEquals(builds, SkiaFontRenderer.wrapCacheBuilds());
    }

    private static byte[] pixels(Surface surface) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(surface.getWidth(), surface.getHeight());
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }
}
