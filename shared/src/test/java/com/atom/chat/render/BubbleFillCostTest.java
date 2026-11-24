package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig.BubbleStyle;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Compares the same solid contour through generic paint inspection and the existing solid-fill API. */
class BubbleFillCostTest {
    @Test void compareIdenticalSolidFillsAndRecordTheirCpuCost() throws Exception {
        try (var generic = Surface.makeRasterN32Premul(190, 90);
             var direct = Surface.makeRasterN32Premul(190, 90);
             var first = new Bitmap(); var second = new Bitmap();
             var paint = new Paint().setAntiAlias(CornerGeometry.ANTIALIAS)) {
            first.allocN32Pixels(190, 90);
            second.allocN32Pixels(190, 90);
            for (var style : BubbleStyle.values()) for (var group : ChatBubble.Group.values())
            for (boolean own : new boolean[]{false, true}) for (int color : new int[]{0xFF1989FA, 0x801989FA}) {
                generic.getCanvas().clear(0x40374B51);
                direct.getCanvas().clear(0x40374B51);
                var shape = ChatBubble.outline(12.25F, 11.5F, 160, 50, style, own, 1, group);
                CornerGeometry.drawRRect(generic.getCanvas(), shape, paint.setColor(color));
                CornerGeometry.fill(direct.getCanvas(), shape, color);
                assertTrue(generic.readPixels(first, 0, 0));
                assertTrue(direct.readPixels(second, 0, 0));
                assertArrayEquals(first.readPixels(), second.readPixels(), style + "/" + group + "/" + own);
            }
            var shape = ChatBubble.outline(12.25F, 11.5F, 160, 50, BubbleStyle.CORNER, false, 1, ChatBubble.Group.MIDDLE);
            var canvas = generic.getCanvas();
            paint.setColor(0xFF1989FA);
            for (int i = 0; i < 1000; i++) {
                CornerGeometry.drawRRect(canvas, shape, paint);
                CornerGeometry.fill(canvas, shape, 0xFF1989FA);
            }
            var bean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            if (bean.isThreadAllocatedMemorySupported()) bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().getId();
            StringBuilder csv = new StringBuilder("round,mode,ns_per_fill,java_bytes_per_fill\n");
            int count = 2000;
            for (int round = 0; round < 8; round++) {
                boolean fast = round % 4 == 1 || round % 4 == 2;
                long bytes = bean.isThreadAllocatedMemoryEnabled() ? bean.getThreadAllocatedBytes(thread) : -1;
                long begin = System.nanoTime();
                for (int i = 0; i < count; i++) {
                    if (fast) CornerGeometry.fill(canvas, shape, 0xFF1989FA);
                    else CornerGeometry.drawRRect(canvas, shape, paint);
                }
                long elapsed = System.nanoTime() - begin;
                long allocated = bytes < 0 ? -1 : bean.getThreadAllocatedBytes(thread) - bytes;
                csv.append(round).append(',').append(fast ? "solid" : "generic").append(',')
                        .append(elapsed / (double)count).append(',').append(allocated < 0 ? -1 : allocated / (double)count).append('\n');
            }
            Path path = Path.of(System.getProperty("atomchat.repoRoot", "../.."), "build/v096/performance",
                    System.getProperty("atomchat.golden.target", "local"), "solid-fill.csv");
            Files.createDirectories(path.getParent());
            Files.writeString(path, csv);
        }
    }
}
