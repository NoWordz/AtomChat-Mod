package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the continuous corner-radius knob: the 28 reference reproduces
 * the shipped default look, the slider maximum is exactly 1.25x, and 0 — pure
 * square — must hold at factor 0, so every {@link UiTokens#radius(float)}
 * call collapses to a hard zero no matter the base radius.
 *
 * <p>Also guards the contrast-derived feedback colours: the hover wash flip
 * ({@link UiTokens#cardHover(int, int, float)}) and the three-tier outline
 * hierarchy ({@link UiTokens#outlineColor(int, int, int)}), both on a dark
 * and a light surface.</p>
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

    // --- cardHover: contrast-derived hover wash ---

    @Test
    void hoverKeepsAccentWhereContrastAllows() {
        // The shipped default pair: blue accent over the shipped dark panel.
        int c = UiTokens.cardHover(0xFF4A90E2, DARK_BASE, 1.0F);
        assertEquals(0x4A90E2, c & 0x00FFFFFF, "the accent RGB survives");
        assertEquals(45, c >>> 24, "alpha stays at the classic 45 when the accent contrasts");
    }

    @Test
    void hoverFlipsToDarkWashOnLightSurface() {
        // A pale accent over a pale card was white-on-white invisible.
        int c = UiTokens.cardHover(0xFFFFFFFF, LIGHT_BASE, 1.0F);
        assertEquals(0x000000, c & 0x00FFFFFF, "the wash flips to black");
        assertEquals(60, c >>> 24, "the flipped wash rises to 60 so it still reads");
    }

    @Test
    void hoverFlipsToLightWashOnDarkSurface() {
        // A near-black accent over the near-black panel is equally invisible.
        int c = UiTokens.cardHover(0xFF101418, DARK_BASE, 1.0F);
        assertEquals(0xFFFFFF, c & 0x00FFFFFF, "the wash flips to white");
        assertEquals(60, c >>> 24);
    }

    // --- outlineColor: the three-tier border hierarchy ---

    @Test
    void outlineTiersFallStrictlyOnDarkSurface() {
        int l1 = UiTokens.outlineColor(1, 0xFFFFFFFF, DARK_BASE);
        int l2 = UiTokens.outlineColor(2, 0xFFFFFFFF, DARK_BASE);
        int l3 = UiTokens.outlineColor(3, 0xFFFFFFFF, DARK_BASE);
        assertEquals(0xFFFFFF, l1 & 0x00FFFFFF,
                "a white outline contrasts with the dark surface and stays white");
        assertTrue((l1 >>> 24) > (l2 >>> 24), "tier 1 must be stronger than tier 2");
        assertTrue((l2 >>> 24) > (l3 >>> 24), "tier 2 must be stronger than tier 3");
    }

    @Test
    void outlineTiersFallStrictlyOnLightSurface() {
        int l1 = UiTokens.outlineColor(1, 0xFFFFFFFF, LIGHT_BASE);
        int l2 = UiTokens.outlineColor(2, 0xFFFFFFFF, LIGHT_BASE);
        int l3 = UiTokens.outlineColor(3, 0xFFFFFFFF, LIGHT_BASE);
        assertEquals(0x000000, l1 & 0x00FFFFFF,
                "a white outline on a light surface flips to black");
        assertTrue((l1 >>> 24) > (l2 >>> 24), "tier 1 must be stronger than tier 2");
        assertTrue((l2 >>> 24) > (l3 >>> 24), "tier 2 must be stronger than tier 3");
    }

    @Test
    void outlineTiersArePairwiseDistinctOnBothSurfaces() {
        for (int base : new int[]{DARK_BASE, LIGHT_BASE}) {
            int l1 = UiTokens.outlineColor(1, 0xFFFFFFFF, base);
            int l2 = UiTokens.outlineColor(2, 0xFFFFFFFF, base);
            int l3 = UiTokens.outlineColor(3, 0xFFFFFFFF, base);
            assertTrue(l1 != l2, "tiers 1 and 2 differ (base 0x" + Integer.toHexString(base) + ")");
            assertTrue(l2 != l3, "tiers 2 and 3 differ");
            assertTrue(l1 != l3, "tiers 1 and 3 differ");
        }
    }

    @Test
    void outlineKeepsHalfTransparentOutlineAtItsOwnAlpha() {
        // A user-tuned translucent outline keeps its alpha as the tier-1
        // baseline instead of being forced opaque.
        int l1 = UiTokens.outlineColor(1, 0x80FFFFFF, DARK_BASE);
        assertEquals(0x80, l1 >>> 24);
        assertEquals(0xFFFFFF, l1 & 0x00FFFFFF);
    }

    @Test
    void outlineRejectsUnknownLevels() {
        assertThrows(IllegalArgumentException.class,
                () -> UiTokens.outlineColor(0, 0xFFFFFFFF, DARK_BASE));
        assertThrows(IllegalArgumentException.class,
                () -> UiTokens.outlineColor(4, 0xFFFFFFFF, DARK_BASE));
    }

    // --- layout baselines ---

    /**
     * Pins the row clip inset at the shipped 8px. The clearance reasoning in
     * the row pages (settings rows, conversation list, profile info rows) and
     * the {@code ROW_CLIP_INSET} javadoc budget note both quote 8px against
     * the ~5.24px release peak, so a silent retune of s(6.4) would leave the
     * documented budget arithmetic false without failing anything. Same pin
     * pattern as the INPUT_ROW_PAD 5px baseline in UiLayoutTest.
     */
    @Test
    void rowClipInsetStaysAtTheShippedEightPx() {
        assertEquals(8.0F, UiTokens.ROW_CLIP_INSET, 0.01F,
                "ROW_CLIP_INSET is the shipped 8px (s(6.4) at the default scale)");
    }
}
