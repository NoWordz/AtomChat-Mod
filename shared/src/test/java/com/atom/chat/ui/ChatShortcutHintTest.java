package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatShortcutHintTest {
    @Test void sameVisibilityDrawsTheSameFrameInEitherDirection() {
        var visible = ChatShortcutHint.frame(1);
        var hidden = ChatShortcutHint.frame(0);
        assertEquals(1, visible.alpha());
        assertEquals(0, visible.offsetX(), .0001F);
        assertEquals(0, hidden.alpha());
        assertTrue(hidden.offsetX() < 0);
        for (float progress : new float[]{0, .1F, .3F, .7F, 1}) {
            var frame = ChatShortcutHint.frame(1 - progress);
            assertEquals(1 - progress, frame.alpha(), .0001F);
            assertEquals(hidden.offsetX() * progress, frame.offsetX(), .0001F);
        }
    }
}
