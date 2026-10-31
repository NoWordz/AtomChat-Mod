package com.atom.chat.tools;

import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.ui.ComposerPopupLayout;

/** The tools share the shell's dimensions; paint and input use these same rectangles. */
public final class ToolLayout {
    private ToolLayout() { }
    public static final float MENU_ROW_H = UiTokens.TOOL_MENU_ROW_H;
    public static final float GAP = UiTokens.PANEL_TOP_GAP;

    public static UiLayout.Rect menu(UiLayout source) {
        return menu(source, 3);
    }
    public static UiLayout.Rect menu(UiLayout source, int rows) {
        float width = Math.min(UiTokens.TOOL_MENU_WIDTH, source.panelW - UiTokens.LIST_PAD_X * 2);
        float height = Math.min(MENU_ROW_H * Math.min(7, Math.max(1, rows)),
                Math.max(MENU_ROW_H, ComposerPopupLayout.bottom(source) - source.header.bottom()));
        return ComposerPopupLayout.place(source, width, height);
    }
}
