package com.atom.chat.settings;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import java.util.*;

/** Settings' title/description spacing and compact numeric control geometry. */
public final class SettingsRowText {
    private SettingsRowText(){}
    public static final float NUMBER_W=UiTokens.s(80),UNIT_W=UiTokens.s(36); // bare-value: approved compact number and external unit
    public record Text(List<String> titles,List<String> descriptions,boolean stacked,float titleH,float descriptionH,float height){}
    public static Text measure(String title,String description,float width,float reserved,float controlH){
        return measure(title,description,width,reserved,controlH,false);
    }
    /** Ordinary settings retain their original character-title/compact-description flow. */
    public static Text measure(String title,String description,float width,float reserved,float controlH,boolean words){
        var main=FontManager.font(UiTokens.SETTINGS_TILE_TITLE);var sub=FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float inner=Math.max(1,width-UiTokens.SETTINGS_ROW_PAD*2);boolean stacked=SkiaFontRenderer.getStringWidth(main,title)>inner-reserved;
        var titles=stacked?(words?SkiaFontRenderer.wrapWords(main,title,inner):SkiaFontRenderer.wrap(main,title,inner)):List.of(title);
        var descriptions=description.isEmpty()?List.<String>of():stacked||words?SkiaFontRenderer.wrapWords(sub,description,stacked?inner:Math.max(1,inner-reserved)):SkiaFontRenderer.wrap(sub,description,Math.max(1,inner-reserved));
        float th=SkiaFontRenderer.getHeight(main),dh=SkiaFontRenderer.getHeight(sub);
        float height=stacked?UiTokens.SETTINGS_ROW_PAD*2+titles.size()*th+descriptions.size()*dh+controlH+UiTokens.SETTINGS_COMPACT_GAP*2:
                UiTokens.SETTINGS_ROW_H+Math.max(0,descriptions.size()-1)*dh;
        return new Text(titles,descriptions,stacked,th,dh,Math.max(UiTokens.SETTINGS_ROW_H,height));
    }
    public static float draw(Canvas canvas,UiLayout.Rect rect,Text text,int primary,int secondary){
        var title=FontManager.font(UiTokens.SETTINGS_TILE_TITLE);var sub=FontManager.font(UiTokens.SETTINGS_TILE_SUB);float x=rect.x()+UiTokens.SETTINGS_ROW_PAD;
        float top=rect.y()+UiTokens.SETTINGS_ROW_PAD;
        if(text.stacked()){
            for(var line:text.titles()){SkiaFontRenderer.drawText(canvas,title,line,x,SkiaFontRenderer.centerBaselineY(title,top+text.titleH()/2),primary);top+=text.titleH();}
            top+=UiTokens.SETTINGS_COMPACT_GAP;
            for(var line:text.descriptions()){SkiaFontRenderer.drawText(canvas,sub,line,x,SkiaFontRenderer.centerBaselineY(sub,top+text.descriptionH()/2),secondary);top+=text.descriptionH();}
        }else{
            float titleCenter=rect.y()+UiTokens.s(20),descCenter=rect.y()+UiTokens.s(37); // bare-value: existing settings title/description centers
            SkiaFontRenderer.drawText(canvas,title,text.titles().get(0),x,SkiaFontRenderer.centerBaselineY(title,titleCenter),primary);
            float blockH=text.descriptions().size()*text.descriptionH();
            if(text.descriptions().size()>1)descCenter=rect.y()+Math.max(UiTokens.s(37),text.height()-UiTokens.s(8)-blockH/2); // bare-value: unchanged settings wrapped-description placement
            SkiaFontRenderer.drawLines(canvas,sub,text.descriptions(),x,descCenter,text.descriptionH(),secondary);
            top=rect.y()+text.height()-UiTokens.SETTINGS_ROW_PAD;
        }
        return top;
    }
    public static void description(Canvas canvas,UiLayout.Rect rect,Font font,String text,float x,float maxWidth,int color){
        if(text==null||text.isEmpty()||maxWidth<=0||SkiaFontRenderer.getStringWidth(font,text)<=maxWidth){
            SkiaFontRenderer.drawText(canvas,font,text==null?"":text,x,SkiaFontRenderer.centerBaselineY(font,rect.y()+UiTokens.s(37)),color);return; // bare-value: extracted settings description center
        }
        var lines=SkiaFontRenderer.wrap(font,text,maxWidth);float lineH=SkiaFontRenderer.getHeight(font),blockH=lines.size()*lineH;
        float centerY=rect.y()+Math.max(UiTokens.s(37),rect.h()-UiTokens.s(8)-blockH/2); // bare-value: extracted settings description placement
        SkiaFontRenderer.drawLines(canvas,font,lines,x,centerY,lineH,color);
    }
    public static UiLayout.Rect number(UiLayout.Rect row,boolean stacked){
        float h=SettingsFormField.BASE_H;
        return new UiLayout.Rect(row.right()-UiTokens.SETTINGS_ROW_PAD-NUMBER_W-UNIT_W,stacked?row.bottom()-UiTokens.SETTINGS_ROW_PAD-h:row.y()+(row.h()-h)/2,NUMBER_W,h);
    }
    public static Text measureNumber(String title,String description,float width){
        return measure(title,description,width,NUMBER_W+UNIT_W+UiTokens.SETTINGS_COMPACT_GAP,SettingsFormField.BASE_H,true);
    }
    public static void drawNumberUnit(Canvas canvas,UiLayout.Rect field,String unit,int color){
        var font=FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        SkiaFontRenderer.drawText(canvas,font,unit,field.right()+UiTokens.SETTINGS_ROW_GAP,
                SkiaFontRenderer.centerBaselineY(font,field.y()+field.h()/2),color);
    }
}
