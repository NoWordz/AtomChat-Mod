package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionChipsTest {
    @Test
    void tooltipIsSuppressedOnlyWhileAnOverlayOwnsThePointer() {
        assertFalse(ReactionChips.tooltipSuppressed(false, false, false, false, false));
        assertTrue(ReactionChips.tooltipSuppressed(true, false, false, false, false));
        assertTrue(ReactionChips.tooltipSuppressed(false, true, false, false, false));
        assertTrue(ReactionChips.tooltipSuppressed(false, false, true, false, false));
        assertTrue(ReactionChips.tooltipSuppressed(false, false, false, true, false));
        assertTrue(ReactionChips.tooltipSuppressed(false, false, false, false, true));
    }
}
