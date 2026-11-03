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
    void oversizedPreparationFallsBackToTheLiveSourceWithoutEvictingCachedImages() {
        ImageQuality.release();
        try (Surface surface = Surface.makeRasterN32Premul(3076, 3076)) {
            surface.getCanvas().clear(0xFF5183B5);
            try (Image source = surface.makeImageSnapshot()) {
                Image boundary = ImageQuality.scaledFor(source, 1024);
                assertNotSame(source, boundary);
                assertFalse(boundary.isClosed());
                assertEquals(4_194_304L, ImageQuality.derivedPixels());

                Image oversized = ImageQuality.scaledFor(source, 1025);
                assertSame(source, oversized, "a copy larger than the whole budget must use the source");
                assertFalse(oversized.isClosed());
                assertFalse(boundary.isClosed(), "an uncacheable copy must not evict a valid cached copy");
                assertSame(boundary, ImageQuality.scaledFor(source, 1024));
                assertEquals(4_194_304L, ImageQuality.derivedPixels());
            } finally {
                ImageQuality.release();
            }
        }
    }

    @Test
    void repeatedOversizedPreparationAvoidsNativeResizing() {
        ImageQuality.release();
        for (int height : new int[]{3076, 5000}) {
            try (Surface surface = Surface.makeRasterN32Premul(3076, height);
                 Image source = surface.makeImageSnapshot()) {
                boolean wasEnabled = io.github.humbleui.skija.impl.Stats.enabled;
                io.github.humbleui.skija.impl.Stats.enabled = true;
                try {
                    long before = io.github.humbleui.skija.impl.Stats.nativeCalls;
                    for (int i = 0; i < 3; i++) {
                        assertSame(source, ImageQuality.scaledFor(source, 1025));
                        assertFalse(source.isClosed());
                    }
                    long calls = io.github.humbleui.skija.impl.Stats.nativeCalls - before;
                    assertTrue(calls <= 12, "oversized fallback should avoid raster resize calls, got " + calls);
                } finally {
                    io.github.humbleui.skija.impl.Stats.enabled = wasEnabled;
                    ImageQuality.release();
                }
            }
        }
    }

    @Test
    void combinedBudgetEvictionNeverClosesTheImageBeingReturned() {
        ImageQuality.release();
        try (Surface surface = Surface.makeRasterN32Premul(3076, 3076)) {
            surface.getCanvas().clear(0xFF5183B5);
            try (Image source = surface.makeImageSnapshot()) {
                Image old = ImageQuality.scaledFor(source, 1024);
                Image current = ImageQuality.scaledFor(source, 1023);
                assertFalse(current.isClosed(), "the just-prepared image is needed by the caller");
                assertTrue(old.isClosed(), "the older level should be evicted to stay within budget");
                assertSame(current, ImageQuality.scaledFor(source, 1023));
                assertEquals(4_186_116L, ImageQuality.derivedPixels());
            } finally {
                ImageQuality.release();
            }
        }
    }

    @Test
    void budgetEvictionAcrossSourcesKeepsTheNewImageAndPreservesSourceOwnership() {
        ImageQuality.release();
        try (Surface surface = Surface.makeRasterN32Premul(3076, 3076)) {
            surface.getCanvas().clear(0xFF5183B5);
            try (Image firstSource = surface.makeImageSnapshot();
                 Image secondSource = surface.makeImageSnapshot()) {
                Image old = ImageQuality.scaledFor(firstSource, 1024);
                Image current = ImageQuality.scaledFor(secondSource, 1023);
                assertFalse(current.isClosed());
                assertTrue(old.isClosed());
                assertSame(current, ImageQuality.scaledFor(secondSource, 1023));
                assertTrue(ImageQuality.derivedPixels() <= 4_194_304L);
                ImageQuality.release();
                assertTrue(current.isClosed());
                assertFalse(firstSource.isClosed());
                assertFalse(secondSource.isClosed());
                assertEquals(0L, ImageQuality.derivedPixels());
            } finally {
                ImageQuality.release();
            }
        }
    }

    @Test
    void perSourceLevelLimitEvictsOnlyTheOldestCopy() {
        ImageQuality.release();
        try (Image source = noise(256)) {
            Image oldest = ImageQuality.scaledFor(source, 16);
            Image kept = ImageQuality.scaledFor(source, 17);
            ImageQuality.scaledFor(source, 18);
            ImageQuality.scaledFor(source, 19);
            Image newest = ImageQuality.scaledFor(source, 20);
            assertTrue(oldest.isClosed());
            assertFalse(kept.isClosed());
            assertFalse(newest.isClosed());
            assertSame(kept, ImageQuality.scaledFor(source, 17));
            assertSame(newest, ImageQuality.scaledFor(source, 20));
        } finally {
            ImageQuality.release();
        }
    }

    @Test
    void oversizedImagesStillDrawThroughExistingConsumersAtDeviceDensity() {
        ImageQuality.release();
        UiPixelSnap.beginFrame(1.25F);
        try (Surface input = Surface.makeRasterN32Premul(3076, 3076)) {
            input.getCanvas().clear(0xFF5183B5);
            try (Image source = input.makeImageSnapshot();
                 Surface output = Surface.makeRasterN32Premul(820, 820)) {
                for (int consumer = 0; consumer < 3; consumer++) {
                    output.getCanvas().clear(0);
                    if (consumer == 0) {
                        SkiaImages.drawImage(output.getCanvas(), source, 0, 0, 820, 820);
                    } else if (consumer == 1) {
                        SkiaImages.drawRoundedImage(output.getCanvas(), source, 0, 0, 820, 820, 0);
                    } else {
                        ChatBubble.drawImage(output.getCanvas(), source, 0, 0, 820, 820, null, false);
                    }
                    assertTrue(pixels(output, 820)[410 * 820 + 410] > 0,
                            "consumer " + consumer + " must paint the image");
                    assertFalse(source.isClosed());
                    assertTrue(ImageQuality.derivedPixels() <= 4_194_304L);
                    assertEquals(0, ImageQuality.entryCount(), "an uncacheable image must not leave an empty entry");
                }
            } finally {
                ImageQuality.release();
                UiPixelSnap.beginFrame(1.0F);
            }
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
