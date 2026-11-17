package com.atom.chat.config;

import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.settings.SettingsGroupFrontend;
import static com.atom.lib.design.atomchat.UiTokens.s;

/** Fixed chrome and scroll viewport share the settings page's geometry tokens. */
public record ServerConfigLayout(UiLayout.Rect viewport, UiLayout.Rect save, UiLayout.Rect status) {
    public static ServerConfigLayout of(UiLayout layout) {
        boolean flush=layout.header.x()==layout.panelX;
        float bottom = layout.panelY+layout.panelH-(flush?0:UiTokens.PANEL_BOTTOM_PAD);
        float actionH = ServerConfigTokens.CONTROL_H, gap = UiTokens.SETTINGS_ROW_PAD;
        float top = layout.list.y()+SettingsGroupFrontend.chipBand();
        var save = new UiLayout.Rect(layout.header.x()+UiTokens.TAB_EDGE_PAD,bottom-UiTokens.TAB_EDGE_PAD-actionH,
                layout.header.w()-UiTokens.TAB_EDGE_PAD*2,actionH);
        var status = new UiLayout.Rect(layout.list.x(), save.y() - ServerConfigTokens.STATUS_GAP, layout.list.w(), ServerConfigTokens.TEXT_LINE_H);
        return new ServerConfigLayout(new UiLayout.Rect(layout.list.x()+UiTokens.ROW_CLIP_INSET, top, layout.list.w()-UiTokens.ROW_CLIP_INSET*2,
                Math.max(0, status.y() - gap - top)), save, status);
    }
    public UiLayout.Rect bar(){return new UiLayout.Rect(save.x()-UiTokens.TAB_EDGE_PAD,save.y()-UiTokens.TAB_EDGE_PAD,save.w()+UiTokens.TAB_EDGE_PAD*2,save.h()+UiTokens.TAB_EDGE_PAD*2);}
    public UiLayout.Rect scrollViewport(){return new UiLayout.Rect(viewport.x()-UiTokens.ROW_CLIP_INSET,viewport.y(),viewport.w()+UiTokens.ROW_CLIP_INSET*2,viewport.h());}
}
