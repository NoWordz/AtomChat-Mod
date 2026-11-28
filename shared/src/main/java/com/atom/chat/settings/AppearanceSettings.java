package com.atom.chat.settings;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.api.ui.AppearanceUi.Part;
import com.atom.chat.config.AtomChatConfig;

import java.util.Objects;

/** Shared settings model for add-on appearance choices. */
final class AppearanceSettings {
    static final String SAVE_FAILED = "atomchat.settings.appearance.parts.save_failed";

    private AppearanceSettings() {}

    /** Publication runs on the client thread, which may have reloaded the config since the click. */
    static boolean commitStaticWallpaperFor(String expectedDynamic) {
        AtomChatConfig config = AtomChatConfig.get();
        return Objects.equals(expectedDynamic, config.dynamicWallpaperAppearanceId)
                && (expectedDynamic == null || AppearanceUi.select(Part.DYNAMIC_WALLPAPER, null));
    }

    static String label(Part part, String selected) {
        if (selected == null) return null;
        for (var option : AppearanceUi.options(part)) {
            if (option.id().equals(selected)) return option.translationKey();
        }
        return null;
    }

    record Selection(String bubble, String panel, String avatar, String wallpaper) {
        static Selection capture(AtomChatConfig config) {
            return new Selection(config.bubbleAppearanceId, config.panelOutlineAppearanceId,
                    config.avatarFrameAppearanceId, config.dynamicWallpaperAppearanceId);
        }

        boolean matches(AtomChatConfig config) {
            return Objects.equals(bubble, config.bubbleAppearanceId)
                    && Objects.equals(panel, config.panelOutlineAppearanceId)
                    && Objects.equals(avatar, config.avatarFrameAppearanceId)
                    && Objects.equals(wallpaper, config.dynamicWallpaperAppearanceId);
        }
    }
}
