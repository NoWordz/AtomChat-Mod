package com.atom.chat.settings;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.UiTokens;
import com.atom.chat.wallpaper.WallpaperStore;
import io.github.humbleui.skija.Color;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The switch rows of every settings section.
 *
 * <p>Only options that are read live are listed here. An entry whose config
 * field nothing consults would be a switch wired to nothing, which is exactly
 * the false affordance this screen must not ship.</p>
 */
public final class SettingsCatalog {
    private SettingsCatalog() {
    }

    /**
     * The catalog is static configuration, but the UI asks for it several
     * times per frame (render, measure, hit-test, drag). The entries themselves
     * are live — their getters/setters read and write the config on every call —
     * so caching the immutable lists costs nothing and changes nothing.
     */
    private static final java.util.Map<SettingsSection, List<SettingsItem>> ITEM_CACHE =
            new java.util.EnumMap<>(SettingsSection.class);
    private static final java.util.Map<SettingsSection, List<SettingsSlider>> SLIDER_CACHE =
            new java.util.EnumMap<>(SettingsSection.class);
    private static final java.util.Map<SettingsSection, List<SettingsColor>> COLOR_CACHE =
            new java.util.EnumMap<>(SettingsSection.class);

    public static List<SettingsItem> items(SettingsSection section) {
        return ITEM_CACHE.computeIfAbsent(section, SettingsCatalog::buildItems);
    }

    private static List<SettingsItem> buildItems(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> List.of(
                    new SettingsItem("blur",
                            "atomchat.settings.appearance.blur",
                            "atomchat.settings.appearance.blur.desc",
                            () -> AtomChatConfig.get().blurEnabled,
                            v -> AtomChatConfig.get().blurEnabled = v,
                            () -> !WallpaperStore.isSet()),
                    new SettingsItem("outline",
                            "atomchat.settings.appearance.outline",
                            "atomchat.settings.appearance.outline.desc",
                            () -> AtomChatConfig.get().panelOutline,
                            v -> AtomChatConfig.get().panelOutline = v),
                    new SettingsItem("motion",
                            "atomchat.settings.appearance.motion",
                            "atomchat.settings.appearance.motion.desc",
                            () -> AtomChatConfig.get().animationEnabled,
                            v -> AtomChatConfig.get().animationEnabled = v));
            case CHAT -> List.of(
                    new SettingsItem("entry",
                            "atomchat.settings.chat.entry",
                            "atomchat.settings.chat.entry.desc",
                            () -> AtomChatConfig.get().messageEntryAnimation,
                            v -> AtomChatConfig.get().messageEntryAnimation = v),
                    new SettingsItem("poke",
                            "atomchat.settings.chat.poke",
                            "atomchat.settings.chat.poke.desc",
                            () -> AtomChatConfig.get().avatarPokeEnabled,
                            v -> AtomChatConfig.get().avatarPokeEnabled = v),
                    new SettingsItem("images",
                            "atomchat.settings.chat.images",
                            "atomchat.settings.chat.images.desc",
                            () -> AtomChatConfig.get().imageMessagesEnabled,
                            v -> AtomChatConfig.get().imageMessagesEnabled = v),
                    new SettingsItem("anti_spam",
                            "atomchat.settings.chat.anti_spam",
                            "atomchat.settings.chat.anti_spam.desc",
                            () -> AtomChatConfig.get().antiSpamEnabled,
                            v -> AtomChatConfig.get().antiSpamEnabled = v),
                    new SettingsItem("compact_messages",
                            "atomchat.settings.chat.compact_messages",
                            "atomchat.settings.chat.compact_messages.desc",
                            () -> AtomChatConfig.get().compactMessagesEnabled,
                            v -> AtomChatConfig.get().compactMessagesEnabled = v),
                    new SettingsItem("mention_banner",
                            "atomchat.settings.chat.mention_banner",
                            "atomchat.settings.chat.mention_banner.desc",
                            () -> AtomChatConfig.get().mentionBannerEnabled,
                            v -> AtomChatConfig.get().mentionBannerEnabled = v),
                    new SettingsItem("mention_sound",
                            "atomchat.settings.chat.mention_sound",
                            "atomchat.settings.chat.mention_sound.desc",
                            () -> AtomChatConfig.get().mentionSoundEnabled,
                            v -> AtomChatConfig.get().mentionSoundEnabled = v),
                    new SettingsItem("whisper_banner",
                            "atomchat.settings.chat.whisper_banner",
                            "atomchat.settings.chat.whisper_banner.desc",
                            () -> AtomChatConfig.get().whisperBannerEnabled,
                            v -> AtomChatConfig.get().whisperBannerEnabled = v),
                    new SettingsItem("whisper_sound",
                            "atomchat.settings.chat.whisper_sound",
                            "atomchat.settings.chat.whisper_sound.desc",
                            () -> AtomChatConfig.get().whisperSoundEnabled,
                            v -> AtomChatConfig.get().whisperSoundEnabled = v),
                    new SettingsItem("history",
                            "atomchat.settings.chat.history",
                            "atomchat.settings.chat.history.desc",
                            () -> AtomChatConfig.get().chatHistoryEnabled,
                            v -> AtomChatConfig.get().chatHistoryEnabled = v));
            case PRIVACY -> List.of(
                    new SettingsItem("hideBlocked",
                            "atomchat.settings.privacy.hide",
                            "atomchat.settings.privacy.hide.desc",
                            () -> AtomChatConfig.get().hideBlockedMessages,
                            v -> AtomChatConfig.get().hideBlockedMessages = v),
                    new SettingsItem("server_packs",
                            "atomchat.settings.privacy.server_packs",
                            "atomchat.settings.privacy.server_packs.desc",
                            () -> AtomChatConfig.get().serverPacksEnabled,
                            v -> {
                                AtomChatConfig.get().serverPacksEnabled = v;
                                if (!v) {
                                    // Dropping the view (not the folder) keeps the
                                    // panel honest the moment the switch goes off.
                                    com.atom.chat.pack.ServerPackStore.clear();
                                }
                            }));
            case ABOUT -> List.of(
                    new SettingsItem("debug",
                            "atomchat.settings.about.debug",
                            "atomchat.settings.about.debug.desc",
                            () -> AtomChatConfig.get().debug,
                            v -> AtomChatConfig.get().debug = v));
        };
    }

    /**
     * Continuous settings, in display order per section. Rendered as slider
     * rows under the switches. Like the switches, only live-read config fields
     * qualify — a slider wired to nothing would be worse than a missing one.
     */
    public static List<SettingsSlider> sliders(SettingsSection section) {
        return SLIDER_CACHE.computeIfAbsent(section, SettingsCatalog::buildSliders);
    }

    private static List<SettingsSlider> buildSliders(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> List.of(
                    new SettingsSlider("opacity",
                            "atomchat.settings.appearance.opacity",
                            0.30F, 1.00F, 0.05F,
                            () -> AtomChatConfig.get().panelOpacity,
                            v -> AtomChatConfig.get().panelOpacity = v,
                            v -> Math.round(v * 100.0F) + "%"),
                    new SettingsSlider("width",
                            "atomchat.settings.appearance.width",
                            400.0F, 600.0F, 10.0F,
                            () -> AtomChatConfig.get().panelWidth,
                            v -> AtomChatConfig.get().panelWidth = v,
                            v -> String.valueOf(Math.round(v))),
                    new SettingsSlider("scale",
                            "atomchat.settings.appearance.scale",
                            0.75F, 1.50F, 0.05F,
                            () -> AtomChatConfig.get().uiScale,
                            v -> AtomChatConfig.get().uiScale = v,
                            v -> "x" + String.format(java.util.Locale.ROOT, "%.2f", v)),
                    new SettingsSlider("contentscale",
                            "atomchat.settings.appearance.contentscale",
                            0.80F, 1.50F, 0.05F,
                            () -> AtomChatConfig.get().contentScale,
                            v -> AtomChatConfig.get().contentScale = v,
                            v -> Math.round(v * 100.0F) + "%"),
                    new SettingsSlider("cardtint",
                            "atomchat.settings.appearance.cardtint",
                            0.00F, 1.00F, 0.05F,
                            () -> AtomChatConfig.get().cardTint,
                            v -> AtomChatConfig.get().cardTint = v,
                            v -> Math.round(v * 100.0F) + "%"),
                    // Continuous corner radius in 1080p-basis pixels (it
                    // scales with the UI density rather than staying pinned
                    // to physical pixels), 0 = square corners everywhere.
                    // Replaces the old three-step cornerStyle chips; presets
                    // and the factory reset never write it.
                    new SettingsSlider("corner_radius",
                            "atomchat.settings.appearance.corners",
                            0.0F, UiTokens.s(20), 1.0F,
                            () -> AtomChatConfig.get().cornerRadius,
                            v -> AtomChatConfig.get().cornerRadius = v,
                            v -> Math.round(v) + " px"));
            case CHAT -> List.of(
                    new SettingsSlider("timestamp",
                            "atomchat.settings.chat.timestamp",
                            0.00F, 60.0F, 1.0F,
                            () -> AtomChatConfig.get().timestampIntervalMinutes,
                            v -> AtomChatConfig.get().timestampIntervalMinutes = Math.round(v),
                            v -> Math.round(v) == 0
                                    ? Component.translatable("atomchat.settings.chat.timestamp.off").getString()
                                    : Math.round(v) + " min"),
                    new SettingsSlider("history_retention",
                            "atomchat.settings.chat.history.retention",
                            0.00F, 365.0F, 1.0F,
                            () -> AtomChatConfig.get().historyRetentionDays,
                            v -> AtomChatConfig.get().historyRetentionDays = Math.round(v),
                            v -> {
                                int days = Math.round(v);
                                return days == 0
                                        ? Component.translatable("atomchat.settings.chat.history.retention.forever").getString()
                                        : Component.translatable("atomchat.settings.chat.history.retention.days", days).getString();
                            }),
                    new SettingsSlider("notify_volume",
                            "atomchat.settings.chat.notify_volume",
                            0.00F, 1.00F, 0.05F,
                            () -> AtomChatConfig.get().notifyVolume,
                            v -> AtomChatConfig.get().notifyVolume = v,
                            v -> Math.round(v * 100.0F) + "%"));
            default -> List.of();
        };
    }

    /**
     * Colour settings, in display order per section. Rendered as swatch-strip
     * rows. As with switches and sliders, only live-read config fields qualify.
     */
    public static List<SettingsColor> colors(SettingsSection section) {
        return COLOR_CACHE.computeIfAbsent(section, SettingsCatalog::buildColors);
    }

    private static List<SettingsColor> buildColors(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> List.of(
                    new SettingsColor("bubble_text",
                            "atomchat.settings.appearance.bubbletext",
                            "bubble",
                            new int[]{
                                    Color.makeARGB(255, 255, 255, 255),  // white
                                    Color.makeARGB(255, 232, 234, 240),  // pale grey
                                    Color.makeARGB(255, 255, 246, 224),  // warm white
                                    Color.makeARGB(255, 20, 22, 27)},    // near-black
                            () -> AtomChatConfig.get().bubbleTextColor,
                            v -> AtomChatConfig.get().bubbleTextColor = v),
                    new SettingsColor("own_bubble",
                            "atomchat.settings.appearance.ownbubble",
                            "bubble",
                            new int[]{
                                    Color.makeARGB(255, 30, 144, 255),   // e33chat DodgerBlue
                                    Color.makeARGB(255, 26, 188, 156),   // teal
                                    Color.makeARGB(255, 155, 89, 182),   // purple
                                    Color.makeARGB(255, 233, 30, 99)},   // pink
                            () -> AtomChatConfig.get().ownBubbleColor,
                            v -> AtomChatConfig.get().ownBubbleColor = v),
                    new SettingsColor("other_bubble_text",
                            "atomchat.settings.appearance.otherbubbletext",
                            "bubble",
                            new int[]{
                                    Color.makeARGB(255, 255, 255, 255),  // white
                                    Color.makeARGB(255, 232, 234, 240),  // pale grey
                                    Color.makeARGB(255, 255, 246, 224),  // warm white
                                    Color.makeARGB(255, 20, 22, 27)},    // near-black
                            () -> AtomChatConfig.get().otherBubbleTextColor,
                            v -> AtomChatConfig.get().otherBubbleTextColor = v),
                    new SettingsColor("other_bubble",
                            "atomchat.settings.appearance.otherbubble",
                            "bubble",
                            new int[]{
                                    Color.makeARGB(255, 52, 58, 68),     // classic dark
                                    Color.makeARGB(255, 30, 41, 59),     // navy
                                    Color.makeARGB(255, 20, 22, 27),     // near-black
                                    Color.makeARGB(255, 75, 85, 99)},    // light grey
                            () -> AtomChatConfig.get().otherBubbleColor,
                            v -> AtomChatConfig.get().otherBubbleColor = v),
                    new SettingsColor("secondary_capsule_bg",
                            "atomchat.settings.appearance.secondarycapsulebg",
                            "bubble",
                            new int[]{
                                    Color.makeARGB(150, 44, 62, 80),     // shipped default (translucent dark)
                                    Color.makeARGB(150, 255, 255, 255),  // translucent white
                                    Color.makeARGB(150, 20, 22, 27),     // translucent near-black
                                    Color.makeARGB(150, 52, 58, 68)},    // translucent classic dark
                            () -> AtomChatConfig.get().secondaryCapsuleBg,
                            v -> AtomChatConfig.get().secondaryCapsuleBg = v),
                    new SettingsColor("secondary_capsule_text",
                            "atomchat.settings.appearance.secondarycapsuletext",
                            "bubble",
                            new int[]{
                                    Color.makeARGB(255, 220, 170, 186),  // shipped default
                                    Color.makeARGB(255, 255, 255, 255),  // white
                                    Color.makeARGB(255, 232, 234, 240),  // pale grey
                                    Color.makeARGB(255, 20, 22, 27)},    // near-black
                            () -> AtomChatConfig.get().secondaryCapsuleText,
                            v -> AtomChatConfig.get().secondaryCapsuleText = v),
                    new SettingsColor("panel_bg",
                            "atomchat.settings.appearance.panelbg",
                            "ui",
                            new int[]{
                                    0xEE16191F,                          // shipped default (translucent dark slate)
                                    Color.makeARGB(255, 16, 20, 24),     // opaque near-black
                                    Color.makeARGB(255, 245, 247, 250),  // opaque white
                                    Color.makeARGB(238, 255, 255, 255)}, // translucent white
                            () -> AtomChatConfig.get().panelBgColor,
                            v -> AtomChatConfig.get().panelBgColor = v),
                    new SettingsColor("text_primary",
                            "atomchat.settings.appearance.textprimary",
                            "ui",
                            new int[]{
                                    Color.makeARGB(255, 255, 255, 255),  // white
                                    Color.makeARGB(255, 232, 234, 240),  // pale grey
                                    Color.makeARGB(255, 255, 246, 224),  // warm white
                                    Color.makeARGB(255, 20, 22, 27)},    // near-black
                            () -> AtomChatConfig.get().textPrimaryColor,
                            v -> AtomChatConfig.get().textPrimaryColor = v),
                    new SettingsColor("text_secondary",
                            "atomchat.settings.appearance.textsecondary",
                            "ui",
                            new int[]{
                                    Color.makeARGB(255, 220, 170, 186),  // shipped default
                                    Color.makeARGB(255, 232, 234, 240),  // pale grey
                                    Color.makeARGB(255, 255, 246, 224),  // warm white
                                    Color.makeARGB(255, 20, 22, 27)},    // near-black
                            () -> AtomChatConfig.get().textSecondaryColor,
                            v -> AtomChatConfig.get().textSecondaryColor = v),
                    new SettingsColor("card",
                            "atomchat.settings.appearance.cardcolor",
                            "ui",
                            new int[]{
                                    Color.makeARGB(255, 255, 255, 255),  // white (frosted default)
                                    Color.makeARGB(255, 34, 40, 49),     // slate
                                    Color.makeARGB(255, 30, 41, 59),     // navy
                                    Color.makeARGB(255, 20, 22, 27)},    // near-black
                            () -> AtomChatConfig.get().cardColor,
                            v -> AtomChatConfig.get().cardColor = v),
                    new SettingsColor("outline",
                            "atomchat.settings.appearance.outlinecolor",
                            "ui",
                            new int[]{
                                    Color.makeARGB(255, 255, 255, 255),  // white
                                    Color.makeARGB(255, 200, 210, 222),  // silver grey
                                    Color.makeARGB(255, 96, 165, 250),   // link blue
                                    Color.makeARGB(255, 20, 22, 27)},    // near-black
                            () -> AtomChatConfig.get().panelOutlineColor,
                            v -> AtomChatConfig.get().panelOutlineColor = v),
                    new SettingsColor("accent",
                            "atomchat.settings.appearance.accent",
                            "ui",
                            new int[]{
                                    Color.makeARGB(255, 74, 144, 226),   // classic blue
                                    Color.makeARGB(255, 26, 188, 156),   // teal
                                    Color.makeARGB(255, 155, 89, 182),   // purple
                                    Color.makeARGB(255, 233, 30, 99)},   // pink
                            () -> AtomChatConfig.get().accentColor,
                            v -> AtomChatConfig.get().accentColor = v));
            default -> List.of();
        };
    }

    /**
     * Read-only entries for the about page. Kept out of {@link SettingsItem}
     * because they have no config binding at all — they are facts, not options.
     * A non-null {@code uri} renders the value as a link.
     */
    public static List<InfoRow> aboutCoreRows() {
        return List.of(
                new InfoRow("atomchat.settings.about.version", AtomChat.version(),
                        "https://github.com/E33EPUS/AtomChat/releases"),
                new InfoRow("atomchat.settings.about.license", "Apache-2.0",
                        "https://www.apache.org/licenses/LICENSE-2.0"),
                new InfoRow("atomchat.settings.about.repo", "E33EPUS/AtomChat",
                        "https://github.com/E33EPUS/AtomChat"));
    }

    /** Bundled third-party components, each linking to its own project page. */
    public static List<InfoRow> thirdPartyRows() {
        return List.of(
                new InfoRow("atomchat.settings.about.thirdparty", "Skija",
                        "https://github.com/HumbleUI/Skija"),
                new InfoRow("atomchat.settings.about.thirdparty.skia", "Skia",
                        "https://skia.org/"),
                new InfoRow("atomchat.settings.about.thirdparty.flatlaf", "FlatLaf",
                        "https://github.com/JFormDesigner/FlatLaf"));
    }

    public record InfoRow(String titleKey, String value, String uri) {
        public boolean isLink() {
            return uri != null && !uri.isBlank();
        }
    }
}
