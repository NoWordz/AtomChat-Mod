package com.atom.chat.settings;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;

/** History switch and cleanup text, shared by the settings page and hosted controls. */
public final class HistoryLabelRow {
    private static final float TITLE_CENTER = UiTokens.s(20); // bare-value: original settings title baseline
    private static final float SWITCH_GUTTER = UiTokens.s(10); // bare-value: original space before the switch
    private static final float VERB_GUTTER = UiTokens.s(12); // bare-value: original space before the action verb

    private HistoryLabelRow() {}

    public record Label(String title, String description, String verb, boolean enabled, boolean toggle) {
        public float descriptionWidth(float width) {
            float available = width - 2 * UiTokens.SETTINGS_ROW_PAD;
            if (toggle) return Math.max(0, available - UiTokens.SWITCH_W - SWITCH_GUTTER);
            float reserved = enabled ? verbWidth() + VERB_GUTTER : 0;
            return Math.max(0, available - reserved);
        }

        private float verbWidth() {
            return SkiaFontRenderer.getStringWidth(FontManager.font(UiTokens.SETTINGS_TILE_TITLE), verb);
        }

        private float controlHeight() {
            if (toggle) return UiTokens.SWITCH_H;
            return enabled ? SkiaFontRenderer.getHeight(FontManager.font(UiTokens.SETTINGS_TILE_TITLE)) : 0;
        }

        public SettingsRowText.Text flow(float width) {
            float reserved = (toggle ? UiTokens.SWITCH_W : enabled ? verbWidth() : 0)
                    + (toggle || enabled ? UiTokens.SETTINGS_COMPACT_GAP : 0);
            return SettingsRowText.measure(title, description, width, reserved, controlHeight());
        }

        public float height(float width, SettingsRowText.Text flow) {
            if (flow.stacked()) return flow.height();
            float available = descriptionWidth(width);
            var font = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
            if (description.isEmpty() || available <= 0 || SkiaFontRenderer.getStringWidth(font, description) <= available) {
                return UiTokens.SETTINGS_ROW_H;
            }
            int extra = Math.max(0, SkiaFontRenderer.wrap(font, description, available).size() - 1);
            return UiTokens.SETTINGS_ROW_H + extra * SkiaFontRenderer.getHeight(font);
        }

        public float switchX(UiLayout.Rect row) {
            return row.right() - UiTokens.SETTINGS_ROW_PAD - UiTokens.SWITCH_W;
        }

        public float switchY(UiLayout.Rect row, boolean stacked) {
            return stacked ? row.bottom() - UiTokens.SETTINGS_ROW_PAD - UiTokens.SWITCH_H
                    : row.y() + (row.h() - UiTokens.SWITCH_H) / 2;
        }

        public void paint(Canvas canvas, UiLayout.Rect row, SettingsRowText.Text flow, int primary, int secondary) {
            boolean stacked = flow != null && flow.stacked();
            // The original compact action uses full caption alpha even when unavailable.
            int caption = UiTokens.withAlpha(secondary, enabled || stacked && !toggle ? 200 : 130);
            var font = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            if (stacked) {
                SettingsRowText.draw(canvas, row, flow, primary, caption);
            } else {
                float x = row.x() + UiTokens.SETTINGS_ROW_PAD;
                float width = toggle ? Math.max(0, switchX(row) - x - SWITCH_GUTTER)
                        : row.w() - UiTokens.SETTINGS_ROW_PAD * 2;
                SkiaFontRenderer.drawText(canvas, font, SkiaFontRenderer.truncate(font, title, width), x,
                        SkiaFontRenderer.centerBaselineY(font, row.y() + TITLE_CENTER), primary);
                SettingsRowText.description(canvas, row, FontManager.font(UiTokens.SETTINGS_TILE_SUB),
                        description, x, descriptionWidth(row.w()), caption);
            }
            if (!toggle && enabled) {
                float y = stacked ? row.bottom() - UiTokens.SETTINGS_ROW_PAD - controlHeight() / 2
                        : row.y() + row.h() / 2;
                SkiaFontRenderer.drawTextRight(canvas, font, verb, row.right() - UiTokens.SETTINGS_ROW_PAD, y, primary);
            }
        }
    }
}
