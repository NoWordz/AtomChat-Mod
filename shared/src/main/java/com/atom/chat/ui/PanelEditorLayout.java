package com.atom.chat.ui;

import com.atom.lib.design.atomchat.UiTokens;

/** Only restore and save; the panel border itself owns geometry editing. */
public record PanelEditorLayout(UiLayout.Rect toolbar, UiLayout.Rect title,
                                UiLayout.Rect reset, UiLayout.Rect save) {
    public static PanelEditorLayout of(float width, float height, UiLayout.Rect panel) {
        float pad = UiTokens.PANEL_BOTTOM_PAD, gap = UiTokens.BUTTON_GAP, row = UiTokens.BUTTON_H;
        float leftRoom = panel.x() - pad * 2, rightRoom = width - panel.right() - pad * 2;
        boolean outside = Math.max(leftRoom, rightRoom) >= UiTokens.DIALOG_STACK_WIDTH;
        boolean right = rightRoom >= leftRoom;
        float room = outside ? (right ? rightRoom : leftRoom) : width - pad * 2;
        float w = Math.max(1, Math.min(UiTokens.DIALOG_MAX_WIDTH, room));
        float h = pad * 3 + row * 2;
        float x = outside ? (right ? panel.right() + pad : pad) + (room - w) / 2 : (width - w) / 2;
        float y = Math.max(pad, height - h - pad);
        float buttonWidth = Math.max(1, (w - pad * 2 - gap) / 2);
        float buttonY = y + h - pad - row;
        return new PanelEditorLayout(new UiLayout.Rect(x, y, w, h),
                new UiLayout.Rect(x + pad, y + pad, w - pad * 2, row),
                new UiLayout.Rect(x + pad, buttonY, buttonWidth, row),
                new UiLayout.Rect(x + pad + buttonWidth + gap, buttonY, buttonWidth, row));
    }
    public PanelEditorLayout at(float x, float y) {
        float dx = x - toolbar.x(), dy = y - toolbar.y();
        return new PanelEditorLayout(offset(toolbar, dx, dy), offset(title, dx, dy),
                offset(reset, dx, dy), offset(save, dx, dy));
    }
    private static UiLayout.Rect offset(UiLayout.Rect rect, float dx, float dy) {
        return new UiLayout.Rect(rect.x() + dx, rect.y() + dy, rect.w(), rect.h());
    }
}
