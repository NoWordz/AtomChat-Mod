package com.atom.chat.notification;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.lib.design.atomchat.UiCards;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.Rect;
import java.util.function.Function;

/** Full-viewport layout editor. Its preview never enqueues or renews a real notice. */
public final class NotificationHudEditor {
    public enum Action { NONE, SAVE, CANCEL }
    private final Function<String,String> translate;
    private NotificationHudEditorModel model;
    private float baseWidth;
    private float progress;
    private boolean closing;
    public void requestClose(){closing=true;}
    public boolean closing(){return closing;}
    public boolean closed(){return closing&&progress<=.001f;}
    private long lastFrame;
    private float frameDt=16;
    private final com.atom.chat.ui.ConfirmationSurface.Button[] buttons={new com.atom.chat.ui.ConfirmationSurface.Button(),new com.atom.chat.ui.ConfirmationSurface.Button(),new com.atom.chat.ui.ConfirmationSurface.Button()};
    private final float baseHeight=NotificationBanner.bannerHeight();
    public NotificationHudEditor(Function<String,String> translate) { this.translate=translate; }
    private void ensureModel(float width) {
        float next=NotificationBanner.hudBaseWidth(width);
        if(model!=null && next==baseWidth) return;
        var cfg=AtomChatConfig.get();
        var anchor=model==null?cfg.notificationHudPosition:model.anchor();
        float x=model==null?UiTokens.s(cfg.notificationHudOffsetX):model.offsetX();
        float y=model==null?UiTokens.s(cfg.notificationHudOffsetY):model.offsetY();
        float scale=model==null?cfg.notificationHudScale:model.scale();
        baseWidth=next;
        model=new NotificationHudEditorModel(baseWidth,baseHeight,UiTokens.PANEL_TOP_GAP,anchor,x,y,scale);
    }
    public void render(Canvas canvas,float width,float height,float mouseX,float mouseY) {
        long now=System.nanoTime();float dt=lastFrame==0?16:Math.min(50,(now-lastFrame)/1_000_000f);lastFrame=now;
        render(canvas,width,height,mouseX,mouseY,dt);
    }
    public void render(Canvas canvas,float width,float height,float mouseX,float mouseY,float dt) {
        ensureModel(width);frameDt=Math.max(0,dt);
        progress=com.atom.chat.ui.UiMotion.approach(progress,closing?0:1,frameDt,com.atom.chat.ui.Animations.ms(com.atom.chat.ui.UiMotion.POPUP_MS));
        int saved=canvas.save();
        try(Paint opacity=new Paint().setAlphaf(progress)) {
        canvas.saveLayer(Rect.makeXYWH(0,0,width,height),opacity);
        SkiaDraw.drawRoundedRect(canvas,0,0,width,height,0,0x66000000);
        var placement=model.bounds(width,height); var box=placement.rect();
        try(Paint guide=new Paint().setColor(0x667AC8FF).setStrokeWidth(1).setMode(PaintMode.STROKE)) {
            canvas.drawLine(width/2,0,width/2,height,guide);
            canvas.drawLine(0,height/2,width,height/2,guide);
            for(var anchor:NotificationHudLayout.Anchor.values()) {
                var r=NotificationHudLayout.rect(width,height,box.w(),box.h(),UiTokens.PANEL_TOP_GAP,anchor,0,0);
                float x=r.x()+r.w()/2, y=r.y()+r.h()/2;
                canvas.drawCircle(x,y,UiTokens.EDGE_CONTROL_INSET,guide);
            }
        }
        canvas.save();
        try {
            canvas.translate(box.x(),box.y()); canvas.scale(placement.scale(),placement.scale());
            NotificationBanner.INSTANCE.renderPreview(canvas,baseWidth);
        } finally { canvas.restore(); }
        int accent=AtomChatConfig.get().accentColor;
        try(Paint border=new Paint().setColor(accent).setMode(PaintMode.STROKE).setStrokeWidth(1)) {
            canvas.drawRect(Rect.makeXYWH(box.x(),box.y(),box.w(),box.h()),border);
        }
        var layout=NotificationHudEditorLayout.of(width,height,box);
        var handle=layout.handle();
        SkiaDraw.drawRoundedRect(canvas,handle.x(),handle.y(),handle.w(),handle.h(),UiTokens.EDGE_CONTROL_INSET,accent);
        var toolbar=layout.toolbar();
        int fill=UiTokens.floatSurfaceFill(), ink=UiTokens.onFloatSurface(fill);
        com.atom.chat.ui.ConfirmationSurface.surface(canvas,toolbar,progress);
        fill=0xFF20242E;ink=0xFFFFFFFF;
        String title=text("title")+" · "+translate.apply(model.anchor().key())+" · "+Math.round(model.scale()*100)+"%";
        float cx=toolbar.x()+toolbar.w()/2;
        drawFitted(canvas,title,cx,toolbar.y()+UiTokens.PANEL_BOTTOM_PAD,toolbar.w()-UiTokens.PANEL_BOTTOM_PAD*2,UiTokens.SETTINGS_TILE_TITLE,ink);
        drawFitted(canvas,com.atom.chat.onboarding.TutorialSandbox.active()?translate.apply("atomchat.tutorial.hud_editor"):text("hint"),cx,toolbar.y()+UiTokens.ACTION_BUTTON_SIZE,toolbar.w()-UiTokens.PANEL_BOTTOM_PAD*2,UiTokens.FONT_BUTTON,UiTokens.onFloatSurfaceSecondary(fill));
        button(canvas,0,layout.reset(),text("reset"),false,mouseX,mouseY);
        button(canvas,1,layout.cancel(),text("cancel"),false,mouseX,mouseY);
        button(canvas,2,layout.save(),text("save"),true,mouseX,mouseY);
        } finally {canvas.restoreToCount(saved);}
    }
    private static void drawFitted(Canvas canvas,String text,float x,float y,float width,float size,int color) {
        var font=FontManager.font(size);
        String fitted=SkiaFontRenderer.truncate(font,text,Math.max(0,width));
        SkiaFontRenderer.drawTextCentered(canvas,font,fitted,x,y,color);
    }
    private void button(Canvas canvas,int index,UiLayout.Rect r,String label,boolean primary,float mx,float my) {
        buttons[index].draw(canvas,r,label,primary,false,false,r.contains(mx,my),frameDt,progress);
    }
    public Action click(float x,float y,float width,float height,int button) {
        ensureModel(width);
        if(button!=0||closing) return Action.NONE;
        var box=model.bounds(width,height).rect();
        var layout=NotificationHudEditorLayout.of(width,height,box);
        if(layout.save().contains(x,y)) {buttons[2].press();return Action.SAVE;}
        if(layout.cancel().contains(x,y)) {buttons[1].press();return Action.CANCEL;}
        if(layout.reset().contains(x,y)) { buttons[0].press();model.reset(); return Action.NONE; }
        if(layout.toolbar().contains(x,y)) return Action.NONE;
        boolean resize=layout.handle().contains(x,y);
        if(resize || box.contains(x,y)) model.begin(x,y,width,height,resize);
        return Action.NONE;
    }
    public void drag(float x,float y,float width,float height) { if(closing)return;ensureModel(width); model.drag(x,y,width,height); }
    public void release() { if(model!=null) model.end(); }
    public void nudge(float x,float y) { if(!closing&&model!=null) model.nudge(x,y); }
    public void save() {
        if(model==null) return;
        var cfg=AtomChatConfig.get(); cfg.notificationHudPosition=model.anchor();
        cfg.notificationHudOffsetX=model.offsetX()/UiTokens.SCALE;
        cfg.notificationHudOffsetY=model.offsetY()/UiTokens.SCALE;
        cfg.notificationHudScale=model.scale(); AtomChatConfig.save(cfg);
    }
    private String text(String name) { return translate.apply("atomchat.notify.editor."+name); }
}
