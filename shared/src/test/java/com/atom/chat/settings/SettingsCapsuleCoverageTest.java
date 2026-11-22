package com.atom.chat.settings;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsCapsuleCoverageTest {
    private static final int WIDTH = 96;
    private static final int HEIGHT = 44;
    private static final int SUPERSAMPLE = 16;
    private static final float X = 7.25F;
    private static final float Y = 5.45F;
    private static final float W = 78F;
    private static final float H = 32.5F;
    private static final float RADIUS = 12.25F;

    @Test
    void fractionalEdgesMatchTheSupersampledContour() {
        for (float scale : new float[]{1F, 1.25F, 2.5F}) {
            checkCoverage(scale, 0xFF478BD5);
            checkCoverage(scale, 0x37478BD5);
        }
    }

    @Test
    void squareAndRotatedContoursUseNativeCoverage() {
        for (boolean rotate : new boolean[]{false, true}) {
            float radius = rotate ? RADIUS : 0F;
            try (var actual = Surface.makeRasterN32Premul(WIDTH, HEIGHT);
                 var reference = Surface.makeRasterN32Premul(WIDTH, HEIGHT);
                 var actualPixels = new Bitmap(); var referencePixels = new Bitmap()) {
                if (rotate) {
                    actual.getCanvas().rotate(8F);
                    reference.getCanvas().rotate(8F);
                }
                int color = 0x37478BD5;
                SettingsCapsuleCoverage.draw(actual.getCanvas(), X, Y, W, H, radius, color);
                drawCircularReference(reference, radius, color);
                actualPixels.allocN32Pixels(WIDTH, HEIGHT);
                referencePixels.allocN32Pixels(WIDTH, HEIGHT);
                assertTrue(actual.readPixels(actualPixels, 0, 0));
                assertTrue(reference.readPixels(referencePixels, 0, 0));
                for (int y = 0; y < HEIGHT; y++) {
                    for (int x = 0; x < WIDTH; x++) {
                        assertEquals(referencePixels.getColor(x, y), actualPixels.getColor(x, y));
                    }
                }
            }
        }
    }

    @Test
    void translucentHighlightBlendsSourceColorAtTheEdge() {
        int tint = 0x37478BD5;
        int background = 0xFF362A1E;
        try (var mask = Surface.makeRasterN32Premul(WIDTH, HEIGHT);
             var composite = Surface.makeRasterN32Premul(WIDTH, HEIGHT);
             var maskPixels = new Bitmap(); var compositePixels = new Bitmap()) {
            composite.getCanvas().clear(background);
            SettingsCapsuleCoverage.draw(mask.getCanvas(), X, Y, W, H, RADIUS, tint);
            SettingsCapsuleCoverage.draw(composite.getCanvas(), X, Y, W, H, RADIUS, tint);
            maskPixels.allocN32Pixels(WIDTH, HEIGHT);
            compositePixels.allocN32Pixels(WIDTH, HEIGHT);
            assertTrue(mask.readPixels(maskPixels, 0, 0));
            assertTrue(composite.readPixels(compositePixels, 0, 0));
            int partial = 0;
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int alpha = maskPixels.getColor(x, y) >>> 24;
                    int blended = compositePixels.getColor(x, y);
                    if (alpha > 0 && alpha < tint >>> 24) partial++;
                    assertEquals(255, blended >>> 24);
                    for (int shift = 0; shift <= 16; shift += 8) {
                        int base = (background >>> shift) & 255;
                        int source = (tint >>> shift) & 255;
                        int target = Math.round(base + (source - base) * alpha / 255F);
                        assertEquals(target, (blended >>> shift) & 255, 2);
                    }
                }
            }
            assertTrue(partial > 100);
        }
    }

    private static void checkCoverage(float scale, int color) {
        int width = (int) (WIDTH * scale);
        int height = (int) (HEIGHT * scale);
        int alpha = color >>> 24;
        try (var actual = Surface.makeRasterN32Premul(width, height);
             var reference = Surface.makeRasterN32Premul(width * SUPERSAMPLE, height * SUPERSAMPLE);
             var actualPixels = new Bitmap(); var referencePixels = new Bitmap()) {
            actual.getCanvas().scale(scale, scale);
            reference.getCanvas().scale(scale * SUPERSAMPLE, scale * SUPERSAMPLE);
            SettingsCapsuleCoverage.draw(actual.getCanvas(), X, Y, W, H, RADIUS, color);
            drawCircularReference(reference, RADIUS, 0xFFFFFFFF);
            actualPixels.allocN32Pixels(width, height);
            referencePixels.allocN32Pixels(width * SUPERSAMPLE, height * SUPERSAMPLE);
            assertTrue(actual.readPixels(actualPixels, 0, 0));
            assertTrue(reference.readPixels(referencePixels, 0, 0));
            assertEquals(alpha, actualPixels.getColor(width / 2, height / 2) >>> 24);
            assertEquals(0, actualPixels.getColor(0, 0));
            double totalError = 0;
            int edgeCount = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double expected = referenceAlpha(referencePixels, x, y);
                    int drawn = actualPixels.getColor(x, y) >>> 24;
                    if ((expected > 0 && expected < 255) || (drawn > 0 && drawn < alpha)) {
                        totalError += Math.abs(expected - drawn * 255.0 / alpha);
                        edgeCount++;
                    }
                }
            }
            assertTrue(edgeCount > 100);
            double meanError = totalError / edgeCount;
            assertTrue(meanError < 5, "Mean coverage error at " + scale + "x, alpha " + alpha + ": " + meanError);
        }
    }

    private static void drawCircularReference(Surface surface, float radius, int color) {
        try (var paint = new Paint().setAntiAlias(true).setColor(color)) {
            surface.getCanvas().drawRRect(RRect.makeXYWH(X, Y, W, H, radius), paint);
        }
    }

    private static double referenceAlpha(Bitmap bitmap, int x, int y) {
        int alpha = 0;
        for (int dy = 0; dy < SUPERSAMPLE; dy++) {
            for (int dx = 0; dx < SUPERSAMPLE; dx++) {
                alpha += bitmap.getColor(x * SUPERSAMPLE + dx, y * SUPERSAMPLE + dy) >>> 24;
            }
        }
        return alpha / (double) (SUPERSAMPLE * SUPERSAMPLE);
    }
}
