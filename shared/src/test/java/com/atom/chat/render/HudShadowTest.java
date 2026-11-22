package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudShadowTest {
    @Test void shadowIsSoftOutsideBothTheChatSilhouetteAndTheAvatar() {
        try (var surface = Surface.makeRasterN32Premul(320, 160); var pixels = new Bitmap()) {
            var canvas = surface.getCanvas();
            pixels.allocN32Pixels(320, 160);
            byte[] first = null;
            for (int i = 0; i < 2; i++) {
                canvas.clear(0);
                int depth = canvas.getSaveCount();
                HudShadow.draw(canvas, ChatBubble.outline(100, 30, 180, 60,
                        AtomChatConfig.BubbleStyle.CORNER, false, 1));
                HudShadow.draw(canvas, RRect.makeXYWH(26, 34, 40, 40, 20));
                assertEquals(depth, canvas.getSaveCount());
                assertTrue(surface.readPixels(pixels, 0, 0));
                assertEquals(0, pixels.getColor(150, 50) >>> 24, "transparent bubble/image interiors stay clear");
                assertEquals(0, pixels.getColor(46, 54) >>> 24, "the avatar shadow stays outside its face");
                assertTrue((pixels.getColor(150, 91) >>> 24) > (pixels.getColor(150, 94) >>> 24));
                assertTrue((pixels.getColor(150, 94) >>> 24) > 0);
                assertTrue((pixels.getColor(46, 75) >>> 24) > 0);
                if (first == null) first = pixels.readPixels();
                else assertArrayEquals(first, pixels.readPixels(), "the same cached silhouette paints identically");
            }
        }
    }

    @Test void panelAndHudKeepTheirOwnProfilesForTheSameCachedOutline() {
        try (var surface = Surface.makeRasterN32Premul(340, 110); var pixels = new Bitmap()) {
            var canvas = surface.getCanvas();
            pixels.allocN32Pixels(340, 110);
            for (int pass = 0; pass < 2; pass++) {
                canvas.clear(0);
                boolean hudFirst = pass == 0;
                for (int column = 0; column < 2; column++) {
                    var shape = RRect.makeXYWH(20 + column * 170, 26, 120, 40, 14);
                    if (hudFirst == (column == 0)) {
                        HudShadow.draw(canvas, shape);
                    } else {
                        HudShadow.drawPanel(canvas, shape);
                    }
                }

                assertTrue(surface.readPixels(pixels, 0, 0));
                int hudX = hudFirst ? 80 : 250;
                int panelX = hudFirst ? 250 : 80;
                int hudEdge = pixels.getColor(hudX, 67) >>> 24;
                int panelEdge = pixels.getColor(panelX, 67) >>> 24;
                assertTrue(hudEdge >= 35, "the old HUD contact edge must stay visible: " + hudEdge);
                assertTrue(panelEdge >= 16 && panelEdge < hudEdge,
                        "the panel uses a softer, still visible contact edge: " + panelEdge);
                assertTrue((pixels.getColor(panelX, 77) >>> 24) <= 1, "the panel tail must fall away");
                assertEquals(0, pixels.getColor(hudX, 48) >>> 24);
                assertEquals(0, pixels.getColor(panelX, 48) >>> 24);
            }
        }
    }
}
