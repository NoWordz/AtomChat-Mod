package com.atom.chat.nav;

import com.atom.chat.chat.PlayerRef;
import com.atom.chat.settings.SettingsSection;

import java.util.Objects;

/**
 * A navigation entry: the concrete page plus the argument that page needs.
 * Root pages have no argument; WORLD_CHAT is the public channel; PRIVATE_CHAT
 * carries the partner (uuid + real name) it belongs to; SETTINGS_SECTION
 * carries the settings sub-page id.
 */
public record NavPage(AppPage page, PlayerRef target, String param) {
    public NavPage {
        Objects.requireNonNull(page, "page");
        if (page == AppPage.PRIVATE_CHAT && target == null) {
            throw new IllegalArgumentException("PRIVATE_CHAT requires a PlayerRef target");
        }
        if (page != AppPage.PRIVATE_CHAT) {
            target = null;
        }
        if (page == AppPage.SETTINGS_SECTION && (param == null || param.isBlank())) {
            throw new IllegalArgumentException("SETTINGS_SECTION requires a param");
        }
        if (page.isAddon() && (param == null || param.isBlank())) {
            throw new IllegalArgumentException("Add-on pages require a registered route ID");
        }
        if (page != AppPage.SETTINGS_SECTION && !page.isAddon()) {
            param = null;
        }
    }

    public static NavPage of(AppPage page) {
        return new NavPage(page, null, null);
    }

    public static NavPage privateChat(PlayerRef target) {
        return new NavPage(AppPage.PRIVATE_CHAT, target, null);
    }

    public static NavPage settingsSection(SettingsSection section) {
        return new NavPage(AppPage.SETTINGS_SECTION, null, section.id());
    }

    public static NavPage addonRoot(String id) {
        return new NavPage(AppPage.ADDON_ROOT, null, id);
    }

    public static NavPage addonDetail(String id) {
        return new NavPage(AppPage.ADDON_DETAIL, null, id);
    }

    public boolean available() {
        return !page.isAddon() || com.atom.chat.api.ui.NavigationUi.page(param) != null;
    }

    /** The settings sub-page this entry points at, or null for any other page. */
    public SettingsSection section() {
        return page == AppPage.SETTINGS_SECTION ? SettingsSection.fromId(param) : null;
    }

    public boolean isRoot() {
        return page.isRoot();
    }
}
