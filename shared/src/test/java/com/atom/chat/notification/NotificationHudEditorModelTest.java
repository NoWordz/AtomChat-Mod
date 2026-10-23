package com.atom.chat.notification;

import com.atom.chat.notification.NotificationHudLayout.Anchor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationHudEditorModelTest {
    @Test void draggingNearCenterSnapsExactlyAndKeepsItAfterViewportResize() {
        var model = new NotificationHudEditorModel(300,60,12,Anchor.TOP_CENTER,0,0,1);
        model.begin(500,42,1000,800,false);
        model.drag(503,402,1000,800);
        model.end();
        var r=model.bounds(1000,800).rect();
        assertEquals(500,r.x()+r.w()/2,0.001);
        assertEquals(400,r.y()+r.h()/2,0.001);
        assertEquals(Anchor.CENTER,model.anchor());
        assertEquals(0,model.offsetX()); assertEquals(0,model.offsetY());
        r=model.bounds(1400,900).rect();
        assertEquals(700,r.x()+r.w()/2,0.001); assertEquals(450,r.y()+r.h()/2,0.001);
    }
    @Test void freeDragPersistsOffsetsAndResizeKeepsTheAnchorWithUniformScale() {
        var model = new NotificationHudEditorModel(300,60,12,Anchor.CENTER,0,0,1);
        model.begin(500,400,1000,800,false);
        model.drag(620,490,1000,800); model.end();
        var free=model.bounds(1000,800).rect();
        assertEquals(620,free.x()+free.w()/2,0.001);
        assertEquals(490,free.y()+free.h()/2,0.001);
        model.reset();
        var before=model.bounds(1000,800).rect();
        model.begin(before.right(),before.bottom(),1000,800,true);
        model.drag(before.right()+150,before.bottom()+30,1000,800); model.end();
        var after=model.bounds(1000,800).rect();
        assertEquals(1.5,model.scale(),0.001);
        assertEquals(500,after.x()+after.w()/2,0.001);
        assertEquals(12,after.y(),0.001);
        assertEquals(5,after.w()/after.h(),0.001);
    }
    @Test void resizeAndDragCannotPutTheBannerOutsideTheViewport() {
        var model=new NotificationHudEditorModel(800,200,12,Anchor.BOTTOM_RIGHT,900,900,2);
        var p=model.bounds(360,240);
        assertTrue(p.rect().right()<=360); assertTrue(p.rect().bottom()<=240);
        assertTrue(p.scale()>0); assertTrue(Float.isFinite(p.scale()));
        model.begin(100,100,360,240,false); model.drag(-500,-500,360,240); model.end();
        assertTrue(model.bounds(360,240).rect().x()>=0);
        assertTrue(model.bounds(360,240).rect().y()>=0);
    }
}
