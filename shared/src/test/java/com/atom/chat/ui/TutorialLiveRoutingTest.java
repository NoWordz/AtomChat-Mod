package com.atom.chat.ui;

import com.atom.chat.onboarding.*;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialLiveRoutingTest {
    @org.junit.jupiter.api.BeforeAll static void setup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)throws Exception {UiFeatureRenderTest.setup(root);}
    @Test void aRealImagePreviewKeepsEscapeAheadOfTheTutorial() {
        var preview=new com.atom.chat.image.ImagePreviewOverlay();
        var host=new TutorialControllerTest.Host(){@Override public boolean liveModalActive(){return preview.isActive();}};
        var controller=new TutorialController(host,x->(float)x,y->(float)y);controller.start();
        try(var surface=Surface.makeRasterN32Premul(100,100);var image=surface.makeImageSnapshot()) {
            preview.open(image);
            assertFalse(controller.onKey(256,0,0),"Escape belongs to the image preview");
            assertTrue(controller.touring(),"closing a child preview must not exit the tutorial");
        } finally {preview.close();controller.reset();}
    }
    @Test void avatarMenuStepRejectsLeftClickBeforeItCanNavigateAway() throws Exception {
        var host=new TutorialControllerTest.Host(){
            @Override public java.util.List<TutorialGuide.Entry> itinerary(){return java.util.List.of(TutorialGuide.Entry.page(TutorialSession.Step.PLAYERS_BLOCK));}
            @Override public boolean allowLivePointer(TutorialSession.Step step,float x,float y){return true;}
            @Override public UiLayout.Rect anchor(TutorialSession.Step step){return new UiLayout.Rect(30,160,70,70);}
        };
        var controller=new TutorialController(host,x->(float)x,y->(float)y);controller.start();
        try(var surface=Surface.makeRasterN32Premul(1000,800)) {
            var layout=UiLayout.ofRoot(20,20,400,700);
            controller.render(surface.getCanvas(),layout,layout,layout.tabBar,0,0,1000);
            assertTrue(Boolean.TRUE.equals(newGate(controller).click(60,190,0)),"left click must not escape to profile during the right-click task");
            assertFalse(Boolean.TRUE.equals(newGate(controller).click(60,190,1)),"the requested right click must reach the avatar");
        } finally {controller.reset();}
    }
    @Test void clickingConversationDoesNotAdvanceUntilNextIsPressed() throws Exception {
        var host=new TutorialControllerTest.Host();
        var controller=new TutorialController(host,x->(float)x,y->(float)y);controller.start();
        var f=TutorialController.class.getDeclaredField("overlay");f.setAccessible(true);var overlay=(TutorialOverlay)f.get(controller);
        var layout=UiLayout.ofRoot(20,20,520,900);
        try(var surface=Surface.makeRasterN32Premul(800,1000)) {
            controller.render(surface.getCanvas(),layout,layout,layout.tabBar,0,0,1000);
            var skip=overlay.geometry().buttons().get(2);tap(controller,skip.x()+2,skip.y()+2);
            assertEquals(TutorialSession.Step.CONVERSATIONS,controller.step());
            controller.render(surface.getCanvas(),layout,layout,layout.tabBar,0,0,1000);
            var target=overlay.geometry().target();tap(controller,target.x()+2,target.y()+2);
            assertEquals(TutorialSession.Step.CONVERSATIONS,controller.step(),"an action must never auto-navigate the tutorial");
        } finally {controller.reset();}
    }

    private static com.atom.chat.ui.input.InputGate newGate(TutorialController controller) {
        return new com.atom.chat.ui.input.InputGate(()->false).add(controller);
    }

    private static void tap(TutorialController controller,double x,double y) {
        var gate=newGate(controller);
        gate.click(x,y,0);
        gate.release(x,y,0);
    }
}
