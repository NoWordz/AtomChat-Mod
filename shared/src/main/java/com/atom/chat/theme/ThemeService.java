package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;

/**
 * Built-in theme presets and the corner-style scale factor.
 *
 * <p>A theme is a <em>snapshot application</em>: picking one writes a fixed
 * set of appearance values into the existing config fields (and persists the
 * config). There is no overlay layer — after applying, every single knob
 * stays individually editable, and the subtitle on the theme card simply
 * shows the last applied preset.</p>
 *
 * <p>Two kinds of presets coexist:</p>
 * <ul>
 *   <li><b>Look presets</b> ({@code frosted}, {@code modern}) move only the
 *       surface-style knobs (opacity, blur, bezel, card tint). Colours are a
 *       personal choice a re-skin of the chrome has no business overwriting —
 *       with one exception: the frosted tile doubles as the factory-default
 *       reset and writes the shipped palette back too.</li>
 *   <li><b>Colour presets</b> ({@link #MINIMAL} … {@link #MINT}) are full
 *       re-skins: they write all twelve colour fields plus the look values.
 *       Most are opaque by design; a translucent preset ({@link #DUSK},
 *       {@link #MINT}) ships its own {@code panelOpacity} and asks for the
 *       background blur to sit behind the tint. The corner style is
 *       deliberately not a preset knob — it has its own slider on the
 *       appearance page, so no preset touches it.</li>
 * </ul>
 *
 * <p>Either way the write is one-shot: hand-tuning a colour afterwards simply
 * wins, and picking the same preset again restores its snapshot. No deadlock.
 * The shipped look stays reachable under the name "Default" (formerly
 * "frosted"): it is what an empty {@code themeName} means, and picking that
 * tile restores every colour and look field from
 * {@link AtomChatConfig#DEFAULT} — a real trip back to the factory values,
 * which hand-tuning alone could never return to.</p>
 *
 * <p>Pure Java — unit tests cover the mappings.</p>
 */
public final class ThemeService {
    /** The shipped default: translucent panel, blur on, white bezel, large corners. */
    public static final String FROSTED = "frosted";
    /** Opaque modern flat: full opacity, no blur, no bezel, small corners. */
    public static final String MODERN = "modern";
    /** Accent the frosted tile shows in the theme picker (the shipped blue). */
    public static final int FROSTED_ACCENT = 0xFF4A90E2;

    private ThemeService() {
    }

    /**
     * Corner scale factor for panel/card/pill/popup surfaces. Chat bubbles are
     * excluded by design — their radius is part of the message identity, not
     * of the surrounding chrome.
     */
    public static float cornerFactor(String cornerStyle) {
        return switch (cornerStyle == null ? "large" : cornerStyle) {
            case "medium" -> 0.5F;
            case "small" -> 0.35F;
            default -> 1.0F; // large — the shipped default
        };
    }

    /**
     * One colour preset: the twelve colour fields plus the look values and the
     * corner style the palette was designed around. All values ARGB, surfaces
     * opaque unless the preset carries an explicit {@code panelOpacity} —
     * the frosted preset and the translucent presets (dusk, mint) are the
     * translucent looks.
     *
     * <p>{@code panelOpacity} / {@code blurEnabled} are the preset's look
     * vote: {@code null} means the preset does not manage the knob and the
     * standard colour-preset look block applies (full opacity, blur off —
     * blur under a solid surface is invisible work). A non-null
     * {@code panelOpacity} is written to the config and turns the preset
     * translucent; a translucent preset asks for the background blur so the
     * tint has something to sit on ({@code PanelBackground.tintFor} composes
     * the two).</p>
     *
     * <p>{@code cornerStyle} is a <em>drawing reference only</em> (preview
     * cards render each palette with its native corner scale); applying a
     * preset never writes it — the corner radius is an independently set knob.</p>
     */
    public record Preset(String id, int accent,
                         int ownBubble, int bubbleText,
                         int otherBubble, int otherBubbleText,
                         int panelBg, int textPrimary, int textSecondary,
                         int card, int panelOutlineColor,
                         int capsuleBg, int capsuleText,
                         Float panelOpacity, Boolean blurEnabled,
                         String cornerStyle) {
        /** Writes the snapshot into the config. The caller persists. */
        public void applyTo(AtomChatConfig config) {
            config.accentColor = accent;
            config.ownBubbleColor = ownBubble;
            config.bubbleTextColor = bubbleText;
            config.otherBubbleColor = otherBubble;
            config.otherBubbleTextColor = otherBubbleText;
            config.panelBgColor = panelBg;
            config.textPrimaryColor = textPrimary;
            config.textSecondaryColor = textSecondary;
            config.cardColor = card;
            config.panelOutlineColor = panelOutlineColor;
            config.secondaryCapsuleBg = capsuleBg;
            config.secondaryCapsuleText = capsuleText;
            // cornerRadius deliberately NOT written: it is an independent
            // setting now (appearance page radius slider), not a preset
            // side effect.
        }
    }

    /** 极简 — white settings-app look: opaque white cards, near-black body, blue accent, crisp small corners. */
    public static final Preset MINIMAL = new Preset("minimal",
            0xFF3B82F6,
            0xFF3B82F6, 0xFFFFFFFF,
            0xFFF1F2F6, 0xFF1A1D21,
            0xFFF7F8FA, 0xFF1A1D21, 0xFF6B7280,
            0xFFFFFFFF, 0xFF6B7280,
            0xFFECEEF2, 0xFF6B7280,
            null, null, "small");
    /** 青柠 (id "summer", shown as 夏日 before) — same white base dressed with a lime accent and pale green capsules. */
    public static final Preset SUMMER = new Preset("summer",
            0xFF84CC16,
            0xFF84CC16, 0xFF1A2E05,
            0xFFEFF5E3, 0xFF1F2937,
            0xFFFAFCF3, 0xFF1F2937, 0xFF6B7280,
            0xFFFFFFFF, 0xFF6B7280,
            0xFFEBF2DC, 0xFF4D7C0F,
            null, null, "medium");
    /** 典雅 — Claude beige: warm paper panel, sand cards, terracotta accent. */
    public static final Preset ELEGANT = new Preset("elegant",
            0xFFD97757,
            0xFFD97757, 0xFFFFFFFF,
            0xFFEFE8DC, 0xFF3D3929,
            0xFFF5F0E8, 0xFF3D3929, 0xFF8A8377,
            0xFFE8DDD0, 0xFF8A8377,
            0xFFEAE1D3, 0xFF7A7264,
            null, null, "medium");
    /** 黑鸦 — deep black-blue night, gold accent, GitHub-dark surfaces. */
    public static final Preset RAVEN = new Preset("raven",
            0xFFE3B341,
            0xFFE3B341, 0xFF1C1901,
            0xFF1F2630, 0xFFE6EDF3,
            0xFF0D1117, 0xFFE6EDF3, 0xFF8B949E,
            0xFF161B27, 0xFF8B949E,
            0xFF2A313C, 0xFF8B949E,
            null, null, "medium");
    /** 枢纽 — pure black terminal with the orange accent; sharp corners, no softness. */
    public static final Preset HUB = new Preset("hub",
            0xFFFF9000,
            0xFFFF9000, 0xFF1A0E00,
            0xFF111111, 0xFFF5F5F5,
            0xFF0A0A0A, 0xFFFFFFFF, 0xFF9CA3AF,
            0xFF141414, 0xFF9CA3AF,
            0xFF161616, 0xFFD4D4D4,
            null, null, "small");
    /** 活力 — pale pink background, punchy pink accent, everything else stays soft white. */
    public static final Preset VIVID = new Preset("vivid",
            0xFFE85A8A,
            0xFFE85A8A, 0xFFFFFFFF,
            0xFFFBE3EB, 0xFF3D2C35,
            0xFFFDF0F4, 0xFF3D2C35, 0xFF9A7F8C,
            0xFFFFFFFF, 0xFF9A7F8C,
            0xFFF9DCE7, 0xFFB04A70,
            null, null, "medium");
    /** 薄暮 — grey-blue dusk with a violet cast: translucent smoky panel over
     *  the blurred world, lavender accent and bubbles, near-white text. */
    public static final Preset DUSK = new Preset("dusk",
            0xFFA78BFA,
            0xFFA78BFA, 0xFFFFFFFF,
            0xFF333950, 0xFFECEEF4,
            0xFF232633, 0xFFF2F3F7, 0xFF9BA0B5,
            0xFF2B2E3D, 0xFF9BA0B5,
            0xFF313548, 0xFFC9CDE0,
            0.88F, true, "large");
    /** 薄荷 — deep teal-green base worn translucent, mint accent and bubble,
     *  dark teal text on the bright mint surfaces. */
    public static final Preset MINT = new Preset("mint",
            0xFF5EEAD4,
            0xFF5EEAD4, 0xFF082620,
            0xFF1B3B34, 0xFFE7F5F0,
            0xFF0F2622, 0xFFEFFAF6, 0xFF9DB8AF,
            0xFF16332D, 0xFF9DB8AF,
            0xFF1E423A, 0xFFBFE8DC,
            0.9F, true, "large");

    private static final Preset[] COLOR_PRESETS =
            {MINIMAL, SUMMER, ELEGANT, RAVEN, HUB, VIVID, DUSK, MINT};

    /** The colour presets in picker order (the frosted tile is rendered separately). */
    public static Preset[] presets() {
        return COLOR_PRESETS.clone();
    }

    /** Preset by id, or null for the frosted default, look presets and garbage. */
    public static Preset byId(String id) {
        for (Preset preset : COLOR_PRESETS) {
            if (preset.id().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Accent swatch colour for the theme picker: colour presets use their own
     * accent, every other tile (frosted, modern, hand-tuned) shows the shipped
     * blue so an unknown id can never render as black-on-black.
     */
    public static int accentOf(String themeId) {
        Preset preset = byId(themeId);
        return preset != null ? preset.accent() : FROSTED_ACCENT;
    }

    /**
     * The palette one theme-preview card draws with: accent, mini panel
     * background, title-bar strip (the card surface), both bubbles and the
     * corner scale the palette was designed around. Colour presets answer from
     * their own fields; the Default tile (and any unknown id) answers from the
     * factory config, so the default card previews exactly what picking it
     * restores.
     */
    public record Preview(int accent, int panelBg, int card,
                          int ownBubble, int otherBubble, float cornerFactor) {
    }

    public static Preview previewOf(String themeId) {
        Preset preset = byId(themeId);
        if (preset != null) {
            return new Preview(preset.accent(), preset.panelBg(), preset.card(),
                    preset.ownBubble(), preset.otherBubble(), cornerFactor(preset.cornerStyle()));
        }
        AtomChatConfig def = AtomChatConfig.DEFAULT;
        return new Preview(def.accentColor, def.panelBgColor, def.cardColor,
                def.ownBubbleColor, def.otherBubbleColor, cornerFactor(def.cornerStyle));
    }

    /**
     * Whether the panel surface reads as light. Text backing shadows are
     * tuned per surface polarity: on a light panel a whisper of shadow
     * grounds dark glyphs, on a dark panel the same filter needs more alpha
     * to lift white glyphs off the frosted world. Reads the live config's
     * panel background (alpha ignored) as relative luminance — the WCAG sRGB
     * linearisation with the 0.2126/0.7152/0.0722 weights — against a 0.5
     * threshold. Mid-grey (#808080, linear ≈ 0.216) counts as dark: a surface
     * that dark never carries black text anyway.
     */
    public static boolean panelIsLight() {
        return panelIsLight(AtomChatConfig.get());
    }

    /** Luminance test against an explicit config (the unit-test seam). */
    public static boolean panelIsLight(AtomChatConfig config) {
        return relativeLuminance(config.panelBgColor) >= 0.5F;
    }

    /**
     * WCAG relative luminance of an sRGB colour, alpha ignored. One
     * implementation for every polarity question: the panel surface above, and
     * whether content can sit on a filled accent ({@code UiTokens.onAccent}).
     */
    public static float relativeLuminance(int argb) {
        float r = srgbToLinear(((argb >>> 16) & 0xFF) / 255.0F);
        float g = srgbToLinear(((argb >>> 8) & 0xFF) / 255.0F);
        float b = srgbToLinear((argb & 0xFF) / 255.0F);
        return 0.2126F * r + 0.7152F * g + 0.0722F * b;
    }

    /** Whether a colour is light enough to need dark content on top of it. */
    public static boolean colorIsLight(int argb) {
        return relativeLuminance(argb) >= 0.5F;
    }

    private static float srgbToLinear(float c) {
        return c <= 0.04045F ? c / 12.92F : (float) Math.pow((c + 0.055F) / 1.055F, 2.4);
    }

    /**
     * Writes the preset's appearance values into the config. The caller
     * persists. Unknown ids are ignored so a hand-edited themeName can never
     * scramble the settings. The modern look keeps the panel outline — it is
     * the mod's signature, only the surface style changes. The frosted tile
     * is the full factory reset: every colour and look field goes back to its
     * shipped value (see {@link #applyFactoryDefaults}).
     */
    public static void apply(AtomChatConfig config, String themeId) {
        switch (themeId == null ? "" : themeId) {
            case MODERN -> {
                config.panelOpacity = 1.0F;
                config.blurEnabled = false;
                config.panelOutline = true;
                config.cardTint = 1.0F;
                config.cardColor = 0xFF222831;
            }
            case FROSTED -> applyFactoryDefaults(config);
            default -> {
                Preset preset = byId(themeId);
                if (preset == null) {
                    return;
                }
                // One look block, then the palette. Presets without a look
                // vote stay opaque — blur under a solid surface is invisible
                // work. A translucent preset (dusk, mint) writes its own
                // opacity and asks for the blur behind the tint
                // (PanelBackground.tintFor composes the two).
                config.panelOpacity = preset.panelOpacity() != null
                        ? preset.panelOpacity() : 1.0F;
                config.blurEnabled = Boolean.TRUE.equals(preset.blurEnabled());
                config.panelOutline = true;
                config.cardTint = 1.0F;
                preset.applyTo(config);
            }
        }
        config.themeName = themeId;
    }

    /**
     * Restores every look-and-feel field to its factory value: the twelve
     * colour fields, panel opacity, card tint, the outline toggle and blur.
     * The values come from {@link AtomChatConfig#DEFAULT} — the config class's
     * own field initialisers — so there is exactly one source of truth and a
     * shipped-default change updates the reset automatically.
     *
     * <p>Not touched on purpose: the independent knobs ({@code cornerRadius},
     * panel size/scale, wallpapers, everything outside the theme snapshot) and
     * the {@code themeName} stamp, which the caller sets.</p>
     */
    public static void applyFactoryDefaults(AtomChatConfig config) {
        AtomChatConfig def = AtomChatConfig.DEFAULT;
        config.accentColor = def.accentColor;
        config.ownBubbleColor = def.ownBubbleColor;
        config.bubbleTextColor = def.bubbleTextColor;
        config.otherBubbleColor = def.otherBubbleColor;
        config.otherBubbleTextColor = def.otherBubbleTextColor;
        config.panelBgColor = def.panelBgColor;
        config.textPrimaryColor = def.textPrimaryColor;
        config.textSecondaryColor = def.textSecondaryColor;
        config.cardColor = def.cardColor;
        config.panelOutlineColor = def.panelOutlineColor;
        config.secondaryCapsuleBg = def.secondaryCapsuleBg;
        config.secondaryCapsuleText = def.secondaryCapsuleText;
        config.panelOpacity = def.panelOpacity;
        config.blurEnabled = def.blurEnabled;
        config.panelOutline = def.panelOutline;
        config.cardTint = def.cardTint;
    }
}
