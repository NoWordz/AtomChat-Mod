package com.atom.chat.ui;

import com.atom.chat.nav.*;
import io.github.humbleui.skija.Paint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransitionLifecycleTest {
    private Paint paint(Object owner, String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);
        return (Paint)field.get(owner);
    }
    @Test void returningToParentReinitializesPageNativeResources() throws Exception {
        var composite=new PageTransitionComposite(new PageTransitionComposite.Host() {
            public NavPage topNav(){return NavPage.of(AppPage.SETTINGS);}
            public void drawArrivingBody(io.github.humbleui.skija.Canvas c,UiLayout l,int x,int y){}
            public void drawArrivingHeader(io.github.humbleui.skija.Canvas c,UiLayout l,int x,int y,float vx,float vy){}
            public boolean arrivalStaggersSurfaces(){return false;}
        });
        var first=paint(composite,"alphaPaint");
        composite.close();assertTrue(first.isClosed());
        composite.reset();
        assertFalse(paint(composite,"alphaPaint").isClosed(),"same parent Screen init must own a usable Paint before any native call");
        composite.close();composite.close();
    }
    @Test void returningToParentReinitializesChromeNativeResources() throws Exception {
        var chrome=new BottomChromeTransition();var first=paint(chrome,"alpha");
        chrome.close();assertTrue(first.isClosed());
        chrome.reset();
        assertFalse(paint(chrome,"alpha").isClosed(),"same parent Screen init must own a usable Paint before any native call");
        chrome.close();chrome.close();
    }
}
