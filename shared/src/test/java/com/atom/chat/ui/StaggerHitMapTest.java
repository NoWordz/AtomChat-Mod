package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaggerHitMapTest {
    @Test void visibleGeometryIsInvertedExactlyOnceAndOldRestingTargetsAreRejected() throws Exception {
        Class<?> type=assertDoesNotThrow(()->Class.forName("com.atom.chat.ui.StaggerHitMap"));
        Object map=type.getConstructor().newInstance();
        var rect=new UiLayout.Rect(20,100,200,80);
        type.getMethod("add",UiLayout.Rect.class,float.class,float.class,float.class,float.class,float.class)
                .invoke(map,rect,24F,18F,.97F,120F,140F);
        Object point=type.getMethod("inverse",float.class,float.class).invoke(map,144F,158F);
        assertEquals(120F,((Number)point.getClass().getMethod("x").invoke(point)).floatValue(),.0001F);
        assertEquals(140F,((Number)point.getClass().getMethod("y").invoke(point)).floatValue(),.0001F);
        Object outside=type.getMethod("inverse",float.class,float.class).invoke(map,21F,101F);
        assertTrue(((Number)outside.getClass().getMethod("x").invoke(outside)).floatValue() < -1000,"old stationary target cannot remain clickable");
        type.getMethod("clear").invoke(map);
        Object rest=type.getMethod("inverse",float.class,float.class).invoke(map,21F,101F);
        assertEquals(21F,((Number)rest.getClass().getMethod("x").invoke(rest)).floatValue());
    }
}
