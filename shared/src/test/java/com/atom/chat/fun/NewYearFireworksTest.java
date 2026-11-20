package com.atom.chat.fun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NewYearFireworksTest {
    @Test void recognisesOnlyTheThreeGreetingsInMessageBody() {
        var fireworks = new NewYearFireworks();
        for (String text : new String[]{"祝大家新年快乐！", "祝大家新年快樂！", "Happy New Year!", "HAPPY  NEW YEAR"}) {
            assertTrue(fireworks.onMessage(text, true, 1000), text);
            fireworks.reset();
        }
        for (String text : new String[]{"HNY", "shocked", "黑洞", "happy new years", "newyear.example/新年快乐",
                "/say 新年快乐", "「引用 @Bob: 新年快樂」", "`happy new year`", "> Happy New Year",
                "https://example.com/新年快乐", "happy https://example.com new year"}) {
            assertFalse(fireworks.onMessage(text, true, 1000), text);
        }
    }

    @Test void enablingDoesNotPlayAndDisablingCancelsWithoutReplaying() {
        var fireworks = new NewYearFireworks();
        assertFalse(fireworks.active(true, 1000));
        assertFalse(fireworks.onMessage("新年快樂", false, 1000));
        assertTrue(fireworks.onMessage("新年快樂", true, 1000));
        assertTrue(fireworks.active(true, 1010));
        assertFalse(fireworks.active(false, 1020));
        assertFalse(fireworks.active(true, 1030));
        assertFalse(fireworks.onMessage("Happy New Year", true, 1030));
        assertFalse(fireworks.onMessage("Happy New Year", true, 6000));
        assertTrue(fireworks.onMessage("Happy New Year", true, 10500));
        assertTrue(fireworks.active(true, 16700));
        assertFalse(fireworks.onMessage("新年快乐", true, 16701));
        assertFalse(fireworks.active(true, 17800));
        assertFalse(fireworks.onMessage("新年快乐", true, 17810));
        fireworks.reset();
        assertTrue(fireworks.onMessage("新年快乐", true, 17810));
    }

    @Test void realBurstIsBoundedAndExpires() {
        try (var surface = io.github.humbleui.skija.Surface.makeRasterN32Premul(480, 270);
             var bitmap = new io.github.humbleui.skija.Bitmap()) {
            var canvas = surface.getCanvas();
            canvas.clear(0);
            bitmap.allocN32Pixels(480, 270);
            NewYearFireworks.drawFrame(canvas, 480, 270, 1750);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            byte[] pixels = bitmap.readPixels();
            int lit = 0;
            for (int i = 3; i < pixels.length; i += 4) {
                if (Byte.toUnsignedInt(pixels[i]) > 24) lit++;
            }
            assertTrue(lit > 100, "the frame contains visible sparks");
            canvas.clear(0);
            NewYearFireworks.drawFrame(canvas, 480, 270, 7200);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            assertArrayEquals(new byte[pixels.length], bitmap.readPixels());
        }
    }

    @Test void finaleFillsBothSidesAndCenterOfTheScreen() {
        try (var surface = io.github.humbleui.skija.Surface.makeRasterN32Premul(480, 270);
             var bitmap = new io.github.humbleui.skija.Bitmap()) {
            bitmap.allocN32Pixels(480, 270);
            int[] coverage = new int[3];
            for (long elapsed : new long[]{4900, 5150, 5450, 6200}) {
                surface.getCanvas().clear(0);
                NewYearFireworks.drawFrame(surface.getCanvas(), 480, 270, elapsed);
                assertTrue(surface.readPixels(bitmap, 0, 0));
                byte[] pixels = bitmap.readPixels();
                for (int i = 3; i < pixels.length; i += 4) {
                    if (Byte.toUnsignedInt(pixels[i]) > 24) coverage[((i / 4) % 480) / 160]++;
                }
            }
            for (int lit : coverage) assertTrue(lit >= 2400, java.util.Arrays.toString(coverage));
        }
    }
}
