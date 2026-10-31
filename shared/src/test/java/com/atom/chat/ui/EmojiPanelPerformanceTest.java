package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.platform.Platform;
import com.atom.chat.font.FontManager;
import com.atom.chat.reaction.ReactionEmoji;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit diagnostic, CPU raster only; durations are evidence, never FPS or CI assertions. */
class EmojiPanelPerformanceTest {
    @Test void measureGlEmojiRendering(@TempDir Path root) {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_EMOJI_BENCH")));
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        org.lwjgl.glfw.GLFW.glfwInit();
        org.lwjgl.glfw.GLFW.glfwDefaultWindowHints();
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_VISIBLE, 0);
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_STENCIL_BITS, 8);
        long window = org.lwjgl.glfw.GLFW.glfwCreateWindow(2560, 1600, "AtomChat emoji diagnostic", 0, 0);
        org.junit.jupiter.api.Assertions.assertNotEquals(0, window);
        try {
            org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(window);
            org.lwjgl.opengl.GL.createCapabilities();
            System.out.println("Emoji GL renderer: " + org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER));
            try (var context = io.github.humbleui.skija.DirectContext.makeGL();
                 var target = io.github.humbleui.skija.BackendRenderTarget.makeGL(2560, 1600, 0, 8, 0, 32856);
                 var surface = Surface.wrapBackendRenderTarget(context, target,
                         io.github.humbleui.skija.SurfaceOrigin.BOTTOM_LEFT,
                         io.github.humbleui.skija.ColorType.RGBA_8888,
                         io.github.humbleui.skija.ColorSpace.getDisplayP3())) {
                var panel = new EmojiPanel(new EmojiPanel.Host() {
                    public void insert(String text) { }
                    public void sendSticker(Path file) { }
                    public void pickEmoteFile() { }
                });
                var layout = UiLayout.of(15, 15, 420, 780);
                panel.toggle(); panel.update(200);
                var canvas = surface.getCanvas();
                var font = FontManager.font(UiTokens.FONT_EMOJI);
                for (int mode = 0; mode < 4; mode++) {
                    long[] times = new long[80];
                    for (int i = -20; i < times.length; i++) {
                        context.resetAll(); canvas.clear(0xFF182432);
                        long start = System.nanoTime();
                        if (mode == 0) panel.render(canvas, layout, -1, -1, 16);
                        else if (mode == 1 || mode == 2) for (int j = 0; j < 64; j++) {
                            SkiaFontRenderer.drawTextCentered(canvas, font, mode == 1 ? ReactionEmoji.ALL.get(j) : "A",
                                    30 + j % 8 * 48, 350 + j / 8 * 40, 0xFFFFFFFF);
                        }
                        context.flush(surface); org.lwjgl.opengl.GL11C.glFinish();
                        if (i >= 0) times[i] = System.nanoTime() - start;
                    }
                    Arrays.sort(times);
                    System.out.printf(java.util.Locale.ROOT,
                            "Emoji GL completed %s: p50=%.3fms p95=%.3fms%n",
                            new String[]{"whole-popup", "64-emoji", "64-latin", "empty"}[mode],
                            times[40] / 1e6, times[76] / 1e6);
                }
                try (var raster = Surface.makeRasterN32Premul(920, 1640)) {
                    var rasterCanvas = raster.getCanvas(); rasterCanvas.clear(0); rasterCanvas.scale(2, 2);
                    for (int j = 0; j < 64; j++) SkiaFontRenderer.drawTextCentered(rasterCanvas, font,
                            ReactionEmoji.ALL.get(j), 30 + j % 8 * 48, 350 + j / 8 * 40, 0xFFFFFFFF);
                    try (var image = raster.makeImageSnapshot()) {
                        for (int mode = 0; mode < 3; mode++) {
                            long[] paint = new long[80], flush = new long[80];
                            for (int i = 0; i < 80; i++) {
                                context.resetAll(); canvas.clear(0xFF182432);
                                canvas.save(); canvas.scale(1600F / 1080, 1600F / 1080);
                                float zoom = .92F + .08F * ((i * 37 % 101) / 100F);
                                canvas.scale(zoom, zoom);
                                long t = System.nanoTime();
                                if (mode == 2) canvas.drawImageRect(image, io.github.humbleui.types.Rect.makeXYWH(0, 0, 460, 820));
                                else for (int j = 0; j < 64; j++) SkiaFontRenderer.drawTextCentered(canvas, font,
                                        mode == 0 ? ReactionEmoji.ALL.get(j) : "A", 30 + j % 8 * 48,
                                        350 + j / 8 * 40, 0xFFFFFFFF);
                                canvas.restore(); paint[i] = System.nanoTime() - t;
                                t = System.nanoTime(); context.flush(surface); org.lwjgl.opengl.GL11C.glFinish();
                                flush[i] = System.nanoTime() - t;
                            }
                            Arrays.sort(paint); Arrays.sort(flush);
                            System.out.printf(java.util.Locale.ROOT, "Emoji GL varying scale %s paint p50=%.3f p95=%.3f max=%.3f ms; flush p50=%.3f p95=%.3f max=%.3f ms%n",
                                    new String[]{"64-emoji", "64-latin", "raster-emoji"}[mode],
                                    paint[40]/1e6, paint[76]/1e6, paint[79]/1e6, flush[40]/1e6, flush[76]/1e6, flush[79]/1e6);
                        }
                    }
                }
            }
        } finally {
            org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(0);
            org.lwjgl.glfw.GLFW.glfwDestroyWindow(window);
            org.lwjgl.glfw.GLFW.glfwTerminate();
        }
    }
    @Test void measureSteadyEmojiRendering(@TempDir Path root) {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_EMOJI_BENCH")));
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        var panel = new EmojiPanel(new EmojiPanel.Host() {
            public void insert(String text) { }
            public void sendSticker(Path file) { }
            public void pickEmoteFile() { }
        });
        var layout = UiLayout.of(15, 15, 420, 780);
        panel.toggle(); panel.update(200);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            var canvas = surface.getCanvas();
            int saves = canvas.getSaveCount();
            var font = FontManager.font(UiTokens.FONT_EMOJI);
            for (int mode = 0; mode < 3; mode++) {
                long[] times = new long[80];
                for (int i = -20; i < times.length; i++) {
                    canvas.clear(0xFF182432);
                    long start = System.nanoTime();
                    if (mode == 0) panel.render(canvas, layout, -1, -1, 16);
                    else if (mode == 1) for (int j = 0; j < 64; j++) {
                        SkiaFontRenderer.drawTextCentered(canvas, font, ReactionEmoji.ALL.get(j),
                                30 + j % 8 * 48, 350 + j / 8 * 40, 0xFFFFFFFF);
                    }
                    else for (int j = 0; j < 64; j++) {
                        SkiaFontRenderer.drawTextCentered(canvas, font, "A", 30 + j % 8 * 48,
                                350 + j / 8 * 40, 0xFFFFFFFF);
                    }
                    if (i >= 0) times[i] = System.nanoTime() - start;
                    assertEquals(saves, canvas.getSaveCount());
                }
                Arrays.sort(times);
                System.out.printf(java.util.Locale.ROOT,
                        "Emoji CPU raster %s: p50=%.3fms p95=%.3fms%n",
                        new String[]{"whole-popup", "64-emoji", "64-latin"}[mode],
                        times[40] / 1e6, times[76] / 1e6);
            }
        }
    }
}
