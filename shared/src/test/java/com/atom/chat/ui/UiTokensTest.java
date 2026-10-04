package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the continuous corner-radius knob: the 28 reference reproduces
 * the shipped default look, the slider maximum is exactly 1.25x, and 0 — pure
 * square — must hold at factor 0, so every {@link UiTokens#radius(float)}
 * call collapses to a hard zero no matter the base radius.
 */
class UiTokensTest {

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
}
