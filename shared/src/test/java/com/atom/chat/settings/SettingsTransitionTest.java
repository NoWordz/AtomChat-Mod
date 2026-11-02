package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.UiStagger;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A chip switch is a navigation-grade arrival now: the row set swaps in one
 * frame and the page entrance replays on the same per-row stagger, so there is
 * no second crossfade clock to interrogate. What this pins is that the switch
 * really re-arms the entrance, and that with decorative motion off it reads
 * settled (the rows swap instantly).
 */
class SettingsTransitionTest {
    @Test void aChipSwitchReplaysThePageEntrance() throws Exception {
        Field configField = AtomChatConfig.class.getDeclaredField("instance");
        configField.setAccessible(true);
        Object old = configField.get(null);
        AtomChatConfig config = new AtomChatConfig();
        configField.set(null, config);
        try {
            SettingsSectionPage page = new SettingsSectionPage();
            UiStagger.Entrance entrance = entranceOf(page);
            long before = System.currentTimeMillis();
            assertTrue(UiStagger.itemAlpha(entrance, 0, before) >= 1.0F,
                    "a fresh page is settled");

            page.selectChip(SettingsSection.APPEARANCE, 1);
            long after = System.currentTimeMillis();
            assertTrue(entrance.armed(), "a chip switch re-arms the entrance");
            assertEquals(0.0F, UiStagger.itemAlpha(entrance, 0, after), 0.02F,
                    "the rows start transparent, then rise in");

            config.animationEnabled = false;
            assertEquals(1.0F, UiStagger.itemAlpha(entrance, 0, after), 0.0F,
                    "with decorative motion off the rows are already in place");
        } finally {
            configField.set(null, old);
        }
    }

    @Test void switchingToTheActiveChipDoesNotReplay() throws Exception {
        SettingsSectionPage page = new SettingsSectionPage();
        page.selectChip(SettingsSection.APPEARANCE, 0);
        UiStagger.Entrance entrance = entranceOf(page);
        entrance.markSettled();
        page.selectChip(SettingsSection.APPEARANCE, 0);
        assertFalse(entrance.armed(), "tapping the active chip is a no-op");
    }

    private static UiStagger.Entrance entranceOf(SettingsSectionPage page) throws Exception {
        Field field = SettingsSectionPage.class.getDeclaredField("entrance");
        field.setAccessible(true);
        return (UiStagger.Entrance) field.get(page);
    }
}
