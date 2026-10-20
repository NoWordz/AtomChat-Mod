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
            assertEquals(-1, pixel(s, 17, 17));
            s.getCanvas().clear(0);
            ChatBubble.draw(s.getCanvas(), 10, 10, 140, 60, BubbleStyle.ROUND, false, -1);
            assertEquals(0, pixel(s, 17, 17));
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
    void veryNarrowBubbleStillHasAFullCentreAndNoPixelsOutside() {
        try (Surface s = Surface.makeRasterN32Premul(60, 100)) {
            ChatBubble.draw(s.getCanvas(), 10, 10, 20, 80, BubbleStyle.ROUND, false, -1);
            assertEquals(-1, pixel(s, 20, 11));
            assertEquals(-1, pixel(s, 10, 50));
            assertEquals(0, pixel(s, 9, 50));
        }
    }
}
