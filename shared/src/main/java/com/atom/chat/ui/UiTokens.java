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
     * {@link #radiusFactor(float)}: the knob is in 1080p-basis pixels and
     * rides the whole uiDensity (vanilla scale × uiScale × contentScale),
     * so the drawn radius scales with it rather than staying pinned to
     * physical pixels — the default {@code 20} is factor 1, while 0 gives
     * hard 0-radius (pure square) calls on every surface.
     */
    public static float radius(float v) {
        return s(v) * radiusFactor(
                com.atom.chat.config.AtomChatConfig.get().cornerRadius);
    }

    /**
     * Radius multiplier for a configured corner radius. The knob is in
     * 1080p-basis pixels (it scales with the UI density, not pinned to
     * physical pixels): {@code factor = cornerRadius / 20}, so the {@code 20}
     * default is factor 1 and {@code 0} is square (factor 0 everywhere). The
     * product clamps to the shipped 0..1.25 span, which is also where the
     * slider maximum {@code s(20)} lands at the default scale.
     *
     * <p>The denominator was {@code 28} while the knob was on the reference-px
     * scale; a config file from that era is rescaled once on load, so its
     * stored 28 arrives here as 20 (see
     * {@code AtomChatConfig#migratePixelRadius()}).</p>
     */
    public static float radiusFactor(float cornerRadius) {
        return Math.max(0.0F, Math.min(cornerRadius / 20f, 1.25F));
    }

    /**
     * Shared card-family radius: every content card (settings tiles, settings
     * rows, profile rows, notification banners) rounds at this one base, so
     * the slider moves them all together. At the default knob it reproduces
     * s(16) in 1080p-basis pixels.
     */
    public static float cardRadius() {
        return radius(16);
    }

    /**
     * Floating-chrome radius: the composer input bar and the bottom tab bar
     * round at this one tier, one step looser than the card family, so the two
     * bars cannot drift apart. One token owns them both.
     */
    public static float chromeRadius() {
        return radius(18);
    }

    /**
     * Card/chrome surface fill: the configured card colour with its alpha
     * driven by the card-tint slider (60 at 0% → 255 at 100%). The default
     * white at 0% reproduces the shipped frosted wash exactly; a dark colour
     * at 100% is the modern flat "black background, white text". Hover is a
     * separate language ({@link #cardHover(float)}) that follows the accent
     * at both ends of the axis.
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
     * Hover wash on a surface; {@code weight} 0..1. The wash is always dyed
     * by the theme's accent — the hue comes from the accent, never from a
     * neutral — while the surface's polarity decides only the direction of
     * the shift: over a light card the accent is deepened (lime towards
     * olive, so the wash visibly darkens), over a dark card it is lifted
     * (gold towards pale gold, so the wash visibly brightens). The alpha is
     * constant across themes at 55×weight: identical feedback strength on
     * light and dark surfaces is an explicit user requirement from the
     * v0.2.16 field reports, not a tuning accident.
     *
     * <p>The previous language — keep the accent when it contrasts with the
     * surface, otherwise flip to plain black/white — was rejected in real
     * use: on light themes the flip fired almost always, and hover read as a
     * neutral grey-black smudge that had lost the theme colour entirely.
     * Deepening or lifting the accent instead keeps every hover on-theme by
     * construction, because the polarity only chooses the direction, and the
     * accent carries the hue through both branches.</p>
     */
    public static int cardHover(float weight) {
        return cardHover(com.atom.chat.config.AtomChatConfig.get().accentColor,
                cardCutout(), weight);
    }

    /**
     * Accent-dyed, polarity-adaptive hover wash against explicit colours —
     * the unit-test seam (the {@link com.atom.chat.theme.ThemeService#panelIsLight(com.atom.chat.config.AtomChatConfig)
     * panelIsLight(config)} pattern): {@code accent} is the configured accent
     * ARGB, {@code base} the opaque surface the wash lands on. Light base
     * deepens the accent 55% towards black, dark base lifts it 45% towards
     * white; either way the result ships at a constant alpha of 55×weight.
     */
    public static int cardHover(int accent, int base, float weight) {
        float w = Math.max(0.0F, Math.min(1.0F, weight));
        int rgb = com.atom.chat.theme.ThemeService.colorIsLight(base)
                ? mixRgb(accent, 0x000000, 0.55F)
                : mixRgb(accent, 0xFFFFFF, 0.45F);
        return withAlpha(rgb, 55.0F * w);
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
     * Destructive/danger hue, deliberately NOT theme-adaptive: like the link
     * blue, a danger colour is its own identity and must not drift with the
     * accent — a mint themed success and a mint themed failure would be
     * indistinguishable. Shared by the destructive-confirm copy and the action
     * feedback toast so the two never diverge.
     */
    public static int dangerColor() {
        return io.github.humbleui.skija.Color.makeARGB(255, 235, 64, 52);
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
     * The fill of a surface that floats above message content — the notification
     * banner and the action toast. Opaque by design: a translucent fill would let
     * message text bleed through the card.
     *
     * <p>One token so the two floaters cannot drift apart, which is exactly what
     * happened: the toast used {@link #cardCutout()} while the banner hard-coded
     * {@code 0xFF000000 | cardColor}. The latter is opaque white for the default
     * (and every light) card colour, so a banner drew a white card under text
     * picked for the panel's polarity — white on white, sender invisible. This
     * mixes the configured card colour through the card-tint slider over the
     * panel colour, so it follows the theme the same way the rest of the card
     * family does, and a light theme yields a dark-enough panel-toned float
     * instead of white.</p>
     */
    public static int floatSurfaceFill() {
        return cardCutout();
    }

    /**
     * Ink for text and glyphs sitting on {@link #floatSurfaceFill()}. Derived from
     * the fill's own luminance by the same rule {@link #onAccent(int)} uses,
     * rather than from {@code textPrimaryColor}: the float is a surface of its
     * own, so the panel's text colour must not be assumed to read on it.
     */
    /** The same derivation as a pure function (the test seam). */
    public static int onFloatSurface(int fill) {
        return onAccent(fill);
    }

    /**
     * Contrast floor for a glyph drawn in a theme colour on a float surface —
     * WCAG's 3:1 non-text minimum. The success tick's rule
     * ({@link com.atom.chat.avatar.ColorUtil#readableAccent(int, int, float)})
     * is held to this: below it the accent is walked along its own value axis,
     * which keeps the theme's hue instead of flipping to the float's neutral
     * ink. The circle of a checkmark is a shape, not body text, so 3:1 is the
     * right floor rather than 4.5:1.
     */
    public static final float MIN_GLYPH_CONTRAST = 3.0F;

    /**
     * A muted companion to {@link #onFloatSurface(int)}, for the banner's
     * preview line and the toast's secondary wording: the float ink held at 60%
     * so the two lines have a hierarchy without a second hard-coded colour.
     */
    /** As {@link #onFloatSurfaceSecondary(int)}, against an explicit fill. */
    public static int onFloatSurfaceSecondary(int fill) {
        return withAlpha(onFloatSurface(fill), 152.0F);
    }

    /**
     * Placeholder fill for an avatar that has no face to draw on a float surface:
     * the float ink lifted off the fill, so the empty circle reads as a neutral
     * stand-in on a light and a dark float alike instead of the old fixed slate
     * grey that ignored both.
     */
    /** As {@link #onFloatSurfaceTint(int)}, against an explicit fill. */
    public static int onFloatSurfaceTint(int fill) {
        return withAlpha(onFloatSurface(fill), 64.0F);
    }

    /**
     * How far a float surface's shadow is allowed to travel before the layer
     * around it cuts it off. The layer around a fading card must contain the
     * whole shadow, or its clipped edge shows up as a hard rectangle — exactly
     * the artefact reported against the toast. Measured on a raster surface, the
     * two-pass chrome shadow dies out around 38px on its longest side (the inner
     * pass is offset down by half its blur, then spreads), so the pad is that
     * reach, not the blur radius.
     */
    public static float floatSurfaceShadowPad() {
        return s(32);
    }

    /**
     * The reply / quote pill tint: the accent held at the alpha the old
     * hardcoded blue (74, 144, 226)@90 shipped with. The composer's reply
     * bar and the quoted-message surfaces draw this one derived colour, so
     * a reply reads as the theme's own accent instead of a foreign blue.
     */
    public static int quoteAccent(int accent) {
        return withAlpha(accent, 90.0F);
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
     * Polarity-adaptive hairline stroke for card edges: black at alpha 42 on
     * a light surface, white at alpha 42 on a dark one. This replaces the
     * three-tier {@code outlineColor} ladder, which at its card tier (alpha
     * 153 on a user-tuned opaque outline) drew borders so heavy they read as
     * frames, not edges — the v0.2.16 "borders feel flat / too thick"
     * report. The hairline language is one strength for every static edge:
     * whisper-thin (consumers draw it at s(1.0)), polarity-picked, and
     * identical in alpha on both themes, so a light and a dark theme show
     * the same edge intensity. The one deliberate exception is
     * {@link #rim()}, the switch's functional rim, which needs real
     * visibility while it hugs the accent.
     */
    public static int hairline() {
        return hairline(cardCutout());
    }

    /**
     * The hairline pair as a pure function (the test seam): {@code base} is
     * the opaque surface the stroke lands on.
     */
    public static int hairline(int base) {
        return com.atom.chat.theme.ThemeService.colorIsLight(base)
                ? io.github.humbleui.skija.Color.makeARGB(42, 0, 0, 0)
                : io.github.humbleui.skija.Color.makeARGB(42, 255, 255, 255);
    }

    /**
     * A functional stroke — the visible tier exempt from the hairline
     * language, for shapes whose outline must stay readable regardless of
     * theme polarity: the switch's on-rim (a pale accent on a pale card),
     * the inline number-field border, the swatch selection ring. Alpha 110
     * is the floor where that job still gets done. Polarity-adaptive like
     * every stroke: black on light, white on dark.
     */
    public static int rim() {
        return rim(cardCutout());
    }

    /** The rim pair as a pure function (the test seam). */
    public static int rim(int base) {
        return com.atom.chat.theme.ThemeService.colorIsLight(base)
                ? io.github.humbleui.skija.Color.makeARGB(110, 0, 0, 0)
                : io.github.humbleui.skija.Color.makeARGB(110, 255, 255, 255);
    }

    /**
     * Unfilled half of a slider track, polarity-adaptive: deep grey at alpha
     * 70 on a light surface, white at alpha 70 on a dark one. The old single
     * translucent-white track vanished entirely on light themes; splitting
     * the track into accent-filled + this rest segment keeps the untravelled
     * half visible on both polarities. The grey is the switch off-track's
     * own deep grey (30, 30, 34), so the two control families read as one
     * neutral language.
     */
    public static int trackRest() {
        return trackRest(cardCutout());
    }

    /** The track-rest pair as a pure function (the test seam). */
    public static int trackRest(int base) {
        return com.atom.chat.theme.ThemeService.colorIsLight(base)
                ? io.github.humbleui.skija.Color.makeARGB(70, 30, 30, 34)
                : io.github.humbleui.skija.Color.makeARGB(70, 255, 255, 255);
    }

    /**
     * Icon colour that follows the secondary text colour: icons that annotate
     * text (copy glyphs, chevrons) must read as the same ink as the text
     * beside them, so they take the configured secondary text RGB and only
     * rebalance its presence through {@code alpha}.
     */
    public static int iconSecondary(float alpha) {
        return withAlpha(
                com.atom.chat.config.AtomChatConfig.get().textSecondaryColor, alpha);
    }

    // Panel
    public static float panelRadius() {
        return radius(28);
    }

    /**
     * Single-tier drop-shadow colour for floating chrome. The panel bars
     * (header, composer, tab bar) have moved to the two-tier pair below
     * ({@link #CHROME_SHADOW_INNER} / {@link #CHROME_SHADOW_OUTER} via
     * {@code SkiaDraw.drawChromeShadow}); this one stays for the small floaters
     * that keep the single-pass language (jump FAB, quick-phrase panel).
     */
    public static final int CHROME_SHADOW = io.github.humbleui.skija.Color.makeARGB(100, 0, 0, 0);
    /** Two-tier chrome shadow, inner pass: tight blur hugging the surface. */
    public static final int CHROME_SHADOW_INNER = io.github.humbleui.skija.Color.makeARGB(70, 0, 0, 0);
    /** Two-tier chrome shadow, outer pass: wider, softer spread. */
    public static final int CHROME_SHADOW_OUTER = io.github.humbleui.skija.Color.makeARGB(45, 0, 0, 0);
    /**
     * The float family's own pair — the toast and the notification banner. The
     * same two-tier shape as the chrome pair on the same blurs, one third
     * lighter in both passes, so a float reads as a soft containment over the
     * message content behind it instead of a second dark edge under the card.
     * The shell bars keep the chrome pair: that difference is what still tells
     * a floating status surface from the panel's own chrome.
     *
     * <p>Only alpha moved, and deliberately so. {@link #floatSurfaceShadowPad()}
     * is sized to the reach the shared blur pair produces (~38px against the
     * 40px pad), so a lower peak can only make the tail die out sooner — the
     * pad cannot be outrun and no re-measurement is needed. A blur change would
     * be a different change: it moves the reach and needs the raster probe and
     * a bigger pad first.</p>
     */
    public static final int FLOAT_SHADOW_INNER = io.github.humbleui.skija.Color.makeARGB(47, 0, 0, 0);
    /** The float family's outer pass; see {@link #FLOAT_SHADOW_INNER}. */
    public static final int FLOAT_SHADOW_OUTER = io.github.humbleui.skija.Color.makeARGB(30, 0, 0, 0);
    /**
     * The lighter elevation tier for content cards (settings rows, tiles,
     * preview cards): low alpha, small blur — enough to lift a card off the
     * panel without the weight of floating chrome.
     */
    public static final int CARD_SHADOW = io.github.humbleui.skija.Color.makeARGB(38, 0, 0, 0);
    /**
     * Unified base colour of every fixed dark popup plate — the context menus
     * and the emoji / quick-phrase panels. These float above arbitrary content
     * whose polarity no theme token knows, so they do not follow the theme:
     * they all share this one fixed dark surface, and the plate constants
     * elsewhere reference this token so the family cannot drift apart.
     */
    public static final int SKIN_PANEL = io.github.humbleui.skija.Color.makeARGB(245, 35, 39, 47);
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
    // share a box - or an edge inset. The three keys (image / emoji / phrase)
    // are square and belong to the header action family: the header button's
    // own side, radius and s(4) edge inset (EDGE_CONTROL_INSET), so they sit
    // against the bar's left edge and top exactly like the back / filter keys
    // sit in the header card. Send keeps the wider accent capsule it has
    // always had, and with it the roomier s(8) side gap of its own design
    // (INPUT_ROW_PAD) instead of the key family's tighter inset. Their heights
    // differ by design - s(36) against s(30) - so the row is as tall as the
    // taller family and both are centred on that row's axis: Send sits s(3)
    // inside the key band top and bottom instead of on a line of its own.
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
     * The composer's Send-side breathing room: the gap between the Send
     * capsule and the bar's right edge, and the bar's bottom pad below the
     * text band. s(8) = 10 px, the value the capsule shipped with through
     * v0.2.15 and keeps by design. It is NOT the square keys' edge inset any
     * more: the keys joined the header family and sit at
     * {@link #EDGE_CONTROL_INSET} (s(4)) off the bar's left edge and top,
     * so this token now owns only the capsule's side gap and the bar's
     * bottom pad.
     */
    public static final float INPUT_ROW_PAD = s(8);
    /**
     * Height of the button row band. The taller family owns it, which is what
     * lets two heights share one axis: the shorter capsule centres inside the
     * taller family's band and the row's top gap stays EDGE_CONTROL_INSET whatever
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
     * One-line bar height, built from its parts instead of restated: the key
     * inset above the row ({@link #EDGE_CONTROL_INSET} — the header family's
     * own inset, now that the keys belong to it), the row band, the text
     * band, and the row pad below it ({@link #INPUT_ROW_PAD}, the Send
     * family's gap, which also owns the bar's bottom). A taller button
     * family therefore grows the bar by exactly its own growth, so the text
     * keeps the clearance (and the screen position) it already had.
     */
    public static final float INPUT_HEIGHT = EDGE_CONTROL_INSET + INPUT_ROW_H + INPUT_TEXT_BAND + INPUT_ROW_PAD;
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
     * release peak — the overshoot gain is 1.31x the budget), and the
     * s(6) card-shadow blur carries its visible tail out to ~9.4px. The two
     * together overrun 8px — the shadow's tail alone does — but what spills
     * past is the shadow's near-transparent outer fringe, and clipping it is
     * invisible; the trade buys the list back 7px of width per side against
     * the old 15px (s(12)) inset, which budgeted the full press-release
     * spring peak as if the clip were a hard edge that had to survive it
     * unclipped.</p>
     *
     * <p>This is also the profile page's horizontal card margin: the banner
     * card, stat tiles, info card and identity card all sit at this inset
     * from the list edges, so the whole UI reads as one margin language
     * instead of the profile column carrying its own.</p>
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
        return cardRadius();
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
        return cardRadius();
    }
    /** Group heading inside a section (e.g. the blocked-players list title). */
    public static final float SETTINGS_LABEL_H = s(32);
    /** Glyph size for the "nothing here" empty state under a group heading. */
    public static final float SETTINGS_EMPTY_ICON = s(40);
    /** Avatar inside a blocked-player row. */
    public static final float SETTINGS_ROW_AVATAR = s(36);

    // Profile page (QQ-home redesign, banner-card pass): a hero banner CARD
    // inset from the panel edges with the circular avatar straddling its
    // bottom edge, the name and the signature line under it, the stat-tile
    // row, the info rows merged into one card and a bottom identity card that
    // grows to fill whatever list height is left.
    /** Gap between the list top and the banner card's top edge, s(14). */
    public static final float PROFILE_BANNER_MARGIN_TOP = s(14);
    /**
     * Hero banner card height, s(160) = 200 px. The card carries the custom
     * banner photo cover-cropped (own profile), the enlarged pixelated skin
     * face (someone else's profile) or the accent -> panelBg fallback
     * gradient, and melts into the panel through a bottom gradient, so the
     * avatar ring below reads on a quiet ground.
     */
    public static final float PROFILE_BANNER_H = s(160);
    /**
     * Circular avatar side, s(76) = 95 px. Its centre sits exactly on the
     * banner card's bottom edge (half over the banner, half in the content
     * area — the QQ look), so the banner-bottom -> tile-top span carries half
     * of it.
     */
    public static final float PROFILE_AVATAR = s(76);
    /**
     * Width of the panelBg divider ring drawn around the avatar, s(2): an
     * opaque panel-coloured circle one ring wider than the bitmap sits
     * underneath it, which is what separates the avatar from the busy banner
     * behind it. The rim stroke (the outer edge language) sits OUTSIDE this
     * divider ring — divider in, rim out.
     */
    public static final float PROFILE_AVATAR_RING = s(2);
    /** Baseline distance from the avatar's bottom edge to the name baseline. */
    public static final float PROFILE_NAME_BAND = s(24);
    /** Signature font: the small secondary line centred under the name. */
    public static final float PROFILE_SIGN_FONT = s(13);
    /** Baseline distance from the name baseline to the signature baseline. */
    public static final float PROFILE_SIGN_BAND = s(20);
    /** Space from the signature baseline down to the stat-tile row top. */
    public static final float PROFILE_SIGN_TO_TILES = s(14);
    /**
     * Total banner-bottom -> tile-top span, derived from its parts rather than
     * restated: the avatar's lower half hangs into the content by
     * {@link #PROFILE_AVATAR} / 2, then the name band, the signature band and
     * the signature-to-tiles gap. measureContent and every rect builder read
     * this, so moving one part moves the whole stack.
     */
    public static final float PROFILE_HERO_BELOW_H = PROFILE_AVATAR / 2.0F
            + PROFILE_NAME_BAND + PROFILE_SIGN_BAND + PROFILE_SIGN_TO_TILES;
    public static final float PROFILE_EDIT_BADGE = s(28);
    public static final float PROFILE_NAME_FONT = s(20);
    /** Info row height, compacted from s(48) in the QQ-home pass. */
    public static final float PROFILE_ROW_H = s(42);
    public static final float PROFILE_ROW_PAD = s(14);
    public static float profileRowRadius() {
        return cardRadius();
    }
    public static final float PROFILE_ROW_FONT = s(15);
    public static final float PROFILE_ROW_VALUE_FONT = s(13);
    /**
     * Base height of the bottom identity card: card pad above and below ONE
     * UUID row. The card is content-sized now — it no longer swallows the
     * remaining list height and the page ends where the card ends. ProfilePage
     * adds the blocked marker row (row gap + row) on top when the subject is
     * on the block list.
     */
    public static final float PROFILE_IDCARD_MIN_H =
            PROFILE_ROW_PAD * 2.0F + PROFILE_ROW_H;
    public static final float PROFILE_TILE_H = s(50);
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
     * Per-channel RGB mix: {@code t} (0..1) is how much of {@code b} shows
     * through; alpha is zeroed because every caller re-alphas through
     * {@link #withAlpha(int, float)}. The mix behind the hover wash's
     * deepen/lift branches.
     */
    private static int mixRgb(int a, int b, float t) {
        int ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return io.github.humbleui.skija.Color.makeARGB(0,
                Math.round(ar + (br - ar) * t),
                Math.round(ag + (bg - ag) * t),
                Math.round(ab + (bb - ab) * t));
    }

    /** Re-alpha helper: keeps the RGB, replaces the alpha (clamped, rounded).
     *  Public for overlays whose every element multiplies its own alpha
     *  by the open/close progress and has no whole-card alpha layer. */
    public static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return io.github.humbleui.skija.Color.makeARGB(a,
                (rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF);
    }

    private UiTokens() {
    }
}
