package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig.BubbleStyle;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatBubbleTest {
    private int pixel(Surface surface, int x, int y) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(surface.getWidth(), surface.getHeight());
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.getColor(x, y);
        }
    }

    @Test
    void roundRemovesMoreCornerAreaWithoutChangingTheBubbleBounds() {
        try (Surface s = Surface.makeRasterN32Premul(160, 90)) {
            ChatBubble.draw(s.getCanvas(), 10, 10, 140, 60, BubbleStyle.CLASSIC, false, -1);
            assertEquals(-1, pixel(s, 15, 15));
            s.getCanvas().clear(0);
            ChatBubble.draw(s.getCanvas(), 10, 10, 140, 60, BubbleStyle.ROUND, false, -1);
            assertEquals(0, pixel(s, 15, 15));
            assertEquals(-1, pixel(s, 80, 10));
            assertEquals(-1, pixel(s, 10, 40));
            assertEquals(0, pixel(s, 9, 40));
            assertEquals(0, pixel(s, 150, 40));
        }
    }

    @Test
    void cornerMirrorsTheSharpUpperCornerTowardTheAvatar() {
        try (Surface s = Surface.makeRasterN32Premul(160, 90)) {
            ChatBubble.draw(s.getCanvas(), 10, 10, 140, 60, BubbleStyle.CORNER, false, -1);
            assertEquals(-1, pixel(s, 10, 10));
            assertEquals(0, pixel(s, 149, 10));
            assertEquals(0, pixel(s, 10, 69));
            s.getCanvas().clear(0);
            ChatBubble.draw(s.getCanvas(), 10, 10, 140, 60, BubbleStyle.CORNER, true, -1);
            assertEquals(0, pixel(s, 10, 10));
            assertEquals(-1, pixel(s, 149, 10));
            assertEquals(0, pixel(s, 149, 69));
        }
    }

    @Test
    void singleTipMatchesCircularAreaAtFractionalScale() {
        int width = 190, height = 100, samples = 16;
        try (Surface actual = Surface.makeRasterN32Premul(width, height);
             Surface reference = Surface.makeRasterN32Premul(width * samples, height * samples);
             Bitmap a = new Bitmap(); Bitmap b = new Bitmap();
             var paint = new io.github.humbleui.skija.Paint().setAntiAlias(true).setColor(-1)) {
            a.allocN32Pixels(width, height);
            b.allocN32Pixels(width * samples, height * samples);
            actual.getCanvas().scale(1.25F, 1.25F);
            reference.getCanvas().scale(1.25F * samples, 1.25F * samples);
            for (boolean own : new boolean[]{false, true}) {
                actual.getCanvas().clear(0);
                reference.getCanvas().clear(0);
                ChatBubble.draw(actual.getCanvas(), 10.25F, 10.5F, 120, 50, BubbleStyle.CORNER, own, -1);
                reference.getCanvas().drawRRect(ChatBubble.outline(10.25F, 10.5F, 120, 50, BubbleStyle.CORNER, own, 1F), paint);
                assertTrue(actual.readPixels(a, 0, 0));
                assertTrue(reference.readPixels(b, 0, 0));
                byte[] low = a.readPixels(), high = b.readPixels();
                float error = 0F;
                int edgePixels = 0;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int sum = 0;
                    for (int dy = 0; dy < samples; dy++) for (int dx = 0; dx < samples; dx++) {
                        int index = ((y * samples + dy) * width * samples + x * samples + dx) * 4 + 3;
                        sum += high[index] & 255;
                    }
                    float expected = sum / (float) (samples * samples);
                    int alpha = low[(y * width + x) * 4 + 3] & 255;
                    if (expected != alpha || (alpha > 0 && alpha < 255)) {
                        edgePixels++;
                        error += Math.abs(expected - alpha);
                    }
                }
                assertTrue(edgePixels > 200);
                assertTrue(error / edgePixels < 3F, "edge alpha MAE " + error / edgePixels);
            }
        }
    }

    @Test
    void imageUsesTheSameShapeAndRestoresClipAndSaveStack() {
        try (Surface source = Surface.makeRasterN32Premul(140, 60);
             Surface target = Surface.makeRasterN32Premul(160, 90)) {
            source.getCanvas().clear(0xFFFF0000);
            try (Image image = source.makeImageSnapshot()) {
                int saves = target.getCanvas().getSaveCount();
                ChatBubble.drawImage(target.getCanvas(), image, 10, 10, 140, 60, BubbleStyle.CORNER, true);
                assertEquals(0xFFFF0000, pixel(target, 149, 10));
                assertEquals(0, pixel(target, 10, 10));
                assertEquals(saves, target.getCanvas().getSaveCount());
                target.getCanvas().clear(0xFF00FF00);
                assertEquals(0xFF00FF00, pixel(target, 0, 0), "image clip must not leak");
            }
        }
    }

    @Test
    void opaqueImageAndSolidBubbleHaveTheSameFractionalEdgeCoverage() {
        try (Surface source = Surface.makeRasterN32Premul(120, 50);
             Surface text = Surface.makeRasterN32Premul(190, 100);
             Surface photo = Surface.makeRasterN32Premul(190, 100);
             Bitmap textPixels = new Bitmap(); Bitmap photoPixels = new Bitmap()) {
            source.getCanvas().clear(0xFFFFFFFF);
            textPixels.allocN32Pixels(190, 100);
            photoPixels.allocN32Pixels(190, 100);
            try (Image image = source.makeImageSnapshot()) {
                for (float phase : new float[]{0F, .25F, .5F, .75F}) {
                    var textCanvas = text.getCanvas();
                    var photoCanvas = photo.getCanvas();
                    textCanvas.clear(0);
                    photoCanvas.clear(0);
                    textCanvas.save();
                    photoCanvas.save();
                    textCanvas.scale(1.25F, 1.25F);
                    photoCanvas.scale(1.25F, 1.25F);
                    ChatBubble.draw(textCanvas, 10F + phase, 10F + phase, 120F, 50F, BubbleStyle.CORNER, false, -1);
                    ChatBubble.drawImage(photoCanvas, image, 10F + phase, 10F + phase, 120F, 50F, BubbleStyle.CORNER, false);
                    textCanvas.restore();
                    photoCanvas.restore();
                    assertTrue(text.readPixels(textPixels, 0, 0));
                    assertTrue(photo.readPixels(photoPixels, 0, 0));
                    int partial = 0;
                    int worstError = 0;
                    for (int y = 0; y < 100; y++) for (int x = 0; x < 190; x++) {
                        int a = textPixels.getColor(x, y) >>> 24;
                        int b = photoPixels.getColor(x, y) >>> 24;
                        if (a > 0 && a < 255) partial++;
                        worstError = Math.max(worstError, Math.abs(a - b));
                    }
                    assertTrue(partial > 0, "the solid bubble edge must be antialiased");
                    assertTrue(worstError <= 1, "phase " + phase + ", maximum alpha difference " + worstError);
                }
            }
        }
    }

    @Test
    void veryNarrowBubbleStillHasAFullCentreAndNoPixelsOutside() {
        try (Surface s = Surface.makeRasterN32Premul(60, 100)) {
            ChatBubble.draw(s.getCanvas(), 10, 10, 20, 80, BubbleStyle.ROUND, false, -1);
            assertEquals(-1, pixel(s, 20, 11));
            assertEquals(-1, pixel(s, 10, 50));
            assertEquals(0, pixel(s, 9, 50));
        }
    }
}
