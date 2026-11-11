package com.atom.chat.onboarding;

import com.atom.chat.ui.UiLayout.Rect;
import com.atom.lib.design.atomchat.UiTokens;
import java.util.List;

/** One geometry for annotation painting and pointer routing, recomputed after resize. */
public record TutorialLayout(Rect panel, Rect target, Rect card, List<Rect> buttons) {
    public static TutorialLayout of(Rect panel, Rect target, float contentHeight) {
        float pad = UiTokens.SETTINGS_ROW_PAD, gap = UiTokens.BUTTON_GAP;
        float width = Math.max(0, panel.w() - pad * 2);
        float height = Math.min(Math.max(0, panel.h() - pad * 2), contentHeight);
        float aboveGap=Math.max(0,target.y()-panel.y()-pad-gap);
        float belowGap=Math.max(0,panel.bottom()-pad-target.bottom()-gap);
        float available=Math.max(aboveGap,belowGap);
        float compactMinimum=pad*2+UiTokens.DIALOG_LINE_HEIGHT*3+UiTokens.BUTTON_H+gap;
        boolean compact=available>=compactMinimum&&available<height;
        if(compact)height=available;
        float below = target.bottom() + gap;
        float y = panel.bottom() - pad - height;
        if (target.y() - pad - gap >= height) y = target.y() - gap - height;
        else if (below + height <= panel.bottom() - pad) y = below;
        y = Math.max(panel.y() + pad, Math.min(y, panel.bottom() - pad - height));
        Rect card = new Rect(panel.x() + pad, y, width, height);
        float inner = Math.max(0, width - pad * 2);
        if(compact) {
            float buttonWidth=Math.max(0,(inner-gap*3)/4);
            float buttonHeight=Math.min(UiTokens.BUTTON_H,Math.max(0,height-pad*2));
            var buttons=new java.util.ArrayList<Rect>(4);
            for(int i=0;i<4;i++)buttons.add(new Rect(card.x()+pad+i*(buttonWidth+gap),card.bottom()-pad-buttonHeight,buttonWidth,buttonHeight));
            return new TutorialLayout(panel,target,card,List.copyOf(buttons));
        }
        float buttonWidth = Math.max(0, (inner - gap) / 2);
        float buttonHeight = Math.min(UiTokens.BUTTON_H, Math.max(0, (height - pad * 2 - gap) / 2));
        float footer = card.bottom() - pad - buttonHeight * 2 - gap;
        return new TutorialLayout(panel, target, card, List.of(
                new Rect(card.x() + pad, footer, buttonWidth, buttonHeight),
                new Rect(card.x() + pad + buttonWidth + gap, footer, buttonWidth, buttonHeight),
                new Rect(card.x() + pad, footer + buttonHeight + gap, buttonWidth, buttonHeight),
                new Rect(card.x() + pad + buttonWidth + gap, footer + buttonHeight + gap, buttonWidth, buttonHeight)));
    }
    public int buttonAt(float x, float y) {
        for (int i=0;i<buttons.size();i++) if (buttons.get(i).contains(x,y)) return i;
        return -1;
    }
}
