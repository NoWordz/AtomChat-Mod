package com.atom.chat.ui;

import com.atom.chat.onboarding.*;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TutorialControllerTest {
    @TempDir Path root;
    static class Host implements TutorialController.Host {
        int enters,leaves,navigation;
        TutorialSession.Step current;
        public String translate(String key){return key.substring(key.lastIndexOf('.')+1);}
        public void enter(){enters++;}
        public void show(TutorialSession.Step step){current=step;}
        public void leave(){leaves++;}
        public void selectRoot(int index){navigation=index;}
        public boolean mayOffer(){return false;}
        public boolean allowLivePointer(TutorialSession.Step step,float x,float y){return step==TutorialSession.Step.APPEARANCE&&x>200;}
    }
    @Test void tutorialConsumesDangerousInputAndRestoresOnceOnExit() throws Exception {
        var host=new Host();var controller=new TutorialController(host,v->(float)v,v->(float)v);
        controller.initialize(root.resolve("atomchat/client/onboarding.json"));
        controller.start();
        var gate=new com.atom.chat.ui.input.InputGate(()->false).add(controller);
        assertTrue(gate.click(-100,-100,0));
        assertTrue(controller.onChar('x',0));
        assertTrue(controller.onKey(86,0,2),"paste must not reach the live upload handler");
        assertTrue(controller.onScroll(0,0,0,1));
        assertTrue(gate.drag(0,0,0,1,1));
        assertTrue(gate.release(0,0,0));
        assertTrue(controller.onKey(256,0,0));
        assertFalse(controller.active());
        controller.reset();assertEquals(1,host.leaves);
        controller.start();assertEquals(TutorialSession.Step.NAVIGATION,host.current);
        controller.reset();assertEquals(2,host.leaves);
    }
    @Test void allStepsRenderAndResizeWithoutCanvasLeaks() throws Exception {
        var session=new TutorialSession();var overlay=new TutorialOverlay();session.start();
        for(int i=0;i<TutorialSession.Step.values().length;i++) {
            try(var surface=Surface.makeRasterN32Premul(800,1100)) {
                var c=surface.getCanvas();int saves=c.getSaveCount();
                for(float width:new float[]{400,560,720}) {
                    var layout=UiLayout.of(10,10,width,1000,0,0);
                    var nav=UiLayout.ofRoot(10,10,width,1000);
                    overlay.render(c,session,layout,nav,layout.inputBar,k->"A readable tutorial paragraph with details to exercise wrapping.",0,0);
                    assertEquals(saves,c.getSaveCount(),session.step().name());
                    assertTrue(overlay.geometry().panel().contains(overlay.geometry().card()));
                    for(var button:overlay.geometry().buttons())assertTrue(overlay.geometry().card().contains(button));
                }
            }
            session.next();
        }
        overlay.close();
    }
    @Test void unchangedTutorialTextDoesNotRebuildItsLayoutEveryFrame() {
        var session=new TutorialSession();session.start();var overlay=new TutorialOverlay();
        var layout=UiLayout.ofRoot(10,10,520,950);
        try(var surface=Surface.makeRasterN32Premul(560,1000)) {
            for(int i=0;i<60;i++)overlay.render(surface.getCanvas(),session,layout,layout,layout.tabBar,k->"Stable localized paragraph",0,0);
            assertEquals(1,overlay.textLayoutBuildCount());
        }
        overlay.close();
    }
    @Test void appearanceDoesNotLeakTypingOrPasteToTheHiddenChatField() {
        var host=new Host();var controller=new TutorialController(host,v->(float)v,v->(float)v);
        controller.start();
        var overlayField=getOverlay(controller);
        var gate=new com.atom.chat.ui.input.InputGate(()->false).add(controller);
        try(var surface=Surface.makeRasterN32Premul(560,1100)) {
            var layout=UiLayout.ofDetail(10,10,520,1000);
            var root=UiLayout.ofRoot(10,10,520,1000);
            while(controller.step()!=TutorialSession.Step.APPEARANCE) {
                controller.render(surface.getCanvas(),layout,root,root.tabBar,0,0,16);
                var skip=overlayField.geometry().buttons().get(2);
                double x=skip.x()+skip.w()/2, y=skip.y()+skip.h()/2;
                var before=controller.step();
                assertTrue(gate.click(x,y,0));
                assertNotEquals(before,controller.step(),"pressing Skip advances the guide");
                assertTrue(gate.release(x,y,0),"release of tutorial control must not reach the new page");
            }
            assertTrue(controller.onChar('x',0));
            assertTrue(controller.onKey(86,0,2));
            controller.reset();
        }
    }
    private static TutorialOverlay getOverlay(TutorialController controller) {
        try{var field=TutorialController.class.getDeclaredField("overlay");field.setAccessible(true);return (TutorialOverlay)field.get(controller);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
}
