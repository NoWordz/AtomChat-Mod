package com.atom.chat.settings;

import com.atom.chat.api.ui.SettingsUi;
import com.atom.chat.api.ui.UiContributions;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.settings.contract.HistoryFieldsPage;
import com.atom.chat.settings.contract.SettingsContractMount;
import com.atom.chat.settings.contract.SettingsContractResources;
import com.atom.lib.client.input.ContractSoundOutput;
import com.atom.lib.input.KeyEvent;
import com.atom.lib.input.PointerEvent;
import com.atom.lib.frontend.StrictPage;
import com.atom.lib.frontend.contract.ContractRegistry;
import com.atom.lib.frontend.contract.UiSoundSink;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.nav.PageNavAxis;
import com.atom.chat.ui.UiStagger;
import com.atom.chat.ui.WallpaperTokens;
import com.atom.chat.AtomChat;
import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.Animator;
import com.atom.chat.render.CardShadowReach;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaImages;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.api.ui.AppearanceUi.Part;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.PanelOpenMotion;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.TablerIcons;
import com.atom.chat.ui.ToggleSwitch;
import com.atom.chat.wallpaper.WallpaperStore;

import java.nio.file.Path;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.lib.design.atomchat.UiTokens;
import static com.atom.lib.design.atomchat.UiTokens.s;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.ColorFilter;
import io.github.humbleui.types.Rect;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.PictureRecorder;
import io.github.humbleui.skija.SamplingMode;
import net.minecraft.util.Language;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Body of one settings section: a vertical list of rows in the same visual
 * language as the conversation list — switch rows, slider rows, read-only info
 * rows (optionally linking out), the standalone About brand row and the
 * blocked-player list.
 *
 * <p>Row geometry comes from {@link #rows(SettingsSection)} for order and
 * {@link #rowRect(SettingsSection, List, int, float, UiLayout)} for position,
 * so rendering, hit-testing and measurement can never disagree.</p>
 */
public final class SettingsSectionPage {
    private final com.atom.chat.settings.contract.SettingsYogaLayout contractLayout=new com.atom.chat.settings.contract.SettingsYogaLayout();

    /**
     * Page-entrance (unit V): the section's rows rise into place over the
     * 380ms item clock. The chip crossfade keeps its own alpha/dy channels —
     * the entrance adds its rise to whatever dy the crossfade hands in.
     * Painted row transforms are recorded for navigation pointer inversion.
     */
    private final UiStagger.Entrance entrance = new UiStagger.Entrance();

    /** Arms the same item clock for navigation arrivals and local chip changes. */
    public void markEntered() {
        markEntered(UiStagger.frameTimeMillis());
    }

    public void markEntered(long nowMs) {
        entrance.markEntered(nowMs);
        staggerHits.clear();
    }
    private final com.atom.chat.ui.StaggerHitMap staggerHits = new com.atom.chat.ui.StaggerHitMap();
    public com.atom.chat.ui.StaggerHitMap staggerHits() { return staggerHits; }


    /**
     * Keeps the rows settled: used when the page envelope already owns the
     * arrival. Two motions over the same content (page drift + row rise) read as
     * a jump, so the navigation arms exactly one of them.
     */
    public void markSettled() {
        entrance.markSettled();
    }
    public enum RowKind { HERO, AUTHORS, SWITCH, SLIDER, COLOR, INFO, BLOCKED, BLOCKED_EMPTY, LABEL, ACTION, THEMES, BUBBLES, WALLPAPERS, CREDITS, QUALITY }

    public record Row(RowKind kind, SettingsItem item, SettingsSlider slider,
                      SettingsColor color, SettingsCatalog.InfoRow info, PlayerRef player,
                      String labelKey, String actionId, String titleText, String subtitleText,
                      String presentationId) {
        public Row(RowKind kind, SettingsItem item, SettingsSlider slider, SettingsColor color,
                   SettingsCatalog.InfoRow info, PlayerRef player, String labelKey, String actionId,
                   String titleText, String subtitleText) {
            this(kind, item, slider, color, info, player, labelKey, actionId, titleText, subtitleText, null);
        }

        static Row ofGroup(String key, String id) {
            return new Row(RowKind.LABEL, null, null, null, null, null, key, null, null, null, id);
        }

        public String switchAnimationKey() {
            return presentationId != null ? presentationId : item.id();
        }

        Row withPresentationId(String id) {
            return new Row(kind, item, slider, color, info, player, labelKey, actionId, titleText, subtitleText, id);
        }

        static Row ofSwitch(SettingsItem item) {
            return new Row(RowKind.SWITCH, item, null, null, null, null, null, null, null, null);
        }

        static Row ofSlider(SettingsSlider slider) {
            return new Row(RowKind.SLIDER, null, slider, null, null, null, null, null, null, null);
        }

        static Row ofColor(SettingsColor color) {
            return new Row(RowKind.COLOR, null, null, color, null, null, null, null, null, null);
        }

        static Row ofInfo(SettingsCatalog.InfoRow info) {
            return new Row(RowKind.INFO, null, null, null, info, null, null, null, null, null);
        }

        static Row ofBlocked(PlayerRef player) {
            return new Row(RowKind.BLOCKED, null, null, null, null, player, null, null, null, null);
        }

        static Row ofLabel(String key) {
            return new Row(RowKind.LABEL, null, null, null, null, null, key, null, null, null);
        }

        static Row ofAuthors() {
            return new Row(RowKind.AUTHORS, null, null, null, null, null, null, null, null, null);
        }

        static Row ofHero() {
            return new Row(RowKind.HERO, null, null, null, null, null, null, null, null, null);
        }

        static Row ofAction(String actionId, SettingsItem item) {
            return new Row(RowKind.ACTION, item, null, null, null, null, null, actionId, null, null);
        }

        static Row ofBubbles() {
            return new Row(RowKind.BUBBLES, null, null, null, null, null, null, null, null, null);
        }

        static Row ofThemes() {
            return new Row(RowKind.THEMES, null, null, null, null, null, null, null, null, null);
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
    // hue that must not drift with the theme or the accent. One definition, in
    // UiTokens, shared with the action-feedback toast.
    private static final int DANGER_RED = UiTokens.dangerColor();
    private static final String LABEL_BLOCKED = "atomchat.settings.privacy.list";
    private static final String LABEL_THIRD_PARTY = "atomchat.settings.about.thirdparty.group";
    private static final String LABEL_MOD_INFO = "atomchat.settings.about.modinfo";
    private static final String LABEL_MAINTENANCE = "atomchat.settings.group.about.maintenance";
    private static final String LABEL_APPEARANCE_ADVANCED = "atomchat.settings.group.advancedcolors";
    private static final String ACTION_WALLPAPER_PICK = "wallpaper_pick";
    private static final String ACTION_WALLPAPER_CLEAR = "wallpaper_clear";
    private static final String ACTION_TELEPORT_MODE = "teleport_mode";
    private static final String ACTION_PAGE_NAV_STYLE = "page_nav_style";
    private static final String ACTION_HUD_EDIT = "hud_edit";
    private static final String ACTION_PANEL_EDIT = "panel_edit";
    private static final String ACTION_AVATAR_STYLE = "avatar_style";
    public static final String ACTION_BUBBLE_STYLE = "bubble_style:";
    private static final String ACTION_HISTORY_CLEAR = "history_clear";
    private static final String ACTION_CACHE_CLEAR = "cache_clear";
    private static final String ACTION_TEST_SOUND = "test_sound";
    public static final String ACTION_THEME_PRESET_SAVE = "theme_preset_save";
    public static final String ACTION_THEME_PRESET_APPLY = "theme_preset_apply:";
    public static final String ACTION_THEME_PRESET_DELETE = "theme_preset_delete:";

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

    private SettingsItem panelEditItem() {
        return new SettingsItem("panel_edit", "atomchat.panel_editor.title",
                "atomchat.panel_editor.desc", () -> true, v -> { });
    }

    private SettingsItem hudEditItem() {
        return new SettingsItem("hud_edit", "atomchat.settings.hud.editor",
                "atomchat.settings.hud.editor.desc", () -> true, v -> { });
    }

    /** "Test sound" card: fires the notification cue right away, no message needed. */
    private SettingsItem testSoundItem() {
        return new SettingsItem("test_sound",
                "atomchat.settings.chat.test_sound",
                "atomchat.settings.chat.test_sound.desc",
                () -> true, v -> {
        });
    }

    /** Teleport command selector: descriptive subtitle, current mode between arrows. */
    private SettingsItem teleportModeItem() {
        return new SettingsItem("teleport_mode",
                "atomchat.settings.chat.teleport",
                "atomchat.settings.chat.teleport.desc",
                () -> true, v -> {
        });
    }

    /**
     * Avatar style selector; arrow actions are dispatched by SettingsActions.
     */
    private SettingsItem avatarStyleItem() {
        return new SettingsItem("avatar_style",
                "atomchat.settings.chat.avatar_style",
                "atomchat.settings.chat.avatar_style.desc",
                () -> true, v -> {
        });
    }

    private SettingsItem bubbleStyleItem() {
        return new SettingsItem("bubble_style",
                "atomchat.settings.appearance.bubble_style",
                "atomchat.settings.appearance.bubble_style.desc",
                () -> true, v -> { });
    }

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
     * shows one natural group; switching swaps the row set in one frame and
     * replays the page entrance (the same per-row stagger a navigation arrival
     * plays). Privacy and about stay single-page (no chips) - their lists fit
     * one screen.
     */
    public record SectionChip(String id, String labelKey) {
    }

    private static final List<SectionChip> APPEARANCE_CHIPS = List.of(
            new SectionChip("theme", "atomchat.settings.tab.presets"),
            new SectionChip("material", "atomchat.settings.tab.material"),
            new SectionChip("adjust", "atomchat.settings.tab.layout"),
            new SectionChip("interaction", "atomchat.settings.tab.interaction"),
            new SectionChip("colors", "atomchat.settings.tab.colors"));
    private static final List<SectionChip> CHAT_CHIPS = List.of(
            new SectionChip("messages", "atomchat.settings.group.chat.messages"),
            new SectionChip("history", "atomchat.settings.group.chat.history"),
            new SectionChip("notify", "atomchat.settings.group.chat.notify"),
            new SectionChip("effects", "atomchat.settings.tab.effects"));
    /** Active chip index per section id (chip-less sections never read it). */
    private final Map<String, String> activeChip = new HashMap<>();

    // The chip row reuses BottomTabBar's cell geometry and clock: equal cells
    // from the layout rect, a sliding selection capsule, a pure-colour hover
    // wash. The bounce is compact(), like the tab capsules; unlike them it
    // wraps the label only, because the capsule is one shared shape that
    // slides between cells and a per-cell hover spring on it would fight the
    // slide.
    private final SettingsChipBar sharedChipBar = new SettingsChipBar();
    private int pressedChip = -1;
    /** Hover row of the interactive (incoming) layer, fed to rowHover decay. */
    private int lastInteractiveHover = -1;

    /** The chip row of a section, or an empty list when the section has none. */
    public List<SectionChip> chips(SettingsSection section) {
        List<SectionChip> builtIn = switch (section) {
            case APPEARANCE -> APPEARANCE_CHIPS;
            case CHAT -> CHAT_CHIPS;
            default -> List.of();
        };
        var edits = SettingsUi.tabs(section);
        if (!edits.hasEdits() || com.atom.chat.onboarding.TutorialSandbox.active()) return builtIn;
        if (builtIn.isEmpty()) {
            builtIn = List.of(new SectionChip(section.id(), "atomchat.settings." + section.id()));
        }
        var defaults = builtIn.stream().map(chip -> new UiContributions.Entry<>(
                SettingsUi.builtInTabId(chip.id()), new SettingsUi.Chip(chip.labelKey(), true))).toList();
        return edits.resolve(defaults).stream().map(entry -> {
            String id = entry.id();
            if (entry.value().builtInContent() && defaults.stream().anyMatch(d -> d.id().equals(id))) {
                return new SectionChip(id.substring("atomchat:".length()), entry.value().titleKey());
            }
            return new SectionChip(id, entry.value().titleKey());
        }).toList();
    }

    /** Index of the chip whose rows are showing. */
    public int activeChipIndex(SettingsSection section) {
        List<SectionChip> sectionChips = chips(section);
        if (sectionChips.isEmpty()) {
            return -1;
        }
        String selected = activeChip.get(section.id());
        for (int i = 0; i < sectionChips.size(); i++) {
            if (sectionChips.get(i).id().equals(selected)) return i;
        }
        return 0;
    }

    public void requestTabReset(SettingsSection section, java.util.function.BiConsumer<String, Boolean> feedback) {
        requestTabReset(section, feedback, () -> {});
    }

    public void requestTabReset(SettingsSection section, java.util.function.BiConsumer<String, Boolean> feedback,
                               Runnable afterGeometryReset) {
        String chip = activeChipId(section);
        if (!canResetActiveTab(section)) return;
        String label = tr("atomchat.settings." + section.id());
        if (!chips(section).isEmpty()) label += " · " + tr(chips(section).get(activeChipIndex(section)).labelKey());
        new SettingsTabResetPrompt(confirmation, AtomChatConfig::get, AtomChatConfig::saveChecked,
                key -> tr(key), feedback, () -> {
                    rowGeometry = null;
                    com.atom.chat.chat.TeleportCommands.reset();
                    if (section == SettingsSection.APPEARANCE && "adjust".equals(chip)) afterGeometryReset.run();
                }).request(section, chip, label);
    }

    public boolean canResetActiveTab(SettingsSection section) {
        return !activeChipId(section).contains(":");
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
        return SettingsGroupFrontend.chipBand();
    }


    /** Chip index under the pointer, or -1 (equal cells, like the tab bar). */
    public int chipAt(SettingsSection section,float vmx,float vmy,UiLayout layout){
        return SettingsChipBar.hit(layout,chips(section).size(),vmx,vmy);
    }

    /**
     * Switches the visible chip and replays the page entrance. No-op on the
     * active chip; with decorative motion off the re-arm reads fully settled,
     * so the rows swap instantly.
     */
    public void selectChip(SettingsSection section, int index) {
        List<SectionChip> sectionChips = chips(section);
        if (index < 0 || index >= sectionChips.size()
                || index == activeChipIndex(section)) {
            return;
        }
        if (section == SettingsSection.CHAT && historyMount != null) historyMount.embedded().clear();
        cancelPreviewPress();
        activeChip.put(section.id(), sectionChips.get(index).id());
        rowGeometry = null;
        // The new row set draws in one frame, then rises in on the same
        // per-row stagger a navigation arrival uses (no crossfade, no second
        // clock — the entrance IS the transition).
        entrance.markEntered(System.currentTimeMillis());
    }

    /** Cancels a captured press without applying a theme, preset or setting. */
    public void cancelPointer() {
        cancelPreviewPress();
        endSliderDrag();
        clearRowPress();
    }

    private void cancelPreviewPress() {
        for (CardStrip strip : previewStrips) {
            strip.pressed = false;
            strip.dragged = false;
            strip.pressedCard = -1;
        }
    }

    private void clearRowPress() {
        if (historyMount != null) historyMount.embedded().clear();
        setPressedRow(-1);
        setPressedChip(-1);
    }

    /** Rows of one chip by id (the crossfade's outgoing layer). */
    private List<Row> rowsForChip(SettingsSection section, String chipId) {
        return switch (section) {
            case APPEARANCE -> appearanceRows(chipId);
            case CHAT -> chatRows(chipId);
            default -> rows(section);
        };
    }

    private final com.atom.chat.ui.ConfirmationDialog confirmation = new com.atom.chat.ui.ConfirmationDialog();

    public com.atom.chat.ui.ConfirmationDialog confirmation() { return confirmation; }

    /** Session-only user folds. Every group starts expanded; a click may
     *  collapse it until this settings page instance is discarded. */
    private final java.util.Set<String> collapsedColorGroups =
            new java.util.HashSet<>();
    /**
     * Time constant of the fold animation: a group's children neither pop in
     * nor pop out any more, they grow/shrink behind an exponential settle at
     * the same 50ms tau the ZOOM page-nav phases use (~170ms to visually
     * land), so folding reads as one continuous motion of the same family.
     */
    public static final float GROUP_FOLD_TAU_MS = SettingsGroupFrontend.FOLD_TAU_MS;
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
    private static String foldableGroup(Row row) {
        return row.presentationId() != null ? row.presentationId() : foldableGroup(row.labelKey());
    }

    private static String foldableGroup(String labelKey) {
        if (labelKey == null) {
            return null;
        }
        return "label:" + labelKey;
    }

    /**
     * Theme picker row: nine mini-panel preview cards (frosted + eight colour
     * presets) in a horizontal strip, each drawn in its own preset palette;
     * the active one wears an accent edge. Replaces the old parked theme_cycle
     * action card.
     */

    // Horizontal theme-card strip: the wheel glides toward a target, a press
    // drag follows the pointer 1:1, and a press that never travels selects.
    private static final class CardStrip {
        final RowKind kind;
        float scroll, target, animFrom, dragStartX, dragStartY, dragStartScroll;
        long animStart;
        boolean animActive, pressed, dragged;
        int pressedCard = -1;
        int pressedDeleteCard = -1;
        int hoveredCard = -1;
        UiLayout.Rect rect;
        final Map<Integer, PressScale> scales = new HashMap<>();
        CardStrip(RowKind kind) { this.kind = kind; }
        void reset() {
            scroll = target = 0F;
            animActive = pressed = dragged = false;
            pressedCard = hoveredCard = -1;
            rect = null;
            scales.clear();
        }
    }
    private long stripHitTests;
    private final CardStrip themeStrip = new CardStrip(RowKind.THEMES);
    private final CardStrip bubbleStrip = new CardStrip(RowKind.BUBBLES);
    private final CardStrip wallpaperStrip = new CardStrip(RowKind.WALLPAPERS);
    private static final String QUALITY_TITLE = "atomchat.settings.appearance.quality";
    private static final GraphicsQuality[] GRAPHICS_QUALITIES = GraphicsQuality.values();
    private final CardStrip qualityStrip = new CardStrip(RowKind.QUALITY);
    private final CardStrip creditsStrip = new CardStrip(RowKind.CREDITS);
    private final CardStrip[] previewStrips = {qualityStrip, themeStrip, bubbleStrip, wallpaperStrip, creditsStrip};
    private CardStrip pointerStrip = themeStrip;
    private final Map<Integer, Float> themeDeleteHover = new HashMap<>();
    private boolean revealLatestPresetPending;

    private final Map<String, ToggleSwitch> switches = new HashMap<>();
    private final Map<Integer, Float> rowHover = new HashMap<>();
    private float numberFieldHover;
    private long lastNumberFieldHover;
    private final SettingsStepper.HoverState stepperHover = new SettingsStepper.HoverState();
    /** Per-row press bounce: compact(), so a row lifts 1.04 on hover and
     *  dips 0.95 while pressed. */
    private final Map<Integer, PressScale> rowPress = new HashMap<>();
    /** Press springs of the merged about-page cards, keyed by each card's
     *  first row index. The map keeps the
     *  same reset() contract as the other per-row spring maps. Each card
     *  carries the one bounce: its child rows scale together, never each
     *  alone. */
    private final Map<Integer, PressScale> aboutCardPress = new HashMap<>();
    /** Row index under an active press for the bounce; -1 = none. */
    private int pressedRow = -1;
    private final Map<String, PressScale> colorPress = new HashMap<>();
    /** Row list from this frame's measure pass, reused by the immediately following draw. */
    private SettingsSection measuredRowsSection;
    private String measuredRowsChip;
    private List<Row> measuredRows;
    private long rowBuilds;
    private long compactTextBuilds;
    private List<Row> rowGroupRows;
    private String[] rowGroups;
    private RowGeometry rowGeometry;
    private boolean colorRowHovered;
    private int drawnRowIndex = -1;
    /** Render-time pointer for per-control hover. */
    private float pointerX;
    private float pointerY;
    private String draggingSliderId;
    private int activeSliderIndex = -1;
    private float dragValue;
    /** Inline numeric editor state. */
    private ContractRegistry settingsContract = SettingsContractResources.approved();
    private UiSoundSink settingsSound = new ContractSoundOutput();
    private SettingsContractMount historyMount;
    private final SettingsNumberEditor numberEditor = new SettingsNumberEditor();
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
    private long lastChipFrameMs = System.currentTimeMillis();
    private static final Map<String, String> NO_ARG_TEXT = new HashMap<>();
    private static Language translationLanguage;
    private static long translationRevision;

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
        currentTranslationRevision();
        String cached = NO_ARG_TEXT.get(key);
        if (cached != null) return cached;
        String value = Text.translatable(key).getString();
        NO_ARG_TEXT.put(key, value);
        return value;
    }

    private static String tr(String key, Object... args) {
        return args.length == 0 ? tr(key) : Text.translatable(key, args).getString();
    }

    /** Language-only reloads can replace the table without calling our resource reload listener. */
    private static long currentTranslationRevision() {
        if (translationLanguage != Language.getInstance()) clearTranslationCache();
        return translationRevision;
    }

    /** Also invalidate explicitly on a full resource reload. */
    public static void clearTranslationCache() {
        translationLanguage = Language.getInstance();
        NO_ARG_TEXT.clear();
        translationRevision++;
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
            case SLIDER -> UiTokens.SETTINGS_SLIDER_ROW_H;
            case BLOCKED_EMPTY -> s(160);
            case THEMES, WALLPAPERS -> UiTokens.SETTINGS_THEME_ROW_H;
            case BUBBLES -> UiTokens.SETTINGS_THEME_ROW_H;
            case CREDITS, QUALITY -> AboutCreditCards.ROW_HEIGHT;
            case HERO -> UiTokens.SETTINGS_HERO_H;
            case AUTHORS -> UiTokens.SETTINGS_AUTHOR_ROW_H;
            default -> UiTokens.SETTINGS_ROW_H;
        };
    }

    /**
     * Dynamic row height: switch/action/info descriptions may wrap to a second
     * line instead of being ellipsized. The row grows only when the text does
     * not fit the width the right-hand control/verb/hint leaves available.
     */
    /** One measured text block; the bottom control owns a separate band when crowded. */
    private record CompactText(List<String> title, List<String> description,
                               float titleH, float descriptionH, float controlH, float height) {}

    /** One frame's row heights, tops and text measurements. */
    private record RowGeometry(List<Row> rows, SettingsSection section,
                               float listX, float listY, float listW,
                               float cardWidth, float[] tops, float[] heights,
                               float contentHeight, long languageRevision,
                               IdentityHashMap<Row, CompactText> compactTexts) {}

    private CompactText compactText(Row row, float width) {
        if (rowGeometry != null && rowGeometry.languageRevision() == currentTranslationRevision()
                && Float.compare(rowGeometry.cardWidth(), width) == 0
                && rowGeometry.compactTexts().containsKey(row)) {
            return rowGeometry.compactTexts().get(row);
        }
        return measureCompactText(row, width);
    }

    private CompactText measureCompactText(Row row, float width) {
        compactTextBuilds++;
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float inner = Math.max(1.0F, width - UiTokens.SETTINGS_ROW_PAD * 2.0F);
        String title;
        String desc = "";
        float reserved = 0.0F;
        float controlH = 0.0F;
        switch (row.kind()) {
            case SWITCH -> {
                title = tr(row.item().titleKey());
                desc = descriptionText(row);
                reserved = inner - descriptionMaxWidth(row, width);
                controlH = UiTokens.SWITCH_H;
            }
            case ACTION -> {
                ActionCopy copy = actionCopy(row);
                title = copy.title(); desc = copy.subtitle();
                if (!copy.verb().isEmpty()) {
                    float valueWidth = SkiaFontRenderer.getStringWidth(titleFont, copy.verb());
                    boolean stepper = copy.available() && SettingsStepper.supports(row.actionId());
                    reserved = (stepper ? SettingsStepper.preferredWidth(valueWidth) : valueWidth)
                            + UiTokens.SETTINGS_COMPACT_GAP;
                    controlH = stepper ? UiTokens.SETTINGS_STEPPER_ARROW : SkiaFontRenderer.getHeight(titleFont);
                }
            }
            case INFO -> {
                title = tr(row.info().titleKey()); desc = row.info().value();
                if (row.info().isLink()) {
                    reserved = SkiaFontRenderer.getStringWidth(titleFont, tr("atomchat.settings.about.open")) + s(12);
                    controlH = SkiaFontRenderer.getHeight(titleFont);
                }
            }
            case SLIDER -> {
                title = tr(row.slider().titleKey());
                String value = "card_blur_strength".equals(row.slider().id()) ? "300% → 300%" : row.slider().displayValue();
                reserved = SkiaFontRenderer.getStringWidth(subFont, value) + s(12);
                controlH = UiTokens.SETTINGS_VALUE_BAND;
                if (isInlineNumberSlider(row.slider().id())) {
                    desc = inlineNumberDescription(row.slider());
                    reserved = SettingsRowText.NUMBER_W+SettingsRowText.UNIT_W+UiTokens.SETTINGS_COMPACT_GAP;
                    controlH = SettingsFormField.BASE_H;
                }
            }
            default -> { return null; }
        }
        boolean number=row.kind()==RowKind.SLIDER&&isInlineNumberSlider(row.slider().id());
        boolean wordTitles = number || (row.kind() != RowKind.SLIDER && SettingsRowText.hasWordBoundaries(title));
        var flow = SettingsRowText.measure(title, desc, width, reserved, controlH, wordTitles);
        if(!flow.stacked())return null;
        float h=UiTokens.SETTINGS_ROW_PAD*2+flow.titles().size()*flow.titleH()
                +flow.descriptions().size()*flow.descriptionH()+controlH+UiTokens.SETTINGS_COMPACT_GAP*2;
        if (row.kind() == RowKind.SLIDER && !number) h += UiTokens.SETTINGS_VALUE_BAND;
        return new CompactText(flow.titles(),flow.descriptions(),flow.titleH(),flow.descriptionH(),controlH,Math.max(rowHeight(row.kind()),h));
    }

    private static SettingsRowText.Text compactFlow(CompactText flow) {
        return flow == null ? null : new SettingsRowText.Text(flow.title(), flow.description(), true,
                flow.titleH(), flow.descriptionH(), flow.height());
    }

    private void drawCompactText(Canvas canvas,UiLayout.Rect rect,CompactText flow,int descColor){
        SettingsRowText.draw(canvas,rect,compactFlow(flow),textPrimary(),descColor);
    }

    public float rowHeight(Row row, UiLayout layout) {
        return rowHeight(row, layout, compactText(row, rowCardWidth(layout)));
    }

    private float rowHeight(Row row, UiLayout layout, CompactText compact) {
        RowKind kind = row.kind();
        float base = rowHeight(kind);
        if (kind == RowKind.LABEL) {
            String title = tr(row.labelKey());
            return title.equals(row.labelKey()) ? base
                    : SettingsGroupPaint.height(title, rowCardWidth(layout), foldableGroup(row) != null);
        }
        if (compact != null) base = compact.height();
        if (kind == RowKind.COLOR) {
            Font font = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            int lines = colorTitleLines(row.color(), font, rowCardWidth(layout)).size();
            return Math.max(base, lines * SkiaFontRenderer.getHeight(font) + s(12));
        }
        if (compact != null) return base;
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
        List<String> lines = SettingsRowText.wrapCopy(subFont, text, maxWidth);
        return base + Math.max(0, lines.size() - 1) * descriptionLineHeight(subFont);
    }

    /** Copy for an action card, shared by layout measurement and drawing. */
    private record ActionCopy(String title, String subtitle, String verb,
                              boolean available, boolean redConfirm) {
    }

    private ActionCopy historyCleanupCopy(SettingsItem item) {
        boolean available = item.available();
        String subtitle = available
                ? tr(AtomChatConfig.get().chatHistoryEnabled
                        ? "atomchat.settings.chat.history.clear.desc.saved"
                        : "atomchat.settings.chat.history.clear.desc.memory")
                : tr(item.subtitleKey() + ".unavailable");
        return new ActionCopy(tr(item.titleKey()), subtitle,
                available ? tr("atomchat.settings.action.clear") : "", available, false);
    }

    private ActionCopy actionCopy(Row row) {
        if (ACTION_HISTORY_CLEAR.equals(row.actionId())) return historyCleanupCopy(row.item());
        SettingsItem item = row.item();
        boolean available = item.available();
        boolean redConfirm = false;
        String subtitle;
        String verb;
        if (!available) {
            subtitle = tr(item.performanceLocked() ? "atomchat.settings.performance.locked"
                    : item.subtitleKey() + ".unavailable");
            verb = item.performanceLocked() ? tr("atomchat.settings.performance.off") : "";
        } else if (redConfirm) {
            subtitle = tr(item.subtitleKey());
            verb = tr("atomchat.settings.appearance.wallpaper.clear.confirm");
        } else if (AppearanceSettings.part(row.actionId()) != null) {
            Part part = AppearanceSettings.part(row.actionId());
            String selected = AppearanceSettings.selectedId(part, AtomChatConfig.get());
            String label = AppearanceSettings.label(part, selected);
            subtitle = AppearanceSettings.MISSING.equals(label)
                    ? tr(AppearanceSettings.TITLE_PREFIX + "missing.desc", selected) : "";
            verb = tr(label);
        } else if ("onboarding_replay".equals(row.actionId())) {
            subtitle = tr(item.subtitleKey());
            verb = tr("atomchat.tutorial.replay");
        } else if (ACTION_WALLPAPER_PICK.equals(row.actionId())) {
            Path wallpaper = WallpaperStore.current();
            subtitle = wallpaper != null && wallpaper.getFileName() != null
                    ? wallpaper.getFileName().toString()
                    : tr(item.subtitleKey());
            verb = tr("atomchat.settings.action.choose");
        } else if (ACTION_TELEPORT_MODE.equals(row.actionId())) {
            String mode = AtomChatConfig.get().teleportCommandMode;
            subtitle = tr(item.subtitleKey());
            verb = tr("atomchat.settings.chat.teleport." + (mode == null ? "auto" : mode));
        } else if (ACTION_AVATAR_STYLE.equals(row.actionId())) {
            subtitle = tr(item.subtitleKey());
            verb = tr(AtomChatConfig.get().effectiveAvatarStyle() == AtomChatConfig.AvatarStyle.E33CHAT
                    ? "atomchat.settings.chat.avatar_style.e33chat"
                    : "atomchat.settings.chat.avatar_style.atomchat");
        } else if (ACTION_HUD_EDIT.equals(row.actionId()) || "panel_edit".equals(row.actionId())) {
            subtitle = tr(item.subtitleKey());
            verb = tr("atomchat.settings.action.edit");
        } else if (ACTION_PAGE_NAV_STYLE.equals(row.actionId())) {
            subtitle = tr(item.subtitleKey());
            verb = tr(AtomChatConfig.get().pageNavStyle
                    == AtomChatConfig.PageNavStyle.ZOOM
                    ? "atomchat.settings.appearance.page_nav_style.zoom"
                    : "atomchat.settings.appearance.page_nav_style.slide");

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
                String key = item.performanceLocked() ? "atomchat.settings.performance.locked"
                        : item.available() ? item.subtitleKey() : item.subtitleKey() + ".unavailable";
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
                if (!copy.verb().isEmpty()) {
                    Font verbFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
                    float valueWidth = SkiaFontRenderer.getStringWidth(verbFont, copy.verb());
                    reserved = (copy.available() && SettingsStepper.supports(row.actionId())
                            ? SettingsStepper.preferredWidth(valueWidth) : valueWidth) + s(12);
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
        rowBuilds++;
        String chip = activeChipId(section);
        boolean addonTab = chip.contains(":");
        List<Row> base = addonTab ? List.of() : switch (section) {
            case CHAT -> chatRows(chip);
            case APPEARANCE -> appearanceRows(chip);
            case PRIVACY -> privacyRows();
            case ABOUT -> aboutRows();
        };
        String tabId = addonTab ? chip : SettingsUi.builtInTabId(chip);
        var edits = SettingsUi.registeredGroups(section, tabId);
        var rowEdits = SettingsUi.registeredRows(section, tabId);
        return (edits == null || !edits.hasEdits()) && rowEdits.isEmpty() ? base
                : mergeGroups(base, edits, rowEdits, section.id() + "/" + tabId);
    }

    private record OriginalGroup(SettingsUi.Group value, List<Row> rows) {}

    private List<Row> mergeGroups(List<Row> base, UiContributions<SettingsUi.Group> edits,
                                  Map<String, UiContributions<SettingsUi.Control>> rowEdits, String scope) {
        List<UiContributions.Entry<SettingsUi.Group>> groups = new ArrayList<>();
        Map<String, OriginalGroup> originals = new HashMap<>();
        int start = 0;
        while (start < base.size() && base.get(start).kind() != RowKind.LABEL) start++;
        List<Row> result = new ArrayList<>();
        appendScopedRows(result, mergeRows(base.subList(0, start), rowEdits.get(SettingsUi.TOP_ROWS)),
                scope + "/" + SettingsUi.TOP_ROWS);
        while (start < base.size()) {
            String key = base.get(start).labelKey();
            int end = start + 1;
            while (end < base.size() && base.get(end).kind() != RowKind.LABEL) end++;
            String id = SettingsUi.builtInGroupId(key);
            var value = new SettingsUi.Group(key, List.of());
            groups.add(new UiContributions.Entry<>(id, value));
            originals.put(id, new OriginalGroup(value, base.subList(start, end)));
            start = end;
        }
        for (var group : edits == null ? groups : edits.resolve(groups)) {
            var original = originals.get(group.id());
            Row header;
            List<Row> children;
            if (original != null && original.value() == group.value()) {
                var rows = original.rows();
                header = rows.get(0);
                children = rows.subList(1, rows.size());
            } else {
                header = Row.ofGroup(group.value().labelKey(), scope + "/" + group.id());
                children = group.value().toggles().stream()
                        .map(toggle -> Row.ofSwitch(SettingsItem.addon(toggle))).toList();
            }
            result.add(header);
            String fold = foldableGroup(header);
            if (fold == null || !collapsedColorGroups.contains(fold) || expansionOf(fold) > 0F) {
                appendScopedRows(result, mergeRows(children, rowEdits.get(group.id())),
                        scope + "/" + group.id());
            }
        }
        return result;
    }

    private static void appendScopedRows(List<Row> target, List<Row> rows, String scope) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            target.add(row.kind() == RowKind.SWITCH ? row.withPresentationId(scope + "/" + controlId(row) + "/" + i) : row);
        }
    }

    private List<Row> mergeRows(List<Row> rows, UiContributions<SettingsUi.Control> edits) {
        if (edits == null || !edits.hasEdits()) return rows;
        Map<String, Row> original = new HashMap<>();
        List<UiContributions.Entry<SettingsUi.Control>> defaults = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            String id = controlId(row);
            if (original.containsKey(id)) id += "/" + i;
            original.put(id, row);
            defaults.add(new UiContributions.Entry<>(id, SettingsUi.OriginalControl.KEEP));
        }
        List<Row> resolved = new ArrayList<>();
        for (var entry : edits.resolve(defaults)) {
            if (entry.value() instanceof SettingsUi.Toggle toggle) {
                resolved.add(Row.ofSwitch(SettingsItem.addon(toggle)));
            } else if (entry.value() instanceof SettingsUi.Info info) {
                resolved.add(Row.ofInfo(new SettingsCatalog.InfoRow(info.titleKey(), tr(info.bodyKey()), null)));
            } else if (original.containsKey(entry.id())) {
                resolved.add(original.get(entry.id()));
            }
        }
        return resolved;
    }

    private static String controlId(Row row) {
        if (row.item() != null) {
            String id = row.item().id();
            return id.contains(":") ? id : SettingsUi.builtInRowId(id);
        }
        if (row.slider() != null) return SettingsUi.builtInRowId(row.slider().id());
        if (row.color() != null) return SettingsUi.builtInRowId(row.color().id());
        if (row.info() != null) return SettingsUi.builtInRowId(row.info().titleKey());
        if (row.player() != null) return SettingsUi.builtInRowId(row.player().realName());
        return SettingsUi.builtInRowId(row.kind().name());
    }

    long rowBuildsForTest() { return rowBuilds; }

    long compactTextBuildsForTest() { return compactTextBuilds; }

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

    private List<Row> historyRows() {
        List<Row> rows = new ArrayList<>();
        addHistoryRows(rows);
        return rows;
    }

    private void addHistoryRows(List<Row> rows) {
        addGroup(rows, "atomchat.settings.group.history.storage", () -> {
            rows.add(Row.ofSwitch(SettingsCatalog.historySwitch()));
            rows.add(Row.ofSlider(SettingsCatalog.historyRetention()));
        });
        addGroup(rows, "atomchat.settings.group.history.cleanup",
                () -> rows.add(Row.ofAction(ACTION_HISTORY_CLEAR, historyClearItem())));
    }

    private List<Row> chatRows(String chip) {
        List<Row> rows = new ArrayList<>();
        switch (chip == null ? "messages" : chip) {
            case "history" -> addHistoryRows(rows);
            case "notify" -> {
                addGroup(rows, "atomchat.settings.group.notify.mentions", () ->
                        addSwitches(rows, SettingsSection.CHAT, "mention_banner", "mention_sound"));
                addGroup(rows, "atomchat.settings.group.notify.whispers", () ->
                        addSwitches(rows, SettingsSection.CHAT, "whisper_banner", "whisper_sound"));
                addGroup(rows, "atomchat.settings.group.notify.audio", () -> {
                    addSwitches(rows, SettingsSection.CHAT, "notification");
                    addSliders(rows, SettingsSection.CHAT, "notify_volume");
                    rows.add(Row.ofAction(ACTION_TEST_SOUND, testSoundItem()));
                });
            }
            case "effects" -> {
                addGroup(rows, "atomchat.settings.group.chat.fun",
                        () -> addSwitches(rows, SettingsSection.CHAT, "fun_effects"));
                addGroup(rows, "atomchat.settings.group.chat.experimental",
                        () -> addSwitches(rows, SettingsSection.CHAT, "experimental_effects"));
            }
            default -> {
                addSwitches(rows, SettingsSection.CHAT, "enabled");
                addGroup(rows, "atomchat.settings.group.chat.hud", () -> {
                    addSwitches(rows, SettingsSection.CHAT, "chat_hud");
                    addSliders(rows, SettingsSection.CHAT, "chat_hud_max_messages");
                    addSwitches(rows, SettingsSection.CHAT, "chat_hud_timed_exit");
                });
                addGroup(rows, "atomchat.settings.group.chat.input", () ->
                        addSwitches(rows, SettingsSection.CHAT, "close_after_send", "retain_draft_on_close"));
                addGroup(rows, "atomchat.settings.group.chat.messages_display", () -> {
                    rows.add(Row.ofAction(ACTION_AVATAR_STYLE, avatarStyleItem()));
                    addSwitches(rows, SettingsSection.CHAT, "compact_messages");
                    addSliders(rows, SettingsSection.CHAT, "timestamp");
                });
                addGroup(rows, "atomchat.settings.group.chat.content", () ->
                        addSwitches(rows, SettingsSection.CHAT, "images", "anti_spam"));
            }
        }
        return rows;
    }

    private List<Row> appearanceRows(String chip) {
        List<Row> rows = new ArrayList<>();
        switch (chip == null ? "theme" : chip) {
            case "material" -> {
                addGroup(rows, "atomchat.settings.group.appearance.panel_background", () -> {
                    addSwitches(rows, SettingsSection.APPEARANCE, "blur");
                    addSliders(rows, SettingsSection.APPEARANCE, "blur_strength", "opacity");
                });
                addGroup(rows, "atomchat.settings.group.appearance.cards", () ->
                        addSliders(rows, SettingsSection.APPEARANCE, "card_blur_strength", "cardtint"));
                addGroup(rows, "atomchat.settings.group.appearance.corners", () ->
                        addSliders(rows, SettingsSection.APPEARANCE, "outline_width", "corner_radius"));
            }
            case "adjust" -> {
                addGroup(rows, "atomchat.settings.group.appearance.panel_layout", () ->
                        rows.add(Row.ofAction("panel_edit", panelEditItem())));
                addGroup(rows, "atomchat.settings.group.appearance.hud", () -> {
                    rows.add(Row.ofAction(ACTION_HUD_EDIT, hudEditItem()));
                    addSwitches(rows, SettingsSection.APPEARANCE, "hud_icon");
                });
                addGroup(rows, "atomchat.settings.group.appearance.layout", () -> {
                    addSwitches(rows, SettingsSection.APPEARANCE, "half_screen");
                    addSliders(rows, SettingsSection.APPEARANCE, "width", "height", "scale", "contentscale");
                });
            }
            case "interaction" -> {
                addGroup(rows, "atomchat.settings.group.appearance.motion", () -> {
                    addSwitches(rows, SettingsSection.APPEARANCE, "motion");
                    rows.add(Row.ofAction(ACTION_PAGE_NAV_STYLE, pageNavStyleItem()));
                    addSwitches(rows, SettingsSection.CHAT, "entry");
                });
                addGroup(rows, "atomchat.settings.group.interaction.feedback", () -> {
                    addSwitches(rows, SettingsSection.CHAT, "feedback", "feedback_button", "feedback_send", "feedback_panel");
                    addSliders(rows, SettingsSection.CHAT, "feedback_volume");
                });
                addGroup(rows, "atomchat.settings.group.chat.shortcuts", () -> {
                    addSwitches(rows, SettingsSection.CHAT, "poke");
                    rows.add(Row.ofAction(ACTION_TELEPORT_MODE, teleportModeItem()));
                });
            }
            case "colors" -> {
                addColorGroup(rows, "atomchat.settings.group.appearance.message_colors", "bubble");
                addColorGroup(rows, "atomchat.settings.group.appearance.ui_colors", "ui");
            }
            default -> {
                addGroup(rows, QUALITY_TITLE, () -> rows.add(new Row(
                        RowKind.QUALITY, null, null, null, null, null, null, null, null, null)));
                addGroup(rows, "atomchat.settings.group.appearance.presets", () -> rows.add(Row.ofThemes()));
                addGroup(rows, "atomchat.settings.group.appearance.bubbles", () -> rows.add(Row.ofBubbles()));
                AtomChatConfig config = AtomChatConfig.get();
                if (AppearanceSettings.visible(Part.BUBBLE, config)
                        || AppearanceSettings.visible(Part.AVATAR_FRAME, config)) {
                    addGroup(rows, AppearanceSettings.TITLE_PREFIX + "group", () -> {
                        addAppearanceSelector(rows, Part.BUBBLE, config);
                        addAppearanceSelector(rows, Part.AVATAR_FRAME, config);
                    });
                }
                addGroup(rows, "atomchat.settings.group.appearance.wallpaper", () -> {
                    rows.add(new Row(RowKind.WALLPAPERS, null, null, null, null, null, null, null, null, null));
                    rows.add(Row.ofAction(ACTION_WALLPAPER_PICK, wallpaperPickItem()));
                    if (WallpaperStore.isSet()) {
                        rows.add(Row.ofAction(ACTION_WALLPAPER_CLEAR, wallpaperClearItem()));
                    }
                });
            }
        }
        return rows;
    }

    private void addAppearanceSelector(List<Row> rows, Part part, AtomChatConfig config) {
        if (!AppearanceSettings.visible(part, config)) return;
        String action = AppearanceSettings.action(part);
        rows.add(Row.ofAction(action, new SettingsItem(action, AppearanceSettings.title(part),
                "", () -> false, unused -> {})));
    }

    private void addColorGroup(List<Row> rows, String heading, String group) {
        addGroup(rows, heading, () -> {
            for (SettingsColor color : SettingsCatalog.colors(SettingsSection.APPEARANCE)) {
                if (group.equals(color.group())) rows.add(Row.ofColor(color));
            }
        });
    }

    private List<Row> privacyRows() {
        List<Row> rows = new ArrayList<>();
        addGroup(rows, "atomchat.settings.group.privacy.sync", () ->
                addSwitches(rows, SettingsSection.PRIVACY, "server_packs"));
        addGroup(rows, "atomchat.settings.group.privacy.messages", () ->
                addSwitches(rows, SettingsSection.PRIVACY, "hideBlocked"));
        addGroup(rows, "atomchat.settings.group.privacy.blocked", () -> {
            List<PlayerRef> players = blockedPlayers();
            if (players.isEmpty()) {
                rows.add(new Row(RowKind.BLOCKED_EMPTY, null, null, null, null, null, null, null, null, null));
            }
            for (PlayerRef player : players) {
                rows.add(Row.ofBlocked(player));
            }
        });
        return rows;
    }

    private List<Row> aboutRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.ofHero());
        rows.add(Row.ofAction("onboarding_replay", new SettingsItem("onboarding_replay",
                "atomchat.tutorial.prompt.title", "atomchat.tutorial.replay.desc", () -> true, v -> { })));
        addGroup(rows, "atomchat.settings.about.authors", () -> rows.add(Row.ofAuthors()));
        addGroup(rows, LABEL_MOD_INFO, () -> {
            for (SettingsCatalog.InfoRow info : SettingsCatalog.aboutCoreRows()) {
                rows.add(Row.ofInfo(info));
            }
        });
        addGroup(rows, LABEL_THIRD_PARTY, () ->
                rows.add(new Row(RowKind.CREDITS, null, null, null, null, null, null, null, null, null)));
        addGroup(rows, LABEL_MAINTENANCE, () -> {
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
    private String owningGroup(List<Row> rows, int index) {
        return rowGroups(rows)[index];
    }

    /** Resolves every row's fold owner in one pass; callers used to rescan the prefix. */
    private String[] rowGroups(List<Row> rows) {
        if (rowGroupRows == rows && rowGroups != null && rowGroups.length == rows.size()) {
            return rowGroups;
        }
        String[] resolved = new String[rows.size()];
        String active = null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind() == RowKind.LABEL) {
                String own = foldableGroup(row);
                resolved[i] = own == null ? active : null;
                active = own;
            } else {
                resolved[i] = active;
            }
        }
        rowGroupRows = rows;
        rowGroups = resolved;
        return resolved;
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
        for (Row row : rows) if (row.kind() == RowKind.LABEL) {
            String id=foldableGroup(row);
            if(id!=null)groupExpansion.putIfAbsent(id,collapsedColorGroups.contains(id)?0f:1f);
        }
        if(SettingsGroupFrontend.stepFolds(groupExpansion,collapsedColorGroups,dtMs))rowGeometry=null;
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
     * page's merged-card fold groups. Those children
     * render as one merged card ({@link #drawAboutCard}), so {@link
     * #rowAdvance} collapses the gap between two of them and the draw pass
     * routes the whole run through the card. Membership comes from the row
     * list itself (via {@link #owningGroup}), like every other geometry
     * consumer.
     */
    private boolean isAboutCardChild(List<Row> rows, int index) {
        // These rows paint their own content while their heading still controls folding.
        RowKind kind = rows.get(index).kind();
        return kind != RowKind.AUTHORS && kind != RowKind.BLOCKED_EMPTY && owningGroup(rows, index) != null;
    }

    private float rowAdvance(List<Row> rows, int index, UiLayout layout) {
        float expansion = rowExpansion(rows, index);
        float nextExpansion = index + 1 < rows.size() ? rowExpansion(rows, index + 1) : 1.0F;
        // Two children of a merged about card sit contiguously — the hairline
        // divider owns the seam inside the card — so the inter-row gap
        // between them is zero. Measurement, rects and the hit-test all walk
        // this method, so the card's geometry cannot drift apart.
        float gap = UiTokens.SETTINGS_ROW_GAP * nextExpansion;
        if (index + 1 < rows.size()) {
            String group = owningGroup(rows, index);
            if (group != null && group.equals(owningGroup(rows, index + 1))) {
                gap = 0.0F;
            }
        }
        return rowHeight(rows.get(index), layout) * expansion + gap;
    }

    private AppearanceMeasurement appearanceMeasurement;

    private static final class AppearanceMeasurement {
        private final List<Row> rows;
        private final float width;
        private final java.util.Set<String> collapsed;
        private final Font titleFont;
        private final Font subtitleFont;
        private final long languageRevision;
        private final boolean hasWallpaper;
        private final long appearanceRevision;
        private final AppearanceSettings.Selection appearanceSelection;
        private final boolean tutorial;
        private final IdentityHashMap<Row, CompactText> text = new IdentityHashMap<>();
        private int nextRow;
        private boolean themePreviewsPending;

        private AppearanceMeasurement(List<Row> rows, float width, java.util.Set<String> collapsed,
                                      Font titleFont, Font subtitleFont, long languageRevision, boolean hasWallpaper,
                                      long appearanceRevision, AppearanceSettings.Selection appearanceSelection, boolean tutorial) {
            this.rows = rows;
            this.width = width;
            this.collapsed = java.util.Set.copyOf(collapsed);
            this.titleFont = titleFont;
            this.subtitleFont = subtitleFont;
            this.languageRevision = languageRevision;
            this.hasWallpaper = hasWallpaper;
            this.appearanceRevision = appearanceRevision;
            this.appearanceSelection = appearanceSelection;
            this.tutorial = tutorial;
            this.themePreviewsPending = rows.stream().anyMatch(row -> row.kind() == RowKind.THEMES);
        }
    }

    private AppearanceMeasurement appearanceMeasurement(UiLayout layout) {
        if (!"theme".equals(activeChipId(SettingsSection.APPEARANCE))) return null;
        for (var fold : groupExpansion.entrySet()) {
            if (fold.getValue() != (collapsedColorGroups.contains(fold.getKey()) ? 0F : 1F)) return null;
        }
        boolean hasWallpaper = WallpaperStore.isSet();
        AtomChatConfig config = AtomChatConfig.get();
        long appearanceRevision = AppearanceUi.revision();
        boolean tutorial = com.atom.chat.onboarding.TutorialSandbox.active();
        float width = rowCardWidth(layout);
        Font title = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font subtitle = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        long languageRevision = currentTranslationRevision();
        AppearanceMeasurement measurement = appearanceMeasurement;
        if (measurement == null || measurement.width != width || measurement.titleFont != title
                || measurement.subtitleFont != subtitle || measurement.languageRevision != languageRevision
                || measurement.hasWallpaper != hasWallpaper || measurement.appearanceRevision != appearanceRevision
                || !measurement.appearanceSelection.matches(config) || measurement.tutorial != tutorial
                || !measurement.collapsed.equals(collapsedColorGroups)) {
            measurement = new AppearanceMeasurement(rows(SettingsSection.APPEARANCE), width,
                    collapsedColorGroups, title, subtitle, languageRevision, hasWallpaper,
                    appearanceRevision, AppearanceSettings.Selection.capture(config), tutorial);
            appearanceMeasurement = measurement;
        }
        return measurement;
    }

    /** Prepare one row per settled settings-home frame, including the cold theme preview paint. */
    public void warmAppearanceTheme(UiLayout layout) {
        AppearanceMeasurement measurement = appearanceMeasurement(layout);
        if (measurement == null) return;
        boolean textComplete = measurement.nextRow == measurement.rows.size();
        // Measuring the page may finish the text work before a preview is ever painted.
        if (measurement.themePreviewsPending && (textComplete
                || measurement.rows.get(measurement.nextRow).kind() == RowKind.THEMES)) {
            warmThemePreviews(measurement.width);
            measurement.themePreviewsPending = false;
            return;
        }
        if (textComplete) return;
        Row row = measurement.rows.get(measurement.nextRow++);
        if (row.kind() != RowKind.SLIDER) {
            measurement.text.put(row, measureCompactText(row, measurement.width));
        }
    }

    private RowGeometry buildRowGeometry(SettingsSection section, List<Row> rows,
                                         UiLayout layout) {
        float width = rowCardWidth(layout);
        long languageRevision = currentTranslationRevision();
        String[] groups = rowGroups(rows);
        IdentityHashMap<Row, CompactText> compactTexts = new IdentityHashMap<>();
        AppearanceMeasurement prepared = appearanceMeasurement;
        boolean preparedRows = prepared != null && prepared.rows == rows && prepared.languageRevision == languageRevision;
        float[] full = new float[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            CompactText compact = preparedRows && prepared.text.containsKey(row)
                    ? prepared.text.get(row) : measureCompactText(row, width);
            compactTexts.put(row, compact);
            full[i] = rowHeight(row, layout, compact);
        }
        if (preparedRows) {
            compactTexts.forEach((row, text) -> {
                if (row.kind() != RowKind.SLIDER) prepared.text.put(row, text);
            });
            prepared.nextRow = rows.size();
        }
        var slots=section==SettingsSection.CHAT&&"history".equals(activeChipId(section))
                ?contractLayout.layout(width,full,groups,this::expansionOf).slots()
                :SettingsGroupFrontend.layout(full,groups,this::expansionOf);
        float contentHeight = slots.contentHeight();
        RowGeometry geometry = new RowGeometry(rows, section, layout.list.x(), layout.list.y(),
                layout.list.w(), width, slots.tops(), slots.heights(), contentHeight, languageRevision, compactTexts);
        rowGeometry = geometry;
        return geometry;
    }

    private RowGeometry rowGeometry(SettingsSection section, List<Row> rows, UiLayout layout) {
        RowGeometry cached = rowGeometry;
        if (cached != null && cached.languageRevision() == currentTranslationRevision()
                && cached.rows() == rows && cached.section() == section
                && Float.compare(cached.listX(), layout.list.x()) == 0
                && Float.compare(cached.listY(), layout.list.y()) == 0
                && Float.compare(cached.listW(), layout.list.w()) == 0) {
            return cached;
        }
        return buildRowGeometry(section, rows, layout);
    }

    public float measureContent(UiLayout layout, SettingsSection section) {
        AppearanceMeasurement prepared = section == SettingsSection.APPEARANCE ? appearanceMeasurement(layout) : null;
        List<Row> rows = prepared == null ? rows(section) : prepared.rows;
        measuredRowsSection = section;
        measuredRowsChip = activeChipId(section);
        measuredRows = rows;
        RowGeometry geometry = buildRowGeometry(section, rows, layout);
        if (rows.isEmpty()) return UiTokens.ROOT_CONTENT_GAP + chipBarHeight(section);
        float total = UiTokens.ROOT_CONTENT_GAP + chipBarHeight(section) + geometry.contentHeight();
        // Leave the same hover-clearance under the last card that rowCardWidth
        // leaves at its sides. Without it, scrolling to the end parks the card
        // exactly on the clip edge and the press spring shears its bottom.
        return total + UiTokens.ROW_CLIP_INSET;
    }

    /** Top edge of the scrollable rows: content gap plus the chip band. */
    private float contentTop(UiLayout layout, SettingsSection section) {
        return SettingsGroupFrontend.contentTop(layout,chipBarHeight(section));
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

    public record TutorialFolds(java.util.Set<String> collapsed,java.util.Map<String,Float> expansion) { }
    public TutorialFolds tutorialFolds(){return new TutorialFolds(java.util.Set.copyOf(collapsedColorGroups),java.util.Map.copyOf(groupExpansion));}
    public void restoreTutorialFolds(TutorialFolds saved){collapsedColorGroups.clear();collapsedColorGroups.addAll(saved.collapsed());groupExpansion.clear();groupExpansion.putAll(saved.expansion());}
    public void expandTutorialGroup(SettingsSection section,int index) {
        var visible=rows(section);if(index<0||index>=visible.size())return;
        String group=null;
        for(int i=0;i<=index;i++)if(visible.get(i).kind()==RowKind.LABEL)group=foldableGroup(visible.get(i));
        if(group!=null){collapsedColorGroups.remove(group);groupExpansion.put(group,1f);}
    }
    public record TutorialRow(String title,String body,boolean startsGroup,boolean standalone) { }
    public List<TutorialRow> tutorialRows(SettingsSection section) {
        var result=new java.util.ArrayList<TutorialRow>();
        for(var row:rows(section)) {
            String title=row.item()!=null?tr(row.item().titleKey()):row.slider()!=null?tr(row.slider().titleKey())
                    :row.color()!=null?tr(row.color().titleKey()):row.info()!=null?tr(row.info().titleKey())
                    :row.labelKey()!=null?tr(row.labelKey()):row.kind()==RowKind.THEMES?tr("atomchat.settings.appearance.theme")
                    :row.kind()==RowKind.QUALITY?tr(QUALITY_TITLE)
                    :row.kind()==RowKind.WALLPAPERS?tr("atomchat.settings.appearance.wallpaper")
                    :row.kind()==RowKind.BUBBLES?tr("atomchat.tutorial.appearance.title"):tr("atomchat.tutorial."+section.id()+".title");
            String body=descriptionText(row);
            if(body==null||body.isBlank())body=title;
            result.add(new TutorialRow(title,body,row.kind()==RowKind.LABEL,row.kind()==RowKind.THEMES||row.kind()==RowKind.WALLPAPERS||row.kind()==RowKind.BUBBLES||row.kind()==RowKind.QUALITY));
        }
        return java.util.List.copyOf(result);
    }
    public int graphicsQualityTutorialRow() {
        var rows = rows(SettingsSection.APPEARANCE);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind() == RowKind.QUALITY) return i;
        }
        return -1;
    }

    /** The same row geometry used by paint and hit testing, scrolled into view before annotating. */
    public UiLayout.Rect tutorialAnchor(SettingsSection section,int index,UiLayout layout,com.atom.chat.ui.ScrollController scroll) {
        expandTutorialGroup(section,index);
        var rows=rows(section);
        if(index<0||index>=rows.size())return layout.list;
        var rect=rowRect(section,rows,index,scroll.getScrollY(),layout);
        if(rect.y()<layout.list.y()||rect.bottom()>layout.list.bottom()) {
            float top=contentTop(layout,section);
            var geometry=rowGeometry(section,rows,layout);
            scroll.setContent(Math.max(layout.list.h(),geometry.contentHeight()+top-layout.list.y()),layout.list.h());
            scroll.scrollTo(Math.max(0,geometry.tops()[index]),false);
            rect=rowRect(section,rows,index,scroll.getScrollY(),layout);
        }
        return staggerHits.paintedBounds(rect);
    }

    public UiLayout.Rect tutorialGroupAnchor(SettingsSection section,int first,int last,UiLayout layout,float scrollY) {
        var rows=rows(section);
        if(first<0||first>=rows.size())return layout.list;
        var a=rowRect(section,rows,first,scrollY,layout);
        var b=rowRect(section,rows,Math.min(last,rows.size()-1),scrollY,layout);
        a=staggerHits.paintedBounds(a);b=staggerHits.paintedBounds(b);
        float top=Math.max(layout.list.y(),a.y()),bottom=Math.min(layout.list.bottom(),b.bottom());
        return new UiLayout.Rect(a.x(),top,a.w(),Math.max(0,bottom-top));
    }

    /** Package-private so layout tests can assert the scroll-end clearance. */
    public UiLayout.Rect actionAnchor(SettingsSection section, String actionId, UiLayout layout, float scrollY) {
        var rows=rows(section);
        for(int i=0;i<rows.size();i++) if(actionId.equals(rows.get(i).actionId())) return rowRect(section,rows,i,scrollY,layout);
        return layout.list;
    }

    UiLayout.Rect rowRect(SettingsSection section, List<Row> rows, int index,
                          float scrollY, UiLayout layout) {
        RowGeometry geometry = rowGeometry(section, rows, layout);
        float y = contentTop(layout, section) - scrollY + geometry.tops()[index];
        return new UiLayout.Rect(layout.list.x() + ROW_CLIP_INSET, y,
                geometry.cardWidth(), geometry.heights()[index]);
    }

    private static UiLayout.Rect sliderTrackRect(UiLayout.Rect row) {
        float halfKnob = UiTokens.SLIDER_KNOB / 2.0F;
        return new UiLayout.Rect(row.x() + UiTokens.SETTINGS_ROW_PAD + halfKnob,
                row.bottom() - UiTokens.SETTINGS_SLIDER_ROW_H + UiTokens.SETTINGS_SLIDER_TRACK_Y,
                Math.max(0.0F, row.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F
                        - UiTokens.SLIDER_KNOB),
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
        render(canvas,layout,section,vmx,vmy,scrollY,accent,true);
    }

    public void renderBody(Canvas canvas, UiLayout layout, SettingsSection section,
                           float vmx, float vmy, float scrollY, int accent) {
        render(canvas,layout,section,vmx,vmy,scrollY,accent,false);
    }

    private void render(Canvas canvas, UiLayout layout, SettingsSection section,
                        float vmx, float vmy, float scrollY, int accent, boolean includeChipBar) {
        staggerHits.clear();
        long now = UiStagger.frameTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        currentSectionForSettle = section;
        pointerX = vmx;
        pointerY = vmy;

        String chip = activeChipId(section);
        if (section == SettingsSection.CHAT && historyTabSelected() && settingsContract != null) {
            beginHistoryBody(now, dt);
        }
        List<Row> rows = measuredRowsSection == section && Objects.equals(measuredRowsChip, chip)
                && measuredRows != null ? measuredRows : rows(section);
        // Fold animation clock: advances every group's expansion toward the
        // state the collapsed set names, before drawing consumes it. The row
        // list above was built against last frame's values; at the one moment
        // that matters (the snap to 0) those rows contribute exactly zero
        // height, so membership and geometry never visibly disagree.
        stepGroupExpansions(rows, dt);
        float barH = chipBarHeight(section);
        updateStepperHover(section, rows, layout, vmx, vmy, scrollY, barH, dt);
        // The nav style as an axis, resolved once per frame: SLIDE moves rows
        // horizontally only, ZOOM lifts and scales them.
        UiStagger.Axis axis = PageNavAxis.current();
        // A SLIDE row starts up to ITEM_STYLE_SLIDE_PX right of its slot, past
        // the viewport's right edge, so its rounded corner would be shaved off
        // on the way in. Widening only that edge by the drift budget while
        // travel remains keeps the corner whole; once every slot has landed the
        // clip is the plain viewport again, so a resting frame draws no wider.
        float driftClipPad = axis == UiStagger.Axis.HORIZONTAL && !UiStagger.itemsSettled(entrance, now)
                ? UiStagger.ITEM_STYLE_SLIDE_PX : 0.0F;
        canvas.save();
        try {
            // The chip band owns the top of the list: rows clip below it, so a
            // scrolled list never draws through the pills.
            CornerGeometry.clip(canvas, layout.list.x() - CardShadowReach.PAD, layout.list.y() + barH,
                    layout.list.w() + driftClipPad + CardShadowReach.PAD * 2, layout.list.h() - barH, 0.0F);
            // One draw of one row set: a chip switch re-armed the entrance, so
            // the new rows rise in per row rather than crossfading against the
            // old set (the crossfade's two layers were the "slight jump + very
            // fast fade" the field reported on a chip switch).
            drawRows(canvas, layout, section, rows, vmx, vmy, scrollY, accent, dt, true);
        } finally {
            canvas.restore();
        }

        if (includeChipBar) renderFixedChipBar(canvas,layout,section,vmx,vmy,accent);

        int hovered = lastInteractiveHover;
        hoveredIndex = hovered;
        SettingsGroupFrontend.stepHover(rowHover,hovered>=0?hovered:null,dt);

        advanceSettle(now);
    }

    /**
     * The row loop of the active chip. {@code interactive} is false only for a
     * non-pointer pass (a frozen layer): it must not advance hover/press state.
     * Each visible row fades and rises on the page entrance's stagger.
     */
    private void drawRows(Canvas canvas, UiLayout layout, SettingsSection section,
                          List<Row> rows, float vmx, float vmy, float scrollY,
                          int accent, float dt, boolean interactive) {
        if (interactive) {
            lastInteractiveHover = -1;
        }
        if (rows.isEmpty()) {
            return;
        }
        long now = UiStagger.frameTimeMillis();
        // One axis for the whole pass: every row of this frame plays the same
        // style's motion, so the mapping cannot change mid-list.
        UiStagger.Axis axis = PageNavAxis.current();
        boolean skipTransparentPaint = section == SettingsSection.APPEARANCE;
        Font buttonFont = FontManager.font(UiTokens.FONT_QUOTE);
        int hovered = -1;
        canvas.save();
        try {
            // Stagger position = the row's slot inside the VISIBLE viewport, so
            // scrolling never re-plays an entrance for rows already on screen.
            int staggerIndex = 0;
            for (int i = 0; i < rows.size(); i++) {
                // Every fold group renders as ONE card (the owner's
                // single-card + hairline language): the first child draws the
                // whole block, the rest are already on the canvas behind it.
                if (isAboutCardChild(rows, i)) {
                    if (i > 0 && isAboutCardChild(rows, i - 1)) {
                        continue;
                    }
                    int end = i;
                    String group = owningGroup(rows, i);
                    while (end < rows.size() && group != null
                            && group.equals(owningGroup(rows, end))) {
                        end++;
                    }
                    // The card is one entry in the stagger: it fades and rises
                    // as a whole (its rows are already folded into one surface).
                    // The card's own bounds, derived exactly as drawAboutCard does
                    // (the union of its first and last row) — the ZOOM channel
                    // scales around it, not around the whole viewport.
                    UiLayout.Rect cardFirst = rowRect(section, rows, i, scrollY, layout);
                    UiLayout.Rect cardLast = rowRect(section, rows, end - 1, scrollY, layout);
                    UiLayout.Rect cardBounds = new UiLayout.Rect(cardFirst.x(), cardFirst.y(),
                            cardFirst.w(), cardLast.bottom() - cardFirst.y());
                    var cardMotion=SettingsGroupFrontend.motion(cardBounds,entrance,staggerIndex,now,axis);
                    staggerHits.add(cardBounds,cardMotion.dx(),cardMotion.dy(),cardMotion.scale());
                    // An appearance preview starts work only once its stagger can display it.
                    // Keep the same hit transform and slot even on an invisible frame.
                    if (skipTransparentPaint && cardMotion.alpha() == 0F) {
                        updateHiddenPreviewStrips(section, rows, i, end, cardBounds, layout, scrollY);
                    } else {
                        final int previousHovered=hovered;
                        final int cardStart=i,cardEnd=end;
                        hovered=SettingsGroupFrontend.stagger(canvas,cardBounds,
                                cardMotion,
                                ()->drawAboutCard(canvas,layout,section,rows,cardStart,cardEnd,scrollY,vmx,vmy,buttonFont,accent,dt,interactive,previousHovered));
                    }
                    staggerIndex++;
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
                        && row.kind() != RowKind.AUTHORS
                        && row.kind() != RowKind.BLOCKED_EMPTY
                        && (row.kind() != RowKind.LABEL || foldableGroup(row) != null)
                        && vmx >= rect.x() && vmx <= rect.right()
                        && vmy >= rect.y() && vmy <= rect.bottom();
                if (over) {
                    hovered = i;
                }
                colorRowHovered = over && row.kind() == RowKind.COLOR;
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
                PressScale press = row.kind() == RowKind.AUTHORS
                        || (row.kind() == RowKind.LABEL && foldableGroup(row) == null) ? null
                        : interactive ? rowPress.computeIfAbsent(i, k -> PressScale.bounce())
                        : rowPress.get(i);
                if (press != null && interactive) {
                    press.update(i == hovered, i == pressedRow, dt, Animations.enabled(), rect.w());
                }
                float rowAlpha = UiStagger.itemAlpha(entrance, staggerIndex, now);
                float rowRise = UiStagger.itemRise(entrance, staggerIndex, now, axis);
                float rowDx = styleDriftDx(axis, staggerIndex, now);
                float combinedAlpha = expansion * rowAlpha;
                float combinedRise = UiTokens.SETTINGS_FOLD_LIFT * (1.0F - expansion) + rowRise;
                staggerHits.add(rect, rowDx, combinedRise,
                        UiStagger.itemStyleZoomScale(entrance, staggerIndex, now, axis));
                if (skipTransparentPaint && combinedAlpha == 0F) {
                    staggerIndex++;
                    continue;
                }

                canvas.save();
                Paint foldFade = null;
                try {
                    // The page entrance and the fold share one layer: either can
                    // be at full weight and the row still fades once, and the
                    // rise is a single translate.
                    if (combinedAlpha < 0.995F) {
                        // The padded saveLayer keeps press overshoot and shadow
                        // tail inside the fade instead of punching through at
                        // full weight; the rect carries the drift and the rise.
                        // It used to grow UPWARD by the rise while the content
                        // moved down, which cut the bottom of the row's shadow.
                        foldFade = new Paint().setAlphaf(combinedAlpha);
                        canvas.saveLayer(CardShadowReach.layerRect(
                                rect.x(), rect.y(), rect.w(), rect.h(), combinedRise, rowDx),
                                foldFade);
                    }
                    if (combinedRise != 0.0F) {
                        canvas.translate(0.0F, combinedRise);
                    }
                    applyStyleMotion(canvas, axis, rect, rowDx, staggerIndex, now);
                    if (press != null) {
                        press.begin(canvas, rect.x() + rect.w() / 2.0F, rect.y() + rect.h() / 2.0F);
                    }
                    float maskOpacity = com.atom.chat.render.CardBackdrop.pushMaskOpacity(
                            combinedAlpha < .995F ? combinedAlpha : 1F);
                    try {
                        drawRow(canvas, row, rect, rowHover.getOrDefault(i, 0.0F),
                                buttonFont, accent, dt);
                    } finally {
                        com.atom.chat.render.CardBackdrop.restoreMaskOpacity(maskOpacity);
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
                staggerIndex++;
            }
        } finally {
            canvas.restore();
        }
        if (interactive) {
            lastInteractiveHover = hovered;
        }
    }

    /** The section chip row, sharing the bottom tab bar's cell geometry: equal
     *  cells from {@code layout.chipBar}, the accent capsule for the selection
     *  (sliding on the tab clock), an accent hover wash on the
     *  unselected cells, and a bounce on
     *  the whole chip cell. Like {@code BottomTabBar}, the per-cell spring
     *  wraps the entire cell (wash plus label) while the shared capsule rides
     *  the selected cell's spring: a capsule of its own would fight the slide. */
    public void renderFixedChipBar(Canvas canvas, UiLayout layout, SettingsSection section,
                                   float vmx, float vmy, int accent) {
        if (chipBarHeight(section) <= 0) return;
        long now=UiStagger.frameTimeMillis();
        float dt=Math.min(50F,Math.max(1F,now-lastChipFrameMs));
        lastChipFrameMs=now;
        pointerX=vmx;pointerY=vmy;
        updateChipMotion(layout,section,dt);
        drawChipBar(canvas,layout,section,accent,dt);
    }

    private void drawChipBar(Canvas canvas,UiLayout layout,SettingsSection section,int accent,float dtMs){
        sharedChipBar.draw(canvas,layout,chips(section).stream().map(chip->tr(chip.labelKey())).toList(),activeChipIndex(section),sec(200),true);
    }

    /** Advances the chip hover washes, press springs and the selection slide.
     *  Same cadence as {@code BottomTabBar.update}: once per frame. */
    public void updateChipMotion(UiLayout layout,SettingsSection section,float dtMs){
        sharedChipBar.update(layout,chips(section).size(),activeChipIndex(section),pressedChip,pointerX,pointerY,dtMs);
    }



    /** The style channel's drift for one stagger slot; 0 under ZOOM or once settled. */
    private float styleDriftDx(UiStagger.Axis axis, int staggerIndex, long now) {
        return UiStagger.itemStyleSlidePx(entrance, staggerIndex, now, axis);
    }

    /** Applies the item's style transform: ZOOM scales around {@code rect}, SLIDE drifts. */
    private void applyStyleMotion(Canvas canvas, UiStagger.Axis axis, UiLayout.Rect rect,
                                  float styleDx, int staggerIndex, long now) {
        if (axis == UiStagger.Axis.VERTICAL) {
            PanelOpenMotion.applyLayerScale(canvas, rect,
                    UiStagger.itemStyleZoomScale(entrance, staggerIndex, now, axis));
        } else if (styleDx != 0.0F) {
            canvas.translate(styleDx, 0.0F);
        }
    }

    private void drawRow(Canvas canvas, Row row, UiLayout.Rect rect, float hover,
                         Font buttonFont, int accent, float dtMs) {
        if (row.kind() == RowKind.LABEL) {
            drawLabel(canvas, row, rect);
            return;
        }
        if (row.kind() != RowKind.HERO && row.kind() != RowKind.AUTHORS
                && row.kind() != RowKind.BLOCKED_EMPTY) {
            AtomChatAppearance.drawCard(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                    UiTokens.settingsRowRadius(AtomChatAppearance.theme()), hover);
        }
        drawRowContent(canvas, row, rect, hover, buttonFont, accent, dtMs);
    }

    /** Draws content for a settings row. {@link #drawRow} paints its card
     *  surface where applicable; the About hero is a standalone brand row.
     *  Split from {@link #drawRow} so a merged settings card can lay
     *  its child rows on one shared surface instead of a card each. */
    private void drawRowContent(Canvas canvas, Row row, UiLayout.Rect rect, float hover,
                                Font buttonFont, int accent, float dtMs) {
        String contractSlot = hostedHistorySlot(row);
        if (contractSlot != null && currentSectionForSettle == SettingsSection.CHAT) {
            paintHistorySlot(canvas, contractSlot, rect);
            return;
        }
        switch (row.kind()) {
            case HERO -> drawHero(canvas, rect);
            case AUTHORS -> drawAuthors(canvas, rect, dtMs);
            case SWITCH -> drawSwitch(canvas, row, rect, accent, dtMs);
            case SLIDER -> drawSlider(canvas, row, rect, accent);
            case COLOR -> drawColor(canvas, row, rect, dtMs);
            case THEMES -> drawThemes(canvas, rect, dtMs);
            case WALLPAPERS -> drawWallpapers(canvas, rect, dtMs);
            case BUBBLES -> drawBubblePreviews(canvas, rect, dtMs);
            case CREDITS -> drawCredits(canvas, rect, dtMs);
            case QUALITY -> drawGraphicsQuality(canvas, rect, dtMs, accent);
            case INFO -> drawInfo(canvas, row, rect);
            case BLOCKED -> drawBlocked(canvas, row, rect, hover, buttonFont);
            case BLOCKED_EMPTY -> drawEmptyState(canvas, rect, "atomchat.settings.privacy.empty");
            case ACTION -> drawAction(canvas, row, rect, hover);
            default -> {
            }
        }
    }

    /**
     * One of the settings page's merged-card blocks:
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
            boolean stripPressed = switch (rows.get(start).kind()) {
                case CREDITS -> creditsStrip.pressed && !creditsStrip.dragged;
                case QUALITY -> qualityStrip.pressed && !qualityStrip.dragged;
                default -> false;
            };
            press.update(cardOver, stripPressed || pressedRow >= start && pressedRow < end,
                    dt, Animations.enabled(), Math.max(card.w(), card.h()));
        }
        final int initialHovered=hovered;
        return SettingsGroupFrontend.merged(canvas, card, expansion, press, () -> {
            String slot = section == SettingsSection.CHAT ? hostedHistorySlot(rows.get(start)) : null;
            if (slot != null) paintHistoryGroup(canvas, slot, card);
            else {
                SettingsGroupFrontend.drawCardSurface(canvas, card);
                SettingsGroupFrontend.clipCard(canvas, card);
            }
        }, () -> {
            int selected=initialHovered;
            float pad=UiTokens.SETTINGS_ROW_PAD;
                    for (int j = start; j < end; j++) {
                        Row row = rows.get(j);
                        UiLayout.Rect rect = rowRect(section, rows, j, scrollY, layout);
                        boolean over = interactive
                                && vmx >= rect.x() && vmx <= rect.right()
                                && vmy >= rect.y() && vmy <= rect.bottom();
                        if (over) {
                            selected = j;
                        }
                        if (interactive) {
                            drawnRowIndex = j;
                        }
                        colorRowHovered = over && row.kind() == RowKind.COLOR;
                        float rowAlpha = rowHover.getOrDefault(j, 0.0F);
                        if (rowAlpha > 0.01F) {
                            CornerGeometry.drawRoundedRect(canvas, rect.x(), rect.y(),
                                    rect.w(), rect.h(), 0.0F, UiTokens.cardHover(rowAlpha, AtomChatAppearance.theme()));
                        }
                        if (j > start) {
                            // Hairline divider, 1px at the card-edge alpha,
                            // indented to the row pad on both sides — it owns
                            // the seam the inter-row gap gave up.
                            CornerGeometry.drawRoundedRect(canvas, card.x() + pad, rect.y(),
                                    card.w() - pad * 2.0F, UiTokens.s(1.0F), 0.0F,
                                    UiTokens.hairline(AtomChatAppearance.theme()));
                        }
                        drawRowContent(canvas, row, rect, rowAlpha, buttonFont, accent, dt);
                    }
            return selected;
        });
    }

    /** Arms the row press bounce (mouse-down) or clears it (-1 on release). */
    public void setPressedRow(int index) {
        pressedRow = index;
    }

    /** Arms the chip press bounce (mouse-down) or clears it (-1 on release). */
    public void setPressedChip(int index) {
        pressedChip = index;
    }

    private static float colorControlSize() {
        return s(28);
    }

    private static UiLayout.Rect colorControl(UiLayout.Rect row) {
        float size = colorControlSize();
        return new UiLayout.Rect(row.right() - UiTokens.SETTINGS_ROW_PAD - size,
                row.y() + (row.h() - size) / 2F, size, size);
    }

    private List<String> colorTitleLines(SettingsColor color, Font font, float width) {
        float available = width - 2 * UiTokens.SETTINGS_ROW_PAD - colorControlSize() - UiTokens.SETTINGS_COMPACT_GAP;
        return SkiaFontRenderer.wrapWords(font, tr(color.titleKey()), Math.max(1F, available));
    }

    // ---------------------------------------------------------------- themes

    /** Content width of the card strip (both edge pads included). */
    private float stripContentWidth(CardStrip strip) {
        int count = stripCardCount(strip);
        return UiTokens.SETTINGS_ROW_PAD + count * stripCardWidth(strip)
                + (count - 1) * UiTokens.THEME_CARD_GAP + UiTokens.SETTINGS_ROW_PAD;
    }

    private int stripCardCount(CardStrip strip) {
        return switch (strip.kind) {
            case CREDITS -> SettingsCatalog.thirdPartyRows().size();
            case QUALITY -> GRAPHICS_QUALITIES.length;
            case BUBBLES -> AtomChatConfig.BubbleStyle.values().length;
            case WALLPAPERS -> com.atom.chat.wallpaper.WallpaperPresets.presets().length;
            default -> themeCardCount();
        };
    }

    private static boolean usesLogoCards(CardStrip strip) {
        return strip.kind == RowKind.CREDITS || strip.kind == RowKind.QUALITY;
    }

    private static float stripCardWidth(CardStrip strip) {
        return usesLogoCards(strip) ? AboutCreditCards.WIDTH : UiTokens.THEME_CARD_W;
    }

    private int builtInThemeCount() {
        return 1 + ThemeService.presets().length;
    }

    private int themeCardCount() {
        return builtInThemeCount() + AppearancePresetStore.get().list().size() + 1;
    }

    private float stripMaxScroll(CardStrip strip, UiLayout.Rect rect) {
        return Math.max(0.0F, stripContentWidth(strip) - rect.w());
    }

    private static float clampScroll(float value, float max) {
        return Math.max(0.0F, Math.min(value, max));
    }

    /** X of card {@code index} inside the row rect, scroll offset applied. */
    private static float themeCardX(UiLayout.Rect rect, int index, float scroll) {
        return stripCardX(rect, index, scroll, UiTokens.THEME_CARD_W);
    }

    private static float stripCardX(UiLayout.Rect rect, int index, float scroll, float width) {
        return rect.x() + UiTokens.SETTINGS_ROW_PAD
                + index * (width + UiTokens.THEME_CARD_GAP) - scroll;
    }

    /** Top edge of the preview cards inside the row rect. */
    private static float themeCardY(UiLayout.Rect rect) {
        return rect.y() + s(28);
    }

    /** The strip clip owns the edges; drawing fully hidden cards only burns frames. */
    private static boolean stripCardVisible(UiLayout.Rect rect,float x,float w) {
        float reach=com.atom.chat.render.CardShadowReach.PAD;
        return x+w+reach>=rect.x()&&x-reach<=rect.right();
    }

    /** Card index under the pointer, or -1. Mirrors the renderer geometry. */
    private int stripCardAt(CardStrip strip, UiLayout.Rect rect, float vmx, float vmy) {
        stripHitTests++;
        if (!rect.contains(vmx, vmy)) return -1;
        float scroll = strip.scroll;
        boolean logoCards = usesLogoCards(strip);
        float top = logoCards ? rect.y() + AboutCreditCards.TOP : themeCardY(rect);
        float height = logoCards ? AboutCreditCards.LOGO : UiTokens.THEME_CARD_H + s(18);
        if (vmy < top || vmy > top + height) return -1;
        float width = stripCardWidth(strip);
        int count = stripCardCount(strip);
        for (int i = 0; i < count; i++) {
            float x = stripCardX(rect, i, scroll, width);
            if (logoCards ? AboutCreditCards.logoRect(x, rect.y()).contains(vmx, vmy)
                    : vmx >= x && vmx <= x + width) {
                return i;
            }
        }
        return -1;
    }

    /** Custom card's remove key under the pointer, or -1. */
    private int themeDeleteAt(UiLayout.Rect rect, float vmx, float vmy, float scroll) {
        int card = stripCardAt(themeStrip, rect, vmx, vmy);
        int customStart = builtInThemeCount();
        int customEnd = customStart + AppearancePresetStore.get().list().size();
        if (card < customStart || card >= customEnd) {
            return -1;
        }
        float x = themeCardX(rect, card, scroll);
        float y = themeCardY(rect);
        float size = s(20);
        return vmx >= x + UiTokens.THEME_CARD_W - size - s(2) && vmx <= x + UiTokens.THEME_CARD_W - s(2)
                && vmy >= y + s(2) && vmy <= y + size + s(2) ? card : -1;
    }

    /** The themes row rect, for the screen's pointer routing (wheel/drag/click). */
    public UiLayout.Rect themesRowRect(UiLayout layout, SettingsSection section, float scrollY) {
        return stripRowRect(pointerStrip, layout, section, scrollY);
    }

    private UiLayout.Rect stripRowRect(CardStrip strip, UiLayout layout, SettingsSection section, float scrollY) {
        List<Row> rows = rows(section);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind() == strip.kind && rowExpansion(rows, i) >= GROUP_FOLD_MIN_DRAWN) {
                return rowRect(section, rows, i, scrollY, layout);
            }
        }
        return null;
    }

    /** Both preview strips use the same wheel and drag routing. */
    public boolean themesUnderPointer(float vmx, float vmy, UiLayout layout,
                                      SettingsSection section, float scrollY) {
        if (pointerStrip.pressed) return true;
        if (!layout.list.contains(vmx, vmy)) return false;
        for (CardStrip strip : previewStrips) {
            UiLayout.Rect rect = stripRowRect(strip, layout, section, scrollY);
            if (rect != null && rect.contains(vmx, vmy)) {
                pointerStrip = strip;
                strip.rect = rect;
                return true;
            }
        }
        return false;
    }

    public boolean themesPress(float vmx, float vmy, UiLayout layout,
                               SettingsSection section, float scrollY) {
        if (!themesUnderPointer(vmx, vmy, layout, section, scrollY)) return false;
        CardStrip strip = pointerStrip;
        strip.pressed = true;
        strip.dragged = false;
        strip.dragStartX = vmx;
        strip.dragStartY = vmy;
        strip.dragStartScroll = strip.scroll;
        strip.pressedCard = stripCardAt(strip, strip.rect, vmx, vmy);
        strip.pressedDeleteCard = strip.kind == RowKind.THEMES ? themeDeleteAt(strip.rect, vmx, vmy, strip.scroll) : -1;
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
        if (!pointerStrip.pressed || rect == null) {
            return;
        }
        float dx = vmx - pointerStrip.dragStartX;
        float dy = vmy - pointerStrip.dragStartY;
        if (!pointerStrip.dragged) {
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > UiTokens.s(8)) {
                if (Math.abs(dy) > Math.abs(dx)) {
                    // Vertical intent: hand the gesture back to the list.
                    pointerStrip.pressed = false;
                    pointerStrip.pressedCard = -1;
                    return;
                }
                // Past the slop this is a horizontal scroll, not a tap:
                // the armed card dies.
                pointerStrip.dragged = true;
                pointerStrip.pressedCard = -1;
            }
        }
        if (pointerStrip.dragged) {
            pointerStrip.scroll = pointerStrip.target = clampScroll(
                    pointerStrip.dragStartScroll - dx, stripMaxScroll(pointerStrip, rect));
            pointerStrip.animActive = false;
        }
    }

    public boolean isDraggingThemeStrip() {
        return pointerStrip.pressed;
    }

    /** Release: a press that never turned into a drag selects the card. */
    public void endThemeStrip(float mx, float my, UiLayout layout) {
        UiLayout.Rect rect = pointerStrip.rect;
        if (rect == null || !layout.list.contains(mx, my)
                || stripCardAt(pointerStrip, rect, mx, my) != pointerStrip.pressedCard
                || pointerStrip.kind == RowKind.THEMES
                && themeDeleteAt(rect, mx, my, pointerStrip.scroll) != pointerStrip.pressedDeleteCard) {
            pointerStrip.pressedCard = -1;
        }
        endThemeStrip();
    }

    void endThemeStrip() {
        int card = pointerStrip.pressedCard;
        boolean select = pointerStrip.pressed && !pointerStrip.dragged && card >= 0;
        UiLayout.Rect rect = pointerStrip.rect;
        int deleteCard = select ? pointerStrip.pressedDeleteCard : -1;
        pointerStrip.pressed = false;
        pointerStrip.pressedCard = -1;
        pointerStrip.pressedDeleteCard = -1;
        if (pointerStrip.kind == RowKind.QUALITY) {
            if (select) selectGraphicsQuality(GRAPHICS_QUALITIES[card]);
            return;
        }
        if (pointerStrip.kind == RowKind.CREDITS) {
            if (select) {
                openUri(SettingsCatalog.thirdPartyRows().get(card).uri());
            }
            return;
        }
        if (select && pointerStrip.kind == RowKind.WALLPAPERS) {
            var presets = com.atom.chat.wallpaper.WallpaperPresets.presets();
            if (actionHandler != null && card < presets.length)
                actionHandler.onAction("wallpaper_preset:" + presets[card].id());
            return;
        }
        if (select && pointerStrip.kind == RowKind.BUBBLES) {
            if (actionHandler != null && card < AtomChatConfig.BubbleStyle.values().length) {
                actionHandler.onAction(ACTION_BUBBLE_STYLE + AtomChatConfig.BubbleStyle.values()[card].name());
            }
            return;
        }
        if (deleteCard >= 0) {
            int customIndex = deleteCard - builtInThemeCount();
            List<AppearancePresetStore.Preset> presets = AppearancePresetStore.get().list();
            if (customIndex >= 0 && customIndex < presets.size() && actionHandler != null) {
                actionHandler.onAction(ACTION_THEME_PRESET_DELETE + presets.get(customIndex).id());
                pointerStrip.target = pointerStrip.scroll = clampScroll(pointerStrip.scroll,
                        stripMaxScroll(pointerStrip, pointerStrip.rect));
            }
        } else if (select && card >= 0) {
            int builtins = builtInThemeCount();
            int customEnd = builtins + AppearancePresetStore.get().list().size();
            if (card < builtins) {
            AtomChatConfig config = AtomChatConfig.get();
            ThemeService.apply(config, card == 0
                    ? ThemeService.FROSTED : ThemeService.presets()[card - 1].id());
            boolean saved=AtomChatConfig.saveChecked(config);
            com.atom.chat.diagnostics.FaultTrace.theme(config.themeName,saved);
            } else if (card >= builtins && card < customEnd && actionHandler != null) {
                actionHandler.onAction(ACTION_THEME_PRESET_APPLY
                        + AppearancePresetStore.get().list().get(card - builtins).id());
            } else if (actionHandler != null) {
                actionHandler.onAction(ACTION_THEME_PRESET_SAVE);
            }
        }
    }


    /** Called after a successful save: the new card and its trailing + are in view. */
    public void revealLatestPreset(UiLayout layout, SettingsSection section, float scrollY) {
        UiLayout.Rect rect = stripRowRect(themeStrip, layout, section, scrollY);
        if (rect != null) {
            themeStrip.target = themeStrip.scroll = stripMaxScroll(themeStrip, rect);
            themeStrip.animActive = false;
        }
    }

    public void requestRevealLatestPreset() {
        revealLatestPresetPending = true;
    }

    public boolean scrollCardStrip(float mx, float my, UiLayout layout, SettingsSection section,
                                   float scrollY, float horizontal, float vertical) {
        if (pointerStrip.pressed) return true;
        if (!themesUnderPointer(mx, my, layout, section, scrollY)) return false;
        boolean horizontalGesture = horizontal != 0F && Math.abs(horizontal) >= Math.abs(vertical);
        if (stripMaxScroll(pointerStrip, pointerStrip.rect) <= 0F) {
            return false;
        }
        wheelThemeStrip(horizontalGesture ? horizontal : vertical, pointerStrip.rect);
        return true;
    }

    /** Wheel over the strip: glide horizontally, one step per notch. */
    void wheelThemeStrip(float amount, UiLayout.Rect rect) {
        if (pointerStrip.pressed || rect == null) {
            return;
        }
        pointerStrip.target = clampScroll(pointerStrip.target - amount * UiTokens.s(60),
                stripMaxScroll(pointerStrip, rect));
        pointerStrip.animFrom = pointerStrip.scroll;
        pointerStrip.animStart = System.currentTimeMillis();
        pointerStrip.animActive = Math.abs(pointerStrip.target - pointerStrip.scroll) > 0.5F;
        if (!Animations.enabled()) advanceStrip(pointerStrip, rect);
    }

    /** Glides the offset toward its target; a drag owns the offset directly. */
    private void advanceStrip(CardStrip strip, UiLayout.Rect rect) {
        float max = stripMaxScroll(strip, rect);
        strip.target = clampScroll(strip.target, max);
        if (strip.pressed) {
            return;
        }
        if (!Animations.enabled()) {
            strip.scroll = strip.target;
            strip.animActive = false;
            return;
        }
        if (strip.animActive) {
            float t = Math.min(1.0F, (System.currentTimeMillis() - strip.animStart) / 180.0F);
            strip.scroll = clampScroll(strip.animFrom
                    + (strip.target - strip.animFrom) * Easing.easeOutCubic(t), max);
            if (t >= 1.0F) {
                strip.scroll = strip.target;
                strip.animActive = false;
            }
        } else {
            strip.scroll = strip.target;
        }
    }

    private void updatePreviewStrip(CardStrip strip, UiLayout.Rect rect) {
        advanceStrip(strip, rect);
        strip.rect = rect;
        if (strip == themeStrip && revealLatestPresetPending) {
            themeStrip.target = themeStrip.scroll = stripMaxScroll(themeStrip, rect);
            themeStrip.animActive = false;
            revealLatestPresetPending = false;
        }
    }

    private void updateHiddenPreviewStrips(SettingsSection section, List<Row> rows, int first, int end,
                                           UiLayout.Rect card, UiLayout layout, float scrollY) {
        if (card.bottom() < layout.list.y() || card.y() > layout.list.bottom()
                || rowExpansion(rows, first) < GROUP_FOLD_MIN_DRAWN) return;
        for (int i = first; i < end; i++) {
            CardStrip strip = switch (rows.get(i).kind()) {
                case THEMES -> themeStrip;
                case BUBBLES -> bubbleStrip;
                case WALLPAPERS -> wallpaperStrip;
                case CREDITS -> creditsStrip;
                case QUALITY -> qualityStrip;
                default -> null;
            };
            if (strip != null) updatePreviewStrip(strip, rowRect(section, rows, i, scrollY, layout));
        }
    }

    /**
     * Theme strip: title + current-preset name on the caption line, then the
     * horizontally scrollable preview cards - Default (drawn with the factory
     * palette), then the eight colour presets. Every card is a pure-code mini
     * panel in its theme's own colours; the selected card wears an accent
     * hairline and the whole strip scrolls by wheel or drag.
     */
    private void drawWallpapers(Canvas canvas, UiLayout.Rect row, float dtMs) {
        updatePreviewStrip(wallpaperStrip, row);
        wallpaperStrip.hoveredCard = stripCardAt(wallpaperStrip, row, pointerX, pointerY);
        Font title = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font value = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        SkiaFontRenderer.drawText(canvas, title, tr("atomchat.settings.wallpaper.current"),
                row.x() + UiTokens.SETTINGS_ROW_PAD,
                SkiaFontRenderer.centerBaselineY(title, row.y() + WallpaperTokens.CAPTION_Y), textPrimary());
        String selected = com.atom.chat.wallpaper.WallpaperPresets.selectedId();
        String status = com.atom.chat.wallpaper.WallpaperPresets.error();
        if (com.atom.chat.wallpaper.WallpaperPresets.loading()) status = "atomchat.settings.wallpaper.generating";
        var cfg = AtomChatConfig.get();
        if (status.isEmpty()) status = selected.isEmpty()
                ? WallpaperStore.isSet() ? "atomchat.settings.wallpaper.custom" : "atomchat.settings.wallpaper.none"
                : "atomchat.settings.wallpaper." + selected;
        String statusText = tr(status);
        SkiaFontRenderer.drawTextRight(canvas, value, statusText,
                row.right() - UiTokens.SETTINGS_ROW_PAD, row.y() + WallpaperTokens.CAPTION_Y, sec(255));
        canvas.save();
        try {
            CornerGeometry.clip(canvas, row.x(), themeCardY(row) - WallpaperTokens.CLIP_PAD, row.w(),
                    UiTokens.THEME_CARD_H + WallpaperTokens.CLIP_EXTRA, UiTokens.radius(6, AtomChatAppearance.theme()));
            var presets = com.atom.chat.wallpaper.WallpaperPresets.presets();
            for (int i = 0; i < presets.length; i++) {
                var preset = presets[i];
                var preview = new ThemeService.Preview(cfg.accentColor, preset.previewColor(),
                        cfg.cardColor, cfg.ownBubbleColor, cfg.otherBubbleColor, UiTokens.radiusFactor(cfg.cornerRadius));
                drawPreviewCard(canvas, wallpaperStrip, row, i, preview,
                        tr("atomchat.settings.wallpaper." + preset.id()), preset.id().equals(selected),
                        false, cfg.effectiveBubbleStyle(), dtMs,
                        com.atom.chat.wallpaper.WallpaperPresets.preview(preset.id()), true);
            }
        } finally { canvas.restore(); }
    }

    private void drawBubblePreviews(Canvas canvas, UiLayout.Rect row, float dtMs) {
        updatePreviewStrip(bubbleStrip, row);
        bubbleStrip.hoveredCard = stripCardAt(bubbleStrip, row, pointerX, pointerY);
        AtomChatConfig cfg = AtomChatConfig.get();
        var preview = previewOf(AppearancePresetStore.Snapshot.capture(cfg));
        Font title = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font value = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        SkiaFontRenderer.drawText(canvas, title, tr("atomchat.settings.appearance.bubble_style"),
                row.x() + UiTokens.SETTINGS_ROW_PAD,
                SkiaFontRenderer.centerBaselineY(title, row.y() + s(18)), textPrimary());
        String addonLabel = AppearanceSettings.label(Part.BUBBLE, cfg.bubbleAppearanceId);
        boolean addonSelected = !com.atom.chat.onboarding.TutorialSandbox.active() && cfg.bubbleAppearanceId != null
                && !AppearanceSettings.MISSING.equals(addonLabel);
        String current = addonSelected ? tr(addonLabel) : bubbleStyleName(cfg.effectiveBubbleStyle());
        float captionWidth = SkiaFontRenderer.getStringWidth(title, tr("atomchat.settings.appearance.bubble_style"))
                + SkiaFontRenderer.getStringWidth(value, current) + UiTokens.SETTINGS_COMPACT_GAP;
        if (captionWidth <= row.w() - UiTokens.SETTINGS_ROW_PAD * 2F) {
            SkiaFontRenderer.drawTextRight(canvas, value, current,
                    row.right() - UiTokens.SETTINGS_ROW_PAD, row.y() + s(18), sec(255));
        }
        canvas.save();
        try {
            CornerGeometry.clip(canvas, row.x(), themeCardY(row) - s(6), row.w(),
                    UiTokens.THEME_CARD_H + s(30), UiTokens.radius(6, AtomChatAppearance.theme()));
            var styles = AtomChatConfig.BubbleStyle.values();
            for (int i = 0; i < styles.length; i++) {
                drawPreviewCard(canvas, bubbleStrip, row, i, preview, bubbleStyleName(styles[i]),
                        !addonSelected && styles[i] == cfg.effectiveBubbleStyle(), false, styles[i], dtMs);
            }
        } finally { canvas.restore(); }
    }

    private static String bubbleStyleName(AtomChatConfig.BubbleStyle style) {
        return tr("atomchat.settings.appearance.bubble_style." + style.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void drawThemes(Canvas canvas, UiLayout.Rect rect, float dtMs) {
        updatePreviewStrip(themeStrip, rect);
        themeStrip.hoveredCard = stripCardAt(themeStrip, rect, pointerX, pointerY);
        drawThemePreviews(canvas, themeStrip, rect, dtMs);
        if (appearanceMeasurement != null && appearanceMeasurement.width == rect.w()) {
            appearanceMeasurement.themePreviewsPending = false;
        }
    }

    private void warmThemePreviews(float width) {
        float height = rowHeight(RowKind.THEMES);
        // Recording initializes the same fonts and paint paths without replaying on the GPU.
        try (PictureRecorder recorder = new PictureRecorder()) {
            Canvas canvas = recorder.beginRecording(Rect.makeWH(width, height));
            drawThemePreviews(canvas, new CardStrip(RowKind.THEMES), new UiLayout.Rect(0, 0, width, height), 0F);
            recorder.finishRecordingAsPicture().close();
        }
    }

    private PressScale logoMotion(CardStrip strip, int index, float dtMs) {
        var motion = strip.scales.computeIfAbsent(index, unused -> PressScale.bounce());
        motion.update(strip.hoveredCard == index, strip.pressed && strip.pressedCard == index, dtMs,
                AtomChatConfig.get().animationEnabled, AboutCreditCards.LOGO);
        return motion;
    }

    private void drawCredits(Canvas canvas, UiLayout.Rect rect, float dtMs) {
        updatePreviewStrip(creditsStrip, rect);
        creditsStrip.hoveredCard = stripCardAt(creditsStrip, rect, pointerX, pointerY);
        var credits = SettingsCatalog.thirdPartyRows();
        int secondary = sec(220);
        int save = canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(rect.x(), rect.y(), rect.w(), rect.h()));
            for (int i = 0; i < credits.size(); i++) {
                float x = stripCardX(rect, i, creditsStrip.scroll, AboutCreditCards.WIDTH);
                if (x >= rect.right() || x + AboutCreditCards.WIDTH <= rect.x()) continue;
                var credit = credits.get(i);
                AboutCreditCards.draw(canvas, credit.titleKey(), credit.value(), x, rect.y(), secondary,
                        logoMotion(creditsStrip, i, dtMs));
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void drawGraphicsQuality(Canvas canvas, UiLayout.Rect rect, float dtMs, int accent) {
        updatePreviewStrip(qualityStrip, rect);
        qualityStrip.hoveredCard = stripCardAt(qualityStrip, rect, pointerX, pointerY);
        GraphicsQuality active = AtomChatConfig.get().graphicsQuality;
        int save = canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(rect.x(), rect.y(), rect.w(), rect.h()));
            for (int i = 0; i < GRAPHICS_QUALITIES.length; i++) {
                float x = stripCardX(rect, i, qualityStrip.scroll, AboutCreditCards.WIDTH);
                if (x >= rect.right() || x + AboutCreditCards.WIDTH <= rect.x()) continue;
                GraphicsQuality quality = GRAPHICS_QUALITIES[i];
                AboutCreditCards.drawQuality(canvas, quality, tr(QUALITY_TITLE + "." + quality.id()), x, rect.y(),
                        active == quality ? accent : sec(220), logoMotion(qualityStrip, i, dtMs),
                        active == quality ? accent : 0);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static void selectGraphicsQuality(GraphicsQuality quality) {
        var config = AtomChatConfig.get();
        GraphicsQuality previous = config.graphicsQuality;
        if (previous == quality) return;
        config.graphicsQuality = quality;
        if (!AtomChatConfig.saveChecked(config)) config.graphicsQuality = previous;
    }

    private void drawThemePreviews(Canvas canvas, CardStrip strip, UiLayout.Rect rect, float dtMs) {
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        AtomChatConfig config = AtomChatConfig.get();
        String currentId = config.themeName;
        if (currentId == null) {
            currentId = "";
        }

        SkiaFontRenderer.drawText(canvas, titleFont,
                tr("atomchat.settings.appearance.theme"),
                rect.x() + UiTokens.SETTINGS_ROW_PAD,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(18)),
                textPrimary());
        String currentName;
        List<AppearancePresetStore.Preset> custom = AppearancePresetStore.get().list();
        AppearancePresetStore.Preset matchingCustom = AppearancePresetStore.selected(custom, config);
        if (currentId.isEmpty() || ThemeService.FROSTED.equals(currentId)) {
            currentName = tr("atomchat.settings.theme.frosted");
        } else if (ThemeService.byId(currentId) != null) {
            currentName = tr("atomchat.settings.theme." + currentId);
        } else if (ThemeService.MODERN.equals(currentId)) {
            currentName = tr("atomchat.settings.theme.modern");
        } else {
            currentName = tr("atomchat.settings.theme.custom");
        }
        if (matchingCustom != null) {
            currentName = presetName(matchingCustom);
        }
        SkiaFontRenderer.drawTextRight(canvas, valueFont, currentName,
                rect.right() - UiTokens.SETTINGS_ROW_PAD, rect.y() + s(18),
                sec(255));

        canvas.save();
        try {
            CornerGeometry.clip(canvas, rect.x(), themeCardY(rect) - s(6), rect.w(),
                    UiTokens.THEME_CARD_H + s(30), UiTokens.radius(6, AtomChatAppearance.theme()));
            boolean frostedActive = matchingCustom == null
                    && (currentId.isEmpty() || ThemeService.FROSTED.equals(currentId));
            AtomChatConfig.BubbleStyle bubbleStyle = config.effectiveBubbleStyle();
            drawPreviewCard(canvas, strip, rect, 0, ThemeService.previewOf(ThemeService.FROSTED),
                    tr("atomchat.settings.theme.frosted"), frostedActive, false, bubbleStyle, dtMs);
            ThemeService.Preset[] presets = ThemeService.presets();
            for (int i = 0; i < presets.length; i++) {
                drawPreviewCard(canvas, strip, rect, i + 1, ThemeService.previewOf(presets[i].id()),
                        tr("atomchat.settings.theme." + presets[i].id()),
                        matchingCustom == null && presets[i].id().equals(currentId), false, bubbleStyle, dtMs);
            }
            int customStart = presets.length + 1;
            for (int i = 0; i < custom.size(); i++) {
                AppearancePresetStore.Preset preset = custom.get(i);
                drawPreviewCard(canvas, strip, rect, customStart + i,
                        previewOf(preset.snapshot()), presetName(preset), preset == matchingCustom,
                        strip == themeStrip, AtomChatConfig.BubbleStyle.valueOf(preset.snapshot().bubbleStyle()), dtMs);
            }
            drawAddThemeCard(canvas, strip, rect, customStart + custom.size(), dtMs);
        } finally {
            canvas.restore();
        }
    }

    private static void drawThemeBubble(Canvas canvas, float x, float y, float w, float h,
                                        AtomChatConfig.BubbleStyle style, boolean own, int color) {
        canvas.save();
        try {
            canvas.translate(x, y);
            float scale = h / UiTokens.BUBBLE_PREVIEW_BUBBLE_H;
            canvas.scale(scale, scale);
            com.atom.chat.render.ChatBubble.draw(canvas, 0F, 0F, w / scale,
                    UiTokens.BUBBLE_PREVIEW_BUBBLE_H, style, own, color);
        } finally { canvas.restore(); }
    }

    private static String presetName(AppearancePresetStore.Preset preset) {
        return tr("atomchat.settings.appearance.presets.number", preset.number());
    }

    private static ThemeService.Preview previewOf(AppearancePresetStore.Snapshot s) {
        return new ThemeService.Preview(s.accentColor(), s.panelBgColor(), s.cardColor(),
                s.ownBubbleColor(), s.otherBubbleColor(), UiTokens.radiusFactor(s.cornerRadius()));
    }

    /**
     * One mini-panel preview card: the theme's panel ground, a title-bar strip
     * carrying the accent dot, the other player's bubble on the left, the
     * player's own bubble on the right and a faint composer strip below - all
     * pure Skija primitives, no assets, at the theme's native corner scale.
     */
    private void drawPreviewCard(Canvas canvas, CardStrip strip, UiLayout.Rect rect, int index,
                                 ThemeService.Preview preview, String name,
                                 boolean selected, boolean deletable, AtomChatConfig.BubbleStyle bubbleStyle, float dtMs) {
        drawPreviewCard(canvas, strip, rect, index, preview, name, selected, deletable, bubbleStyle, dtMs, null, false);
    }

    private void drawPreviewCard(Canvas canvas, CardStrip strip, UiLayout.Rect rect, int index,
                                 ThemeService.Preview preview, String name,
                                 boolean selected, boolean deletable, AtomChatConfig.BubbleStyle bubbleStyle, float dtMs, io.github.humbleui.skija.Image image, boolean wallpaper) {
        float x = themeCardX(rect, index, strip.scroll);
        float y = themeCardY(rect);
        float w = UiTokens.THEME_CARD_W;
        float h = UiTokens.THEME_CARD_H;
        float radius = UiTokens.radius(10, AtomChatAppearance.theme());
        if(!stripCardVisible(rect,x,w))return;

        // The cards are separated by THEME_CARD_GAP and packed against the
        // strip's own clip; the width budget keeps the gap and the rounded
        // edges visible. The scaled shape is the card itself (w).
        PressScale press = strip.scales.computeIfAbsent(index, k -> PressScale.bounce());
        press.update(strip.hoveredCard == index,
                strip.pressed && strip.pressedCard == index, dtMs, Animations.enabled(), w);
        press.begin(canvas, x + w / 2.0F, y + h / 2.0F);
        try {
            SettingsPreviewCard.drawSurface(canvas, x, y, w, h, radius, preview.panelBg());
            if (wallpaper) {
                if (image != null) {
                    canvas.save();
                    try {
                        CornerGeometry.clip(canvas, x, y, w, h, radius);
                        canvas.drawImageRect(image, io.github.humbleui.types.Rect.makeXYWH(x, y, w, h));
                    } finally { canvas.restore(); }
                }
            } else {
            // Title bar strip + its accent dot.
            float barH = s(12);
            float barY = y + s(7);
            CornerGeometry.drawRoundedRect(canvas, x + s(7), barY, w - s(14), barH,
                    s(4) * preview.cornerFactor(), preview.card());

            // The other player's bubble on the left, the player's own on the right.
            float bubbleH = s(16);
            drawThemeBubble(canvas, x + s(7), y + s(27), w - s(30), bubbleH, bubbleStyle, false, preview.otherBubble());
            drawThemeBubble(canvas, x + s(23), y + s(50), w - s(30), bubbleH, bubbleStyle, true, preview.ownBubble());
            // A faint composer strip balances the lower half of the card.
            // Mirrors the real composer's surface construction (cardFill():
            // card colour at the card-tint alpha over the panel) but derives
            // from THIS preview's own card colour — the global cardCutout()
            // reads the live theme's config and recoloured every tile's strip
            // whenever a dark theme was active. Over the card's own panelBg
            // ground the composite equals the cardCutout weight of this card.
            float composerTint = Math.max(0.0F, Math.min(1.0F,
                    AtomChatConfig.get().cardTint));
            CornerGeometry.drawRoundedRect(canvas, x + s(7), y + h - s(19), w - s(14), s(12),
                    s(4) * preview.cornerFactor(),
                    UiTokens.withAlpha(preview.card(), 255.0F * composerTint));
            }
            float dotR = WallpaperTokens.DOT_RADIUS;
            float dotY = y + WallpaperTokens.DOT_Y;
            CornerGeometry.drawRoundedRect(canvas, x + w - WallpaperTokens.DOT_RIGHT - dotR, dotY - dotR,
                    dotR * 2, dotR * 2, dotR, preview.accent());
            if (selected) {
                CornerGeometry.drawEdgeHighlight(canvas, x, y, w, h, radius, s(1.5F), preview.accent());
            }
            if (deletable) {
                int hoveredCard = strip.hoveredCard;
                boolean hover = hoveredCard == index;
                float fade = UiMotion.approach(themeDeleteHover.getOrDefault(index, 0.0F),
                        hover ? 1.0F : 0.0F, dtMs, UiMotion.HOVER_MS);
                themeDeleteHover.put(index, fade);
                if (fade > 0.01F) {
                    float rs = s(18);
                    CornerGeometry.drawRoundedRect(canvas, x + w - rs - s(2), y + s(2), rs, rs,
                            UiTokens.radius(4, AtomChatAppearance.theme()), Color.makeARGB((int) (200.0F * fade), 214, 48, 48));
                    Font xFont = FontManager.font(UiTokens.FONT_QUOTE);
                    SkiaFontRenderer.drawTextCentered(canvas, xFont, "×",
                            x + w - rs / 2.0F - s(2), y + s(2) + rs / 2.0F,
                            Color.makeARGB((int) (255.0F * fade), 255, 255, 255));
                }
            }
        } finally {
            canvas.restore();
        }
        Font nameFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        SkiaFontRenderer.drawTextCentered(canvas, nameFont,
                SkiaFontRenderer.truncate(nameFont, name, w + s(8)),
                x + w / 2.0F, SettingsPreviewCard.captionY(y, h),
                selected ? textPrimary() : sec(220));
    }

    private void drawAddThemeCard(Canvas canvas, CardStrip strip, UiLayout.Rect rect, int index, float dtMs) {
        float x = themeCardX(rect, index, strip.scroll);
        float y = themeCardY(rect);
        float w = UiTokens.THEME_CARD_W;
        float h = UiTokens.THEME_CARD_H;
        if(!stripCardVisible(rect,x,w))return;
        PressScale press = strip.scales.computeIfAbsent(index, k -> PressScale.bounce());
        press.update(strip.hoveredCard == index,
                strip.pressed && strip.pressedCard == index, dtMs, Animations.enabled(), w);
        press.begin(canvas, x + w / 2.0F, y + h / 2.0F);
        try {
            CornerGeometry.drawRoundedRect(canvas, x, y, w, h, UiTokens.radius(10, AtomChatAppearance.theme()),
                    UiTokens.trackRest(AtomChatAppearance.theme()));
            drawIconCentered(canvas, TablerIcons.PLUS, x + w / 2.0F, y + h / 2.0F,
                    s(18), textPrimary());
        } finally {
            canvas.restore();
        }
        Font nameFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        SkiaFontRenderer.drawTextCentered(canvas, nameFont,
                tr("atomchat.settings.appearance.presets.add"), x + w / 2.0F, y + h + s(11), sec(220));
    }

    private void drawColor(Canvas canvas, Row row, UiLayout.Rect rect, float dtMs) {
        SettingsColor color = row.color();
        Font font = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        var lines = colorTitleLines(color, font, rect.w());
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float centerY = rect.y() + rect.h() / 2F;
        float lineCenter = centerY - (lines.size() - 1) * lineHeight / 2F;
        for (String line : lines) {
            SkiaFontRenderer.drawText(canvas, font, line, rect.x() + UiTokens.SETTINGS_ROW_PAD,
                    SkiaFontRenderer.centerBaselineY(font, lineCenter), textPrimary());
            lineCenter += lineHeight;
        }

        var control = colorControl(rect);
        float centerX = control.x() + control.w() / 2F;
        float edge = s(24);
        float left = centerX - edge / 2F;
        float top = centerY - edge / 2F;
        var scale = colorPress.computeIfAbsent(color.id(), id -> PressScale.bounce());
        scale.update(colorRowHovered, colorRowHovered && drawnRowIndex == pressedRow,
                dtMs, Animations.enabled(), edge);
        scale.begin(canvas, centerX, centerY);
        try {
            var theme = AtomChatAppearance.theme();
            float radius = UiTokens.radius(4, theme);
            CornerGeometry.drawRoundedRect(canvas, left - s(1), top - s(1), edge + s(2), edge + s(2),
                    radius + s(1), UiTokens.rim(theme));
            CornerGeometry.drawRoundedRect(canvas, left, top, edge, edge, radius, color.value());
            if (colorRowHovered) {
                CornerGeometry.drawRoundedRect(canvas, left, top, edge, edge, radius, UiTokens.cardHover(.30F, theme));
            }
        } finally {
            canvas.restore();
        }
    }

    private static void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon,
                                         float cx, float cy, float size, int color) {
        io.github.humbleui.types.Rect b = com.atom.chat.render.OutlineIconBounds.of(icon);
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

    /** Application identity: icon, flat red wordmark and version, without a hero card. */
    private void drawHero(Canvas canvas, UiLayout.Rect rect) {
        float available = rect.w() - 2.0F * UiTokens.SETTINGS_HERO_PAD;
        if (available <= 0.0F) {
            return;
        }
        float iconSize = Math.min(UiTokens.SETTINGS_HERO_ICON_SIZE, available);
        float top = heroBrandTop(rect);
        float iconX = rect.x() + (rect.w() - iconSize) / 2.0F;
        float haloPad = UiTokens.DOT_HERO_PAD;
        float haloX = Math.max(rect.x(), iconX - haloPad);
        float haloRight = Math.min(rect.right(), iconX + iconSize + haloPad);
        com.atom.chat.render.DotTexture.drawHalo(canvas,
                new UiLayout.Rect(haloX, Math.max(rect.y(), top - haloPad), haloRight - haloX,
                        Math.min(rect.bottom(), top + iconSize + haloPad) - Math.max(rect.y(), top - haloPad)),
                AtomChatConfig.get().textSecondaryColor);
        Image icon = heroAppIcon();
        if (icon != null) {
            com.atom.chat.render.SoftShadow.drawRounded(canvas, iconX, top, iconSize, iconSize,
                    UiTokens.SETTINGS_HERO_ICON_RADIUS, UiTokens.SETTINGS_HERO_ICON_SHADOW,
                    UiTokens.CARD_SHADOW);
            SkiaImages.drawRoundedImage(canvas, icon, iconX, top, iconSize, iconSize,
                    UiTokens.SETTINGS_HERO_ICON_RADIUS, SamplingMode.LINEAR);
        }
        float markY = top + iconSize + UiTokens.SETTINGS_HERO_GAP;
        Image mark = heroWordmark();
        if (mark != null) {
            UiLayout.Rect box = heroWordmarkRect(rect, mark.getWidth(), mark.getHeight());
            if (box.w() > 0.0F && box.h() > 0.0F) {
                try (ColorFilter tint = ColorFilter.makeBlend(UiTokens.SETTINGS_HERO_BRAND_COLOR,
                        BlendMode.SRC_IN);
                     Paint paint = new Paint().setAntiAlias(true).setColorFilter(tint)) {
                    // The wordmark is a large PNG drawn into a small box.
                    Image fitted = com.atom.chat.render.ImageQuality.scaledForLogical(
                            mark, Math.max(box.w(), box.h()));
                    canvas.drawImageRect(fitted,
                            Rect.makeXYWH(0, 0, fitted.getWidth(), fitted.getHeight()),
                            Rect.makeXYWH(box.x(), box.y(), box.w(), box.h()),
                            SamplingMode.LINEAR, paint, false);
                }
            }
        } else {
            Font font = FontManager.font(UiTokens.SETTINGS_HERO_FONT);
            SkiaFontRenderer.drawTextCentered(canvas, font, tr("atomchat.screen.title"),
                    rect.x() + rect.w() / 2.0F,
                    markY + UiTokens.SETTINGS_HERO_WORDMARK_MAX_H / 2.0F, textPrimary());
        }
        Font versionFont = FontManager.font(UiTokens.SETTINGS_HERO_VERSION_FONT);
        String version = AtomChat.version();
        float versionW = Math.min(available, SkiaFontRenderer.getStringWidth(versionFont, version)
                + 2.0F * UiTokens.SETTINGS_HERO_VERSION_PAD);
        float versionX = rect.x() + (rect.w() - versionW) / 2.0F;
        float versionY = markY + UiTokens.SETTINGS_HERO_WORDMARK_MAX_H + UiTokens.SETTINGS_HERO_GAP;
        CornerGeometry.drawRoundedRect(canvas, versionX, versionY, versionW,
                UiTokens.SETTINGS_HERO_VERSION_H,
                UiTokens.radius(UiTokens.SETTINGS_HERO_VERSION_RADIUS / UiTokens.SCALE, AtomChatAppearance.theme()), sec(12));
        SkiaFontRenderer.drawTextCentered(canvas, versionFont, version,
                rect.x() + rect.w() / 2.0F,
                versionY + UiTokens.SETTINGS_HERO_VERSION_H / 2.0F, sec(255));
    }

    private static float heroBrandTop(UiLayout.Rect hero) {
        float iconSize = Math.min(UiTokens.SETTINGS_HERO_ICON_SIZE,
                Math.max(0.0F, hero.w() - 2.0F * UiTokens.SETTINGS_HERO_PAD));
        float groupH = iconSize + 2.0F * UiTokens.SETTINGS_HERO_GAP
                + UiTokens.SETTINGS_HERO_WORDMARK_MAX_H + UiTokens.SETTINGS_HERO_VERSION_H;
        return hero.y() + Math.max(0.0F, (hero.h() - groupH) / 2.0F);
    }

    /** Contain the wordmark in the middle slot of the application-identity stack. */
    static UiLayout.Rect heroWordmarkRect(UiLayout.Rect hero, float markW, float markH) {
        float availW = hero.w() - 2.0F * UiTokens.SETTINGS_HERO_PAD;
        if (markW <= 0.0F || markH <= 0.0F || availW <= 0.0F
                || hero.h() <= 2.0F * UiTokens.SETTINGS_HERO_PAD) {
            return new UiLayout.Rect(hero.x(), hero.y(), 0.0F, 0.0F);
        }
        float scale = Math.min(availW / markW,
                Math.min(UiTokens.SETTINGS_HERO_WORDMARK_MAX_W / markW,
                        UiTokens.SETTINGS_HERO_WORDMARK_MAX_H / markH));
        float w = markW * scale;
        float h = markH * scale;
        float iconSize = Math.min(UiTokens.SETTINGS_HERO_ICON_SIZE, availW);
        return new UiLayout.Rect(hero.x() + (hero.w() - w) / 2.0F,
                heroBrandTop(hero) + iconSize + UiTokens.SETTINGS_HERO_GAP
                        + (UiTokens.SETTINGS_HERO_WORDMARK_MAX_H - h) / 2.0F, w, h);
    }

    private static final String[] AUTHOR_NAMES = { "E33EPUS", "NoWordz" };
    private static final Image[] AUTHOR_IMAGES = new Image[AUTHOR_NAMES.length];
    private static final boolean[] AUTHOR_IMAGES_LOADED = new boolean[AUTHOR_NAMES.length];

    private static Image authorImage(int index) {
        if (!AUTHOR_IMAGES_LOADED[index]) {
            AUTHOR_IMAGES_LOADED[index] = true;
            AUTHOR_IMAGES[index] = loadBundled("/assets/atomchat/authors/"
                    + (index == 0 ? "e33epus.png" : "nowordz.png"));
        }
        return AUTHOR_IMAGES[index];
    }

    private final PressScale[] authorScale = {PressScale.bounce(), PressScale.bounce()};
    private final float[] authorHover = new float[2];

    private void drawAuthors(Canvas canvas, UiLayout.Rect rect, float dt) {
        float gap = UiTokens.SETTINGS_TILE_GAP;
        float width = Math.max(0.0F, (rect.w() - gap) / 2.0F);
        float avatarSize = Math.min(s(40), Math.max(0.0F, width - s(20)));
        Font font = FontManager.boldFont(UiTokens.SETTINGS_TILE_SUB);
        float textHeight = SkiaFontRenderer.textHeight(font);
        float innerGap = s(10);
        float groupHeight = avatarSize + innerGap + textHeight;
        for (int i = 0; i < AUTHOR_NAMES.length; i++) {
            float x = rect.x() + i * (width + gap);
            float cx = x + width / 2.0F;
            float top = rect.y() + (rect.h() - groupHeight) / 2.0F;
            boolean over = pointerX >= x && pointerX <= x + width
                    && pointerY >= rect.y() && pointerY <= rect.bottom();
            authorHover[i] = UiMotion.approach(authorHover[i], over ? 1F : 0F, dt, UiMotion.HOVER_MS);
            authorScale[i].update(over, false, dt, Animations.enabled(), width);
            authorScale[i].begin(canvas, cx, rect.y() + rect.h() / 2F);
            try {
            AtomChatAppearance.drawCard(canvas, x, rect.y(), width, rect.h(),
                    UiTokens.settingsTileRadius(AtomChatAppearance.theme()), authorHover[i]);
            Image image = authorImage(i);
            if (image != null) {
                float scale = Math.min(avatarSize / image.getWidth(), avatarSize / image.getHeight());
                float imageW = image.getWidth() * scale;
                float imageH = image.getHeight() * scale;
                SkiaImages.drawRoundedImage(canvas, image, cx - imageW / 2.0F,
                        top + (avatarSize - imageH) / 2.0F,
                        imageW, imageH, 0.0F, SamplingMode.DEFAULT);
            }
            String name = SkiaFontRenderer.truncate(font, AUTHOR_NAMES[i],
                    Math.max(0.0F, width - s(16)));
            SkiaFontRenderer.drawTextCentered(canvas, font, name, cx,
                    top + avatarSize + innerGap + textHeight / 2.0F, textPrimary());
            } finally { canvas.restore(); }
        }
    }

    private static Image heroAppIcon;

    private static Image heroAppIcon() {
        if (heroAppIcon == null) {
            heroAppIcon = loadBundled("/assets/atomchat/mod_logo.png");
        }
        return heroAppIcon;
    }

    private static Image heroWordmark;

    /**
     * The bundled red {@code AtomChat} wordmark with its white ground keyed out
     * to alpha, decoded once. The square mark uses the same resource as the
     * loader's mod icon. A null return is the caller's cue to fall back to text.
     */
    private static Image heroWordmark() {
        if (heroWordmark != null) {
            return heroWordmark;
        }
        heroWordmark = loadBundled("/assets/atomchat/logo_wordmark.png");
        return heroWordmark;
    }

    /** Decodes a bundled PNG, or null when it is missing or unreadable. */
    private static Image loadBundled(String path) {
        try (var stream = SettingsSectionPage.class.getResourceAsStream(path)) {
            if (stream != null) {
                return Image.makeFromEncoded(stream.readAllBytes());
            }
        } catch (Exception ignored) {
            // Missing art is a packaging mistake, not a crash: the caller
            // falls back to the product name as text.
        }
        return null;
    }

    /**
     * Draws a settings description below the title. Fits on one line it keeps
     * the existing baseline; otherwise it wraps and sits bottom-aligned inside
     * the (already grown) row so long English text is never lost to an ellipsis.
     */
    private void drawWrappedDescription(Canvas canvas,UiLayout.Rect rect,Font font,String text,float x,float maxWidth,int color){
        SettingsRowText.description(canvas,rect,font,text,x,maxWidth,color);
    }

    /** One geometry consumed by both selector rendering and pointer resolution. */
    SettingsStepper.Geometry stepperGeometry(Row row, UiLayout.Rect rect) {
        Font font = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        return SettingsStepper.layout(rect, SkiaFontRenderer.getStringWidth(font, actionCopy(row).verb()),
                compactText(row, rect.w()) != null);
    }

    private void drawStepper(Canvas canvas, Row row, UiLayout.Rect rect, ActionCopy copy) {
        var geometry = stepperGeometry(row, rect);
        Font font = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(rect.x(), rect.y(), rect.w(), rect.h()));
            for (int direction : new int[]{-1, 1}) {
                UiLayout.Rect arrow = direction < 0 ? geometry.previous() : geometry.next();
                float hover = stepperHover.weight(row.actionId(), direction);
                if (hover > 0.0F) CornerGeometry.drawRoundedRect(canvas, arrow.x(), arrow.y(), arrow.w(), arrow.h(),
                        UiTokens.settingsRowRadius(AtomChatAppearance.theme()), UiTokens.cardHover(hover, AtomChatAppearance.theme()));
                float size = Math.min(UiTokens.SETTINGS_STEPPER_CHEVRON, arrow.w() / 4);
                drawChevron(canvas, arrow.x() + arrow.w() / 2, arrow.y() + arrow.h() / 2,
                        size, direction < 0 ? 2 : 0, textPrimary());
            }
            UiLayout.Rect value = geometry.value();
            SkiaFontRenderer.drawTextCentered(canvas, font,
                    SkiaFontRenderer.truncate(font, copy.verb(), value.w()),
                    value.x() + value.w() / 2, value.y() + value.h() / 2, textPrimary());
        } finally { canvas.restore(); }
    }

    /**
     * Resolves the one arrow under the pointer with the exact geometry used by
     * painting and hit testing; every other action/direction falls back to 0.
     */
    private void updateStepperHover(SettingsSection section, List<Row> rows, UiLayout layout,
                                    float vmx, float vmy, float scrollY, float barH, float dt) {
        SettingsStepper.ArrowHover active = null;
        if (layout.list.contains(vmx, vmy) && vmy >= layout.list.y() + barH) {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row.kind() != RowKind.ACTION || !SettingsStepper.supports(row.actionId())
                        || !row.item().available()) {
                    continue;
                }
                UiLayout.Rect rect = rowRect(section, rows, i, scrollY, layout);
                if (rect.bottom() < layout.list.y() || rect.y() > layout.list.bottom()
                        || rowExpansion(rows, i) < GROUP_FOLD_MIN_DRAWN) {
                    continue;
                }
                int direction = stepperGeometry(row, rect).directionAt(vmx, vmy);
                if (direction != 0) {
                    active = new SettingsStepper.ArrowHover(row.actionId(), direction);
                    break;
                }
            }
        }
        stepperHover.advance(active, dt, Animations.enabled());
    }

    /** Plain action card: title + description + a right-aligned verb or selector. */
    private void drawAction(Canvas canvas, Row row, UiLayout.Rect rect, float hover) {
        ActionCopy copy = actionCopy(row);
        if (ACTION_HISTORY_CLEAR.equals(row.actionId())) {
            var label = new HistoryLabelRow.Label(copy.title(), copy.subtitle(), copy.verb(), copy.available(), false);
            label.paint(canvas, rect, compactFlow(compactText(row, rect.w())), textPrimary(), sec(255));
            return;
        }
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        boolean guide = "onboarding_replay".equals(row.actionId());
        if (guide) {
            com.atom.chat.render.DrawPrimitives.drawIconCentered(canvas, com.atom.chat.onboarding.GuideIcon.PATH,
                    textX + UiTokens.COMPOSER_ICON_SIZE / 2, rect.y() + rect.h() / 2,
                    UiTokens.COMPOSER_ICON_SIZE, textPrimary());
            textX += UiTokens.COMPOSER_ICON_SIZE + UiTokens.SETTINGS_TILE_TEXT_GAP;
        }
        float guideInset = guide ? UiTokens.COMPOSER_ICON_SIZE + UiTokens.SETTINGS_TILE_TEXT_GAP : 0;
        float maxW = rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F - guideInset;
        CompactText compact = compactText(row, rect.w());
        boolean stepper = SettingsStepper.supports(row.actionId());
        if (compact != null) {
            drawCompactText(canvas, rect, compact, copy.redConfirm() ? DANGER_RED : sec(200));
            if (copy.available() && stepper) drawStepper(canvas, row, rect, copy);
            if ((!stepper || !copy.available()) && !copy.verb().isEmpty()) SkiaFontRenderer.drawTextRight(canvas, titleFont, copy.verb(),
                    rect.right() - UiTokens.SETTINGS_ROW_PAD,
                    rect.bottom() - UiTokens.SETTINGS_ROW_PAD - compact.controlH() / 2.0F,
                    copy.redConfirm() ? DANGER_RED : copy.available() ? textPrimary() : sec(130));
            return;
        }

        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, copy.title(), maxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(20)),
                textPrimary());
        drawWrappedDescription(canvas, rect, subFont, copy.subtitle(), textX,
                Math.max(0, descriptionMaxWidth(row, rect.w()) - guideInset), copy.redConfirm()
                        ? DANGER_RED
                        : sec(copy.available() ? 200 : 130));
        // Same treatment as the link cards' "Open": full-weight, centred —
        // the card's call to action, not a footnote.
        if (copy.available() && stepper) drawStepper(canvas, row, rect, copy);
        if ((!stepper || !copy.available()) && !copy.verb().isEmpty()) {
            Font verbFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            SkiaFontRenderer.drawTextRight(canvas, verbFont, copy.verb(),
                    rect.right() - UiTokens.SETTINGS_ROW_PAD,
                    rect.y() + rect.h() / 2.0F,
                    copy.redConfirm() ? DANGER_RED : copy.available() ? textPrimary() : sec(130));
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
        String group=foldableGroup(row);
        SettingsGroupPaint.draw(canvas,rect,tr(row.labelKey()),SettingsGroupIcons.forLabel(row.labelKey()),group!=null,group==null?1:expansionOf(group));
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
    public static void drawChevron(Canvas canvas, float cx, float cy, float size,
                                    float expansion, int color) {
        try (io.github.humbleui.skija.PathBuilder builder = new io.github.humbleui.skija.PathBuilder();
             Paint paint = new Paint()) {
            builder.moveTo(cx - size / 2.0F, cy - size);
            builder.lineTo(cx - size / 2.0F, cy + size);
            builder.lineTo(cx + size / 2.0F, cy);
            paint.setColor(color);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(90.0F * expansion);
            canvas.translate(-cx, -cy);
            try (io.github.humbleui.skija.Path path = builder.detach()) {
                canvas.drawPath(path, paint);
            }
            canvas.restore();
        }
    }

    /** Centred "nothing here" glyph + caption, shared by every empty list. */
    public static void drawEmptyState(Canvas canvas, UiLayout.Rect area, String textKey) {
        com.atom.chat.ui.EmptyState.draw(canvas, area, Text.translatable(textKey).getString());
    }

    private void drawSwitch(Canvas canvas, Row row, UiLayout.Rect rect, int accent, float dtMs) {
        SettingsItem item = row.item();
        ToggleSwitch control = switches.computeIfAbsent(row.switchAnimationKey(), k -> new ToggleSwitch());

        float switchX = rect.right() - UiTokens.SETTINGS_ROW_PAD - UiTokens.SWITCH_W;
        // Pointer-over-switch drives the control's own hover bounce; a row
        // press only counts when it landed on the switch. Unscaled coordinates.
        boolean overSwitch = pointerX >= switchX && pointerX <= switchX + UiTokens.SWITCH_W
                && pointerY >= rect.y() && pointerY <= rect.bottom();
        control.setInteraction(overSwitch, overSwitch && pressedRow == drawnRowIndex);
        control.update(dtMs, item.available() && item.value());

        CompactText compact = compactText(row, rect.w());
        float switchY = compact == null ? rect.y() + (rect.h() - UiTokens.SWITCH_H) / 2.0F
                : rect.bottom() - UiTokens.SETTINGS_ROW_PAD - UiTokens.SWITCH_H;
        // The four-arg overload wires the disabled state: an unavailable
        // switch renders its whole capsule at the control's disabled opacity.
        control.render(canvas, switchX, switchY, accent, item.available());
        if ("history".equals(item.id())) {
            var label = new HistoryLabelRow.Label(tr(item.titleKey()), descriptionText(row), "", item.available(), true);
            label.paint(canvas, rect, compactFlow(compact), textPrimary(), sec(255));
            return;
        }
        if (compact != null) {
            drawCompactText(canvas, rect, compact, sec(item.available() ? 200 : 130));
            return;
        }

        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float maxW = Math.max(0.0F, switchX - textX - s(10));
        float descMaxW = descriptionMaxWidth(row, rect.w());
        // An overridden option explains itself instead of silently doing nothing.
        String subtitleKey = item.performanceLocked() ? "atomchat.settings.performance.locked"
                : item.available() ? item.subtitleKey() : item.subtitleKey() + ".unavailable";
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
        boolean dragging = slider.available() && slider.id().equals(draggingSliderId);
        if (!slider.available()) accent = sec(125);
        CompactText compact = compactText(row, rect.w());
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float titleMaxW = Math.max(0.0F, rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F
                - SkiaFontRenderer.getStringWidth(valueFont, slider.displayValue()) - s(12));

        if (compact != null) {
            drawCompactText(canvas, rect, compact, sec(200));
            SkiaFontRenderer.drawTextRight(canvas, valueFont, slider.displayValue(),
                    rect.right() - UiTokens.SETTINGS_ROW_PAD,
                    rect.bottom() - UiTokens.SETTINGS_SLIDER_ROW_H + UiTokens.SETTINGS_VALUE_BAND,
                    dragging ? accent : textPrimary());
        } else {
        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(slider.titleKey()), titleMaxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(18)),
                textPrimary());
        SkiaFontRenderer.drawTextRight(canvas, valueFont, slider.displayValue(),
                rect.right() - UiTokens.SETTINGS_ROW_PAD, rect.y() + s(18),
                dragging ? accent : textPrimary());

        }

        UiLayout.Rect track = sliderTrackRect(rect);
        float t = knobPosition(slider, dragging);
        float radius = UiTokens.radius(UiTokens.SLIDER_TRACK_H / (2.0F * UiTokens.SCALE), AtomChatAppearance.theme());
        CornerGeometry.drawRoundedRect(canvas, track.x(), track.y(), track.w(), track.h(), radius,
                UiTokens.trackRest(AtomChatAppearance.theme()));
        float fillW = Math.max(track.h(), track.w() * t);
        CornerGeometry.drawRoundedRect(canvas, track.x(), track.y(), fillW, track.h(), radius, accent);

        float knobX = track.x() + track.w() * t - UiTokens.SLIDER_KNOB / 2.0F;
        float knobY = track.y() + track.h() / 2.0F - UiTokens.SLIDER_KNOB / 2.0F;
        CornerGeometry.drawRoundedRect(canvas, knobX, knobY, UiTokens.SLIDER_KNOB, UiTokens.SLIDER_KNOB,
                UiTokens.handleRadius(UiTokens.SLIDER_KNOB, AtomChatAppearance.theme()),
                slider.available() ? 0xFFFFFFFF : sec(125)); // knob: mechanical white
        // The accent trace follows the same radius as the head. A circular
        // ring here would preserve the old circle after the fill itself had
        // gone square, which is exactly the leak this audit is closing.
        CornerGeometry.drawEdgeHighlight(canvas, knobX, knobY,
                UiTokens.SLIDER_KNOB, UiTokens.SLIDER_KNOB,
                UiTokens.handleRadius(UiTokens.SLIDER_KNOB, AtomChatAppearance.theme()), s(1.8F), accent);
    }

    /** Sliders rendered as a right-side input field instead of a drag track. */
    public static boolean isInlineNumberSlider(String id) {
        return "history_retention".equals(id) || "chat_hud_max_messages".equals(id);
    }

    private static String inlineNumberDescription(SettingsSlider slider) {
        return tr("history_retention".equals(slider.id())
                ? "atomchat.settings.chat.history.retention.desc" : "atomchat.settings.chat.hud.limit.desc");
    }

    /**
     * Label + input-field row for integer values that need exact entry (the
     * retention slider could not land on precise day counts). The field is
     * always visible on the right; clicking it opens the inline editor.
     */
    private void drawInlineNumberSlider(Canvas canvas, Row row, UiLayout.Rect rect, int accent) {
        SettingsSlider slider = row.slider();
        boolean editing = numberEditor.editing(slider.id());
        inlineNumberRowRect = rect;
        CompactText compact = compactText(row, rect.w());
        var numberField=SettingsRowText.number(rect,compact!=null);
        long fieldNow=UiStagger.frameTimeMillis();float fieldDt=lastNumberFieldHover==0?0:Math.max(0,Math.min(50,fieldNow-lastNumberFieldHover));lastNumberFieldHover=fieldNow;
        numberFieldHover=UiMotion.approach(numberFieldHover,numberField.contains(pointerX,pointerY)?1:0,fieldDt,UiMotion.HOVER_MS);
        String draft = numberEditor.text();
        String text = editing ? (draft.isEmpty() ? "0" : draft) : Integer.toString(Math.round(slider.value()));
        var stacked=compact==null?null:new SettingsRowText.Text(compact.title(),compact.description(),true,
                compact.titleH(),compact.descriptionH(),compact.height());
        String unit = "history_retention".equals(slider.id()) ? "atomchat.admin.days" : "atomchat.settings.chat.hud.limit.unit";
        HistoryNumberRow.draw(canvas,rect,stacked,tr(slider.titleKey()),inlineNumberDescription(slider),
                tr(unit),text,editing,numberFieldHover,AtomChatAppearance.theme(),
                accent,textPrimary(),sec(200),editing?HistoryNumberRow.caretMillis():0);
    }

    private void drawInfo(Canvas canvas, Row row, UiLayout.Rect rect) {
        CompactText compact = compactText(row, rect.w());
        if (compact != null) {
            drawCompactText(canvas, rect, compact, row.info().isLink() ? LINK_COLOR : sec(200));
            if (row.info().isLink()) SkiaFontRenderer.drawTextRight(canvas,
                    FontManager.font(UiTokens.SETTINGS_TILE_TITLE), tr("atomchat.settings.about.open"),
                    rect.right() - UiTokens.SETTINGS_ROW_PAD,
                    rect.bottom() - UiTokens.SETTINGS_ROW_PAD - compact.controlH() / 2.0F, LINK_COLOR);
            return;
        }
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
                CornerGeometry.drawRoundedRect(canvas, textX, underlineY,
                        SkiaFontRenderer.getStringWidth(valueFont, valueText), s(1.5F), s(0.75F), LINK_COLOR);
            }
        } else {
            List<String> lines = SettingsRowText.wrapCopy(valueFont, valueText, valueMaxW);
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
                    CornerGeometry.drawRoundedRect(canvas, textX, underlineY,
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
        boolean e33Style = PlayerAvatar.drawE33Chat(canvas,
                row.player() != null ? row.player().uuid() : null,
                row.player() != null ? row.player().realName() : null,
                rect.x() + UiTokens.SETTINGS_ROW_PAD, avatarY, avatar);
        if (!e33Style) {
            Image face = row.player() != null
                    ? PlayerAvatar.face(row.player().uuid(), row.player().realName()) : null;
            if (face != null) {
                SkiaImages.drawRoundedImage(canvas, face, rect.x() + UiTokens.SETTINGS_ROW_PAD, avatarY,
                        avatar, avatar, avatar / 2.0F, SamplingMode.LINEAR);
            } else {
                // Avatar stand-in: an opaque neutral, fixed on purpose — it is a
                // placeholder face, not a themed surface, and reads on any panel.
                CornerGeometry.drawRoundedRect(canvas, rect.x() + UiTokens.SETTINGS_ROW_PAD, avatarY,
                        avatar, avatar, avatar / 2.0F, Color.makeARGB(255, 120, 130, 145));
            }
            // Hairline rim concentric with the bitmap (face or placeholder):
            // AvatarRing centres the stroke ON the bitmap edge, so half covers
            // the avatar and half sits past it, and the separator's outer edge
            // lands on the stroke's outer edge — nothing of the row card shows
            // through. Not pixel-nudged on purpose: that moves the ring and
            // opens a one-side seam. Polarity-adaptive UiTokens.rim separates
            // the circle from the row card behind. Same ring language as the
            // color controls.
            com.atom.chat.render.AvatarRing.draw(canvas, rect.x() + UiTokens.SETTINGS_ROW_PAD + avatar / 2.0F, avatarY + avatar / 2.0F,
                    avatar / 2.0F, UiTokens.rim(AtomChatAppearance.theme()));
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
        CornerGeometry.drawRoundedRect(canvas, buttonX, buttonY, buttonW, buttonH, UiTokens.radius(8, AtomChatAppearance.theme()),
                UiTokens.cardHover(hover, AtomChatAppearance.theme()));
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
            if (kind == RowKind.AUTHORS || kind == RowKind.BLOCKED_EMPTY) {
                continue;
            }
            if (kind == RowKind.CREDITS) {
                if (!layout.list.contains(vmx, vmy)) continue;
                int card = stripCardAt(creditsStrip, rect, vmx, vmy);
                if (card < 0) continue;
                float x = stripCardX(rect, card, creditsStrip.scroll, AboutCreditCards.WIDTH);
                UiLayout.Rect logo = AboutCreditCards.logoRect(x, rect.y());
                float left = Math.max(rect.x(), logo.x());
                return new RowHit(Row.ofInfo(SettingsCatalog.thirdPartyRows().get(card)), i, left, logo.y(),
                        Math.min(rect.right(), logo.right()) - left,
                        Math.min(rect.bottom(), logo.bottom()) - logo.y(), left);
            }
            if (kind == RowKind.BUBBLES || kind == RowKind.WALLPAPERS) {
                if (layout.list.contains(vmx, vmy)) {
                    int cardIndex = stripCardAt(kind == RowKind.WALLPAPERS ? wallpaperStrip : bubbleStrip, rect, vmx, vmy);
                    if (cardIndex >= 0) {
                        Row action = kind == RowKind.WALLPAPERS
                                ? Row.ofAction("wallpaper_preset:" + com.atom.chat.wallpaper.WallpaperPresets.presets()[cardIndex].id(), wallpaperPickItem())
                                : Row.ofAction(ACTION_BUBBLE_STYLE + AtomChatConfig.BubbleStyle.values()[cardIndex].name(), bubbleStyleItem());
                        float x = themeCardX(rect, cardIndex, kind == RowKind.WALLPAPERS ? wallpaperStrip.scroll : bubbleStrip.scroll);
                        float y = themeCardY(rect);
                        return new RowHit(action, i, Math.max(x, rect.x()), y,
                                Math.min(x + UiTokens.THEME_CARD_W, rect.right()) - Math.max(x, rect.x()),
                                Math.min(y + UiTokens.THEME_CARD_H + s(18), rect.bottom()) - y, x);
                    }
                }
                continue;
            }
            Row row = rows.get(i);
            if (kind == RowKind.ACTION && SettingsStepper.supports(row.actionId())) {
                if (!row.item().available()) continue;
                var geometry = stepperGeometry(row, rect);
                int direction = geometry.directionAt(vmx, vmy);
                if (direction != 0 && layout.list.contains(vmx, vmy)) {
                    UiLayout.Rect arrow = direction < 0 ? geometry.previous() : geometry.next();
                    Row action = Row.ofAction(row.actionId() + (direction < 0 ? ":prev" : ""), row.item());
                    return new RowHit(action, i, arrow.x(), arrow.y(), arrow.w(), arrow.h(), arrow.x());
                }
                continue;
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
        if (!layout.list.contains(vmx, vmy) || vmy < layout.list.y() + chipBarHeight(section)) return null;
        List<Row> rows = rows(section);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind() != RowKind.SLIDER || !row.slider().available()) {
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
        if (!slider.available()) return;
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
        finishSliderDrag(hasSliderCapture() ? rows(currentSectionForSettle) : List.of());
    }

    private boolean hasSliderCapture() {
        return draggingSliderId != null && activeSliderIndex >= 0;
    }

    private void finishSliderDrag(List<Row> rows) {
        if (hasSliderCapture() && activeSliderIndex < rows.size()
                && rows.get(activeSliderIndex).kind() == RowKind.SLIDER) {
            SettingsSlider slider = rows.get(activeSliderIndex).slider();
            slider.persist();
            settleId = slider.id();
            settleFrom = slider.normalize(dragValue);
            settleTo = slider.normalize(slider.value());
            settleStart = System.currentTimeMillis();
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
        return numberEditor.active();
    }

    public String editingNumberText() {
        return numberEditor.text();
    }

    /** Opens the inline editor with the slider's current rounded value. */
    public void beginNumberEdit(SettingsSlider slider) {
        if (slider == null) {
            return;
        }
        if (hostedHistoryActive() && "history_retention".equals(slider.id())) {
            if (inlineNumberRowRect != null) pressHistory("history_retention", inlineNumberRowRect.x(), inlineNumberRowRect.y());
            return;
        }
        numberEditor.open(slider.id(), Math.round(slider.value()), slider::set, "chat_hud_max_messages".equals(slider.id()));
    }

    public void appendNumberChar(char c) {
        if (hostedHistoryActive()) historyMount.embedded().text("history_retention", String.valueOf(c));
        else numberEditor.append(c);
    }

    public void backspaceNumber() {
        if (hostedHistoryActive()) historyKey(KeyEvent.Key.BACKSPACE);
        else numberEditor.backspace();
    }

    /** Parses and applies the typed value; empty buffer is treated as 0. */
    public void commitNumberEdit() {
        if (hostedHistoryActive()) historyKey(KeyEvent.Key.ENTER);
        else numberEditor.commit();
    }

    public void cancelNumberEdit() {
        if (hostedHistoryActive()) historyKey(KeyEvent.Key.ESCAPE);
        else numberEditor.cancel();
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
        if (!slider.available()) return 0F;
        if (dragging) {
            return slider.normalize(dragValue);
        }
        if (!AtomChatConfig.get().performanceModeEnabled() && slider.id().equals(settleId)) {
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

    public record ColorHit(int rowIndex, SettingsColor color) {}

    public ColorHit colorHit(float vmx, float vmy, UiLayout layout, SettingsSection section, float scrollY) {
        if (!layout.list.contains(vmx, vmy) || vmy < layout.list.y() + chipBarHeight(section)) return null;
        List<Row> rows = rows(section);
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (row.kind() != RowKind.COLOR || rowExpansion(rows, index) < GROUP_FOLD_MIN_DRAWN) continue;
            UiLayout.Rect rect = rowRect(section, rows, index, scrollY, layout);
            if (rect.contains(vmx, vmy)) {
                return new ColorHit(index, row.color());
            }
        }
        return null;
    }

    /** Applies a hit: flips a switch, opens a link, fires an action, folds a
     *  colour group, or unblocks. Destructive actions open a modal confirmation. */
    public boolean perform(RowHit hit) {
        if (hit == null) {
            return false;
        }
        String slot = hostedHistorySlot(hit.row());
        if (slot != null) return pressHistory(slot, hit.x() + hit.w() / 2, hit.y() + hit.h() / 2);
        switch (hit.row().kind()) {
            case SWITCH -> {
                SettingsItem item = hit.row().item();
                if (!item.available()) {
                    return false;
                }
                item.set(!item.value());
                // One-shot pulse on the control the click flipped: the bounce
                // follows the state change, not the physical press window (the
                // same frame writes the config, so a press-bound bounce can be
                // swallowed entirely).
                ToggleSwitch control = switches.get(hit.row().switchAnimationKey());
                if (control != null) {
                    control.pulse();
                }
            }
            case INFO -> {
                if (hit.row().info().isLink()) {
                    openUri(hit.row().info().uri());
                } else { return false; }
            }
            case LABEL -> {
                String group = foldableGroup(hit.row());
                if (group == null) {
                    return false;
                }
                if (!collapsedColorGroups.remove(group)) {
                    collapsedColorGroups.add(group);
                }
            }
            case ACTION -> { return performAction(hit.row()); }
            case BLOCKED -> {
                if (hit.row().player() != null) {
                    BlockList.setBlocked(hit.row().player(), false);
                } else { return false; }
            }
            default -> { return false; }
        }
        return true;
    }

    private boolean performAction(Row row) {
        if (row.item() == null || !row.item().available() || actionHandler == null) return false;
        String actionId = row.actionId();
        if (ACTION_HISTORY_CLEAR.equals(actionId)) {
            String bodyKey = historyClearBodyKey();
            cancelPointer();
            showHistoryClear(row.item(), bodyKey);
            return true;
        }
        if (needsConfirm(actionId)) {
            String bodyKey = "atomchat.confirm." + actionId;
            cancelPointer();
            confirmation.openDangerous(tr(row.item().titleKey()), tr(bodyKey),
                    tr("atomchat.confirm.cancel"), tr("atomchat.confirm.confirm"),
                    () -> actionHandler.onAction(actionId));
        } else {
            actionHandler.onAction(actionId);
        }
        return true;
    }

    private boolean confirmHistoryClear(SettingsItem item) {
        if (!historyTabSelected() || !item.available() || actionHandler == null) return false;
        String bodyKey = historyClearBodyKey();
        cancelPreviewPress();
        finishSliderDrag(hasSliderCapture() ? historyRows() : List.of());
        clearRowPress();
        showHistoryClear(item, bodyKey);
        return true;
    }

    private static String historyClearBodyKey() {
        return AtomChatConfig.get().chatHistoryEnabled
                ? "atomchat.confirm.history_clear" : "atomchat.confirm.history_clear.memory";
    }

    private void showHistoryClear(SettingsItem item, String bodyKey) {
        confirmation.openDangerous(tr(item.titleKey()), tr(bodyKey),
                tr("atomchat.confirm.cancel"), tr("atomchat.confirm.confirm"),
                () -> actionHandler.onAction(ACTION_HISTORY_CLEAR));
    }

    public void bindSettingsContract(ContractRegistry registry, UiSoundSink sound) {
        closeHistoryContract();
        settingsContract = registry;
        settingsSound = java.util.Objects.requireNonNull(sound);
    }

    public boolean historyBodyMounted() { return hostedHistoryActive(); }

    /** The selected field's accepted feedback is emitted by its strict input transaction. */
    public boolean historyOwnsFeedback(RowHit hit) { return hostedHistorySlot(hit.row()) != null; }

    private boolean hostedHistoryActive() { return historyMount != null && historyTabSelected(); }

    private String hostedHistorySlot(Row row) {
        if (!hostedHistoryActive()) return null;
        if (row.kind() == RowKind.SWITCH && "history".equals(row.item().id())) return "history";
        if (row.kind() == RowKind.SLIDER && "history_retention".equals(row.slider().id())) return "history_retention";
        if (row.kind() == RowKind.ACTION && ACTION_HISTORY_CLEAR.equals(row.actionId())) return "history_clear";
        return null;
    }

    private void beginHistoryBody(long now, float dt) {
        if (historyMount == null) {
            historyMount = SettingsContractMount.history(settingsContract, this::historyFields, settingsSound, numberEditor);
        }
        historyMount.begin(now, dt);
    }

    private void paintHistorySlot(Canvas canvas, String slot, UiLayout.Rect rect) {
        boolean number = "history_retention".equals(slot);
        if (number) inlineNumberRowRect = rect;
        historyMount.paint(canvas, slot, rect, pointerX, pointerY,
                number && numberEditor.active(), drawnRowIndex == pressedRow);
    }

    private void paintHistoryGroup(Canvas canvas, String slot, UiLayout.Rect card) {
        historyMount.paintGroup(canvas, "history_clear".equals(slot) ? "cleanupFields" : "storageFields", card);
    }

    private boolean pressHistory(String slot, float x, float y) {
        return historyMount.embedded().pointer(slot, new PointerEvent(PointerEvent.Type.PRESS, x, y, 0));
    }

    private void historyKey(KeyEvent.Key key) {
        historyMount.embedded().key("history_retention", new KeyEvent(key, true, false, false, false));
    }

    private boolean historyTabSelected() {
        return "history".equals(activeChipId(SettingsSection.CHAT));
    }

    /** Binds the real settings data and the existing confirmation route for the history tab. */
    public HistoryFieldsPage historyFields() {
        if (!historyTabSelected()) throw new IllegalStateException("History settings tab is not selected");
        List<Row> rows = historyRows();
        Row toggle = rows.stream().filter(row -> row.kind() == RowKind.SWITCH).findFirst().orElseThrow();
        Row retention = rows.stream().filter(row -> row.slider() != null && isInlineNumberSlider(row.slider().id()))
                .findFirst().orElseThrow();
        Row cleanup = rows.stream().filter(row -> ACTION_HISTORY_CLEAR.equals(row.actionId())).findFirst().orElseThrow();
        SettingsItem item = toggle.item();
        var enabled = new StrictPage.Content(Map.of(
                "title", new StrictPage.Text(tr(item.titleKey())),
                "description", new StrictPage.Text(tr(item.available() ? item.subtitleKey() : item.subtitleKey() + ".unavailable")),
                "value", new StrictPage.BooleanInput(item.value(), next -> {
                    if (!historyTabSelected() || !item.available() || next == item.value()) return false;
                    item.set(next);
                    return true;
                })), item.available());
        SettingsSlider slider = retention.slider();
        var days = HistoryFieldsPage.days(tr(slider.titleKey()), tr("atomchat.settings.chat.history.retention.desc"),
                tr("atomchat.admin.days"), new StrictPage.NumberInput(Math.round(slider.value()), next -> {
                    if (!historyTabSelected()) return false;
                    slider.set((float) next);
                    return true;
                }));
        ActionCopy copy = historyCleanupCopy(cleanup.item());
        return new HistoryFieldsPage(enabled, days, HistoryFieldsPage.cleanup(copy.title(), copy.subtitle(),
                copy.verb(), copy.available(), new StrictPage.Action(() -> confirmHistoryClear(cleanup.item()))));
    }

    /** Shell callback for action cards; the screen owns the file picker. */
    public SettingsContractMount mountHistoryContract(ContractRegistry registry, UiSoundSink sound) {
        return SettingsContractMount.history(registry, this::historyFields, sound);
    }

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
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("ui", "Failed to open link {}"), uri, e);
        }
    }

    /** Drops per-page transient state so reopening a section starts clean. */
    public void closeHistoryContract() {
        if (historyMount != null) {
            historyMount.close();
            historyMount = null;
            numberEditor.cancel();
            inlineNumberRowRect = null;
        }
    }

    public void reset() {
        closeHistoryContract();
        contractLayout.close();
        switches.clear();
        activeChip.clear();
        entrance.markSettled();
        lastInteractiveHover = -1;
        rowHover.clear();
        stepperHover.clear();
        rowPress.clear();
        aboutCardPress.clear();
        colorPress.clear();
        pressedRow = -1;
        colorRowHovered = false;
        themeStrip.reset();
        bubbleStrip.reset();
        creditsStrip.reset();
        qualityStrip.reset();
        pointerStrip = themeStrip;
        themeDeleteHover.clear();
        draggingSliderId = null;
        hoveredIndex = -1;
        cancelNumberEdit();
    }
}
