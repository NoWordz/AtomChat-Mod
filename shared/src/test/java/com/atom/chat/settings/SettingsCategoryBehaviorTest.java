package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.PreviewLanguage;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Surface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SettingsCategoryBehaviorTest {
    @BeforeAll static void installPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test void matchingPresetsAndCustomWallpaperShareTheFirstPage() {
        var page = new SettingsSectionPage();
        var rows = page.rows(SettingsSection.APPEARANCE);
        var kinds = rows.stream().map(SettingsSectionPage.Row::kind).toList();
        assertTrue(kinds.containsAll(List.of(SettingsSectionPage.RowKind.THEMES,
                SettingsSectionPage.RowKind.BUBBLES, SettingsSectionPage.RowKind.WALLPAPERS)));
        assertEquals("wallpaper_pick", rows.get(kinds.indexOf(SettingsSectionPage.RowKind.WALLPAPERS) + 1).actionId());
        assertFalse(kinds.contains(SettingsSectionPage.RowKind.COLOR));
    }

    @Test void notificationAndInteractionSettingsAreIndependent() {
        var page = new SettingsSectionPage();
        var notify = select(page, SettingsSection.CHAT, "notify");
        assertFalse(notify.stream().anyMatch(row -> "hud_edit".equals(row.actionId())));
        assertFalse(notify.stream().anyMatch(row -> row.item() != null && row.item().id().startsWith("feedback")));
        var interaction = select(page, SettingsSection.APPEARANCE, "interaction");
        for (String id : List.of("feedback", "feedback_panel", "motion", "entry", "poke")) {
            assertTrue(interaction.stream().anyMatch(row -> row.item() != null && id.equals(row.item().id())), id);
        }
        assertTrue(select(page, SettingsSection.APPEARANCE, "adjust").stream()
                .anyMatch(row -> "hud_edit".equals(row.actionId())));
        assertTrue(page.rows(SettingsSection.PRIVACY).stream()
                .anyMatch(row -> row.item() != null && "server_packs".equals(row.item().id())));
    }

    @Test void restoringMaterialRestoresBothOpacitiesAndKeepsAnimationAndColors() {
        var config = new AtomChatConfig();
        config.panelOpacity = .12F;
        config.cardTint = .21F;
        config.cardBlurStrength = 2F;
        config.animationEnabled = false;
        config.cardColor = 0xff112233;
        assertTrue(SettingsTabDefaults.reset(config, SettingsSection.APPEARANCE, "material", () -> true));
        assertEquals(AtomChatConfig.DEFAULT.panelOpacity, config.panelOpacity);
        assertEquals(AtomChatConfig.DEFAULT.cardTint, config.cardTint);
        assertEquals(2F, config.cardBlurStrength);
        assertFalse(config.animationEnabled);
        assertEquals(0xff112233, config.cardColor);
    }

    @Test void manualColorsRetainAllTwelveFieldsOnASeparatePage() {
        var page = new SettingsSectionPage();
        var rows = select(page, SettingsSection.APPEARANCE, "colors");
        var colors = rows.stream().filter(row -> row.kind() == SettingsSectionPage.RowKind.COLOR).toList();
        assertEquals(12, colors.size());
        var layout = UiLayout.ofDetail(0, 0, 430, 750);
        assertTrue(page.rowHeight(colors.get(0), layout) >= SettingsSectionPage.rowHeight(SettingsSectionPage.RowKind.SWITCH));
        assertTrue(page.rowHeight(colors.get(0), layout) < SettingsSectionPage.rowHeight(SettingsSectionPage.RowKind.SLIDER));
    }

    @Test void narrowColorRowsOpenFromBothTheLabelAndTheSwatch() throws Exception {
        try (var language = PreviewLanguage.install("en_us");
             var surface = Surface.makeRasterN32Premul(360, 820)) {
            SettingsSectionPage.clearTranslationCache();
            var page = new SettingsSectionPage();
            var rows = select(page, SettingsSection.APPEARANCE, "colors");
            var layout = UiLayout.ofDetail(20, 20, 240, 780);
            int index = 1;
            var row = rows.get(index);
            assertEquals(SettingsSectionPage.RowKind.COLOR, row.kind());
            var rect = page.rowRect(SettingsSection.APPEARANCE, rows, index, 0, layout);
            float y = rect.y() + rect.h() / 2;
            float right = rect.right() - UiTokens.SETTINGS_ROW_PAD - UiTokens.s(14);
            var hit = page.colorHit(right, y, layout, SettingsSection.APPEARANCE, 0);
            assertNotNull(hit);
            assertSame(row.color(), hit.color());
            var label = page.colorHit(rect.x() + UiTokens.SETTINGS_ROW_PAD, y, layout, SettingsSection.APPEARANCE, 0);
            assertNotNull(label);
            assertSame(row.color(), label.color());
            var previewLayout = UiLayout.ofDetail(20, 20, 320, 780);
            page.markEntered(1);
            UiStagger.atFrame(1000, () -> page.render(surface.getCanvas(), previewLayout,
                    SettingsSection.APPEARANCE, -1, -1, 0, 0xff4085eb));
            var output = Path.of("build/test-artifacts/settings-colors-narrow.png");
            Files.createDirectories(output.getParent());
            try (var frame = surface.makeImageSnapshot(); var data = frame.encodeToData()) {
                Files.write(output, data.getBytes());
            }
        } finally {
            SettingsSectionPage.clearTranslationCache();
        }
    }

    @Test void privacyPutsTheBlockListAfterSyncAndMessageFiltering() {
        var config = AtomChatConfig.get();
        var original = config.blockedPlayers;
        try {
            config.blockedPlayers = List.of();
            var rows = new SettingsSectionPage().rows(SettingsSection.PRIVACY);
            assertEquals(List.of("atomchat.settings.group.privacy.sync", "atomchat.settings.group.privacy.messages",
                            "atomchat.settings.group.privacy.blocked"),
                    rows.stream().filter(row -> row.labelKey() != null).map(SettingsSectionPage.Row::labelKey).toList());
            assertEquals(SettingsSectionPage.RowKind.BLOCKED_EMPTY, rows.get(rows.size() - 1).kind());
            var page = new SettingsSectionPage();
            var layout = UiLayout.ofDetail(10, 10, 580, 950);
            page.markSettled();
            var empty = page.rowRect(SettingsSection.PRIVACY, rows, rows.size() - 1, 0, layout);
            try (var surface = Surface.makeRasterN32Premul(620, 980);
                 var bitmap = new io.github.humbleui.skija.Bitmap()) {
                bitmap.allocN32Pixels(620, 980);
                page.render(surface.getCanvas(), layout, SettingsSection.PRIVACY, -1, -1, 0, 0xff4085eb);
                assertTrue(surface.readPixels(bitmap, 0, 0));
                assertEquals(0, bitmap.getColor((int) (empty.x() + 20), (int) (empty.y() + empty.h() / 2)) >>> 24);
            }
            config.blockedPlayers = List.of("Example");
            rows = new SettingsSectionPage().rows(SettingsSection.PRIVACY);
            var last = rows.get(rows.size() - 1);
            assertEquals(SettingsSectionPage.RowKind.BLOCKED, last.kind());
            assertEquals("Example", last.player().realName());
        } finally {
            config.blockedPlayers = original;
        }
    }

    @Test void chatHudCountCanBeTypedDirectlyAndRestoredWithoutResettingTheShortcut() {
        var page = new SettingsSectionPage();
        var rows = page.rows(SettingsSection.CHAT);
        var item = rows.stream().filter(row -> row.item() != null && "chat_hud".equals(row.item().id()))
                .findFirst().orElseThrow().item();
        var input = rows.stream().filter(row -> row.slider() != null && "chat_hud_max_messages".equals(row.slider().id()))
                .findFirst().orElseThrow();
        var number = input.slider();
        assertTrue(SettingsSectionPage.isInlineNumberSlider(number.id()));
        assertEquals("chat_hud_timed_exit", rows.get(rows.indexOf(input) + 1).item().id());
        var layout = UiLayout.ofDetail(20, 20, 320, 600);
        var rect = page.rowRect(SettingsSection.CHAT, rows, rows.indexOf(input), 0, layout);
        float x = rect.right() - UiTokens.SETTINGS_ROW_PAD;
        float y = rect.y() + rect.h() / 2F;
        float visibleY = layout.list.y() + page.chipBarHeight(SettingsSection.CHAT) + UiTokens.s(36);
        var visibleHit = page.sliderHit(x, visibleY, layout, SettingsSection.CHAT, y - visibleY);
        assertNotNull(visibleHit);
        assertEquals(number.id(), visibleHit.row().slider().id());
        float fixedTabsY = layout.list.y() + page.chipBarHeight(SettingsSection.CHAT) / 2F;
        assertNull(page.sliderHit(x, fixedTabsY, layout, SettingsSection.CHAT, y - fixedTabsY));
        assertTrue(new AtomChatConfig().chatHudEnabled);
        assertFalse(new AtomChatConfig().chatHudTimedExit);
        assertEquals(5, new AtomChatConfig().effectiveChatHudMaxMessages());
        boolean enabled = item.value();
        float savedLimit = number.value();
        try {
            number.set(5);
            page.beginNumberEdit(number);
            page.appendNumberChar('0');
            page.commitNumberEdit();
            assertEquals(0F, number.value());
            item.set(false);
            assertEquals(0F, number.value());
            page.beginNumberEdit(number);
            page.appendNumberChar('9');
            page.appendNumberChar('9');
            page.commitNumberEdit();
            assertEquals(10F, number.value());
            var reset = new AtomChatConfig();
            reset.chatHudEnabled = false;
            reset.chatHudTimedExit = true;
            reset.chatHudMaxMessages = 10;
            reset.hudIconEnabled = false;
            assertTrue(SettingsTabDefaults.reset(reset, SettingsSection.CHAT, "messages", () -> true));
            assertTrue(reset.chatHudEnabled);
            assertFalse(reset.chatHudTimedExit);
            assertEquals(5, reset.effectiveChatHudMaxMessages());
            assertFalse(reset.hudIconEnabled);
        } finally {
            item.set(enabled);
            number.set(savedLimit);
        }
    }

    private static List<SettingsSectionPage.Row> select(SettingsSectionPage page, SettingsSection section, String id) {
        var chips = page.chips(section);
        for (int i = 0; i < chips.size(); i++) {
            if (id.equals(chips.get(i).id())) {
                page.selectChip(section, i);
                return page.rows(section);
            }
        }
        throw new AssertionError("missing settings tab " + id);
    }
}
