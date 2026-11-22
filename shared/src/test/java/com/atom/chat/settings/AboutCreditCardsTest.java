package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AboutCreditCardsTest {
    @Test
    void ordinaryWheelScrollsLinksAndOnlyLogoBoundsAreClickable(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        var config = AtomChatConfig.get();
        boolean previous = config.performanceMode;
        config.performanceMode = true;
        try {
            var page = new SettingsSectionPage();
            var layout = UiLayout.ofDetail(0, 0, 240, 1600);
            var about = SettingsSection.ABOUT;
            var rows = page.rows(about);
            int index = 0;
            while (rows.get(index).kind() != SettingsSectionPage.RowKind.CREDITS) index++;
            assertEquals("atomchat.settings.group.about.maintenance", rows.get(index + 1).labelKey());
            assertEquals(AboutCreditCards.ROW_HEIGHT, page.rowHeight(rows.get(index), layout));
            var row = page.rowRect(about, rows, index, 0F, layout);
            float firstX = row.x() + UiTokens.SETTINGS_ROW_PAD + AboutCreditCards.WIDTH / 2;
            float y = row.y() + AboutCreditCards.TOP + AboutCreditCards.LOGO / 2;
            assertEquals("https://github.com/HumbleUI/Skija", page.hit(firstX, y, layout, about, 0F).row().info().uri());
            assertNull(page.hit(firstX, y + AboutCreditCards.LOGO, layout, about, 0F));
            assertNull(page.hit(row.x() + UiTokens.SETTINGS_ROW_PAD, y, layout, about, 0F));
            assertTrue(page.scrollCardStrip(firstX, y, layout, about, 0F, 0F, -20F));
            float lastX = row.right() - UiTokens.SETTINGS_ROW_PAD - AboutCreditCards.WIDTH / 2;
            assertEquals("https://github.com/btzy/nativefiledialog-extended",
                    page.hit(lastX, y, layout, about, 0F).row().info().uri());
            assertNull(page.hit(row.right() + 1, y, layout, about, 0F));
            assertTrue(page.scrollCardStrip(lastX, y, layout, about, 0F, 20F, 0F));
            assertEquals("Skija", page.hit(firstX, y, layout, about, 0F).row().info().value());

            var header = page.rowRect(about, rows, index - 1, 0F, layout);
            assertTrue(page.perform(page.hit(firstX, header.y() + header.h() / 2, layout, about, 0F)));
            assertFalse(page.themesUnderPointer(firstX, y, layout, about, 0F));
        } finally {
            config.performanceMode = previous;
        }
    }
}
