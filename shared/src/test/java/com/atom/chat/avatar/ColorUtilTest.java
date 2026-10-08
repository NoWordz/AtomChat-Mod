package com.atom.chat.avatar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorUtilTest {
    private static final float EPS = 1e-3F;

    @Test
    void hsvToRgbPrimaries() {
        assertEquals(0xFFFF0000, ColorUtil.hsvToRgb(0.0F, 1.0F, 1.0F));
        assertEquals(0xFF00FF00, ColorUtil.hsvToRgb(1.0F / 3.0F, 1.0F, 1.0F));
        assertEquals(0xFF0000FF, ColorUtil.hsvToRgb(2.0F / 3.0F, 1.0F, 1.0F));
        assertEquals(0xFF000000, ColorUtil.hsvToRgb(0.7F, 1.0F, 0.0F));
        assertEquals(0xFFFFFFFF, ColorUtil.hsvToRgb(0.7F, 0.0F, 1.0F));
    }

    @Test
    void rgbToHsvRoundTrips() {
        int[] samples = {
                0xFF1E90FF, 0xFF343A44, 0xFFFFFFFF, 0xFF000000,
                0xFF2ECC71, 0xFFE91E63, 0xFF4A90E2, 0xFFE67E22
        };
        for (int argb : samples) {
            float[] hsv = ColorUtil.rgbToHsv(argb);
            int back = ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]);
            int dr = Math.abs(((back >> 16) & 0xFF) - ((argb >> 16) & 0xFF));
            int dg = Math.abs(((back >> 8) & 0xFF) - ((argb >> 8) & 0xFF));
            int db = Math.abs((back & 0xFF) - (argb & 0xFF));
            assertTrue(dr <= 1 && dg <= 1 && db <= 1,
                    "round trip drift on " + ColorUtil.formatHex(argb));
        }
    }

    @Test
    void hueWraps() {
        int a = ColorUtil.hsvToRgb(0.0F, 1.0F, 1.0F);
        int b = ColorUtil.hsvToRgb(1.0F, 1.0F, 1.0F);
        assertEquals(a, b);
    }

    @Test
    void formatHex() {
        assertEquals("#1E90FF", ColorUtil.formatHex(0xFF1E90FF));
        assertEquals("#343A44", ColorUtil.formatHex(0xFF343A44));
    }

    // --- readableAccent: the success glyph keeps its hue and gains contrast ---

    /** The shipped dark float surface (a default-theme cardCutout). */
    private static final int DARK_FLOAT = 0xFF16191F;
    /** A pale float surface (a light theme's near-white card). */
    private static final int LIGHT_FLOAT = 0xFFF4F5F7;
    /** WCAG's non-text contrast floor — what the toast's glyph asks for. */
    private static final float MIN = 3.0F;

    @Test
    void contrastRatioMatchesTheWcagExtremes() {
        assertEquals(21.0F, ColorUtil.contrastRatio(0xFFFFFFFF, 0xFF000000), 0.01F,
                "white on black is the 21:1 end of the scale");
        assertEquals(1.0F, ColorUtil.contrastRatio(DARK_FLOAT, DARK_FLOAT), 0.001F,
                "a colour against itself is 1:1 — no contrast to gain");
    }

    @Test
    void readableAccentLeavesAReadableAccentUntouched() {
        int accent = 0xFF6FD08C;
        assertTrue(ColorUtil.contrastRatio(accent, DARK_FLOAT) > MIN,
                "the sample must start out readable");
        assertEquals(accent, ColorUtil.readableAccent(accent, DARK_FLOAT, MIN),
                "a readable accent is returned byte for byte, not re-derived");
    }

    @Test
    void readableAccentDeepensOnALightSurface() {
        // Pale gold on a pale float: unreadable until the accent is walked down
        // the value axis, which is the direction a light surface asks for.
        int accent = 0xFFF6E7A0;
        int out = ColorUtil.readableAccent(accent, LIGHT_FLOAT, MIN);
        float[] before = ColorUtil.rgbToHsv(accent);
        float[] after = ColorUtil.rgbToHsv(out);
        assertTrue(ColorUtil.contrastRatio(out, LIGHT_FLOAT) >= MIN,
                "the result clears the floor (" + ColorUtil.contrastRatio(out, LIGHT_FLOAT) + ")");
        assertTrue(after[2] < before[2],
                "a light float pushes the value down, never up: " + before[2] + " -> " + after[2]);
        assertEquals(before[0], after[0], 0.02F, "hue survives the walk");
        assertEquals(before[1], after[1], 0.02F, "saturation survives the walk");
        assertEquals(0xFF, out >>> 24, "the accent was opaque and stays opaque");
    }

    @Test
    void readableAccentLiftsOnADarkSurface() {
        // Slate on the near-black float: the value has to come up, not down.
        int accent = 0xFF2C3E50;
        int out = ColorUtil.readableAccent(accent, DARK_FLOAT, MIN);
        float[] before = ColorUtil.rgbToHsv(accent);
        float[] after = ColorUtil.rgbToHsv(out);
        assertTrue(ColorUtil.contrastRatio(out, DARK_FLOAT) >= MIN,
                "the result clears the floor (" + ColorUtil.contrastRatio(out, DARK_FLOAT) + ")");
        assertTrue(after[2] > before[2],
                "a dark float lifts the value, never pushes it down: " + before[2] + " -> " + after[2]);
        assertEquals(before[0], after[0], 0.02F, "hue survives the lift");
        assertEquals(before[1], after[1], 0.02F, "saturation survives the lift");
    }

    @Test
    void readableAccentKeepsHueAndSaturationOnBothPolarities() {
        int[] accents = {0xFFE3B341, 0xFF3F8AE0, 0xFF84CC16, 0xFFE91E63, 0xFF2C3E50, 0xFFE5E7EB};
        for (int accent : accents) {
            for (int surface : new int[]{DARK_FLOAT, LIGHT_FLOAT}) {
                int out = ColorUtil.readableAccent(accent, surface, MIN);
                float[] before = ColorUtil.rgbToHsv(accent);
                float[] after = ColorUtil.rgbToHsv(out);
                String what = ColorUtil.formatHex(accent) + " on " + ColorUtil.formatHex(surface);
                assertEquals(before[0], after[0], 0.03F, "hue held: " + what);
                assertEquals(before[1], after[1], 0.03F, "saturation held: " + what);
                assertTrue(ColorUtil.contrastRatio(out, surface)
                                >= Math.min(MIN, ColorUtil.contrastRatio(accent, surface)) - 1e-4F,
                        "the walk never trades contrast away: " + what);
            }
        }
    }

    @Test
    void readableAccentStopsAtTheHueWhenTheTargetIsUnreachable() {
        // Pure blue's brightest form still only reaches ~2.0:1 on the near-black
        // float, so no value along the blue hue can reach 3:1. The endpoint must
        // be the hue itself — never a silent black or white.
        int blue = 0xFF0000FF;
        assertTrue(ColorUtil.contrastRatio(blue, DARK_FLOAT) < MIN,
                "the sample really is unreachable (" + ColorUtil.contrastRatio(blue, DARK_FLOAT) + ")");
        int out = ColorUtil.readableAccent(blue, DARK_FLOAT, MIN);
        assertEquals(blue, out, "the endpoint is the accent itself, not a neutral fallback");
        assertEquals(0xFF, out & 0xFF, "still blue");
        assertEquals(0, (out >>> 16) & 0xFF, "no white flip: red stays empty");
        assertEquals(0, (out >>> 8) & 0xFF, "no white flip: green stays empty");
    }

    @Test
    void readableAccentFloorsAtTheHueSafeValueWhenEvenTheDeepestHueMisses() {
        // An impossible floor (20:1) on the pale float: the walk runs to the
        // deepest hue-safe value. It must stop there with its value and hue
        // intact instead of collapsing to black.
        int accent = 0xFFF6E7A0;
        int out = ColorUtil.readableAccent(accent, LIGHT_FLOAT, 20.0F);
        float[] hsv = ColorUtil.rgbToHsv(accent);
        int expected = ColorUtil.hsvToRgb(hsv[0], hsv[1], ColorUtil.MIN_HUE_VALUE);
        assertTrue(channelDrift(expected, out) <= 1,
                "the endpoint is the hue-safe value floor: " + ColorUtil.formatHex(expected)
                        + " vs " + ColorUtil.formatHex(out));
        assertTrue(((out >>> 16) & 0xFF) + ((out >>> 8) & 0xFF) + (out & 0xFF) > 0,
                "the floor is not black — the hue is still in there");
        assertEquals(hsv[1], ColorUtil.rgbToHsv(out)[1], 0.05F, "saturation still present");
        assertTrue(ColorUtil.contrastRatio(out, LIGHT_FLOAT) < 20.0F,
                "an unreachable target is reported honestly, not faked by going black");
    }

    @Test
    void readableAccentTreatsATrivialMinimumAsAlreadyMet() {
        int accent = 0xFF2C3E50;
        assertEquals(accent, ColorUtil.readableAccent(accent, DARK_FLOAT, 1.0F),
                "1:1 is the bottom of the scale — every colour meets it");
        assertEquals(accent, ColorUtil.readableAccent(accent, DARK_FLOAT, 0.0F),
                "a minimum below 1 clamps instead of looping the search");
    }

    @Test
    void readableAccentKeepsTheAccentsAlpha() {
        int accent = 0x80F6E7A0;
        assertEquals(0x80, ColorUtil.readableAccent(accent, LIGHT_FLOAT, MIN) >>> 24,
                "the walk moves the RGB value only; alpha rides through");
    }

    /** Largest per-channel difference between two RGB colours. */
    private static int channelDrift(int a, int b) {
        return Math.max(Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF)),
                Math.max(Math.abs(((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF)),
                        Math.abs((a & 0xFF) - (b & 0xFF))));
    }
}
