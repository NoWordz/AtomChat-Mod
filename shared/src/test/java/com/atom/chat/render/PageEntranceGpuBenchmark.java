package com.atom.chat.render;

import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiCards;
import com.atom.chat.font.FontManager;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Explicit hidden-GL diagnostic for the settings-card entrance layer. */
class PageEntranceGpuBenchmark {
    private static final String SKIP_HINT = "set ATOMCHAT_BENCH=1 to run the hidden-GL entrance benchmark";
    private static final int WIDTH = 480;
    private static final int HEIGHT = 860;
    private static final int CARDS = 10;
    private static final int WARMUP = 24;
    private static final int MEASURED = 96;

    @Test
    void layeredEntranceAgainstSettledDraw() {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_BENCH")), SKIP_HINT);
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"),
                "the diagnostic needs a visible Windows OpenGL driver");
        assertTrue(GLFW.glfwInit(), "GLFW init");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "AtomChat entrance diagnostic", 0, 0);
        assertNotEquals(0L, window);
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("Entrance GL renderer: " + GL11C.glGetString(GL11C.GL_RENDERER));
            try (DirectContext context = DirectContext.makeGL();
                 BackendRenderTarget target = BackendRenderTarget.makeGL(
                         WIDTH, HEIGHT, 0, 8, 0, GL11C.GL_RGBA8);
                 Surface surface = Surface.wrapBackendRenderTarget(
                         context, target, SurfaceOrigin.BOTTOM_LEFT,
                         ColorType.RGBA_8888, ColorSpace.getSRGB())) {
                Canvas canvas = surface.getCanvas();
                int saves = canvas.getSaveCount();
                boolean[][] modes = {{false, false}, {true, false}, {false, true}, {true, true},
                        {false, false}, {true, false}, {false, true}, {true, true}};
                for (boolean[] mode : modes) {
                    boolean layers = mode[0];
                    boolean legacyText = mode[1];
                    long[] milliseconds = draw(context, surface, layers, legacyText);
                    System.out.printf(Locale.ROOT,
                            "Entrance GL %s/%s: p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms%n",
                            layers ? "10-row saveLayer" : "settled direct",
                            legacyText ? "legacy drawString" : "cached TextBlob",
                            percentile(milliseconds, 0.50),
                            percentile(milliseconds, 0.95),
                            percentile(milliseconds, 0.99),
                            milliseconds[milliseconds.length - 1] / 1_000_000.0);
                    assertEquals(saves, canvas.getSaveCount(), "benchmark leaked canvas saves");
                }
            }
        } finally {
            GLFW.glfwMakeContextCurrent(0);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static long[] draw(DirectContext context, Surface surface, boolean layers, boolean legacyText) {
        Canvas canvas = surface.getCanvas();
        long[] samples = new long[MEASURED];
        for (int i = 0; i < WARMUP + MEASURED; i++) {
            context.resetAll();
            canvas.clear(0xFF10151D);
            long started = System.nanoTime();
            for (int card = 0; card < CARDS; card++) {
                float x = 28.0F;
                float y = 72.0F + card * 74.0F;
                float width = 424.0F;
                float height = 60.0F;
                if (layers) {
                    try (Paint alpha = new Paint().setAlphaf(0.72F)) {
                        canvas.saveLayer(Rect.makeXYWH(x - 16.0F, y - 16.0F,
                                width + 32.0F, height + 32.0F), alpha);
                        drawCard(canvas, card, x, y, width, height, legacyText);
                        canvas.restore();
                    }
                } else {
                    drawCard(canvas, card, x, y, width, height, legacyText);
                }
            }
            context.flush(surface);
            GL11C.glFinish();
            if (i >= WARMUP) samples[i - WARMUP] = System.nanoTime() - started;
        }
        Arrays.sort(samples);
        return samples;
    }

    private static void drawCard(Canvas canvas, int card, float x, float y, float width, float height,
                                 boolean legacyText) {
        UiCards.drawCard(canvas, x, y, width, height, 14.0F, 0.0F);
        SkiaDraw.drawRoundedRect(canvas, x + 16.0F, y + 16.0F, 36.0F, 28.0F, 8.0F,
                0xFF303846 + card * 0x00010101);
        drawText(canvas, FontManager.font(15.0F), "Settings row " + card,
                x + 64.0F, y + 25.0F, 0xFFE8EDF5, legacyText);
        drawText(canvas, FontManager.font(11.0F), "description and current value",
                x + 64.0F, y + 43.0F, 0xFF98A4B3, legacyText);
    }

    private static void drawText(Canvas canvas, io.github.humbleui.skija.Font font, String text,
                                 float x, float y, int color, boolean legacy) {
        if (!legacy) {
            SkiaFontRenderer.drawText(canvas, font, text, x, y, color);
            return;
        }
        font.measureTextWidth(text);
        try (Paint paint = new Paint().setColor(color)) {
            canvas.drawString(text, x, y, font, paint);
        }
        font.measureTextWidth(text);
    }

    private static double percentile(long[] sorted, double fraction) {
        int index = Math.max(0, Math.min(sorted.length - 1,
                (int) Math.ceil(sorted.length * fraction) - 1));
        return sorted[index] / 1_000_000.0;
    }
}
