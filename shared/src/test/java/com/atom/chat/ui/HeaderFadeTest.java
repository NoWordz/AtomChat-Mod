package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeaderFadeTest {
    private static final long DURATION = UiMotion.PAGE_NAV_MS;

    @Test void outgoingControlCannotReceiveTheIncomingAction() {
        var fade = new HeaderFade<String, String>();
        assertEquals(1, fade.draw("filter", "everyone", 0, DURATION).opacity());
        assertTrue(fade.accepts("filter"));

        var from = fade.draw("restore", "restore defaults", 10, DURATION);
        assertEquals("everyone", from.value());
        assertFalse(fade.accepts("restore"));

        var handoff = fade.draw("restore", "restore defaults", 80, DURATION);
        assertEquals("restore defaults", handoff.value());
        assertEquals(0, handoff.opacity());
        assertFalse(fade.accepts("restore"));

        var entering = fade.draw("restore", "restore defaults", 110, DURATION);
        assertTrue(entering.opacity() > .4F && entering.opacity() < 1);
        assertTrue(fade.accepts("restore"));
        assertFalse(fade.accepts("filter"));
    }

    @Test void aRapidReturnStartsFromTheLastPaintedOpacity() {
        var fade = new HeaderFade<String, String>();
        fade.draw("old", "Old title", 0, DURATION);
        fade.draw("new", "New title", 10, DURATION);
        var leaving = fade.draw("new", "New title", 35, DURATION);
        assertEquals("Old title", leaving.value());
        assertTrue(leaving.opacity() > 0 && leaving.opacity() < 1);
        assertEquals(leaving, fade.draw("old", "Old title", 41, DURATION));
        assertTrue(fade.draw("old", "Old title", 75, DURATION).opacity() > leaving.opacity());
    }

    @Test void commonActionsKeepFullOpacityAndDisabledMotionSettlesImmediately() {
        var fade = new HeaderFade<String, String>();
        fade.draw("back", "Back", 0, DURATION);
        assertEquals(1, fade.draw("back", "Back", 10, DURATION).opacity());
        assertTrue(fade.accepts("back"));
        fade.draw(null, null, 20, DURATION);
        assertEquals(0, fade.draw(null, null, 21, 0).opacity());
        assertFalse(fade.accepts("back"));
        assertEquals(1, fade.draw("back", "Back", 22, 0).opacity());
        assertTrue(fade.accepts("back"));
    }
}
