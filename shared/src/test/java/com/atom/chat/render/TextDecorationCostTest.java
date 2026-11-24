package com.atom.chat.render;

import com.atom.chat.font.FontManager;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TextDecorationCostTest {
    @Test void scratchPaintPreservesTheUnderlinePixelsAndReducesAllocation() throws Exception {
        Font font = FontManager.font(24);
        try (var old = Surface.makeRasterN32Premul(220, 90);
             var current = Surface.makeRasterN32Premul(220, 90);
             var a = new Bitmap(); var b = new Bitmap()) {
            a.allocN32Pixels(220, 90);
            b.allocN32Pixels(220, 90);
            for (float scale : new float[]{1F, 1.25F, 2F}) for (int color : new int[]{0xFFFFFFFF, 0x8055FF55}) {
                old.getCanvas().clear(0x40374B51);
                current.getCanvas().clear(0x40374B51);
                int sa = old.getCanvas().save(), sb = current.getCanvas().save();
                old.getCanvas().scale(scale, scale);
                current.getCanvas().scale(scale, scale);
                legacy(old.getCanvas(), font, 8.25F, 25.5F, 90F, color);
                RichTextRenderer.drawUnderline(current.getCanvas(), font, 8.25F, 25.5F, 90F, color);
                old.getCanvas().restoreToCount(sa);
                current.getCanvas().restoreToCount(sb);
                assertTrue(old.readPixels(a, 0, 0));
                assertTrue(current.readPixels(b, 0, 0));
                assertArrayEquals(a.readPixels(), b.readPixels());
            }
            Canvas canvas = current.getCanvas();
            for (int i = 0; i < 3000; i++) {
                legacy(canvas, font, 8, 26, 90, -1);
                RichTextRenderer.drawUnderline(canvas, font, 8, 26, 90, -1);
            }
            var bean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            assertTrue(bean.isThreadAllocatedMemorySupported());
            bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().getId();
            StringBuilder csv = new StringBuilder("round,mode,ns_per_line,java_bytes_per_line\n");
            long[] bytesByMode = new long[2];
            int count = 6000;
            for (int round = 0; round < 8; round++) {
                boolean scratch = round % 4 == 1 || round % 4 == 2;
                long bytes = bean.getThreadAllocatedBytes(thread), begin = System.nanoTime();
                for (int i = 0; i < count; i++) {
                    if (scratch) RichTextRenderer.drawUnderline(canvas, font, 8, 26, 90, -1);
                    else legacy(canvas, font, 8, 26, 90, -1);
                }
                long elapsed = System.nanoTime() - begin;
                long allocated = bean.getThreadAllocatedBytes(thread) - bytes;
                bytesByMode[scratch ? 1 : 0] += allocated;
                csv.append(round).append(',').append(scratch ? "scratch" : "legacy").append(',')
                        .append(elapsed / (double)count).append(',').append(allocated / (double)count).append('\n');
            }
            assertTrue(bytesByMode[1] < bytesByMode[0], "reusing the native paint must reduce measured JVM allocations");
            Path path = Path.of(System.getProperty("atomchat.repoRoot", "../.."), "build/v096/performance",
                    System.getProperty("atomchat.golden.target", "local"), "text-decoration.csv");
            Files.createDirectories(path.getParent());
            Files.writeString(path, csv);
        }
    }

    private static void legacy(Canvas canvas, Font font, float x, float baseline, float width, int color) {
        var metrics = font.getMetrics();
        Float position = metrics.getUnderlinePosition(), thickness = metrics.getUnderlineThickness();
        float y = baseline + (position == null ? 1.5F : position);
        float stroke = thickness != null && thickness > 0 ? thickness : Math.max(1F, font.getSize() / 14F);
        try (var paint = new Paint().setColor(color).setAntiAlias(true).setStroke(true).setStrokeWidth(stroke)) {
            canvas.drawLine(x, y, x + width, y, paint);
        }
    }
}
