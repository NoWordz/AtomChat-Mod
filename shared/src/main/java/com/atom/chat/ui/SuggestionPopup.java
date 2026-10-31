package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.font.FontManager;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.FormattedLine;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import java.util.List;

/** Appearance only. Parsing, selected candidates, application, scrolling and narration stay vanilla. */
public final class SuggestionPopup {
    private SuggestionGeometry geometry;
    private List<FormattedLine> labels = List.of();
    private int offset, selected = -1;
    private float progress;
    private boolean open;
    private boolean interactive = true;
    private ComposerPopupTransform pose;
    private final float[] wash = new float[10];
    private final PressScale[] bounce = java.util.stream.IntStream.range(0,10).mapToObj(i -> PressScale.bounce()).toArray(PressScale[]::new);
    public SuggestionPopup() { }
    public void reset() {
        geometry=null;labels=List.of();progress=0;pose=null;open=false;interactive=true;selected=-1;offset=0;resetRows();
    }
    private void resetRows() { java.util.Arrays.fill(wash,0);for(int i=0;i<bounce.length;i++)bounce[i]=PressScale.bounce(); }
    public int hit(float x, float y) {
        if (!open || !interactive || geometry == null || pose == null) return -1;
        var p = pose.toLocal(x,y); return geometry.hit(p.x(),p.y());
    }
    public UiLayout.Rect paintedBounds() { return geometry == null || pose == null ? null : pose.paintedBounds(geometry.bounds()); }
    public static float measure(List<FormattedLine> labels) {
        var font = FontManager.font(UiTokens.FONT_BUTTON);
        float width = 0; for (FormattedLine label : labels) width = Math.max(width,label.width(font));
        return width;
    }
    public void draw(Canvas canvas, SuggestionGeometry next, List<FormattedLine> labels, int offset, int selected,
                     float mx, float my, float dt) {
        prepare(next,labels,offset,dt);
        paint(canvas,selected,mx,my,dt);
    }
    /** Establish the exact pose before vanilla hover selection is evaluated. */
    public void prepare(SuggestionGeometry next,List<FormattedLine> labels,int offset,float dt) {
        prepare(next,labels,offset,true,dt);
    }
    public void prepare(SuggestionGeometry next,List<FormattedLine> labels,int offset,boolean interactive,float dt) {
        open = next != null && !labels.isEmpty();
        if (open) {
            if(this.interactive!=interactive)resetRows();
            this.geometry=next;this.labels=labels;this.offset=offset;this.interactive=interactive;
        }
        progress = UiMotion.approach(progress,open ? 1 : 0,dt,Animations.ms(UiMotion.POPUP_MS));
        if (progress < .01F || geometry == null) { if (!open) reset(); return; }
        pose = ComposerPopupTransform.of(geometry.bounds(),progress);
    }
    public void paint(Canvas canvas,int selected,float mx,float my,float dt) {
        if (progress < .01F || geometry == null || pose == null) return;
        if (open) this.selected=selected;
        var box = geometry.bounds();
        var pointer = pose.toLocal(mx,my); int hovered = open && interactive ? geometry.hit(pointer.x(),pointer.y()) : -1;
        int saved = canvas.getSaveCount(); canvas.save();
        try (var opacity = new Paint().setAlphaf(progress)) {
            float pad = UiTokens.COMPOSER_POPUP_LAYER_PAD;
            if (progress < 1) canvas.saveLayer(Rect.makeXYWH(box.x()-pad,box.y()-pad,box.w()+2*pad,box.h()+2*pad),opacity);
            pose.apply(canvas);
            MenuPopup.drawSurface(canvas,box.x(),box.y(),box.w(),box.h());
            var font = FontManager.font(UiTokens.FONT_BUTTON);
            for (int row=0;row<geometry.rows() && row+this.offset<this.labels.size();row++) {
                var rect=geometry.row(row); boolean active=row+this.offset==this.selected;
                wash[row]=UiMotion.approach(wash[row],active || row==hovered ? 1 : 0,dt,UiMotion.HOVER_MS);
                MenuPopup.updateRowBounce(bounce[row],row==hovered,dt,rect.w());
                MenuPopup.beginRowBounce(canvas,bounce[row],rect.x()+rect.w()/2,rect.y()+rect.h()/2);
                try {
                    if (interactive) MenuPopup.drawRowWash(canvas,rect.x(),rect.y(),rect.w(),rect.h(),wash[row]);
                    // Limit long native labels, independently of the uncut wash/bounce contour.
                    canvas.save();
                    try {
                        canvas.clipRect(Rect.makeXYWH(rect.x()+SuggestionGeometry.PAD,rect.y(),
                                Math.max(0,rect.w()-SuggestionGeometry.PAD*2),rect.h()));
                        this.labels.get(row+this.offset).draw(canvas,font,rect.x()+SuggestionGeometry.PAD,
                                SkiaFontRenderer.centerBaselineY(font,rect.y()+rect.h()/2),active ? 0xFFFFFFFF : 0xFFCDD3DE);
                    } finally { canvas.restore(); }
                } finally { MenuPopup.endRowBounce(canvas); }
            }
        } finally { canvas.restoreToCount(saved); }
    }
    /** Settled render for exports; the screen retains an instance and uses draw() every frame. */
    public static void render(Canvas canvas,SuggestionGeometry geometry,List<FormattedLine> labels,int offset,int selected) {
        new SuggestionPopup().draw(canvas,geometry,labels,offset,selected,-1,-1,UiMotion.POPUP_MS);
    }
}
