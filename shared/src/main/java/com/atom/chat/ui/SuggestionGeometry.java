package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
/** Candidate rows are laid out once in panel units, then mapped to the vanilla rectangle for foreign UI masking. */
public record SuggestionGeometry(UiLayout.Rect bounds, int rows, float rowHeight) {
    public static final float ROW_HEIGHT = UiTokens.SUGGESTION_ROW_HEIGHT;
    public static final float PAD = UiTokens.SUGGESTION_PADDING;
    public static SuggestionGeometry of(UiLayout layout, int count, float textWidth) {
        float gap = UiTokens.PANEL_TOP_GAP;
        float bottom = ComposerPopupLayout.bottom(layout);
        float top = layout.header.bottom() + gap;
        float available = Math.max(1, bottom - top);
        float rowHeight = Math.min(ROW_HEIGHT, available);
        int rows = Math.max(1, Math.min(count, capacity(layout)));
        float width = Math.max(1, Math.min(layout.inputBar.w(), Math.max(UiTokens.SUGGESTION_MIN_WIDTH, textWidth + PAD * 2)));
        float left = ComposerPopupLayout.left(layout);
        return new SuggestionGeometry(new UiLayout.Rect(left, bottom - rows * rowHeight, width, rows * rowHeight), rows, rowHeight);
    }
    /** The vanilla viewport limit and the custom drawing must have the same number of slots. */
    public static int capacity(UiLayout layout) {
        float available = ComposerPopupLayout.bottom(layout) - layout.header.bottom() - UiTokens.PANEL_TOP_GAP;
        return Math.max(1, Math.min(10, (int)(available / ROW_HEIGHT)));
    }
    public UiLayout.Rect row(int index) { return new UiLayout.Rect(bounds.x(), bounds.y() + index * rowHeight, bounds.w(), rowHeight); }
    public int hit(float x, float y) {
        return x >= bounds.x() && x < bounds.right() && y >= bounds.y() && y < bounds.bottom()
                ? Math.min(rows - 1, (int) ((y - bounds.y()) / rowHeight)) : -1;
    }
}
