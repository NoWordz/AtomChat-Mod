package com.atom.chat.ui;

import io.github.humbleui.skija.Canvas;

/**
 * Per-control hover/press scale driven by the bounce spring
 * ({@link UiSpring#newBounceSpring()}). One instance per interactive
 * control — never shared — and hit-testing keeps using unscaled
 * coordinates: the scale is draw-only, exactly like the message-entrance
 * slide.
 *
 * <p>Controls (switches, grid cells, icons, swatches) breathe both ways:
 * 1.08 on hover, 0.92 while pressed, springing back on release — sized so
 * the bounce actually reads at arm's length: on a 40px icon the press dip
 * is ~3px per side and the release overshoot another ~2px. Rows
 * (settings/message lines) press only at 0.95 — scaling a whole row on
 * hover reads as jitter, so their hover target stays 1.0.</p>
 *
 * <p>With decorative motion off ({@link Animations#enabled()}) the scale
 * pins to exactly 1.0.</p>
 */
public final class PressScale {
    private final SpringAnim spring = UiSpring.newBounceSpring();
    private final float hoverTarget;
    private final float pressTarget;

    private PressScale(float hoverTarget, float pressTarget) {
        this.hoverTarget = hoverTarget;
        this.pressTarget = pressTarget;
        spring.snapTo(1.0F);
    }

    /** Controls: 1.08 on hover, 0.92 while pressed. */
    public static PressScale control() {
        return new PressScale(1.08F, 0.92F);
    }

    /**
     * Controls packed against a container edge or against each other — the
     * bottom bar's capsules, the composer buttons. The full 1.08 would push a
     * capsule past the bar it sits in and close the gap to its neighbour, so
     * the hover lift is trimmed to a size that stays inside its own cell. The
     * dip keeps most of the control value: the press is the moment the user is
     * looking straight at the control, and it shrinks the shape rather than
     * growing it, so it cannot overflow anything.
     */
    public static PressScale compact() {
        return new PressScale(1.04F, 0.95F);
    }

    /** Rows: press-only 0.95, hover does not scale. */
    public static PressScale row() {
        return new PressScale(1.0F, 0.95F);
    }

    /**
     * Advances toward the state's target. With {@code animEnabled} false the
     * target is 1.0 and the spring snaps, so the scale never leaves 1.
     */
    public void update(boolean hover, boolean pressed, float dtMs, boolean animEnabled) {
        spring.setTarget(animEnabled ? (pressed ? pressTarget : hover ? hoverTarget : 1.0F) : 1.0F);
        spring.update(dtMs, animEnabled);
    }

    /** Current scale (1 = no transform). */
    public float scale() {
        return spring.value();
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
