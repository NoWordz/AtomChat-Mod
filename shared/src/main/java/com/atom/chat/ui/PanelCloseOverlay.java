package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.render.SoftShadow;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Picture;
import io.github.humbleui.types.Rect;

/** A short-lived, non-interactive exit. The live Screen is removed immediately. */
public final class PanelCloseOverlay {
    public static final PanelCloseOverlay INSTANCE=new PanelCloseOverlay();
    public record Environment(Object world,long context,float density,float width,float height) { }
    private final PanelTimeline motion=new PanelTimeline();
    private Picture picture;
    private UiLayout.Rect bounds;
    private AtomChatConfig.PageNavStyle style;
    private Environment environment;
    private boolean blur;

    private PanelCloseOverlay() { }

    /** Takes ownership of commands; these retain their native resources after Screen.removed(). */
    public void begin(Picture picture,UiLayout.Rect bounds,AtomChatConfig.PageNavStyle style,
                      Environment environment,float progress,boolean blur,long now) {
        clear();
        this.picture=picture;this.bounds=bounds;this.style=style;this.environment=environment;this.blur=blur;
        motion.snapTo(Math.max(0F,Math.min(1F,progress)));
        motion.target(0,now,true);
    }

    public float progress(long now) { return picture==null ? 0 : motion.value(now); }
    public boolean blur() { return blur; }
    public UiLayout.Rect bounds() { return bounds; }
    public AtomChatConfig.PageNavStyle style() { return style; }

    public boolean active(Object world,float density,float width,float height,long now,boolean enabled) {
        if(picture==null) return false;
        if(!enabled || environment.world()!=world || environment.density()!=density
                || environment.width()!=width || environment.height()!=height || progress(now)<=0) {
            clear();return false;
        }
        return true;
    }

    public float takeProgress(Object world,long now) {
        float value=environment!=null && environment.world()==world ? progress(now) : 0;
        clear();return value;
    }

    public void draw(Canvas canvas,long context,long now) {
        if(picture==null) return;
        if(environment.context()!=context) {clear();return;}
        float progress=progress(now);
        if(progress<=0) {clear();return;}
        int save=canvas.save();
        try(var paint=new Paint().setAlphaf(progress)) {
            var layer=PanelOpenMotion.fadeLayer(progress,style,bounds.x(),bounds.y(),bounds.w(),bounds.h(),SoftShadow.panelPad());
            canvas.saveLayer(Rect.makeXYWH(layer.x(),layer.y(),layer.w(),layer.h()),paint);
            float cx=bounds.x()+bounds.w()/2F,cy=bounds.y()+bounds.h()/2F;
            canvas.translate(cx+PanelOpenMotion.openSlidePx(progress,style),cy);
            float scale=PanelOpenMotion.openScale(progress,style);
            canvas.scale(scale,scale);canvas.translate(-cx,-cy);
            picture.playback(canvas);
        } finally {canvas.restoreToCount(save);}
    }

    public void clear() {
        if(picture!=null) picture.close();
        picture=null;environment=null;bounds=null;blur=false;
    }
}
