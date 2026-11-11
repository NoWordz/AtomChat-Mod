package com.atom.chat.onboarding;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import java.util.function.Function;

/** Panel-local annotation; sample controls are isolated and cannot call live business actions. */
public final class TutorialOverlay {
    private static final float HIGHLIGHT_STROKE = 1.5F;
    private TutorialLayout geometry;
    private UiLayout.Rect demoArea, sendArea;
    private TutorialDemoPanels panels;
    private UiLayout currentLayout;
    private UiLayout.Rect currentChrome;
    private UiLayout.Rect customTarget;
    public void anchor(UiLayout.Rect target) { customTarget=target; }
    private String cachedBody;
    private float cachedWidth, cachedSize, bodyScroll, maxBodyScroll;
    private java.util.List<String> bodyLines=java.util.List.of();
    private long textLayoutBuilds;
    private float progress;
    public TutorialLayout geometry() { return geometry; }
    public void reset() { geometry=null; demoArea=null; sendArea=null; bodyScroll=0; progress=0; if(panels!=null)panels.reset(); }
    public void close() { reset();if(panels!=null){panels.close();panels=null;} }
    public boolean key(TutorialSession session,int key,int modifiers) { return panels!=null&&panels.key(session,key,modifiers); }
    public boolean type(TutorialSession session,char chr) { return panels!=null&&panels.type(session,chr); }
    public boolean scrollDemo(double amount) { return panels!=null&&panels.scroll(currentLayout,amount); }
    public long textLayoutBuildCount() { return textLayoutBuilds; }
    public boolean scroll(float x,float y,double amount) {
        if(geometry==null||!geometry.card().contains(x,y))return false;
        bodyScroll=Math.max(0,Math.min(maxBodyScroll,bodyScroll-(float)amount*UiTokens.DIALOG_LINE_HEIGHT));
        return true;
    }

    public void render(Canvas canvas, TutorialSession session, UiLayout layout, UiLayout root,
                       UiLayout.Rect chrome, Function<String,String> tr, float mx, float my) {
        render(canvas,session,layout,root,chrome,tr,mx,my,16);
    }
    public void render(Canvas canvas, TutorialSession session, UiLayout layout, UiLayout root,
                       UiLayout.Rect chrome, Function<String,String> tr, float mx, float my,float dt) {
        progress=UiMotion.approach(progress,1,dt,Animations.ms(UiMotion.POPUP_MS));
        var step=session.step();
        currentLayout=layout;currentChrome=chrome;
        if(panels==null)panels=new TutorialDemoPanels();
        panels.prepare(session,layout,tr,dt);
        var panel=layout.rect();
        var target=target(step,layout,root,chrome);
        if(customTarget!=null)target=customTarget;
        if(isPanelStep(step))target=panels.bounds(layout);
        if((step==TutorialSession.Step.MESSAGE_ACTIONS||step==TutorialSession.Step.PLAYERS_BLOCK)&&target.equals(layout.list))target=sampleRect(layout);
        var font=FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        float pad=UiTokens.SETTINGS_ROW_PAD, gap=UiTokens.BUTTON_GAP;
        float textWidth=Math.max(1,panel.w()-pad*4);
        String body=tr.apply("atomchat.tutorial."+step.key()+".body");
        if(!body.equals(cachedBody)||textWidth!=cachedWidth||cachedSize!=UiTokens.SETTINGS_TILE_TITLE) {
            bodyLines=java.util.List.copyOf(SkiaFontRenderer.wrapWords(font,body,textWidth));
            cachedBody=body;cachedWidth=textWidth;cachedSize=UiTokens.SETTINGS_TILE_TITLE;textLayoutBuilds++;
        }
        var lines=bodyLines;
        float line=UiTokens.DIALOG_LINE_HEIGHT;
        float height=pad*3+line*(lines.size()+2)+UiTokens.BUTTON_H*2+gap;
        geometry=TutorialLayout.of(panel,target,height);
        int save=canvas.save();
        try {
            PanelBackground.clipInterior(canvas,panel);
            try(var shade=new Paint().setColor(0xC808090E).setAlphaf(progress*200/255F)) {
                // A difference clip avoids allocating a full-panel offscreen texture for the spotlight.
                int mask=canvas.save();
                canvas.clipRRect(RRect.makeXYWH(target.x(),target.y(),target.w(),target.h(),UiTokens.settingsRowRadius()),ClipMode.DIFFERENCE,true);
                canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(panel.x(),panel.y(),panel.w(),panel.h()),shade);
                canvas.restoreToCount(mask);
            }
            try(var ring=new Paint().setMode(PaintMode.STROKE).setStrokeWidth(UiTokens.s(HIGHLIGHT_STROKE))
                    .setAntiAlias(true).setColor(AtomChatConfig.get().accentColor)) {
                canvas.drawRRect(RRect.makeXYWH(target.x(),target.y(),target.w(),target.h(),UiTokens.settingsRowRadius()),ring);
            }
            drawDemo(canvas,session,layout,target,tr);
            panels.render(canvas,session,layout,tr,mx,my,dt);
            if(step==TutorialSession.Step.EXPRESSIONS||step==TutorialSession.Step.QUICK_PHRASES) {
                var bar=layout.inputBar;
                float previewHeight=UiTokens.DIALOG_LINE_HEIGHT*2;
                SkiaDraw.drawRoundedRect(canvas,bar.x(),bar.bottom()-previewHeight,bar.w(),previewHeight,UiTokens.settingsRowRadius(),0xFF232A35);
                text(canvas,font,tr.apply("atomchat.tutorial.demo"),bar.x()+pad,bar.bottom()-previewHeight+UiTokens.DIALOG_LINE_HEIGHT/2,bar.w()-pad*2);
                text(canvas,font,panels.previewText().isEmpty()?tr.apply("atomchat.tutorial.type_hint"):panels.previewText(),bar.x()+pad,bar.bottom()-UiTokens.DIALOG_LINE_HEIGHT/2,bar.w()-pad*2);
            }
            var box=geometry.card();
            PickerModalPaint.surface(canvas,box,progress);
            int cardClip=canvas.save();
            SkiaDraw.clip(canvas,box.x(),box.y(),box.w(),box.h(),PickerModalPaint.radius());
            var title=FontManager.boldFont(UiTokens.FONT_BODY);
            float y=box.y()+pad+line/2;
            text(canvas,title,tr.apply("atomchat.tutorial."+step.key()+".title"),box.x()+pad,y,Math.max(0,box.w()-pad*2));
            y+=line;
            text(canvas,FontManager.font(UiTokens.FONT_QUOTE),(session.index()+1)+" / "+TutorialSession.Step.values().length+"   "+tr.apply("atomchat.tutorial.progress"),box.x()+pad,y,box.w()-pad*2,AtomChatConfig.get().textSecondaryColor);
            y+=line;
            float bodyBottom=geometry.buttons().get(0).y()-gap;
            int bodyClip=canvas.save();
            SkiaDraw.clip(canvas,box.x()+pad,box.y()+pad+line*2,Math.max(0,box.w()-pad*2),Math.max(0,bodyBottom-(box.y()+pad+line*2)),0);
            maxBodyScroll=Math.max(0,lines.size()*line-Math.max(0,bodyBottom-(box.y()+pad+line*2)));
            bodyScroll=Math.min(bodyScroll,maxBodyScroll);
            y-=bodyScroll;
            for(String value:lines) { text(canvas,font,value,box.x()+pad,y,textWidth); y+=line; }
            canvas.restoreToCount(bodyClip);
            for(int i=0;i<4;i++) {
                String key=switch(i) {case 0->"previous";case 1->session.index()==TutorialSession.Step.values().length-1?"finish":step==TutorialSession.Step.APPEARANCE?"styled":"next";case 2->"skip";default->"exit";};
                var button=geometry.buttons().get(i);
                boolean ready=i!=1||!step.requiresAction()||session.demoPerformed();
                int fill=i==1&&ready?AtomChatConfig.get().accentColor:0xFF23272F;
                SkiaDraw.drawRoundedRect(canvas,button.x(),button.y(),button.w(),button.h(),PickerModalPaint.buttonRadius(button),fill);
                if(button.contains(mx,my)) SkiaDraw.drawRoundedRect(canvas,button.x(),button.y(),button.w(),button.h(),PickerModalPaint.buttonRadius(button),0x24FFFFFF);
                var f=FontManager.font(UiTokens.FONT_BUTTON);
                String label=tr.apply("atomchat.tutorial."+key);
                float w=SkiaFontRenderer.getStringWidth(f,label);
                text(canvas,f,label,button.x()+Math.max(pad/4,(button.w()-w)/2),button.y()+button.h()/2,Math.max(0,button.w()-pad/2),
                        !ready?0xFF9AA2B0:i==1?UiTokens.onAccent(fill):0xFFFFFFFF);
            }
            canvas.restoreToCount(cardClip);
        } finally { canvas.restoreToCount(save); }
    }
    public void notice(Canvas canvas,UiLayout.Rect panel,String message) {
        var font=FontManager.font(UiTokens.FONT_QUOTE);
        float pad=UiTokens.SETTINGS_ROW_PAD;
        var lines=SkiaFontRenderer.wrapWords(font,message,Math.max(1,panel.w()-pad*4));
        var box=new UiLayout.Rect(panel.x()+pad,panel.y()+pad,Math.max(0,panel.w()-pad*2),pad*2+lines.size()*UiTokens.DIALOG_LINE_HEIGHT);
        PickerModalPaint.surface(canvas,box,1);
        float y=box.y()+pad+UiTokens.DIALOG_LINE_HEIGHT/2;
        for(String line:lines){text(canvas,font,line,box.x()+pad,y,box.w()-pad*2);y+=UiTokens.DIALOG_LINE_HEIGHT;}
    }

    private static UiLayout.Rect target(TutorialSession.Step step,UiLayout layout,UiLayout root,UiLayout.Rect chrome) {
        return switch(step) {
            case NAVIGATION -> chrome.w()>0?chrome:root.tabBar;
            case CONVERSATIONS -> sampleRect(layout);
            case COMPOSE -> chrome;
            case EXPRESSIONS -> mapped(layout.emojiBtn,layout.inputBar,chrome);
            case IMAGES -> mapped(layout.imageBtn,layout.inputBar,chrome);
            case QUICK_PHRASES -> mapped(layout.phraseBtn,layout.inputBar,chrome);
            case SEARCH_FILTER -> mapped(layout.imageBtn,layout.inputBar,chrome);
            case APPEARANCE -> new UiLayout.Rect(layout.list.x(),layout.chipBar.y(),layout.list.w(),layout.list.bottom()-layout.chipBar.y());
            case CHAT_SETTINGS -> layout.chipBar;
            default -> layout.list;
        };
    }
    private static UiLayout.Rect sampleRect(UiLayout layout) {
        var list=layout.list;
        float pad=UiTokens.SETTINGS_ROW_PAD;
        float height=Math.min(list.h()*.3F,UiTokens.HEADER_HEIGHT*2.5F);
        return new UiLayout.Rect(list.x()+pad,list.y()+pad,Math.max(0,list.w()-pad*2),Math.max(0,height));
    }
    private static boolean isPanelStep(TutorialSession.Step step) {
        return step==TutorialSession.Step.MESSAGE_ACTIONS||step==TutorialSession.Step.PLAYERS_BLOCK
                ||step==TutorialSession.Step.EXPRESSIONS||step==TutorialSession.Step.IMAGES
                ||step==TutorialSession.Step.QUICK_PHRASES||step==TutorialSession.Step.SEARCH_FILTER;
    }
    private static UiLayout.Rect mapped(UiLayout.Rect rect,UiLayout.Rect rest,UiLayout.Rect chrome) {
        if(rest.w()<=0||rest.h()<=0)return rect;
        return new UiLayout.Rect(chrome.x()+(rect.x()-rest.x())*chrome.w()/rest.w(),
                chrome.y()+(rect.y()-rest.y())*chrome.h()/rest.h(),rect.w()*chrome.w()/rest.w(),rect.h()*chrome.h()/rest.h());
    }
    private void drawDemo(Canvas canvas,TutorialSession session,UiLayout layout,UiLayout.Rect target,Function<String,String> tr) {
        demoArea=null; sendArea=null;
        var step=session.step();
        if(step==TutorialSession.Step.COMPOSE) {
            SkiaDraw.drawRoundedRect(canvas,target.x(),target.y(),target.w(),target.h(),UiTokens.shellChromeRadius(),0xFF202630);
            float pad=UiTokens.SETTINGS_ROW_PAD;
            sendArea=mapped(layout.sendBtn,layout.inputBar,target);
            var font=FontManager.font(UiTokens.SETTINGS_TILE_SUB);
            text(canvas,font,tr.apply("atomchat.tutorial.demo"),target.x()+pad,target.y()+pad,Math.max(0,target.w()-pad*2));
            text(canvas,font,session.demoText().isEmpty()?tr.apply("atomchat.tutorial.type_hint"):session.demoText(),target.x()+pad,target.bottom()-pad,Math.max(0,sendArea.x()-target.x()-pad*2));
            SkiaDraw.drawRoundedRect(canvas,sendArea.x(),sendArea.y(),sendArea.w(),sendArea.h(),PickerModalPaint.buttonRadius(sendArea),AtomChatConfig.get().accentColor);
            text(canvas,font,tr.apply(session.demoPerformed()?"atomchat.tutorial.demo_sent":"atomchat.tutorial.demo_send"),sendArea.x()+pad/2,sendArea.y()+sendArea.h()/2,Math.max(0,sendArea.w()-pad));
            return;
        }
        boolean sample=step==TutorialSession.Step.CONVERSATIONS||step==TutorialSession.Step.MESSAGE_ACTIONS
                ||step==TutorialSession.Step.PLAYERS_BLOCK||step==TutorialSession.Step.NOTIFICATIONS;
        if(!sample)return;
        float pad=UiTokens.SETTINGS_ROW_PAD;
        demoArea=sampleRect(layout);
        var b=demoArea;
        SkiaDraw.drawRoundedRect(canvas,b.x(),b.y(),b.w(),b.h(),UiTokens.settingsRowRadius(),0xFF232A35);
        var font=FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        text(canvas,font,tr.apply("atomchat.tutorial.demo"),b.x()+pad,b.y()+pad,b.w()-pad*2);
        String key=switch(step) {
            case CONVERSATIONS -> "demo_player";
            case MESSAGE_ACTIONS -> session.demoPerformed()?"demo_reply":"demo_message";
            case PLAYERS_BLOCK -> session.demoPerformed()?"demo_blocked":"demo_block";
            default -> "demo_notification";
        };
        float thumbnailWidth=step==TutorialSession.Step.CONVERSATIONS?UiTokens.HEADER_HEIGHT:0;
        text(canvas,font,tr.apply("atomchat.tutorial."+key),b.x()+pad,b.y()+pad+UiTokens.DIALOG_LINE_HEIGHT,b.w()-pad*2-thumbnailWidth);
        if(thumbnailWidth>0) {
            var image=panels.thumbnail();
            var imageRect=io.github.humbleui.types.Rect.makeXYWH(b.right()-pad-thumbnailWidth,b.y()+pad,thumbnailWidth,Math.min(thumbnailWidth,b.h()-pad*2));
            int save=canvas.save();
            SkiaDraw.clip(canvas,imageRect.getLeft(),imageRect.getTop(),imageRect.getWidth(),imageRect.getHeight(),UiTokens.settingsRowRadius());
            float imageScale=Math.max(imageRect.getWidth()/image.getWidth(),imageRect.getHeight()/image.getHeight());
            float sourceW=imageRect.getWidth()/imageScale,sourceH=imageRect.getHeight()/imageScale;
            canvas.drawImageRect(image,io.github.humbleui.types.Rect.makeXYWH((image.getWidth()-sourceW)/2,(image.getHeight()-sourceH)/2,sourceW,sourceH),imageRect,SamplingMode.LINEAR,null,false);
            canvas.restoreToCount(save);
        }
    }
    public boolean sampleClick(TutorialSession session,float x,float y,int button) {
        if(session.step()==TutorialSession.Step.COMPOSE) {
            if(button==0&&sendArea!=null&&sendArea.contains(x,y)&&!session.demoText().isBlank())session.performDemo();
            return true;
        }
        if(panels!=null&&isPanelStep(session.step())) {
            if(geometry==null||!geometry.target().contains(x,y))return false;
            if(currentChrome!=null&&currentChrome.contains(x,y)&&currentChrome.w()>0&&currentChrome.h()>0) {
                x=currentLayout.inputBar.x()+(x-currentChrome.x())*currentLayout.inputBar.w()/currentChrome.w();
                y=currentLayout.inputBar.y()+(y-currentChrome.y())*currentLayout.inputBar.h()/currentChrome.h();
            }
            return panels.click(session,currentLayout,x,y,button);
        }
        return false;
    }
    private static void text(Canvas canvas,Font font,String value,float x,float cy,float width) { text(canvas,font,value,x,cy,width,0xFFFFFFFF); }
    private static void text(Canvas canvas,Font font,String value,float x,float cy,float width,int color) {
        SkiaFontRenderer.drawText(canvas,font,SkiaFontRenderer.truncate(font,value,Math.max(0,width)),x,SkiaFontRenderer.centerBaselineY(font,cy),color);
    }
}
