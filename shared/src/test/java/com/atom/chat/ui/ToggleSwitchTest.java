package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import io.github.humbleui.skija.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the switch's colour math, on the package-private pure seams
 * extracted from the render path ({@link ToggleSwitch#trackOff(boolean)},
 * {@link ToggleSwitch#onRim(float, float)}, {@link ToggleSwitch#dim(int, float)}):
 * the on-rim is the functional alpha-110 stroke ({@link UiTokens#rim(int)},
 * the one outline exempt from the hairline language) faded in with the
 * accent, the off track flips polarity against the card surface (both
 * branches neutral), and the disabled state dims alpha only, to 40%.
 */
class ToggleSwitchTest {

    // --- off track: polarity pair ---

    @Test
    void offTrackFollowsCardPolarity() {
        int dark = ToggleSwitch.trackOff(false);
        int light = ToggleSwitch.trackOff(true);
        assertEquals(0xFF3A3A3E, dark,
                "dark card: solid dim grey (255, 58, 58, 62)");
        assertEquals(0x6E1E1E22, light,
                "light card: near-black at 110 alpha (110, 30, 30, 34)");
    }

    // --- on rim: the functional alpha-110 stroke, faded with progress ---

    /** The switch rim resolved through the explicit-base seam (dark card → white). */
    private static final int RIM = UiTokens.rim(0xFF16191F);

    @Test
    void onRimIsTheSwitchRimAtFullProgress() {
        assertEquals(RIM, ToggleSwitch.onRim(RIM, 1.0F, 1.0F),
                "the rim at full progress is exactly the switch rim token");
    }

    @Test
    void onRimFadesInWithProgressAlphaOnly() {
        float p = 0.5F;
        int half = ToggleSwitch.onRim(RIM, p, 1.0F);
        int expectedAlpha = Math.round(((RIM >>> 24) & 0xFF) * p);
        assertEquals(expectedAlpha, (half >>> 24) & 0xFF,
                "alpha fades with the on progress");
        assertEquals(RIM & 0x00FFFFFF, half & 0x00FFFFFF,
                "the fade is alpha-only, RGB untouched");
    }

    @Test
    void onRimCombinesProgressWithTheDisabledOpacity() {
        // p and op multiply: 0.5 progress at 40% disabled opacity leaves 20%
        // of the rim alpha.
        int d = ToggleSwitch.onRim(RIM, 0.5F, ToggleSwitch.DISABLED_OPACITY);
        assertEquals(Math.round(((RIM >>> 24) & 0xFF) * 0.2F), (d >>> 24) & 0xFF,
                "progress and opacity compose multiplicatively");
    }

    // --- disabled: alpha-only dim to 40% ---

    @Test
    void disabledDimsAlphaOnly() {
        int c = Color.makeARGB(200, 10, 20, 30);
        int d = ToggleSwitch.dim(c, ToggleSwitch.DISABLED_OPACITY);
        assertEquals(80, (d >>> 24) & 0xFF, "alpha scales to 40% (200 -> 80)");
        assertEquals(0x0A141E, d & 0x00FFFFFF, "RGB untouched by the dim");
        // Rounding follows Math.round, not truncation.
        int odd = ToggleSwitch.dim(Color.makeARGB(101, 0, 0, 0), ToggleSwitch.DISABLED_OPACITY);
        assertEquals(40, (odd >>> 24) & 0xFF, "101 * 0.4 rounds to 40");
    }

    @Test
    void disabledOpacityPin() {
        // The whole-switch opacity contract: whatever colour enters the dim,
        // DISABLED_OPACITY leaves it at 40% of its own alpha — far below any
        // enabled state, so "unavailable" stays legible at a glance.
        assertEquals(0.4F, ToggleSwitch.DISABLED_OPACITY, 0.0F,
                "the disabled opacity is the tuned 40%");
        for (int a : new int[]{255, 110, 45}) {
            int d = ToggleSwitch.dim(Color.makeARGB(a, 7, 8, 9), ToggleSwitch.DISABLED_OPACITY);
            assertTrue(Math.abs(((d >>> 24) & 0xFF) - a * 0.4F) <= 0.5F,
                    "alpha " + a + " dims to 40% within rounding");
        }
    }
}
