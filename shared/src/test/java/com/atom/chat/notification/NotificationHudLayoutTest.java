package com.atom.chat.notification;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationHudLayoutTest {
    @Test void eachAnchorPositionsTheWholeStackWithOffsets() {
        for (var anchor : NotificationHudLayout.Anchor.values()) {
            var r = NotificationHudLayout.rect(1000, 800, 200, 100, 20, anchor, 5, -5);
            int col = anchor.ordinal() % 3, row = anchor.ordinal() / 3;
            assertEquals(new float[]{25,405,785}[col], r.x(), .01);
            assertEquals(new float[]{15,345,675}[row], r.y(), .01);
            assertEquals(200, r.w()); assertEquals(100, r.h());
        }
    }
    @Test void smallScreensAndExtremeOffsetsNeverLoseTheStack() {
        var r = NotificationHudLayout.rect(80, 50, 400, 200, 16,
                NotificationHudLayout.Anchor.BOTTOM_RIGHT, 10000, -10000);
        assertTrue(r.w() > 0 && r.h() > 0);
        assertTrue(r.x() >= 0 && r.right() <= 80);
        assertTrue(r.y() >= 0 && r.bottom() <= 50);
    }
    @Test void scalingKeepsTheWholeStackAtTheExactAnchorCentre() {
        var scaled = NotificationHudLayout.scaled(1000, 800, 300, 100, 20,
                NotificationHudLayout.Anchor.CENTER, 0, 0, 1.5F);
        assertEquals(450, scaled.rect().w(), 0.01);
        assertEquals(150, scaled.rect().h(), 0.01);
        assertEquals(500, scaled.rect().x() + scaled.rect().w() / 2, 0.01);
        assertEquals(400, scaled.rect().y() + scaled.rect().h() / 2, 0.01);
        assertEquals(1.5F, scaled.scale(), 0.001F);
    }
    @Test void hudIsExclusiveToUnobstructedGameplay() {
        assertTrue(NotificationHudLayout.visible(true,true,false,false,false));
        assertFalse(NotificationHudLayout.visible(false,true,false,false,false));
        assertFalse(NotificationHudLayout.visible(true,false,false,false,false));
        assertFalse(NotificationHudLayout.visible(true,true,true,false,false));
        assertFalse(NotificationHudLayout.visible(true,true,false,true,false));
        assertFalse(NotificationHudLayout.visible(true,true,false,false,true));
    }
}
