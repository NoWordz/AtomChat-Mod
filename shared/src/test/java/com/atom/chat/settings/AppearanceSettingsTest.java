package com.atom.chat.settings;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.api.ui.AppearanceUi.Part;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.wallpaper.WallpaperStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceSettingsTest {
    @Test
    void nativeRowsPersistMixedAddonsAndAnAbsentChoiceCanBeReset(@TempDir Path dir) throws Exception {
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
        try {
            AppearanceUi.registerBubble(bubble, "sample.bubble", c -> null, null);
            AppearanceUi.registerPanelOutline(outline, "sample.outline", c -> null);
            AppearanceUi.registerAvatarFrame(avatar, "sample.avatar", (c, f) -> {}, null);
            AppearanceUi.registerDynamicWallpaper(wallpaper, "sample.wallpaper", () -> (c, f) -> {});

            var page = new SettingsSectionPage();
            var avatars = new com.atom.chat.avatar.AvatarStore(dir.resolve("avatars"));
            var host = (SettingsActions.Host) Proxy.newProxyInstance(SettingsActions.Host.class.getClassLoader(),
                    new Class<?>[]{SettingsActions.Host.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "sectionPage" -> page;
                        case "avatarStore" -> avatars;
                        default -> throw new AssertionError("Unexpected host call " + method.getName());
                    });
            new SettingsActions(host);

            for (Part part : Part.values()) clickArrow(page, part, 1);
            assertEquals(new AppearanceSettings.Selection(bubble, outline, avatar, wallpaper),
                    AppearanceSettings.Selection.capture(config));

            instance.set(null, null);
            config = AtomChatConfig.get();
            assertEquals(new AppearanceSettings.Selection(bubble, outline, avatar, wallpaper),
                    AppearanceSettings.Selection.capture(config));
            assertTrue(AppearanceUi.unregister(Part.PANEL_OUTLINE, outline));
            assertEquals(AppearanceSettings.MISSING, AppearanceSettings.label(Part.PANEL_OUTLINE, outline));
            assertEquals(outline, config.panelOutlineAppearanceId);
            clickArrow(page, Part.PANEL_OUTLINE, -1);
            assertNull(config.panelOutlineAppearanceId);
            assertEquals(bubble, config.bubbleAppearanceId);
            assertEquals(wallpaper, config.dynamicWallpaperAppearanceId);

            assertTrue(SettingsTabDefaults.reset(config, SettingsSection.APPEARANCE, "theme",
                    () -> AtomChatConfig.saveChecked(AtomChatConfig.get())));
            assertEquals(new AppearanceSettings.Selection(null, null, null, null),
                    AppearanceSettings.Selection.capture(config));
        } finally {
            for (var option : List.of(bubble, outline, avatar, wallpaper)) {
                for (Part part : Part.values()) AppearanceUi.unregister(part, option);
            }
            WallpaperStore.init(null);
            instance.set(null, previous);
        }
    }

    private static void clickArrow(SettingsSectionPage page, Part part, int direction) throws Exception {
        var section = SettingsSection.APPEARANCE;
        var layout = UiLayout.ofDetail(0, 0, 520, 1800);
        var rows = page.rows(section);
        String expected = AppearanceSettings.action(part);
        var row = rows.stream().filter(candidate -> expected.equals(candidate.actionId())).findFirst().orElseThrow();
        var rowRect = SettingsSectionPage.class.getDeclaredMethod("rowRect", SettingsSection.class,
                List.class, int.class, float.class, UiLayout.class);
        rowRect.setAccessible(true);
        var rect = (UiLayout.Rect) rowRect.invoke(page, section, rows, rows.indexOf(row), 0F, layout);
        var geometry = page.stepperGeometry(row, rect);
        var arrow = direction < 0 ? geometry.previous() : geometry.next();
        var hit = page.hit(arrow.x() + arrow.w() / 2, arrow.y() + arrow.h() / 2, layout, section, 0);
        assertNotNull(hit, part.toString());
        assertEquals(direction < 0 ? expected + ":prev" : expected, hit.row().actionId());
        assertTrue(page.perform(hit));
    }
}
