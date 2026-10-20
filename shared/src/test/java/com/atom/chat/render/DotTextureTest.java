package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DotTextureTest {
    @Test void edgeDecorationReachesTheBarWithoutEnteringItsInterior() {
        try (Surface surface = Surface.makeRasterN32Premul(240, 180); Bitmap pixels = new Bitmap()) {
            var canvas = surface.getCanvas();
            int saves = canvas.getSaveCount();
            DotTexture.drawEdge(canvas, new UiLayout.Rect(20, 90, 200, 65), 25, true, 0xFFFFFFFF);
            assertEquals(saves, canvas.getSaveCount());
            pixels.allocN32Pixels(240, 180); assertTrue(surface.readPixels(pixels, 0, 0));
            assertTrue((pixels.getColor(120, 89) >>> 24) > 0, "soft backing touches the straight edge");
            assertTrue((pixels.getColor(24, 97) >>> 24) > 0, "backing follows the rounded corner");
            assertEquals(0, pixels.getColor(120, 110), "never paints inside the bar");
            assertEquals(0, pixels.getColor(120, 20), "decoration stays local");
            canvas.clear(0);
            DotTexture.drawEdge(canvas, new UiLayout.Rect(20, 20, 200, 65), 25, false, 0xFFFFFFFF);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertTrue((pixels.getColor(120, 85) >>> 24) > 0, "header decoration touches the lower edge");
            assertEquals(0, pixels.getColor(120, 60));
        }
    }

    @Test void titleTrailAndHaloAreBoundedAndTransparentTintIsRespected() {
        try (Surface surface = Surface.makeRasterN32Premul(240, 100); Bitmap pixels = new Bitmap()) {
            var canvas = surface.getCanvas();
            int saves = canvas.getSaveCount();
            var box = new UiLayout.Rect(20, 20, 200, 60);
            DotTexture.drawTrail(canvas, box, 0xFFFF0000);
            assertEquals(saves, canvas.getSaveCount());
            pixels.allocN32Pixels(240, 100); assertTrue(surface.readPixels(pixels, 0, 0));
            long left = 0, right = 0;
            for (int y=0;y<100;y++) for(int x=0;x<240;x++) {
                int a = pixels.getColor(x,y) >>> 24;
                if (x<20 || x>=220 || y<20 || y>=80) assertEquals(0,a);
                if(x<120) left+=a; else right+=a;
            }
            assertTrue(left > right && right > 0, "static trail fades toward the right");
            canvas.clear(0); DotTexture.drawHalo(canvas, box, 0x00FFFFFF);
            assertTrue(surface.readPixels(pixels,0,0));
            for (int y=0;y<100;y++) for(int x=0;x<240;x++) assertEquals(0,pixels.getColor(x,y));
        }
    }
}
