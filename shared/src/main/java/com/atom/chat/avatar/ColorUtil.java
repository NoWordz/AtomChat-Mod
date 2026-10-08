package com.atom.chat.avatar;

/**
 * RGB ↔ HSV conversions, hex formatting and the contrast walk for the colour
 * picker and the float-surface glyphs. Pure math.
 *
 * <p>H, S, V are all 0..1. H wraps (0 and 1 are both red); S and V clamp.
 */
public final class ColorUtil {
    /**
     * The deepest value the contrast walk is allowed to reach. Below this a
     * colour stops reading as its own hue and starts reading as black, which
     * is exactly the "silently turned neutral" outcome the walk exists to
     * avoid — so the value axis is bounded here instead of at 0.
     */
    public static final float MIN_HUE_VALUE = 0.15F;

    private ColorUtil() {
    }

    /** ARGB (opaque) → {@code {h, s, v}}. */
    public static float[] rgbToHsv(int argb) {
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d <= 0.0F) {
            h = 0.0F;
        } else if (max == r) {
            h = ((g - b) / d) % 6.0F;
        } else if (max == g) {
            h = (b - r) / d + 2.0F;
        } else {
            h = (r - g) / d + 4.0F;
        }
        h /= 6.0F;
        if (h < 0.0F) {
            h += 1.0F;
        }
        float s = max <= 0.0F ? 0.0F : d / max;
        return new float[]{h, s, max};
    }

    /** {@code h, s, v} (0..1) → opaque ARGB. */
    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = clamp01(s);
        v = clamp01(v);
        float r;
        float g;
        float b;
        float i = h * 6.0F;
        int sector = (int) Math.floor(i);
        float f = i - sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - s * f);
        float t = v * (1.0F - s * (1.0F - f));
        switch (sector % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        int ir = Math.round(clamp01(r) * 255.0F);
        int ig = Math.round(clamp01(g) * 255.0F);
        int ib = Math.round(clamp01(b) * 255.0F);
        return 0xFF000000 | (ir << 16) | (ig << 8) | ib;
    }

    /** {@code #RRGGBB} of the RGB part (alpha ignored). */
    public static String formatHex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    /**
     * WCAG contrast ratio (1..21) of two colours, alpha ignored — the same
     * formula the theme's polarity tests use, kept here so a glyph's
     * legibility can be settled without a surface to draw on.
     */
    public static float contrastRatio(int a, int b) {
        float la = com.atom.chat.theme.ThemeService.relativeLuminance(a);
        float lb = com.atom.chat.theme.ThemeService.relativeLuminance(b);
        return (Math.max(la, lb) + 0.05F) / (Math.min(la, lb) + 0.05F);
    }

    /**
     * The accent moved along its own value axis until it reads on
     * {@code surface} at {@code minContrast}, with its hue and saturation
     * untouched. The success glyph's rule: the tick should be the theme's
     * accent, and an accent that has been pushed towards white or black
     * instead is no longer the theme's accent.
     *
     * <p>Three behaviours, all of them deliberate:</p>
     * <ul>
     *   <li><b>Already readable → the input, byte for byte.</b> No re-derivation,
     *       so a theme whose accent already contrasts is drawn exactly as
     *       configured.</li>
     *   <li><b>Not readable → walk V only.</b> The direction follows the
     *       surface's polarity: a light surface deepens the accent, a dark one
     *       lifts it. This is the direction that can work — against a light
     *       surface (relative luminance ≥ 0.5) a passing colour must be darker
     *       than the surface, and against a dark one it must be lighter. S and
     *       H never move, so a mint accent stays mint at whatever brightness
     *       the surface demands; the walk stops at the smallest move that
     *       passes, and alpha rides through unchanged.</li>
     *   <li><b>Unreachable → the hue-safe endpoint, never a neutral.</b> Some
     *       hues cannot reach a given contrast against some surfaces by any
     *       value change (pure blue on a near-black float tops out near 2:1 —
     *       a blue channel's luminance ceiling is low). The walk then stops at
     *       the far end of the axis: the accent itself when it already sits at
     *       that end, otherwise {@link #MIN_HUE_VALUE} when deepening and full
     *       value when lifting. The caller gets a best effort that is still a
     *       colour, not a black-or-white fallback that throws the theme away
     *       silently.</li>
     * </ul>
     */
    public static int readableAccent(int accent, int surface, float minContrast) {
        float target = Math.max(1.0F, minContrast);
        if (contrastRatio(accent, surface) >= target) {
            return accent;
        }
        float[] hsv = rgbToHsv(accent);
        int floor = Math.max(1, Math.round(MIN_HUE_VALUE * 255.0F));
        int start = Math.round(hsv[2] * 255.0F);
        boolean deepen = com.atom.chat.theme.ThemeService.colorIsLight(surface);
        if (deepen ? start <= floor : start >= 255) {
            // The accent already sits on the endpoint of its own axis: there is
            // no room left for a hue-preserving move.
            return accent;
        }
        int value = deepen
                ? deepestThatReads(hsv, start, floor, surface, target)
                : brightestThatReads(hsv, start, surface, target);
        int rgb = hsvToRgb(hsv[0], hsv[1], value / 255.0F);
        return (accent & 0xFF000000) | (rgb & 0x00FFFFFF);
    }

    /**
     * Largest value in {@code [floor, start]} whose colour clears the target.
     * Contrast against a light surface rises monotonically as the value falls
     * (a passing colour is always darker than the surface), so the boundary is
     * binary-searchable. {@code floor} is the answer when nothing clears it.
     */
    private static int deepestThatReads(float[] hsv, int start, int floor, int surface, float target) {
        int best = -1;
        int lo = floor;
        int hi = start;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (reads(hsv, mid, surface, target)) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return best < 0 ? floor : best;
    }

    /**
     * Smallest value in {@code [start, 255]} whose colour clears the target;
     * the mirror of {@link #deepestThatReads} — contrast against a dark
     * surface rises as the value climbs, and full value is the answer when
     * nothing clears it.
     */
    private static int brightestThatReads(float[] hsv, int start, int surface, float target) {
        int best = -1;
        int lo = start;
        int hi = 255;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (reads(hsv, mid, surface, target)) {
                best = mid;
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return best < 0 ? 255 : best;
    }

    /** Whether the hue at {@code valueByte} clears {@code target} on {@code surface}. */
    private static boolean reads(float[] hsv, int valueByte, int surface, float target) {
        return contrastRatio(hsvToRgb(hsv[0], hsv[1], valueByte / 255.0F), surface) >= target;
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }
}
