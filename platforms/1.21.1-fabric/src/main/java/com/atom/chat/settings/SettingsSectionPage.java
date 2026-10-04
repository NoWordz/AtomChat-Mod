package com.atom.chat.settings;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.ToggleSwitch;
import com.atom.chat.wallpaper.WallpaperStore;

import java.nio.file.Path;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.SamplingMode;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Body of one settings section: a vertical list of cards in the same visual
 * language as the conversation list — switch rows, slider rows, read-only info
 * rows (optionally linking out) and the blocked-player list.
 *
 * <p>Row geometry comes from {@link #rows(SettingsSection)} for order and
 * {@link #rowRect(List, int, float, UiLayout)} for position, so rendering,
 * hit-testing and measurement can never disagree.</p>
 */
public final class SettingsSectionPage {
    public enum RowKind { HERO, SWITCH, SLIDER, COLOR, INFO, BLOCKED, LABEL, ACTION, THEMES }

    public record Row(RowKind kind, SettingsItem item, SettingsSlider slider,
                      SettingsColor color, SettingsCatalog.InfoRow info, PlayerRef player,
                      String labelKey, String actionId) {
        static Row ofSwitch(SettingsItem item) {
            return new Row(RowKind.SWITCH, item, null, null, null, null, null, null);
        }

        static Row ofSlider(SettingsSlider slider) {
            return new Row(RowKind.SLIDER, null, slider, null, null, null, null, null);
        }

        static Row ofColor(SettingsColor color) {
            return new Row(RowKind.COLOR, null, null, color, null, null, null, null);
        }

        static Row ofInfo(SettingsCatalog.InfoRow info) {
            return new Row(RowKind.INFO, null, null, null, info, null, null, null);
        }

        static Row ofBlocked(PlayerRef player) {
            return new Row(RowKind.BLOCKED, null, null, null, null, player, null, null);
        }

        static Row ofLabel(String key) {
            return new Row(RowKind.LABEL, null, null, null, null, null, key, null);
        }

        static Row ofHero() {
            return new Row(RowKind.HERO, null, null, null, null, null, null, null);
        }

        static Row ofAction(String actionId, SettingsItem item) {
            return new Row(RowKind.ACTION, item, null, null, null, null, null, actionId);
        }

        static Row ofThemes() {
            return new Row(RowKind.THEMES, null, null, null, null, null, null, null);
        }
    }

    /**
     * @param actionX left edge of the clickable action: the whole row for a
     *                switch or a link, the unblock button for a blocked player.
     */
    public record RowHit(Row row, int index, float x, float y, float w, float h, float actionX) {
        public boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        public boolean onAction(float px, float py) {
            // LABEL rows pass too: foldable colour groups toggle on click,
            // plain labels no-op inside perform().
            return contains(px, py) && px >= actionX;
        }
    }

    /** A slider row plus its track rect, in the same geometry the renderer uses. */
    public record SliderHit(Row row, int index, UiLayout.Rect rowRect, UiLayout.Rect track) {
        public boolean contains(float px, float py) {
            return px >= rowRect.x() && px <= rowRect.right()
                    && py >= rowRect.y() && py <= rowRect.bottom();
        }

        /** Whether the press landed on (or near) the knob, i.e. a drag not a nudge. */
        public boolean onKnob(float px, float py, float normalized) {
            float knobX = track.x() + track.w() * normalized;
            float r = UiTokens.SLIDER_KNOB / 2.0F + UiTokens.s(12);
            return px >= knobX - r && px <= knobX + r
                    && py >= track.y() - r && py <= track.bottom() + r;
        }
    }

    private static final int LINK_COLOR = Color.makeARGB(255, 96, 165, 250);
    private static final String LABEL_BLOCKED = "atomchat.settings.privacy.list";
    private static final String LABEL_THIRD_PARTY = "atomchat.settings.about.thirdparty.group";
    private static final String LABEL_MOD_INFO = "atomchat.settings.about.modinfo";
    private static final String LABEL_ADVANCED = "atomchat.settings.group.advanced";
    private static final String LABEL_APPEARANCE_ADVANCED = "atomchat.settings.group.appearance.advanced";
    private static final String LABEL_ADJUST = "atomchat.settings.group.adjust";
    private static final String LABEL_CHAT_MESSAGES = "atomchat.settings.group.chat.messages";
    private static final String LABEL_CHAT_HISTORY = "atomchat.settings.group.chat.history";
    private static final String LABEL_CHAT_NOTIFY = "atomchat.settings.group.chat.notify";
    private static final String LABEL_CHAT_TELEPORT = "atomchat.settings.group.chat.teleport";
    private static final String ACTION_WALLPAPER_PICK = "wallpaper_pick";
    private static final String ACTION_WALLPAPER_CLEAR = "wallpaper_clear";
    private static final String ACTION_TELEPORT_MODE = "teleport_mode";
    private static final String ACTION_HISTORY_CLEAR = "history_clear";
    private static final String ACTION_CACHE_CLEAR = "cache_clear";
    private static final String ACTION_TEST_SOUND = "test_sound";

    /** Per-section heading for the leading switch/action group. */
    private static String groupKey(SettingsSection section) {
        return switch (section) {
            case APPEARANCE, PRIVACY -> "atomchat.settings.group.display";
            case CHAT -> "atomchat.settings.group.behaviour";
            case ABOUT -> LABEL_MOD_INFO;
        };
    }

    /** "Custom wallpaper" card: picking an image copies it into the config dir. */
    private SettingsItem wallpaperPickItem() {
        return new SettingsItem("wallpaper_pick",
                "atomchat.settings.appearance.wallpaper",
                "atomchat.settings.appearance.wallpaper.desc",
                () -> true, v -> {
        });
    }

    /** "Clear wallpaper" card: only offered while a wallpaper is actually set. */
    private SettingsItem wallpaperClearItem() {
        return new SettingsItem("wallpaper_clear",
                "atomchat.settings.appearance.wallpaper.clear",
                "atomchat.settings.appearance.wallpaper.clear.desc",
                () -> true, v -> {
        });
    }

    /** "Test sound" card: fires the notification cue right away, no message needed. */
    private SettingsItem testSoundItem() {
        return new SettingsItem("test_sound",
                "atomchat.settings.chat.test_sound",
                "atomchat.settings.chat.test_sound.desc",
                () -> true, v -> {
        });
    }

    /** Teleport command card: subtitle shows the current auto/tp/tpa mode. */
    private SettingsItem teleportModeItem() {
        return new SettingsItem("teleport_mode",
                "atomchat.settings.chat.teleport",
                "atomchat.settings.chat.teleport.desc",
                () -> true, v -> {
        });
    }

    /**
     * "Clear chat history" card. Always offered: with persistence off it still
     * wipes the current session's bubbles, with persistence on it also deletes
     * the world's saved file.
     */
    private SettingsItem historyClearItem() {
        return new SettingsItem("history_clear",
                "atomchat.settings.chat.history.clear",
                "atomchat.settings.chat.history.clear.desc",
                () -> true, v -> {
        });
    }

    /** "Clear image cache" card on the About page. */
    private SettingsItem cacheClearItem() {
        return new SettingsItem("cache_clear",
                "atomchat.settings.about.cache.clear",
                "atomchat.settings.about.cache.clear.desc",
                () -> true, v -> {
        });
    }

    /** Two-step confirm for destructive actions: first tap arms a red
     *  "确定清除？", second tap within the window really fires. Guards against
     *  accidental wipes; any click that is not the armed button disarms. */
    private static final long CONFIRM_ARM_MS = 3000L;
    private String armedActionId;
    private long armedActionAt;
    /** Collapsed colour groups; session-only. The appearance "advanced" group
     *  starts collapsed by design: theme + everyday knobs on top, the twelve
     *  colour rows tucked away until asked for. */
    private final java.util.Set<String> collapsedColorGroups =
            new java.util.HashSet<>(java.util.List.of("appearance_advanced"));
    /** Dot under the pointer when the theme row was hit; hit() writes it,
     *  perform() consumes it (same click, so no staleness window). */
    private int pressedThemeDot = -1;

    /** Whether the given destructive action is showing its red confirm state. */
    public boolean actionArmed(String actionId) {
        return actionId != null && actionId.equals(armedActionId)
                && System.currentTimeMillis() - armedActionAt <= CONFIRM_ARM_MS;
    }

    /** Cancels any armed confirmation (click-outside rule). */
    public void disarmAction() {
        armedActionId = null;
    }

    /** Destructive actions that must pass through the two-step confirm. */
    private static boolean needsConfirm(String actionId) {
        return ACTION_WALLPAPER_CLEAR.equals(actionId)
                || ACTION_HISTORY_CLEAR.equals(actionId)
                || ACTION_CACHE_CLEAR.equals(actionId);
    }

    /**
     * Foldable group id for a label row, or null for a plain non-collapsible
     * label. Any label returning a non-null id gets a chevron and hides its
     * child rows when collapsed.
     */
    private static String foldableGroup(String labelKey) {
        if (LABEL_ADJUST.equals(labelKey)) {
            return "adjust";
        }
        if (LABEL_ADVANCED.equals(labelKey)) {
            return "advanced";
        }
        if (LABEL_APPEARANCE_ADVANCED.equals(labelKey)) {
            return "appearance_advanced";
        }
        if (LABEL_BLOCKED.equals(labelKey)) {
            return "blocked";
        }
        if (LABEL_MOD_INFO.equals(labelKey)) {
            return "modinfo";
        }
        if (LABEL_THIRD_PARTY.equals(labelKey)) {
            return "thirdparty";
        }
        if ("atomchat.settings.group.display".equals(labelKey)) {
            return "display";
        }
        if (LABEL_CHAT_MESSAGES.equals(labelKey)) {
            return "chat_messages";
        }
        if (LABEL_CHAT_HISTORY.equals(labelKey)) {
            return "chat_history";
        }
        if (LABEL_CHAT_TELEPORT.equals(labelKey)) {
            return "chat_teleport";
        }
        if (LABEL_CHAT_NOTIFY.equals(labelKey)) {
            return "chat_notify";
        }
        return null;
    }

    /**
     * Theme picker row: seven dots (frosted + six colour presets) drawn in a
     * horizontal strip, each filled with its preset accent; the active one
     * gets a ring. Replaces the old parked theme_cycle action card.
     */
    private static final int THEME_DOT_COUNT = 1 + ThemeService.presets().length;

    private final Map<String, ToggleSwitch> switches = new HashMap<>();
    private final Map<Integer, Float> rowHover = new HashMap<>();
    private String draggingSliderId;
    private int activeSliderIndex = -1;
    private float dragValue;
    /** Inline numeric editor state (used by the retention days row). */
    private String editingSliderId;
    private SettingsSlider editingSlider;
    private String editBuffer = "";
    /** Last drawn geometry of the inline number row, for click-to-blur hit tests. */
    private UiLayout.Rect inlineNumberRowRect;
    /** Section of the frame currently being rendered; drag release needs it. */
    private SettingsSection currentSectionForSettle;
    /** Release glide: from the continuous drag position to the snapped value. */
    private String settleId;
    private float settleFrom;
    private float settleTo;
    private long settleStart;
    private int hoveredIndex = -1;
    private long lastFrameMs = System.currentTimeMillis();

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /** Title/value/verb/icon colour — follows the interface text colour setting. */
    private static int textPrimary() {
        return AtomChatConfig.get().textPrimaryColor;
    }

    /** Subtitle/divider/muted colour at a given alpha over the secondary text colour. */
    private static int sec(int alpha) {
        int c = AtomChatConfig.get().textSecondaryColor;
        return Color.makeARGB(alpha, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", kb);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", kb / 1024.0);
    }

    public static float rowHeight(RowKind kind) {
        return switch (kind) {
            case LABEL -> UiTokens.SETTINGS_LABEL_H;
            case SLIDER, COLOR, THEMES -> UiTokens.SETTINGS_SLIDER_ROW_H;
            case HERO -> UiTokens.SETTINGS_HERO_H;
            default -> UiTokens.SETTINGS_ROW_H;
        };
    }

    /**
     * Dynamic row height: switch/action/info descriptions may wrap to a second
     * line instead of being ellipsized. The row grows only when the text does
     * not fit the width the right-hand control/verb/hint leaves available.
     */
    public float rowHeight(Row row, UiLayout layout) {
        RowKind kind = row.kind();
        float base = rowHeight(kind);
        if (kind != RowKind.SWITCH && kind != RowKind.ACTION && kind != RowKind.INFO) {
            return base;
        }
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        String text = descriptionText(row);
        float maxWidth = descriptionMaxWidth(row, layout.list.w());
        if (text.isEmpty() || maxWidth <= 0.0F
                || SkiaFontRenderer.getStringWidth(subFont, text) <= maxWidth) {
            return base;
        }
        List<String> lines = SkiaFontRenderer.wrap(subFont, text, maxWidth);
        return base + Math.max(0, lines.size() - 1) * descriptionLineHeight(subFont);
    }

    /** Copy for an action card, shared by layout measurement and drawing. */
    private record ActionCopy(String title, String subtitle, String verb,
                              boolean available, boolean redConfirm) {
    }

    private ActionCopy actionCopy(Row row) {
        SettingsItem item = row.item();
        boolean available = item.available();
        boolean redConfirm = needsConfirm(row.actionId()) && actionArmed(row.actionId());
        String subtitle;
        String verb;
        if (!available) {
            subtitle = tr(item.subtitleKey() + ".unavailable");
            verb = "";
        } else if (redConfirm) {
            subtitle = tr(item.subtitleKey());
            verb = tr("atomchat.settings.appearance.wallpaper.clear.confirm");
        } else if (ACTION_WALLPAPER_PICK.equals(row.actionId())) {
            Path wallpaper = WallpaperStore.current();
            subtitle = wallpaper != null && wallpaper.getFileName() != null
                    ? wallpaper.getFileName().toString()
                    : tr(item.subtitleKey());
            verb = tr("atomchat.settings.action.choose");
        } else if (ACTION_TELEPORT_MODE.equals(row.actionId())) {
            String mode = AtomChatConfig.get().teleportCommandMode;
            subtitle = tr("atomchat.settings.chat.teleport." + (mode == null ? "auto" : mode));
            verb = tr("atomchat.settings.action.cycle");
        } else if (ACTION_HISTORY_CLEAR.equals(row.actionId())) {
            subtitle = tr(AtomChatConfig.get().chatHistoryEnabled
                    ? "atomchat.settings.chat.history.clear.desc.saved"
                    : "atomchat.settings.chat.history.clear.desc.memory");
            verb = tr("atomchat.settings.action.clear");
        } else if (ACTION_CACHE_CLEAR.equals(row.actionId())) {
            long bytes = ImageLoader.get().diskCacheBytes();
            subtitle = humanBytes(bytes) + " · " + tr(item.subtitleKey());
            verb = tr("atomchat.settings.action.clear");
        } else if (ACTION_TEST_SOUND.equals(row.actionId())) {
            subtitle = tr(item.subtitleKey());
            verb = tr("atomchat.settings.action.play");
        } else {
            subtitle = tr(item.subtitleKey());
            verb = tr("atomchat.settings.action.clear");
        }
        return new ActionCopy(tr(item.titleKey()), subtitle, verb, available, redConfirm);
    }

    /** The descriptive line that may wrap (switch subtitle / action subtitle / info value). */
    private String descriptionText(Row row) {
        return switch (row.kind()) {
            case SWITCH -> {
                SettingsItem item = row.item();
                String key = item.available()
                        ? item.subtitleKey() : item.subtitleKey() + ".unavailable";
                yield tr(key);
            }
            case ACTION -> actionCopy(row).subtitle();
            case INFO -> row.info().value();
            default -> "";
        };
    }

    /** Width available for the descriptive line after reserving right-side UI. */
    private float descriptionMaxWidth(Row row, float rowW) {
        float padX = UiTokens.SETTINGS_ROW_PAD;
        return switch (row.kind()) {
            case SWITCH -> {
                float switchX = rowW - padX - UiTokens.SWITCH_W;
                yield Math.max(0.0F, switchX - padX - s(10));
            }
            case ACTION -> {
                ActionCopy copy = actionCopy(row);
                float reserved = 0.0F;
                if (copy.available()) {
                    Font verbFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
                    reserved = SkiaFontRenderer.getStringWidth(verbFont, copy.verb()) + s(12);
                }
                yield Math.max(0.0F, rowW - padX * 2.0F - reserved);
            }
            case INFO -> {
                float reserved = 0.0F;
                if (row.info().isLink()) {
                    Font hintFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
                    reserved = SkiaFontRenderer.getStringWidth(hintFont,
                            tr("atomchat.settings.about.open")) + s(12);
                }
                yield Math.max(0.0F, rowW - padX * 2.0F - reserved);
            }
            default -> 0.0F;
        };
    }

    private static float descriptionLineHeight(Font font) {
        return SkiaFontRenderer.getHeight(font);
    }

    /** Rows in display order for the given section. */
    public List<Row> rows(SettingsSection section) {
        return switch (section) {
            case CHAT -> chatRows();
            case APPEARANCE -> appearanceRows();
            case PRIVACY -> privacyRows();
            case ABOUT -> aboutRows();
        };
    }

    /**
     * Adds a collapsible group: the label is always present; the children are
     * only added while the group is expanded. Non-foldable labels (null id)
     * always show their children.
     */
    private void addGroup(List<Row> rows, String labelKey, Runnable children) {
        rows.add(Row.ofLabel(labelKey));
        String id = foldableGroup(labelKey);
        if (id == null || !collapsedColorGroups.contains(id)) {
            children.run();
        }
    }

    private void addSwitches(List<Row> rows, SettingsSection section, String... ids) {
        List<SettingsItem> all = SettingsCatalog.items(section);
        java.util.Set<String> wanted = java.util.Set.of(ids);
        for (SettingsItem item : all) {
            if (wanted.contains(item.id())) {
                rows.add(Row.ofSwitch(item));
            }
        }
    }

    private void addSliders(List<Row> rows, SettingsSection section, String... ids) {
        List<SettingsSlider> all = SettingsCatalog.sliders(section);
        java.util.Set<String> wanted = java.util.Set.of(ids);
        for (SettingsSlider slider : all) {
            if (wanted.contains(slider.id())) {
                rows.add(Row.ofSlider(slider));
            }
        }
    }

    private List<Row> chatRows() {
        List<Row> rows = new ArrayList<>();
        addGroup(rows, LABEL_CHAT_MESSAGES, () -> {
            addSwitches(rows, SettingsSection.CHAT,
                    "entry", "poke", "images", "anti_spam", "compact_messages");
            addSliders(rows, SettingsSection.CHAT, "timestamp");
        });
        addGroup(rows, LABEL_CHAT_HISTORY, () -> {
            addSwitches(rows, SettingsSection.CHAT, "history");
            addSliders(rows, SettingsSection.CHAT, "history_retention");
            rows.add(Row.ofAction(ACTION_HISTORY_CLEAR, historyClearItem()));
        });
        addGroup(rows, LABEL_CHAT_NOTIFY, () -> {
            addSwitches(rows, SettingsSection.CHAT,
                    "mention_banner", "mention_sound", "whisper_banner", "whisper_sound");
            addSliders(rows, SettingsSection.CHAT, "notify_volume");
            rows.add(Row.ofAction(ACTION_TEST_SOUND, testSoundItem()));
        });
        addGroup(rows, LABEL_CHAT_TELEPORT, () ->
                rows.add(Row.ofAction(ACTION_TELEPORT_MODE, teleportModeItem())));
        return rows;
    }

    private List<Row> appearanceRows() {
        List<Row> rows = new ArrayList<>();
        // Theme strip first — one tap to a whole new look.
        rows.add(Row.ofThemes());
        addGroup(rows, groupKey(SettingsSection.APPEARANCE), () -> {
            addSwitches(rows, SettingsSection.APPEARANCE, "blur", "outline", "motion");
            rows.add(Row.ofAction(ACTION_WALLPAPER_PICK, wallpaperPickItem()));
            if (WallpaperStore.isSet()) {
                rows.add(Row.ofAction(ACTION_WALLPAPER_CLEAR, wallpaperClearItem()));
            }
        });
        addGroup(rows, LABEL_ADJUST, () ->
                addSliders(rows, SettingsSection.APPEARANCE,
                        "opacity", "width", "scale", "cardtint"));
        // Everything else: the full colour palette, folded away by default.
        addGroup(rows, LABEL_APPEARANCE_ADVANCED, () -> {
            for (SettingsColor color : SettingsCatalog.colors(SettingsSection.APPEARANCE)) {
                rows.add(Row.ofColor(color));
            }
        });
        return rows;
    }

    private List<Row> privacyRows() {
        List<Row> rows = new ArrayList<>();
        addGroup(rows, groupKey(SettingsSection.PRIVACY), () ->
                addSwitches(rows, SettingsSection.PRIVACY, "hideBlocked"));
        addGroup(rows, LABEL_BLOCKED, () -> {
            for (PlayerRef player : blockedPlayers()) {
                rows.add(Row.ofBlocked(player));
            }
        });
        return rows;
    }

    private List<Row> aboutRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.ofHero());
        addGroup(rows, LABEL_MOD_INFO, () -> {
            for (SettingsCatalog.InfoRow info : SettingsCatalog.aboutCoreRows()) {
                rows.add(Row.ofInfo(info));
            }
        });
        addGroup(rows, LABEL_THIRD_PARTY, () -> {
            for (SettingsCatalog.InfoRow info : SettingsCatalog.thirdPartyRows()) {
                rows.add(Row.ofInfo(info));
            }
        });
        addGroup(rows, LABEL_ADVANCED, () -> {
            rows.add(Row.ofAction(ACTION_CACHE_CLEAR, cacheClearItem()));
            addSwitches(rows, SettingsSection.ABOUT, "debug");
        });
        return rows;
    }

    public static List<PlayerRef> blockedPlayers() {
        List<String> names = AtomChatConfig.get().blockedPlayers;
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<PlayerRef> out = new ArrayList<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                out.add(PlayerRef.of(null, name));
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- layout

    public float measureContent(UiLayout layout, SettingsSection section) {
        List<Row> rows = rows(section);
        if (rows.isEmpty()) {
            return UiTokens.ROOT_CONTENT_GAP;
        }
        float total = UiTokens.ROOT_CONTENT_GAP;
        for (Row row : rows) {
            total += rowHeight(row, layout);
        }
        total += (rows.size() - 1) * UiTokens.SETTINGS_ROW_GAP;
        return total;
    }

    private UiLayout.Rect rowRect(List<Row> rows, int index, float scrollY, UiLayout layout) {
        float y = layout.list.y() + UiTokens.ROOT_CONTENT_GAP - scrollY;
        for (int i = 0; i < index; i++) {
            y += rowHeight(rows.get(i), layout) + UiTokens.SETTINGS_ROW_GAP;
        }
        return new UiLayout.Rect(layout.list.x(), y, layout.list.w(), rowHeight(rows.get(index), layout));
    }

    private static UiLayout.Rect sliderTrackRect(UiLayout.Rect row) {
        return new UiLayout.Rect(row.x() + UiTokens.SETTINGS_ROW_PAD,
                row.y() + UiTokens.SETTINGS_SLIDER_TRACK_Y,
                row.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F,
                UiTokens.SLIDER_TRACK_H);
    }

    /** Left edge of the clickable action for a row rect. */
    private static float actionX(Row row, UiLayout.Rect rect, Font buttonFont) {
        if (row.kind() == RowKind.BLOCKED) {
            float textW = SkiaFontRenderer.getStringWidth(buttonFont, tr("atomchat.settings.privacy.unblock"));
            return rect.right() - UiTokens.SETTINGS_ROW_PAD - (textW + s(18));
        }
        return rect.x();
    }

    // ----------------------------------------------------------------- render

    public void render(Canvas canvas, UiLayout layout, SettingsSection section,
                       float vmx, float vmy, float scrollY, int accent) {
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        currentSectionForSettle = section;

        List<Row> rows = rows(section);
        Font buttonFont = FontManager.font(UiTokens.FONT_QUOTE);
        int hovered = -1;

        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(), 0.0F);
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                UiLayout.Rect rect = rowRect(rows, i, scrollY, layout);
                if (rect.bottom() < layout.list.y() || rect.y() > layout.list.bottom()) {
                    continue;
                }
                boolean over = row.kind() != RowKind.LABEL
                        && vmx >= rect.x() && vmx <= rect.right()
                        && vmy >= rect.y() && vmy <= rect.bottom();
                if (over) {
                    hovered = i;
                }
                // Draw from the animated value only. Never force it to 1 while
                // hovered — that is what made the highlight snap in instead of
                // fading in over the same 90ms the toolbar buttons use.
                drawRow(canvas, row, rect, rowHover.getOrDefault(i, 0.0F),
                        buttonFont, accent, dt);
            }
        } finally {
            canvas.restore();
        }

        hoveredIndex = hovered;
        if (hovered >= 0) {
            rowHover.putIfAbsent(hovered, 0.0F);
        }
        for (Integer key : new ArrayList<>(rowHover.keySet())) {
            float target = key == hovered ? 1.0F : 0.0F;
            rowHover.put(key, UiMotion.approach(rowHover.get(key), target, dt, UiMotion.HOVER_MS));
        }

        if (section == SettingsSection.PRIVACY && blockedPlayers().isEmpty()) {
            drawEmptyBlocked(canvas, layout, rows, scrollY);
        }
        advanceSettle(now);
    }

    private void drawEmptyBlocked(Canvas canvas, UiLayout layout, List<Row> rows, float scrollY) {
        UiLayout.Rect last = rowRect(rows, rows.size() - 1, scrollY, layout);
        float top = last.bottom() + s(10);
        float height = Math.min(s(160), Math.max(s(90), layout.list.bottom() - top));
        if (height <= 0.0F) {
            return;
        }
        drawEmptyState(canvas, new UiLayout.Rect(layout.list.x(), top, layout.list.w(), height),
                "atomchat.settings.privacy.empty");
    }

    private void drawRow(Canvas canvas, Row row, UiLayout.Rect rect, float hover,
                         Font buttonFont, int accent, float dtMs) {
        if (row.kind() == RowKind.LABEL) {
            drawLabel(canvas, row, rect);
            return;
        }
        SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), UiTokens.cardFill());
        SkiaDraw.drawEdgeHighlight(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), s(1.2F), UiTokens.CARD_EDGE);
        if (hover > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                    UiTokens.settingsRowRadius(), UiTokens.cardHover(hover));
        }
        switch (row.kind()) {
            case HERO -> drawHero(canvas, rect);
            case SWITCH -> drawSwitch(canvas, row, rect, accent, dtMs);
            case SLIDER -> drawSlider(canvas, row, rect, accent);
            case COLOR -> drawColor(canvas, row, rect);
            case THEMES -> drawThemes(canvas, rect);
            case INFO -> drawInfo(canvas, row, rect);
            case BLOCKED -> drawBlocked(canvas, row, rect, hover, buttonFont);
            case ACTION -> drawAction(canvas, row, rect, hover);
            default -> {
            }
        }
    }

    /** Swatch strip geometry: centre Y within the row and the X step. */
    private static float swatchCy(UiLayout.Rect rect) {
        return rect.y() + s(42);
    }

    private static float swatchX(UiLayout.Rect rect, int index) {
        return rect.x() + UiTokens.SETTINGS_ROW_PAD + UiTokens.s(9) + index * UiTokens.s(26);
    }

    /**
     * Theme strip: title + current-preset name on the caption line, then the
     * seven dots — frosted, then the six colour presets — each filled with its
     * own accent so the row previews every palette at a glance. The active
     * dot wears a ring in the interface text colour (white vanishes on the
     * light palettes' white cards).
     */
    private void drawThemes(Canvas canvas, UiLayout.Rect rect) {
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        String currentId = AtomChatConfig.get().themeName;
        if (currentId == null) {
            currentId = "";
        }

        SkiaFontRenderer.drawText(canvas, titleFont,
                tr("atomchat.settings.appearance.theme"),
                rect.x() + UiTokens.SETTINGS_ROW_PAD,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(18)),
                textPrimary());
        String currentKey;
        if (currentId.isEmpty() || ThemeService.FROSTED.equals(currentId)) {
            currentKey = "atomchat.settings.theme.frosted";
        } else if (ThemeService.byId(currentId) != null) {
            currentKey = "atomchat.settings.theme." + currentId;
        } else if (ThemeService.MODERN.equals(currentId)) {
            currentKey = "atomchat.settings.theme.modern";
        } else {
            currentKey = "atomchat.settings.theme.custom";
        }
        SkiaFontRenderer.drawTextRight(canvas, valueFont, tr(currentKey),
                rect.right() - UiTokens.SETTINGS_ROW_PAD, rect.y() + s(18),
                sec(255));

        float r = UiTokens.s(10);
        float cy = swatchCy(rect);
        boolean frostedActive = currentId.isEmpty() || ThemeService.FROSTED.equals(currentId);
        drawThemeDot(canvas, swatchX(rect, 0), cy, r, ThemeService.FROSTED_ACCENT, frostedActive);
        ThemeService.Preset[] presets = ThemeService.presets();
        for (int i = 0; i < presets.length; i++) {
            drawThemeDot(canvas, swatchX(rect, i + 1), cy, r,
                    presets[i].accent(), presets[i].id().equals(currentId));
        }
    }

    /** One theme dot with the same selection-ring language as the colour rows. */
    private void drawThemeDot(Canvas canvas, float cx, float cy, float r,
                              int color, boolean selected) {
        SkiaDraw.drawRoundedRect(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, r, color);
        if (selected) {
            try (Paint ring = new Paint().setColor(textPrimary())
                    .setMode(PaintMode.STROKE).setStrokeWidth(s(2)).setAntiAlias(true)) {
                canvas.drawOval(io.github.humbleui.types.Rect.makeXYWH(
                        cx - r - s(3), cy - r - s(3), 2.0F * (r + s(3)), 2.0F * (r + s(3))), ring);
            }
        }
    }

    /** Index of the theme dot under the pointer, or -1. Geometry matches the
     *  renderer (same swatch strip); 0 = frosted, 1..6 = colour presets. */
    private int themeDotAt(UiLayout.Rect rect, float vmx, float vmy) {
        float cy = swatchCy(rect);
        if (vmy < cy - UiTokens.s(16) || vmy > cy + UiTokens.s(16)) {
            return -1;
        }
        for (int i = 0; i < THEME_DOT_COUNT; i++) {
            if (Math.abs(vmx - swatchX(rect, i)) <= UiTokens.s(13)) {
                return i;
            }
        }
        return -1;
    }

    private void drawColor(Canvas canvas, Row row, UiLayout.Rect rect) {
        SettingsColor color = row.color();
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);

        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(color.titleKey()), rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F),
                rect.x() + UiTokens.SETTINGS_ROW_PAD,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(18)),
                textPrimary());
        String hex = String.format("#%06X", color.value() & 0xFFFFFF);
        float hexW = SkiaFontRenderer.getStringWidth(valueFont, hex);
        SkiaFontRenderer.drawTextRight(canvas, valueFont, hex,
                rect.right() - UiTokens.SETTINGS_ROW_PAD, rect.y() + s(18),
                textPrimary());
        // Live element preview: the current value as one small square, same
        // shape for every colour row.
        float prevW = s(11);
        float prevR = s(3);
        float prevRight = rect.right() - UiTokens.SETTINGS_ROW_PAD - hexW - s(8);
        SkiaDraw.drawRoundedRect(canvas, prevRight - prevW, rect.y() + s(18) - prevW / 2.0F,
                prevW, prevW, prevR, color.value());

        float r = UiTokens.s(9);
        float cy = swatchCy(rect);
        for (int i = 0; i < color.swatchCount(); i++) {
            float scx = swatchX(rect, i);
            int swatch = color.swatchColor(i);
            SkiaDraw.drawRoundedRect(canvas, scx - r, cy - r, 2.0F * r, 2.0F * r, r, swatch);
            if (swatch == color.value()) {
                // Selection ring: white outline with a breathing gap.
                try (Paint ring = new Paint().setColor(Color.makeARGB(255, 255, 255, 255))
                        .setMode(PaintMode.STROKE).setStrokeWidth(s(2)).setAntiAlias(true)) {
                    canvas.drawOval(io.github.humbleui.types.Rect.makeXYWH(
                            scx - r - s(3), cy - r - s(3), 2.0F * (r + s(3)), 2.0F * (r + s(3))), ring);
                }
            }
        }
        // "+" cell: opens the custom colour picker (emote-grid plus language).
        float px = swatchX(rect, color.swatchCount());
        SkiaDraw.drawRoundedRect(canvas, px - r, cy - r, 2.0F * r, 2.0F * r, r,
                Color.makeARGB(70, 255, 255, 255));
        drawIconCentered(canvas, AppIcons.ICON_PLUS_PATH, px, cy, s(12),
                textPrimary());
    }

    private static void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon,
                                         float cx, float cy, float size, int color) {
        io.github.humbleui.types.Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float sc = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * sc,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * sc);
            canvas.scale(sc, sc);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE).setStrokeWidth(UiTokens.iconStroke(size) / sc)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * About-page hero: the logo on a white plate (the source PNG has no alpha,
     * so it needs a light ground) beside the wordmark. The plate/image split is
     * deliberate — a future art-text logo only has to replace
     * {@link #heroImage()}, nothing else in the card moves.
     */
    private void drawHero(Canvas canvas, UiLayout.Rect rect) {
        SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), UiTokens.cardFill());
        SkiaDraw.drawEdgeHighlight(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), s(1.2F), UiTokens.CARD_EDGE);
        Image hero = heroImage();
        float plate = UiTokens.SETTINGS_HERO_PLATE;
        float plateX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float plateY = rect.y() + (rect.h() - plate) / 2.0F;
        float plateR = s(12);
        SkiaDraw.drawRoundedRect(canvas, plateX, plateY, plate, plate, plateR, Color.makeARGB(255, 250, 250, 250));
        if (hero != null) {
            float inset = s(7);
            SkiaDraw.drawRoundedImage(canvas, hero, plateX + inset, plateY + inset,
                    plate - inset * 2.0F, plate - inset * 2.0F, plateR - inset, SamplingMode.LINEAR);
        }
        Font heroFont = FontManager.font(UiTokens.SETTINGS_HERO_FONT);
        SkiaFontRenderer.drawText(canvas, heroFont, tr("atomchat.screen.title"),
                plateX + plate + s(14),
                SkiaFontRenderer.centerBaselineY(heroFont, rect.y() + rect.h() / 2.0F),
                textPrimary());
    }

    private static Image heroImage;

    /** The bundled {@code logo.png}, decoded once and cached for the session. */
    private static Image heroImage() {
        if (heroImage != null) {
            return heroImage;
        }
        try (var stream = SettingsSectionPage.class.getResourceAsStream("/assets/atomchat/logo.png")) {
            if (stream != null) {
                heroImage = Image.makeFromEncoded(stream.readAllBytes());
            }
        } catch (Exception ignored) {
            // No logo: the plate draws empty and the card still reads fine.
        }
        return heroImage;
    }

    /**
     * Draws a settings description below the title. Fits on one line it keeps
     * the existing baseline; otherwise it wraps and sits bottom-aligned inside
     * the (already grown) row so long English text is never lost to an ellipsis.
     */
    private void drawWrappedDescription(Canvas canvas, UiLayout.Rect rect, Font font,
                                        String text, float x, float maxWidth, int color) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F
                || SkiaFontRenderer.getStringWidth(font, text) <= maxWidth) {
            SkiaFontRenderer.drawText(canvas, font, text == null ? "" : text, x,
                    SkiaFontRenderer.centerBaselineY(font, rect.y() + s(37)), color);
            return;
        }
        List<String> lines = SkiaFontRenderer.wrap(font, text, maxWidth);
        float lineH = descriptionLineHeight(font);
        float blockH = lines.size() * lineH;
        float centerY = rect.y() + Math.max(s(37), rect.h() - s(8) - blockH / 2.0F);
        SkiaFontRenderer.drawLines(canvas, font, lines, x, centerY, lineH, color);
    }

    private void drawAction(Canvas canvas, Row row, UiLayout.Rect rect, float hover) {
        ActionCopy copy = actionCopy(row);
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float maxW = rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F;

        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, copy.title(), maxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(20)),
                textPrimary());
        drawWrappedDescription(canvas, rect, subFont, copy.subtitle(), textX,
                descriptionMaxWidth(row, rect.w()), copy.redConfirm()
                        ? Color.makeARGB(255, 235, 64, 52)
                        : sec(copy.available() ? 200 : 130));
        // Same treatment as the link cards' "Open": full-weight, centred —
        // the card's call to action, not a footnote.
        if (copy.available()) {
            Font verbFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            SkiaFontRenderer.drawTextRight(canvas, verbFont, copy.verb(),
                    rect.right() - UiTokens.SETTINGS_ROW_PAD,
                    rect.y() + rect.h() / 2.0F,
                    copy.redConfirm() ? Color.makeARGB(255, 235, 64, 52) : textPrimary());
        }
        // Unavailable veil, same language as the wallpaper-gated blur switch.
        if (!copy.available()) {
            SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                    UiTokens.settingsRowRadius(), Color.makeARGB(90, 10, 12, 16));
        }
    }

    /**
     * Group heading: title-weight text centred between two rules of the same
     * colour, so it reads as a section divider rather than a stray caption.
     */
    private void drawLabel(Canvas canvas, Row row, UiLayout.Rect rect) {
        // Plan A: left-aligned bold group title with a faint full-width rule
        // underneath. The chevron lives at the right edge for foldable groups.
        Font font = FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
        int textColor = textPrimary();
        int lineColor = sec(120);
        String group = foldableGroup(row.labelKey());
        String text = tr(row.labelKey());
        float padX = UiTokens.SETTINGS_ROW_PAD;
        float textX = rect.x() + padX;
        float rightX = rect.right() - padX;
        float cy = rect.y() + rect.h() / 2.0F;

        SkiaFontRenderer.drawText(canvas, font,
                SkiaFontRenderer.truncate(font, text, Math.max(0.0F, rightX - textX - s(24))),
                textX, SkiaFontRenderer.centerBaselineY(font, cy), textColor);

        if (group != null) {
            boolean collapsed = collapsedColorGroups.contains(group);
            float chevron = s(7);
            drawChevron(canvas, rightX - chevron / 2.0F, cy, chevron, collapsed, textColor);
        }

        float lineY = rect.bottom() - s(5);
        SkiaDraw.drawRoundedRect(canvas, textX, lineY,
                Math.max(0.0F, rightX - textX), s(1), s(0.5F), lineColor);
    }

    /** Small fold indicator: right-pointing when collapsed, down when open.
     *  No explicit contour close: fill mode auto-closes, and Path.close() is
     *  the resource-release method (contour close is closePath()) — calling it
     *  inside the block frees the native path before drawPath runs (crashed). */
    private static void drawChevron(Canvas canvas, float cx, float cy, float size,
                                    boolean collapsed, int color) {
        try (io.github.humbleui.skija.Path path = new io.github.humbleui.skija.Path();
             Paint paint = new Paint()) {
            if (collapsed) {
                path.moveTo(cx - size / 2.0F, cy - size);
                path.lineTo(cx - size / 2.0F, cy + size);
                path.lineTo(cx + size / 2.0F, cy);
            } else {
                path.moveTo(cx - size, cy - size / 2.0F);
                path.lineTo(cx + size, cy - size / 2.0F);
                path.lineTo(cx, cy + size / 2.0F);
            }
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }
    }

    /** Centred "nothing here" glyph + caption, shared by every empty list. */
    public static void drawEmptyState(Canvas canvas, UiLayout.Rect area, String textKey) {
        Font font = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float icon = UiTokens.SETTINGS_EMPTY_ICON;
        float textH = SkiaFontRenderer.textHeight(font);
        float gap = s(12);
        float groupH = icon + gap + textH;
        float top = area.y() + Math.max(0.0F, (area.h() - groupH) / 2.0F);
        float cx = area.x() + area.w() / 2.0F;
        // Empty-state icon and caption read as secondary information: they
        // follow the secondary text colour, dimmed to keep the muted look.
        int muted = sec(150);
        drawIconCentered(canvas, AppIcons.ICON_NO_PLAYERS_PATH, cx, top + icon / 2.0F, icon, muted);
        SkiaFontRenderer.drawTextCentered(canvas, font, Text.translatable(textKey).getString(),
                cx, top + icon + gap + textH / 2.0F, muted);
    }

    private void drawSwitch(Canvas canvas, Row row, UiLayout.Rect rect, int accent, float dtMs) {
        SettingsItem item = row.item();
        ToggleSwitch control = switches.computeIfAbsent(item.id(), k -> new ToggleSwitch());
        control.update(dtMs, item.available() && item.value());

        float switchX = rect.right() - UiTokens.SETTINGS_ROW_PAD - UiTokens.SWITCH_W;
        float switchY = rect.y() + (rect.h() - UiTokens.SWITCH_H) / 2.0F;
        control.render(canvas, switchX, switchY, accent);

        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float maxW = Math.max(0.0F, switchX - textX - s(10));
        float descMaxW = descriptionMaxWidth(row, rect.w());
        // An overridden option explains itself instead of silently doing nothing.
        String subtitleKey = item.available()
                ? item.subtitleKey() : item.subtitleKey() + ".unavailable";
        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(item.titleKey()), maxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(20)),
                textPrimary());
        drawWrappedDescription(canvas, rect, subFont, tr(subtitleKey), textX, descMaxW,
                sec(item.available() ? 200 : 130));
        if (!item.available()) {
            SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                    UiTokens.settingsRowRadius(), Color.makeARGB(90, 10, 12, 16));
        }
    }

    private void drawSlider(Canvas canvas, Row row, UiLayout.Rect rect, int accent) {
        SettingsSlider slider = row.slider();
        if (isInlineNumberSlider(slider.id())) {
            drawInlineNumberSlider(canvas, row, rect, accent);
            return;
        }
        boolean dragging = slider.id().equals(draggingSliderId);
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float titleMaxW = Math.max(0.0F, rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F
                - SkiaFontRenderer.getStringWidth(valueFont, slider.displayValue()) - s(12));

        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(slider.titleKey()), titleMaxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(18)),
                textPrimary());
        SkiaFontRenderer.drawTextRight(canvas, valueFont, slider.displayValue(),
                rect.right() - UiTokens.SETTINGS_ROW_PAD, rect.y() + s(18),
                dragging ? accent : textPrimary());

        UiLayout.Rect track = sliderTrackRect(rect);
        float t = knobPosition(slider, dragging);
        float radius = UiTokens.SLIDER_TRACK_H / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, track.x(), track.y(), track.w(), track.h(), radius,
                Color.makeARGB(70, 255, 255, 255));
        float fillW = Math.max(track.h(), track.w() * t);
        SkiaDraw.drawRoundedRect(canvas, track.x(), track.y(), fillW, track.h(), radius, accent);

        float knobX = track.x() + track.w() * t - UiTokens.SLIDER_KNOB / 2.0F;
        float knobY = track.y() + track.h() / 2.0F - UiTokens.SLIDER_KNOB / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, knobX, knobY, UiTokens.SLIDER_KNOB, UiTokens.SLIDER_KNOB,
                UiTokens.SLIDER_KNOB / 2.0F, Color.makeARGB(255, 255, 255, 255)); // knob: mechanical white
    }

    /** Sliders rendered as a right-side input field instead of a drag track. */
    private static boolean isInlineNumberSlider(String id) {
        return "history_retention".equals(id);
    }

    /**
     * Label + input-field row for integer values that need exact entry (the
     * retention slider could not land on precise day counts). The field is
     * always visible on the right; clicking it opens the inline editor.
     */
    private void drawInlineNumberSlider(Canvas canvas, Row row, UiLayout.Rect rect, int accent) {
        SettingsSlider slider = row.slider();
        boolean editing = slider.id().equals(editingSliderId);
        inlineNumberRowRect = rect;
        float padX = UiTokens.SETTINGS_ROW_PAD;
        float textX = rect.x() + padX;
        float fieldW = Math.min(s(170), rect.w() * 0.45F);
        float fieldX = rect.right() - padX - fieldW;
        float fieldH = s(36);
        float fieldY = rect.y() + (rect.h() - fieldH) / 2.0F;
        float fieldCY = fieldY + fieldH / 2.0F;
        float descMaxW = Math.max(0.0F, fieldX - textX - s(12));

        // Title plus a one-line description on the left, matching switch/action
        // cards; the input field stays vertically centred on the right.
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(slider.titleKey()), descMaxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(20)),
                textPrimary());
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        SkiaFontRenderer.drawText(canvas, subFont,
                SkiaFontRenderer.truncate(subFont,
                        tr("atomchat.settings.chat.history.retention.desc"), descMaxW), textX,
                SkiaFontRenderer.centerBaselineY(subFont, rect.y() + s(37)),
                sec(200));

        float radius = s(8);
        SkiaDraw.drawRoundedRect(canvas, fieldX, fieldY, fieldW, fieldH, radius,
                Color.makeARGB(60, 255, 255, 255));
        try (Paint border = new Paint().setMode(PaintMode.STROKE)
                .setAntiAlias(true).setStrokeWidth(s(1.5F))
                .setColor(editing ? accent : Color.makeARGB(110, 255, 255, 255))) {
            canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                    fieldX, fieldY, fieldW, fieldH, radius), border);
        }

        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        String text = editing ? (editBuffer.isEmpty() ? "0" : editBuffer) : slider.displayValue();
        String shown = SkiaFontRenderer.truncate(inputFont, text, fieldW - s(20));
        SkiaFontRenderer.drawText(canvas, inputFont, shown,
                fieldX + s(10),
                SkiaFontRenderer.centerBaselineY(inputFont, fieldCY),
                editing ? accent : textPrimary());
        // Blinking caret while the inline editor owns the keyboard.
        if (editing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = fieldX + s(10) + SkiaFontRenderer.getStringWidth(inputFont, shown) + s(2);
            try (Paint caret = new Paint().setColor(Color.makeARGB(255, 255, 255, 255))
                    .setStrokeWidth(s(1.5F)).setAntiAlias(true)) {
                canvas.drawLine(caretX, fieldCY - s(8), caretX, fieldCY + s(8), caret);
            }
        }
    }

    private void drawInfo(Canvas canvas, Row row, UiLayout.Rect rect) {
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float maxW = rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F;
        boolean link = row.info().isLink();
        // Reserve room for the right-aligned "opens a browser" hint so a long
        // value can never run under it.
        float valueMaxW = maxW;
        if (link) {
            valueMaxW -= SkiaFontRenderer.getStringWidth(titleFont, tr("atomchat.settings.about.open")) + s(12);
        }

        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(row.info().titleKey()), maxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(20)),
                textPrimary());

        String valueText = row.info().value();
        boolean valueFits = valueMaxW <= 0.0F
                || SkiaFontRenderer.getStringWidth(valueFont, valueText) <= valueMaxW;
        // Link affordance = colour plus underline, so it stays distinct from
        // the grey non-link values on the same page. The right-aligned hint
        // tells the user the whole card is clickable before they try it.
        if (valueFits) {
            SkiaFontRenderer.drawText(canvas, valueFont, valueText, textX,
                    SkiaFontRenderer.centerBaselineY(valueFont, rect.y() + s(37)),
                    link ? LINK_COLOR : sec(200));
            if (link) {
                float underlineY = rect.y() + s(37) + SkiaFontRenderer.textHeight(valueFont) / 2.0F + s(2);
                SkiaDraw.drawRoundedRect(canvas, textX, underlineY,
                        SkiaFontRenderer.getStringWidth(valueFont, valueText), s(1.5F), s(0.75F), LINK_COLOR);
            }
        } else {
            List<String> lines = SkiaFontRenderer.wrap(valueFont, valueText, valueMaxW);
            float lineH = descriptionLineHeight(valueFont);
            float blockH = lines.size() * lineH;
            float centerY = rect.y() + Math.max(s(37), rect.h() - s(8) - blockH / 2.0F);
            SkiaFontRenderer.drawLines(canvas, valueFont, lines, textX, centerY, lineH,
                    link ? LINK_COLOR : sec(200));
            if (link) {
                float top = centerY - blockH / 2.0F;
                for (int i = 0; i < lines.size(); i++) {
                    float lineCenterY = top + (i + 0.5F) * lineH;
                    float baseline = SkiaFontRenderer.centerBaselineY(valueFont, lineCenterY);
                    float underlineY = baseline + SkiaFontRenderer.textHeight(valueFont) / 2.0F + s(2);
                    SkiaDraw.drawRoundedRect(canvas, textX, underlineY,
                            SkiaFontRenderer.getStringWidth(valueFont, lines.get(i)),
                            s(1.5F), s(0.75F), LINK_COLOR);
                }
            }
        }
        if (link) {
            // Full-weight and vertically centred: it is the card's call to
            // action, not a footnote.
            String hint = tr("atomchat.settings.about.open");
            Font hintFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            SkiaFontRenderer.drawTextRight(canvas, hintFont, hint,
                    rect.right() - UiTokens.SETTINGS_ROW_PAD,
                    rect.y() + rect.h() / 2.0F, textPrimary());
        }
    }

    private void drawBlocked(Canvas canvas, Row row, UiLayout.Rect rect, float hover, Font buttonFont) {
        float avatar = UiTokens.SETTINGS_ROW_AVATAR;
        float avatarY = rect.y() + (rect.h() - avatar) / 2.0F;
        Image face = row.player() != null
                ? PlayerAvatar.face(row.player().uuid(), row.player().realName()) : null;
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, rect.x() + UiTokens.SETTINGS_ROW_PAD, avatarY,
                    avatar, avatar, avatar / 2.0F, SamplingMode.LINEAR);
        } else {
            SkiaDraw.drawRoundedRect(canvas, rect.x() + UiTokens.SETTINGS_ROW_PAD, avatarY,
                    avatar, avatar, avatar / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }

        String name = row.player() != null ? row.player().realName() : "";
        Font nameFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        float nameX = rect.x() + UiTokens.SETTINGS_ROW_PAD + avatar + s(10);
        String label = tr("atomchat.settings.privacy.unblock");
        float buttonW = SkiaFontRenderer.getStringWidth(buttonFont, label) + s(18);
        float buttonX = rect.right() - UiTokens.SETTINGS_ROW_PAD - buttonW;
        float maxNameW = Math.max(0.0F, buttonX - nameX - s(10));
        SkiaFontRenderer.drawText(canvas, nameFont,
                SkiaFontRenderer.truncate(nameFont, name, maxNameW), nameX,
                SkiaFontRenderer.centerBaselineY(nameFont, rect.y() + rect.h() / 2.0F),
                textPrimary());

        float buttonH = s(28);
        float buttonY = rect.y() + (rect.h() - buttonH) / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, buttonX, buttonY, buttonW, buttonH, s(8),
                Color.makeARGB((int) (70.0F + 45.0F * hover), 255, 255, 255));
        SkiaFontRenderer.drawTextCentered(canvas, buttonFont, label,
                buttonX + buttonW / 2.0F, buttonY + buttonH / 2.0F,
                textPrimary());
    }

    // ------------------------------------------------------------------ input

    public RowHit hit(float vmx, float vmy, UiLayout layout, SettingsSection section, float scrollY) {
        List<Row> rows = rows(section);
        Font buttonFont = FontManager.font(UiTokens.FONT_QUOTE);
        for (int i = 0; i < rows.size(); i++) {
            UiLayout.Rect rect = rowRect(rows, i, scrollY, layout);
            if (rows.get(i).kind() == RowKind.THEMES) {
                pressedThemeDot = themeDotAt(rect, vmx, vmy);
            }
            RowHit hit = new RowHit(rows.get(i), i, rect.x(), rect.y(), rect.w(), rect.h(),
                    actionX(rows.get(i), rect, buttonFont));
            if (hit.contains(vmx, vmy)) {
                return hit;
            }
        }
        return null;
    }

    /** Slider row under the pointer, or null. Geometry matches the renderer. */
    public SliderHit sliderHit(float vmx, float vmy, UiLayout layout, SettingsSection section, float scrollY) {
        List<Row> rows = rows(section);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind() != RowKind.SLIDER) {
                continue;
            }
            UiLayout.Rect rect = rowRect(rows, i, scrollY, layout);
            SliderHit hit = new SliderHit(row, i, rect, sliderTrackRect(rect));
            if (hit.contains(vmx, vmy)) {
                return hit;
            }
        }
        return null;
    }

    /**
     * Starts dragging the slider in {@code hit}. The knob jumps to the pointer
     * immediately, so picking the handle up never feels like it slipped. Only
     * the row index is kept: the panel can resize under the pointer (the width
     * slider does exactly that), so the geometry must be recomputed every frame.
     */
    public void beginSliderDrag(int rowIndex, SettingsSlider slider, UiLayout.Rect rowRect, float vmx) {
        activeSliderIndex = rowIndex;
        draggingSliderId = slider.id();
        settleId = null;
        UiLayout.Rect track = sliderTrackRect(rowRect);
        float t = track.w() <= 0.0F ? 0.0F : (vmx - track.x()) / track.w();
        dragValue = slider.denormalizeContinuous(t);
        slider.apply(dragValue);
    }

    /**
     * Retargets the active drag. The knob follows the pointer continuously;
     * only the snapped value is applied to the config, so the label always
     * names a value the mod is actually using.
     */
    public void dragSlider(UiLayout layout, SettingsSection section, float scrollY, float vmx) {
        if (activeSliderIndex < 0 || draggingSliderId == null) {
            return;
        }
        List<Row> rows = rows(section);
        if (activeSliderIndex >= rows.size()) {
            return;
        }
        Row row = rows.get(activeSliderIndex);
        if (row.kind() != RowKind.SLIDER) {
            return;
        }
        UiLayout.Rect track = sliderTrackRect(rowRect(rows, activeSliderIndex, scrollY, layout));
        float t = track.w() <= 0.0F ? 0.0F : (vmx - track.x()) / track.w();
        dragValue = row.slider().denormalizeContinuous(t);
        row.slider().apply(dragValue);
    }

    /**
     * Releases the drag: persists once, then lets the knob glide from wherever
     * the finger left it to the snapped value instead of teleporting there.
     */
    public void endSliderDrag() {
        if (draggingSliderId != null && activeSliderIndex >= 0) {
            List<Row> rows = rows(currentSectionForSettle);
            if (activeSliderIndex < rows.size() && rows.get(activeSliderIndex).kind() == RowKind.SLIDER) {
                SettingsSlider slider = rows.get(activeSliderIndex).slider();
                slider.persist();
                settleId = slider.id();
                settleFrom = slider.normalize(dragValue);
                settleTo = slider.normalize(slider.value());
                settleStart = System.currentTimeMillis();
            }
        }
        activeSliderIndex = -1;
        draggingSliderId = null;
    }

    public boolean isDraggingSlider() {
        return draggingSliderId != null;
    }

    // ------------------------------------------------------------ number edit

    /** Whether the inline numeric editor is open (keyboard owns the row). */
    public boolean isEditingNumber() {
        return editingSliderId != null;
    }

    public String editingNumberText() {
        return editBuffer;
    }

    /** Opens the inline editor with the slider's current rounded value. */
    public void beginNumberEdit(SettingsSlider slider) {
        if (slider == null) {
            return;
        }
        editingSlider = slider;
        editingSliderId = slider.id();
        editBuffer = String.valueOf(Math.round(slider.value()));
    }

    public void appendNumberChar(char c) {
        if (editingSliderId == null || c < '0' || c > '9' || editBuffer.length() >= 6) {
            return;
        }
        editBuffer += c;
    }

    public void backspaceNumber() {
        if (editingSliderId != null && !editBuffer.isEmpty()) {
            editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
        }
    }

    /** Parses and applies the typed value; empty buffer is treated as 0. */
    public void commitNumberEdit() {
        if (editingSliderId == null) {
            return;
        }
        try {
            int value = editBuffer.isEmpty() ? 0 : Integer.parseInt(editBuffer);
            if (editingSlider != null) {
                editingSlider.apply(value);
                editingSlider.persist();
            }
        } catch (NumberFormatException ignored) {
            // Invalid input: keep the old value.
        } finally {
            cancelNumberEdit();
        }
    }

    public void cancelNumberEdit() {
        editingSliderId = null;
        editingSlider = null;
        editBuffer = "";
    }

    /** Whether a virtual point lies inside the inline number row (focus stays). */
    public boolean isInsideNumberEditRow(float vx, float vy) {
        return inlineNumberRowRect != null && vx >= inlineNumberRowRect.x()
                && vx <= inlineNumberRowRect.right()
                && vy >= inlineNumberRowRect.y() && vy <= inlineNumberRowRect.bottom();
    }

    /**
     * Knob position for rendering: the pointer while dragging, the snapped
     * value otherwise, with a short glide in between so releasing never reads
     * as a jump. A drag itself is deliberately 1:1 — easing it would only make
     * the knob lag behind the finger.
     */
    private float knobPosition(SettingsSlider slider, boolean dragging) {
        if (dragging) {
            return slider.normalize(dragValue);
        }
        if (slider.id().equals(settleId)) {
            return settleFrom + (settleTo - settleFrom)
                    * Easing.easeOutCubic(Math.min(1.0F, (System.currentTimeMillis() - settleStart) / 120.0F));
        }
        return slider.normalize(slider.value());
    }

    private void advanceSettle(long now) {
        if (settleId != null && now - settleStart >= 120L) {
            settleId = null;
        }
    }

    /** One {@link SettingsSlider#step()} in {@code direction} (-1 / +1). */
    public void nudgeSlider(SliderHit hit, int direction) {
        hit.row().slider().nudge(direction);
    }

    /** A colour swatch (or the "+" custom cell) under the pointer. */
    public record ColorHit(SettingsColor color, int swatchIndex, int swatch, boolean plus) {
    }

    /**
     * Colour swatch or the trailing "+" cell under the pointer, or null.
     * Geometry mirrors {@link #drawColor}: the row's swatch strip is
     * hit-tested swatch by swatch, not per row, so a click between swatches
     * falls through.
     */
    public ColorHit colorHit(float vmx, float vmy, UiLayout layout, SettingsSection section, float scrollY) {
        List<Row> rows = rows(section);
        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Row row = rows.get(rowIdx);
            if (row.kind() != RowKind.COLOR) {
                continue;
            }
            SettingsColor color = row.color();
            UiLayout.Rect rect = rowRect(rows, rowIdx, scrollY, layout);
            float r = UiTokens.s(9);
            float cy = swatchCy(rect);
            float dy = vmy - cy;
            if (Math.abs(dy) > r + UiTokens.s(4) || vmy < rect.y() || vmy > rect.bottom()) {
                continue;
            }
            for (int i = 0; i < color.swatchCount(); i++) {
                float dx = vmx - swatchX(rect, i);
                if (Math.abs(dx) <= r + UiTokens.s(4)) {
                    return new ColorHit(color, i, color.swatchColor(i), false);
                }
            }
            // The "+" cell right after the last swatch.
            float dx = vmx - swatchX(rect, color.swatchCount());
            if (Math.abs(dx) <= r + UiTokens.s(4)) {
                return new ColorHit(color, -1, 0, true);
            }
        }
        return null;
    }

    /** Applies a colour swatch; writes through to the config immediately. */
    public void applyColor(ColorHit hit) {
        hit.color().apply(hit.swatch());
    }

    /** Applies a hit: flips a switch, opens a link, fires an action, folds a
     *  colour group, or unblocks. Destructive actions go through the two-step
     *  confirm. */
    public void perform(RowHit hit) {
        if (hit == null) {
            return;
        }
        switch (hit.row().kind()) {
            case SWITCH -> {
                SettingsItem item = hit.row().item();
                if (!item.available()) {
                    return;
                }
                item.set(!item.value());
            }
            case INFO -> {
                if (hit.row().info().isLink()) {
                    openUri(hit.row().info().uri());
                }
            }
            case LABEL -> {
                String group = foldableGroup(hit.row().labelKey());
                if (group == null) {
                    return;
                }
                if (!collapsedColorGroups.remove(group)) {
                    collapsedColorGroups.add(group);
                }
            }
            case ACTION -> {
                if (!hit.row().item().available() || actionHandler == null) {
                    return;
                }
                String actionId = hit.row().actionId();
                if (needsConfirm(actionId)) {
                    // Two-step confirm: first tap arms, second tap fires.
                    long now = System.currentTimeMillis();
                    if (!actionArmed(actionId) || now - armedActionAt > CONFIRM_ARM_MS) {
                        armedActionId = actionId;
                        armedActionAt = now;
                        return;
                    }
                    armedActionId = null;
                }
                actionHandler.onAction(actionId);
            }
            case BLOCKED -> {
                if (hit.row().player() != null) {
                    BlockList.setBlocked(hit.row().player(), false);
                }
            }
            case THEMES -> {
                // pressedThemeDot was computed by hit() for this same click.
                int dot = pressedThemeDot;
                pressedThemeDot = -1;
                if (dot < 0) {
                    return;
                }
                AtomChatConfig config = AtomChatConfig.get();
                if (dot == 0) {
                    ThemeService.apply(config, ThemeService.FROSTED);
                } else {
                    ThemeService.apply(config, ThemeService.presets()[dot - 1].id());
                }
                AtomChatConfig.save(config);
            }
            default -> {
            }
        }
    }

    /** Shell callback for action cards; the screen owns the file picker. */
    public interface ActionHandler {
        void onAction(String actionId);
    }

    private ActionHandler actionHandler;

    public void setActionHandler(ActionHandler handler) {
        this.actionHandler = handler;
    }

    private void openUri(String uri) {
        try {
            net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(uri));
        } catch (Exception e) {
            AtomChat.LOGGER.warn("Failed to open link {}", uri, e);
        }
    }

    /** Drops per-page transient state so reopening a section starts clean. */
    public void reset() {
        switches.clear();
        rowHover.clear();
        draggingSliderId = null;
        hoveredIndex = -1;
        cancelNumberEdit();
    }
}
