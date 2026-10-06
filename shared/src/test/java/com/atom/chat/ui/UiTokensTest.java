package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the continuous corner-radius knob: the 28 reference reproduces
 * the shipped default look, the slider maximum is exactly 1.25x, and 0 — pure
 * square — must hold at factor 0, so every {@link UiTokens#radius(float)}
 * call collapses to a hard zero no matter the base radius.
 *
 * <p>Also guards the colour-derivation language on both surface
 * polarities: the accent-dyed, alpha-constant hover wash
 * ({@link UiTokens#cardHover(int, int, float)}) and the three stroke pairs —
 * {@link UiTokens#hairline(int)}, {@link UiTokens#rim(int)} and
 * {@link UiTokens#trackRest(int)}.</p>
 */
class UiTokensTest {

    /** The shipped default panel surface: near-black blue-grey. */
    private static final int DARK_BASE = 0xFF16191F;
    /** A light card surface (pale theme): the white-wash blind spot. */
    private static final int LIGHT_BASE = 0xFFF4F5F7;

    @Test
    void zeroRadiusIsSquareEverywhere() {
        assertEquals(0.0F, UiTokens.radiusFactor(0f), 0.0F);
        // The full radius path collapses to a hard zero for every base the UI
        // draws with (chrome 18, cards 12, pills 8...).
        for (float base : new float[]{3f, 8f, 12f, 18f, 28f}) {
            assertEquals(0.0F, UiTokens.s(base) * UiTokens.radiusFactor(0f), 0.0F,
                    "radius(" + base + ") must be 0 at cornerRadius 0");
        }
    }

    @Test
    void referenceRadiusIsTheShippedLook() {
        assertEquals(1.0F, UiTokens.radiusFactor(28f), 1e-6F,
                "28 is the shipped default (the old large)");
        assertEquals(UiTokens.s(12), UiTokens.s(12) * UiTokens.radiusFactor(28f), 1e-4F);
    }

    @Test
    void sliderMaximumIsOnePointTwoFive() {
        assertEquals(1.25F, UiTokens.radiusFactor(UiTokens.s(28)), 1e-6F,
                "s(28) is the slider maximum");
    }

    @Test
    void outOfRangeValuesClamp() {
        assertTrue(UiTokens.radiusFactor(-5f) >= 0.0F, "negative clamps to square");
        assertEquals(0.0F, UiTokens.radiusFactor(-5f), 0.0F, "negative clamps to exactly 0");
        assertEquals(1.25F, UiTokens.radiusFactor(999f), 1e-6F, "oversized clamps to the maximum");
    }

    // --- cardHover: accent-dyed, polarity-adaptive, constant-alpha wash ---

    @Test
    void hoverDeepensTheAccentOnALightSurface() {
        // Summer's lime accent over a pale card: every channel drops (the
        // wash visibly darkens, lime heading towards olive), and green stays
        // clearly the dominant channel — the hue the user picked survives
        // the deepening instead of collapsing into neutral grey.
        int accent = 0xFF84CC16;
        int c = UiTokens.cardHover(accent, LIGHT_BASE, 1.0F);
        int r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF;
        assertTrue(r < ((accent >>> 16) & 0xFF)
                && g < ((accent >>> 8) & 0xFF)
                && b < (accent & 0xFF),
                "a light surface deepens every accent channel");
        assertTrue(g - Math.max(r, b) >= 20,
                "the hue survives: green stays clearly dominant (olive, not grey)");
        assertEquals(55, c >>> 24, "alpha is the constant 55 at full weight");
    }

    @Test
    void hoverLiftsTheAccentOnADarkSurface() {
        // Raven's gold accent over the shipped dark panel: every channel
        // rises (the wash visibly brightens) and the gold ordering
        // red > green > blue survives the lift.
        int accent = 0xFFE3B341;
        int c = UiTokens.cardHover(accent, DARK_BASE, 1.0F);
        int r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF;
        assertTrue(r > ((accent >>> 16) & 0xFF)
                && g > ((accent >>> 8) & 0xFF)
                && b > (accent & 0xFF),
                "a dark surface lifts every accent channel");
        assertTrue(r > g && g > b,
                "the hue survives: gold stays red > green > blue");
        assertEquals(55, c >>> 24, "alpha is the constant 55 at full weight");
    }

    @Test
    void hoverAlphaIsIdenticalAcrossThemes() {
        // The explicit user requirement from the v0.2.16 field reports:
        // feedback strength must not depend on the theme's polarity. The
        // old language also flipped low-contrast washes to plain black or
        // white, which read as a neutral smudge on light themes — the RGB
        // may follow the polarity, the alpha may not.
        int light = UiTokens.cardHover(0xFF4A90E2, LIGHT_BASE, 1.0F);
        int dark = UiTokens.cardHover(0xFF4A90E2, DARK_BASE, 1.0F);
        assertEquals(light >>> 24, dark >>> 24,
                "same weight, same alpha, whatever the surface polarity");
    }

    @Test
    void hoverWeightScalesAlphaOnly() {
        int full = UiTokens.cardHover(0xFF4A90E2, DARK_BASE, 1.0F);
        int half = UiTokens.cardHover(0xFF4A90E2, DARK_BASE, 0.5F);
        assertEquals(Math.round(55.0F * 0.5F), half >>> 24,
                "weight scales the constant 55 linearly");
        assertEquals(full & 0x00FFFFFF, half & 0x00FFFFFF,
                "weight is an alpha multiplier only, RGB untouched");
        assertEquals(UiTokens.cardHover(0xFF4A90E2, DARK_BASE, 1.0F),
                UiTokens.cardHover(0xFF4A90E2, DARK_BASE, 9.0F),
                "weight clamps at 1");
        assertEquals(0, UiTokens.cardHover(0xFF4A90E2, DARK_BASE, -1.0F) >>> 24,
                "weight clamps at 0");
    }

    // --- hairline / rim / trackRest: the polarity pairs, alphas pinned ---

    @Test
    void hairlineIsPolarityAdaptiveAtAlpha42() {
        assertEquals(0x2A000000, UiTokens.hairline(LIGHT_BASE),
                "light surface: black hairline at the hairline alpha 42");
        assertEquals(0x2AFFFFFF, UiTokens.hairline(DARK_BASE),
                "dark surface: white hairline at the same 42 — one strength on both themes");
    }

    @Test
    void rimIsPolarityAdaptiveAtAlpha110() {
        assertEquals(0x6E000000, UiTokens.rim(LIGHT_BASE),
                "light surface: black switch rim at the functional alpha 110");
        assertEquals(0x6EFFFFFF, UiTokens.rim(DARK_BASE),
                "dark surface: white switch rim at the same 110");
    }

    @Test
    void trackRestIsPolarityAdaptiveAtAlpha70() {
        assertEquals(0x461E1E22, UiTokens.trackRest(LIGHT_BASE),
                "light surface: deep grey (30,30,34) rest at 70");
        assertEquals(0x46FFFFFF, UiTokens.trackRest(DARK_BASE),
                "dark surface: white rest at the same 70");
    }

    // --- layout baselines ---

    /**
     * Pins the row clip inset at the shipped 8px. The clearance reasoning in
     * the row pages (settings rows, conversation list, profile info rows) and
     * the {@code ROW_CLIP_INSET} javadoc budget note both quote 8px against
     * the ~5.24px release peak, so a silent retune of s(6.4) would leave the
     * documented budget arithmetic false without failing anything. Same pin
     * pattern as the INPUT_ROW_PAD 10px baseline in UiLayoutTest.
     */
    @Test
    void rowClipInsetStaysAtTheShippedEightPx() {
        assertEquals(8.0F, UiTokens.ROW_CLIP_INSET, 0.01F,
                "ROW_CLIP_INSET is the shipped 8px (s(6.4) at the default scale)");
    }
}
