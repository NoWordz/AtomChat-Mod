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
            Map.entry("APPEARANCE/theme", List.of("bubbleStyle", "graphicsQuality",
                "bubbleAppearanceId", "panelOutlineAppearanceId", "avatarFrameAppearanceId", "dynamicWallpaperAppearanceId")),
            Map.entry("APPEARANCE/colors", List.of(
                "accentColor", "ownBubbleColor", "bubbleTextColor",
                "otherBubbleColor", "otherBubbleTextColor", "panelBgColor",
                "textPrimaryColor", "textSecondaryColor", "cardColor",
                "panelOutlineColor", "secondaryCapsuleBg", "secondaryCapsuleText")),
            Map.entry("APPEARANCE/material", List.of(
                "blurEnabled", "blurStrength", "cardBlurStrength", "panelOutline",
                "panelOutlineWidth", "cornerRadius", "panelOpacity", "cardTint")),
            Map.entry("APPEARANCE/adjust", List.of(
                "halfScreenMode", "halfScreenWidth", "halfScreenWidthCustomized",
                "panelWidth", "panelHeight", "panelWidthAutomatic", "panelHeightAutomatic", "panelPositionCustom",
                "panelPositionX", "panelPositionY", "uiScale",
                "contentScale", "notificationHudPosition", "notificationHudOffsetX",
                "notificationHudOffsetY", "notificationHudScale", "hudIconEnabled")),
            Map.entry("APPEARANCE/interaction", List.of(
                "animationEnabled", "pageNavStyle", "messageEntryAnimation", "avatarPokeEnabled",
                "teleportCommandMode", "feedbackSoundEnabled", "feedbackButtonEnabled",
                "feedbackSendEnabled", "feedbackPanelEnabled", "feedbackVolume")),
            Map.entry("CHAT/messages", List.of(
                "enabled", "closeAfterSend", "retainDraftOnClose",
                "chatHudEnabled", "chatHudMaxMessages", "chatHudTimedExit",
                "avatarStyle", "imageMessagesEnabled", "antiSpamEnabled",
                "compactMessagesEnabled", "timestampIntervalMinutes")),
            Map.entry("CHAT/history", List.of(
                "chatHistoryEnabled", "historyRetentionDays")),
            Map.entry("CHAT/notify", List.of(
                "mentionBannerEnabled", "whisperBannerEnabled",
                "notificationSoundEnabled", "mentionSoundEnabled", "whisperSoundEnabled",
                "notifyVolume")),
            Map.entry("CHAT/effects", List.of("funEffectsEnabled", "experimentalEffectsEnabled")),
            Map.entry("PRIVACY/privacy", List.of(
                "hideBlockedMessages", "serverPacksEnabled")),
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
        if (config.performanceModeEnabled()) fields = fields.stream().filter(field -> !coveredByPerformanceMode(field)).toList();
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

    private static boolean coveredByPerformanceMode(Field field) {
        return switch (field.getName()) {
            case "blurEnabled", "blurStrength", "cardBlurStrength", "animationEnabled", "pageNavStyle",
                 "messageEntryAnimation", "avatarPokeEnabled", "funEffectsEnabled", "experimentalEffectsEnabled" -> true;
            default -> false;
        };
    }
}
