package com.atom.chat.render;

/**
 * Device-pixel alignment helpers for the Skia draw path (unit V, the sharpness
 * batch).
 *
 * <p><b>Why this exists.</b> At fractional densities (1.25/1.3333/1.5) a
 * logical coordinate lands on an arbitrary subpixel phase, so a shape edge the
 * eye expects to be crisp is spread across two device pixels with its
 * neighbours at a different phase — the "dirty edge" the sharpness probe
 * measured (d=1.0: zero intermediate edge pixels; d=1.3333: 6-11% of the
 * card outline band). Snapping the geometry to the device grid before drawing
 * recovers the d=1.0 crispness without touching the visual language.</p>
 *
 * <p><b>The density is a per-frame render-thread input</b>, set once by the
 * screen's Skia pass ({@link #beginFrame}) and read by the draw helpers — the
 * same discipline DotTexture uses (render thread only, no cross-frame GPU
 * state). Off the render thread (tests, the blur pre-pass which computes its
 * own device coordinates from explicit parameters) the density defaults to 1,
 * which makes every helper a no-op: an unset frame never changes geometry.</p>
 *
 * <p>Bucket: shared single source, no Minecraft names.</p>
 */
public final class UiPixelSnap {
    private static final ThreadLocal<Float> DENSITY = ThreadLocal.withInitial(() -> 1.0F);

    private UiPixelSnap() {
    }

    /** Sets the frame's device density; call once per Skia pass on the render thread. */
    public static void beginFrame(float density) {
        DENSITY.set(density <= 0.0F ? 1.0F : density);
    }

    /** The frame density, or 1 when no frame is active (helpers become no-ops). */
    public static float density() {
        return DENSITY.get();
    }

    /**
     * Snaps {@code value} to the device-pixel grid: the result times the
     * density is an integer, so a shape edge drawn there is a hard device
     * edge instead of a two-pixel blend. With density 1 this is rounding.
     */
    public static float snapToDevice(float value) {
        float d = DENSITY.get();
        return Math.round(value * d) / d;
    }

    /**
     * Half a device pixel, in logical units — the offset that moves a
     * centred stroke onto pixel centres (a 1px stroke centred on a pixel
     * boundary always straddles two pixels at half intensity each; the
     * sharpness probe measured exactly that on the avatar rims).
     */
    public static float halfDevicePx() {
        return 0.5F / DENSITY.get();
    }
}
