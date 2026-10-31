package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
/** Shared dialog geometry: cancel left, confirm right, including narrow panels. */
public record ConfirmationLayout(UiLayout.Rect surface, UiLayout.Rect cancel, UiLayout.Rect confirm, float padding) {
    public float titleTextX() { return surface.x()+padding+UiTokens.COMPOSER_ICON_SIZE+UiTokens.SETTINGS_TILE_TEXT_GAP; }
    public float titleCenterY() { return surface.y()+padding+UiTokens.HEADER_HEIGHT/2; }
    public static final float PAD = UiTokens.HEADER_PAD_X;
    public static final float TEXT_LINE = UiTokens.DIALOG_LINE_HEIGHT;
    public static ConfirmationLayout of(UiLayout.Rect panel, int lines) {
        return of(panel, lines, TEXT_LINE);
    }
    public static ConfirmationLayout of(UiLayout.Rect panel, int lines, float lineHeight) {
        float width = Math.max(0, Math.min(UiTokens.DIALOG_MAX_WIDTH, panel.w() - UiTokens.PANEL_TOP_GAP * 2));
        boolean compact = width < UiTokens.DIALOG_STACK_WIDTH;
        float pad = compact ? UiTokens.INPUT_ROW_PAD : PAD;
        float height = Math.min(panel.h() - UiTokens.PANEL_TOP_GAP * 2,
                pad * 3 + UiTokens.HEADER_HEIGHT + lines * lineHeight + UiTokens.BUTTON_H);
        var box = new UiLayout.Rect(panel.x() + (panel.w() - width) / 2, panel.y() + (panel.h() - height) / 2, width, Math.max(0, height));
        float available = Math.max(0, width - pad * 2);
        float buttonW = Math.max(0,(available - UiTokens.BUTTON_GAP) / 2);
        float bottom = box.bottom() - pad;
        var confirm = new UiLayout.Rect(box.x() + pad + buttonW + UiTokens.BUTTON_GAP,
                bottom - UiTokens.BUTTON_H, buttonW, UiTokens.BUTTON_H);
        var cancel = new UiLayout.Rect(box.x() + pad, confirm.y(), buttonW, UiTokens.BUTTON_H);
        return new ConfirmationLayout(box, cancel, confirm, pad);
    }
}
