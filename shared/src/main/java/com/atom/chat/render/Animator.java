package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;

/**
 * Simple tween animator. Ported from Tuui's SmoothAnimator concept.
 */
public class Animator {
    @FunctionalInterface
    public interface Easing {
        float apply(float t);
    }

    private float start;
    private float end;
    private float lastEnd = Float.NaN;
    private float duration;
    private float timePassed;
    private float value;
    private boolean done = true;
    private final Easing easing;

    public Animator(Easing easing) {
        this.easing = easing;
    }

    public Animator animateTo(float durationMs, float target) {
        if (durationMs <= 0F || AtomChatConfig.performanceModeActive()) {
            return setValue(target);
        }
        if (lastEnd != target || end != target || this.duration != durationMs) {
            start = value;
            duration = durationMs;
            end = target;
            lastEnd = target;
            timePassed = 0.0F;
            done = false;
        }
        return this;
    }

    public Animator setValue(float immediate) {
        start = immediate;
        end = immediate;
        value = immediate;
        lastEnd = immediate;
        timePassed = 0.0F;
        done = true;
        return this;
    }

    public void update(float deltaMs) {
        if (done) {
            return;
        }
        if (AtomChatConfig.performanceModeActive()) {
            setValue(end);
            return;
        }
        timePassed += deltaMs;
        if (timePassed >= duration) {
            timePassed = duration;
            value = end;
            done = true;
        } else {
            float progress = duration <= 0 ? 1.0F : timePassed / duration;
            value = start + (end - start) * easing.apply(progress);
        }
    }

    public float getValue() {
        return value;
    }

    public boolean isDone() {
        return done;
    }
}
