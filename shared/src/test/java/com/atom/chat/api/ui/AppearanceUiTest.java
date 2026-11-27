package com.atom.chat.api.ui;

import com.atom.chat.api.ui.AppearanceUi.Part;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceUiTest {
    private static final String FIRST = "appearance_test:amber";
    private static final String SECOND = "appearance_test:violet";
    private static final String MISSING = "appearance_test:missing";
    private static final String LABEL = "appearance.appearance_test.amber";
    private static final AppearanceUi.Selection BUILT_IN = new AppearanceUi.Selection(null, null, null, null);
    private static final AppearanceUi.Bubble BUBBLE = context -> cutCorner(context.bounds());
    private static final AppearanceUi.PanelOutline PANEL = context -> cutCorner(context.bounds());
    private static final AppearanceUi.AvatarFrame FRAME = (canvas, context) -> {};
    private static final AppearanceUi.DynamicWallpaper WALLPAPER = () -> (canvas, frame) -> {};

    @AfterEach
    void removeTestRegistrations() {
        for (var part : Part.values()) {
            for (String id : List.of(FIRST, SECOND, MISSING)) AppearanceUi.unregister(part, id);
        }
    }

    @Test
    void validatesStableIdsAndRejectsInvalidRegistrationWithoutChangingRevision() {
        assertTrue(AppearanceUi.validId("example.mod-1:ui/cut_corner-2.0"));
        for (String id : List.of("", "bare", "mod:", ":part", "MOD:part", "mod:Part", "mod:a:b", "mod:a ", "mod:a\\b")) {
            assertFalse(AppearanceUi.validId(id), id);
            assertThrows(IllegalArgumentException.class, () -> AppearanceUi.registerBubble(id, LABEL, BUBBLE, null));
        }
        assertFalse(AppearanceUi.validId(null));

        long before = AppearanceUi.revision();
        assertThrows(NullPointerException.class, () -> AppearanceUi.registerBubble(null, LABEL, BUBBLE, null));
        assertThrows(IllegalArgumentException.class, () -> AppearanceUi.registerPanelOutline(FIRST, " ", PANEL));
        assertThrows(IllegalArgumentException.class, () -> AppearanceUi.registerBubble(FIRST, LABEL, null, null));
        assertThrows(IllegalArgumentException.class, () -> AppearanceUi.registerAvatarFrame(FIRST, LABEL, null, null));
        assertThrows(NullPointerException.class, () -> AppearanceUi.registerDynamicWallpaper(FIRST, LABEL, null));
        assertFalse(AppearanceUi.unregister(Part.BUBBLE, MISSING));
        assertEquals(before, AppearanceUi.revision());
    }

    @Test
    void listsOnlyAddonOptionsInIdOrderAndKeepsIdsIndependentByPart() {
        long before = AppearanceUi.revision();
        AppearanceUi.registerBubble(SECOND, "appearance.appearance_test.violet", BUBBLE, null);
        AppearanceUi.registerBubble(FIRST, LABEL, BUBBLE, null);
        AppearanceUi.registerPanelOutline(FIRST, LABEL, PANEL);

        var bubbleOptions = AppearanceUi.options(Part.BUBBLE);
        assertEquals(List.of(new AppearanceUi.Option(FIRST, LABEL),
                new AppearanceUi.Option(SECOND, "appearance.appearance_test.violet")), bubbleOptions);
        assertSame(bubbleOptions, AppearanceUi.options(Part.BUBBLE));
        assertEquals(List.of(new AppearanceUi.Option(FIRST, LABEL)), AppearanceUi.options(Part.PANEL_OUTLINE));
        assertEquals(List.of(), AppearanceUi.options(Part.AVATAR_FRAME));
        assertThrows(UnsupportedOperationException.class, () -> bubbleOptions.clear());
        assertEquals(before + 3, AppearanceUi.revision());

        assertThrows(IllegalArgumentException.class, () -> AppearanceUi.registerBubble(FIRST, "other", BUBBLE, null));
        assertEquals(before + 3, AppearanceUi.revision());
        assertSame(bubbleOptions, AppearanceUi.options(Part.BUBBLE));

        assertTrue(AppearanceUi.unregister(Part.BUBBLE, FIRST));
        assertEquals(List.of(SECOND), AppearanceUi.options(Part.BUBBLE).stream().map(AppearanceUi.Option::id).toList());
        assertEquals(List.of(new AppearanceUi.Option(FIRST, LABEL)), AppearanceUi.options(Part.PANEL_OUTLINE));
        assertEquals(2, bubbleOptions.size());
        assertEquals(before + 4, AppearanceUi.revision());
    }

    @Test
    void resolvesMixedPartsAndFallsBackIndependentlyForPanelAndHud() {
        AppearanceUi.registerBubble(FIRST, LABEL, BUBBLE, null);
        AppearanceUi.registerPanelOutline(SECOND, LABEL, PANEL);
        AppearanceUi.registerAvatarFrame(FIRST, LABEL, null, FRAME);
        AppearanceUi.registerDynamicWallpaper(SECOND, LABEL, WALLPAPER);

        var resolver = new AppearanceUi.Resolver();
        var selected = new AppearanceUi.Selection(FIRST, SECOND, FIRST, SECOND);
        var resolved = resolver.resolve(selected);
        assertEquals(selected, resolved.selection());
        assertSame(BUBBLE, resolved.panelBubble());
        assertNull(resolved.hudBubble());
        assertSame(PANEL, resolved.panelOutline());
        assertNull(resolved.panelAvatarFrame());
        assertSame(FRAME, resolved.hudAvatarFrame());
        assertSame(WALLPAPER, resolved.dynamicWallpaper());
        assertSame(resolved, resolver.resolve(new AppearanceUi.Selection(FIRST, SECOND, FIRST, SECOND)));

        var defaults = resolver.resolve(BUILT_IN);
        assertNotSame(resolved, defaults);
        assertBuiltIn(defaults);
    }

    @Test
    void resolvesSeparatePanelAndHudImplementationsForTheSameSelection() {
        AppearanceUi.Bubble hudBubble = context -> cutCorner(context.bounds());
        AppearanceUi.AvatarFrame hudFrame = (canvas, context) -> {};
        AppearanceUi.registerBubble(FIRST, LABEL, BUBBLE, hudBubble);
        AppearanceUi.registerAvatarFrame(FIRST, LABEL, FRAME, hudFrame);
        var resolved = new AppearanceUi.Resolver().resolve(new AppearanceUi.Selection(FIRST, null, FIRST, null));

        assertSame(BUBBLE, resolved.panelBubble());
        assertSame(hudBubble, resolved.hudBubble());
        assertSame(FRAME, resolved.panelAvatarFrame());
        assertSame(hudFrame, resolved.hudAvatarFrame());
    }

    @Test
    void invalidatesCachedMissingSelectionOnRegistrationAndRestoresItAfterReinstallation() {
        var resolver = new AppearanceUi.Resolver();
        var selected = new AppearanceUi.Selection("invalid id", MISSING, MISSING, MISSING);
        long before = AppearanceUi.revision();
        var missing = resolver.resolve(selected);
        assertBuiltIn(missing);
        assertSame(selected, missing.selection());
        assertSame(missing, resolver.resolve(selected));
        assertEquals(before, missing.revision());
        assertEquals(before, AppearanceUi.revision());

        AppearanceUi.registerDynamicWallpaper(MISSING, LABEL, WALLPAPER);
        var installed = resolver.resolve(selected);
        assertNotSame(missing, installed);
        assertEquals(before + 1, installed.revision());
        assertSame(WALLPAPER, installed.dynamicWallpaper());
        assertNull(installed.panelOutline());

        assertTrue(AppearanceUi.unregister(Part.DYNAMIC_WALLPAPER, MISSING));
        var removed = resolver.resolve(selected);
        assertBuiltIn(removed);
        assertSame(selected, removed.selection());
        assertEquals(before + 2, removed.revision());
        assertSame(WALLPAPER, installed.dynamicWallpaper());

        AppearanceUi.DynamicWallpaper replacement = () -> (canvas, frame) -> {};
        AppearanceUi.registerDynamicWallpaper(MISSING, LABEL, replacement);
        var reinstalled = resolver.resolve(selected);
        assertSame(replacement, reinstalled.dynamicWallpaper());
        assertEquals(before + 3, reinstalled.revision());
    }

    @Test
    void defaultBubblePaintsItsSuppliedOutlineAndHostColor() {
        var bounds = new AppearanceUi.Bounds(2, 3, 20, 18);
        var context = new AppearanceUi.BubbleContext(bounds, AppearanceUi.BubbleGroup.FIRST, true,
                1.5f, 0xff39b6e8, 0xff222222);
        try (var shape = BUBBLE.outline(context); var surface = Surface.makeRasterN32Premul(32, 32);
             var bitmap = new Bitmap()) {
            surface.getCanvas().clear(0);
            BUBBLE.draw(surface.getCanvas(), context, shape);

            bitmap.allocN32Pixels(32, 32);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            assertEquals(0xff39b6e8, bitmap.getColor(10, 10));
            assertEquals(0, bitmap.getColor(3, 4));
            assertEquals(0, bitmap.getColor(22, 21));
            assertTrue(shape.contains(10, 10));
            assertFalse(shape.contains(3, 4));
        }
    }

    @Test
    void resolutionKeepsWallpaperFactoriesLazyAndSessionsCanDrawAndReleaseResources() {
        var bounds = new AppearanceUi.Bounds(1, 2, 15, 15);
        var frames = List.of(
                new AppearanceUi.WallpaperFrame(bounds, 0L, 0L, 1F, AppearanceUi.Quality.BALANCED),
                new AppearanceUi.WallpaperFrame(bounds, 16_000_000L, 16_000_000L, 1F, AppearanceUi.Quality.BALANCED));
        var events = new ArrayList<String>();
        AppearanceUi.registerDynamicWallpaper(FIRST, LABEL, () -> {
            events.add("open");
            var paint = new Paint();
            return new AppearanceUi.WallpaperSession() {
                @Override
                public void draw(Canvas canvas, AppearanceUi.WallpaperFrame frame) {
                    events.add("frame:" + frame.timeNanos());
                    int color = frame.deltaNanos() == 0 ? 0xff226688 : 0xff882244;
                    var area = frame.bounds();
                    canvas.drawRect(Rect.makeXYWH(area.x(), area.y(), area.width(), area.height()), paint.setColor(color));
                }

                @Override
                public void close() {
                    paint.close();
                    events.add("close");
                }
            };
        });
        var resolver = new AppearanceUi.Resolver();
        var selection = new AppearanceUi.Selection(null, null, null, FIRST);
        var appearance = resolver.resolve(selection);
        assertSame(appearance, resolver.resolve(selection));
        assertTrue(events.isEmpty());

        try (var session = appearance.dynamicWallpaper().open(); var surface = Surface.makeRasterN32Premul(20, 20);
             var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(20, 20);
            for (var frame : frames) {
                session.draw(surface.getCanvas(), frame);
                assertTrue(surface.readPixels(bitmap, 0, 0));
                assertEquals(frame.deltaNanos() == 0 ? 0xff226688 : 0xff882244, bitmap.getColor(5, 6));
            }
        }
        assertEquals(List.of("open", "frame:0", "frame:16000000", "close"), events);
    }

    private static Path cutCorner(AppearanceUi.Bounds bounds) {
        try (var builder = new PathBuilder()) {
            return builder.moveTo(bounds.x() + 6, bounds.y())
                    .lineTo(bounds.right(), bounds.y()).lineTo(bounds.right(), bounds.bottom())
                    .lineTo(bounds.x(), bounds.bottom()).lineTo(bounds.x(), bounds.y() + 6)
                    .closePath().detach();
        }
    }

    private static void assertBuiltIn(AppearanceUi.Resolved resolved) {
        assertNull(resolved.panelBubble());
        assertNull(resolved.hudBubble());
        assertNull(resolved.panelOutline());
        assertNull(resolved.panelAvatarFrame());
        assertNull(resolved.hudAvatarFrame());
        assertNull(resolved.dynamicWallpaper());
    }
}
