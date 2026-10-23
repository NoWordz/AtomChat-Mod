package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTransitionTest {
    @Test void chipInputLockUsesTheSameClockAndMotionSwitchAsTheCrossfade() throws Exception {
        Field configField = AtomChatConfig.class.getDeclaredField("instance"); configField.setAccessible(true);
        Object old = configField.get(null);
        AtomChatConfig config = new AtomChatConfig(); configField.set(null, config);
        try {
            SettingsSectionPage page = new SettingsSectionPage();
            Method active = SettingsSectionPage.class.getMethod("chipTransitionActive");
            assertFalse((boolean) active.invoke(page));
            page.selectChip(SettingsSection.APPEARANCE, 1);
            assertTrue((boolean) active.invoke(page));
            config.animationEnabled = false;
            assertFalse((boolean) active.invoke(page));
            config.animationEnabled = true;
            Field started = SettingsSectionPage.class.getDeclaredField("chipSwitchAtMs"); started.setAccessible(true);
            started.setLong(page, System.currentTimeMillis() - 1000);
            assertFalse((boolean) active.invoke(page));
        } finally { configField.set(null, old); }
    }
}
