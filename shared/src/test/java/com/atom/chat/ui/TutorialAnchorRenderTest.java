package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.onboarding.*;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TutorialAnchorRenderTest {
    @BeforeAll static void setup(@TempDir Path root)throws Exception{UiFeatureRenderTest.setup(root);}
    @Test void annotationShadeNeverSquaresOffThePhoneCorners() {
        try(var surface=Surface.makeRasterN32Premul(1000,700);var pixels=new Bitmap()) {
            surface.getCanvas().clear(0xFF718092);var layout=UiLayout.ofRoot(20,20,400,600);
            var session=new TutorialSession();session.start();var overlay=new TutorialOverlay();
            overlay.viewport(new UiLayout.Rect(0,0,1000,700));overlay.shape(new TutorialAnchor(new UiLayout.Rect(50,150,160,160),0));
            overlay.render(surface.getCanvas(),session,layout,layout,layout.tabBar,k->"说明",0,0,1000);
            pixels.allocN32Pixels(1000,700);assertTrue(surface.readPixels(pixels,0,0));
            assertEquals(0xFF718092,pixels.getColor(20,20),"rounded phone corners must keep showing the world");
            overlay.close();
        }
    }
    @Test void squareAndCircularControlsKeepTheirOwnMaskAndRingContour() {
        int previous=AtomChatConfig.get().accentColor;AtomChatConfig.get().accentColor=0xFF00FF00;
        try(var square=Surface.makeRasterN32Premul(1000,700);var circle=Surface.makeRasterN32Premul(1000,700);
            var a=new Bitmap();var b=new Bitmap()) {
            var panel=UiLayout.ofRoot(20,20,400,600);var session=new TutorialSession();session.start();
            var target=new UiLayout.Rect(50,150,160,160);
            for(int i=0;i<2;i++) {
                var surface=i==0?square:circle;surface.getCanvas().clear(0xFF000000);
                var overlay=new TutorialOverlay();overlay.viewport(new UiLayout.Rect(0,0,1000,700));
                overlay.shape(new TutorialAnchor(target,i==0?0:80));
                overlay.render(surface.getCanvas(),session,panel,panel,panel.tabBar,k->"说明",0,0,1000);
                overlay.close();
            }
            a.allocN32Pixels(1000,700);b.allocN32Pixels(1000,700);
            assertTrue(square.readPixels(a,0,0));assertTrue(circle.readPixels(b,0,0));
            assertTrue(((a.getColor(50,150)>>>8)&255)>100,"square controls need a square corner");
            assertTrue(((b.getColor(50,150)>>>8)&255)<30,"round avatars must not gain a square corner");
        } finally {AtomChatConfig.get().accentColor=previous;}
    }
}
