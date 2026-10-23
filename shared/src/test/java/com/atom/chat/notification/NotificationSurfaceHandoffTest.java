package com.atom.chat.notification;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.text.RichText;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationSurfaceHandoffTest {
    @Test void switchingSurfacesKeepsTheSameNotificationAndExpiryAndBalancesEveryFrame() throws Exception {
        Field cfg = AtomChatConfig.class.getDeclaredField("instance"); cfg.setAccessible(true);
        Object before = cfg.get(null);
        AtomChatConfig config = new AtomChatConfig(); config.animationEnabled = false; cfg.set(null, config);
        var time = new java.util.concurrent.atomic.AtomicLong(1000);
        var constructor = NotificationBanner.class.getDeclaredConstructor(java.util.function.LongSupplier.class);
        constructor.setAccessible(true);
        NotificationBanner banner = constructor.newInstance((java.util.function.LongSupplier) time::get);
        Field queueField = NotificationBanner.class.getDeclaredField("banners"); queueField.setAccessible(true);
        @SuppressWarnings("unchecked") var queue = (List<NotificationBanner.Active>) queueField.get(banner);
        long born = 0;
        var notification = new NotificationBanner.Active(NotificationBanner.Type.MENTION,
                RichText.literal("Alice"), RichText.literal("hello"), born, null);
        queue.add(notification);
        try (Surface surface = Surface.makeRasterN32Premul(1000, 800); Bitmap bitmap = new Bitmap()) {
            var canvas = surface.getCanvas();
            int stack = canvas.getSaveCount();
            for (int i = 0; i < 8; i++) {
                canvas.clear(0);
                if (i % 2 == 0) banner.renderInHud(canvas, 1000, 800);
                else banner.renderInPanel(canvas, 10, 10, 650, 700, -1, -1);
                assertEquals(stack, canvas.getSaveCount(), "saveLayer must be balanced on every frame");
                assertSame(notification, banner.newest());
                assertEquals(born, banner.newest().born(), "handoff never renews the six-second timer");
                bitmap.allocN32Pixels(1000,800);
                assertTrue(surface.readPixels(bitmap,0,0));
                boolean painted = false;
                for (byte b : bitmap.readPixels()) if (b != 0) { painted = true; break; }
                assertTrue(painted, "real card renderer must paint pixels on both hosts");
                if (i % 2 == 0) assertNull(banner.hitTest(500, 40), "HUD cannot leave panel click targets");
            }
            time.set(6200);
            banner.tick();
            assertFalse(banner.hasActive(), "elapsed notifications expire instead of restarting after handoff");
            banner.reset();
            assertNull(banner.newest());
        } finally { cfg.set(null, before); }
    }
}
