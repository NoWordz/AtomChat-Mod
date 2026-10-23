package com.atom.chat.notification;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class NotificationPresentationTest {
    @Test void frontBannerBodyOccludesAButtonOnAnOlderReflowingBanner() throws Exception {
        withConfig(config -> {
            var clock = new AtomicLong(1000); var banner = new NotificationBanner(clock::get);
            banner.enqueue(NotificationBanner.Type.MENTION, "Alice", "first", null);
            var old = banner.newest();
            try (var surface = Surface.makeRasterN32Premul(1000, 800)) {
                clock.addAndGet(600);
                banner.renderInPanel(surface.getCanvas(), 10, 10, 650, 700, -1, -1);
                banner.enqueue(NotificationBanner.Type.MENTION, "Bob", "second", null);
                clock.addAndGet(16);
                banner.renderInPanel(surface.getCanvas(), 10, 10, 650, 700, -1, -1);
                Rect oldButton = bounds(banner, old, "buttonRects");
                Rect newButton = bounds(banner, banner.newest(), "buttonRects");
                float x = oldButton.getLeft() + oldButton.getWidth() / 2;
                float y = newButton.getBottom() + 1;
                assertTrue(y < oldButton.getBottom() && y > oldButton.getTop(), "fixture must overlap old button");
                assertSame(banner.newest(), banner.hitTest(x, y));
                assertNull(banner.buttonHit(x, y), "front card body must occlude the old card's reply button");
            }
        });
    }

    @Test void dismissAndImmediateHudHandoffDoesNotHideRemainingCardDuringReflow() throws Exception {
        withConfig(config -> {
            var clock = new AtomicLong(1000); var banner = new NotificationBanner(clock::get);
            banner.enqueue(NotificationBanner.Type.MENTION, "Alice", "first", null);
            banner.enqueue(NotificationBanner.Type.MENTION, "Bob", "second", null);
            try (var surface = Surface.makeRasterN32Premul(1000, 800); var bitmap = new Bitmap()) {
                clock.addAndGet(600);
                banner.renderInPanel(surface.getCanvas(), 10, 10, 650, 700, -1, -1);
                banner.dismiss(banner.newest());
                banner.renderInHud(surface.getCanvas(), 1000, 800); // begins the host fade
                clock.addAndGet(130);
                surface.getCanvas().clear(0);
                banner.renderInHud(surface.getCanvas(), 1000, 800);
                bitmap.allocN32Pixels(1000, 800); assertTrue(surface.readPixels(bitmap, 0, 0));
                boolean painted = false;
                for (byte pixel : bitmap.readPixels()) if (pixel != 0) { painted = true; break; }
                assertTrue(painted, "remaining banner must paint once the host fade completes");
            }
        });
    }
    @Test void addingNoticeDoesNotTeleportExistingHudPixelsAtAnyAnchor() throws Exception {
        withConfig(config -> {
            for (var anchor : NotificationHudLayout.Anchor.values()) {
                config.notificationHudPosition = anchor;
                var clock = new AtomicLong(1000);
                var banner = new NotificationBanner(clock::get);
                banner.enqueue(NotificationBanner.Type.MENTION, "Alice", "first", null);
                try (var surface = Surface.makeRasterN32Premul(1000, 800); var bitmap = new Bitmap()) {
                    var canvas = surface.getCanvas();
                    clock.addAndGet(600);
                    banner.renderInHud(canvas, 1000, 800);
                    bitmap.allocN32Pixels(1000, 800); assertTrue(surface.readPixels(bitmap, 0, 0));
                    byte[] before = bitmap.readPixels();
                    banner.enqueue(NotificationBanner.Type.MENTION, "Bob", "second", null);
                    canvas.clear(0);
                    banner.renderInHud(canvas, 1000, 800);
                    assertTrue(surface.readPixels(bitmap, 0, 0));
                    byte[] after = bitmap.readPixels();
                    int maxDifference = 0, changed = 0;
                    for (int i = 0; i < before.length; i++) {
                        int difference = Math.abs((before[i] & 255) - (after[i] & 255));
                        maxDifference = Math.max(maxDifference, difference);
                        if (difference != 0) changed++;
                    }
                    // Equivalent rebased float matrices may round one channel by one unit.
                    assertTrue(maxDifference <= 1, "anchor=" + anchor + ", max=" + maxDifference + ", changed=" + changed);
                }
            }
        });
    }
    @Test void backdropReallyBlursCurrentPixelsInsideTheCardAndKeepsTheOuterFrameBalanced() throws Exception {
        withConfig(config -> {
            config.cardTint = .5F; config.cardColor = 0xFFFFFFFF;
            try (var surface = Surface.makeRasterN32Premul(800, 180); var bitmap = new Bitmap(); var paint = new Paint()) {
                var canvas = surface.getCanvas();
                int saves = canvas.getSaveCount();
                for (int frame = 0; frame < 8; frame++) {
                    canvas.clear(0);
                    // Also exercise the non-root layer used while the panel opens/closes.
                    canvas.saveLayerAlpha(null, 255);
                    for (int x = 0; x < 800; x += 4) {
                        paint.setColor(((x / 4 + frame) % 2 == 0) ? 0xFF000000 : 0xFFFFFFFF);
                        canvas.drawRect(Rect.makeXYWH(x, 0, 4, 180), paint);
                    }
                    new NotificationBanner(() -> 1000).renderPreview(canvas, 700);
                    canvas.restore();
                    assertEquals(saves, canvas.getSaveCount());
                    bitmap.allocN32Pixels(800, 180); assertTrue(surface.readPixels(bitmap, 0, 0));
                    for (int x : new int[]{652, 656}) {
                        int red = (bitmap.getColor(x, (int) NotificationBanner.bannerHeight() / 2) >> 16) & 255;
                        assertTrue(red > 165 && red < 220, "fine stripes must be blurred before the translucent fill: " + red);
                    }
                    int outside = bitmap.getColor(760, 100);
                    assertEquals(((760 / 4 + frame) % 2 == 0) ? 0xFF000000 : 0xFFFFFFFF, outside,
                            "backdrop never filters pixels outside the rounded card");
                }
            }
        });
    }
    @Test void opacityFollowsCardsWithHalfOpacityFloorWithoutChangingRgb() throws Exception {
        withConfig(config -> {
            config.cardColor = 0xFFFFFFFF;
            try (var surface = Surface.makeRasterN32Premul(800, 160); var bitmap = new Bitmap()) {
                var banner = new NotificationBanner(() -> 1000);
                int previous = -1;
                for (float tint : new float[]{0, .25F, .5F, .75F, 1}) {
                    config.cardTint = tint;
                    surface.getCanvas().clear(0);
                    banner.renderPreview(surface.getCanvas(), 700);
                    bitmap.allocN32Pixels(800, 160);
                    assertTrue(surface.readPixels(bitmap, 0, 0));
                    int color = bitmap.getColor(660, (int) NotificationBanner.bannerHeight() / 2);
                    int alpha = color >>> 24;
                    assertTrue(Math.abs(alpha - Math.round(255 * Math.max(.5F, tint))) < 20,
                            "card tint " + tint + " must control alpha, actual=" + alpha);
                    if (tint <= .5F && previous >= 0) assertEquals(previous, color, "50% floor");
                    if (tint == .75F) assertTrue(alpha > (previous >>> 24));
                    previous = color;
                }
            }
        });
    }

    @Test void newBannerSlidesOlderBannerDownWithoutTeleportingEvenInZoomMode() throws Exception {
        withConfig(config -> {
            config.pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
            var clock = new AtomicLong(1000);
            var banner = new NotificationBanner(clock::get);
            banner.enqueue(NotificationBanner.Type.MENTION, "Alice", "first", null);
            var old = banner.newest();
            try (var surface = Surface.makeRasterN32Premul(1000, 800)) {
                for (int i = 0; i < 30; i++) {
                    clock.addAndGet(16);
                    banner.renderInPanel(surface.getCanvas(), 10, 10, 650, 700, -1, -1);
                }
                Rect before = bounds(banner, old);
                banner.enqueue(NotificationBanner.Type.MENTION, "Bob", "second", null);
                banner.renderInPanel(surface.getCanvas(), 10, 10, 650, 700, -1, -1);
                assertEquals(before.getTop(), bounds(banner, old).getTop(), .001F, "no first-frame jump");
                clock.addAndGet(16);
                banner.renderInPanel(surface.getCanvas(), 10, 10, 650, 700, -1, -1);
                Rect moving = bounds(banner, old);
                assertTrue(moving.getTop() > before.getTop(), "newest enters above older notices");
                assertTrue(moving.getTop() < before.getTop() + NotificationBanner.bannerHeight(), "slide through intermediate positions");
                assertEquals(before.getWidth(), moving.getWidth(), .001F, "reflow must not zoom");
            }
        });
    }

    @Test void disabledAnimationsPaintImmediatelyAndKeepOriginalExpiry() throws Exception {
        withConfig(config -> {
            config.animationEnabled = false;
            var clock = new AtomicLong(1000);
            var banner = new NotificationBanner(clock::get);
            banner.enqueue(NotificationBanner.Type.MENTION, "Alice", "hello", null);
            var notice = banner.newest();
            try (var surface = Surface.makeRasterN32Premul(1000, 800)) {
                banner.renderInPanel(surface.getCanvas(), 10, 10, 650, 700, -1, -1);
                assertNotNull(bounds(banner, notice), "no invisible first frame with motion disabled");
                assertSame(notice, banner.hitTest(bounds(banner, notice).getLeft() + 1, bounds(banner, notice).getTop() + 1));
                clock.addAndGet(6000);
                banner.tick();
                assertFalse(banner.hasActive(), "disabled exit must not hold an invisible queue slot");
            }
        });
    }

    @Test void zoomAndSlideUseGlobalPanelGeometryAndHitThePaintedBounds() throws Exception {
        withConfig(config -> {
            for (var style : AtomChatConfig.PageNavStyle.values()) {
                config.pageNavStyle = style;
                var clock = new AtomicLong(1000);
                var banner = new NotificationBanner(clock::get);
                banner.enqueue(NotificationBanner.Type.MENTION, "Alice", "hello", null);
                try (var surface = Surface.makeRasterN32Premul(1000, 800)) {
                    banner.renderInPanel(surface.getCanvas(), 10, 10, 650, 700, -1, -1);
                    clock.addAndGet(16);
                    banner.renderInPanel(surface.getCanvas(), 10, 10, 650, 700, -1, -1);
                    Rect r = bounds(banner, banner.newest());
                    assertNotNull(r);
                    if (style == AtomChatConfig.PageNavStyle.ZOOM)
                        assertTrue(r.getWidth() < NotificationBanner.bannerWidth(650), "zoom must affect actual hit rect");
                    else assertTrue(r.getLeft() < NotificationBanner.bannerX(10), "slide uses global horizontal motion");
                }
            }
        });
    }

    private static Rect bounds(NotificationBanner banner, NotificationBanner.Active notice) throws Exception {
        return bounds(banner, notice, "hitRects");
    }
    private static Rect bounds(NotificationBanner banner, NotificationBanner.Active notice, String fieldName) throws Exception {
        var field = NotificationBanner.class.getDeclaredField(fieldName); field.setAccessible(true);
        for (Object hit : (List<?>) field.get(banner)) {
            var owner = hit.getClass().getDeclaredMethod("banner"); owner.setAccessible(true);
            var rect = hit.getClass().getDeclaredMethod("rect"); rect.setAccessible(true);
            if (owner.invoke(hit) == notice) return (Rect) rect.invoke(hit);
        }
        return null;
    }

    interface Check { void run(AtomChatConfig config) throws Exception; }
    private static void withConfig(Check check) throws Exception {
        Field field = AtomChatConfig.class.getDeclaredField("instance"); field.setAccessible(true);
        Object before = field.get(null);
        var config = new AtomChatConfig(); field.set(null, config);
        try { check.run(config); } finally { field.set(null, before); }
    }
}
