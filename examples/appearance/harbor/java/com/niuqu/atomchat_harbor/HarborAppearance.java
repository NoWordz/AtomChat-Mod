package com.niuqu.atomchat_harbor;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.api.ui.AppearanceUi.AvatarContext;
import com.atom.chat.api.ui.AppearanceUi.Bounds;
import com.atom.chat.api.ui.AppearanceUi.BubbleContext;
import com.atom.chat.api.ui.AppearanceUi.BubbleGroup;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;

import static com.niuqu.atomchat_harbor.HarborRegistration.MOD_ID;

final class HarborAppearance {
    private HarborAppearance() {}

    static void register() {
        AppearanceUi.registerBubble(MOD_ID + ":chamfer", MOD_ID + ".bubble.chamfer",
                HarborAppearance::bubble, HarborAppearance::bubble);
        AppearanceUi.registerPanelOutline(MOD_ID + ":chamfer", MOD_ID + ".panel.chamfer",
                context -> {
                    float cut = 30 * context.scale();
                    return chamfer(context.bounds(), cut, cut, cut, cut);
                });
        AppearanceUi.registerAvatarFrame(MOD_ID + ":tide_ring", MOD_ID + ".avatar.tide_ring",
                HarborAppearance::avatar, HarborAppearance::avatar);
        AppearanceUi.registerDynamicWallpaper(MOD_ID + ":moonlit_harbor", MOD_ID + ".wallpaper.moonlit_harbor",
                HarborWallpaper::new);
    }

    private static Path bubble(BubbleContext context) {
        float cut = 7 * context.scale();
        BubbleGroup group = context.group();
        float top = group == BubbleGroup.SINGLE || group == BubbleGroup.FIRST ? cut : cut / 3;
        float bottom = group == BubbleGroup.SINGLE || group == BubbleGroup.LAST ? cut : cut / 3;
        return context.own()
                ? chamfer(context.bounds(), cut, top, bottom, cut)
                : chamfer(context.bounds(), top, cut, cut, bottom);
    }

    private static Path chamfer(Bounds bounds, float topLeft, float topRight, float bottomRight, float bottomLeft) {
        float limit = Math.min(bounds.width(), bounds.height()) / 3;
        topLeft = Math.min(topLeft, limit);
        topRight = Math.min(topRight, limit);
        bottomRight = Math.min(bottomRight, limit);
        bottomLeft = Math.min(bottomLeft, limit);
        float left = bounds.x();
        float right = bounds.right();
        float top = bounds.y();
        float bottom = bounds.bottom();
        try (var path = new PathBuilder()) {
            return path.moveTo(left + topLeft, top)
                    .lineTo(right - topRight, top).lineTo(right, top + topRight)
                    .lineTo(right, bottom - bottomRight).lineTo(right - bottomRight, bottom)
                    .lineTo(left + bottomLeft, bottom).lineTo(left, bottom - bottomLeft)
                    .lineTo(left, top + topLeft).closePath().detach();
        }
    }

    private static void avatar(Canvas canvas, AvatarContext context) {
        Bounds avatar = context.avatarBounds();
        Bounds frame = context.frameBounds();
        float x = avatar.x() + avatar.width() / 2;
        float y = avatar.y() + avatar.height() / 2;
        float edge = Math.min(Math.min(x - frame.x(), frame.right() - x),
                Math.min(y - frame.y(), frame.bottom() - y));
        if (edge <= 0) return;

        float stroke = Math.min(1.5f * context.scale(), edge / 5);
        float outer = edge - stroke / 2;
        try (var paint = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE).setStrokeWidth(stroke)) {
            canvas.drawCircle(x, y, outer, paint.setColor(0xFF5EEAD4));
            canvas.drawCircle(x, y, outer - 2 * stroke, paint.setColor(0xD0286372));
        }
    }
}
