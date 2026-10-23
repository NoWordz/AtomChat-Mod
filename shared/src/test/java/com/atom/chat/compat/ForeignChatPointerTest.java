package com.atom.chat.compat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForeignChatPointerTest {
    @Test void closingASuggestionCannotExposeItsHeldPressToQuark() {
        var pointer = new ForeignChatPointer();
        assertTrue(pointer.press(0, true, true));
        assertTrue(pointer.hoverMasked(false));
        pointer.release(0);
        assertFalse(pointer.hoverMasked(false));
    }

    @Test void closingAModalOrFinishingAnimationDoesNotReplayThePress() {
        var pointer = new ForeignChatPointer();
        assertTrue(pointer.press(0, false, false));
        assertTrue(pointer.hoverMasked(false));
        pointer.release(0);
        assertFalse(pointer.press(0, true, false));
        assertFalse(pointer.hoverMasked(false));
    }

    @Test void aSecondaryPressAndReleaseCannotUnmaskAHeldPrimaryPress() {
        var pointer = new ForeignChatPointer();
        pointer.press(0, false, false);
        assertFalse(pointer.press(1, true, false));
        pointer.release(1);
        assertTrue(pointer.hoverMasked(false));
        pointer.release(0);
        assertFalse(pointer.hoverMasked(false));
    }

    @Test void recreatingQuarkButtonsOnResizeCannotRepeatAnAlreadyHandledPress() {
        var pointer = new ForeignChatPointer();
        assertFalse(pointer.press(0, true, false));
        pointer.cancelForReinit();
        assertTrue(pointer.hoverMasked(false));
        pointer.release(0);
        pointer.cancelForReinit();
        assertFalse(pointer.hoverMasked(false));
    }

    @Test void merelyHoveringACoveringPopupDoesNotLatchTheMask() {
        var pointer = new ForeignChatPointer();
        assertTrue(pointer.hoverMasked(true));
        assertFalse(pointer.hoverMasked(false));
    }
}
