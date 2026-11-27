package com.atom.chat.render;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;

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

    public static void draw(Canvas canvas, float centerX, float centerY, float avatarRadius, int color,
                            AppearanceUi.AvatarFrame frame) {
        if (!drawAddon(canvas, centerX, centerY, avatarRadius, color, frame)) {
            draw(canvas, centerX, centerY, avatarRadius, color);
        }
    }

    public static boolean drawAddon(Canvas canvas, float centerX, float centerY, float avatarRadius, int color,
                             AppearanceUi.AvatarFrame frame) {
        if (frame == null || !(avatarRadius > 0)) return false;
        float rim = separatorRadius(avatarRadius);
        var avatar = new AppearanceUi.Bounds(centerX - avatarRadius, centerY - avatarRadius,
                avatarRadius * 2F, avatarRadius * 2F);
        var bounds = new AppearanceUi.Bounds(centerX - rim, centerY - rim, rim * 2F, rim * 2F);
        var context = new AppearanceUi.AvatarContext(avatar, bounds, UiTokens.SCALE, color);
        return AddonPaint.draw(canvas, Rect.makeXYWH(bounds.x(), bounds.y(), bounds.width(), bounds.height()), null,
                output -> frame.draw(output, context), frame, "avatar frame");
    }
}
