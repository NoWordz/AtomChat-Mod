package com.atom.chat.settings;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.DesignHost;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One cached line/advance model for height, text, selection, caret and pointer coordinates. */
public final class SettingsFormField {
    private static final float TEXT_INSET=UiTokens.s(10),LINE_H=UiTokens.s(22),CARET_H=UiTokens.s(18); // bare-value: existing input typography
    public static final float BASE_H=UiTokens.s(32); // bare-value: approved compact single-line input height
    private static final float V_PAD=(BASE_H-LINE_H)/2;
    public final FormText edit=new FormText();
    private record Line(int start,int end,String text,float[] advances) {
        float x(int offset){return advances[Math.max(0,Math.min(advances.length-1,offset-start))];}
    }
    private final List<Line> lines=new ArrayList<>();
    private UiLayout.Rect rect;private float hover,shift,textTop;private int firstLine;
    private boolean focused,enabled=true,multiline,wrap,number;private int max=512;
    private char pendingHigh;
    private String shapedText;private float shapedWidth;private Font shapedFont;private boolean shapedMultiline;
    private long layoutBuilds;
    private int lastCaret=-1;private String visibleText;private float wheelRemainder;private long visibleShape=-1;
    public long layoutBuilds(){return layoutBuilds;}
    public float hoverWeight(){return hover;}
    public void focus(boolean value){if(value&&!focused)lastCaret=-1;focused=value;if(!value)pendingHigh=0;}public boolean focused(){return focused;}
    public void configure(boolean number,boolean multiline,int max){this.number=number;this.multiline=multiline;this.wrap=multiline;this.max=max;}
    public void wrap(boolean value){wrap=value;}
    private Font font(){return FontManager.font(number?UiTokens.FONT_INPUT:UiTokens.SETTINGS_TILE_TITLE);}
    public float preferredHeight(String text,float width){
        shape(text,width);return BASE_H+(wrap?Math.min(4,lines.size())-1:0)*LINE_H;
    }
    private void shape(String text,float width){
        var font=font();width=Math.max(1,width-TEXT_INSET*2);
        if(Objects.equals(shapedText,text)&&shapedWidth==width&&shapedFont==font&&shapedMultiline==wrap)return;
        shapedText=text;shapedWidth=width;shapedFont=font;shapedMultiline=wrap;layoutBuilds++;lines.clear();
        int start=0;float[] advances=new float[Math.min(64,text.length()+1)];
        for(int i=0;i<text.length();){
            int next=text.offsetByCodePoints(i,1);
            if(text.charAt(i)=='\n'){
                lines.add(new Line(start,i,text.substring(start,i),Arrays.copyOf(advances,i-start+1)));start=next;advances=new float[Math.min(64,text.length()-start+1)];
            }else{
                float w=SkiaFontRenderer.getStringWidth(font,text.substring(start,next));
                if(wrap&&i>start&&w>width){
                    lines.add(new Line(start,i,text.substring(start,i),Arrays.copyOf(advances,i-start+1)));start=i;advances=new float[Math.min(64,text.length()-start+1)];
                    w=SkiaFontRenderer.getStringWidth(font,text.substring(start,next));
                }
                if(next-start>=advances.length)advances=Arrays.copyOf(advances,Math.max(next-start+1,advances.length*2));
                for(int j=i-start+1;j<next-start;j++)advances[j]=advances[i-start];
                advances[next-start]=w;
            }
            i=next;
        }
        lines.add(new Line(start,text.length(),text.substring(start),Arrays.copyOf(advances,text.length()-start+1)));
    }
    public void paint(Canvas canvas,UiLayout.Rect bounds,String value,boolean enabled,boolean error,float mx,float my,float dt){
        var config=com.atom.chat.config.AtomChatConfig.get();
        paint(canvas,bounds,value,enabled,error,mx,my,dt,AtomChatAppearance.theme(),
                config.textPrimaryColor,UiStagger.frameTimeMillis());
    }
    public void paint(Canvas canvas,UiLayout.Rect bounds,String value,boolean enabled,boolean error,float mx,float my,float dt,
                      DesignHost appearance,int primary,long millis){
        rect=bounds;this.enabled=enabled;edit.set(value);shape(value,bounds.w());
        hover=UiMotion.approach(hover,enabled&&bounds.contains(mx,my)?1:0,dt,UiMotion.HOVER_MS);
        int accent=appearance.accentColor(),secondary=appearance.textSecondaryColor();
        var font=font();
        SettingsFieldPaint.surface(canvas,bounds,hover,focused,error,enabled,accent,appearance);
        int caretLine=lineAt(edit.cursor());
        int visible=Math.max(1,(int)((bounds.h()-V_PAD*2+.01f)/LINE_H));
        if(focused&&(lastCaret!=edit.cursor()||!Objects.equals(visibleText,value)||visibleShape!=layoutBuilds)){firstLine=Math.max(0,Math.min(firstLine,caretLine));if(caretLine>=firstLine+visible)firstLine=caretLine-visible+1;}
        firstLine=Math.min(firstLine,Math.max(0,lines.size()-visible));lastCaret=edit.cursor();visibleText=value;visibleShape=layoutBuilds;
        int count=Math.min(visible,lines.size()-firstLine);
        textTop=bounds.y()+(bounds.h()-count*LINE_H)/2;
        shift=!wrap&&focused?Math.max(0,lines.get(caretLine).x(edit.cursor())-bounds.w()+TEXT_INSET*2):0;
        canvas.save();
        try{
            CornerGeometry.clip(canvas,bounds.x()+TEXT_INSET/2,bounds.y()+V_PAD/2,Math.max(0,bounds.w()-TEXT_INSET),Math.max(0,bounds.h()-V_PAD),0);
            for(int i=firstLine;i<firstLine+count;i++){
                var line=lines.get(i);float cy=textTop+LINE_H/2+(i-firstLine)*LINE_H;
                int a=Math.max(line.start(),edit.start()),b=Math.min(line.end(),edit.end());
                if(focused&&a<b)CornerGeometry.drawRoundedRect(canvas,bounds.x()+TEXT_INSET+line.x(a)-shift,cy-LINE_H/2,line.x(b)-line.x(a),LINE_H,0,(accent&0xffffff)|0x55000000);
                SkiaFontRenderer.drawText(canvas,font,line.text(),bounds.x()+TEXT_INSET-shift,SkiaFontRenderer.centerBaselineY(font,cy),!enabled?secondary:number&&focused?accent:primary);
                if(focused&&i==caretLine&&Animations.caretVisible(millis))CornerGeometry.drawRoundedRect(canvas,bounds.x()+TEXT_INSET+line.x(edit.cursor())-shift,cy-CARET_H/2,UiTokens.FOCUS_RING_WIDTH,CARET_H,0,accent);
            }
        }finally{canvas.restore();}
    }
    private int lineAt(int offset){int at=0;for(int i=0;i<lines.size();i++)if(offset>=lines.get(i).start()&&offset<=lines.get(i).end())at=i;return at;}
    private int offsetAt(Line line,float x){
        int at=line.start();while(at<line.end()){int next=shapedText.offsetByCodePoints(at,1);if(x<(line.x(at)+line.x(next))/2)break;at=next;}return at;
    }
    private void vertical(int direction,boolean extend){
        if(rect==null){edit.vertical(direction,extend);return;}
        shape(edit.text(),rect.w());int current=lineAt(edit.cursor());var target=lines.get(Math.max(0,Math.min(lines.size()-1,current+direction)));
        edit.select(offsetAt(target,lines.get(current).x(edit.cursor())),extend);
    }
    public void pointer(float x,float y,boolean extend){
        if(rect==null||!enabled)return;focused=true;shape(edit.text(),rect.w());
        int index=Math.max(0,Math.min(lines.size()-1,firstLine+(int)Math.floor((y-textTop)/LINE_H)));
        edit.select(offsetAt(lines.get(index),x-rect.x()-TEXT_INSET+shift),extend);
    }
    public boolean scroll(double amount){
        if(rect==null||!wrap)return false;int visible=Math.max(1,(int)((rect.h()-V_PAD*2+.01f)/LINE_H));
        if(lines.size()<=visible)return false;
        wheelRemainder+=(float)amount;int steps=(int)wheelRemainder;wheelRemainder-=steps;
        firstLine=Math.max(0,Math.min(lines.size()-visible,firstLine-steps));return true;
    }
    public void key(int key,int modifiers,String clipboard,Consumer<String> writeClipboard){key(key,modifiers,()->clipboard,writeClipboard);}
    public void key(int key,int modifiers,Supplier<String> clipboard,Consumer<String> writeClipboard){
        if(!focused||!enabled)return;boolean ctrl=(modifiers&2)!=0,select=(modifiers&1)!=0;
        if(ctrl&&key==65)edit.all();
        else if(ctrl&&(key==67||key==88)){try{writeClipboard.accept(edit.selected());if(key==88)insert("");}catch(RuntimeException unavailable){/* Keep selected text when the system clipboard is unavailable. */}}
        else if(ctrl&&key==86){try{insert(clipboard.get());}catch(RuntimeException unavailable){/* A failed paste never deletes the selection. */}}
        else if(ctrl&&(key==90||key==89))edit.undo(key==89||select);
        else if(key==259||key==261)edit.delete(key==261,ctrl,max,number,multiline);
        else if(key==263||key==262)edit.move(key==263?-1:1,select,ctrl);
        else if(key==268||key==269)edit.home(key==269,ctrl,select);
        else if(key==265||key==264)vertical(key==265?-1:1,select);
        else if(key==257||key==335){if(multiline)insert("\n");else focused=false;}
    }
    public void insert(String value){if(focused&&enabled)edit.replace(value,max,number,multiline);}
    public void typed(char value){
        if(Character.isHighSurrogate(value)){pendingHigh=value;return;}
        if(Character.isLowSurrogate(value)){if(pendingHigh!=0)insert(new String(new char[]{pendingHigh,value}));pendingHigh=0;return;}
        pendingHigh=0;insert(String.valueOf(value));
    }
}
