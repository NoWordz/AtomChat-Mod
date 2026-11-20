package com.atom.chat.settings;

import com.atom.chat.render.DrawPrimitives;
import com.atom.lib.design.atomchat.AppIcons;
import com.atom.chat.ui.SettingsGroupGlyphs;
import com.atom.chat.ui.SettingsIcons;
import io.github.humbleui.skija.Path;
import java.util.Map;
import java.util.Set;

/** Semantic assignments by complete translation key; the atomchat namespace is never a category. */
public final class SettingsGroupIcons {
    private SettingsGroupIcons() { }
    private static final Map<String,Path> ICONS=Map.ofEntries(
        Map.entry("atomchat.settings.group.history.storage",AppIcons.ICON_CLOCK_PATH),
        Map.entry("atomchat.settings.group.history.cleanup",SettingsIcons.TRASH),
        Map.entry("atomchat.settings.group.notify.banner",SettingsGroupGlyphs.BANNER),
        Map.entry("atomchat.settings.sounds.notification",SettingsGroupGlyphs.SPEAKER),
        Map.entry("atomchat.settings.sounds.feedback",SettingsGroupGlyphs.CLICK),
        Map.entry("atomchat.settings.group.notify.mentions",DrawPrimitives.ICON_MENTION_PATH),
        Map.entry("atomchat.settings.group.notify.whispers",SettingsGroupGlyphs.BANNER),
        Map.entry("atomchat.settings.group.notify.audio",SettingsIcons.VOLUME),
        Map.entry("atomchat.settings.group.interaction.feedback",SettingsGroupGlyphs.CLICK),
        Map.entry("atomchat.settings.group.chat.shortcuts",SettingsGroupGlyphs.TOUCH),
        Map.entry("atomchat.settings.group.chat.fun",DrawPrimitives.ICON_EMOJI_PATH),
        Map.entry("atomchat.settings.group.chat.experimental",SettingsGroupGlyphs.EXPERIMENTAL),
        Map.entry("atomchat.settings.group.chat.input",SettingsIcons.KEYBOARD),
        Map.entry("atomchat.settings.group.chat.messages_display",SettingsGroupGlyphs.MESSAGE_LIST),
        Map.entry("atomchat.settings.group.chat.hud",SettingsIcons.DEVICE_DESKTOP),
        Map.entry("atomchat.settings.group.chat.interaction",SettingsGroupGlyphs.TOUCH),
        Map.entry("atomchat.settings.group.chat.content",DrawPrimitives.ICON_IMAGE_PATH),
        Map.entry("atomchat.settings.group.appearance.effects",SettingsGroupGlyphs.SPARKLES),
        Map.entry("atomchat.settings.group.appearance.motion",SettingsGroupGlyphs.MOTION),
        Map.entry("atomchat.settings.group.appearance.wallpaper",SettingsIcons.PHOTO),
        Map.entry("atomchat.settings.group.appearance.layout",SettingsGroupGlyphs.LAYOUT),
        Map.entry("atomchat.settings.group.appearance.panel_layout",SettingsGroupGlyphs.LAYOUT),
        Map.entry("atomchat.settings.group.appearance.hud",SettingsGroupGlyphs.BANNER),
        Map.entry("atomchat.settings.group.appearance.panel_background",SettingsGroupGlyphs.SPARKLES),
        Map.entry("atomchat.settings.group.appearance.cards",SettingsGroupGlyphs.OPACITY),
        Map.entry("atomchat.settings.group.appearance.transparency",SettingsGroupGlyphs.OPACITY),
        Map.entry("atomchat.settings.group.appearance.presets",SettingsGroupGlyphs.PRESETS),
        Map.entry("atomchat.settings.group.appearance.bubbles",AppIcons.ICON_TAB_CHAT_PATH),
        Map.entry("atomchat.settings.group.appearance.corners",SettingsGroupGlyphs.CORNERS),
        Map.entry("atomchat.settings.group.appearance.message_colors",SettingsGroupGlyphs.MESSAGE_COLORS),
        Map.entry("atomchat.settings.group.appearance.ui_colors",SettingsGroupGlyphs.PALETTE),
        Map.entry("atomchat.settings.group.privacy.messages",SettingsIcons.EYE_OFF),
        Map.entry("atomchat.settings.group.privacy.blocked",SettingsIcons.BAN),
        Map.entry("atomchat.settings.group.privacy.sync",SettingsIcons.PLUG_CONNECTED),
        Map.entry("atomchat.settings.about.authors",AppIcons.ICON_TAB_PROFILE_PATH),
        Map.entry("atomchat.settings.about.modinfo",AppIcons.ICON_SETTINGS_ABOUT_PATH),
        Map.entry("atomchat.settings.about.thirdparty.group",SettingsGroupGlyphs.PLUG),
        Map.entry("atomchat.settings.group.about.maintenance",AppIcons.ICON_TAB_SETTINGS_PATH));
    static Set<String> labels(){return ICONS.keySet();}
    public static Path forLabel(String key) {
        return key==null?AppIcons.ICON_TAB_SETTINGS_PATH:ICONS.getOrDefault(key,AppIcons.ICON_TAB_SETTINGS_PATH);
    }
}
