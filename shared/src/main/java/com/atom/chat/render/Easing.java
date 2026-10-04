package com.atom.chat.render;

public final class Easing {
    private Easing() {
    }

    public static float linear(float t) {
        return t;
    }

    public static float easeOutQuart(float t) {
        t = 1.0F - t;
        return 1.0F - t * t * t * t;
    }

    public static float easeOutBack(float t) {
        return easeOutBack(t, 1.70158F);
    }

    /**
     * easeOutBack with a tunable overshoot constant: the peak overshoot above
     * 1 is (4/27)*c1^3/(c1+1)^2, so callers can buy a smaller bounce than the
     * standard curve's ~10% (c1 1.70158) — see {@code UiSpring.MESSAGE_EASE_C1}
     * for the restrained variant used by the message entrance.
     */
    public static float easeOutBack(float t, float c1) {
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(t - 1.0F, 3) + c1 * (float) Math.pow(t - 1.0F, 2);
    }

    public static float easeOutCubic(float t) {
        t = 1.0F - t;
        return 1.0F - t * t * t;
    }

    /** Standard ease-in-out for elements gliding between positions on screen. */
    public static float easeInOutCubic(float t) {
        return t < 0.5F
                ? 4.0F * t * t * t
                : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0F) / 2.0F;
    }

    /**
     * Gentler than easeOutCubic: the eye needs to see an opacity ramp actually
     * ramping, and cubic spends ~88% of its travel in the first half of the
     * duration — which is why a cubic-driven fade reads as "just a slide".
     */
    public static float easeOutQuad(float t) {
        t = 1.0F - t;
        return 1.0F - t * t;
    }

    /** Exponential decel (Tuui's EaseOutQuart): long smooth tail for scrolling. */
    public static float easeOutExpo(float t) {
        return t >= 1.0F ? 1.0F : (float) (1.0 - Math.pow(2.0, -10.0 * t));
    }
}
