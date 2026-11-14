package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Explicit tab ownership includes folded rows; never infer reset scope from painted rows. */
public final class SettingsTabDefaults {
    private SettingsTabDefaults() { }
    private static final Map<String, List<String>> NAMES = Map.ofEntries(
            Map.entry("APPEARANCE/theme", List.of(
                "accentColor", "ownBubbleColor", "bubbleTextColor",
                "otherBubbleColor", "otherBubbleTextColor", "panelBgColor",
                "textPrimaryColor", "textSecondaryColor", "cardColor",
                "panelOutlineColor", "secondaryCapsuleBg", "secondaryCapsuleText",
                "themeName", "bubbleStyle", "cornerRadius")),
            Map.entry("APPEARANCE/display", List.of(
                "blurEnabled", "blurStrength", "panelOutline",
                "panelOutlineWidth", "animationEnabled", "pageNavStyle")),
            Map.entry("APPEARANCE/adjust", List.of(
                "halfScreenMode", "halfScreenWidth", "halfScreenWidthCustomized",
                "panelWidth", "panelHeight", "panelWidthAutomatic", "panelHeightAutomatic", "panelPositionCustom",
                "panelPositionX", "panelPositionY", "uiScale",
                "contentScale", "panelOpacity", "cardTint")),
            Map.entry("CHAT/messages", List.of(
                "enabled", "closeAfterSend", "retainDraftOnClose",
                "avatarStyle", "imageMessagesEnabled", "antiSpamEnabled",
                "compactMessagesEnabled", "timestampIntervalMinutes", "messageEntryAnimation",
                "avatarPokeEnabled")),
            Map.entry("CHAT/history", List.of(
                "chatHistoryEnabled", "historyRetentionDays")),
            Map.entry("CHAT/notify", List.of(
                "mentionBannerEnabled", "whisperBannerEnabled", "notificationHudPosition",
                "notificationHudOffsetX", "notificationHudOffsetY", "notificationHudScale",
                "notificationSoundEnabled", "mentionSoundEnabled", "whisperSoundEnabled",
                "notifyVolume", "feedbackSoundEnabled", "feedbackButtonEnabled",
                "feedbackSendEnabled", "feedbackPanelEnabled", "feedbackVolume")),
            Map.entry("CHAT/advanced", List.of(
                "teleportCommandMode", "funEffectsEnabled", "experimentalEffectsEnabled")),
            Map.entry("PRIVACY/privacy", List.of(
                "hideBlockedMessages")),
            Map.entry("ABOUT/about", List.of(
                "debug")));
    private static final Map<String, List<Field>> FIELDS = resolveFields();

    private static Map<String, List<Field>> resolveFields() {
        var result = new java.util.HashMap<String, List<Field>>();
        var owned = new java.util.HashSet<String>();
        for (var entry : NAMES.entrySet()) {
            var fields = new java.util.ArrayList<Field>();
            for (String name : entry.getValue()) {
                try {
                    Field field = AtomChatConfig.class.getField(name);
                    Class<?> type = field.getType();
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())
                            || !(type.isPrimitive() || type == String.class || type.isEnum()) || !owned.add(name)) {
                        throw new IllegalStateException("Invalid or duplicate tab-owned field: " + name);
                    }
                    fields.add(field);
                } catch (NoSuchFieldException error) { throw new ExceptionInInitializerError(error); }
            }
            result.put(entry.getKey(), List.copyOf(fields));
        }
        return Map.copyOf(result);
    }
    private static String key(SettingsSection section, String chip) {
        return section.name() + "/" + (chip == null || chip.isEmpty() ? section.id() : chip);
    }
    public static List<String> fieldNames(SettingsSection section, String chip) {
        return NAMES.getOrDefault(key(section, chip), List.of());
    }
    public static boolean reset(AtomChatConfig config, SettingsSection section, String chip, BooleanSupplier save) {
        var fields = FIELDS.get(key(section, chip));
        if (fields == null || config == AtomChatConfig.DEFAULT) return false;
        var before = new java.util.ArrayList<Object>(fields.size());
        boolean committed = false;
        try {
            for (Field field : fields) before.add(field.get(config));
            try {
                for (Field field : fields) field.set(config, field.get(AtomChatConfig.DEFAULT));
                committed = save.getAsBoolean();
                return committed;
            } finally {
                if (!committed) for (int i=0; i<fields.size(); i++) fields.get(i).set(config, before.get(i));
            }
        } catch (IllegalAccessException error) { throw new IllegalStateException("Cannot restore tab settings", error); }
    }
}
