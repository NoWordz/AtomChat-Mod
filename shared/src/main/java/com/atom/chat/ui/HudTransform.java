package com.atom.chat.ui;

import io.github.humbleui.skija.Canvas;
import org.joml.Matrix4f;

/** The game's 2D HUD pose, applied after layout so zoom can reuse the same raster ink. */
public record HudTransform(float x, float y, float scaleX, float scaleY) {
    public static final HudTransform IDENTITY = new HudTransform(0, 0, 1, 1);

    public static HudTransform from(Matrix4f pose) {
        if ((pose.properties() & Matrix4f.PROPERTY_IDENTITY) != 0) return IDENTITY;
        if (pose.m01() != 0 || pose.m10() != 0 || pose.m03() != 0 || pose.m13() != 0 || pose.m33() != 1
                || !Float.isFinite(pose.m30()) || !Float.isFinite(pose.m31())
                || !Float.isFinite(pose.m00()) || !Float.isFinite(pose.m11())
                || pose.m00() <= 0F || pose.m11() <= 0F) return null;
        return new HudTransform(pose.m30(), pose.m31(), pose.m00(), pose.m11());
    }

    public void apply(Canvas canvas, float unitsPerGuiPixel) {
        if (this == IDENTITY) return;
        canvas.translate(x * unitsPerGuiPixel, y * unitsPerGuiPixel);
        canvas.scale(scaleX, scaleY);
    }
}
