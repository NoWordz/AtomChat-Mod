package com.atom.chat.ui;

import com.atom.chat.render.CornerGeometry;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.Canvas;

/** Existing ScrollSyncService scrollbar, shared with non-root settings forms. */
public final class SettingsScrollbar {
    private SettingsScrollbar() {}
    private static final float TRACK=UiTokens.s(6),MIN=UiTokens.s(30),GROW=UiTokens.s(3),EDGE=UiTokens.s(2),HIT=UiTokens.s(8); // bare-value: extracted unchanged scrollbar geometry
    public static boolean over(UiLayout.Rect list,ScrollController controller,float x,float y){
        float left=list.right()-TRACK-EDGE;return controller.getMaxScroll()>0&&x>=left-HIT&&x<=left+TRACK+HIT&&y>=list.y()&&y<=list.bottom();
    }
    public static void draw(Canvas canvas,UiLayout.Rect list,ScrollController controller,float x,float y,int accent,float dt){
        float max=controller.getMaxScroll();if(max<=0)return;
        float ratio=Math.min(1,list.h()/(list.h()+max)),thumb=Math.max(MIN,list.h()*ratio);
        float top=list.y()+(list.h()-thumb)*(controller.getScrollY()/max);
        if(controller.updateScrollbarFade(UiStagger.frameTimeMillis(),x,y,list,controller.isDragging(),TRACK,dt)<=0)return;
        if(controller.isDragging())controller.dragTo(y,list.h());
        float width=TRACK+controller.getScrollEmphasis()*GROW;
        int alpha=Math.min(255,Math.max(0,(int)((170+60*controller.getScrollEmphasis())*controller.getScrollBarAlpha())));
        CornerGeometry.drawRoundedRect(canvas,list.right()-TRACK-EDGE-(width-TRACK)/2,top,width,thumb,UiTokens.radius(width/(2*UiTokens.SCALE), AtomChatAppearance.theme()),(accent&0xffffff)|(alpha<<24));
    }
}
