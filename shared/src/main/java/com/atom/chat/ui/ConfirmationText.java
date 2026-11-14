package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import java.util.ArrayList;
import java.util.List;

/** Fit explicit short paragraphs before wrapping; never shrink below the existing readable floor. */
record ConfirmationText(float size,float lineHeight,List<String> lines) {
    static ConfirmationText fit(String text,float width,float requestedSize) {
        float size=Math.max(UiTokens.DIALOG_MIN_TEXT_SIZE,requestedSize);
        var font=FontManager.font(size);
        float widest=0;
        for(String paragraph:text.split("\n")) widest=Math.max(widest,SkiaFontRenderer.getStringWidth(font,paragraph));
        if(widest>width)size=Math.max(UiTokens.DIALOG_MIN_TEXT_SIZE,size*width/widest);
        return wrap(text,width,size);
    }
    /** Fixed-size wrapping for tutorial copy: width changes never lower the glyph size. */
    static ConfirmationText wrap(String text,float width,float requestedSize) {
        float size=Math.max(UiTokens.DIALOG_MIN_TEXT_SIZE,requestedSize);
        var font=FontManager.font(size);
        var lines=new ArrayList<String>();
        for(String paragraph:text.split("\n",-1)) {
            var wrapped=new ArrayList<>(SkiaFontRenderer.wrapWords(font,paragraph,width));
            if(wrapped.size()>1) {
                int last=wrapped.size()-1;String tail=wrapped.get(last),previous=wrapped.get(last-1);
                if(tail.codePointCount(0,tail.length())==1&&!previous.isEmpty()
                        &&Character.UnicodeScript.of(tail.codePointAt(0))==Character.UnicodeScript.HAN
                        &&Character.UnicodeScript.of(previous.codePointBefore(previous.length()))==Character.UnicodeScript.HAN) {
                    int split=previous.offsetByCodePoints(previous.length(),-1);
                    String balanced=previous.substring(split)+tail;
                    if(SkiaFontRenderer.getStringWidth(font,balanced)<=width) {
                        wrapped.set(last-1,previous.substring(0,split));wrapped.set(last,balanced);
                    }
                }
            }
            lines.addAll(wrapped);
        }
        return new ConfirmationText(size,ConfirmationLayout.TEXT_LINE*size/UiTokens.SETTINGS_TILE_SUB,List.copyOf(lines));
    }
}
