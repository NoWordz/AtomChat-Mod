package com.atom.chat.ui;

/**
 * Single source of truth for every UI size. No layout code may hardcode pixel
 * values; one-off paddings go through s() so the global scale stays coherent.
 */
public final class UiTokens {
    /** Global multiplier — bump this to scale the whole UI. */
    public static final float SCALE = 1.25F;

    public static float s(float v) {
        return v * SCALE;
    }

    /**
     * Corner-scaled radius for the surrounding chrome — panel, cards, pills,
     * popups. Chat bubbles are deliberately excluded (their radius is part of
     * the message identity), so bubble draws keep using {@link #BUBBLE_RADIUS}.
     * Driven by the continuous {@code cornerRadius} config knob through
     * {@link #radiusFactor(float)}: 0 gives hard 0-radius (pure square) calls
     * on every surface.
     */
    public static float radius(float v) {
        return s(v) * radiusFactor(
                com.atom.chat.config.AtomChatConfig.get().cornerRadius);
    }

    /**
     * Radius multiplier for a configured corner radius: {@code 0} is square
     * (factor 0 everywhere), the {@code 28} reference is the shipped default
     * look (factor 1, the old "large"), the slider maximum {@code s(28)} is
     * 1.25. Values outside 0..s(28) clamp.
     */
    public static float radiusFactor(float cornerRadius) {
        float r = Math.max(0.0F, Math.min(cornerRadius, s(28)));
        return r / 28f;
    }

    /**
     * Card/chrome surface fill: the configured card colour with its alpha
     * driven by the card-tint slider (60 at 0% → 255 at 100%). The default
     * white at 0% reproduces the shipped frosted wash exactly; a dark colour
     * at 100% is the modern flat "black background, white text". Hover stays
     * a plain white overlay at every position, so one hover language covers
     * the whole axis.
     */
    public static int cardFill() {
        com.atom.chat.config.AtomChatConfig config = com.atom.chat.config.AtomChatConfig.get();
        float t = Math.max(0.0F, Math.min(1.0F, config.cardTint));
        int rgb = config.cardColor & 0x00FFFFFF;
        // The slider is the card opacity and it means it: 0% = no fill at all,
        // text floats directly on the panel. The shipped frosted look lives at
        // ~24% (white @ alpha 60), which is the config default.
        return io.github.humbleui.skija.Color.makeARGB(Math.round(255.0F * t),
                (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /**
     * Hover wash on a surface; {@code weight} 0..1. Uses the theme's accent
     * when the accent actually contrasts with the surface under it, and a
     * polarity-flipped neutral wash when it does not: a pale accent over a
     * pale card was white-on-white invisible (the "hover does nothing"
     * report), and no alpha tuning can rescue a wash whose colour matches
     * the ground. Visibility is decided by the WCAG contrast ratio between
     * the accent and the card's pre-mixed surface ({@link #cardCutout()}),
     * with 3:1 — the WCAG graphics-contrast bar — as the pass line. On a
     * fail the wash flips to black over a light surface / white over a dark
     * one and its alpha rises 45 → 60 so the flipped neutral still reads at
     * feedback strength. The weight stays a plain alpha multiplier, so each
     * polarity keeps one feedback strength along the whole tint axis.
     */
    public static int cardHover(float weight) {
        return cardHover(com.atom.chat.config.AtomChatConfig.get().accentColor,
                cardCutout(), weight);
    }

    /**
     * Contrast-derived hover wash against explicit colours — the unit-test
     * seam (the {@link com.atom.chat.theme.ThemeService#panelIsLight(com.atom.chat.config.AtomChatConfig)
     * panelIsLight(config)} pattern): {@code accent} is the configured accent
     * RGB, {@code base} the opaque surface the wash lands on.
     */
    public static int cardHover(int accent, int base, float weight) {
        float w = Math.max(0.0F, Math.min(1.0F, weight));
        if (contrastRatio(accent, base) >= 3.0F) {
            return withAlpha(accent, 45.0F * w);
        }
        return com.atom.chat.theme.ThemeService.colorIsLight(base)
                ? withAlpha(0xFF000000, 60.0F * w)
                : withAlpha(0xFFFFFFFF, 60.0F * w);
    }

    /**
     * Selected pill: the accent held back to a translucent wash rather than
     * painted solid. Solid accent reads as a filled block stamped on the surface;
     * at this alpha the surface picks up the theme colour instead, which is the
     * same relationship the hover wash has with it, just far stronger - so the
     * selected cell is unmistakably ahead of a hovered one without becoming a
     * slab. This is the value the pill shipped with before it was briefly made
     * opaque.
     */
    public static int accentFill() {
        int rgb = com.atom.chat.config.AtomChatConfig.get().accentColor & 0xFFFFFF;
        return io.github.humbleui.skija.Color.makeARGB(162,
                (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /**
     * Content colour that reads on top of a filled accent shape. Picked from the
     * fill's own luminance, not the panel's: a theme's accent can be pale
     * enough to need dark glyphs on a dark panel, or deep enough to need white
     * ones on a light panel, so the panel tells us nothing about it.
     */
    public static int onAccent(int fill) {
        return com.atom.chat.theme.ThemeService.colorIsLight(fill)
                ? io.github.humbleui.skija.Color.makeARGB(255, 28, 25, 1)
                : io.github.humbleui.skija.Color.makeARGB(255, 255, 255, 255);
    }

    /**
     * Opaque stand-in for the card surface, pre-mixed from the card colour at
     * the card-tint alpha over the panel colour. Used where a shape has to
     * read as a hole cut through to the card (the slider knob's gap ring): a
     * translucent {@link #cardFill()} over the accent track only tints it,
     * while this pre-mix looks like a cut-out on frosted and opaque panels
     * alike.
     */
    public static int cardCutout() {
        com.atom.chat.config.AtomChatConfig config = com.atom.chat.config.AtomChatConfig.get();
        float t = Math.max(0.0F, Math.min(1.0F, config.cardTint));
        int cardRgb = config.cardColor & 0x00FFFFFF;
        int panelRgb = config.panelBgColor & 0x00FFFFFF;
        int r = Math.round(((cardRgb >> 16) & 0xFF) * t + ((panelRgb >> 16) & 0xFF) * (1.0F - t));
        int g = Math.round(((cardRgb >> 8) & 0xFF) * t + ((panelRgb >> 8) & 0xFF) * (1.0F - t));
        int b = Math.round((cardRgb & 0xFF) * t + (panelRgb & 0xFF) * (1.0F - t));
        return io.github.humbleui.skija.Color.makeARGB(255, r, g, b);
    }

    /**
     * Stroke colour for outline tier {@code level} (1..3): the three-level
     * border hierarchy behind the "borders feel flat" report. Tier 1 is the
     * page container (strongest), tier 2 card panels and card-level controls,
     * tier 3 the faintest elements inside a card. The configured panel
     * outline colour is resolved with the same contrast logic as
     * {@link #cardHover(int, int, float)}: when it cannot be seen on the
     * card surface (white outline on a light card) the stroke flips to the
     * surface's opposite polarity. All three tiers share that resolved
     * colour and differ only in alpha — 100% / 60% / 30% of the outline's
     * own alpha — so the hierarchy reads as one border language at three
     * intensities instead of three unrelated edges. Pending in-game visual
     * acceptance, tuned blind against the luminance math.
     */
    public static int outlineColor(int level) {
        return outlineColor(level,
                com.atom.chat.config.AtomChatConfig.get().panelOutlineColor,
                cardCutout());
    }

    /**
     * Explicit-colour seam for tests and for callers whose stroke lands on a
     * different surface than the card (the page container's tier-1 rim will
     * pass the panel background here when it migrates to this token).
     */
    public static int outlineColor(int level, int outline, int base) {
        float k = switch (level) {
            case 1 -> 1.0F;
            case 2 -> 0.6F;
            case 3 -> 0.3F;
            default -> throw new IllegalArgumentException(
                    "outline level must be 1..3, got " + level);
        };
        int resolved = contrastRatio(outline, base) >= 3.0F
                ? (outline & 0x00FFFFFF)
                : (com.atom.chat.theme.ThemeService.colorIsLight(base) ? 0x000000 : 0xFFFFFF);
        return withAlpha(resolved, ((outline >>> 24) & 0xFF) * k);
    }

    // Panel
    public static float panelRadius() {
        return radius(28);
    }

    /** Shared drop-shadow colour for floating chrome (header, composer, tab bar). */
    public static final int CHROME_SHADOW = io.github.humbleui.skija.Color.makeARGB(100, 0, 0, 0);
    /**
     * The lighter elevation tier for content cards (settings rows, tiles,
     * preview cards): low alpha, small blur — enough to lift a card off the
     * panel without the weight of floating chrome.
     */
    public static final int CARD_SHADOW = io.github.humbleui.skija.Color.makeARGB(38, 0, 0, 0);
    public static final float PANEL_ANCHOR_X = s(24);
    public static final float PANEL_TOP_GAP = s(8);

    // Header
    public static final float HEADER_HEIGHT = s(44);
    public static float headerRadius() {
        return radius(16);
    }
    public static final float HEADER_PAD_X = s(20);
    /**
     * Square side of every chrome action button: the header's back / filter
     * button and, by this one token, each of the three composer keys. The header
     * used to carry the number as a local s(36) inside each screen's
     * {@code backButton()}, which this file's own rule forbids, and nothing
     * compared it to the composer keys - so the keys drifted onto the Send
     * capsule's s(30) and the two families stopped matching. One token owns the
     * square, so they can only move together now.
     *
     * <p>Send is deliberately NOT this size: it keeps the wider, shorter
     * BUTTON_W x BUTTON_H capsule it has always been.</p>
     */
    public static final float ACTION_BUTTON_SIZE = s(36);
    /**
     * Edge inset the chrome action keys keep to their card, s(4) = 5px on
     * screen: the header's back button off the header's left edge, the filter
     * key one slot to its right, and the hover wash every one of these keys
     * draws inside its own square — ShellHeader's icon buttons and the
     * composer keys alike. One token owns the whole family so the insets
     * cannot drift apart again.
     */
    public static final float EDGE_CONTROL_INSET = s(4);

    // Composer row. Two button families share one row and deliberately do NOT
    // share a box: the three keys (image / emoji / phrase) are square and take
    // the header action button's own side, with that button's insets and radius
    // (s(4) between neighbours, s(8) radius, s(18) glyph), while Send keeps the
    // wider accent capsule it has always had. Their heights differ by design -
    // s(36) against s(30) - so the row is as tall as the taller family and both
    // are centred on that row's axis: Send sits s(3) inside the key band top and
    // bottom instead of on a line of its own.
    public static final float BUTTON_W = s(56);
    public static final float BUTTON_H = s(30);
    public static final float BUTTON_RADIUS = s(9);
    public static final float BUTTON_GAP = s(6);
    public static final float COMPOSER_KEY_GAP = s(4);

    // Input bar. INPUT_HEIGHT is the one-line baseline; the bar grows upward by
    // one line height while the text wraps onto a second line and never beyond
    // INPUT_MAX_LINES - past that the text scrolls inside the fixed box.
    public static final int INPUT_MAX_LINES = 2;
    public static final float INPUT_BAR_PAD = s(12);
    /**
     * Edge inset of the composer's button row against its bar (left, right and
     * the gap above the row). Unified with the header keys' own s(4) edge
     * inset — it was s(8) before the two card-edge insets were reconciled.
     */
    public static final float INPUT_ROW_PAD = s(4);
    /**
     * Height of the button row band. The taller family owns it, which is what
     * lets two heights share one axis: the shorter capsule centres inside the
     * taller family's band and the row's top gap stays INPUT_ROW_PAD whatever
     * either family measures. Derived from the two families rather than restated
     * so a change to either one carries the row, the bar below it and the text
     * with it - the row was once pinned to BUTTON_H while the keys were a
     * different size, and the placeholder paid for it.
     */
    public static final float INPUT_ROW_H = Math.max(ACTION_BUTTON_SIZE, BUTTON_H);
    /**
     * Vertical band the input text owns: from the bottom of the button row to
     * the bar's inner bottom edge. s(36) = 45 px, which centres one line box of
     * the shipped font ({@link #INPUT_LINE_H_REF}, 36.2 px) in it with 4.4 px
     * above and below. The line is centred in this band, so the two clearances
     * stay equal, and the band must keep that headroom: a band squeezed down to
     * the line box is exactly what let a taller button row eat the descenders.
     *
     * <p>This is the band's own number, not the key side; the two land on the
     * same value at this scale and are free to move apart.</p>
     */
    public static final float INPUT_TEXT_BAND = s(36);
    /**
     * One line box of FONT_INPUT on the shipped bundled font, in real pixels.
     * Its hhea metrics are 1.448 em (ascent 1160, descent 288 per 1000 upem) and
     * FONT_INPUT is s(20) = 25 px, so descent-to-ascent is 36.2 px. UiLayout is
     * pure and cannot ask Skia for this, so the measurement is pinned here and
     * the tests use it to prove the band still clears a full line and its
     * descenders. A Latin fallback font reports about 29, which is how the size
     * of this box went unnoticed while the composer was clipping text.
     */
    public static final float INPUT_LINE_H_REF = 36.2F;
    /**
     * One-line bar height, built from its parts instead of restated: the row pad
     * above the row, the row band, the text band, and the row pad below it. A
     * taller button family therefore grows the bar by exactly its own growth, so
     * the text keeps the clearance (and the screen position) it already had.
     */
    public static final float INPUT_HEIGHT = INPUT_ROW_PAD * 2.0F + INPUT_ROW_H + INPUT_TEXT_BAND;
    public static final float INPUT_TEXT_X = s(14);
    public static final float PANEL_BOTTOM_PAD = s(14);

    // Fonts. Body/input/name/quote were bumped one notch (r15 legibility pass):
    // on 2K/4K panels the physical glyph size was diluted by uiDensity, so the
    // virtual sizes carry more weight now. Every layout that wraps or centres
    // these fonts reads the height back from the Font metrics (messageHeight,
    // inputLineHeight, wrapped lines), so rows grow with the tokens; the fixed
    // bands (NAME_BAND, QUOTE_HEIGHT) keep headroom above the new metric boxes.
    public static final float FONT_TITLE = s(19);
    public static final float FONT_TIME = s(16);
    public static final float FONT_NAME = s(17);
    public static final float FONT_BODY = s(20);
    public static final float FONT_INPUT = s(20);
    public static final float FONT_BUTTON = s(16);
    public static final float FONT_EMOJI = s(22);
    public static final float FONT_KAOMOJI = s(17);
    public static final float FONT_QUOTE = s(16);

    // Messages
    public static final float AVATAR_SIZE = s(40);
    /** Gap between the avatar's outer edge and the bubble. */
    public static final float AVATAR_GAP = s(8);
    /** Name band doubles as the gap between the name and the bubble top. */
    public static final float NAME_BAND = s(26);
    public static final float LIST_GAP = s(10);
    public static final float LIST_PAD_X = s(12);
    public static final float BUBBLE_RADIUS = s(12);
    /** Horizontal bubble padding (both sides). Shared by drawing and messageHeight(). */
    public static final float BUBBLE_PAD = s(12);
    /** Vertical bubble padding. Shared by drawing and messageHeight() — never inline it. */
    public static final float BUBBLE_PAD_Y = s(11);
    /** Same, for the centered system-message capsule. */
    public static final float SYSTEM_BUBBLE_PAD_Y = s(6);
    public static final float BUBBLE_MIN_W = s(24);
    public static final float BUBBLE_RETRACT = s(106);
    /**
     * Image messages are scaled to fit this box and the bubble then hugs the
     * result, so there is no stretching, cropping or letterboxing. Also the
     * fallback size while the image is still downloading.
     */
    public static final float IMAGE_MAX_W = s(220);
    public static final float IMAGE_MAX_H = s(140);
    /**
     * Horizontal QQ-style entrance slide distance (own from right, other from
     * left). Kept small on purpose: a long travel dominates the fade and the
     * entrance reads as "a bubble flew in" instead of "a bubble appeared".
     */
    public static final float MESSAGE_SLIDE = s(14);

    // Quote pill (e33chat style: a small capsule above the bubble)
    public static final float QUOTE_HEIGHT = s(24);
    public static final float QUOTE_GAP = s(3);
    public static final float QUOTE_PAD_X = s(8);

    // Emoji panel. Sized so 8 columns fill most of the 420-wide panel:
    // cell width is (panelW - LIST_PAD_X*2 - PANEL_PAD*2) / EMOJI_COLS.
    public static final float EMOJI_CELL = s(34);
    public static final int EMOJI_COLS = 8;
    public static final int EMOJI_VISIBLE_ROWS = 5;
    public static final float EMOJI_TAB_H = s(34);
    public static final float EMOJI_PANEL_PAD = s(12);
    /** Kaomoji rows hold long strings, so they get their own (taller) row height. */
    public static final float EMOJI_KAOMOJI_ROW_H = s(24);

    // Emote (sticker) grid: bigger cells than emoji so the images read clearly.
    // The pack cap of 10 fills two rows of six, so the grid never needs to scroll.
    public static final int EMOTE_COLS = 6;
    public static final float EMOTE_CELL = s(44);
    /** Corner remove button shown on a hovered emote cell. */
    public static final float EMOTE_REMOVE_SIZE = s(14);

    // Context menu
    public static final float MENU_W = s(110);
    public static final float MENU_H = s(64);
    /** Context-menu icon size; also the reference size for icon stroke scaling. */
    public static final float CONTEXT_ICON_SIZE = s(16);

    // Icon line language. All AtomChat SVG paths use a hand-written line-icon
    // language. Stroke width follows an optical taper rather than a strict
    // linear scale: tiny 16px icons keep 1.5 stroke, 24px uses 2.0 and 32px
    // uses 2.5 (the svg-design reference table), so large tab icons do not get
    // proportionally heavy.
    public static final float ICON_STROKE_REF = s(1.5F);
    public static final float ICON_STROKE_LARGE = s(2.5F);
    public static final float ICON_LARGE_SIZE = s(32);

    /** Returns the stroke width (UI units) that matches {@code iconSize}. */
    public static float iconStroke(float iconSize) {
        if (iconSize <= CONTEXT_ICON_SIZE) {
            return ICON_STROKE_REF;
        }
        float t = (iconSize - CONTEXT_ICON_SIZE) / (ICON_LARGE_SIZE - CONTEXT_ICON_SIZE);
        return ICON_STROKE_REF + t * (ICON_STROKE_LARGE - ICON_STROKE_REF);
    }

    // Bottom tab bar (root pages only; hidden on detail pages). Icon-only:
    // no text labels, so the bar is a formula layout around the icon:
    //   bar height = icon size
    //              + 2 * capsule padding (icon -> highlight pill)
    //              + 2 * edge padding    (highlight pill -> bar edge)
    // The pill is full-cell wide; the icon sits on the pill's vertical centre,
    // and the pill keeps s(8) breathing room from the bar edge on all sides.
    public static final float TAB_EDGE_PAD = s(8);
    /** Height of a section-chip capsule (same as the settings row controls). */
    public static final float CHIP_PILL_H = s(26);
    public static final float TAB_CAPSULE_PAD = s(4);
    public static final float TAB_ICON_SIZE = s(24);
    public static final float TAB_BAR_H = TAB_ICON_SIZE + 2.0F * (TAB_CAPSULE_PAD + TAB_EDGE_PAD);
    /** Vertical inset between the root content list and content rows/controls. */
    public static final float ROOT_CONTENT_GAP = s(10);
    /**
     * Horizontal clearance a hover-scaled card leaves to the list clip on each
     * side. A card drawn exactly as wide as the clip has zero room, so the
     * hover scale shears its rounded ends flat against the clip edge.
     *
     * <p>8px on screen (s(6.4)): the width-budget bounce spends
     * {@link PressScale#BUDGET_PX} per side (4px nominal, ~5.24px at the
     * release peak — the overshoot gain is 1.31x the budget), and the s(4)
     * card-shadow blur carries its visible tail out to ~6.25px. The two can
     * graze 8px together, but whatever spills past is the shadow's
     * near-transparent outer fringe — clipping it is invisible, and the trade
     * buys the list back 7px of width per side against the old 15px (s(12))
     * inset, which budgeted the full press-release spring peak as if the
     * clip were a hard edge that had to survive it unclipped.</p>
     */
    public static final float ROW_CLIP_INSET = s(6.4F);

    // Settings home: a 2-column tile grid. Tile width is derived from the list
    // width ((listW - TILE_GAP) / 2 = 188.75 at the default 420 panel), so the
    // grid always fills the column exactly and never needs its own constant.
    public static final int SETTINGS_TILE_COLS = 2;
    /**
     * Tiles are square: the side equals the computed tile width, so the grid is
     * always 2x2 of perfect squares regardless of panel width. The glyph and
     * its label form one centred group inside that square.
     */
    public static final float SETTINGS_TILE_GAP = s(10);
    public static float settingsTileRadius() {
        return radius(12);
    }
    public static final float SETTINGS_TILE_ICON = s(34);
    public static final float SETTINGS_TILE_TITLE = s(15);
    public static final float SETTINGS_TILE_SUB = s(12);
    /** Gap between the glyph and its label inside the centred group. */
    public static final float SETTINGS_TILE_TEXT_GAP = s(10);

    // Settings section rows (switch items, blocked players, about entries).
    public static final float SETTINGS_ROW_H = s(56);
    /**
     * Theme-preview row: title line up top, then the horizontally scrollable
     * mini-panel cards, then the theme name under each card.
     */
    public static final float SETTINGS_THEME_ROW_H = s(172);
    /** One mini-panel preview card (the theme picker card, not its name line). */
    public static final float THEME_CARD_W = s(96);
    public static final float THEME_CARD_H = s(116);
    public static final float THEME_CARD_GAP = s(10);
    /** About-page hero card: logo plate on the left, wordmark on the right. */
    public static final float SETTINGS_HERO_H = s(88);
    public static final float SETTINGS_HERO_PLATE = s(56);
    public static final float SETTINGS_HERO_FONT = s(24);
    public static final float SETTINGS_ROW_GAP = s(8);
    public static final float SETTINGS_ROW_PAD = s(14);
    public static float settingsRowRadius() {
        return radius(12);
    }
    /** Group heading inside a section (e.g. the blocked-players list title). */
    public static final float SETTINGS_LABEL_H = s(32);
    /** Glyph size for the "nothing here" empty state under a group heading. */
    public static final float SETTINGS_EMPTY_ICON = s(40);
    /** Avatar inside a blocked-player row. */
    public static final float SETTINGS_ROW_AVATAR = s(36);

    // Profile page: hero identity card (large circular avatar with an edit
    // badge) above an info-card list of copyable rows.
    public static final float PROFILE_AVATAR = s(96);
    public static final float PROFILE_AVATAR_HERO_H = s(180);
    public static final float PROFILE_EDIT_BADGE = s(28);
    public static final float PROFILE_NAME_FONT = s(20);
    public static final float PROFILE_ROW_H = s(48);
    public static final float PROFILE_ROW_PAD = s(14);
    public static float profileRowRadius() {
        return radius(12);
    }
    public static final float PROFILE_ROW_FONT = s(15);
    public static final float PROFILE_ROW_VALUE_FONT = s(13);
    public static final float PROFILE_TILE_H = s(62);
    public static final float PROFILE_TILE_GAP = s(10);
    public static final float PROFILE_TILE_VALUE_FONT = s(17);
    public static final float PROFILE_TILE_LABEL_FONT = s(11);

    // Slider row: title line on top, track below. The knob is deliberately a
    // touch larger than the track so it reads as a handle, not a filled bar.
    public static final float SETTINGS_SLIDER_ROW_H = s(64);
    public static final float SETTINGS_SLIDER_TRACK_Y = s(40);
    public static final float SLIDER_TRACK_H = s(6);
    public static final float SLIDER_KNOB = s(18);

    // Toggle switch. Geometry is iOS-proportioned: knob diameter is track
    // height minus the two insets, so the travel is exactly
    // SWITCH_W - SWITCH_KNOB - 2 * SWITCH_INSET = s(40) - s(18) - s(4) = 22.5.
    public static final float SWITCH_W = s(40);
    public static final float SWITCH_H = s(22);
    public static final float SWITCH_KNOB = s(18);
    public static final float SWITCH_INSET = s(2);

    // Panel background blur (gated by AtomChatConfig.blurEnabled). The tint sits
    // on top of the blurred snapshot so text stays legible without smothering
    // it — 0x66 read as a bare oil film over the world, 0x99 keeps the panel's
    // blue-grey character while still letting the blur show through.
    public static final float PANEL_BLUR_SIGMA = 30.0F;
    public static final int PANEL_BLUR_TINT = 0xCC16191F;

    /**
     * WCAG contrast ratio (1..21) between two opaque colours, alpha ignored.
     * One definition of "can this be seen on that surface" for every
     * feedback stroke the UI derives — the hover-wash flip and the outline
     * polarity — so visibility means the same thing everywhere.
     */
    private static float contrastRatio(int a, int b) {
        float la = com.atom.chat.theme.ThemeService.relativeLuminance(a);
        float lb = com.atom.chat.theme.ThemeService.relativeLuminance(b);
        float hi = Math.max(la, lb);
        float lo = Math.min(la, lb);
        return (hi + 0.05F) / (lo + 0.05F);
    }

    /** Re-alpha helper: keeps the RGB, replaces the alpha (clamped, rounded). */
    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return io.github.humbleui.skija.Color.makeARGB(a,
                (rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF);
    }

    private UiTokens() {
    }
}
