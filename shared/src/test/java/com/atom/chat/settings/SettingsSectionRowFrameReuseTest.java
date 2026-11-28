package com.atom.chat.settings;

import com.atom.chat.api.ui.SettingsUi;
import com.atom.chat.api.ui.UiContributions;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import com.atom.chat.wallpaper.WallpaperStore;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsSectionRowFrameReuseTest {
    @Test
    void narrowGroupHeadingKeepsTheWholeEnglishLabel() {
        String label = "Diagnostics and maintenance";
        float narrow = 280F;
        var lines = SettingsGroupPaint.titleLines(label, narrow, true);
        org.junit.jupiter.api.Assertions.assertTrue(lines.size() > 1);
        assertEquals(label.replace(" ", ""), String.join("", lines).replace(" ", ""));
        org.junit.jupiter.api.Assertions.assertTrue(lines.stream().anyMatch(line -> line.contains("maintenance")));
        org.junit.jupiter.api.Assertions.assertTrue(SettingsGroupPaint.height(label, narrow, true)
                > SettingsGroupPaint.height(label, 420F, true));
    }

    @Test
    void measureAndDrawBuildTheRowListOncePerFrame(@TempDir Path root) {
        installPlatform(root);
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(10.0F, 10.0F, 420.0F, 760.0F);
        try (Surface surface = Surface.makeRasterN32Premul(460, 820)) {
            page.measureContent(layout, SettingsSection.PRIVACY);
            page.render(surface.getCanvas(), layout, SettingsSection.PRIVACY,
                    -1.0F, -1.0F, 0.0F, 0xFF3B82F6);
            assertEquals(1L, page.rowBuildsForTest(),
                    "the draw pass must reuse the rows measured for the same frame");

            page.selectChip(SettingsSection.APPEARANCE, 1);
            page.measureContent(layout, SettingsSection.APPEARANCE);
            page.render(surface.getCanvas(), layout, SettingsSection.APPEARANCE,
                    -1.0F, -1.0F, 0.0F, 0xFF3B82F6);
            assertEquals(2L, page.rowBuildsForTest(),
                    "the next frame rebuilds once and its draw still reuses that list");
        }
    }

    @Test
    void warmedPresetsReuseStaticTextAndMaterialRemeasuresDynamicControls(@TempDir Path root) {
        installPlatform(root);
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout narrow = UiLayout.ofDetail(10F, 10F, 420F, 760F);
        for (int frame = 0; frame < 80; frame++) page.warmAppearanceTheme(narrow);
        long measured = page.compactTextBuildsForTest();
        page.measureContent(narrow, SettingsSection.APPEARANCE);
        page.measureContent(narrow, SettingsSection.APPEARANCE);
        assertEquals(measured, page.compactTextBuildsForTest(), "preset text stays cached");
        UiLayout widened = UiLayout.ofDetail(10F, 10F, 500F, 760F);
        page.measureContent(widened, SettingsSection.APPEARANCE);
        org.junit.jupiter.api.Assertions.assertTrue(page.compactTextBuildsForTest() > measured + 3);
        page.selectChip(SettingsSection.APPEARANCE, 1);
        int rows = page.rows(SettingsSection.APPEARANCE).size();
        page.measureContent(widened, SettingsSection.APPEARANCE);
        long afterMaterial = page.compactTextBuildsForTest();
        page.measureContent(widened, SettingsSection.APPEARANCE);
        assertEquals(afterMaterial + rows, page.compactTextBuildsForTest(),
                "material settings remeasure changing values");
    }

    @Test
    void presetGeometrySurvivesSuccessivePaintsWithTheSameInputs(@TempDir Path root) throws Exception {
        installPlatform(root);
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(10F, 10F, 420F, 760F);
        var geometry = SettingsSectionPage.class.getDeclaredField("rowGeometry");
        geometry.setAccessible(true);
        float height = page.measureContent(layout, SettingsSection.APPEARANCE);
        Object first = geometry.get(page);
        long entered = 10_000L;
        page.markEntered(entered);
        try (Surface surface = Surface.makeRasterN32Premul(550, 820)) {
            for (long elapsed : new long[]{0L, 80L, 240L, 650L}) {
                assertEquals(height, page.measureContent(layout, SettingsSection.APPEARANCE));
                assertSame(first, geometry.get(page), "unchanged input must reuse the measured row geometry");
                UiStagger.atFrame(entered + elapsed, () -> page.renderBody(surface.getCanvas(), layout,
                        SettingsSection.APPEARANCE, -1F, -1F, 0F, 0xFF3B82F6));
                assertSame(first, geometry.get(page), "the renderer must consume the measured geometry");
            }
        }

        UiLayout wider = UiLayout.ofDetail(10F, 10F, 520F, 760F);
        page.measureContent(wider, SettingsSection.APPEARANCE);
        Object resized = geometry.get(page);
        assertNotSame(first, resized);
        SettingsSectionPage.clearTranslationCache();
        page.measureContent(wider, SettingsSection.APPEARANCE);
        assertNotSame(resized, geometry.get(page));
    }

    @Test
    void presetGeometryTracksAddedAndRemovedRows(@TempDir Path root) {
        installPlatform(root);
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(10F, 10F, 420F, 760F);
        float baseline = page.measureContent(layout, SettingsSection.APPEARANCE);
        var rows = SettingsUi.rows(SettingsSection.APPEARANCE, SettingsUi.builtInTabId("theme"), SettingsUi.TOP_ROWS);
        String id = "atomchat_test:preset_geometry";
        rows.add(id, new SettingsUi.Info("atomchat.settings.appearance", "atomchat.settings.appearance.presets"),
                UiContributions.Position.first());
        try {
            assertTrue(page.measureContent(layout, SettingsSection.APPEARANCE) > baseline);
        } finally {
            rows.unregister(id);
        }
        assertEquals(baseline, page.measureContent(layout, SettingsSection.APPEARANCE));
    }

    @Test
    void changingAnAlreadySetWallpaperInvalidatesPresetMeasurement(@TempDir Path root) throws Exception {
        installPlatform(root);
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(10F, 10F, 420F, 760F);
        var active = WallpaperStore.class.getDeclaredField("active");
        var directory = WallpaperStore.class.getDeclaredField("dir");
        var measurement = SettingsSectionPage.class.getDeclaredField("appearanceMeasurement");
        active.setAccessible(true);
        directory.setAccessible(true);
        measurement.setAccessible(true);
        Object original = active.get(null);
        Object originalDirectory = directory.get(null);
        try {
            Path first = Files.createDirectories(root.resolve("first"));
            Files.write(first.resolve("wallpaper.png"), new byte[]{1});
            WallpaperStore.init(first);
            assertTrue(WallpaperStore.isSet());
            page.measureContent(layout, SettingsSection.APPEARANCE);
            Object firstMeasurement = measurement.get(page);

            Path second = Files.createDirectories(root.resolve("second"));
            Files.write(second.resolve("wallpaper.webp"), new byte[]{2});
            WallpaperStore.init(second);
            assertTrue(WallpaperStore.isSet());
            page.measureContent(layout, SettingsSection.APPEARANCE);
            assertNotSame(firstMeasurement, measurement.get(page));
        } finally {
            directory.set(null, originalDirectory);
            active.set(null, original);
        }
    }

    @Test
    void reusedAndFreshPresetLayoutsPaintIdenticallyDuringEntranceAndScroll(@TempDir Path root) throws Exception {
        installPlatform(root);
        SettingsSectionPage reused = new SettingsSectionPage();
        SettingsSectionPage rebuilt = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(10F, 10F, 420F, 470F);
        var measurement = SettingsSectionPage.class.getDeclaredField("appearanceMeasurement");
        measurement.setAccessible(true);
        AtomChatConfig config = AtomChatConfig.get();
        boolean animated = config.animationEnabled;
        var style = config.pageNavStyle;
        config.animationEnabled = true;
        config.pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
        long entered = 10_000L;
        reused.markEntered(entered);
        rebuilt.markEntered(entered);
        try (Surface actual = Surface.makeRasterN32Premul(450, 500);
             Surface expected = Surface.makeRasterN32Premul(450, 500)) {
            for (long elapsed : new long[]{0L, 80L, 240L, 650L}) {
                float scroll = elapsed == 650L ? 12F : 0F;
                measurement.set(rebuilt, null);
                assertEquals(rebuilt.measureContent(layout, SettingsSection.APPEARANCE),
                        reused.measureContent(layout, SettingsSection.APPEARANCE));
                assertArrayEquals(paintFrame(rebuilt, expected, layout, entered + elapsed, scroll),
                        paintFrame(reused, actual, layout, entered + elapsed, scroll),
                        "the cached and fresh layouts differ at " + elapsed + " ms");
            }
        } finally {
            config.animationEnabled = animated;
            config.pageNavStyle = style;
        }
    }

    private static byte[] paintFrame(SettingsSectionPage page, Surface surface, UiLayout layout,
                                     long now, float scroll) {
        surface.getCanvas().clear(0);
        UiStagger.atFrame(now, () -> page.renderBody(surface.getCanvas(), layout,
                SettingsSection.APPEARANCE, -1F, -1F, scroll, 0xFF3B82F6));
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(surface.getWidth(), surface.getHeight());
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }

    private static void installPlatform(Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String modId) { return false; }
        });
    }

    @Test
    void rowRectsDoNotRemeasureThePrefixForEveryRow(@TempDir Path root) {
        installPlatform(root);
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(10.0F, 10.0F, 420.0F, 760.0F);
        SettingsSectionPage.Row textRow = page.rows(SettingsSection.CHAT).stream()
                .filter(row -> row.kind() == SettingsSectionPage.RowKind.SWITCH)
                .findFirst()
                .orElseThrow();
        List<SettingsSectionPage.Row> rows = Collections.nCopies(80, textRow);

        long before = page.compactTextBuildsForTest();
        for (int i = 0; i < rows.size(); i++) {
            page.rowRect(SettingsSection.CHAT, rows, i, 0.0F, layout);
        }

        long measured = page.compactTextBuildsForTest() - before;
        // The old prefix walk measured rows 0..i for every rowRect call (about 3,240
        // text measurements for 80 rows). Geometry is O(n), with a small allowance
        // for callers that legitimately measure the row while building it.
        assertEquals(rows.size(), measured,
                "one frame should measure each settings row once, not once per prefix step");
    }
}
