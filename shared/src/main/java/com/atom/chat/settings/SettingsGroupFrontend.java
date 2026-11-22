package com.atom.chat.settings;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.render.CardShadowReach;
import com.atom.chat.render.CardBackdrop;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.*;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.ToDoubleFunction;

/** Extracted settings group geometry and surface motion. No page data, navigation or form state. */
public final class SettingsGroupFrontend {
    private SettingsGroupFrontend(){}
    public static final float FOLD_TAU_MS=50f,MIN_DRAWN=.01f;
    public record Slots(float[] tops,float[] heights,float contentHeight){}
    public static boolean stepFolds(Map<String,Float> expansion,Set<String> collapsed,float dt){
        boolean changed=false;
        for(var entry:expansion.entrySet()){
            float next=UiMotion.expApproach(entry.getValue(),collapsed.contains(entry.getKey())?0:1,dt,FOLD_TAU_MS);
            changed|=Float.compare(next,entry.getValue())!=0;entry.setValue(next);
        }
        return changed;
    }
    public static <K> void stepHover(Map<K,Float> hover,K selected,float dt){
        if(selected!=null)hover.putIfAbsent(selected,0f);
        for(var entry:hover.entrySet())entry.setValue(UiMotion.approach(entry.getValue(),Objects.equals(entry.getKey(),selected)?1:0,dt,UiMotion.HOVER_MS));
    }
    public static float advance(float height,float expansion,String group,String nextGroup,float nextExpansion){
        return height*expansion+(group!=null&&group.equals(nextGroup)?0:UiTokens.SETTINGS_ROW_GAP*nextExpansion);
    }
    public static Slots layout(float[] full,String[] groups,ToDoubleFunction<String> expansion){
        float[] tops=new float[full.length],heights=new float[full.length];float y=0;
        for(int i=0;i<full.length;i++){
            float e=groups[i]==null?1:(float)expansion.applyAsDouble(groups[i]);tops[i]=y;heights[i]=full[i]*e;y+=heights[i];
            if(i+1<full.length)y+=advance(0,0,groups[i],groups[i+1],groups[i+1]==null?1:(float)expansion.applyAsDouble(groups[i+1]));
        }
        return new Slots(tops,heights,y);
    }
    public static float chipBand(){return UiTokens.CHIP_PILL_H+UiTokens.TAB_EDGE_PAD*2;}
    public static float contentTop(UiLayout layout,float chipBand){return layout.list.y()+UiTokens.ROOT_CONTENT_GAP+chipBand;}
    public record Motion(float cx,float cy,float scale,float dx,float dy,float alpha){
        public UiLayout.Rect map(UiLayout.Rect r){return new UiLayout.Rect(cx+(r.x()-cx)*scale+dx,cy+(r.y()-cy)*scale+dy,r.w()*scale,r.h()*scale);}
        public float x(float value){return cx+(value-cx-dx)/scale;}
        public float y(float value){return cy+(value-cy-dy)/scale;}
    }
    public static Motion motion(UiLayout.Rect rect,UiStagger.Entrance entrance,int slot,long now,UiStagger.Axis axis){
        return new Motion(rect.x()+rect.w()/2,rect.y()+rect.h()/2,UiStagger.itemStyleZoomScale(entrance,slot,now,axis),UiStagger.itemStyleSlidePx(entrance,slot,now,axis),UiStagger.itemRise(entrance,slot,now,axis),UiStagger.itemAlpha(entrance,slot,now));
    }
    /** Settings' outer stagger: padded local layer, rise, then style scale/drift. */
    public static int stagger(Canvas canvas,UiLayout.Rect rect,Motion m,IntSupplier paint){
        canvas.save();Paint fade=null;
        float maskOpacity = CardBackdrop.pushMaskOpacity(m.alpha());
        try{
            if(m.alpha()<.995f){fade=new Paint().setAlphaf(m.alpha());canvas.saveLayer(CardShadowReach.layerRect(rect.x(),rect.y(),rect.w(),rect.h(),m.dy(),m.dx()),fade);}
            canvas.translate(m.dx(),m.dy());canvas.translate(m.cx(),m.cy());canvas.scale(m.scale(),m.scale());canvas.translate(-m.cx(),-m.cy());
            return paint.getAsInt();
        }finally{CardBackdrop.restoreMaskOpacity(maskOpacity);if(fade!=null){fade.close();canvas.restore();}canvas.restore();}
    }
    /** Exact merged settings surface: fold fade/lift, press, one rounded card and clipped rows. */
    public static int merged(Canvas canvas,UiLayout.Rect card,float expansion,PressScale press,IntSupplier paintRows){
        return merged(canvas, card, expansion, press, () -> {
            drawCardSurface(canvas, card);
            clipCard(canvas, card);
        }, paintRows);
    }
    public static int merged(Canvas canvas, UiLayout.Rect card, float expansion, PressScale press,
                             Runnable surface, IntSupplier paintRows) {
        canvas.save();Paint foldFade=null;
        float maskOpacity = CardBackdrop.pushMaskOpacity(expansion);
        try{
            if(expansion<.995f){
                canvas.translate(0,UiTokens.SETTINGS_FOLD_LIFT*(1-expansion));float pad=UiTokens.s(12); // bare-value: extracted existing settings fold layer reach
                foldFade=new Paint().setAlphaf(expansion);canvas.saveLayer(io.github.humbleui.types.Rect.makeXYWH(card.x()-pad,card.y()-pad,card.w()+pad*2,card.h()+pad*2),foldFade);
            }
            if(press!=null)press.begin(canvas,card.x()+card.w()/2,card.y()+card.h()/2);
            try{
                canvas.save();try{surface.run();return paintRows.getAsInt();}finally{canvas.restore();}
            }finally{if(press!=null)canvas.restore();}
        }finally{CardBackdrop.restoreMaskOpacity(maskOpacity);if(foldFade!=null){foldFade.close();canvas.restore();}canvas.restore();}
    }
    public static void drawCardSurface(Canvas canvas, UiLayout.Rect card) {
        AtomChatAppearance.drawCard(canvas, card.x(), card.y(), card.w(), card.h(),
                UiTokens.settingsRowRadius(AtomChatAppearance.theme()), 0);
    }

    public static void clipCard(Canvas canvas, UiLayout.Rect card) {
        CornerGeometry.clip(canvas, card.x(), card.y(), card.w(), card.h(),
                UiTokens.settingsRowRadius(AtomChatAppearance.theme()));
    }

    public static void divider(Canvas canvas,UiLayout.Rect card,float y){
        CornerGeometry.drawRoundedRect(canvas,card.x()+UiTokens.SETTINGS_ROW_PAD,y,card.w()-UiTokens.SETTINGS_ROW_PAD*2,UiTokens.s(1),0,UiTokens.hairline(AtomChatAppearance.theme())); // bare-value: extracted settings divider thickness
    }
}
