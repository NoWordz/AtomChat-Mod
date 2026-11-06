package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD contract for P0-3 (dot-field image cache) plus a standalone pixel probe.
 *
 * <p>The reflection-based probes keep the public API untouched: the legacy
 * {@link DotTexture} compiles but fails these tests; the cached build passes.
 * {@link #bakedImageMatchesDirectPixels()} deliberately does NOT depend on the
 * flag: it answers, on the real Skija rasteriser, whether baking the dot field
 * into an Image and drawing it once can be pixel-identical to drawing every
 * circle directly onto an opaque background.
 */
class DotTextureCacheTest {

    private static final int W = 240;
    private static final int H = 180;
    private static final boolean INITIAL_FLAG = !"false".equalsIgnoreCase(
            System.getProperty("atomchat.dots.cached", "true"));

    private static Method method(String name, Class<?>... args) throws Exception {
        Method m = DotTexture.class.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private static boolean flag() throws Exception {
        return (Boolean) method("dotsCached").invoke(null);
    }

    private static void setFlag(boolean on) throws Exception {
        method("setDotsCached", boolean.class).invoke(null, on);
    }

    private static int cacheSize() throws Exception {
        return (Integer) method("cacheSize").invoke(null);
    }

    private static long cacheBuildCount() throws Exception {
        return (Long) method("cacheBuildCount").invoke(null);
    }

    private static long cacheHitCount() throws Exception {
        return (Long) method("cacheHitCount").invoke(null);
    }

    private static void clearCache() throws Exception {
        method("clearCache").invoke(null);
    }

    @AfterEach
    void restore() throws Exception {
        setFlag(INITIAL_FLAG);
        clearCache();
    }

    @Test
    void flagDefaultsToCachedPath() throws Exception {
        Assumptions.assumeTrue(System.getProperty("atomchat.dots.cached") == null,
                "default only verifiable when no -Datomchat.dots.cached is supplied");
        assertTrue(flag(), "the bounded static-field cache is the default production path");
    }

    @Test
    void flagOffAllocatesNoCache() throws Exception {
        setFlag(false);
        clearCache();
        long builds = cacheBuildCount();
        UiLayout.Rect bar = new UiLayout.Rect(20, 90, 200, 65);
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            Canvas canvas = surface.getCanvas();
            DotTexture.drawEdge(canvas, bar, 25, true, 0xFFFFFFFF);
            DotTexture.drawEdge(canvas, bar, 25, false, 0xFFFFFFFF);
            DotTexture.drawTrail(canvas, new UiLayout.Rect(20, 20, 200, 60), 0xFFFF0000);
            DotTexture.drawHalo(canvas, new UiLayout.Rect(20, 20, 200, 60), 0xFFFF0000);
        }
        assertEquals(0, cacheSize(), "flag off must not create any cached Image");
        assertEquals(builds, cacheBuildCount(), "flag off must not build any cached Image");
    }

    @Test
    void cacheKeyIncludesEveryVisualInputAndInvalidatesOnChange() throws Exception {
        setFlag(true);
        clearCache();
        UiLayout.Rect bar = new UiLayout.Rect(20, 90, 200, 65);
        long builds = cacheBuildCount();
        long hits = cacheHitCount();
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            Canvas canvas = surface.getCanvas();
            DotTexture.drawEdge(canvas, bar, 25, true, 0xFFFFFFFF);
            long afterFirst = cacheBuildCount();
            assertEquals(builds + 1, afterFirst, "first draw with a fresh key must build the Image");
            DotTexture.drawEdge(canvas, bar, 25, true, 0xFFFFFFFF);
            DotTexture.drawEdge(canvas, bar, 25, true, 0xFFFFFFFF);
            assertEquals(afterFirst, cacheBuildCount(), "identical key must reuse the cached Image");
            assertTrue(cacheHitCount() >= hits + 2, "reuse must be counted as hits");

            // Each of these changes one component of the cache key.
            DotTexture.drawEdge(canvas, new UiLayout.Rect(20, 90, 201, 65), 25, true, 0xFFFFFFFF);
            assertEquals(afterFirst + 1, cacheBuildCount(), "width change must rebuild");
            DotTexture.drawEdge(canvas, new UiLayout.Rect(20, 90, 200, 66), 25, true, 0xFFFFFFFF);
            assertEquals(afterFirst + 2, cacheBuildCount(), "height change must rebuild");
            DotTexture.drawEdge(canvas, bar, 24, true, 0xFFFFFFFF);
            assertEquals(afterFirst + 3, cacheBuildCount(), "radius change must rebuild");
            DotTexture.drawEdge(canvas, bar, 25, true, 0xFF00FF00);
            assertEquals(afterFirst + 4, cacheBuildCount(), "tint (theme) change must rebuild");
            DotTexture.drawEdge(canvas, bar, 25, false, 0xFFFFFFFF);
            assertEquals(afterFirst + 5, cacheBuildCount(), "above change must rebuild");
            DotTexture.drawTrail(canvas, new UiLayout.Rect(20, 20, 200, 60), 0xFFFF0000);
            assertEquals(afterFirst + 6, cacheBuildCount(), "different field kind must rebuild");
            DotTexture.drawEdge(canvas, new UiLayout.Rect(21, 91, 200, 65), 25, true, 0xFFFFFFFF);
            assertEquals(afterFirst + 7, cacheBuildCount(), "strip origin change must rebuild");
        }
    }

    @Test
    void sameSizeStripsAtDifferentPositionsAreNotShared() throws Exception {
        UiLayout.Rect a = new UiLayout.Rect(20, 90, 200, 65);
        UiLayout.Rect b = new UiLayout.Rect(20, 20, 200, 65);
        UiLayout.Rect boxA = new UiLayout.Rect(20, 20, 200, 60);
        UiLayout.Rect boxB = new UiLayout.Rect(20, 100, 200, 60);
        java.util.function.Consumer<Canvas> draw = canvas -> {
            opaqueBackground(canvas);
            DotTexture.drawEdge(canvas, a, 25, true, 0xFFFFFFFF);
            DotTexture.drawEdge(canvas, b, 25, true, 0xFFFFFFFF);
            DotTexture.drawTrail(canvas, boxA, 0xFFFF0000);
            DotTexture.drawTrail(canvas, boxB, 0xFFFF0000);
        };
        setFlag(false);
        byte[] off = render(W, H, draw);
        clearCache();
        setFlag(true);
        byte[] on = render(W, H, draw);
        assertArrayEquals(off, on,
                "same-size strips at different positions must not share a Picture");
    }

    @Test
    void cacheReleasesImagesAndRepeatedClearIsSafe() throws Exception {
        setFlag(true);
        clearCache();
        clearCache();
        assertEquals(0, cacheSize());
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            DotTexture.drawEdge(surface.getCanvas(), new UiLayout.Rect(20, 90, 200, 65), 25, true, 0xFFFFFFFF);
            assertTrue(cacheSize() > 0);
        }
        clearCache();
        assertEquals(0, cacheSize(), "clearCache must close and drop every cached Image");
        clearCache();
        assertEquals(0, cacheSize(), "clearCache must be idempotent");
    }

    /**
     * Decision evidence (runs on the legacy build too). Baking the dot field
     * into a transparent Image and drawing it once with drawImageRect is NOT
     * pixel-identical: repeated src-over onto transparent then one src-over onto
     * an opaque background rounds differently from thousands of src-over ops
     * directly on that background (measured: 1 LSB on a channel). That rules
     * out an Image/drawImageRect cache under the zero-pixel-difference rule.
     *
     * <p>Recording the very same draw commands into a Picture and replaying it
     * IS pixel-identical, which is why the cached path uses a Picture.
     */
    @Test
    void pictureReplayIsIdenticalButBakedImageIsNot() {
        UiLayout.Rect bar = new UiLayout.Rect(20, 90, 200, 65);
        byte[] direct = render(W, H, canvas -> {
            opaqueBackground(canvas);
            DotTexture.drawEdge(canvas, bar, 25, true, 0xFFFFFFFF);
        });

        io.github.humbleui.skija.PictureRecorder recorder =
                new io.github.humbleui.skija.PictureRecorder();
        io.github.humbleui.skija.Picture picture;
        try {
            Canvas recording = recorder.beginRecording(Rect.makeXYWH(-100000, -100000, 200000, 200000));
            DotTexture.drawEdge(recording, bar, 25, true, 0xFFFFFFFF);
            picture = recorder.finishRecordingAsPicture();
        } finally {
            recorder.close();
        }
        byte[] replayed;
        try (picture; Surface target = Surface.makeRasterN32Premul(W, H)) {
            opaqueBackground(target.getCanvas());
            target.getCanvas().drawPicture(picture);
            replayed = pixels(target);
        }
        assertArrayEquals(direct, replayed,
                "Picture replay must reproduce the exact draw commands, byte for byte");

        Image baked;
        try (Surface offscreen = Surface.makeRasterN32Premul(W, H)) {
            DotTexture.drawEdge(offscreen.getCanvas(), bar, 25, true, 0xFFFFFFFF);
            baked = offscreen.makeImageSnapshot();
        }
        byte[] composed;
        try (baked; Surface target = Surface.makeRasterN32Premul(W, H)) {
            opaqueBackground(target.getCanvas());
            target.getCanvas().drawImageRect(baked, Rect.makeXYWH(0, 0, W, H));
            composed = pixels(target);
        }
        assertFalse(java.util.Arrays.equals(direct, composed),
                "Image baking must differ (this is the recorded reason it was rejected)");
    }

    @Test
    void flagOffAndOnIdenticalUnderClipAndTransform() throws Exception {
        UiLayout.Rect bar = new UiLayout.Rect(20, 90, 200, 65);
        UiLayout.Rect box = new UiLayout.Rect(20, 20, 200, 60);
        java.util.function.Consumer<Canvas> draw = canvas -> {
            canvas.save();
            try {
                canvas.translate(7, 11);
                canvas.clipRect(Rect.makeXYWH(0, 0, 160, 130));
                opaqueBackground(canvas);
                DotTexture.drawEdge(canvas, bar, 25, true, 0xFFFFFFFF);
                DotTexture.drawEdge(canvas, bar, 25, false, 0xFFFFFFFF);
                DotTexture.drawTrail(canvas, box, 0xFFFF0000);
                DotTexture.drawHalo(canvas, box, 0xFF3B82F6);
            } finally {
                canvas.restore();
            }
        };
        setFlag(false);
        byte[] off = render(W, H, draw);
        clearCache();
        setFlag(true);
        byte[] on = render(W, H, draw);
        assertArrayEquals(off, on,
                "small cached halo fields stay byte-identical under a transform and clip");
    }

    @Test
    void flagOffAndOnProduceIdenticalPixels() throws Exception {
        UiLayout.Rect bar = new UiLayout.Rect(20, 90, 200, 65);
        UiLayout.Rect box = new UiLayout.Rect(20, 20, 200, 60);
        setFlag(false);
        byte[] off = render(W, H, canvas -> {
            opaqueBackground(canvas);
            DotTexture.drawEdge(canvas, bar, 25, true, 0xFFFFFFFF);
            DotTexture.drawEdge(canvas, bar, 25, false, 0xFFFFFFFF);
            DotTexture.drawTrail(canvas, box, 0xFFFF0000);
            DotTexture.drawHalo(canvas, box, 0xFF3B82F6);
        });
        clearCache();
        setFlag(true);
        byte[] on = render(W, H, canvas -> {
            opaqueBackground(canvas);
            DotTexture.drawEdge(canvas, bar, 25, true, 0xFFFFFFFF);
            DotTexture.drawEdge(canvas, bar, 25, false, 0xFFFFFFFF);
            DotTexture.drawTrail(canvas, box, 0xFFFF0000);
            DotTexture.drawHalo(canvas, box, 0xFF3B82F6);
        });
        assertArrayEquals(off, on, "small cached halo fields stay byte-identical");
    }

    @Test
    void largeHaloUsesBoundedImageCacheWithinEightLsb() throws Exception {
        int width = 320;
        int height = 215;
        UiLayout.Rect box = new UiLayout.Rect(0, 0, width, height);
        java.util.function.Consumer<Canvas> draw = canvas -> {
            canvas.clear(0xFF1F2937);
            DotTexture.drawHalo(canvas, box, 0xFF9FB0C8);
        };

        setFlag(false);
        byte[] direct = render(width, height, draw);
        clearCache();
        setFlag(true);
        long before = cacheBuildCount();
        byte[] cached = render(width, height, draw);
        assertEquals(before + 1, cacheBuildCount(), "one static halo image is built");
        assertEquals(1, cacheSize(), "the large halo uses one bounded image entry");
        assertWithinEightLsb(direct, cached, "large cached halo may differ by at most eight LSB");
        render(width, height, draw);
        assertEquals(before + 1, cacheBuildCount(), "repeated frames must reuse the halo image");
        render(width, height, canvas -> DotTexture.drawHalo(
                canvas, new UiLayout.Rect(17, 23, width, height), 0xFF9FB0C8));
        assertEquals(before + 1, cacheBuildCount(), "moving the same halo does not rebuild its local image");
        render(width + 1, height, canvas -> DotTexture.drawHalo(
                canvas, new UiLayout.Rect(0, 0, width + 1, height), 0xFF9FB0C8));
        assertEquals(before + 2, cacheBuildCount(), "size is part of the image key");
        render(width + 1, height, canvas -> DotTexture.drawHalo(
                canvas, new UiLayout.Rect(0, 0, width + 1, height), 0xFFE06060));
        assertEquals(before + 3, cacheBuildCount(), "tint is part of the image key");
    }

    @Test
    void largeHaloCapacityEvictsAndClosesTheOldestNativeImage() throws Exception {
        setFlag(true);
        clearCache();
        render(320, 215, canvas -> DotTexture.drawHalo(
                canvas, new UiLayout.Rect(0, 0, 320, 215), 0xFF9FB0C8));
        Field field = DotTexture.class.getDeclaredField("HALO_IMAGES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Image> images = (Map<Object, Image>) field.get(null);
        Image oldest = images.values().iterator().next();

        for (int i = 1; i <= 8; i++) {
            int width = 320 + i;
            render(width, 215, canvas -> DotTexture.drawHalo(
                    canvas, new UiLayout.Rect(0, 0, width, 215), 0xFF9FB0C8));
        }
        assertTrue(images.size() <= 8, "both entry and byte budgets stay bounded");
        assertTrue(oldest.isClosed(), "capacity eviction must close the native Image");
    }

    @Test
    void oneHaloLargerThanTheByteBudgetFallsBackToPicture() throws Exception {
        setFlag(true);
        clearCache();
        render(8, 8, canvas -> DotTexture.drawHalo(
                canvas, new UiLayout.Rect(0, 0, 1024, 1024), 0xFF9FB0C8));
        Field field = DotTexture.class.getDeclaredField("HALO_IMAGES");
        field.setAccessible(true);
        assertTrue(((Map<?, ?>) field.get(null)).isEmpty(),
                "a single over-budget halo must not be allocated");
        assertEquals(1, cacheSize(), "the bounded Picture fallback remains available");
    }

    private static void opaqueBackground(Canvas canvas) {
        canvas.clear(0xFF1F2937);
        try (io.github.humbleui.skija.Paint paint = new io.github.humbleui.skija.Paint()) {
            paint.setColor(0xFF334155);
            canvas.drawRect(Rect.makeXYWH(10, 10, W - 20, H - 20), paint);
        }
    }

    private static byte[] render(int width, int height, java.util.function.Consumer<Canvas> draw) {
        try (Surface surface = Surface.makeRasterN32Premul(width, height)) {
            draw.accept(surface.getCanvas());
            return pixels(surface);
        }
    }

    private static byte[] pixels(Surface surface) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(surface.getWidth(), surface.getHeight());
            assertTrue(surface.readPixels(bitmap, 0, 0), "cannot read back raster pixels");
            return bitmap.readPixels();
        }
    }

    private static void assertWithinEightLsb(byte[] expected, byte[] actual, String message) {
        assertEquals(expected.length, actual.length, message);
        for (int i = 0; i < expected.length; i++) {
            assertTrue(Math.abs((expected[i] & 0xFF) - (actual[i] & 0xFF)) <= 8,
                    message + " at byte " + i + " (delta "
                            + Math.abs((expected[i] & 0xFF) - (actual[i] & 0xFF)) + ")");
        }
    }
}
