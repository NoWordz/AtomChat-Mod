package com.atom.chat.settings;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.Animator;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.ToggleSwitch;
import com.atom.chat.wallpaper.WallpaperStore;

import java.nio.file.Path;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiCards;
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
import net.minecraft.network.chat.Component;

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
 * {@link #rowRect(SettingsSection, List, int, float, UiLayout)} for position,
 * so rendering, hit-testing and measurement can never disagree.</p>
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

    // Fixed link blue: deliberately NOT polarity-adaptive — it is the link's
    // own identity colour and reads on both light and dark panels.
    private static final int LINK_COLOR = Color.makeARGB(255, 96, 165, 250);
    // Destructive-confirm red, same reasoning as LINK_COLOR: a fixed danger
    // hue that must not drift with the theme or the accent.
    private static final int DANGER_RED = Color.makeARGB(255, 235, 64, 52);
    private static final String LABEL_BLOCKED = "atomchat.settings.privacy.list";
    private static final String LABEL_THIRD_PARTY = "atomchat.settings.about.thirdparty.group";
    /** Fold-group id of the third-party block; its children render merged
     *  into ONE card (see {@link #isAboutCardChild}). */
    private static final String GROUP_THIRD_PARTY = "thirdparty";
    private static final String LABEL_MOD_INFO = "atomchat.settings.about.modinfo";
    /** Fold-group id of the mod-info block; it shares the third-party
     *  block's merged-card language (see {@link #isAboutCardChild}). */
    private static final String GROUP_MOD_INFO = "modinfo";
    private static final String LABEL_ADVANCED = "atomchat.settings.group.advanced";
    private static final String LABEL_APPEARANCE_ADVANCED = "atomchat.settings.group.advancedcolors";
    private static final String ACTION_WALLPAPER_PICK = "wallpaper_pick";
    private static final String ACTION_WALLPAPER_CLEAR = "wallpaper_clear";
    private static final String ACTION_TELEPORT_MODE = "teleport_mode";
    private static final String ACTION_PAGE_NAV_STYLE = "page_nav_style";
    private static final String ACTION_HISTORY_CLEAR = "history_clear";
    private static final String ACTION_CACHE_CLEAR = "cache_clear";
    private static final String ACTION_TEST_SOUND = "test_sound";

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
     * Page transition card: subtitle shows the current slide/zoom style;
     * tapping cycles between the two and saves (the handler lives on the
     * screen, like every other action card).
     */
    private SettingsItem pageNavStyleItem() {
        return new SettingsItem("page_nav_style",
                "atomchat.settings.appearance.page_nav_style",
                "atomchat.settings.appearance.page_nav_style.desc",
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

    /**
     * In-page section tabs: the segmented chip row at the top of the sections
     * whose full row list is too tall to scan (appearance, chat). Each chip
     * shows one natural group; switching crossfades the two row sets over
     * {@link #CHIP_SWITCH_MS} with a 3px micro-slide. Privacy and about stay
     * single-page (no chips) - their lists fit one screen.
     */
    public record SectionChip(String id, String labelKey) {
    }

    private static final List<SectionChip> APPEARANCE_CHIPS = List.of(
            new SectionChip("theme", "atomchat.settings.appearance.theme"),
            new SectionChip("display", "atomchat.settings.group.display"),
            new SectionChip("adjust", "atomchat.settings.group.adjust"));
    private static final List<SectionChip> CHAT_CHIPS = List.of(
            new SectionChip("messages", "atomchat.settings.group.chat.messages"),
            new SectionChip("history", "atomchat.settings.group.chat.history"),
            new SectionChip("notify", "atomchat.settings.group.chat.notify"),
            new SectionChip("teleport", "atomchat.settings.group.chat.teleport"));
    /** Crossfade duration of a chip switch. */
    private static final long CHIP_SWITCH_MS = 110L;
    /** Active chip index per section id (chip-less sections never read it). */
    private final Map<String, Integer> activeChip = new HashMap<>();
    /** Chip id a switch crossfade is coming from; null when settled. */
    private String chipSwitchFromId;
    private long chipSwitchAtMs;

    // The chip row reuses BottomTabBar's cell geometry and clock: equal cells
    // from the layout rect, a sliding selection capsule, a pure-colour hover
    // wash. The bounce is compact(), like the tab capsules; unlike them it
    // wraps the label only, because the capsule is one shared shape that
    // slides between cells and a per-cell hover spring on it would fight the
    // slide.
    private final Animator chipIndicator = new Animator(Easing::easeInOutCubic);
    private final Map<Integer, Float> chipHover = new HashMap<>();
    private final Map<Integer, PressScale> chipScale = new HashMap<>();
    private int pressedChip = -1;
    private int lastChipIndex = -1;
    /** Hover row of the interactive (incoming) layer, fed to rowHover decay. */
    private int lastInteractiveHover = -1;

    /** The chip row of a section, or an empty list when the section has none. */
    public List<SectionChip> chips(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> APPEARANCE_CHIPS;
            case CHAT -> CHAT_CHIPS;
            default -> List.of();
        };
    }

    /** Index of the chip whose rows are showing. */
    public int activeChipIndex(SettingsSection section) {
        List<SectionChip> sectionChips = chips(section);
        if (sectionChips.isEmpty()) {
            return -1;
        }
        int index = activeChip.getOrDefault(section.id(), 0);
        return Math.max(0, Math.min(index, sectionChips.size() - 1));
    }

    private String activeChipId(SettingsSection section) {
        List<SectionChip> sectionChips = chips(section);
        return sectionChips.isEmpty() ? ""
                : sectionChips.get(activeChipIndex(section)).id();
    }

    /** Reserved band height above the rows (zero for chip-less sections). */
    public float chipBarHeight(SettingsSection section) {
        return chips(section).isEmpty() ? 0.0F : chipBarHeight();
    }

    /** The chip bar's height, from the same tokens {@link UiLayout} uses. */
    private static float chipBarHeight() {
        return UiTokens.CHIP_PILL_H + UiTokens.TAB_EDGE_PAD * 2.0F;
    }


    /** Chip index under the pointer, or -1 (equal cells, like the tab bar). */
    public int chipAt(SettingsSection section, float vmx, float vmy, UiLayout layout) {
        List<SectionChip> sectionChips = chips(section);
        if (sectionChips.isEmpty() || layout.chipBar.w() <= 0.0F) {
            return -1;
        }
        float cellW = layout.chipBar.w() / sectionChips.size();
        float inset = UiTokens.TAB_EDGE_PAD;
        for (int i = 0; i < sectionChips.size(); i++) {
            UiLayout.Rect cell = layout.chipRect(i, sectionChips.size());
            float cx = layout.chipBar.x() + cellW * (i + 0.5F);
            float cy = layout.chipBar.y() + layout.chipBar.h() / 2.0F;
            if (Math.abs(vmx - cx) <= cell.w() / 2.0F + inset
                    && Math.abs(vmy - cy) <= cell.h() / 2.0F + inset) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Switches the visible chip and arms the crossfade. No-op on the active
     * chip; with decorative motion off the fade is gated at draw time, so the
     * rows swap instantly.
     */
    public void selectChip(SettingsSection section, int index) {
        List<SectionChip> sectionChips = chips(section);
        if (index < 0 || index >= sectionChips.size()
                || index == activeChipIndex(section)) {
            return;
        }
        chipSwitchFromId = activeChipId(section);
        chipSwitchAtMs = System.currentTimeMillis();
        activeChip.put(section.id(), index);
    }

    /** Rows of one chip by id (the crossfade's outgoing layer). */
    private List<Row> rowsForChip(SettingsSection section, String chipId) {
        return switch (section) {
            case APPEARANCE -> appearanceRows(chipId);
            case CHAT -> chatRows(chipId);
            default -> rows(section);
        };
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
    /**
     * Time constant of the fold animation: a group's children neither pop in
     * nor pop out any more, they grow/shrink behind an exponential settle at
     * the same 50ms tau the ZOOM page-nav phases use (~170ms to visually
     * land), so folding reads as one continuous motion of the same family.
     */
    private static final float GROUP_FOLD_TAU_MS = 50.0F;
    /** Live expansion of each fold group, group id → 0..1 (1 = fully
     *  expanded). Stepped every frame toward the target the collapsed set
     *  names; rows of a group scale their height, alpha and hit bounds by
     *  this value. Groups not yet in the map answer their target directly
     *  (see {@link #expansionOf}), so the very first frame never animates
     *  from a wrong default. */
    private final Map<String, Float> groupExpansion = new HashMap<>();
    /** Below this expansion a group child is fully invisible and untaggable
     *  (no draw, no hit); the row list itself only drops the children once
     *  the value snaps to exactly 0, so the scroll height converges without
     *  a visible end-of-animation jump. */
    private static final float GROUP_FOLD_MIN_DRAWN = 0.01F;
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
            return GROUP_MOD_INFO;
        }
        if (LABEL_THIRD_PARTY.equals(labelKey)) {
            return GROUP_THIRD_PARTY;
        }
        return null;
    }

    /**
     * Theme picker row: nine mini-panel preview cards (frosted + eight colour
     * presets) in a horizontal strip, each drawn in its own preset palette;
     * the active one wears an accent edge. Replaces the old parked theme_cycle
     * action card.
     */
    private static final int THEME_CARD_COUNT = 1 + ThemeService.presets().length;

    // Horizontal theme-card strip: the wheel glides toward a target, a press
    // drag follows the pointer 1:1, and a press that never travels selects.
    private float themeStripScroll;
    private float themeStripTarget;
    private float themeStripAnimFrom;
    private long themeStripAnimStart;
    private boolean themeStripAnimActive;
    private float themeStripDragStartX;
    private float themeStripDragStartY;
    private float themeStripDragStartScroll;
    private boolean themeStripPressed;
    private boolean themeStripDragged;
    private int pressedThemeCard = -1;
    private final Map<Integer, PressScale> themeCardScale = new HashMap<>();

    private final Map<String, ToggleSwitch> switches = new HashMap<>();
    private final Map<Integer, Float> rowHover = new HashMap<>();
    /** Per-row press bounce: compact(), so a row lifts 1.04 on hover and
     *  dips 0.95 while pressed. */
    private final Map<Integer, PressScale> rowPress = new HashMap<>();
    /** Press springs of the merged about-page cards, keyed by each card's
     *  first row index — modinfo and third-party today; the map keeps the
     *  same reset() contract as the other per-row spring maps. Each card
     *  carries the one bounce: its child rows scale together, never each
     *  alone. */
    private final Map<Integer, PressScale> aboutCardPress = new HashMap<>();
    /** Row index under an active press for the bounce; -1 = none. */
    private int pressedRow = -1;
    /** Per-swatch bounce scales, keyed by {@link #swatchKey}. */
    private final Map<Long, PressScale> swatchScale = new HashMap<>();
    /** Swatch hover/press state (row-local swatch index), -1 = none. */
    private int hoveredSwatch = -1;
    private int pressedSwatch = -1;
    /** Index of the colour row the swatch state belongs to; -1 = none. */
    private int swatchRowIndex = -1;
    /** Row index being drawn this iteration (switch press attribution). */
    private int drawnRowIndex = -1;
    /** Render-time pointer, for per-control hover (the switch, swatches). */
    private float pointerX;
    private float pointerY;
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
        return Component.translatable(key).getString();
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
            case SLIDER, COLOR -> UiTokens.SETTINGS_SLIDER_ROW_H;
            case THEMES -> UiTokens.SETTINGS_THEME_ROW_H;
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
        float maxWidth = descriptionMaxWidth(row, rowCardWidth(layout));
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
        } else if (ACTION_PAGE_NAV_STYLE.equals(row.actionId())) {
            subtitle = tr(AtomChatConfig.get().pageNavStyle
                    == AtomChatConfig.PageNavStyle.ZOOM
                    ? "atomchat.settings.appearance.page_nav_style.zoom"
                    : "atomchat.settings.appearance.page_nav_style.slide");
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

    /** Rows in display order for the given section (active chip applied). */
    public List<Row> rows(SettingsSection section) {
        return switch (section) {
            case CHAT -> chatRows(activeChipId(section));
            case APPEARANCE -> appearanceRows(activeChipId(section));
            case PRIVACY -> privacyRows();
            case ABOUT -> aboutRows();
        };
    }

    /**
     * Adds a collapsible group: the label is always present; the children are
     * added while the group is expanded <em>or still folding shut</em> — the
     * live expansion above 0 keeps them in the list so their heights shrink
     * through {@link #rowAdvance} frame by frame instead of vanishing. Once
     * the expansion snaps to 0 the children leave the list for good.
     * Non-foldable labels (null id) always show their children.
     */
    private void addGroup(List<Row> rows, String labelKey, Runnable children) {
        rows.add(Row.ofLabel(labelKey));
        String id = foldableGroup(labelKey);
        if (id == null || !collapsedColorGroups.contains(id)
                || expansionOf(id) > 0.0F) {
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

    /** One chip of the chat page: the rows of that group, no group label
     *  (the chip itself names the group). */
    private List<Row> chatRows(String chip) {
        List<Row> rows = new ArrayList<>();
        switch (chip == null ? "messages" : chip) {
            case "history" -> {
                addSwitches(rows, SettingsSection.CHAT, "history");
                addSliders(rows, SettingsSection.CHAT, "history_retention");
                rows.add(Row.ofAction(ACTION_HISTORY_CLEAR, historyClearItem()));
            }
            case "notify" -> {
                addSwitches(rows, SettingsSection.CHAT,
                        "mention_banner", "mention_sound", "whisper_banner", "whisper_sound");
                addSliders(rows, SettingsSection.CHAT, "notify_volume");
                rows.add(Row.ofAction(ACTION_TEST_SOUND, testSoundItem()));
            }
            case "teleport" ->
                    rows.add(Row.ofAction(ACTION_TELEPORT_MODE, teleportModeItem()));
            default -> {
                addSwitches(rows, SettingsSection.CHAT,
                        "entry", "poke", "images", "anti_spam", "compact_messages");
                addSliders(rows, SettingsSection.CHAT, "timestamp");
            }
        }
        return rows;
    }

    /** One chip of the appearance page. The theme chip carries the preview
     *  strip, the corner knob and the folded colour palette; display holds the
     *  chrome switches plus the wallpaper cards; adjust the five sliders. */
    private List<Row> appearanceRows(String chip) {
        List<Row> rows = new ArrayList<>();
        switch (chip == null ? "theme" : chip) {
            case "display" -> {
                addSwitches(rows, SettingsSection.APPEARANCE, "blur", "outline", "motion");
                // Style pick where the old zoom-enter switch used to sit: the
                // subtitle shows the live value, a tap cycles SLIDE <-> ZOOM.
                rows.add(Row.ofAction(ACTION_PAGE_NAV_STYLE, pageNavStyleItem()));
                rows.add(Row.ofAction(ACTION_WALLPAPER_PICK, wallpaperPickItem()));
                if (WallpaperStore.isSet()) {
                    rows.add(Row.ofAction(ACTION_WALLPAPER_CLEAR, wallpaperClearItem()));
                }
            }
            case "adjust" -> addSliders(rows, SettingsSection.APPEARANCE,
                    "opacity", "width", "scale", "contentscale", "cardtint");
            default -> {
                // Theme strip first - one tap to a whole new look.
                rows.add(Row.ofThemes());
                // Corner radius is an independent knob - no preset writes it -
                // so its continuous slider lives in the theme chip, showing
                // the current value; 0 gives square corners everywhere.
                addSliders(rows, SettingsSection.APPEARANCE, "corner_radius");
                // The full colour palette, folded away by default.
                addGroup(rows, LABEL_APPEARANCE_ADVANCED, () -> {
                    for (SettingsColor color : SettingsCatalog.colors(SettingsSection.APPEARANCE)) {
                        rows.add(Row.ofColor(color));
                    }
                });
            }
        }
        return rows;
    }

    private List<Row> privacyRows() {
        List<Row> rows = new ArrayList<>();
        addGroup(rows, "atomchat.settings.group.display", () ->
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

    /**
     * Fold group the row at {@code index} belongs to: the nearest foldable
     * label <em>strictly above</em> it (a group's children always sit directly
     * under their label, and {@link #addGroup} emits that pair atomically),
     * or null for rows outside any foldable group. The exclusive bound keeps
     * the label itself out of its own group — the heading must stay at full
     * height and full alpha at every expansion. A foldable heading also never
     * belongs to the group above it: adjacent groups would otherwise file the
     * lower heading under the upper one, and collapsing the upper group would
     * fold the lower heading — its own fold toggle with it — away for good
     * (the about page's modinfo -> thirdparty -> advanced run hits exactly
     * that). Deriving membership from the list itself keeps rendering,
     * hit-testing and measurement in lockstep without tagging rows.
     */
    private static String owningGroup(List<Row> rows, int index) {
        if (index >= 0 && index < rows.size() && rows.get(index).kind() == RowKind.LABEL
                && foldableGroup(rows.get(index).labelKey()) != null) {
            return null;
        }
        String group = null;
        for (int i = 0; i < index && i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind() == RowKind.LABEL) {
                group = foldableGroup(row.labelKey());
            }
        }
        return group;
    }

    /** Live expansion of the group owning row {@code index}; 1.0 outside
     *  groups. Every geometry consumer (row advance, rect, hit-test, draw)
     *  funnels through here, so a folding group can never disagree with
     *  itself. */
    private float rowExpansion(List<Row> rows, int index) {
        String group = owningGroup(rows, index);
        return group == null ? 1.0F : expansionOf(group);
    }

    /** Expansion of one fold group: the live animated value, or the state
     *  the collapsed set names for a group that has never animated. */
    private float expansionOf(String group) {
        Float value = groupExpansion.get(group);
        return value != null ? value
                : (collapsedColorGroups.contains(group) ? 0.0F : 1.0F);
    }

    /**
     * One frame of the fold animation: registers unknown fold groups of the
     * current rows at their target (no first-frame slide from a wrong
     * default), then steps every known group toward its target — the
     * collapsed set is the single source of truth for the direction.
     */
    private void stepGroupExpansions(List<Row> rows, float dtMs) {
        for (Row row : rows) {
            if (row.kind() == RowKind.LABEL) {
                String id = foldableGroup(row.labelKey());
                if (id != null) {
                    groupExpansion.putIfAbsent(id,
                            collapsedColorGroups.contains(id) ? 0.0F : 1.0F);
                }
            }
        }
        for (Map.Entry<String, Float> entry : groupExpansion.entrySet()) {
            float target = collapsedColorGroups.contains(entry.getKey()) ? 0.0F : 1.0F;
            entry.setValue(UiMotion.expApproach(entry.getValue(), target, dtMs,
                    GROUP_FOLD_TAU_MS));
        }
    }

    /**
     * Vertical slot the row at {@code index} occupies: its height times the
     * owning group's expansion plus the row gap below it, scaled by the
     * <em>following</em> row's expansion. Owning the gap to the next row is
     * what keeps the fold continuous at both ends: a child's leading gap
     * grows with the child itself, the gap to the next section is owned by
     * the child while it is still in the list (full — the next section keeps
     * its breathing room) and by that section after the children leave (full
     * — same value), and the label's gap to its first child opens and closes
     * with that child instead of holding a phantom gap under a folded
     * heading. At expansion 1 every factor is 1: the plain
     * {@code rowHeight + gap} walk.
     */
    /**
     * True when the row at {@code index} is a child of one of the about
     * page's merged-card fold groups (mod info, third party). Those children
     * render as one merged card ({@link #drawAboutCard}), so {@link
     * #rowAdvance} collapses the gap between two of them and the draw pass
     * routes the whole run through the card. Membership comes from the row
     * list itself (via {@link #owningGroup}), like every other geometry
     * consumer.
     */
    private static boolean isAboutCardChild(List<Row> rows, int index) {
        String group = owningGroup(rows, index);
        return GROUP_THIRD_PARTY.equals(group) || GROUP_MOD_INFO.equals(group);
    }

    private float rowAdvance(List<Row> rows, int index, UiLayout layout) {
        float expansion = rowExpansion(rows, index);
        float nextExpansion = index + 1 < rows.size() ? rowExpansion(rows, index + 1) : 1.0F;
        // Two children of a merged about card sit contiguously — the hairline
        // divider owns the seam inside the card — so the inter-row gap
        // between them is zero. Measurement, rects and the hit-test all walk
        // this method, so the card's geometry cannot drift apart.
        float gap = UiTokens.SETTINGS_ROW_GAP * nextExpansion;
        if (index + 1 < rows.size()
                && isAboutCardChild(rows, index)
                && isAboutCardChild(rows, index + 1)) {
            gap = 0.0F;
        }
        return rowHeight(rows.get(index), layout) * expansion + gap;
    }

    public float measureContent(UiLayout layout, SettingsSection section) {
        List<Row> rows = rows(section);
        if (rows.isEmpty()) {
            return UiTokens.ROOT_CONTENT_GAP + chipBarHeight(section);
        }
        float total = UiTokens.ROOT_CONTENT_GAP + chipBarHeight(section);
        for (int i = 0; i < rows.size(); i++) {
            // Same walk as rowAdvance (the card's zero child-gap lives there),
            // except the last row: nothing follows it, so no gap either way.
            total += i < rows.size() - 1
                    ? rowAdvance(rows, i, layout)
                    : rowHeight(rows.get(i), layout) * rowExpansion(rows, i);
        }
        return total;
    }

    /** Top edge of the scrollable rows: content gap plus the chip band. */
    private float contentTop(UiLayout layout, SettingsSection section) {
        return layout.list.y() + UiTokens.ROOT_CONTENT_GAP + chipBarHeight(section);
    }

    /**
     * Horizontal clearance between a row card and the scroll clip. A card used
     * to be drawn at the clip's full width, so the hover bounce grew it
     * straight into the clip and the clip sheared the rounded ends flat on
     * every hover. The hover lift is now a 4px-per-side pixel budget
     * ({@link PressScale#BUDGET_PX}) and the release overshoot tops out near
     * 1.31x that budget, so ~5.3px per side at the peak; the 8px token clears
     * both with room for the card shadow tail. The clip itself stays full
     * width: it is the scroll viewport, the card is what gets inset.
     *
     * <p>Read from {@link UiTokens#ROW_CLIP_INSET} rather than restated: the
     * conversation rows and the profile info rows have the identical defect at
     * the identical value, and three private copies is how they drift.</p>
     */
    private static final float ROW_CLIP_INSET = UiTokens.ROW_CLIP_INSET;

    /** Width one row card gets: the list minus the clip clearance on both sides. */
    private static float rowCardWidth(UiLayout layout) {
        return layout.list.w() - ROW_CLIP_INSET * 2.0F;
    }

    private UiLayout.Rect rowRect(SettingsSection section, List<Row> rows, int index,
                                  float scrollY, UiLayout layout) {
        float y = contentTop(layout, section) - scrollY;
        for (int i = 0; i < index; i++) {
            y += rowAdvance(rows, i, layout);
        }
        return new UiLayout.Rect(layout.list.x() + ROW_CLIP_INSET, y,
                rowCardWidth(layout), rowHeight(rows.get(index), layout) * rowExpansion(rows, index));
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
        pointerX = vmx;
        pointerY = vmy;

        List<Row> rows = rows(section);
        // Fold animation clock: advances every group's expansion toward the
        // state the collapsed set names, before drawing consumes it. The row
        // list above was built against last frame's values; at the one moment
        // that matters (the snap to 0) those rows contribute exactly zero
        // height, so membership and geometry never visibly disagree.
        stepGroupExpansions(rows, dt);
        // Chip switch crossfade: the outgoing rows fade out while sliding up
        // and away, the incoming rows fade in while sliding up into place.
        boolean chipFading = chipSwitchFromId != null && Animations.enabled()
                && now - chipSwitchAtMs < CHIP_SWITCH_MS;
        float t = chipFading
                ? Math.min(1.0F, (now - chipSwitchAtMs) / (float) CHIP_SWITCH_MS) : 1.0F;
        if (!chipFading) {
            chipSwitchFromId = null;
        }

        float barH = chipBarHeight(section);
        canvas.save();
        try {
            // The chip band owns the top of the list: rows clip below it, so a
            // scrolled list never draws through the pills.
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y() + barH,
                    layout.list.w(), layout.list.h() - barH, 0.0F);
            if (chipFading) {
                drawRows(canvas, layout, section,
                        rowsForChip(section, chipSwitchFromId), vmx, vmy, scrollY,
                        accent, dt, 1.0F - t, -t * s(3), false);
            }
            drawRows(canvas, layout, section, rows, vmx, vmy, scrollY, accent, dt,
                    chipFading ? t : 1.0F, chipFading ? (1.0F - t) * s(3) : 0.0F, true);
        } finally {
            canvas.restore();
        }

        if (barH > 0.0F) {
            updateChipMotion(layout, section, dt);
            drawChipBar(canvas, layout, section, accent, dt);
        }

        int hovered = lastInteractiveHover;
        hoveredIndex = hovered;
        if (hovered >= 0) {
            rowHover.putIfAbsent(hovered, 0.0F);
        }
        for (Integer key : new ArrayList<>(rowHover.keySet())) {
            float target = key == hovered ? 1.0F : 0.0F;
            rowHover.put(key, UiMotion.approach(rowHover.get(key), target, dt, UiMotion.HOVER_MS));
        }

        if (section == SettingsSection.PRIVACY && blockedPlayers().isEmpty()) {
            drawEmptyBlocked(canvas, layout, section, rows, scrollY);
        }
        advanceSettle(now);
    }

    /**
     * One chip layer: the row loop. {@code alpha}/{@code dy} carry the chip
     * switch crossfade; the outgoing layer draws with {@code interactive}
     * false — it must not advance hover/press state, the pointer already
     * belongs to the incoming chip.
     */
    private void drawRows(Canvas canvas, UiLayout layout, SettingsSection section,
                          List<Row> rows, float vmx, float vmy, float scrollY,
                          int accent, float dt, float alpha, float dy, boolean interactive) {
        if (interactive) {
            lastInteractiveHover = -1;
        }
        if (rows.isEmpty() || alpha <= 0.005F) {
            return;
        }
        Font buttonFont = FontManager.font(UiTokens.FONT_QUOTE);
        int hovered = -1;
        canvas.save();
        Paint layer = null;
        try {
            if (alpha < 0.995F) {
                layer = new Paint().setAlphaf(alpha);
                canvas.saveLayer(io.github.humbleui.types.Rect.makeXYWH(
                        layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h()), layer);
            }
            canvas.translate(0.0F, dy);
            for (int i = 0; i < rows.size(); i++) {
                // The about page's modinfo and third-party children each
                // render as ONE merged card (the owner's single-card +
                // hairline language): the first child draws the whole block,
                // the rest are already on the canvas behind it.
                if (isAboutCardChild(rows, i)) {
                    if (i > 0 && isAboutCardChild(rows, i - 1)) {
                        continue;
                    }
                    int end = i;
                    while (end < rows.size() && isAboutCardChild(rows, end)) {
                        end++;
                    }
                    hovered = drawAboutCard(canvas, layout, section, rows,
                            i, end, scrollY, vmx, vmy, buttonFont, accent, dt,
                            interactive, hovered);
                    i = end - 1; // the loop's own i++ lands past the card
                    continue;
                }
                Row row = rows.get(i);
                UiLayout.Rect rect = rowRect(section, rows, i, scrollY, layout);
                if (rect.bottom() < layout.list.y() || rect.y() > layout.list.bottom()) {
                    continue;
                }
                // Below the fold's draw floor a group child takes no part in
                // anything: not drawn, not hovered, not press-tracked. Its
                // slot is near-zero already, so nothing below shifts.
                float expansion = rowExpansion(rows, i);
                if (expansion < GROUP_FOLD_MIN_DRAWN) {
                    continue;
                }
                if (interactive) {
                    drawnRowIndex = i;
                }
                // LABEL rows hover only when they fold a group - a plain
                // heading is not clickable, so it must not answer the pointer.
                boolean over = interactive
                        && (row.kind() != RowKind.LABEL || foldableGroup(row.labelKey()) != null)
                        && vmx >= rect.x() && vmx <= rect.right()
                        && vmy >= rect.y() && vmy <= rect.bottom();
                if (over) {
                    hovered = i;
                }
                // Colour rows resolve the pointer to a row-local swatch index
                // so drawColor can bounce the one under it (-1 = none).
                if (row.kind() == RowKind.COLOR) {
                    hoveredSwatch = interactive ? swatchAt(row.color(), rect, vmx, vmy) : -1;
                    swatchRowIndex = interactive ? i : -1;
                } else {
                    hoveredSwatch = -1;
                    swatchRowIndex = -1;
                }
                // Draw from the animated value only. Never force it to 1 while
                // hovered — that is what made the highlight snap in instead of
                // fading in over the same 90ms the toolbar buttons use.
                // Rows press-scale around their centre and hit-tests keep using
                // unscaled coordinates, like the message entrance.
                //
                // The width budget keeps a full settings row's lift inside the
                // s(8) row gap on its own, without a trimmed tier: the edge
                // travel is capped at BUDGET_PX per side at any row width.
                // Foldable LABEL rows take a spring too: they toggle whole
                // colour groups on click and used to sit dead under the
                // pointer. Plain headings still get none.
                PressScale press = row.kind() == RowKind.LABEL && foldableGroup(row.labelKey()) == null ? null
                        : interactive ? rowPress.computeIfAbsent(i, k -> PressScale.bounce())
                        : rowPress.get(i);
                if (press != null && interactive) {
                    press.update(i == hovered, i == pressedRow, dt, Animations.enabled(), rect.w());
                }
                canvas.save();
                Paint foldFade = null;
                try {
                    if (expansion < 0.995F) {
                        // Fold fade-in: the child's content rises from up to
                        // s(4) below its slot while fading with the expansion,
                        // the same settle the chip crossfade's incoming layer
                        // rides (its (1-t)*s(3)); both ends of the fold are
                        // exactly the resting layout. The padded saveLayer
                        // keeps press overshoot and shadow tail inside the
                        // fade instead of punching through at full weight.
                        canvas.translate(0.0F, UiTokens.s(4) * (1.0F - expansion));
                        float pad = UiTokens.s(12);
                        foldFade = new Paint().setAlphaf(expansion);
                        canvas.saveLayer(io.github.humbleui.types.Rect.makeXYWH(
                                rect.x() - pad, rect.y() - pad,
                                rect.w() + pad * 2.0F, rect.h() + pad * 2.0F), foldFade);
                    }
                    if (press != null) {
                        press.begin(canvas, rect.x() + rect.w() / 2.0F, rect.y() + rect.h() / 2.0F);
                    }
                    try {
                        drawRow(canvas, row, rect, rowHover.getOrDefault(i, 0.0F),
                                buttonFont, accent, dt);
                    } finally {
                        if (press != null) {
                            canvas.restore();
                        }
                    }
                } finally {
                    if (foldFade != null) {
                        foldFade.close();
                        canvas.restore();
                    }
                    canvas.restore();
                }
            }
        } finally {
            if (layer != null) {
                layer.close();
                canvas.restore();
            }
            canvas.restore();
        }
        if (interactive) {
            lastInteractiveHover = hovered;
        }
    }

    /** The section chip row, sharing the bottom tab bar's cell geometry: equal
     *  cells from {@code layout.chipBar}, the accent capsule for the selection
     *  (sliding on the tab clock), an accent-tinted hover wash on the
     *  unselected cells, and a bounce on
     *  the whole chip cell. Like {@code BottomTabBar}, the per-cell spring
     *  wraps the entire cell (wash plus label) while the shared capsule rides
     *  the selected cell's spring: a capsule of its own would fight the slide. */
    private void drawChipBar(Canvas canvas, UiLayout layout, SettingsSection section,
                             int accent, float dtMs) {
        List<SectionChip> chips = chips(section);
        if (chips.isEmpty() || layout.chipBar.w() <= 0.0F) {
            return;
        }
        Font font = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        int activeIndex = activeChipIndex(section);
        int count = chips.size();
        float inset = UiTokens.TAB_EDGE_PAD;
        float cellW = layout.chipBar.w() / count;

        // Selection capsule: the accent at full strength, sliding between cells
        // on the same clock the bottom tab bar uses. Translucent white used to
        // sit here and disappeared on the light themes. The capsule rides the
        // selected cell's spring (BottomTabBar's pattern), so hovering the
        // active chip lifts the capsule with it.
        UiLayout.Rect first = layout.chipRect(0, count);
        float capsuleX = layout.chipBar.x() + chipIndicator.getValue() * cellW + inset;
        PressScale activeScale = chipScale.get(activeIndex);
        if (activeScale != null) {
            activeScale.begin(canvas, capsuleX + first.w() / 2.0F, first.y() + first.h() / 2.0F);
        }
        try {
            SkiaDraw.drawRoundedRect(canvas, capsuleX, first.y(), first.w(), first.h(),
                    UiTokens.radius(8), UiTokens.accentFill());
        } finally {
            if (activeScale != null) {
                canvas.restore();
            }
        }

        // One pass per cell: the bounce scales the whole chip cell — hover
        // wash and label together — around the cell's centre, like the tab
        // bar's cells. Scaling the 15px label alone moved its edge 0.6px and
        // read as nothing at all. The selected cell draws no wash: an active
        // chip already answers the pointer with the capsule's bounce alone,
        // and a hover wash stacked on the capsule would only muddy the accent
        // (user-decided language: hover on a selected cell is bounce-only).
        for (int i = 0; i < count; i++) {
            UiLayout.Rect cell = layout.chipRect(i, count);
            boolean selected = i == activeIndex;
            Font labelFont = selected
                    ? FontManager.boldFont(UiTokens.SETTINGS_TILE_SUB) : font;
            float cx = cell.x() + cell.w() / 2.0F;
            float cy = cell.y() + cell.h() / 2.0F;
            PressScale scale = chipScale.get(i);
            if (scale != null) {
                scale.begin(canvas, cx, cy);
            }
            try {
                Float hov = chipHover.get(i);
                if (!selected && hov != null && hov > 0.01F) {
                    SkiaDraw.drawRoundedRect(canvas, cell.x(), cell.y(), cell.w(), cell.h(),
                            UiTokens.radius(8), UiTokens.cardHover(hov));
                }
                SkiaFontRenderer.drawTextCentered(canvas, labelFont,
                        tr(chips.get(i).labelKey()), cx, cy,
                        selected ? UiTokens.onAccent(UiTokens.accentFill()) : sec(200));
            } finally {
                if (scale != null) {
                    canvas.restore();
                }
            }
        }
    }

    /** Advances the chip hover washes, press springs and the selection slide.
     *  Same cadence as {@code BottomTabBar.update}: once per frame. */
    public void updateChipMotion(UiLayout layout, SettingsSection section, float dtMs) {
        List<SectionChip> chips = chips(section);
        if (chips.isEmpty() || layout.chipBar.w() <= 0.0F) {
            return;
        }
        int activeIndex = activeChipIndex(section);
        if (lastChipIndex < 0) {
            chipIndicator.setValue(activeIndex);
        } else if (activeIndex != lastChipIndex) {
            chipIndicator.animateTo(UiMotion.TAB_MS, activeIndex);
        }
        lastChipIndex = activeIndex;
        chipIndicator.update(dtMs);

        int hovered = chipAt(section, pointerX, pointerY, layout);
        for (int i = 0; i < chips.size(); i++) {
            boolean over = i == hovered;
            float next = UiMotion.approach(chipHover.getOrDefault(i, 0.0F),
                    over ? 1.0F : 0.0F, dtMs, UiMotion.HOVER_MS);
            if (next < 0.01F && !over) {
                chipHover.remove(i);
            } else {
                chipHover.put(i, next);
            }
            // The chip row is a set of adjacent equal cells; the width budget
            // keeps each chip's lift inside its own slot.
            chipScale.computeIfAbsent(i, key -> PressScale.bounce())
                    .update(over, over && pressedChip == i, dtMs, Animations.enabled(),
                            layout.chipRect(i, chips.size()).w());
        }
    }

    private void drawEmptyBlocked(Canvas canvas, UiLayout layout, SettingsSection section,
                                  List<Row> rows, float scrollY) {
        UiLayout.Rect last = rowRect(section, rows, rows.size() - 1, scrollY, layout);
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
        UiCards.drawCard(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), hover);
        drawRowContent(canvas, row, rect, hover, buttonFont, accent, dtMs);
    }

    /** Everything a non-label row draws <em>inside</em> its card surface.
     *  Split from {@link #drawRow} so the merged third-party card can lay
     *  its child rows on one shared surface instead of a card each. */
    private void drawRowContent(Canvas canvas, Row row, UiLayout.Rect rect, float hover,
                                Font buttonFont, int accent, float dtMs) {
        switch (row.kind()) {
            case HERO -> drawHero(canvas, rect);
            case SWITCH -> drawSwitch(canvas, row, rect, accent, dtMs);
            case SLIDER -> drawSlider(canvas, row, rect, accent);
            case COLOR -> drawColor(canvas, row, rect, dtMs);
            case THEMES -> drawThemes(canvas, rect, dtMs);
            case INFO -> drawInfo(canvas, row, rect);
            case BLOCKED -> drawBlocked(canvas, row, rect, hover, buttonFont);
            case ACTION -> drawAction(canvas, row, rect, hover);
            default -> {
            }
        }
    }

    /**
     * One of the about page's merged-card blocks (mod info, third party):
     * ONE card holding every child row of the fold group — the owner's
     * single-card + hairline language, the same structure {@code
     * ProfilePage.drawInfoRows} uses for its info card. Rows sit
     * contiguously inside the card ({@link #rowAdvance} zeroes the gap
     * between two children of this group), separated by a 1px hairline
     * divider indented to the row pad; each row washes on hover (per-row
     * alpha, clipped to the card's rounding); and the whole card rides one
     * {@link PressScale#bounce()} keyed on the pointer being anywhere in the
     * card, wrapped begin/try/finally around the entire draw. The rows carry
     * no press scale of their own — INFO rows have no press semantics, and
     * per-row lifts would read as separate cards, not one surface.
     * Hit-tests keep unscaled coordinates: clicking a row still resolves to
     * that row's {@link RowHit} (link rows open their URI), the card's
     * bounce is draw-only.
     *
     * @param start index of the card's first child row
     * @param end   exclusive bound (the row after the card's last child)
     * @return the row index under the pointer if it lies on a card child,
     *         else the caller's running {@code hovered} state
     */
    private int drawAboutCard(Canvas canvas, UiLayout layout, SettingsSection section,
                              List<Row> rows, int start, int end, float scrollY,
                              float vmx, float vmy, Font buttonFont, int accent,
                              float dt, boolean interactive, int hovered) {
        float expansion = rowExpansion(rows, start);
        // Below the fold's draw floor the whole card takes no part in
        // anything, same as a lone group child in the row loop.
        if (expansion < GROUP_FOLD_MIN_DRAWN) {
            return hovered;
        }
        UiLayout.Rect first = rowRect(section, rows, start, scrollY, layout);
        UiLayout.Rect last = rowRect(section, rows, end - 1, scrollY, layout);
        // The children tile the card contiguously at every expansion (their
        // heights and the advances between them both scale with the group),
        // so the union of first and last row rect IS the card, folded state
        // included.
        UiLayout.Rect card = new UiLayout.Rect(first.x(), first.y(), first.w(),
                last.bottom() - first.y());
        if (card.bottom() < layout.list.y() || card.y() > layout.list.bottom()) {
            return hovered;
        }
        // Hover = the pointer anywhere in the merged card; the pressed half
        // follows the armed row (any child), mirroring the per-row spring
        // contract where press is armed wherever the press landed.
        boolean cardOver = interactive
                && vmx >= card.x() && vmx <= card.right()
                && vmy >= card.y() && vmy <= card.bottom();
        PressScale press = interactive
                ? aboutCardPress.computeIfAbsent(start, k -> PressScale.bounce())
                : aboutCardPress.get(start);
        if (press != null && interactive) {
            press.update(cardOver, pressedRow >= start && pressedRow < end,
                    dt, Animations.enabled(), card.w());
        }
        canvas.save();
        Paint foldFade = null;
        try {
            if (expansion < 0.995F) {
                // Same fold settle the per-row path rides: the card rises
                // from up to s(4) below its slot while fading with the group
                // expansion; the padded saveLayer keeps press overshoot and
                // shadow tail inside the fade.
                canvas.translate(0.0F, UiTokens.s(4) * (1.0F - expansion));
                float fadePad = UiTokens.s(12);
                foldFade = new Paint().setAlphaf(expansion);
                canvas.saveLayer(io.github.humbleui.types.Rect.makeXYWH(
                        card.x() - fadePad, card.y() - fadePad,
                        card.w() + fadePad * 2.0F, card.h() + fadePad * 2.0F), foldFade);
            }
            if (press != null) {
                press.begin(canvas, card.x() + card.w() / 2.0F,
                        card.y() + card.h() / 2.0F);
            }
            try {
                UiCards.drawCard(canvas, card.x(), card.y(), card.w(), card.h(),
                        UiTokens.settingsRowRadius(), 0.0F);
                canvas.save();
                try {
                    // Washes and dividers clip to the card's rounding, so the
                    // first and last rows' bands never bleed past the corners.
                    SkiaDraw.clip(canvas, card.x(), card.y(), card.w(), card.h(),
                            UiTokens.settingsRowRadius());
                    float pad = UiTokens.SETTINGS_ROW_PAD;
                    for (int j = start; j < end; j++) {
                        Row row = rows.get(j);
                        UiLayout.Rect rect = rowRect(section, rows, j, scrollY, layout);
                        boolean over = interactive
                                && vmx >= rect.x() && vmx <= rect.right()
                                && vmy >= rect.y() && vmy <= rect.bottom();
                        if (over) {
                            hovered = j;
                        }
                        if (interactive) {
                            drawnRowIndex = j;
                        }
                        hoveredSwatch = -1;
                        swatchRowIndex = -1;
                        float rowAlpha = rowHover.getOrDefault(j, 0.0F);
                        if (rowAlpha > 0.01F) {
                            SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(),
                                    rect.w(), rect.h(), 0.0F, UiTokens.cardHover(rowAlpha));
                        }
                        if (j > start) {
                            // Hairline divider, 1px at the card-edge alpha,
                            // indented to the row pad on both sides — it owns
                            // the seam the inter-row gap gave up.
                            SkiaDraw.drawRoundedRect(canvas, card.x() + pad, rect.y(),
                                    card.w() - pad * 2.0F, UiTokens.s(1.0F), 0.0F,
                                    UiTokens.hairline());
                        }
                        drawRowContent(canvas, row, rect, rowAlpha, buttonFont, accent, dt);
                    }
                } finally {
                    canvas.restore();
                }
            } finally {
                if (press != null) {
                    canvas.restore();
                }
            }
        } finally {
            if (foldFade != null) {
                foldFade.close();
                canvas.restore();
            }
            canvas.restore();
        }
        return hovered;
    }

    /** Map key for a per-swatch bounce spring: row index << 32 | swatch. */
    private static long swatchKey(int rowIndex, int swatchIndex) {
        return (long) rowIndex << 32 | swatchIndex;
    }

    /** Arms the row press bounce (mouse-down) or clears it (-1 on release). */
    public void setPressedRow(int index) {
        pressedRow = index;
    }

    /** Arms the chip press bounce (mouse-down) or clears it (-1 on release). */
    public void setPressedChip(int index) {
        pressedChip = index;
    }

    /** Arms the colour-swatch press bounce or clears it (-1 on release). */
    public void setPressedSwatch(int swatchIndex) {
        pressedSwatch = swatchIndex;
    }

    /**
     * Row-local swatch index under the pointer, or -1. Geometry mirrors
     * {@link #colorHit}: same strip centre, same r+s(4) slop.
     */
    private int swatchAt(SettingsColor color, UiLayout.Rect rect, float vmx, float vmy) {
        float r = UiTokens.s(9);
        float cy = swatchCy(rect);
        if (Math.abs(vmy - cy) > r + UiTokens.s(4) || vmy < rect.y() || vmy > rect.bottom()) {
            return -1;
        }
        for (int i = 0; i < color.swatchCount(); i++) {
            if (Math.abs(vmx - swatchX(rect, i)) <= r + UiTokens.s(4)) {
                return i;
            }
        }
        // The "+" cell right after the last swatch, mirroring colorHit's
        // geometry so the hover bounce and the hit-test cannot drift apart.
        if (Math.abs(vmx - swatchX(rect, color.swatchCount())) <= r + UiTokens.s(4)) {
            return color.swatchCount();
        }
        return -1;
    }

    /** Swatch strip geometry: centre Y within the row and the X step. */
    private static float swatchCy(UiLayout.Rect rect) {
        return rect.y() + s(42);
    }

    private static float swatchX(UiLayout.Rect rect, int index) {
        return rect.x() + UiTokens.SETTINGS_ROW_PAD + UiTokens.s(9) + index * UiTokens.s(26);
    }

    // ---------------------------------------------------------------- themes

    /** Content width of the card strip (both edge pads included). */
    private float themeStripContentWidth() {
        return UiTokens.SETTINGS_ROW_PAD + THEME_CARD_COUNT * UiTokens.THEME_CARD_W
                + (THEME_CARD_COUNT - 1) * UiTokens.THEME_CARD_GAP + UiTokens.SETTINGS_ROW_PAD;
    }

    private float themeStripMaxScroll(UiLayout.Rect rect) {
        return Math.max(0.0F, themeStripContentWidth() - rect.w());
    }

    private static float clampScroll(float value, float max) {
        return Math.max(0.0F, Math.min(value, max));
    }

    /** X of card {@code index} inside the row rect, scroll offset applied. */
    private static float themeCardX(UiLayout.Rect rect, int index, float scroll) {
        return rect.x() + UiTokens.SETTINGS_ROW_PAD
                + index * (UiTokens.THEME_CARD_W + UiTokens.THEME_CARD_GAP) - scroll;
    }

    /** Top edge of the preview cards inside the row rect. */
    private static float themeCardY(UiLayout.Rect rect) {
        return rect.y() + s(28);
    }

    /** Card index under the pointer, or -1. Mirrors the renderer geometry. */
    private int themeCardAt(UiLayout.Rect rect, float vmx, float vmy, float scroll) {
        float top = themeCardY(rect);
        if (vmy < top || vmy > top + UiTokens.THEME_CARD_H + s(18)) {
            return -1;
        }
        for (int i = 0; i < THEME_CARD_COUNT; i++) {
            float x = themeCardX(rect, i, scroll);
            if (vmx >= x && vmx <= x + UiTokens.THEME_CARD_W) {
                return i;
            }
        }
        return -1;
    }

    /** The themes row rect, for the screen's pointer routing (wheel/drag/click). */
    public UiLayout.Rect themesRowRect(UiLayout layout, SettingsSection section, float scrollY) {
        List<Row> rows = rows(section);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind() == RowKind.THEMES) {
                return rowRect(section, rows, i, scrollY, layout);
            }
        }
        return null;
    }

    /** Whether the pointer is over the theme strip - the wheel's owner test. */
    public boolean themesUnderPointer(float vmx, float vmy, UiLayout layout,
                                      SettingsSection section, float scrollY) {
        UiLayout.Rect rect = themesRowRect(layout, section, scrollY);
        return rect != null && rect.contains(vmx, vmy);
    }

    /** Press routing for the strip: arms a possible card select or drag. */
    public boolean themesPress(float vmx, float vmy, UiLayout layout,
                               SettingsSection section, float scrollY) {
        UiLayout.Rect rect = themesRowRect(layout, section, scrollY);
        if (rect == null || !rect.contains(vmx, vmy)) {
            return false;
        }
        themeStripPressed = true;
        themeStripDragged = false;
        themeStripDragStartX = vmx;
        themeStripDragStartY = vmy;
        themeStripDragStartScroll = themeStripScroll;
        pressedThemeCard = themeCardAt(rect, vmx, vmy, themeStripScroll);
        return true;
    }

    /**
     * Drag routing: the strip follows the pointer 1:1 until release. The slop
     * is two-axis — a mostly vertical flick past the threshold means the user
     * is scrolling the page list, not the strip: the gesture is released (the
     * screen re-arms the list scroll) and the armed card dies, so the release
     * can never select a theme.
     */
    public void dragThemeStrip(float vmx, float vmy, UiLayout.Rect rect) {
        if (!themeStripPressed || rect == null) {
            return;
        }
        float dx = vmx - themeStripDragStartX;
        float dy = vmy - themeStripDragStartY;
        if (!themeStripDragged) {
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > UiTokens.s(8)) {
                if (Math.abs(dy) > Math.abs(dx)) {
                    // Vertical intent: hand the gesture back to the list.
                    themeStripPressed = false;
                    pressedThemeCard = -1;
                    return;
                }
                // Past the slop this is a horizontal scroll, not a tap:
                // the armed card dies.
                themeStripDragged = true;
                pressedThemeCard = -1;
            }
        }
        if (themeStripDragged) {
            themeStripScroll = themeStripTarget = clampScroll(
                    themeStripDragStartScroll - dx, themeStripMaxScroll(rect));
            themeStripAnimActive = false;
        }
    }

    public boolean isDraggingThemeStrip() {
        return themeStripPressed;
    }

    /** Release: a press that never turned into a drag selects the card. */
    public void endThemeStrip() {
        int card = pressedThemeCard;
        boolean select = themeStripPressed && !themeStripDragged && card >= 0;
        themeStripPressed = false;
        pressedThemeCard = -1;
        if (select) {
            AtomChatConfig config = AtomChatConfig.get();
            ThemeService.apply(config, card == 0
                    ? ThemeService.FROSTED : ThemeService.presets()[card - 1].id());
            AtomChatConfig.save(config);
        }
    }

    /** Wheel over the strip: glide horizontally, one step per notch. */
    public void wheelThemeStrip(float amount, UiLayout.Rect rect) {
        if (rect == null) {
            return;
        }
        themeStripTarget = clampScroll(themeStripTarget - amount * UiTokens.s(60),
                themeStripMaxScroll(rect));
        themeStripAnimFrom = themeStripScroll;
        themeStripAnimStart = System.currentTimeMillis();
        themeStripAnimActive = Math.abs(themeStripTarget - themeStripScroll) > 0.5F;
    }

    /** Glides the offset toward its target; a drag owns the offset directly. */
    private void advanceThemeStrip(UiLayout.Rect rect) {
        float max = themeStripMaxScroll(rect);
        themeStripTarget = clampScroll(themeStripTarget, max);
        if (themeStripPressed) {
            return;
        }
        if (themeStripAnimActive) {
            float t = Math.min(1.0F, (System.currentTimeMillis() - themeStripAnimStart) / 180.0F);
            themeStripScroll = clampScroll(themeStripAnimFrom
                    + (themeStripTarget - themeStripAnimFrom) * Easing.easeOutCubic(t), max);
            if (t >= 1.0F) {
                themeStripScroll = themeStripTarget;
                themeStripAnimActive = false;
            }
        } else {
            themeStripScroll = themeStripTarget;
        }
    }

    /**
     * Theme strip: title + current-preset name on the caption line, then the
     * horizontally scrollable preview cards - Default (drawn with the factory
     * palette), then the eight colour presets. Every card is a pure-code mini
     * panel in its theme's own colours; the selected card wears an accent
     * hairline and the whole strip scrolls by wheel or drag.
     */
    private void drawThemes(Canvas canvas, UiLayout.Rect rect, float dtMs) {
        advanceThemeStrip(rect);
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

        canvas.save();
        try {
            SkiaDraw.clip(canvas, rect.x(), themeCardY(rect) - s(6), rect.w(),
                    UiTokens.THEME_CARD_H + s(30), s(6));
            boolean frostedActive = currentId.isEmpty() || ThemeService.FROSTED.equals(currentId);
            drawThemeCard(canvas, rect, 0, ThemeService.previewOf(ThemeService.FROSTED),
                    "atomchat.settings.theme.frosted", frostedActive, dtMs);
            ThemeService.Preset[] presets = ThemeService.presets();
            for (int i = 0; i < presets.length; i++) {
                drawThemeCard(canvas, rect, i + 1, ThemeService.previewOf(presets[i].id()),
                        "atomchat.settings.theme." + presets[i].id(),
                        presets[i].id().equals(currentId), dtMs);
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * One mini-panel preview card: the theme's panel ground, a title-bar strip
     * carrying the accent dot, the other player's bubble on the left, the
     * player's own bubble on the right and a faint composer strip below - all
     * pure Skija primitives, no assets, at the theme's native corner scale.
     */
    private void drawThemeCard(Canvas canvas, UiLayout.Rect rect, int index,
                               ThemeService.Preview preview, String nameKey,
                               boolean selected, float dtMs) {
        float x = themeCardX(rect, index, themeStripScroll);
        float y = themeCardY(rect);
        float w = UiTokens.THEME_CARD_W;
        float h = UiTokens.THEME_CARD_H;
        float radius = UiTokens.s(10) * preview.cornerFactor();

        // The cards are separated by THEME_CARD_GAP and packed against the
        // strip's own clip; the width budget keeps the gap and the rounded
        // edges visible. The scaled shape is the card itself (w).
        PressScale press = themeCardScale.computeIfAbsent(index, k -> PressScale.bounce());
        press.update(themeCardAt(rect, pointerX, pointerY, themeStripScroll) == index,
                themeStripPressed && pressedThemeCard == index, dtMs, Animations.enabled(), w);
        press.begin(canvas, x + w / 2.0F, y + h / 2.0F);
        try {
            SkiaDraw.drawRoundedShadow(canvas, x, y, w, h, radius, s(6), UiTokens.CARD_SHADOW);
            SkiaDraw.drawRoundedRect(canvas, x, y, w, h, radius, preview.panelBg());
            // Title bar strip + its accent dot.
            float barH = s(12);
            float barY = y + s(7);
            SkiaDraw.drawRoundedRect(canvas, x + s(7), barY, w - s(14), barH,
                    s(4) * preview.cornerFactor(), preview.card());
            float dotR = s(2.5F);
            float dotY = barY + barH / 2.0F;
            SkiaDraw.drawRoundedRect(canvas, x + w - s(12) - dotR, dotY - dotR,
                    dotR * 2.0F, dotR * 2.0F, dotR, preview.accent());
            // The other player's bubble on the left, the player's own on the right.
            float bubbleH = s(16);
            SkiaDraw.drawRoundedRect(canvas, x + s(7), y + s(27), w - s(30), bubbleH,
                    bubbleH / 2.0F, preview.otherBubble());
            SkiaDraw.drawRoundedRect(canvas, x + s(23), y + s(50), w - s(30), bubbleH,
                    bubbleH / 2.0F, preview.ownBubble());
            // A faint composer strip balances the lower half of the card.
            // Mirrors the real composer's surface construction (cardFill():
            // card colour at the card-tint alpha over the panel) but derives
            // from THIS preview's own card colour — the global cardCutout()
            // reads the live theme's config and recoloured every tile's strip
            // whenever a dark theme was active. Over the card's own panelBg
            // ground the composite equals the cardCutout weight of this card.
            float composerTint = Math.max(0.0F, Math.min(1.0F,
                    AtomChatConfig.get().cardTint));
            SkiaDraw.drawRoundedRect(canvas, x + s(7), y + h - s(19), w - s(14), s(12),
                    s(4) * preview.cornerFactor(),
                    UiTokens.withAlpha(preview.card(), 255.0F * composerTint));
            if (selected) {
                SkiaDraw.drawEdgeHighlight(canvas, x, y, w, h, radius, s(1.5F), preview.accent());
            }
        } finally {
            canvas.restore();
        }
        Font nameFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        SkiaFontRenderer.drawTextCentered(canvas, nameFont,
                SkiaFontRenderer.truncate(nameFont, tr(nameKey), w + s(8)),
                x + w / 2.0F, y + h + s(11),
                selected ? textPrimary() : sec(220));
    }

    private void drawColor(Canvas canvas, Row row, UiLayout.Rect rect, float dtMs) {
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
            // One bounce spring per swatch (keyed row+index): the swatches sit
            // one step apart and this scale runs inside the row's own bounce,
            // so the two compound. The scaled shape is the 2r swatch square.
            // Hit-tests stay unscaled.
            PressScale ps = swatchScale.computeIfAbsent(swatchKey(swatchRowIndex, i),
                    k -> PressScale.bounce());
            ps.update(i == hoveredSwatch, i == pressedSwatch, dtMs, Animations.enabled(), 2.0F * r);
            ps.begin(canvas, scx, cy);
            try {
                SkiaDraw.drawRoundedRect(canvas, scx - r, cy - r, 2.0F * r, 2.0F * r, r, swatch);
                // Theme-adaptive hairline: pale swatches stay visible on light
                // panels, dark ones on dark panels.
                SkiaDraw.drawRing(canvas, scx, cy, r + s(0.75F), s(1.0F), UiTokens.hairline());
                if (swatch == color.value()) {
                    // Selection ring: the functional rim stroke with a breathing
                    // gap — alpha 110, polarity-picked, so the ring survives pale
                    // swatches on light panels and dark ones on dark panels.
                    try (Paint ring = new Paint().setColor(UiTokens.rim())
                            .setMode(PaintMode.STROKE).setStrokeWidth(s(2)).setAntiAlias(true)) {
                        canvas.drawOval(io.github.humbleui.types.Rect.makeXYWH(
                                scx - r - s(3), cy - r - s(3), 2.0F * (r + s(3)), 2.0F * (r + s(3))), ring);
                    }
                }
            } finally {
                canvas.restore();
            }
        }
        // "+" cell: opens the custom colour picker (emote-grid plus language).
        // Same per-cell bounce as the swatches: it is the strip's last hit
        // target and used to sit dead under the pointer. The press phase keys
        // off pressedSwatch as well, though the screen opens the picker on
        // click, so hover is the visible half of the gesture in practice.
        int plusIndex = color.swatchCount();
        float px = swatchX(rect, plusIndex);
        PressScale plus = swatchScale.computeIfAbsent(swatchKey(swatchRowIndex, plusIndex),
                k -> PressScale.bounce());
        plus.update(plusIndex == hoveredSwatch, plusIndex == pressedSwatch, dtMs,
                Animations.enabled(), 2.0F * r);
        plus.begin(canvas, px, cy);
        try {
            SkiaDraw.drawRoundedRect(canvas, px - r, cy - r, 2.0F * r, 2.0F * r, r,
                    UiTokens.trackRest());
            SkiaDraw.drawRing(canvas, px, cy, r + s(0.75F), s(1.0F), UiTokens.hairline());
            drawIconCentered(canvas, AppIcons.ICON_PLUS_PATH, px, cy, s(12),
                    textPrimary());
        } finally {
            canvas.restore();
        }
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
        UiCards.drawCard(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), 0.0F);
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

    /** Plain action card: title + description + a right-aligned verb. */
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
                        ? DANGER_RED
                        : sec(copy.available() ? 200 : 130));
        // Same treatment as the link cards' "Open": full-weight, centred —
        // the card's call to action, not a footnote.
        if (copy.available()) {
            Font verbFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            SkiaFontRenderer.drawTextRight(canvas, verbFont, copy.verb(),
                    rect.right() - UiTokens.SETTINGS_ROW_PAD,
                    rect.y() + rect.h() / 2.0F,
                    copy.redConfirm() ? DANGER_RED : textPrimary());
        }
        // Disabled rows dim their text/controls only (the sec(130) copy
        // above); the card base stays put so the list never turns patchy.
    }

    /**
     * Group heading: bold title aligned to the card's text column with a faint
     * rule underneath, so it reads as a section divider rather than a stray
     * caption. A foldable group's chevron sits at the right edge.
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
            float chevron = s(7);
            drawChevron(canvas, rightX - chevron / 2.0F, cy, chevron,
                    expansionOf(group), textColor);
        }

        float lineY = rect.bottom() - s(5);
        SkiaDraw.drawRoundedRect(canvas, textX, lineY,
                Math.max(0.0F, rightX - textX), s(1), s(0.5F), lineColor);
    }

    /** Small fold indicator: one right-pointing triangle — the collapsed
     *  glyph — rotating 90° clockwise around its centre with the group's
     *  expansion, landing exactly on the old down-pointing glyph at 1 (the
     *  two static triangles this replaces were those two endpoints; a 90°
     *  turn maps the first onto the second vertex-for-vertex, so the fold
     *  reads as one motion with the rows growing beneath). Skija's Canvas
     *  has no rotate-about-point overload (0.116.8: rotate(float) only), so
     *  the pivot is a translate-rotate-translate-back. No explicit contour
     *  close: fill mode auto-closes, and Path.close() is the resource-release
     *  method (contour close is closePath()) — calling it inside the block
     *  frees the native path before drawPath runs (crashed). */
    private static void drawChevron(Canvas canvas, float cx, float cy, float size,
                                    float expansion, int color) {
        try (io.github.humbleui.skija.Path path = new io.github.humbleui.skija.Path();
             Paint paint = new Paint()) {
            path.moveTo(cx - size / 2.0F, cy - size);
            path.lineTo(cx - size / 2.0F, cy + size);
            path.lineTo(cx + size / 2.0F, cy);
            paint.setColor(color);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(90.0F * expansion);
            canvas.translate(-cx, -cy);
            canvas.drawPath(path, paint);
            canvas.restore();
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
        SkiaFontRenderer.drawTextCentered(canvas, font, Component.translatable(textKey).getString(),
                cx, top + icon + gap + textH / 2.0F, muted);
    }

    private void drawSwitch(Canvas canvas, Row row, UiLayout.Rect rect, int accent, float dtMs) {
        SettingsItem item = row.item();
        ToggleSwitch control = switches.computeIfAbsent(item.id(), k -> new ToggleSwitch());

        float switchX = rect.right() - UiTokens.SETTINGS_ROW_PAD - UiTokens.SWITCH_W;
        // Pointer-over-switch drives the control's own hover bounce; a row
        // press only counts when it landed on the switch. Unscaled coordinates.
        boolean overSwitch = pointerX >= switchX && pointerX <= switchX + UiTokens.SWITCH_W
                && pointerY >= rect.y() && pointerY <= rect.bottom();
        control.setInteraction(overSwitch, overSwitch && pressedRow == drawnRowIndex);
        control.update(dtMs, item.available() && item.value());

        float switchY = rect.y() + (rect.h() - UiTokens.SWITCH_H) / 2.0F;
        // The four-arg overload wires the disabled state: an unavailable
        // switch renders its whole capsule at the control's disabled opacity.
        control.render(canvas, switchX, switchY, accent, item.available());

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
        // Disabled switches dim their copy and their capsule; the card base stays put.
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
                UiTokens.trackRest());
        float fillW = Math.max(track.h(), track.w() * t);
        SkiaDraw.drawRoundedRect(canvas, track.x(), track.y(), fillW, track.h(), radius, accent);

        float knobX = track.x() + track.w() * t - UiTokens.SLIDER_KNOB / 2.0F;
        float knobY = track.y() + track.h() / 2.0F - UiTokens.SLIDER_KNOB / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, knobX, knobY, UiTokens.SLIDER_KNOB, UiTokens.SLIDER_KNOB,
                UiTokens.SLIDER_KNOB / 2.0F, Color.makeARGB(255, 255, 255, 255)); // knob: mechanical white
        // The white head sits directly on the track; the only ring kept is an
        // accent trace at the rim, outer edge essentially flush with the head
        // radius. The stroke is deliberately thicker than a hairline: on the
        // captured frame the old s(1.0) stroke worked out to only ~1.5 device
        // pixels, so the same accent that reads as a solid band in the track
        // fill degenerated into a jagged line around a circle. The thick
        // card-coloured cut-out ring that used to separate head from track
        // read as a dark halo on light panels and is gone.
        float knobCx = knobX + UiTokens.SLIDER_KNOB / 2.0F;
        float knobCy = knobY + UiTokens.SLIDER_KNOB / 2.0F;
        float knobR = UiTokens.SLIDER_KNOB / 2.0F;
        SkiaDraw.drawRing(canvas, knobCx, knobCy, knobR - s(0.5F), s(1.8F), accent);
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
                UiTokens.trackRest());
        try (Paint border = new Paint().setMode(PaintMode.STROKE)
                .setAntiAlias(true).setStrokeWidth(s(1.5F))
                .setColor(editing ? accent : UiTokens.rim())) {
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
        // Blinking caret while the inline editor owns the keyboard. Same accent
        // as the text it blinks inside: a fixed white caret would vanish on a
        // light theme, and the caret is part of the editing text, not chrome.
        if (editing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = fieldX + s(10) + SkiaFontRenderer.getStringWidth(inputFont, shown) + s(2);
            try (Paint caret = new Paint().setColor(accent)
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
            // Avatar stand-in: an opaque neutral, fixed on purpose — it is a
            // placeholder face, not a themed surface, and reads on any panel.
            SkiaDraw.drawRoundedRect(canvas, rect.x() + UiTokens.SETTINGS_ROW_PAD, avatarY,
                    avatar, avatar, avatar / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }
        // Hairline rim hugging the avatar's outer edge (face or placeholder):
        // polarity-adaptive UiTokens.rim separates the circle from the row
        // card behind it. Same ring language as the swatches.
        SkiaDraw.drawRing(canvas, rect.x() + UiTokens.SETTINGS_ROW_PAD + avatar / 2.0F, avatarY + avatar / 2.0F,
                avatar / 2.0F + s(0.75F), s(1.0F), UiTokens.rim());

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
                UiTokens.cardHover(hover));
        SkiaFontRenderer.drawTextCentered(canvas, buttonFont, label,
                buttonX + buttonW / 2.0F, buttonY + buttonH / 2.0F,
                textPrimary());
    }

    // ------------------------------------------------------------------ input

    public RowHit hit(float vmx, float vmy, UiLayout layout, SettingsSection section, float scrollY) {
        List<Row> rows = rows(section);
        Font buttonFont = FontManager.font(UiTokens.FONT_QUOTE);
        for (int i = 0; i < rows.size(); i++) {
            // Folded-away children are not clickable, same floor as drawing.
            if (rowExpansion(rows, i) < GROUP_FOLD_MIN_DRAWN) {
                continue;
            }
            UiLayout.Rect rect = rowRect(section, rows, i, scrollY, layout);
            RowKind kind = rows.get(i).kind();
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
            // Folded-away children are not draggable, same floor as drawing.
            if (rowExpansion(rows, i) < GROUP_FOLD_MIN_DRAWN) {
                continue;
            }
            UiLayout.Rect rect = rowRect(section, rows, i, scrollY, layout);
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
        UiLayout.Rect track = sliderTrackRect(rowRect(section, rows, activeSliderIndex, scrollY, layout));
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
            UiLayout.Rect rect = rowRect(section, rows, rowIdx, scrollY, layout);
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
                // One-shot pulse on the control the click flipped: the bounce
                // follows the state change, not the physical press window (the
                // same frame writes the config, so a press-bound bounce can be
                // swallowed entirely).
                ToggleSwitch control = switches.get(item.id());
                if (control != null) {
                    control.pulse();
                }
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
            net.minecraft.Util.getPlatform().openUri(java.net.URI.create(uri));
        } catch (Exception e) {
            AtomChat.LOGGER.warn("Failed to open link {}", uri, e);
        }
    }

    /** Drops per-page transient state so reopening a section starts clean. */
    public void reset() {
        switches.clear();
        activeChip.clear();
        chipSwitchFromId = null;
        lastInteractiveHover = -1;
        rowHover.clear();
        rowPress.clear();
        aboutCardPress.clear();
        swatchScale.clear();
        pressedRow = -1;
        pressedSwatch = -1;
        pressedThemeCard = -1;
        themeStripPressed = false;
        themeStripDragged = false;
        themeStripScroll = 0.0F;
        themeStripTarget = 0.0F;
        themeStripAnimActive = false;
        draggingSliderId = null;
        hoveredIndex = -1;
        cancelNumberEdit();
    }
}
