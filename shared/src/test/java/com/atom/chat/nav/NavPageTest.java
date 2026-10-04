package com.atom.chat.nav;

import com.atom.chat.chat.PlayerRef;
import com.atom.chat.settings.SettingsSection;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NavPageTest {
    @Test
    void privateChatRequiresTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new NavPage(AppPage.PRIVATE_CHAT, null, null));
    }

    @Test
    void nonPrivateForcesNullTarget() {
        NavPage page = new NavPage(AppPage.WORLD_CHAT, PlayerRef.of(UUID.randomUUID(), "Alice"), null);
        assertNull(page.target());
        assertEquals(AppPage.WORLD_CHAT, page.page());
    }

    @Test
    void rootClassification() {
        assertTrue(NavPage.of(AppPage.CHAT_LIST).isRoot());
        assertFalse(NavPage.of(AppPage.WORLD_CHAT).isRoot());
        assertFalse(NavPage.privateChat(PlayerRef.of(UUID.randomUUID(), "Alice")).isRoot());
        assertFalse(NavPage.settingsSection(SettingsSection.CHAT).isRoot());
    }

    @Test
    void settingsSectionCarriesAndRestoresId() {
        NavPage page = NavPage.settingsSection(SettingsSection.PRIVACY);
        assertEquals(SettingsSection.PRIVACY, page.section());
        assertEquals("privacy", page.param());
        assertNull(page.target());
    }

    @Test
    void settingsSectionRequiresParam() {
        assertThrows(IllegalArgumentException.class,
                () -> new NavPage(AppPage.SETTINGS_SECTION, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new NavPage(AppPage.SETTINGS_SECTION, null, "  "));
    }

    @Test
    void nonSettingsForcesNullParam() {
        NavPage page = new NavPage(AppPage.SETTINGS, null, "appearance");
        assertNull(page.param());
        assertNull(page.section());
    }
}
