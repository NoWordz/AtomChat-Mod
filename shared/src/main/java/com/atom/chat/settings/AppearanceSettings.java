package com.atom.chat.settings;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.api.ui.AppearanceUi.Part;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.onboarding.TutorialSandbox;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Shared settings model for the four optional appearance selectors. */
final class AppearanceSettings {
    static final String TITLE_PREFIX = "atomchat.settings.appearance.parts.";
    static final String DEFAULT = TITLE_PREFIX + "default";
    static final String MISSING = TITLE_PREFIX + "missing";
    static final String SAVE_FAILED = TITLE_PREFIX + "save_failed";
    private static final String ACTION_PREFIX = "appearance_part_";

    private AppearanceSettings() {}

    static String action(Part part) {
        return ACTION_PREFIX + suffix(part);
    }

    static String title(Part part) {
        return TITLE_PREFIX + suffix(part);
    }

    private static String suffix(Part part) {
        return part.name().toLowerCase(Locale.ROOT);
    }

    static Part part(String action) {
        if (action == null || !action.startsWith(ACTION_PREFIX)) return null;
        return switch (action.substring(ACTION_PREFIX.length())) {
            case "bubble", "bubble:prev" -> Part.BUBBLE;
            case "panel_outline", "panel_outline:prev" -> Part.PANEL_OUTLINE;
            case "avatar_frame", "avatar_frame:prev" -> Part.AVATAR_FRAME;
            case "dynamic_wallpaper", "dynamic_wallpaper:prev" -> Part.DYNAMIC_WALLPAPER;
            default -> null;
        };
    }

    static boolean visible(Part part, AtomChatConfig config) {
        return !TutorialSandbox.active() && (selectedId(part, config) != null || !AppearanceUi.options(part).isEmpty());
    }

    static String selectedId(Part part, AtomChatConfig config) {
        return switch (part) {
            case BUBBLE -> config.bubbleAppearanceId;
            case PANEL_OUTLINE -> config.panelOutlineAppearanceId;
            case AVATAR_FRAME -> config.avatarFrameAppearanceId;
            case DYNAMIC_WALLPAPER -> config.dynamicWallpaperAppearanceId;
        };
    }

    static void select(Part part, AtomChatConfig config, String id) {
        switch (part) {
            case BUBBLE -> config.bubbleAppearanceId = id;
            case PANEL_OUTLINE -> config.panelOutlineAppearanceId = id;
            case AVATAR_FRAME -> config.avatarFrameAppearanceId = id;
            case DYNAMIC_WALLPAPER -> config.dynamicWallpaperAppearanceId = id;
        }
    }

    static boolean save(Part part, AtomChatConfig config, String id) {
        String previous = selectedId(part, config);
        select(part, config, id);
        boolean saved = false;
        try {
            saved = AtomChatConfig.saveChecked(config);
            return saved;
        } finally {
            if (!saved) select(part, config, previous);
        }
    }

    /** Publication runs on the client thread, which may have reloaded the config since the click. */
    static boolean commitStaticWallpaperFor(String expectedDynamic) {
        AtomChatConfig config = AtomChatConfig.get();
        return Objects.equals(expectedDynamic, config.dynamicWallpaperAppearanceId)
                && save(Part.DYNAMIC_WALLPAPER, config, null);
    }

    static String label(Part part, String selected) {
        if (selected == null) return DEFAULT;
        for (var option : AppearanceUi.options(part)) {
            if (option.id().equals(selected)) return option.translationKey();
        }
        return MISSING;
    }

    static String next(Part part, String selected, int direction) {
        List<AppearanceUi.Option> options = AppearanceUi.options(part);
        int index = 0;
        if (selected != null) {
            index = -1;
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).id().equals(selected)) {
                    index = i + 1;
                    break;
                }
            }
            if (index < 0) return direction > 0 && !options.isEmpty() ? options.get(0).id() : null;
        }
        int next = Math.floorMod(index + direction, options.size() + 1);
        return next == 0 ? null : options.get(next - 1).id();
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
