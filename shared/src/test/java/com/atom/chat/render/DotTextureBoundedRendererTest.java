package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Raster contracts: visibility, exact small fields, and bounded large-field native lifetimes. */
class DotTextureBoundedRendererTest {
    private final boolean original = DotTexture.dotsCached();

    @AfterEach void restore() {
        DotTexture.clearCache();
        DotTexture.setDotsCached(original);
    }

    @Test void edgeDotsUseTheStrongerAlphaRampWithoutChangingTintOrGlow() {
        var bar = new UiLayout.Rect(16, 90, 200, 60);
        int tint = 0xD839AC61;
        DotTexture.setDotsCached(false);
        byte[] expected = render(240, 180, c -> referenceEdge(c, bar, tint));
        byte[] actual = render(240, 180, c -> DotTexture.drawEdge(c, bar, 25, true, tint));
        assertArrayEquals(expected, actual, "edge dot alpha ramp must end at 64; tint and blur stay unchanged");
    }

    @Test void fieldDotsAndGlowRetainTheirEstablishedRasterProfile() {
        var box = new UiLayout.Rect(10, 10, 200, 100);
        int tint = 0xD839AC61;
        DotTexture.setDotsCached(false);
        for (boolean trail : new boolean[]{false, true}) {
            byte[] expected = render(240, 140, c -> referenceField(c, box, tint, trail));
            byte[] actual = render(240, 140, c -> field(c, box, tint, trail));
            assertArrayEquals(expected, actual, "field alpha and glow retain the established pixel profile");
        }
    }

    @Test void largeTrailUsesOneLocalImageAcrossStableAndTranslatedFrames() throws Exception {
        DotTexture.clearCache();
        DotTexture.setDotsCached(true);
        var box = new UiLayout.Rect(0, 0, 320, 215);
        try (Surface surface = Surface.makeRasterN32Premul(400, 280)) {
            field(surface.getCanvas(), box, 0xE89FB0C8, true);
            assertEquals(1, images().size(), "large trails must stop replaying one circle at a time");
            long builds = DotTexture.cacheBuildCount();
            for (int i = 0; i < 120; i++) field(surface.getCanvas(),
                    new UiLayout.Rect(17, 23, 320, 215), 0xE89FB0C8, true);
            assertEquals(builds, DotTexture.cacheBuildCount());
            assertEquals(1, images().size());
            field(surface.getCanvas(), box, 0xE89FB0C8, false);
            assertEquals(2, images().size(), "trail and halo cannot alias the same image");
        }
    }

    @Test void smallFieldsAndEdgesStayByteIdenticalUnderTransformsClipsAndAlpha() {
        for (float scale : new float[]{.75F, 1F, 1.25F, 2F}) {
            Consumer<Canvas> scene = c -> {
                c.translate(17.25F, 23.5F);
                c.scale(scale, scale);
                c.clipRect(Rect.makeXYWH(0, 0, 220, 180));
                try (Paint alpha = new Paint().setAlphaf(.65F)) {
                    c.saveLayer(null, alpha);
                    DotTexture.drawEdge(c, new UiLayout.Rect(0, 80, 200, 60), 25, true, 0xD839AC61);
                    DotTexture.drawHalo(c, new UiLayout.Rect(0, 0, 200, 100), 0xD839AC61);
                    DotTexture.drawTrail(c, new UiLayout.Rect(0, 0, 200, 100), 0xD839AC61);
                    c.restore();
                }
            };
            DotTexture.setDotsCached(false);
            byte[] direct = render(520, 440, scene);
            DotTexture.clearCache();
            DotTexture.setDotsCached(true);
            assertArrayEquals(direct, render(520, 440, scene), "small-field tolerance remains zero at " + scale);
        }
    }

    @Test void alignedLargeFieldsKeepTheExistingEightLsbLimit() {
        for (boolean trail : new boolean[]{true, false}) {
                Consumer<Canvas> scene = c -> {
                    c.translate(17, 23);
                    c.clipRect(Rect.makeXYWH(1, 1, 318, 213));
                    field(c, new UiLayout.Rect(0, 0, 320, 215), 0xE89FB0C8, trail);
                };
                DotTexture.setDotsCached(false);
                byte[] direct = render(700, 500, scene);
                DotTexture.clearCache();
                DotTexture.setDotsCached(true);
                byte[] cached = render(700, 500, scene);
                int max = maxDelta(direct, cached);
                System.out.println("DOT_PIXELS trail=" + trail + " scale=1 max_delta=" + max);
                assertTrue(max <= 8, "large-field tolerance remains eight; delta=" + max);
        }
    }

    @Test void realUiDensitiesRetainLegacyFourTimesHaloImagesAndStableCache() throws Exception {
        var box = new UiLayout.Rect(0, 0, 320, 215);
        for (float scale : new float[]{1.25F, 1.5F, 2F}) {
            DotTexture.clearCache();
            DotTexture.setDotsCached(false);
            try (Surface bake = Surface.makeRasterN32Premul(1280, 860)) {
                bake.getCanvas().clear(0);
                bake.getCanvas().scale(4, 4);
                DotTexture.drawHalo(bake.getCanvas(), box, 0xE89FB0C8);
                try (Image legacy = bake.makeImageSnapshot()) {
                    byte[] expected = render(700, 500, c -> {
                        c.translate(17.25F, 23.5F);
                        c.scale(scale, scale);
                        c.clipRect(Rect.makeWH(320, 215));
                        c.drawImageRect(legacy, Rect.makeWH(1280, 860), Rect.makeWH(320, 215),
                                SamplingMode.LINEAR, null, false);
                    });
                    DotTexture.setDotsCached(true);
                    Consumer<Canvas> scene = c -> {
                        c.translate(17.25F, 23.5F);
                        c.scale(scale, scale);
                        DotTexture.drawHalo(c, box, 0xE89FB0C8);
                    };
                    assertArrayEquals(expected, render(700, 500, scene),
                            "scaled halo must preserve the legacy 4x sampling at density " + scale);
                    assertEquals(1, images().size(), "scaled halo must not fall back to a Picture");
                    assertEquals(1280, images().values().iterator().next().getWidth());
                    long builds = DotTexture.cacheBuildCount();
                    for (int i = 0; i < 120; i++) render(8, 8, scene);
                    assertEquals(builds, DotTexture.cacheBuildCount());
                }
            }
        }
    }

    @Test void largeFieldImagesShareEntryAndByteBudgetsAndCloseEvictions() throws Exception {
        DotTexture.clearCache();
        DotTexture.setDotsCached(true);
        try (Surface surface = Surface.makeRasterN32Premul(8, 8)) {
            field(surface.getCanvas(), new UiLayout.Rect(0, 0, 320, 215), 0xE89FB0C8, true);
            assertFalse(images().isEmpty());
            Image oldest = images().values().iterator().next();
            for (int i = 1; i <= 12; i++) {
                field(surface.getCanvas(), new UiLayout.Rect(0, 0, 320 + i, 215),
                        0xE89FB0C8, i % 2 == 0);
                assertTrue(images().size() <= 8);
                long bytes = images().values().stream().mapToLong(image ->
                        (long) image.getWidth() * image.getHeight() * 4).sum();
                assertTrue(bytes <= 32L * 1024 * 1024);
            }
            assertTrue(oldest.isClosed());
            Image retained = images().values().iterator().next();
            DotTexture.clearCache();
            assertTrue(retained.isClosed());
            assertTrue(images().isEmpty());
        }
    }

    @Test void overBudgetFieldsUseBoundedPictureFallback() throws Exception {
        DotTexture.clearCache();
        DotTexture.setDotsCached(true);
        try (Surface surface = Surface.makeRasterN32Premul(8, 8)) {
            field(surface.getCanvas(), new UiLayout.Rect(0, 0, 1024, 1024), 0xE89FB0C8, true);
            assertTrue(images().isEmpty());
            assertEquals(1, DotTexture.cacheSize());
        }
    }

    @Test void oversizedDimensionsKeepTheExactPictureFallback() throws Exception {
        DotTexture.setDotsCached(true);
        for (var box : new UiLayout.Rect[]{
                new UiLayout.Rect(0, 0, 5000, 10)}) {
            DotTexture.clearCache();
            render(8, 8, c -> DotTexture.drawTrail(c, box, 0xE89FB0C8));
            assertTrue(images().isEmpty(), "over-budget dimensions must not populate the image cache");
            assertEquals(1, DotTexture.cacheSize());
        }
    }

    @Test void nonFiniteBoundsDoNotAllocateNativeCacheEntries() {
        DotTexture.clearCache();
        DotTexture.setDotsCached(true);
        try (Surface surface = Surface.makeRasterN32Premul(8, 8)) {
            for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                DotTexture.drawHalo(surface.getCanvas(), new UiLayout.Rect(value, 0, 200, 100), 0xFFFFFFFF);
                DotTexture.drawTrail(surface.getCanvas(), new UiLayout.Rect(0, 0, value, 100), 0xFFFFFFFF);
            }
            assertEquals(0, DotTexture.cacheSize());
        }
    }

    private static void field(Canvas c, UiLayout.Rect box, int tint, boolean trail) {
        if (trail) DotTexture.drawTrail(c, box, tint); else DotTexture.drawHalo(c, box, tint);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Image> images() throws Exception {
        Field field = DotTexture.class.getDeclaredField("HALO_IMAGES");
        field.setAccessible(true);
        return (Map<Object, Image>) field.get(null);
    }

    private static byte[] render(int width, int height, Consumer<Canvas> draw) {
        try (Surface surface = Surface.makeRasterN32Premul(width, height); Bitmap bitmap = new Bitmap()) {
            surface.getCanvas().clear(0xFF18212D);
            draw.accept(surface.getCanvas());
            bitmap.allocN32Pixels(width, height);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }

    private static int maxDelta(byte[] a, byte[] b) {
        assertEquals(a.length, b.length);
        int max = 0;
        for (int i = 0; i < a.length; i++) max = Math.max(max, Math.abs((a[i] & 255) - (b[i] & 255)));
        return max;
    }

    private static int tint(int color, float alpha) {
        return (color & 0xFFFFFF) | (Math.round(alpha * (color >>> 24) / 255F) << 24);
    }

    /** Frozen geometry oracle for the reviewed stronger edge ramp; blur and field profile stay unchanged. */
    private static void referenceEdge(Canvas c, UiLayout.Rect bar, int color) {
        float r = 25, depth = UiTokens.MESSAGE_DOT_DEPTH;
        var outline = io.github.humbleui.types.RRect.makeXYWH(bar.x(), bar.y(), bar.w(), bar.h(), r);
        try (Paint dot = new Paint().setAntiAlias(true);
             MaskFilter blur = MaskFilter.makeBlur(FilterBlurMode.NORMAL, UiTokens.DOT_GLOW_BLUR);
             Paint glow = new Paint().setAntiAlias(true).setMaskFilter(blur)) {
            c.save();
            c.clipRect(Rect.makeXYWH(bar.x(), bar.y() - depth, bar.w(), depth + r));
            c.clipRRect(outline, ClipMode.DIFFERENCE, true);
            c.drawRRect(outline, glow.setMode(PaintMode.STROKE).setStrokeWidth(UiTokens.DOT_GLOW_STROKE)
                    .setColor(tint(color, UiTokens.DOT_GLOW_ALPHA)));
            int[] alphas = {19, 26, 32, 38, 45, 50, 54, 59, 64};
            for (int i = 0; i < Math.ceil(bar.w() / UiTokens.MESSAGE_DOT_GAP); i++) {
                float x = UiTokens.MESSAGE_DOT_INSET + i * UiTokens.MESSAGE_DOT_GAP;
                float dx = Math.max(0, Math.max(r - x, x - (bar.w() - r)));
                float corner = r - (float)Math.sqrt(Math.max(0, r*r - dx*dx));
                float fade = Math.max(0, Math.min(1, Math.min(x, bar.w()-x)/UiTokens.MESSAGE_DOT_EDGE_FADE));
                for (int row = 0; row < alphas.length; row++) {
                    float distance = depth - row * UiTokens.MESSAGE_DOT_GAP;
                    c.drawCircle(bar.x()+x, bar.y()+corner-distance,
                            UiTokens.MESSAGE_DOT_RADII[row], dot.setColor(tint(color, alphas[row]*fade)));
                }
            }
            c.restore();
        }
    }

    private static void referenceField(Canvas c, UiLayout.Rect box, int color, boolean trail) {
        try (Paint dot = new Paint().setAntiAlias(true);
             MaskFilter blur = MaskFilter.makeBlur(FilterBlurMode.NORMAL, UiTokens.DOT_GLOW_BLUR);
             Paint glow = new Paint().setAntiAlias(true).setMaskFilter(blur)) {
            c.save();
            c.clipRect(Rect.makeXYWH(box.x(), box.y(), box.w(), box.h()));
            float glowW = box.w() * (trail ? .65F : .7F);
            c.drawOval(Rect.makeXYWH(box.x()+(trail?0:(box.w()-glowW)/2), box.y()+box.h()*.3F,
                    glowW, box.h()*.4F), glow.setMode(PaintMode.FILL).setColor(tint(color, 8F)));
            for (int i=0; i<Math.ceil(box.w()/UiTokens.MESSAGE_DOT_GAP); i++) {
                float x=UiTokens.MESSAGE_DOT_INSET+i*UiTokens.MESSAGE_DOT_GAP, u=x/box.w();
                for (int j=0; j<Math.ceil(box.h()/UiTokens.MESSAGE_DOT_GAP); j++) {
                    float y=UiTokens.MESSAGE_DOT_INSET+j*UiTokens.MESSAGE_DOT_GAP;
                    float v=(y/box.h()-.5F)*2;
                    float falloff=trail?(1-u)*Math.max(0,1-v*v/Math.max(.01F,1-u*.75F))
                            :1-(u*2-1)*(u*2-1)-v*v;
                    if(falloff>0)c.drawCircle(box.x()+x,box.y()+y,UiTokens.MESSAGE_DOT_RADII[8]*(.35F+.65F*falloff),
                            dot.setColor(tint(color,32F*falloff)));
                }
            }
            c.restore();
        }
    }
}
