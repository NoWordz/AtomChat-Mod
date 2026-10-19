package com.atom.chat.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderTextLayoutTest {
    @Test void narrowHeaderKeepsActionsAndDropsClockBeforeOverlappingTitle() {
        UiLayout.Rect header = new UiLayout.Rect(10, 10, 220, 55);
        UiLayout.Rect back = new UiLayout.Rect(15, 15, 45, 45);
        UiLayout.Rect filter = new UiLayout.Rect(65, 15, 45, 45);
        ShellHeader.TextBounds bounds = ShellHeader.textBounds(header, back, filter, 110, 65);
        assertFalse(bounds.showTime());
        assertTrue(bounds.left() > filter.right());
        assertTrue(bounds.right() <= header.right());
        assertTrue(bounds.right() >= bounds.left());
    }
    @Test void wideHeaderReservesClockSpaceAndKeepsCenteredTitleAvailable() {
        UiLayout.Rect header = new UiLayout.Rect(0, 0, 600, 55);
        ShellHeader.TextBounds bounds = ShellHeader.textBounds(header, null, null, 100, 65);
        assertTrue(bounds.showTime());
        assertTrue(bounds.right() < header.right() - 65);
        assertTrue(bounds.left() < 250 && bounds.right() > 350);
    }
}
