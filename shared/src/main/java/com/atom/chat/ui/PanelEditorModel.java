package com.atom.chat.ui;

/** Rectangle editing in design coordinates that do not change with content zoom. */
public final class PanelEditorModel {
    public enum Handle { MOVE, NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST, WEST, EAST, NORTH, SOUTH }
    private static final float MIN_WIDTH = 160F, MIN_HEIGHT = 160F;
    private float x, y, width, height, scale = 1F;
    private float startX, startY, left, top, right, bottom;
    private Handle handle;

    public PanelEditorModel(float x, float y, float width, float height, float viewportWidth, float viewportHeight) {
        this.x=x; this.y=y; this.width=width; this.height=height;
        viewport(viewportWidth, viewportHeight);
    }
    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }
    public float contentScale() { return scale; }
    public void contentScale(float value) { scale=Math.max(.8F, Math.min(1.5F, value)); }
    public void viewport(float vw,float vh) {
        width=Math.max(1F,Math.min(width,vw)); height=Math.max(1F,Math.min(height,vh));
        x=Math.max(0F,Math.min(x,vw-width)); y=Math.max(0F,Math.min(y,vh-height));
    }
    public void begin(Handle handle,float px,float py) {
        this.handle=handle; startX=px; startY=py;
        left=x; top=y; right=x+width; bottom=y+height;
    }
    public void end() { handle=null; }
    public boolean dragging() { return handle!=null; }
    public boolean moving() { return handle==Handle.MOVE; }
    public Handle activeHandle() { return handle; }
    private static float[] anchorOffsets(float space, float margin) {
        space = Math.max(0, space);
        float inset = Math.min(Math.max(0, margin), space/2);
        return new float[]{inset, space/2, space-inset};
    }
    private static int nearest(float value, float[] points) {
        int best = 0;
        for (int i=1; i<points.length; i++) if (Math.abs(value-points[i]) < Math.abs(value-points[best])) best=i;
        return best;
    }
    public int nearestAnchor(float vw, float vh, float margin) {
        return nearest(y, anchorOffsets(vh-height, margin))*3 + nearest(x, anchorOffsets(vw-width, margin));
    }
    public void snap(float vw, float vh, float threshold) {
        var xs = anchorOffsets(vw-width, threshold);
        var ys = anchorOffsets(vh-height, threshold);
        float ax = xs[nearest(x, xs)], ay = ys[nearest(y, ys)];
        if (Math.abs(x-ax) <= threshold) x=ax;
        if (Math.abs(y-ay) <= threshold) y=ay;
        viewport(vw, vh);
    }
    public void drag(float px,float py,float vw,float vh) {
        if(handle==null)return;
        float dx=px-startX, dy=py-startY;
        if(handle==Handle.MOVE) { x=left+dx; y=top+dy; viewport(vw,vh); return; }
        boolean west=handle==Handle.NORTH_WEST || handle==Handle.SOUTH_WEST || handle==Handle.WEST;
        boolean east=handle==Handle.NORTH_EAST || handle==Handle.SOUTH_EAST || handle==Handle.EAST;
        boolean north=handle==Handle.NORTH_WEST || handle==Handle.NORTH_EAST || handle==Handle.NORTH;
        boolean south=handle==Handle.SOUTH_WEST || handle==Handle.SOUTH_EAST || handle==Handle.SOUTH;
        float minW=Math.min(MIN_WIDTH,vw), minH=Math.min(MIN_HEIGHT,vh);
        float l=west?Math.max(0,Math.min(left+dx,right-minW)):left;
        float r=east?Math.min(vw,Math.max(right+dx,left+minW)):right;
        float t=north?Math.max(0,Math.min(top+dy,bottom-minH)):top;
        float b=south?Math.min(vh,Math.max(bottom+dy,top+minH)):bottom;
        x=l;y=t;width=r-l;height=b-t; viewport(vw,vh);
    }
}
