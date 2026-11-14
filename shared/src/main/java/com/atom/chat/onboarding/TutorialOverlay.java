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

/** Annotations only. Every highlighted control is painted and operated by the production screen. */
public final class TutorialOverlay {
    private static final float BODY_FONT_SIZE=UiTokens.FONT_BUTTON;
    private TutorialLayout geometry;
    private final TutorialFocusMotion focus = new TutorialFocusMotion();
    private UiLayout.Rect customTarget,viewport;
    private TutorialAnchor customShape;
    private String cachedBody;
    private float cachedWidth,bodyScroll,maxBodyScroll,progress;
    private java.util.List<String> bodyLines=java.util.List.of();
    private long textLayoutBuilds;
    private final ConfirmationSurface.Button[] buttons={new ConfirmationSurface.Button(),new ConfirmationSurface.Button(),new ConfirmationSurface.Button(),new ConfirmationSurface.Button()};
    public void anchor(UiLayout.Rect target){customTarget=target;customShape=null;}
    public void shape(TutorialAnchor target){customTarget=target==null?null:target.bounds();customShape=target;}
    public void viewport(UiLayout.Rect rect){viewport=rect;}
    public TutorialLayout geometry(){return geometry;}
    public void press(int index){buttons[index].press();}
    public void reset(){geometry=null;bodyScroll=0;progress=0;}
    public void close(){reset();focus.clear();}
    public long textLayoutBuildCount(){return textLayoutBuilds;}
    public boolean scroll(float x,float y,double amount){
        if(geometry==null||!geometry.card().contains(x,y))return false;
        bodyScroll=Math.max(0,Math.min(maxBodyScroll,bodyScroll-(float)amount*UiTokens.DIALOG_LINE_HEIGHT));return true;
    }
    public void render(Canvas canvas,TutorialSession session,UiLayout layout,UiLayout root,UiLayout.Rect chrome,Function<String,String> tr,float mx,float my){render(canvas,session,layout,root,chrome,tr,mx,my,16);}
    public void render(Canvas canvas,TutorialSession session,UiLayout layout,UiLayout root,UiLayout.Rect chrome,Function<String,String> tr,float mx,float my,float dt){
        progress=UiMotion.approach(progress,1,dt,Animations.ms(UiMotion.POPUP_MS));
        var panel=layout.rect();
        var target=customTarget!=null?customTarget:switch(session.step()) {
            case NAVIGATION->root.tabBar;
            case COMPOSE->layout.inputBar;
            case EXPRESSIONS->layout.emojiBtn;
            case IMAGES,SEARCH_FILTER->layout.imageBtn;
            case QUICK_PHRASES->layout.phraseBtn;
            default->layout.list;
        };
        var screen=viewport==null?panel:viewport;
        var contour=customShape==null?RRect.makeXYWH(target.x(),target.y(),target.w(),target.h(),UiTokens.settingsRowRadius()):customShape.contour();
        var moving = focus.update(new TutorialAnchor(target, contour), dt, Animations.enabled());
        contour = moving.contour();
        float pad=UiTokens.SETTINGS_ROW_PAD,line=UiTokens.DIALOG_LINE_HEIGHT;
        var entry=session.entry();
        String body=entry.body()!=null?entry.body():tr.apply("atomchat.tutorial."+session.step().key()+".body");
        var provisional=TutorialLayout.of(screen,target,screen.h(),panel);
        float textWidth=Math.max(1,provisional.card().w()-pad*2);
        var font=FontManager.font(BODY_FONT_SIZE);
        if(!body.equals(cachedBody)||textWidth!=cachedWidth){bodyLines=java.util.List.copyOf(SkiaFontRenderer.wrapWords(font,body,textWidth));cachedBody=body;cachedWidth=textWidth;textLayoutBuilds++;}
        float height=pad*3+line*(bodyLines.size()+2)+UiTokens.BUTTON_H*2+UiTokens.BUTTON_GAP;
        geometry=TutorialLayout.of(screen,target,height,panel);
        int save=canvas.save();
        try {
            try(var shade=new Paint().setColor(0x8808090E).setAlphaf(progress*.53f)) {
                int mask=canvas.save();
                canvas.clipRRect(RRect.makeXYWH(panel.x(),panel.y(),panel.w(),panel.h(),UiTokens.panelRadius()),ClipMode.INTERSECT,true);
                canvas.clipRRect(contour,ClipMode.DIFFERENCE,true);
                canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(panel.x(),panel.y(),panel.w(),panel.h()),shade);canvas.restoreToCount(mask);
            }
            var box=geometry.card();
            try(var ring=new Paint().setAntiAlias(true).setMode(PaintMode.STROKE).setStrokeWidth(UiTokens.CHROME_HAIRLINE + UiTokens.SCALE).setColor(AtomChatConfig.get().accentColor)) {
                canvas.drawRRect(contour,ring);
                var movingBounds = moving.bounds();
                float tx=movingBounds.x()+movingBounds.w()/2,ty=movingBounds.y()+movingBounds.h()/2;
                var cardShape = new TutorialAnchor(box, PickerModalPaint.radius());
                float[] end = TutorialFocusMotion.connectorStart(cardShape, tx, ty);
                float[] start = TutorialFocusMotion.connectorStart(moving, end[0], end[1]);
                canvas.drawLine(start[0], start[1], end[0], end[1], ring);
            }
            ConfirmationSurface.surface(canvas,box,progress);
            try(var opacity=new Paint().setAlphaf(progress)) {
                canvas.saveLayer(io.github.humbleui.types.Rect.makeXYWH(box.x(),box.y(),box.w(),box.h()),opacity);
                SkiaDraw.clip(canvas,box.x(),box.y(),box.w(),box.h(),PickerModalPaint.radius());
                String title=entry.title()!=null?entry.title():tr.apply("atomchat.tutorial."+session.step().key()+".title");
                text(canvas,FontManager.font(UiTokens.SETTINGS_TILE_TITLE),title,box.x()+pad,box.y()+pad+line/2,textWidth,0xFFFFFFFF);
                text(canvas,font,(session.index()+1)+" / "+session.count(),box.x()+pad,box.y()+pad+line*1.5f,textWidth,0xFFB9C3D2);
                float top=box.y()+pad+line*2,bottom=geometry.buttons().get(0).y()-UiTokens.BUTTON_GAP;
                maxBodyScroll=Math.max(0,bodyLines.size()*line-Math.max(0,bottom-top));bodyScroll=Math.min(bodyScroll,maxBodyScroll);
                int clip=canvas.save();SkiaDraw.clip(canvas,box.x()+pad,top,textWidth,Math.max(0,bottom-top),0);
                float y=top+line/2-bodyScroll;for(var value:bodyLines){text(canvas,font,value,box.x()+pad,y,textWidth,0xFFB9C3D2);y+=line;}canvas.restoreToCount(clip);
                boolean last=session.index()==session.count()-1;
                for(int i=0;i<4;i++) {
                    String key=switch(i){case 0->"previous";case 1->last?"finish_restore":"next";case 2->last?"keep":"skip";default->"exit";};
                    var r=geometry.buttons().get(i);buttons[i].draw(canvas,r,tr.apply("atomchat.tutorial."+key),i==1||last&&i==2,false,false,r.contains(mx,my),dt,1);
                }
            }
        } finally {canvas.restoreToCount(save);}
    }
    public void notice(Canvas canvas,UiLayout.Rect panel,String message){
        var box=new UiLayout.Rect(panel.x(),panel.y(),panel.w(),UiTokens.HEADER_HEIGHT);ConfirmationSurface.surface(canvas,box,1);
        text(canvas,FontManager.font(BODY_FONT_SIZE),message,box.x()+UiTokens.SETTINGS_ROW_PAD,box.y()+box.h()/2,box.w()-UiTokens.SETTINGS_ROW_PAD*2,0xFFFFFFFF);
    }
    private static void text(Canvas canvas,Font font,String value,float x,float cy,float width,int color){SkiaFontRenderer.drawText(canvas,font,SkiaFontRenderer.truncate(font,value,Math.max(0,width)),x,SkiaFontRenderer.centerBaselineY(font,cy),color);}
}
