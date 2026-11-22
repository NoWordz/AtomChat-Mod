package com.atom.chat.render;

import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;

/** A circular rim with a small inward overlap at the avatar's antialiased edge. */
public final class AvatarRing {
    public static final float RIM_STROKE_PX = UiTokens.s(1.0F); // bare-value: the avatar's own hairline
    public static final float SEPARATOR_PX = RIM_STROKE_PX;

    private AvatarRing() { }

    public static float separatorRadius(float avatarRadius) {
        return avatarRadius + SEPARATOR_PX;
    }

    static float rimWidth(float avatarRadius) {
        // Abutting two independently antialiased edges can expose the background between them.
        return RIM_STROKE_PX + Math.min(avatarRadius, UiPixelSnap.halfDevicePx());
    }

    public static void draw(Canvas canvas, float centerX, float centerY, float avatarRadius, int color) {
        float stroke = rimWidth(avatarRadius);
        SkiaDraw.drawRing(canvas, centerX, centerY, separatorRadius(avatarRadius) - stroke / 2F, stroke, color);
    }
}
