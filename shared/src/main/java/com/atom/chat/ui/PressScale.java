package com.atom.chat.ui;

import io.github.humbleui.skija.Canvas;

/**
 * Per-control hover/press scale driven by the bounce spring
 * ({@link UiSpring#newBounceSpring()}). One instance per interactive
 * control — never shared — and hit-testing keeps using unscaled
 * coordinates: the scale is draw-only, exactly like the message-entrance
 * slide.
 *
 * <p><b>Pixel budget, not a fixed ratio.</b> The scale is derived from the
 * width of the shape being scaled, so every control's edge travels the same
 * distance in UI pixels no matter how wide the control is:
 * {@link #BUDGET_PX} per side on hover, mirrored inward while pressed. A
 * single ratio — the old control/compact/row tiers of 1.08/1.04/1.0 — moves
 * a 22.5px colour swatch 0.9px per side but a 520px settings card 20.8px, a
 * 23x spread between shapes that should travel alike; one 4px budget
 * restores a single per-side travel across the whole size range. What is
 * uniform is the displacement budget, not a visible overshoot everywhere:
 * wide shapes settle on the first crossing of the target (the analytic
 * overshoot is sub-pixel at that travel — see
 * {@link UiSpring#newBounceSpring()}), so the visible bounce lives on
 * small controls' hovers and on every press release.</p>
 *
 * <p><b>Why small controls ride the 6% cap.</b> A narrow shape cannot spend
 * the full 4px: a 22.5px swatch would need a scale of 1.356 (4px of travel
 * on an 11.25px half-width) to spend the budget, which reads as a twitch,
 * not a bounce. So the per-side travel is capped at {@link #BUDGET_PCT} of
 * the scale: controls narrower than ~133px (where 4px already exceeds 6%)
 * ride the cap, wider ones spend the flat 4px. Reference hover targets:
 * 490px row → 1.01633, 520px card → 1.01538, 255px → 1.03137,
 * 173.33px → 1.04615, and 120/100.5/50/45/22.5px → all exactly 1.06.</p>
 *
 * <p><b>Press mirrors hover.</b> The pressed target is {@code 2 - u}, the
 * point-symmetric twin of the hover target around 1: whatever the edge may
 * travel outward on hover is exactly what it travels inward on press, so
 * both halves of the gesture share one budget.</p>
 *
 * <p>With decorative motion off ({@link Animations#enabled()}) the scale
 * pins to exactly 1.0. The width arrives with every {@link #update} rather
 * than at the factory because layouts recompute per frame — pass the width
 * of the shape being scaled (cell, tab, button), not of the container
 * around it.</p>
 */
public final class PressScale {
    /** Hover/press per-side travel budget in UI px. */
    public static final float BUDGET_PX = 4.0F;
    /** Cap for small controls: per-side travel may not exceed 6% of the scale. */
    public static final float BUDGET_PCT = 0.06F;

    private final SpringAnim spring = UiSpring.newBounceSpring();

    private PressScale() {
        spring.snapTo(1.0F);
    }

    /**
     * One instance per interactive control; the semantic tiers
     * (control/compact/row) are gone, one budget fits all. The control's
     * width is not fixed here — it arrives with every {@link #update} so a
     * relayout takes effect on the next frame.
     */
    public static PressScale bounce() {
        return new PressScale();
    }

    /**
     * Advances toward the state's target. The hover target is
     * {@code u = 1 + min(BUDGET_PX / (widthPx / 2), BUDGET_PCT)} and the
     * pressed target its mirror {@code 2 - u}; with {@code animEnabled}
     * false the target is 1.0 and the spring snaps, so the scale never
     * leaves 1.
     */
    public void update(boolean hover, boolean pressed, float dtMs, boolean animEnabled, float widthPx) {
        float u = 1.0F + Math.min(BUDGET_PX / (widthPx * 0.5F), BUDGET_PCT);
        spring.setTarget(animEnabled ? (pressed ? 2.0F - u : hover ? u : 1.0F) : 1.0F);
        spring.update(dtMs, animEnabled);
    }

    /** Current scale (1 = no transform). */
    public float scale() {
        return spring.value();
    }

    /** The spring's current target — package-private, for contract tests. */
    float target() {
        return spring.target();
    }

    /** True once the spring is back at rest at 1.0 (lets maps drop idle entries). */
    public boolean isResting() {
        return spring.isSettled() && Math.abs(spring.value() - 1.0F) < 1e-4F;
    }

    /**
     * Begins a centered scale: {@code canvas.save()} plus a translate/scale
     * around (cx, cy). The caller draws the control, then calls
     * {@code canvas.restore()} — always, including when the scale is 1.
     */
    public void begin(Canvas canvas, float cx, float cy) {
        canvas.save();
        float s = spring.value();
        if (s != 1.0F) {
            canvas.translate(cx, cy);
            canvas.scale(s, s);
            canvas.translate(-cx, -cy);
        }
    }
}
