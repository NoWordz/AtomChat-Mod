package com.atom.chat.settings;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.api.ui.AppearanceUi.Part;
import com.atom.chat.api.ui.SettingsUi;
import com.atom.chat.api.ui.UiContributions;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.wallpaper.WallpaperStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceSettingsTest {
    @Test
    void savedUninstalledPartsDoNotCreateNativeSettingsRows() throws Exception {
        var instance = AtomChatConfig.class.getDeclaredField("instance");
        instance.setAccessible(true);
        var previous = instance.get(null);
        var config = new AtomChatConfig();
        config.bubbleAppearanceId = "atomchat_signal:capsule";
        config.avatarFrameAppearanceId = "atomchat_harbor:tide_ring";
        instance.set(null, config);
        try {
            var rows = new SettingsSectionPage().rows(SettingsSection.APPEARANCE);
            assertFalse(rows.stream().anyMatch(row ->
                    "atomchat.settings.appearance.parts.group".equals(row.labelKey())
                    || row.actionId() != null && row.actionId().startsWith("appearance_part_")));
            assertEquals("atomchat_signal:capsule", config.bubbleAppearanceId);
            assertEquals("atomchat_harbor:tide_ring", config.avatarFrameAppearanceId);
        } finally {
            instance.set(null, previous);
        }
    }

    @Test
    void generatedWallpaperCommitsAgainstReloadedConfigAndChecksItsOriginalSelection(@TempDir Path dir) throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return dir.resolve("config"); }
            public Path gameDir() { return dir; }
            public boolean isModLoaded(String id) { return false; }
        });
        var instance = AtomChatConfig.class.getDeclaredField("instance");
        instance.setAccessible(true);
        var previous = instance.get(null);
        String requested = "appearance_test:old_wallpaper";
        try {
            var original = new AtomChatConfig();
            original.dynamicWallpaperAppearanceId = requested;
            original.panelOpacity = .51F;
            instance.set(null, original);
            assertTrue(AtomChatConfig.saveChecked(original));
            AtomChatConfig.reload();
            var current = AtomChatConfig.get();
            assertNotSame(original, current);
            current.panelOpacity = .83F;

            assertFalse(AppearanceSettings.commitStaticWallpaperFor(null));
            assertFalse(AppearanceSettings.commitStaticWallpaperFor("appearance_test:another_wallpaper"));
            assertEquals(requested, current.dynamicWallpaperAppearanceId);
            assertTrue(AppearanceSettings.commitStaticWallpaperFor(requested));
            assertNull(current.dynamicWallpaperAppearanceId);
            assertEquals(requested, original.dynamicWallpaperAppearanceId);
            AtomChatConfig.reload();
            assertNull(AtomChatConfig.get().dynamicWallpaperAppearanceId);
            assertTrue(AppearanceSettings.commitStaticWallpaperFor(null));
            assertEquals(.83F, AtomChatConfig.get().panelOpacity);
        } finally {
            instance.set(null, previous);
        }
    }

    @Test
    void onlyAddonContributionsCreateAppearanceControls(@TempDir Path dir) throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return dir.resolve("config"); }
            public Path gameDir() { return dir; }
            public boolean isModLoaded(String id) { return false; }
        });
        var instance = AtomChatConfig.class.getDeclaredField("instance");
        instance.setAccessible(true);
        var previous = instance.get(null);
        var config = new AtomChatConfig();
        instance.set(null, config);
        WallpaperStore.init(dir.resolve("atomchat/client/wallpaper"));

        String bubble = "appearance_test_a:bubble";
        String outline = "appearance_test_b:outline";
        String avatar = "appearance_test_a:avatar";
        String wallpaper = "appearance_test_b:wallpaper";
        String groupId = "appearance_test_a:settings";
        String groupLabel = "sample.appearance.group";
        var groups = SettingsUi.groups(SettingsSection.APPEARANCE, SettingsUi.builtInTabId("theme"));
        try {
            AppearanceUi.registerBubble(bubble, "sample.bubble", c -> null, null);
            AppearanceUi.registerPanelOutline(outline, "sample.outline", c -> null);
            AppearanceUi.registerAvatarFrame(avatar, "sample.avatar", (c, f) -> {}, null);
            AppearanceUi.registerDynamicWallpaper(wallpaper, "sample.wallpaper", () -> (c, f) -> {});
            config.panelOutlineAppearanceId = outline;
            config.dynamicWallpaperAppearanceId = wallpaper;

            var section = SettingsSection.APPEARANCE;
            var page = new SettingsSectionPage();
            int nativeRows = page.rows(section).size();
            assertFalse(page.rows(section).stream().anyMatch(row ->
                    "atomchat.settings.appearance.parts.group".equals(row.labelKey())
                    || row.actionId() != null && row.actionId().startsWith("appearance_part_")));
            assertFalse(AppearanceUi.select(Part.BUBBLE, "appearance_test_a:unregistered"));
            assertNull(AppearanceUi.selectedId(Part.BUBBLE));

            var toggle = new SettingsUi.Toggle("appearance_test_a:bubble_setting", "sample.bubble", "sample.desc",
                    () -> bubble.equals(AppearanceUi.selectedId(Part.BUBBLE)),
                    enabled -> AppearanceUi.select(Part.BUBBLE, enabled ? bubble : null));
            groups.add(groupId, new SettingsUi.Group(groupLabel, List.of(toggle)), UiContributions.Position.last());
            var rows = page.rows(section);
            assertTrue(rows.stream().anyMatch(row -> groupLabel.equals(row.labelKey())));
            var switchRow = rows.stream().filter(row -> row.item() != null && toggle.id().equals(row.item().id()))
                    .findFirst().orElseThrow();
            var hit = new SettingsSectionPage.RowHit(switchRow, rows.indexOf(switchRow), 0, 0, 10, 10, 0);
            assertTrue(page.perform(hit));
            assertTrue(AppearanceUi.select(Part.AVATAR_FRAME, avatar));
            var selections = new AppearanceSettings.Selection(bubble, outline, avatar, wallpaper);
            assertEquals(selections, AppearanceSettings.Selection.capture(AtomChatConfig.get()));

            AtomChatConfig.reload();
            assertEquals(selections, AppearanceSettings.Selection.capture(AtomChatConfig.get()));
            assertTrue(switchRow.item().value(), "the contributed row reads the current saved value");
            Path configPath = dir.resolve("config/atomchat/atomchat-client.json");
            byte[] settings = Files.readAllBytes(configPath);
            Files.writeString(configPath, "{");
            assertFalse(page.perform(hit), "failed persistence must not report a toggled row");
            assertEquals(bubble, AppearanceUi.selectedId(Part.BUBBLE));
            assertEquals("{", Files.readString(configPath));
            Files.write(configPath, settings);
            assertTrue(groups.unregister(groupId));
            assertTrue(AppearanceUi.unregister(Part.BUBBLE, bubble));
            assertTrue(AppearanceUi.unregister(Part.AVATAR_FRAME, avatar));
            assertEquals(nativeRows, page.rows(section).size());
            assertEquals(selections, AppearanceSettings.Selection.capture(AtomChatConfig.get()));
            assertNull(AppearanceSettings.label(Part.BUBBLE, bubble));
            assertTrue(page.rows(section).stream().anyMatch(row -> row.kind() == SettingsSectionPage.RowKind.WALLPAPERS));
            assertTrue(page.rows(section).stream().anyMatch(row -> "wallpaper_pick".equals(row.actionId())));

            assertTrue(SettingsTabDefaults.reset(AtomChatConfig.get(), section, "theme",
                    () -> AtomChatConfig.saveChecked(AtomChatConfig.get())));
            assertEquals(new AppearanceSettings.Selection(null, null, null, null),
                    AppearanceSettings.Selection.capture(AtomChatConfig.get()));
        } finally {
            groups.unregister(groupId);
            AppearanceUi.unregister(Part.BUBBLE, bubble);
            AppearanceUi.unregister(Part.PANEL_OUTLINE, outline);
            AppearanceUi.unregister(Part.AVATAR_FRAME, avatar);
            AppearanceUi.unregister(Part.DYNAMIC_WALLPAPER, wallpaper);
            WallpaperStore.init(null);
            instance.set(null, previous);
        }
    }
}
