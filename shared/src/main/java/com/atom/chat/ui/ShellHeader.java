package com.atom.chat.ui;



import com.atom.chat.render.OutlineIcons;
import com.atom.chat.render.CornerGeometry;
import com.atom.chat.render.ChromeMaterial;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;

import java.time.LocalTime;
import java.util.List;

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
    private static final PressScale BACK_SCALE = PressScale.bounce();
    private static final PressScale ACTION_SCALE = PressScale.bounce();
    private static boolean backPressedNow;
    private static boolean actionPressedNow;
    /**
     * WHY: the header is a render-only class with no update channel, so its
     * springs advance on the nominal 60fps frame step. It is a duration in
     * milliseconds — wall-clock time does not scale with UI density, which is
     * why it is a plain constant rather than a {@code UiTokens.s()} value.
     */
    private static final float FRAME_DT_MS = 16.0F;

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
        LocalTime now = LocalTime.now();
        String time = String.format("%02d:%02d", now.getHour(), now.getMinute());
        render(canvas, header, title, showBack ? backButton : null, backHover, textPrimary, statusOnline,
                action, time, 1, 1, 1, true, true, false, false);
    }

    static void renderNavigation(Canvas canvas, UiLayout.Rect header, String title, UiLayout.Rect backButton,
                                 float backHover, int textPrimary, Boolean statusOnline, HeaderAction action,
                                 String time, float titleOpacity, float backOpacity, float actionOpacity,
                                 boolean backInteractive, boolean actionInteractive, boolean wrapTitle) {
        render(canvas, header, title, backButton, backHover, textPrimary, statusOnline, action,
                time, titleOpacity, backOpacity, actionOpacity, backInteractive, actionInteractive, true, wrapTitle);
    }

    private static void render(Canvas canvas, UiLayout.Rect header, String title, UiLayout.Rect backButton,
                               float backHover, int textPrimary, Boolean statusOnline, HeaderAction action,
                               String time, float titleOpacity, float backOpacity, float actionOpacity,
                               boolean backInteractive, boolean actionInteractive, boolean keepClock, boolean wrapTitle) {
        if (header == null || header.w() <= 0.0F || header.h() <= 0.0F) {
            return;
        }
        // Floating chrome: same elevation language as the menus (two-tier
        // shadow behind an opaque-ish surface), unlike the edge-highlighted
        // content cards.
        float radius = ChromeMaterial.cornerRadius(header, UiTokens.headerRadius(AtomChatAppearance.theme()));
        ChromeMaterial.drawShadow(canvas, header.x(), header.y(), header.w(), header.h(), radius);
        ChromeMaterial.drawBackdrop(canvas, header.x(), header.y(), header.w(), header.h(), radius, 1F);
        CornerGeometry.fill(canvas, CornerGeometry.circularRect(header.x(), header.y(), header.w(), header.h(), radius),
                UiTokens.cardFill(AtomChatAppearance.theme()));
        ChromeMaterial.drawDetails(canvas, header, radius);

        if (backButton != null && backOpacity > 0) {
            boolean pressed = backPressedNow;
            backPressedNow = false;
            drawIconButton(canvas, backButton, backHover, pressed, BACK_SCALE,
                    TablerIcons.ARROW_LEFT, textPrimary, backOpacity, backInteractive);
        }
        if (action != null && action.rect() != null && action.icon() != null && actionOpacity > 0) {
            boolean pressed = actionPressedNow;
            actionPressedNow = false;
            drawIconButton(canvas, action.rect(), action.hover(), pressed, ACTION_SCALE,
                    action.icon(), action.color(), actionOpacity, actionInteractive);
        } else {
            // No trailing action this frame: let the spring return to rest
            // under the real motion gate. Width is irrelevant here — the
            // non-hover/non-press target is 1 — but a positive value keeps
            // the budget formula total.
            ACTION_SCALE.update(false, false, FRAME_DT_MS, Animations.enabled(), 1.0F);
        }
        Font timeFont = FontManager.font(UiTokens.FONT_TIME);
        Font titleFont = FontManager.font(UiTokens.FONT_TITLE);
        float dotSpace = statusOnline == null ? 0.0F : UiTokens.HEADER_TEXT_GAP * 2.0F;
        TextBounds bounds = textBounds(header, backButton,
                action == null ? null : action.rect(),
                keepClock ? 0 : SkiaFontRenderer.getStringWidth(titleFont, title) + dotSpace,
                SkiaFontRenderer.getStringWidth(timeFont, time));
        if (titleOpacity > 0) {
            float available = Math.max(0.0F, bounds.right() - bounds.left() - dotSpace);
            var copy = titleCopy(title, titleFont, timeFont, available, header.h(), wrapTitle);
            float titleW = 0F;
            for (String line : copy.lines()) titleW = Math.max(titleW, SkiaFontRenderer.getStringWidth(copy.font(), line));
            float center = Math.max(bounds.left() + dotSpace + titleW / 2.0F,
                    Math.min(header.x() + header.w() / 2.0F, bounds.right() - titleW / 2.0F));
            canvas.save();
            canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(bounds.left(), header.y(),
                    Math.max(0.0F, bounds.right() - bounds.left()), header.h()));
            float lineHeight = SkiaFontRenderer.getHeight(copy.font());
            float lineY = header.y() + (header.h() - (copy.lines().size() - 1) * lineHeight) / 2F;
            for (String line : copy.lines()) {
                SkiaFontRenderer.drawTextCentered(canvas, copy.font(), line, center, lineY,
                        withOpacity(textPrimary, titleOpacity));
                lineY += lineHeight;
            }

            if (statusOnline != null) {
                float dotR = UiTokens.s(5);
                float dotX = center - titleW / 2.0F - UiTokens.HEADER_TEXT_GAP - dotR;
                float dotY = header.y() + header.h() / 2.0F;
                int dotColor = statusOnline ? Color.makeARGB(255, 82, 196, 110) : Color.makeARGB(255, 130, 140, 150);
                CornerGeometry.drawRoundedRect(canvas, dotX - dotR, dotY - dotR, dotR * 2, dotR * 2, dotR, withOpacity(dotColor, titleOpacity));
            }
            canvas.restore();
        }
        if (bounds.showTime()) SkiaFontRenderer.drawTextRight(canvas, timeFont, time,
                header.right() - UiTokens.HEADER_PAD_X,
                header.y() + header.h() / 2.0F, textPrimary);
    }

    record TextBounds(float left, float right, boolean showTime) {}

    record TitleCopy(Font font, List<String> lines) {}

    static TitleCopy titleCopy(String title, Font normal, Font compact, float width, float height, boolean wrap) {
        float measured = SkiaFontRenderer.getStringWidth(normal, title);
        if (!wrap || width <= 0F || measured <= width) {
            return new TitleCopy(normal, List.of(SkiaFontRenderer.truncate(normal, title, width)));
        }
        var lines = SkiaFontRenderer.wrapWords(compact, title, width);
        if (lines.size() <= 2 && lines.size() * SkiaFontRenderer.getHeight(compact) <= height
                && lines.stream().allMatch(line -> SkiaFontRenderer.getStringWidth(compact, line) <= width)) {
            return new TitleCopy(compact, lines);
        }
        float fittedSize = Math.max(1F, (float) Math.floor(normal.getSize() * width / measured));
        return new TitleCopy(FontManager.font(fittedSize), List.of(title));
    }

    /** Page content must be drawn after this pass, even when the header itself is an overlay. */
    public static void drawBackdrop(Canvas canvas, UiLayout.Rect header) {
        drawBackdrop(canvas, header, false);
    }

    public static void drawBackdrop(Canvas canvas, UiLayout.Rect header, boolean settled) {
        float radius = ChromeMaterial.cornerRadius(header, UiTokens.headerRadius(AtomChatAppearance.theme()));
        com.atom.chat.render.DotTexture.drawPanelEdge(canvas, header, radius, false,
                com.atom.chat.config.AtomChatConfig.get().textSecondaryColor, settled);
    }

    static TextBounds textBounds(UiLayout.Rect header, UiLayout.Rect back, UiLayout.Rect action,
                                 float titleWidth, float timeWidth) {
        float gap = UiTokens.HEADER_TEXT_GAP;
        float left = header.x() + UiTokens.HEADER_PAD_X;
        if (back != null) left = Math.max(left, back.right() + gap);
        if (action != null) left = Math.max(left, action.right() + gap);
        float right = Math.max(left, header.right() - UiTokens.HEADER_PAD_X);
        float clockLeft = right - timeWidth - gap;
        boolean showTime = clockLeft - left >= titleWidth;
        return new TextBounds(left, showTime ? clockLeft : right, showTime);
    }

    /** Hover wash + centred icon, the shared header-button recipe. */
    private static void drawIconButton(Canvas canvas, UiLayout.Rect rect, float hover,
                                       boolean pressed, PressScale scale,
                                       io.github.humbleui.skija.Path icon, int color, float opacity, boolean interactive) {
        if (interactive) {
            ClickRipples.region(rect.x(), rect.y(), rect.w(), rect.h(), UiTokens.radius(8, AtomChatAppearance.theme()),
                    UiTokens.cardFill(AtomChatAppearance.theme()));
        }
        // The scaled shape is the inset wash, so the width budget is spent on
        // that surface (rect minus the inset on both sides), not on the outer
        // hit-test rect. Behaviour is unchanged at the default inset — this is
        // the semantic width the budget formula asks for.
        scale.update(hover > 0.01F, pressed, FRAME_DT_MS, Animations.enabled(),
                rect.w() - 2.0F * UiTokens.EDGE_CONTROL_INSET);
        float cx = rect.x() + rect.w() / 2.0F;
        float cy = rect.y() + rect.h() / 2.0F;
        canvas.save();
        if (scale.scale() != 1.0F) {
            canvas.translate(cx, cy);
            canvas.scale(scale.scale(), scale.scale());
            canvas.translate(-cx, -cy);
        }
        if (hover > 0.01F) {
            float inset = UiTokens.EDGE_CONTROL_INSET;
            float x = rect.x() + inset;
            float y = rect.y() + inset;
            float w = rect.w() - inset * 2.0F;
            float h = rect.h() - inset * 2.0F;
            CornerGeometry.drawRoundedRect(canvas, x, y, w, h, UiTokens.radius(8, AtomChatAppearance.theme()),
                    withOpacity(UiTokens.cardHover(hover, AtomChatAppearance.theme()), opacity));
        }
        drawIconCentered(canvas, icon,
                rect.x() + rect.w() / 2.0F,
                rect.y() + rect.h() / 2.0F,
                UiTokens.s(18), withOpacity(color, opacity));
        canvas.restore();
    }

    private static int withOpacity(int color, float opacity) {
        return (color & 0x00FFFFFF) | (Math.round((color >>> 24) * opacity) << 24);
    }

    private static void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon,
                                         float cx, float cy, float size, int color) {
        OutlineIcons.draw(canvas, icon, cx, cy, size, color);
    }
}
