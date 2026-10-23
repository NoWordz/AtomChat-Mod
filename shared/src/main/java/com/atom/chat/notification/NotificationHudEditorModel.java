package com.atom.chat.notification;

import com.atom.chat.notification.NotificationHudLayout.Anchor;

/** Drag state is independent of the game screen and the live notification queue. */
public final class NotificationHudEditorModel {
    private final float baseWidth, baseHeight, margin;
    private Anchor anchor;
    private float offsetX, offsetY, scale, grabX, grabY, startX, startY, startScale;
    private boolean dragging, resizing;
    public NotificationHudEditorModel(float baseWidth,float baseHeight,float margin,Anchor anchor,float offsetX,float offsetY,float scale) {
        this.baseWidth=baseWidth; this.baseHeight=baseHeight; this.margin=margin;
        this.anchor=anchor==null ? Anchor.TOP_CENTER : anchor;
        this.offsetX=Float.isFinite(offsetX)?offsetX:0; this.offsetY=Float.isFinite(offsetY)?offsetY:0;
        this.scale=Float.isFinite(scale)?Math.max(0.5F,Math.min(2,scale)):1;
    }
    public NotificationHudLayout.Placement bounds(float width,float height) {
        return NotificationHudLayout.scaled(width,height,baseWidth,baseHeight,margin,anchor,offsetX,offsetY,scale);
    }
    public void begin(float x,float y,float width,float height,boolean resize) {
        var p=bounds(width,height); grabX=x-p.rect().x(); grabY=y-p.rect().y();
        startX=x; startY=y; startScale=p.scale(); dragging=true; resizing=resize;
    }
    public void drag(float x,float y,float width,float height) {
        if (!dragging || !Float.isFinite(x) || !Float.isFinite(y)) return;
        if (resizing) {
            scale=Math.max(0.5F,Math.min(2,startScale+((x-startX)*baseWidth+(y-startY)*baseHeight)
                    /(baseWidth*baseWidth+baseHeight*baseHeight)));
            return;
        }
        var r=bounds(width,height).rect();
        float left=Math.max(0,Math.min(width-r.w(),x-grabX));
        float top=Math.max(0,Math.min(height-r.h(),y-grabY));
        float[] xs={margin,(width-r.w())/2,width-margin-r.w()};
        float[] ys={margin,(height-r.h())/2,height-margin-r.h()};
        int column=nearest(left,xs), row=nearest(top,ys);
        anchor=Anchor.values()[row*3+column];
        float snap=Math.min(margin,Math.min(width,height)/10);
        offsetX=Math.abs(left-xs[column])<=snap ? 0 : left-xs[column];
        offsetY=Math.abs(top-ys[row])<=snap ? 0 : top-ys[row];
    }
    private static int nearest(float value,float[] points) {
        int best=0; for(int i=1;i<points.length;i++) if(Math.abs(value-points[i])<Math.abs(value-points[best])) best=i;
        return best;
    }
    public void nudge(float dx,float dy) { offsetX+=dx; offsetY+=dy; }
    public void end() { dragging=false; resizing=false; }
    public void reset() { anchor=Anchor.TOP_CENTER; offsetX=offsetY=0; scale=1; end(); }
    public Anchor anchor() { return anchor; }
    public float offsetX() { return offsetX; }
    public float offsetY() { return offsetY; }
    public float scale() { return scale; }
}
