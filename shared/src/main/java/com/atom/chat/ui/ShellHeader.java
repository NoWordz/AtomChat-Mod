package com.atom.chat.ui;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.types.Rect;

import java.time.LocalTime;

/**
 * Pure Skia shell chrome for the unified AtomChat header. It owns the
 * translucent card, the optional back affordance, the centered page title and
 * the right-aligned clock; page classes never draw these themselves.
 */
public final class ShellHeader {
    private ShellHeader() {
    }

    /**
     * An optional icon button drawn right of the back arrow (world-chat feed
     * filter today). Geometry/hover/icon/color are all decided by the caller.
     */
    public record HeaderAction(UiLayout.Rect rect, float hover, io.github.humbleui.skija.Path icon, int color) {
    }

    // One bounce per header button. Static because the whole class is: the
    // screen owns the press hit-test and arms it here, so render keeps its
    // signature and this class stays free of input handling.
    private static final PressScale BACK_SCALE = PressScale.compact();
    private static final PressScale ACTION_SCALE = PressScale.compact();
    private static boolean backPressedNow;
    private static boolean actionPressedNow;

    /** Arms the back arrow's press bounce; consumed by the next render. */
    public static void armBackPress() {
        backPressedNow = true;
    }

    /** Arms the trailing action's press bounce; consumed by the next render. */
    public static void armActionPress() {
        actionPressedNow = true;
    }

    public static void render(Canvas canvas, UiLayout.Rect header, String title, boolean showBack,
                              UiLayout.Rect backButton, float backHover, int textPrimary) {
        render(canvas, header, title, showBack, backButton, backHover, textPrimary, null, null);
    }

    public static void render(Canvas canvas, UiLayout.Rect header, String title, boolean showBack,
                              UiLayout.Rect backButton, float backHover, int textPrimary,
                              Boolean statusOnline) {
        render(canvas, header, title, showBack, backButton, backHover, textPrimary, statusOnline, null);
    }

    public static void render(Canvas canvas, UiLayout.Rect header, String title, boolean showBack,
                              UiLayout.Rect backButton, float backHover, int textPrimary,
                              Boolean statusOnline, HeaderAction action) {
        if (header == null || header.w() <= 0.0F || header.h() <= 0.0F) {
            return;
        }
        // Floating chrome: same elevation language as the menus (shadow behind
        // an opaque-ish surface), unlike the edge-highlighted content cards.
        SkiaDraw.drawRoundedShadow(canvas, header.x(), header.y(), header.w(), header.h(),
                UiTokens.headerRadius(), UiTokens.s(8), UiTokens.CHROME_SHADOW);
        SkiaDraw.drawRoundedRect(canvas, header.x(), header.y(), header.w(), header.h(),
                UiTokens.headerRadius(), UiTokens.cardFill());

        if (showBack && backButton != null) {
            boolean pressed = backPressedNow;
            backPressedNow = false;
            drawIconButton(canvas, backButton, backHover, pressed, BACK_SCALE,
                    AppIcons.ICON_BACK_PATH, textPrimary);
        }
        if (action != null && action.rect() != null && action.icon() != null) {
            boolean pressed = actionPressedNow;
            actionPressedNow = false;
            drawIconButton(canvas, action.rect(), action.hover(), pressed, ACTION_SCALE,
                    action.icon(), action.color());
        } else {
            ACTION_SCALE.update(false, false, 16.0F, true);
        }
        Font titleFont = FontManager.font(UiTokens.FONT_TITLE);
        SkiaFontRenderer.drawTextCentered(canvas, titleFont, title,
                header.x() + header.w() / 2.0F,
                header.y() + header.h() / 2.0F, textPrimary);

        LocalTime now = LocalTime.now();
        String time = String.format("%02d:%02d", now.getHour(), now.getMinute());
        Font timeFont = FontManager.font(UiTokens.FONT_TIME);
        SkiaFontRenderer.drawTextRight(canvas, timeFont, time,
                header.right() - UiTokens.HEADER_PAD_X,
                header.y() + header.h() / 2.0F, textPrimary);

        if (statusOnline != null) {
            Font dotTitleFont = FontManager.font(UiTokens.FONT_TITLE);
            float titleW = SkiaFontRenderer.getStringWidth(dotTitleFont, title);
            float dotR = UiTokens.s(5);
            float dotX = header.x() + header.w() / 2.0F - titleW / 2.0F - UiTokens.s(12) - dotR;
            float dotY = header.y() + header.h() / 2.0F;
            int dotColor = statusOnline ? Color.makeARGB(255, 82, 196, 110) : Color.makeARGB(255, 130, 140, 150);
            SkiaDraw.drawRoundedRect(canvas, dotX - dotR, dotY - dotR, dotR * 2, dotR * 2, dotR, dotColor);
        }
    }

    /** Hover wash + centred icon, the shared header-button recipe. */
    private static void drawIconButton(Canvas canvas, UiLayout.Rect rect, float hover,
                                       boolean pressed, PressScale scale,
                                       io.github.humbleui.skija.Path icon, int color) {
        scale.update(hover > 0.01F, pressed, 16.0F, Animations.enabled());
        float cx = rect.x() + rect.w() / 2.0F;
        float cy = rect.y() + rect.h() / 2.0F;
        canvas.save();
        if (scale.scale() != 1.0F) {
            canvas.translate(cx, cy);
            canvas.scale(scale.scale(), scale.scale());
            canvas.translate(-cx, -cy);
        }
        if (hover > 0.01F) {
            float inset = UiTokens.s(4);
            float x = rect.x() + inset;
            float y = rect.y() + inset;
            float w = rect.w() - inset * 2.0F;
            float h = rect.h() - inset * 2.0F;
            SkiaDraw.drawRoundedRect(canvas, x, y, w, h, UiTokens.radius(8),
                    UiTokens.cardHover(hover));
        }
        drawIconCentered(canvas, icon,
                rect.x() + rect.w() / 2.0F,
                rect.y() + rect.h() / 2.0F,
                UiTokens.s(18), color);
        canvas.restore();
    }

    private static void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon,
                                         float cx, float cy, float size, int color) {
        Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float scale = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * scale,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * scale);
            canvas.scale(scale, scale);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE)
                    .setStrokeWidth(UiTokens.iconStroke(size) / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }
}
