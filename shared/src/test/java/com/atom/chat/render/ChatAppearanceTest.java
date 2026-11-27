package com.atom.chat.render;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.config.AtomChatConfig;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatAppearanceTest {
    @Test
    void bothSurfacesResolveTheSameSelectionAndKeepIndependentFallbacks() {
        String id = "chat_appearance_test:panel_only";
        var config = new AtomChatConfig();
        config.bubbleAppearanceId = id;
        config.avatarFrameAppearanceId = id;
        AppearanceUi.Bubble bubble = frame -> Path.makeRect(Rect.makeXYWH(0, 0, 1, 1));
        AppearanceUi.AvatarFrame ring = (canvas, frame) -> {};
        var missing = ChatAppearance.current(config);
        assertNull(missing.panelBubble());
        assertNull(missing.hudBubble());
        try {
            AppearanceUi.registerBubble(id, "test.bubble", bubble, null);
            AppearanceUi.registerAvatarFrame(id, "test.avatar", ring, ring);
            var installed = ChatAppearance.current(config);
            assertSame(installed, ChatAppearance.current(config));
            assertSame(bubble, installed.panelBubble());
            assertNull(installed.hudBubble());
            assertSame(ring, installed.panelAvatarFrame());
            assertSame(ring, installed.hudAvatarFrame());
            AppearanceUi.unregister(AppearanceUi.Part.BUBBLE, id);
            assertNull(ChatAppearance.current(config).panelBubble());
            assertEquals(id, config.bubbleAppearanceId);
        } finally {
            AppearanceUi.unregister(AppearanceUi.Part.BUBBLE, id);
            AppearanceUi.unregister(AppearanceUi.Part.AVATAR_FRAME, id);
        }
    }

    @Test
    void customBubbleUsesOneContourForFillImageAndPointQueries() {
        AppearanceUi.Bubble bubble = context -> {
            assertEquals(AppearanceUi.BubbleGroup.LAST, context.group());
            assertTrue(context.own());
            return Path.makeRect(Rect.makeXYWH(12, 12, 10, 10));
        };
        try (var surface = Surface.makeRasterN32Premul(38, 38);
             var source = Surface.makeRasterN32Premul(24, 24);
             var custom = ChatBubble.custom(bubble, 8, 8, 24, 24, true,
                     0xFFCC3300, 0xFFFFFFFF, ChatBubble.Group.LAST)) {
            assertNotNull(custom);
            surface.getCanvas().clear(0);
            assertTrue(custom.draw(surface.getCanvas()));
            assertEquals(0xFFCC3300, pixel(surface, 16, 16));
            assertEquals(0, pixel(surface, 10, 10));
            source.getCanvas().clear(0xFF0066CC);
            try (var art = source.makeImageSnapshot()) {
                custom.drawImage(surface.getCanvas(), art);
            }
            assertTrue(custom.contains(16, 16));
            assertFalse(custom.contains(10, 10));
            assertEquals(0xFF0066CC, pixel(surface, 16, 16));
            assertEquals(0, pixel(surface, 10, 10));
        }
    }

    @Test
    void invalidContoursAndThrowingPaintsDoNotChangeTheHostSurface() {
        AppearanceUi.Bubble outside = context -> Path.makeRect(Rect.makeXYWH(3, 3, 24, 24));
        assertNull(ChatBubble.custom(outside, 8, 8, 16, 16, false, -1, -1, ChatBubble.Group.SINGLE));
        AppearanceUi.Bubble paint = new AppearanceUi.Bubble() {
            public Path outline(AppearanceUi.BubbleContext ctx) {
                return Path.makeRect(Rect.makeXYWH(8, 8, 16, 16));
            }
            public void draw(io.github.humbleui.skija.Canvas canvas, AppearanceUi.BubbleContext ctx, Path path) {
                canvas.clear(0xFF0077FF);
                canvas.save();
                canvas.translate(30, 10);
                throw new IllegalStateException("fixture");
            }
        };
        try (var surface = Surface.makeRasterN32Premul(30, 30);
             var custom = ChatBubble.custom(paint, 8, 8, 16, 16, false, -1, -1, ChatBubble.Group.SINGLE)) {
            surface.getCanvas().clear(0);
            int count = surface.getCanvas().getSaveCount();
            assertFalse(custom.draw(surface.getCanvas()));
            assertEquals(count, surface.getCanvas().getSaveCount());
            assertEquals(0, pixel(surface, 14, 14));
            assertEquals(0, pixel(surface, 3, 3));
        }
    }

    @Test
    void avatarAddonCannotPaintOutsideTheFrameItWasGiven() {
        try (var surface = Surface.makeRasterN32Premul(50, 50)) {
            surface.getCanvas().clear(0);
            AvatarRing.draw(surface.getCanvas(), 25, 25, 8, -1, (canvas, context) -> {
                assertEquals(16F, context.avatarBounds().width());
                assertTrue(context.frameBounds().width() >= context.avatarBounds().width());
                canvas.clear(0xFF008844);
            });
            assertEquals(0xFF008844, pixel(surface, 25, 25));
            assertEquals(0, pixel(surface, 3, 3));
        }
    }

    private static int pixel(Surface surface, int x, int y) {
        try (var bitmap = new Bitmap()) {
            assertTrue(bitmap.allocN32Pixels(surface.getWidth(), surface.getHeight()));
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.getColor(x, y);
        }
    }
}
