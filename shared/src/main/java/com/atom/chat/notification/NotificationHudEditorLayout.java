package com.atom.chat.notification;

import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;

/** The editor paints and hits the exact same handle and toolbar rectangles. */
public record NotificationHudEditorLayout(UiLayout.Rect toolbar, UiLayout.Rect reset,
                                          UiLayout.Rect cancel, UiLayout.Rect save, UiLayout.Rect handle) {
    public static NotificationHudEditorLayout of(float width,float height,UiLayout.Rect banner) {
        float gap=UiTokens.PANEL_TOP_GAP, pad=UiTokens.PANEL_BOTTOM_PAD;
        float w=Math.max(1,Math.min(UiTokens.HUD_EDITOR_TOOLBAR_W,width-pad*2));
        float h=UiTokens.ACTION_BUTTON_SIZE*3;
        float y=banner.bottom()>height-h-pad*2 ? pad : Math.max(pad,height-h-pad);
        var toolbar=new UiLayout.Rect((width-w)/2,y,w,h);
        float buttonW=(w-pad*2-gap*2)/3;
        float buttonY=y+h-pad-UiTokens.BUTTON_H;
        float x=toolbar.x()+pad;
        float handleSize=UiTokens.EDGE_CONTROL_INSET*3;
        return new NotificationHudEditorLayout(toolbar,
                new UiLayout.Rect(x,buttonY,buttonW,UiTokens.BUTTON_H),
                new UiLayout.Rect(x+buttonW+gap,buttonY,buttonW,UiTokens.BUTTON_H),
                new UiLayout.Rect(x+2*(buttonW+gap),buttonY,buttonW,UiTokens.BUTTON_H),
                new UiLayout.Rect(banner.right()-handleSize/2,banner.bottom()-handleSize/2,handleSize,handleSize));
    }
}
