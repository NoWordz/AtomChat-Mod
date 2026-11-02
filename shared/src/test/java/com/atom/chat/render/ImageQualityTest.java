package com.atom.chat.render;

import org.junit.jupiter.api.Test;

import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behaviour pins for {@link ImageQuality} — the minification fix. The claim
 * being tested is not "a different sampling mode is used" but the visible
 * consequence: a downscaled draw of a pre-decimated image must be measurably
 * closer to an exact area average than the same draw straight from the source.
 */
class ImageQualityTest {

    /**
     * Deterministic broadband noise at 1-px cells. This is the input that
     * separates the two strategies: every source texel carries independent
     * detail, so a 2x2 bilinear tap over an 8x reduction keeps a fraction of
     * them, while repeated halving averages them all. A checkerboard with
     * multi-pixel cells is useless here — both paths are exact on it.
     */
    private static Image noise(int side) {
        java.util.Random random = new java.util.Random(20261102L);
        byte[] rgba = new byte[side * side * 4];
        for (int i = 0; i < side * side; i++) {
            int v = random.nextInt(256);
            rgba[i * 4] = (byte) v;
            rgba[i * 4 + 1] = (byte) v;
            rgba[i * 4 + 2] = (byte) v;
            rgba[i * 4 + 3] = (byte) 255;
        }
        return Image.makeRaster(ImageInfo.makeN32Premul(side, side), rgba, side * 4L);
    }

    /** Mean |luma - exact area average| over the whole quad; lower is better. */
    private static double reduceError(Image source, int dst, boolean preDecimate) {
        Image drawn = preDecimate ? ImageQuality.scaledFor(source, dst) : source;
        try (Surface surface = Surface.makeRasterN32Premul(dst, dst);
             Paint paint = new Paint()) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFF202020);
            canvas.drawImageRect(drawn, Rect.makeWH(drawn.getWidth(), drawn.getHeight()),
                    Rect.makeWH(dst, dst), SamplingMode.LINEAR, paint, false);
            int[] got = pixels(surface, dst);
            int factor = source.getWidth() / dst;
            int[] src = pixelsOf(source);
            double total = 0.0;
            for (int y = 0; y < dst; y++) {
                for (int x = 0; x < dst; x++) {
                    int sum = 0;
                    for (int sy = 0; sy < factor; sy++) {
                        for (int sx = 0; sx < factor; sx++) {
                            sum += src[(y * factor + sy) * source.getWidth() + x * factor + sx];
                        }
                    }
                    total += Math.abs(got[y * dst + x] - sum / (factor * factor));
                }
            }
            return total / (dst * (double) dst);
        }
    }

    private static int[] pixels(Surface surface, int side) {
        try (io.github.humbleui.skija.Bitmap bitmap = new io.github.humbleui.skija.Bitmap()) {
            bitmap.allocN32Pixels(side, side);
            assertTrue(surface.readPixels(bitmap, 0, 0), "readPixels");
            byte[] raw = bitmap.readPixels();
            int[] luma = new int[side * side];
            for (int i = 0; i < luma.length; i++) {
                int b = raw[i * 4] & 255, g = raw[i * 4 + 1] & 255, r = raw[i * 4 + 2] & 255;
                luma[i] = (r * 2126 + g * 7152 + b * 722) / 10000;
            }
            return luma;
        }
    }

    private static int[] pixelsOf(Image image) {
        int side = image.getWidth();
        try (Surface surface = Surface.makeRasterN32Premul(side, image.getHeight());
             Paint paint = new Paint()) {
            surface.getCanvas().clear(0xFF000000);
            surface.getCanvas().drawImageRect(image,
                    Rect.makeWH(image.getWidth(), image.getHeight()),
                    Rect.makeWH(image.getWidth(), image.getHeight()),
                    SamplingMode.LINEAR, paint, false);
            return pixels(surface, side);
        }
    }

    @Test
    void preDecimatingIsMeasurablyCloserToAnAreaAverageThanDrawingTheSource() {
        try (Image source = noise(256)) {
            ImageQuality.release();
            double straight = reduceError(source, 32, false);
            double preScaled = reduceError(source, 32, true);
            assertTrue(preScaled < straight * 0.75,
                    "pre-decimated " + preScaled + " must clearly beat a direct LINEAR draw " + straight);
        }
    }

    @Test
    void thePreparedBitmapIsExactlyTwiceTheDrawnSize() {
        // The whole gain comes from landing on exactly 2x: the GPU's bilinear
        // tap is exact there and merely approximate elsewhere. One MITCHELL
        // resize straight to the target (or halving to 2x-minus-a-bit) measured
        // 4x worse in the probe, so this is a behavioural contract, not an
        // implementation detail.
        for (int source : new int[]{128, 256, 768}) {
            for (int target : new int[]{16, 24, 40}) {
                try (Image image = noise(source)) {
                    ImageQuality.release();
                    Image prepared = ImageQuality.scaledFor(image, target);
                    // Preparation only pays past 3x: at 2.1x the copy is almost
                    // the size of the source, so the resize buys nothing.
                    if (source <= target * 3) {
                        assertSame(image, prepared, source + "->" + target + " needs no copy");
                        continue;
                    }
                    assertEquals(target * 2, prepared.getWidth(),
                            source + " -> " + target + " px must prepare at exactly 2x");
                }
            }
        }
    }

    @Test
    void anImageBelowThePreparationGateIsReturnedUntouched() {
        try (Image source = noise(64)) {
            assertSame(source, ImageQuality.scaledFor(source, 40), "1.6x is below the 3x gate");
            assertSame(source, ImageQuality.scaledFor(source, 64), "an exact fit needs nothing");
        }
    }

    @Test
    void theSameSourceIsDecimatedOnceAndReused() {
        try (Image source = noise(256)) {
            ImageQuality.release();
            Image first = ImageQuality.scaledFor(source, 32);
            int entries = ImageQuality.entryCount();
            Image second = ImageQuality.scaledFor(source, 32);
            assertSame(first, second, "a repeat call must hit the cache");
            assertEquals(entries, ImageQuality.entryCount());
            assertTrue(ImageQuality.derivedPixels() > 0);
        }
    }

    @Test
    void releaseClosesDerivedCopiesButNotTheSource() {
        Image source = noise(256);
        Image derived = ImageQuality.scaledFor(source, 32);
        assertNotSame(source, derived);
        ImageQuality.release();
        assertEquals(0, ImageQuality.entryCount());
        assertEquals(0L, ImageQuality.derivedPixels());
        // The source still works: this cache never owns it.
        assertEquals(256, source.getWidth());
        source.close();
    }

    @Test
    void repeatedHalvingKeepsTheAspectRatioAndStaysAboveOnePixelPerSide() {
        try (Image source = noise(256)) {
            ImageQuality.release();
            Image tiny = ImageQuality.scaledFor(source, 1);
            assertTrue(tiny.getWidth() >= 1 && tiny.getHeight() >= 1, "never degenerate");
            assertEquals(tiny.getWidth(), tiny.getHeight(), "a square source stays square");
            assertTrue(tiny.getWidth() <= 2, "target 1 allows at most 2x");
        }
    }

    @Test
    void everyImageDrawSiteGoesThroughTheDecimatingWrappers() throws Exception {
        Path root = repositoryRoot();
        List<String> offenders = new ArrayList<>();
        for (String layer : List.of("layers/mapping/official/src/main/java",
                "platforms/1.21.1-fabric/src/main/java",
                "shared/src/main/java")) {
            for (Path file : javaFiles(root.resolve(layer))) {
                String text = Files.readString(file);
                // SkiaDraw/SkiaImages and the low-level loaders own the raw call.
                if (file.getFileName().toString().equals("SkiaImages.java")) continue;
                if (text.contains("SkiaDraw.drawRoundedImage(")) {
                    offenders.add("raw rounded blit: " + root.relativize(file));
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "image blits must go through SkiaImages so minification quality is not per-call-site: " + offenders);
    }

    @Test
    void aDensityOfOneLeavesTheGeometryAlone() {
        UiPixelSnap.beginFrame(1.0F);
        try (Image source = noise(64)) {
            assertSame(source, ImageQuality.scaledForLogical(source, 40.0F));
        }
    }

    private static Path repositoryRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("platforms"))) root = root.getParent();
        return java.util.Objects.requireNonNull(root);
    }

    private static List<Path> javaFiles(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.walk(dir)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
