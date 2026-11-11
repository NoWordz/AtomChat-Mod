package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatChromeResizeRegressionTest {
    @Test void typingAndEmojiResizeMustNotStartANavigationSpring() {
        var oneLine = new UiLayout.Rect(20, 640, 480, 70);
        var twoLines = new UiLayout.Rect(20, 610, 480, 100);
        var emoji = new UiLayout.Rect(20, 410, 480, 100);
        try (var chrome = new BottomChromeTransition()) {
            chrome.beginFrame(BottomChromeTransition.Mode.CHAT, oneLine, oneLine, 1000, true);
            chrome.beginFrame(BottomChromeTransition.Mode.CHAT, twoLines, oneLine, 1016, true);
            assertEquals(twoLines, chrome.paintedRect(), "row layout already owns its old height animation");
            chrome.beginFrame(BottomChromeTransition.Mode.CHAT, emoji, oneLine, 1032, true);
            assertEquals(emoji, chrome.paintedRect(), "emoji opening must not acquire a navigation spring");
        }
    }
    @Test void actualPageSwitchStillMorphsAndCanReverse() {
        var input = new UiLayout.Rect(20, 610, 480, 100);
        var nav = new UiLayout.Rect(20, 640, 480, 70);
        try (var chrome = new BottomChromeTransition()) {
            chrome.beginFrame(BottomChromeTransition.Mode.CHAT, input, nav, 1000, true);
            chrome.beginFrame(BottomChromeTransition.Mode.NAV, input, nav, 1016, true);
            chrome.beginFrame(BottomChromeTransition.Mode.NAV, input, nav, 1150, true);
            assertNotEquals(nav, chrome.paintedRect());
            var before = chrome.paintedRect();
            chrome.beginFrame(BottomChromeTransition.Mode.CHAT, input, nav, 1150, true);
            assertEquals(before, chrome.paintedRect());
        }
    }
}
