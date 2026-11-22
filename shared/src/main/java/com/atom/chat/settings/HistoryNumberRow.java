package com.atom.chat.settings;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.DesignHost;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;

/** Rendering shared by the history setting and its bound number control. */
public final class HistoryNumberRow {
    private static final float LABEL_GAP = UiTokens.s(12); // bare-value: the original inline number gutter
    private static final float TITLE_LINE = UiTokens.s(20), DESCRIPTION_LINE = UiTokens.s(37); // bare-value: existing label baselines
    private static final float FIELD_INSET = UiTokens.s(10); // bare-value: existing numeric text inset
    private static final float CARET_GAP = UiTokens.s(2), CARET_HALF_HEIGHT = UiTokens.s(8); // bare-value: original caret geometry

    private HistoryNumberRow() {}

    public static long caretMillis() { return System.currentTimeMillis(); }

    public static void draw(Canvas canvas, UiLayout.Rect row, SettingsRowText.Text compact, String title,
                            String description, String unit, String value, boolean editing, float hover,
                            DesignHost theme, int accent, int primary, int secondary, long millis) {
        var field = SettingsRowText.number(row, compact != null);
        float center = field.y() + field.h() / 2;
        if (compact != null) {
            SettingsRowText.draw(canvas, row, compact, primary, secondary);
        } else {
            float labelX = row.x() + UiTokens.SETTINGS_ROW_PAD;
            float labelWidth = Math.max(0, field.x() - labelX - LABEL_GAP);
            var titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            var subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
            SkiaFontRenderer.drawText(canvas, titleFont, SkiaFontRenderer.truncate(titleFont, title, labelWidth),
                    labelX, SkiaFontRenderer.centerBaselineY(titleFont, row.y() + TITLE_LINE), primary);
            SkiaFontRenderer.drawText(canvas, subFont, SkiaFontRenderer.truncate(subFont, description, labelWidth),
                    labelX, SkiaFontRenderer.centerBaselineY(subFont, row.y() + DESCRIPTION_LINE), secondary);
        }

        SettingsFieldPaint.surface(canvas, field, hover, editing, false, true, accent, theme);
        var font = FontManager.font(UiTokens.FONT_INPUT);
        String shown = SkiaFontRenderer.truncate(font, value, field.w() - FIELD_INSET * 2);
        SkiaFontRenderer.drawText(canvas, font, shown, field.x() + FIELD_INSET,
                SkiaFontRenderer.centerBaselineY(font, center), editing ? accent : primary);
        SettingsRowText.drawNumberUnit(canvas, field, unit, secondary);
        if (editing && Animations.caretVisible(millis)) {
            float x = field.x() + FIELD_INSET + SkiaFontRenderer.getStringWidth(font, shown) + CARET_GAP;
            try (Paint caret = new Paint().setColor(accent).setStrokeWidth(UiTokens.FOCUS_RING_WIDTH).setAntiAlias(true)) {
                canvas.drawLine(x, center - CARET_HALF_HEIGHT, x, center + CARET_HALF_HEIGHT, caret);
            }
        }
    }
}
