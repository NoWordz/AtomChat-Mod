package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactComposerLayoutTest {
    @Test void buttonsDoNotOverlapInSmallWindowAtMaximumContentZoom() {
        UiLayout layout = UiLayout.of(0, 0, 252.44444F, 600);
        assertTrue(layout.phraseBtn.right() <= layout.sendBtn.x(),
                "640x480 at both scales 1.5 must keep phrase and send independent");
        assertTrue(layout.sendBtn.w() >= UiTokens.ACTION_BUTTON_SIZE);
    }

    @Test void narrowComposerReflowsButtonsRatherThanShrinkingThem() {
        UiLayout layout = UiLayout.of(0, 0, 200, 600);
        assertTrue(layout.sendBtn.y() >= layout.phraseBtn.bottom());
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, layout.phraseBtn.w());
        assertTrue(layout.inputTextCenterY > layout.sendBtn.bottom());
        assertEquals(layout.inputBar.y(), layout.list.bottom(), 0.001F);
    }
}
