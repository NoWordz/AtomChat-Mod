package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;

/**
 * One continuous setting row. Like {@link SettingsItem}, writing and
 * persisting are fused so a dragged value is on disk the moment the finger
 * lifts — and on every drag frame too, since the setter runs live.
 */
public final class SettingsSlider {
    public interface FloatGetter {
        float get();
    }

    public interface FloatSetter {
        void set(float value);
    }

    public interface Formatter {
        String format(float value);
    }

    private final String id;
    private final String titleKey;
    private final FloatGetter min;
    private final FloatGetter max;
    private final float step;
    private final FloatGetter getter;
    private final FloatSetter setter;
    private final Formatter formatter;

    public SettingsSlider(String id, String titleKey, float min, float max, float step,
                          FloatGetter getter, FloatSetter setter, Formatter formatter) {
        this(id, titleKey, () -> min, () -> max, step, getter, setter, formatter);
    }

    public SettingsSlider(String id, String titleKey, FloatGetter min, FloatGetter max, float step,
                          FloatGetter getter, FloatSetter setter, Formatter formatter) {
        this.id = id;
        this.titleKey = titleKey;
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.formatter = formatter;
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return titleKey;
    }

    public float min() {
        return min.get();
    }

    public float max() {
        return max.get();
    }

    public float step() {
        return step;
    }

    public float value() {
        return clamp(getter.get());
    }

    public String displayValue() {
        return formatter.format(value());
    }

    public void set(float value) {
        apply(value);
        persist();
    }

    /**
     * Updates the config field in memory only. Used while a drag is in
     * progress: committing the value every frame means writing the config file
     * sixty times a second. The write happens once, on release.
     */
    public void apply(float value) {
        setter.set(clamp(value));
    }

    /** Flushes the in-memory value to {@code atomchat-client.json}. */
    public void persist() {
        AtomChatConfig.save(AtomChatConfig.get());
    }

    /** Nudges by one {@link #step()} in the given direction. */
    public void nudge(int direction) {
        float next = value() + Math.signum(direction) * step;
        // Snap to the step grid so repeated nudges cannot drift off it.
        set(Math.round(next / step) * step);
    }

    public float clamp(float value) {
        return Math.max(min(), Math.min(max(), value));
    }

    /** 0..1 position of {@code value} within the range. */
    public float normalize(float value) {
        float low = min(), high = max();
        return high > low ? (clamp(value) - low) / (high - low) : 0F;
    }

    /** Value at normalised position {@code t} (0..1), snapped to the step grid. */
    public float denormalize(float t) {
        if (t >= 1F) return max();
        if (t <= 0F) return min();
        float raw = min() + t * (max() - min());
        return clamp(Math.round(raw / step) * step);
    }

    /**
     * Same as {@link #denormalize(float)} but without step snapping. A drag
     * must follow the pointer continuously — the knob jumping between fourteen
     * allowed positions is what reads as "not smooth", and easing the drag
     * itself would only make it lag behind the finger.
     */
    public float denormalizeContinuous(float t) {
        return clamp(min() + Math.max(0.0F, Math.min(1.0F, t)) * (max() - min()));
    }
}
