package com.atom.chat.settings;

import com.atom.chat.ui.AtomChatAppearance;
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
    private static float insetX(UiLayout layout, int count) {
        float width = layout.chipBar.w() / count;
        return count >= 4 && width < LayoutTokens.CHIP_COMPACT_SLOT ? LayoutTokens.CHIP_COMPACT_INSET : UiTokens.TAB_EDGE_PAD;
    }

    private static UiLayout.Rect cell(UiLayout layout, int index, int count) {
        float inset = insetX(layout, count);
        var bar = layout.chipBar;
        float width = bar.w() / count;
        return new UiLayout.Rect(bar.x() + width * index + inset, bar.y() + UiTokens.TAB_EDGE_PAD,
                Math.max(0, width - 2 * inset), UiTokens.CHIP_PILL_H);
    }

    public int hit(UiLayout layout,float x,float y){return hit(layout,3,x,y);}
    public static int hit(UiLayout layout,int count,float x,float y){
        if(count==0)return -1;float cellW=layout.chipBar.w()/count,inset=insetX(layout,count);
        for(int i=0;i<count;i++){
            var cell=cell(layout,i,count);float cx=layout.chipBar.x()+cellW*(i+.5f),cy=layout.chipBar.y()+layout.chipBar.h()/2;
            if(Math.abs(x-cx)<=cell.w()/2+inset&&Math.abs(y-cy)<=cell.h()/2+UiTokens.TAB_EDGE_PAD)return i;
        }return -1;
    }
    public void update(UiLayout layout,int count,int selected,int pressed,float mx,float my,float dt){
        this.selected=selected;if(count==0||layout.chipBar.w()<=0)return;
        if (lastIndex < 0 || !Animations.enabled()) indicator.setValue(selected);
        else if (selected != lastIndex) indicator.animateTo(UiMotion.TAB_MS, selected);
        lastIndex=selected;indicator.update(dt);int hovered=hit(layout,count,mx,my);
        for(int i=0;i<count;i++){
            boolean over=i==hovered;float next=UiMotion.approach(hover.getOrDefault(i,0f),over?1:0,dt,UiMotion.HOVER_MS);
            if(next<.01f&&!over)hover.remove(i);else hover.put(i,next);
            scales.computeIfAbsent(i,k->PressScale.bounce()).update(over,over&&pressed==i,dt,Animations.enabled(),cell(layout,i,count).w());
        }
    }
    public void draw(Canvas canvas,UiLayout layout,List<String> labels,int selected,int secondary){
        int count=labels.size();if(count==0||layout.chipBar.w()<=0)return;
        var first=cell(layout,0,count);
        float inset=insetX(layout,count);
        boolean compact=inset<UiTokens.TAB_EDGE_PAD;
        float fontSize=compact?LayoutTokens.CHIP_COMPACT_FONT:UiTokens.SETTINGS_TILE_SUB;
        float textInset=compact?LayoutTokens.CHIP_COMPACT_INSET:LayoutTokens.CHIP_LABEL_PADDING;
        float x=first.x()+indicator.getValue()*layout.chipBar.w()/count;
        var theme = AtomChatAppearance.theme();
        float radius = ChromeMaterial.circularInsetCornerRadius(first.w(), first.h(), UiTokens.chromeRadius(theme), inset);
        int accent = UiTokens.accentFill(theme);
        var active = scales.get(selected);
        boolean scaleIndicator = active != null && active.scale() != 1F;
        if (scaleIndicator) active.begin(canvas, x + first.w()/2, first.y() + first.h()/2);
        try {
            SettingsCapsuleCoverage.draw(canvas, x, first.y(), first.w(), first.h(), radius, accent);
        } finally {
            if (scaleIndicator) canvas.restore();
        }
        var regular = FontManager.font(fontSize);
        var bold = FontManager.boldFont(fontSize);
        for(int i=0;i<count;i++){
            var cell=cell(layout,i,count);boolean on=i==selected;var font=on?bold:regular;
            var scale = scales.get(i);
            boolean scaleLabel = scale != null && scale.scale() != 1F;
            if (scaleLabel) scale.begin(canvas, cell.x() + cell.w()/2, cell.y() + cell.h()/2);
            try{
                if(!on&&hover.getOrDefault(i,0f)>.01f)SettingsCapsuleCoverage.draw(canvas,cell.x(),cell.y(),cell.w(),cell.h(),radius,UiTokens.cardHover(hover.get(i), theme));
                String label = SkiaFontRenderer.truncate(font, labels.get(i), Math.max(0, cell.w() - textInset));
                int ink = on ? UiTokens.onAccent(accent) : secondary;
                float labelX = cell.x() + cell.w() / 2F - SkiaFontRenderer.getStringWidth(font, label) / 2F;
                float baseline = SkiaFontRenderer.centerBaselineY(font, cell.y() + cell.h() / 2F);
                if (!on && TextSurface.CAPSULE.shadowed()) {
                    SettingsLabelShadow.draw(canvas, font, label, labelX, baseline);
                }
                SkiaFontRenderer.drawText(canvas, font, label, labelX, baseline, ink);
            }finally{if(scaleLabel)canvas.restore();}
        }
    }
    public void render(Canvas canvas,UiLayout layout,List<String> labels,float mx,float my,float dt){update(layout,labels.size(),selected,pressed,mx,my,dt);draw(canvas,layout,labels,selected,(com.atom.chat.config.AtomChatConfig.get().textSecondaryColor&0xffffff)|(200<<24));}
}
