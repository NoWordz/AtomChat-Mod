package com.atom.chat.render;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalEdgeCoverageTest {
    @Test void localCoverageFollowsTheRounderContinuousContour() {
        int scale = 20;
        var shape = RRect.makeXYWH(0F, 0F, 70F, 42F, 20F);
        try (var shader = Surface.makeRasterN32Premul(180, 180);
             var vector = Surface.makeRasterN32Premul(180, 180);
             var pixels = new Bitmap()) {
            shader.getCanvas().scale(scale, scale);
            vector.getCanvas().scale(scale, scale);
            assertTrue(RoundedCoverage.fill(shader.getCanvas(), shape, 0xFFFFFFFF));
            CornerGeometry.fillNative(vector.getCanvas(), shape, 0xFFFFFFFF);
            pixels.allocN32Pixels(180, 180);
            for (var surface : new Surface[]{shader, vector}) {
                assertTrue(surface.readPixels(pixels, 0, 0));
                // At reach 21 the chosen curve cuts about 4.70 units on the diagonal.
                assertEquals(0, pixels.getColor(90, 90) >>> 24);
                assertEquals(255, pixels.getColor(104, 104) >>> 24);
            }
        }
    }

    @Test void groupClipAppliesOneCoverageMaskAfterTheContentsAreComposited() {
        var clip = RRect.makeXYWH(6.25F, 7.35F, 66F, 34F, 12F);
        try (var expected = Surface.makeRasterN32Premul(100, 60);
             var actual = Surface.makeRasterN32Premul(100, 60);
             var a = new Bitmap(); var b = new Bitmap();
             var content = new Paint().setColor(0xFF2773D0)) {
            expected.getCanvas().scale(1.25F, 1.25F);
            actual.getCanvas().scale(1.25F, 1.25F);
            int background = 0x60323747;
            expected.getCanvas().clear(background);
            actual.getCanvas().clear(background);
            CornerGeometry.fill(expected.getCanvas(), clip, 0xFF2773D0);
            var canvas = actual.getCanvas();
            int saveCount = canvas.getSaveCount();
            CornerGeometry.drawClipped(canvas, clip, () -> {
                // A transformed body can still be masked in the original scope.
                canvas.translate(1F, 2F);
                canvas.drawRect(Rect.makeLTRB(0F, 0F, 90F, 50F), content);
                canvas.drawRect(Rect.makeLTRB(0F, 0F, 90F, 50F), content);
            });
            assertEquals(saveCount, canvas.getSaveCount());
            assertEquals(1.25F, canvas.getLocalToDeviceAsMatrix33()._mat[0]);
            assertEquals(0F, canvas.getLocalToDeviceAsMatrix33()._mat[2]);
            read(expected, a);
            read(actual, b);
            int edges = 0;
            for (int y = 0; y < 60; y++) {
                for (int x = 0; x < 100; x++) {
                    int first = a.getColor(x, y), second = b.getColor(x, y);
                    if ((first >>> 24) > 96 && (first >>> 24) < 255) edges++;
                    assertEquals(first >>> 24, second >>> 24, 2);
                    for (int shift = 0; shift < 24; shift += 8) {
                        assertEquals(premultiplied(first, shift), premultiplied(second, shift), 2);
                    }
                }
            }
            assertTrue(edges > 90);
        }
    }

    @Test void cachedSendIconRetainsPremultipliedTintAndPhase() {
        try (var first = Surface.makeRasterN32Premul(100, 60);
             var second = Surface.makeRasterN32Premul(100, 60);
             var a = new Bitmap(); var b = new Bitmap()) {
            drawIcon(first.getCanvas(), 15.4F, 0xFF4987D1);
            drawIcon(second.getCanvas(), 16.2F, 0x804987D1);
            read(first, a);
            read(second, b);
            int total = 0;
            for (int y = 4; y < 45; y++) {
                for (int x = 2; x < 40; x++) {
                    int alpha = a.getColor(x, y) >>> 24;
                    int color = b.getColor(x + 1, y);
                    assertEquals(Math.round(alpha * 128F / 255F), color >>> 24, 2);
                    if ((color >>> 24) > 64) {
                        assertEquals(0x49, (color >>> 16) & 255, 2);
                        assertEquals(0x87, (color >>> 8) & 255, 2);
                        assertEquals(0xD1, color & 255, 2);
                    }
                    total += alpha;
                }
            }
            assertTrue(total > 6000);
        }
    }

    private static int premultiplied(int pixel, int shift) {
        return Math.round(((pixel >>> shift) & 255) * (pixel >>> 24) / 255F);
    }

    private static void drawIcon(Canvas canvas, float center, int color) {
        canvas.scale(1.25F, 1.25F);
        OutlineIcons.draw(canvas, DrawPrimitives.ICON_SEND_PATH, center, 16.1F, 18F, color);
    }

    private static void read(Surface surface, Bitmap bitmap) {
        bitmap.allocN32Pixels(100, 60);
        assertTrue(surface.readPixels(bitmap, 0, 0));
    }
}
