package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.SkiaDraw;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Controlled hidden-GL A/B for the persistent settings/About costs. This is
 * deliberately opt-in: regular builds stay fast, while the diagnostic runs the
 * production Skia primitives against a real GL backend with glFinish fences.
 */
class AboutGpuBenchmark {
    private static final int W = 480;
    private static final int H = 860;
    private static final int WARMUP = 20;
    private static final int MEASURED = 80;

    @Test
    void shadowAndAboutStaticLayers() throws Exception {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_GPU_BENCH")),
                "set ATOMCHAT_GPU_BENCH=1 for the hidden-GL A/B");
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"),
                "the controlled benchmark requires a Windows OpenGL driver");
        assertTrue(GLFW.glfwInit(), "GLFW init");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(W, H, "AtomChat GPU A/B", 0, 0);
        assertNotEquals(0L, window);
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GPU_AB renderer=" + GL11C.glGetString(GL11C.GL_RENDERER));
            try (DirectContext context = DirectContext.makeGL();
                 BackendRenderTarget backend = BackendRenderTarget.makeGL(W, H, 0, 8, 0, GL11C.GL_RGBA8);
                 Surface surface = Surface.wrapBackendRenderTarget(context, backend,
                         SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888, ColorSpace.getSRGB())) {
                runShadowAab(context, surface);
                runAboutAab(context, surface);
            }
        } finally {
            SkiaDraw.releaseShadowFilters();
            DotTexture.clearCache();
            GLFW.glfwMakeContextCurrent(0);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void runShadowAab(DirectContext context, Surface surface) throws Exception {
        Method direct = SkiaDraw.class.getDeclaredMethod("setDirectShadowForTest", boolean.class);
        direct.setAccessible(true);
        Field images = SkiaDraw.class.getDeclaredField("SHADOW_IMAGES");
        images.setAccessible(true);

        SkiaDraw.releaseShadowFilters();
        direct.invoke(null, false);
        drawShadowScene(surface.getCanvas(), false);
        int stableEntries = ((Map<?, ?>) images.get(null)).size();
        System.out.println("GPU_AB shadow stable_keys=" + stableEntries);

        for (int i = 0; i < 120; i++) drawShadowScene(surface.getCanvas(), false);
        System.out.println("GPU_AB shadow keys_after_120_stable_frames="
                + ((Map<?, ?>) images.get(null)).size());

        long[] cached = measure(context, surface, c -> drawShadowScene(c, false));
        direct.invoke(null, true);
        long[] directSamples = measure(context, surface, c -> drawShadowScene(c, false));
        direct.invoke(null, false);
        print("GPU_AB shadow cached", cached);
        print("GPU_AB shadow MaskFilter", directSamples);
    }

    private static void drawShadowScene(Canvas canvas, boolean unused) {
        canvas.clear(0xFF10151D);
        for (int i = 0; i < 10; i++) {
            float x = 28.0F;
            float y = 72.0F + i * 74.0F;
            float w = 424.0F;
            float h = 60.0F;
            SkiaDraw.drawRoundedShadow(canvas, x, y, w, h, 14.0F, 16.0F, 0x55000000);
            SkiaDraw.drawRoundedRect(canvas, x, y, w, h, 14.0F, 0xFF242C37);
        }
    }

    private static void runAboutAab(DirectContext context, Surface surface) throws Exception {
        UiLayout.Rect halo = new UiLayout.Rect(58.0F, 120.0F, 320.0F, 215.0F);
        DotTexture.setDotsCached(false);
        DotTexture.clearCache();
        long[] direct = measure(context, surface, c -> drawAboutScene(c, halo, null));

        DotTexture.setDotsCached(true);
        DotTexture.clearCache();
        drawAboutScene(surface.getCanvas(), halo, null);
        long[] productionImage = measure(context, surface, c -> drawAboutScene(c, halo, null));

        Image baked;
        try (Surface raster = Surface.makeRasterN32Premul((int) halo.w(), (int) halo.h())) {
            Canvas rc = raster.getCanvas();
            rc.clear(0);
            rc.translate(-halo.x(), -halo.y());
            DotTexture.setDotsCached(false);
            DotTexture.drawHalo(rc, halo, 0xFF9FB0C8);
            baked = raster.makeImageSnapshot();
        }
        int maxDelta = haloPixelDelta(halo, baked);
        long[] bakedSamples = measure(context, surface, c -> drawAboutScene(c, halo, baked));

        System.out.println("GPU_AB about dot-Image max_channel_delta=" + maxDelta);
        print("GPU_AB about direct-dots", direct);
        print("GPU_AB about production-static-image", productionImage);
        print("GPU_AB about standalone-static-image", bakedSamples);
        baked.close();
        DotTexture.setDotsCached(false);
        DotTexture.clearCache();
    }

    private static int haloPixelDelta(UiLayout.Rect halo, Image baked) {
        int width = (int) halo.w();
        int height = (int) halo.h();
        byte[] direct;
        byte[] composed;
        try (Surface a = Surface.makeRasterN32Premul(width, height);
             Surface b = Surface.makeRasterN32Premul(width, height)) {
            DotTexture.setDotsCached(false);
            a.getCanvas().clear(0xFF242C37);
            DotTexture.drawHalo(a.getCanvas(), new UiLayout.Rect(0, 0, width, height), 0xFF9FB0C8);
            direct = pixels(a, width, height);
            b.getCanvas().clear(0xFF242C37);
            b.getCanvas().drawImageRect(baked, Rect.makeWH(width, height),
                    Rect.makeWH(width, height), SamplingMode.LINEAR, null, false);
            composed = pixels(b, width, height);
        }
        int max = 0;
        for (int i = 0; i < Math.min(direct.length, composed.length); i++) {
            max = Math.max(max, Math.abs((direct[i] & 0xFF) - (composed[i] & 0xFF)));
        }
        return max;
    }

    private static byte[] pixels(Surface surface, int width, int height) {
        var bitmap = new io.github.humbleui.skija.Bitmap();
        try {
            bitmap.allocN32Pixels(width, height);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        } finally {
            bitmap.close();
        }
    }

    private static void drawAboutScene(Canvas canvas, UiLayout.Rect halo, Image bakedHalo) {
        canvas.clear(0xFF10151D);
        if (bakedHalo == null) {
            DotTexture.drawHalo(canvas, halo, 0xFF9FB0C8);
        } else {
            canvas.drawImageRect(bakedHalo, Rect.makeWH(bakedHalo.getWidth(), bakedHalo.getHeight()),
                    Rect.makeXYWH(halo.x(), halo.y(), halo.w(), halo.h()), SamplingMode.LINEAR, null, false);
        }
        SkiaDraw.drawRoundedShadow(canvas, 194.0F, 180.0F, 92.0F, 92.0F, 22.0F,
                16.0F, 0x55000000);
        SkiaDraw.drawRoundedRect(canvas, 194.0F, 180.0F, 92.0F, 92.0F, 22.0F, 0xFFC83E45);
        SkiaDraw.drawRoundedRect(canvas, 140.0F, 292.0F, 200.0F, 36.0F, 12.0F, 0xFF242C37);
        for (int i = 0; i < 5; i++) {
            SkiaDraw.drawRoundedRect(canvas, 28.0F, 370.0F + i * 76.0F, 424.0F, 60.0F,
                    14.0F, 0xFF242C37);
        }
    }

    private static long[] measure(DirectContext context, Surface surface,
                                  java.util.function.Consumer<Canvas> painter) {
        Canvas canvas = surface.getCanvas();
        for (int i = 0; i < WARMUP; i++) {
            painter.accept(canvas);
            context.flush(surface);
            GL11C.glFinish();
        }
        long[] samples = new long[MEASURED];
        for (int i = 0; i < MEASURED; i++) {
            long start = System.nanoTime();
            painter.accept(canvas);
            context.flush(surface);
            GL11C.glFinish();
            samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        return samples;
    }

    private static void print(String label, long[] sorted) {
        System.out.printf(Locale.ROOT, "%s p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms%n",
                label, p(sorted, .50), p(sorted, .95), p(sorted, .99), p(sorted, 1.0));
    }

    private static double p(long[] sorted, double fraction) {
        int index = Math.max(0, Math.min(sorted.length - 1,
                (int) Math.ceil(sorted.length * fraction) - 1));
        return sorted[index] / 1_000_000.0;
    }
}
