package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FilterMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.IRect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BalancedShadowSamplingProbeTest {
    record Patch(Image image, float density) implements AutoCloseable {
        @Override public void close() { image.close(); }
    }

    @Test void compareLargePanelPatchSamplingAgainstReleasedBalanced(@TempDir Path root) throws Exception {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_SHADOW_BENCH")));
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root; }
            public boolean isModLoaded(String id) { return false; }
        });
        var config = AtomChatConfig.get();
        var previous = config.graphicsQuality;
        config.graphicsQuality = GraphicsQuality.BALANCED;
        var rows = new StringBuilder("width,height,scale,phase,radius,variant,peak,mean,luma_peak,cold_bake_ns,bytes\n");
        try (var reference = Surface.makeRasterN32Premul(1280, 1280);
             var candidate = Surface.makeRasterN32Premul(1280, 1280);
             var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(1280, 1280);
            measureColdBakes();
            measureGpuDraws();
            for (int[] size : new int[][]{{220, 245}, {480, 820}, {720, 840}}) {
                for (float scale : new float[]{1F, 1.25F, 2F}) {
                    for (float phase : new float[]{0F, .375F}) {
                        for (float radius : new float[]{0F, 26F, 52F}) {
                            var face = RRect.makeXYWH((128F + phase) / scale, (132F + phase) / scale,
                                    size[0] / scale, size[1] / scale, radius);
                            byte[] expected;
                            try (var patch = bake(face, 2F, false)) {
                                prepare(reference, scale);
                                draw(reference, face, patch);
                                assertTrue(reference.readPixels(bitmap, 0, 0));
                                expected = bitmap.readPixels();
                            }
                            prepare(candidate, scale);
                            SoftShadow.drawPanel(candidate.getCanvas(), face, false);
                            assertTrue(candidate.readPixels(bitmap, 0, 0));
                            byte[] product = bitmap.readPixels();
                            if ((long) size[0] * size[1] < 262_144) {
                                assertArrayEquals(expected, product, "small shadows retain the released patch");
                            }
                            for (float density : new float[]{2F, 1F, 1.5F}) {
                                long start = System.nanoTime();
                                try (var patch = bake(face, density, density == 2F)) {
                                    long elapsed = System.nanoTime() - start;
                                    prepare(candidate, scale);
                                    draw(candidate, face, patch);
                                    assertTrue(candidate.readPixels(bitmap, 0, 0));
                                    byte[] got = bitmap.readPixels();
                                    int peak = 0, luma = 0;
                                    long sum = 0;
                                    for (int i = 0; i < got.length; i += 4) {
                                        int diff = Math.abs((expected[i + 3] & 255) - (got[i + 3] & 255));
                                        peak = Math.max(peak, diff);
                                        sum += diff;
                                        // Black coverage over a light grey surface, in eighth-bit code values.
                                        luma = Math.max(luma, Math.abs(composite(expected[i + 3]) - composite(got[i + 3])));
                                    }
                                    if (density == 1.5F && (long) size[0] * size[1] >= 262_144) {
                                        assertArrayEquals(got, product, "large shadows use the measured 1.5x patch");
                                        assertTrue(peak <= 4, "the existing shadow coverage tolerance is 4; got " + peak);
                                    }
                                    long bytes = (long) patch.image().getWidth() * patch.image().getHeight()
                                            * (density == 2F ? 1 : 4);
                                    rows.append(size[0]).append(',').append(size[1]).append(',').append(scale)
                                            .append(',').append(phase).append(',').append(radius).append(',')
                                            .append(density == 2F ? "A8" : density).append(',').append(peak).append(',')
                                            .append(sum * 4.0 / got.length).append(',').append(luma).append(',')
                                            .append(elapsed).append(',').append(bytes).append('\n');
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            config.graphicsQuality = previous;
            SoftShadow.release();
        }
        Path output = Path.of(System.getProperty("atomchat.repoRoot"), "build/v098-perf/shadow-pilot.csv");
        Files.createDirectories(output.getParent());
        Files.writeString(output, rows);
    }

    private static void measureColdBakes() throws Exception {
        var rows = new StringBuilder("radius,density,block,iteration,cpu_ns,patch_bytes\n");
        for (float radius : new float[]{26F, 52F}) {
            var face = RRect.makeXYWH(0, 0, 480, 820, radius);
            int block = 0;
            for (float density : new float[]{2F, 1.5F, 1.5F, 2F}) {
                for (int i = -12; i < 32; i++) {
                    long start = System.nanoTime();
                    try (var patch = bake(face, density, false)) {
                        long elapsed = System.nanoTime() - start;
                        if (i < 0) continue;
                        long bytes = (long) patch.image().getWidth() * patch.image().getHeight() * 4;
                        rows.append(radius).append(',').append(density).append(',').append(block).append(',')
                                .append(i).append(',').append(elapsed).append(',').append(bytes).append('\n');
                    }
                }
                block++;
            }
        }
        Path output = Path.of(System.getProperty("atomchat.repoRoot"), "build/v098-perf/shadow-bake-abba.csv");
        Files.createDirectories(output.getParent());
        Files.writeString(output, rows);
    }

    private static void measureGpuDraws() throws Exception {
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(64, 64, "AtomChat shadow GPU probe", 0, 0);
        var rows = new StringBuilder("density,block,iteration,first_use,cpu_ns,gpu_ns\n");
        try {
            assertTrue(window != 0, "hidden GL window");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            var face = RRect.makeXYWH(100, 100, 480, 820, 26F);
            try (var context = DirectContext.makeGL();
                 var target = Surface.makeRenderTarget(context, true, ImageInfo.makeN32Premul(900, 1150))) {
                int block = 0;
                int query = GL15C.glGenQueries();
                try {
                    for (float density : new float[]{2F, 1.5F, 1.5F, 2F}) {
                        try (var patch = bake(face, density, false)) {
                            for (int frame = -30; frame < 100; frame++) {
                                measureDraw(context, target, face, patch, block, frame, false, query, rows);
                            }
                        }
                        for (int frame = -8; frame < 30; frame++) {
                            try (var patch = bake(face, density, false)) {
                                measureDraw(context, target, face, patch, block, frame, true, query, rows);
                            }
                        }
                        block++;
                    }
                } finally {
                    GL15C.glDeleteQueries(query);
                }
            }
        } finally {
            GLFW.glfwMakeContextCurrent(0);
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
        Path output = Path.of(System.getProperty("atomchat.repoRoot"), "build/v098-perf/shadow-draw-gpu.csv");
        Files.writeString(output, rows);
    }

    private static void measureDraw(DirectContext context, Surface target, RRect face, Patch patch,
                                    int block, int frame, boolean firstUse, int query, StringBuilder rows) {
        prepare(target, 1F);
        long started = System.nanoTime();
        GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
        draw(target, face, patch);
        context.flushAndSubmit(target, false);
        GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        long cpu = System.nanoTime() - started;
        long gpu = GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT);
        if (frame >= 0) {
            rows.append(patch.density()).append(',').append(block).append(',').append(frame).append(',')
                    .append(firstUse).append(',').append(cpu).append(',').append(gpu).append('\n');
        }
    }

    private static void prepare(Surface surface, float scale) {
        var canvas = surface.getCanvas();
        canvas.resetMatrix();
        canvas.clear(0);
        canvas.scale(scale, scale);
    }

    private static int composite(byte alpha) {
        return Math.round((255 - (alpha & 255)) * (246F / 255F));
    }

    private static Patch bake(RRect face, float density, boolean alphaOnly) {
        var profile = ShadowTokens.PANEL;
        float pad = SoftShadow.panelPad();
        float radius = face._radii[0];
        int side = (int) Math.ceil(2F * (radius + profile.wideSigma() * 3F) + 2F);
        int size = (int) ((side + pad * 2F) * density);
        ImageInfo info = alphaOnly ? ImageInfo.makeA8(size, size) : ImageInfo.makeN32Premul(size, size);
        try (Surface surface = Surface.makeRaster(info); Paint paint = new Paint().setAntiAlias(true)) {
            var canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(density, density);
            var shape = RRect.makeXYWH(pad, pad, side, side, radius);
            HudShadow.paintLayer(canvas, shape, paint, profile.ambient(), true);
            HudShadow.paintLayer(canvas, shape, paint, profile.contact(), true);
            return new Patch(surface.makeImageSnapshot(), density);
        }
    }

    private static void draw(Surface surface, RRect face, Patch patch) {
        var canvas = surface.getCanvas();
        int save = canvas.save();
        try {
            canvas.clipRRect(face, ClipMode.DIFFERENCE, true);
            float pad = SoftShadow.panelPad(), density = patch.density();
            canvas.translate(face.getLeft() - pad, face.getTop() - pad);
            canvas.scale(1F / density, 1F / density);
            int seam = patch.image().getWidth() / 2;
            canvas.drawImageNine(patch.image(), IRect.makeXYWH(seam, seam, 1, 1),
                    Rect.makeWH((face.getWidth() + pad * 2F) * density,
                            (face.getHeight() + pad * 2F) * density), FilterMode.LINEAR, null);
        } finally {
            canvas.restoreToCount(save);
        }
    }
}
