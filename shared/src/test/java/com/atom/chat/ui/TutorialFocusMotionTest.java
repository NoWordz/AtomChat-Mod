package com.atom.chat.ui;

import com.atom.chat.onboarding.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialFocusMotionTest {
    @Test void targetChangesTravelContinuouslyAndMotionOffSnaps() {
        var motion=new TutorialFocusMotion();
        var first=new TutorialAnchor(new UiLayout.Rect(10,20,80,80),40);
        var next=new TutorialAnchor(new UiLayout.Rect(200,300,160,90),12);
        assertEquals(first.bounds(),motion.update(first,16,true).bounds());
        var moving=motion.update(next,16,true);
        assertTrue(moving.bounds().x()>10&&moving.bounds().x()<200);
        assertEquals(next.bounds(),motion.update(next,16,false).bounds());
    }
    @Test void connectorMeetsTheCircleInsteadOfItsBoundingBoxCorner() {
        var circle=new TutorialAnchor(new UiLayout.Rect(0,0,100,100),50);
        var point=TutorialFocusMotion.connectorStart(circle,150,150);
        assertEquals(50,Math.hypot(point[0]-50,point[1]-50),.01);
        assertTrue(point[0]<100&&point[1]<100);
    }
}
