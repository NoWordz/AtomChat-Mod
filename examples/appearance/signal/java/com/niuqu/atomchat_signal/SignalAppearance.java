package com.niuqu.atomchat_signal;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.api.ui.AppearanceUi.Part;
import com.atom.chat.api.ui.SettingsUi;
import com.atom.chat.api.ui.UiContributions;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.api.ui.AppearanceUi.AvatarContext;
import com.atom.chat.api.ui.AppearanceUi.Bounds;
import com.atom.chat.api.ui.AppearanceUi.BubbleContext;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.RRect;

import java.util.List;

import static com.niuqu.atomchat_signal.SignalRegistration.MOD_ID;

final class SignalAppearance {
    private SignalAppearance() {}

    static void register() {
        // A panel-only bubble demonstrates the host's built-in HUD fallback.
        AppearanceUi.registerBubble(MOD_ID + ":capsule", MOD_ID + ".bubble.capsule",
                SignalAppearance::bubble, null);
        AppearanceUi.registerAvatarFrame(MOD_ID + ":brackets", MOD_ID + ".avatar.brackets",
                SignalAppearance::avatar, SignalAppearance::avatar);
        registerSettings();
    }

    private static void registerSettings() {
        try {
            AppearanceUi.class.getMethod("select", Part.class, String.class);
        } catch (NoSuchMethodException olderHost) {
            return;
        }
        SettingsUi.groups(SettingsSection.APPEARANCE, SettingsUi.builtInTabId("theme"))
                .add(MOD_ID + ":appearance", new SettingsUi.Group(MOD_ID + ".settings.appearance", List.of(
                        option(Part.BUBBLE, "capsule", "bubble"),
                        option(Part.AVATAR_FRAME, "brackets", "avatar"))), UiContributions.Position.last());
    }

    private static SettingsUi.Toggle option(Part part, String style, String kind) {
        String id = MOD_ID + ":" + style;
        return new SettingsUi.Toggle(MOD_ID + ":apply_" + kind, MOD_ID + "." + kind + "." + style,
                MOD_ID + ".settings." + kind + ".desc", () -> id.equals(AppearanceUi.selectedId(part)),
                on -> {
                    if (on || id.equals(AppearanceUi.selectedId(part))) AppearanceUi.select(part, on ? id : null);
                });
    }

    private static Path bubble(BubbleContext context) {
        Bounds b = context.bounds();
        float radius = Math.min(b.width(), b.height()) / 2;
        try (var path = new PathBuilder()) {
            return path.addRRect(RRect.makeXYWH(b.x(), b.y(), b.width(), b.height(), radius)).detach();
        }
    }

    private static void avatar(Canvas canvas, AvatarContext context) {
        Bounds b = context.frameBounds();
        float edge = Math.min(b.width(), b.height());
        if (edge <= 0) return;

        float stroke = Math.min(2 * context.scale(), edge / 8);
        float arm = edge / 4;
        float left = b.x() + stroke;
        float top = b.y() + stroke;
        float right = b.right() - stroke;
        float bottom = b.bottom() - stroke;
        try (var builder = new PathBuilder();
             var paint = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE)
                     .setStrokeWidth(stroke).setColor(0xFFFFBD56)) {
            builder.moveTo(left, top + arm).lineTo(left, top).lineTo(left + arm, top);
            builder.moveTo(right - arm, top).lineTo(right, top).lineTo(right, top + arm);
            builder.moveTo(right, bottom - arm).lineTo(right, bottom).lineTo(right - arm, bottom);
            builder.moveTo(left + arm, bottom).lineTo(left, bottom).lineTo(left, bottom - arm);
            try (var path = builder.detach()) {
                canvas.drawPath(path, paint);
            }
        }
    }
}
