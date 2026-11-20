package com.atom.chat.settings;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.wallpaper.WallpaperStore;
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
    private static String cardBlurDisplay(float requested) {
        var config = AtomChatConfig.get();
        String value = Math.round(requested * 100F) + "%";
        if (config.blurEnabled && !com.atom.chat.wallpaper.WallpaperStore.isSet()
                && requested < config.effectiveBlurStrength()) {
            return value + " → " + Math.round(config.effectiveBlurStrength() * 100F) + "%";
        }
        return value;
    }
    private SettingsCatalog() {
    }

    private static com.atom.chat.screen.PanelSizing.Size panelViewport() {
        var client = net.minecraft.client.Minecraft.getInstance();
        var window = client == null ? null : client.getWindow();
        return new com.atom.chat.screen.PanelSizing.Size(window == null ? 1920 : window.getWidth(),
                window == null ? 1080 : window.getHeight());
    }
    private static com.atom.chat.screen.PanelSizing.Size automaticPanelSize() {
        var view = panelViewport();
        return com.atom.chat.screen.PanelSizing.automaticSize((int)view.width(), (int)view.height(), AtomChatConfig.get());
    }
    private static float panelHeightMaximum() {
        return com.atom.chat.screen.PanelSizing.maximumHeight((int)panelViewport().height(), AtomChatConfig.get());
    }
    private static float panelWidthMaximum() {
        var view = panelViewport();
        return Math.min(Math.max(600F, automaticPanelSize().width()),
                com.atom.chat.screen.PanelSizing.maximumWidth((int)view.width(), (int)view.height(), AtomChatConfig.get()));
    }
    private static float panelWidthValue() {
        var config = AtomChatConfig.get();
        var view = panelViewport();
        return config.panelWidthAutomatic && !config.halfScreenMode ? automaticPanelSize().width()
                : Math.min(config.activePanelWidth(), com.atom.chat.screen.PanelSizing.maximumWidth((int)view.width(), (int)view.height(), config));
    }
    private static float panelHeightValue() {
        return AtomChatConfig.get().panelHeightAutomatic ? automaticPanelSize().height()
                : Math.min(AtomChatConfig.get().panelHeight, panelHeightMaximum());
    }
    private static void materializePanelSize() {
        var view = panelViewport();
        com.atom.chat.screen.PanelSizing.materialize(AtomChatConfig.get(), (int)view.width(), (int)view.height());
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

    private static final SettingsItem HISTORY_SWITCH = new SettingsItem("history",
        "atomchat.settings.chat.history",
        "atomchat.settings.chat.history.desc",
        () -> AtomChatConfig.get().chatHistoryEnabled,
        v -> AtomChatConfig.get().chatHistoryEnabled = v);

    static SettingsItem historySwitch() { return HISTORY_SWITCH; }

    private static final SettingsSlider HISTORY_RETENTION = new SettingsSlider("history_retention",
        "atomchat.settings.chat.history.retention",
        0.00F, 365.0F, 1.0F,
        () -> AtomChatConfig.get().historyRetentionDays,
        v -> AtomChatConfig.get().historyRetentionDays = Math.round(v),
        v -> {
            int days = Math.round(v);
            return days == 0
                    ? Component.translatable("atomchat.settings.chat.history.retention.forever").getString()
                    : Component.translatable("atomchat.settings.chat.history.retention.days", days).getString();
        });

    static SettingsSlider historyRetention() { return HISTORY_RETENTION; }

    public static List<SettingsItem> items(SettingsSection section) {
        return ITEM_CACHE.computeIfAbsent(section, SettingsCatalog::buildItems);
    }

    private static List<SettingsItem> buildItems(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> List.of(
                    new SettingsItem("half_screen",
                            "atomchat.settings.appearance.half_screen",
                            "atomchat.settings.appearance.half_screen.desc",
                            () -> AtomChatConfig.get().halfScreenMode,
                            v -> AtomChatConfig.get().halfScreenMode = v),
                    new SettingsItem("hud_icon",
                            "atomchat.settings.hud.icon", "atomchat.settings.hud.icon.desc",
                            () -> AtomChatConfig.get().hudIconEnabled,
                            v -> AtomChatConfig.get().hudIconEnabled = v),
                    new SettingsItem("blur",
                            "atomchat.settings.appearance.blur",
                            "atomchat.settings.appearance.blur.desc",
                            () -> AtomChatConfig.get().blurEnabled,
                            v -> AtomChatConfig.get().blurEnabled = v,
                            () -> !WallpaperStore.isSet()),
                    new SettingsItem("motion",
                            "atomchat.settings.appearance.motion",
                            "atomchat.settings.appearance.motion.desc",
                            () -> AtomChatConfig.get().animationEnabled,
                            v -> AtomChatConfig.get().animationEnabled = v));
            case CHAT -> List.of(
                    new SettingsItem("enabled", "atomchat.settings.chat.enabled", "atomchat.settings.chat.enabled.desc",
                            () -> AtomChatConfig.get().enabled, v -> AtomChatConfig.get().enabled = v),
                    new SettingsItem("close_after_send",
                            "atomchat.settings.chat.close_after_send",
                            "atomchat.settings.chat.close_after_send.desc",
                            () -> AtomChatConfig.get().closeAfterSend,
                            v -> AtomChatConfig.get().closeAfterSend = v),
                    new SettingsItem("retain_draft_on_close",
                            "atomchat.settings.chat.retain_draft_on_close",
                            "atomchat.settings.chat.retain_draft_on_close.desc",
                            () -> AtomChatConfig.get().retainDraftOnClose,
                            v -> AtomChatConfig.get().retainDraftOnClose = v),
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
                    new SettingsItem("chat_hud",
                            "atomchat.settings.chat.hud.enabled", "atomchat.settings.chat.hud.enabled.desc",
                            () -> AtomChatConfig.get().chatHudEnabled, v -> AtomChatConfig.get().chatHudEnabled = v),
                    new SettingsItem("chat_hud_timed_exit",
                            "atomchat.settings.chat.hud.timed_exit", "atomchat.settings.chat.hud.timed_exit.desc",
                            () -> AtomChatConfig.get().chatHudTimedExit, v -> AtomChatConfig.get().chatHudTimedExit = v),
                    new SettingsItem("mention_banner",
                            "atomchat.settings.chat.mention_banner",
                            "atomchat.settings.chat.mention_banner.desc",
                            () -> AtomChatConfig.get().mentionBannerEnabled,
                            v -> AtomChatConfig.get().mentionBannerEnabled = v),
                    new SettingsItem("whisper_banner",
                            "atomchat.settings.chat.whisper_banner",
                            "atomchat.settings.chat.whisper_banner.desc",
                            () -> AtomChatConfig.get().whisperBannerEnabled,
                            v -> AtomChatConfig.get().whisperBannerEnabled = v),
                    historySwitch(),
                    new SettingsItem("notification", "atomchat.settings.sounds.notification", "atomchat.settings.sounds.notification.desc",
                            () -> AtomChatConfig.get().notificationSoundEnabled, v -> AtomChatConfig.get().notificationSoundEnabled = v),
                    new SettingsItem("mention_sound", "atomchat.settings.sounds.mention_sound", "atomchat.settings.sounds.mention_sound.desc",
                            () -> AtomChatConfig.get().mentionSoundEnabled, v -> AtomChatConfig.get().mentionSoundEnabled = v),
                    new SettingsItem("whisper_sound", "atomchat.settings.sounds.whisper_sound", "atomchat.settings.sounds.whisper_sound.desc",
                            () -> AtomChatConfig.get().whisperSoundEnabled, v -> AtomChatConfig.get().whisperSoundEnabled = v),
                    new SettingsItem("feedback", "atomchat.settings.sounds.feedback", "atomchat.settings.sounds.feedback.desc",
                            () -> AtomChatConfig.get().feedbackSoundEnabled, v -> AtomChatConfig.get().feedbackSoundEnabled = v),
                    new SettingsItem("feedback_button", "atomchat.settings.sounds.feedback_button", "atomchat.settings.sounds.feedback_button.desc",
                            () -> AtomChatConfig.get().feedbackButtonEnabled, v -> AtomChatConfig.get().feedbackButtonEnabled = v),
                    new SettingsItem("feedback_send", "atomchat.settings.sounds.feedback_send", "atomchat.settings.sounds.feedback_send.desc",
                            () -> AtomChatConfig.get().feedbackSendEnabled, v -> AtomChatConfig.get().feedbackSendEnabled = v),
                    new SettingsItem("feedback_panel", "atomchat.settings.sounds.feedback_panel", "atomchat.settings.sounds.feedback_panel.desc",
                            () -> AtomChatConfig.get().feedbackPanelEnabled, v -> AtomChatConfig.get().feedbackPanelEnabled = v),
                    new SettingsItem("fun_effects", "atomchat.settings.experimental.fun", "atomchat.settings.experimental.fun.desc",
                            () -> AtomChatConfig.get().funEffectsEnabled,
                            v -> AtomChatConfig.get().funEffectsEnabled = v),
                    new SettingsItem("experimental_effects", "atomchat.settings.experimental.enabled", "atomchat.settings.experimental.enabled.desc",
                            () -> AtomChatConfig.get().experimentalEffectsEnabled,
                            v -> AtomChatConfig.get().experimentalEffectsEnabled = v));
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
                    new SettingsSlider("outline_width", "atomchat.settings.appearance.outline_width",
                            0F, 6F, 0.25F, () -> AtomChatConfig.get().effectivePanelOutlineWidth(),
                            v -> AtomChatConfig.get().setPanelOutlineWidth(v),
                            v -> String.format(java.util.Locale.ROOT, "%.2f px", v)),
                    new SettingsSlider("blur_strength", "atomchat.settings.appearance.blur_strength",
                            0.25F, 3.0F, 0.05F, () -> AtomChatConfig.get().effectiveBlurStrength(),
                            v -> AtomChatConfig.get().blurStrength = v, v -> Math.round(v * 100.0F) + "%"),
                    new SettingsSlider("card_blur_strength", "atomchat.settings.appearance.card_blur_strength",
                            0F, 3F, 0.05F, () -> AtomChatConfig.get().effectiveCardBlurStrength(),
                            v -> AtomChatConfig.get().cardBlurStrength = v, SettingsCatalog::cardBlurDisplay),
                    new SettingsSlider("opacity",
                            "atomchat.settings.appearance.opacity",
                            0.30F, 1.00F, 0.05F,
                            () -> AtomChatConfig.get().panelOpacity,
                            v -> AtomChatConfig.get().panelOpacity = v,
                            v -> Math.round(v * 100.0F) + "%"),
                    new SettingsSlider("width",
                            "atomchat.settings.appearance.width",
                            () -> Math.min(400F, automaticPanelSize().width()), SettingsCatalog::panelWidthMaximum, 10.0F,
                            SettingsCatalog::panelWidthValue,
                            v -> { if (!AtomChatConfig.get().halfScreenMode) materializePanelSize(); AtomChatConfig.get().setActivePanelWidth(v); },
                            v -> Math.round(panelWidthValue()) + " px"),
                    new SettingsSlider("height",
                            "atomchat.settings.appearance.height",
                            () -> automaticPanelSize().height() * .7F, SettingsCatalog::panelHeightMaximum, 10.0F,
                            SettingsCatalog::panelHeightValue,
                            v -> { materializePanelSize(); AtomChatConfig.get().panelHeight = v; },
                            v -> Math.round(panelHeightValue()) + " px"),
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
                    new SettingsSlider("chat_hud_max_messages", "atomchat.settings.chat.hud.limit",
                            0, AtomChatConfig.CHAT_HUD_MAX_MESSAGES, 1,
                            () -> AtomChatConfig.get().effectiveChatHudMaxMessages(),
                            v -> AtomChatConfig.get().chatHudMaxMessages = Math.round(v),
                            v -> Integer.toString(Math.round(v))),
                    new SettingsSlider("timestamp",
                            "atomchat.settings.chat.timestamp",
                            0.00F, 60.0F, 1.0F,
                            () -> AtomChatConfig.get().timestampIntervalMinutes,
                            v -> AtomChatConfig.get().timestampIntervalMinutes = Math.round(v),
                            v -> Math.round(v) == 0
                                    ? Component.translatable("atomchat.settings.chat.timestamp.off").getString()
                                    : Math.round(v) + " min"),
                    historyRetention(),
                    new SettingsSlider("hud_offset_x", "atomchat.settings.notify.offset_x",
                            -500F, 500F, 1F, () -> AtomChatConfig.get().notificationHudOffsetX,
                            v -> AtomChatConfig.get().notificationHudOffsetX = v, v -> Math.round(v) + " px"),
                    new SettingsSlider("hud_offset_y", "atomchat.settings.notify.offset_y",
                            -500F, 500F, 1F, () -> AtomChatConfig.get().notificationHudOffsetY,
                            v -> AtomChatConfig.get().notificationHudOffsetY = v, v -> Math.round(v) + " px"),
                    new SettingsSlider("notify_volume",
                            "atomchat.settings.chat.notify_volume",
                            0.00F, 1.00F, 0.05F,
                            () -> AtomChatConfig.get().notifyVolume,
                            v -> AtomChatConfig.get().notifyVolume = v,
                            v -> Math.round(v * 100.0F) + "%"),
                    new SettingsSlider("feedback_volume", "atomchat.settings.sounds.feedback_volume",
                            0.00F, 1.00F, 0.05F,
                            () -> AtomChatConfig.get().feedbackVolume,
                            v -> AtomChatConfig.get().feedbackVolume = v,
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
                            () -> AtomChatConfig.get().bubbleTextColor,
                            v -> AtomChatConfig.get().bubbleTextColor = v),
                    new SettingsColor("own_bubble",
                            "atomchat.settings.appearance.ownbubble",
                            "bubble",
                            () -> AtomChatConfig.get().ownBubbleColor,
                            v -> AtomChatConfig.get().ownBubbleColor = v),
                    new SettingsColor("other_bubble_text",
                            "atomchat.settings.appearance.otherbubbletext",
                            "bubble",
                            () -> AtomChatConfig.get().otherBubbleTextColor,
                            v -> AtomChatConfig.get().otherBubbleTextColor = v),
                    new SettingsColor("other_bubble",
                            "atomchat.settings.appearance.otherbubble",
                            "bubble",
                            () -> AtomChatConfig.get().otherBubbleColor,
                            v -> AtomChatConfig.get().otherBubbleColor = v),
                    new SettingsColor("secondary_capsule_bg",
                            "atomchat.settings.appearance.secondarycapsulebg",
                            "bubble",
                            () -> AtomChatConfig.get().secondaryCapsuleBg,
                            v -> AtomChatConfig.get().secondaryCapsuleBg = v),
                    new SettingsColor("secondary_capsule_text",
                            "atomchat.settings.appearance.secondarycapsuletext",
                            "bubble",
                            () -> AtomChatConfig.get().secondaryCapsuleText,
                            v -> AtomChatConfig.get().secondaryCapsuleText = v),
                    new SettingsColor("panel_bg",
                            "atomchat.settings.appearance.panelbg",
                            "ui",
                            () -> AtomChatConfig.get().panelBgColor,
                            v -> AtomChatConfig.get().panelBgColor = v),
                    new SettingsColor("text_primary",
                            "atomchat.settings.appearance.textprimary",
                            "ui",
                            () -> AtomChatConfig.get().textPrimaryColor,
                            v -> AtomChatConfig.get().textPrimaryColor = v),
                    new SettingsColor("text_secondary",
                            "atomchat.settings.appearance.textsecondary",
                            "ui",
                            () -> AtomChatConfig.get().textSecondaryColor,
                            v -> AtomChatConfig.get().textSecondaryColor = v),
                    new SettingsColor("card",
                            "atomchat.settings.appearance.cardcolor",
                            "ui",
                            () -> AtomChatConfig.get().cardColor,
                            v -> AtomChatConfig.get().cardColor = v),
                    new SettingsColor("outline",
                            "atomchat.settings.appearance.outlinecolor",
                            "ui",
                            () -> AtomChatConfig.get().panelOutlineColor,
                            v -> AtomChatConfig.get().panelOutlineColor = v),
                    new SettingsColor("accent",
                            "atomchat.settings.appearance.accent",
                            "ui",
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
                        "https://github.com/NoWordz/AtomChat-Mod/releases"),
                new InfoRow("atomchat.settings.about.license", "MIT",
                        "https://opensource.org/licenses/MIT"),
                new InfoRow("atomchat.settings.about.repo", "NoWordz/AtomChat-Mod",
                        "https://github.com/NoWordz/AtomChat-Mod"));
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
