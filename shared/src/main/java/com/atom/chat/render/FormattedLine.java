package com.atom.chat.render;

import com.atom.chat.text.RichText;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import java.util.*;

/** An ordered native text line; formatting metadata stays separate from its literal source characters. */
public record FormattedLine(List<Run> runs) {
    public record Run(String text,Integer color,boolean underline) { }
    public FormattedLine { runs=List.copyOf(runs); }
    public static FormattedLine plain(String text) { return new FormattedLine(List.of(new Run(text,null,false))); }
    /** Candidate String labels use the existing chat/legacy colour language, just like vanilla drawString(String). */
    public static FormattedLine legacy(String text) {
        var out=new ArrayList<Run>();
        for(var run:RichText.literal(text).runs()) out.add(new Run(run.text(),run.style().getColor()==null ? null
                : RichTextRenderer.effectiveColor(run.style(),0xFF000000)&0xFFFFFF,run.style().isUnderlined()));
        return new FormattedLine(out);
    }
    public String text() { var out=new StringBuilder();for(var run:runs)out.append(run.text());return out.toString(); }
    public float width(Font font) { float value=0;for(var run:runs)value+=PlainText.width(font,run.text());return value; }
    public void draw(Canvas canvas,Font font,float x,float baseline,int fallback) {
        draw(canvas,font,x,baseline,fallback,null);
    }
    /** Input syntax keeps its native hue but uses the existing UI contrast floor on the configured card. */
    public void drawOnSurface(Canvas canvas,Font font,float x,float baseline,int fallback,int surface) {
        draw(canvas,font,x,baseline,fallback,surface);
    }
    private void draw(Canvas canvas,Font font,float x,float baseline,int fallback,Integer surface) {
        for(var run:runs) {
            int color=run.color()==null ? fallback : (fallback&0xFF000000)|(run.color()&0xFFFFFF);
            if(surface!=null && run.color()!=null) color=(color&0xFF000000)
                    | (com.atom.chat.avatar.ColorUtil.readableAccent(color,surface,com.atom.lib.design.atomchat.UiTokens.MIN_GLYPH_CONTRAST)&0xFFFFFF);
            float width=PlainText.width(font,run.text());PlainText.draw(canvas,font,run.text(),x,baseline,color);
            if(run.underline())RichTextRenderer.drawUnderline(canvas,font,x,baseline,width,color);
            x+=width;
        }
    }
    public static final class Builder {
        private final List<Run> runs=new ArrayList<>();
        private final StringBuilder text=new StringBuilder();
        private Integer color;
        private boolean underline;
        public void append(int cp,Integer color,boolean underline) {
            if(!text.isEmpty() && (!Objects.equals(this.color,color)||this.underline!=underline)) flush();
            this.color=color;this.underline=underline;text.appendCodePoint(cp);
        }
        private void flush() {if(!text.isEmpty()){runs.add(new Run(text.toString(),color,underline));text.setLength(0);}}
        public FormattedLine finish() {flush();return new FormattedLine(runs);}
    }
}
