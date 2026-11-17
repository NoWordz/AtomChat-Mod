package com.atom.chat.settings;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.*;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.Canvas;
import java.util.*;

/** The real settings chip implementation, shared by settings sections and server configuration. */
public final class SettingsChipBar {
    private final Animator indicator=new Animator(Easing::easeInOutCubic);
    private final Map<Integer,Float> hover=new HashMap<>();
    private final Map<Integer,PressScale> scales=new HashMap<>();
    private int selected,pressed=-1,lastIndex=-1;
    public int selected(){return selected;}
    public void select(int index){if(index<0||index>2||index==selected)return;selected=index;pressed=index;}
    public void release(){pressed=-1;}
    public int hit(UiLayout layout,float x,float y){return hit(layout,3,x,y);}
    public static int hit(UiLayout layout,int count,float x,float y){
        if(count==0)return -1;float cellW=layout.chipBar.w()/count,inset=UiTokens.TAB_EDGE_PAD;
        for(int i=0;i<count;i++){
            var cell=layout.chipRect(i,count);float cx=layout.chipBar.x()+cellW*(i+.5f),cy=layout.chipBar.y()+layout.chipBar.h()/2;
            if(Math.abs(x-cx)<=cell.w()/2+inset&&Math.abs(y-cy)<=cell.h()/2+inset)return i;
        }return -1;
    }
    public void update(UiLayout layout,int count,int selected,int pressed,float mx,float my,float dt){
        this.selected=selected;if(count==0||layout.chipBar.w()<=0)return;
        if(lastIndex<0)indicator.setValue(selected);else if(selected!=lastIndex)indicator.animateTo(UiMotion.TAB_MS,selected);
        lastIndex=selected;indicator.update(dt);int hovered=hit(layout,count,mx,my);
        for(int i=0;i<count;i++){
            boolean over=i==hovered;float next=UiMotion.approach(hover.getOrDefault(i,0f),over?1:0,dt,UiMotion.HOVER_MS);
            if(next<.01f&&!over)hover.remove(i);else hover.put(i,next);
            scales.computeIfAbsent(i,k->PressScale.bounce()).update(over,over&&pressed==i,dt,Animations.enabled(),layout.chipRect(i,count).w());
        }
    }
    public void draw(Canvas canvas,UiLayout layout,List<String> labels,int selected,int secondary){
        int count=labels.size();if(count==0||layout.chipBar.w()<=0)return;
        var first=layout.chipRect(0,count);float inset=UiTokens.TAB_EDGE_PAD;
        float x=layout.chipBar.x()+indicator.getValue()*layout.chipBar.w()/count+inset;
        var active=scales.get(selected);if(active!=null)active.begin(canvas,x+first.w()/2,first.y()+first.h()/2);
        try{SkiaDraw.drawRoundedRect(canvas,x,first.y(),first.w(),first.h(),UiTokens.insetRadius(UiTokens.chromeRadius(),inset,first.w(),first.h()),UiTokens.accentFill());}finally{if(active!=null)canvas.restore();}
        for(int i=0;i<count;i++){
            var cell=layout.chipRect(i,count);boolean on=i==selected;var font=on?FontManager.boldFont(UiTokens.SETTINGS_TILE_SUB):FontManager.font(UiTokens.SETTINGS_TILE_SUB);
            var scale=scales.get(i);if(scale!=null)scale.begin(canvas,cell.x()+cell.w()/2,cell.y()+cell.h()/2);
            try{
                if(!on&&hover.getOrDefault(i,0f)>.01f)SkiaDraw.drawRoundedRect(canvas,cell.x(),cell.y(),cell.w(),cell.h(),UiTokens.insetRadius(UiTokens.chromeRadius(),inset,cell.w(),cell.h()),UiTokens.cardHover(hover.get(i)));
                SkiaFontRenderer.drawTextCentered(canvas,font,SkiaFontRenderer.truncate(font,labels.get(i),Math.max(0,cell.w()-UiTokens.s(10))),cell.x()+cell.w()/2,cell.y()+cell.h()/2,on?UiTokens.onAccent(UiTokens.accentFill()):secondary); // bare-value: unchanged settings chip text inset
            }finally{if(scale!=null)canvas.restore();}
        }
    }
    public void render(Canvas canvas,UiLayout layout,List<String> labels,float mx,float my,float dt){update(layout,labels.size(),selected,pressed,mx,my,dt);draw(canvas,layout,labels,selected,(com.atom.chat.config.AtomChatConfig.get().textSecondaryColor&0xffffff)|(200<<24));}
}
