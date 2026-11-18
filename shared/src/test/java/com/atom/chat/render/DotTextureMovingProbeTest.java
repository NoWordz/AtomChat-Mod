package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DotTextureMovingProbeTest {
    @Test void movingEdgeMatchesStableEdgeAcrossTransforms() {
        try (Surface a = Surface.makeRasterN32Premul(480, 360);
             Surface b = Surface.makeRasterN32Premul(480, 360);
             Bitmap pixelsA = new Bitmap(); Bitmap pixelsB = new Bitmap()) {
            pixelsA.allocN32Pixels(480, 360); pixelsB.allocN32Pixels(480, 360);
            for (float scale : new float[]{1F, 1.5F, 2F}) {
                for (boolean above : new boolean[]{false, true}) {
                    var rect = new UiLayout.Rect(20, 70, 180, 60);
                    a.getCanvas().clear(0); b.getCanvas().clear(0);
                    a.getCanvas().save(); b.getCanvas().save();
                    a.getCanvas().scale(scale, scale); b.getCanvas().scale(scale, scale);
                    DotTexture.drawEdge(a.getCanvas(), rect, 20, above, 0xFFD0A070);
                    DotTexture.drawMovingEdge(b.getCanvas(), rect, 20, above, 0xFFD0A070);
                    a.getCanvas().restore(); b.getCanvas().restore();
                    assertTrue(a.readPixels(pixelsA, 0, 0)); assertTrue(b.readPixels(pixelsB, 0, 0));
                    assertArrayEquals(pixelsA.readPixels(), pixelsB.readPixels());
                }
            }
        }
    }

    @Test void measureMovingEdgeAllocation() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue("1".equals(System.getenv("ATOMCHAT_BENCH")));
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        try (Surface surface = Surface.makeRasterN32Premul(480, 360)) {
            var canvas = surface.getCanvas();
            for (int i = 0; i < 30; i++) DotTexture.drawMovingEdge(canvas, new UiLayout.Rect(20, 100, 180, 60), 20, true, 0xFFFFFFFF);
            long thread = Thread.currentThread().getId();
            long before = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                canvas.clear(0);
                DotTexture.drawMovingEdge(canvas, new UiLayout.Rect(20 + i % 3, 100, 180, 60), 20, true, 0xFFFFFFFF);
            }
            System.out.println("moving edge 200 frames: bytes=" + (bean.getThreadAllocatedBytes(thread) - before)
                    + " nanos=" + (System.nanoTime() - start));
            String output = System.getenv("ATOMCHAT_DOT_IMAGE");
            if (output != null) try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) {
                Files.write(Path.of(output), data.getBytes());
            }
        }
    }
}
