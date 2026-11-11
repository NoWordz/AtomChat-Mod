package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Live screens need a client; these guards pin the otherwise unexercised wiring. */
class CompatibilityRegressionSourceTest {
    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("platforms"))) p = p.getParent();
        return java.util.Objects.requireNonNull(p);
    }

    /**
     * Reads a source file with its line endings normalised to LF. The repo
     * stores LF blobs and checks out native endings ({@code text=auto} plus
     * {@code core.autocrlf}), so on Windows the working copy is CRLF — and a
     * guard that matches a multi-line snippet with {@code \n} in it would pass
     * on CI and fail on a developer's machine (or the reverse). Normalising
     * makes the guard a statement about the code, not about the checkout.
     */
    private static String source(Path root, String relative) throws Exception {
        return Files.readString(root.resolve(relative)).replace("\r\n", "\n").replace('\r', '\n');
    }

    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java");
    private String method(String src, String name) {
        var definition = java.util.regex.Pattern.compile("(?m)^\\s*(?:public|private|protected)\\s+[\\w<>.]+\\s+" + java.util.regex.Pattern.quote(name) + "\\s*\\(").matcher(src);
        int start = definition.find() ? definition.start() : -1;
        assertTrue(start >= 0, name);
        int open = src.indexOf('{', start), depth = 1, end = open + 1;
        while (depth > 0 && end < src.length()) {
            char c = src.charAt(end++);
            if (c == '{') depth++;
            if (c == '}') depth--;
        }
        return src.substring(open, end);
    }
    @Test void foreignPaintDoesNotWaitForPanelAnimation() throws Exception {
        for (String file : SCREENS) {
            String src = source(root(), file);
            String paint = method(src, "canRenderForeignChatUi");
            assertFalse(paint.contains("PanelOpenMotion.animating"), file);
            assertTrue(src.contains("if (canRenderForeignChatUi())"), file);
            assertTrue(src.contains("!canUseForeignChatUi() ||"), "paint during animation must mask raw clicks: " + file);
        }
    }
    /**
     * The interruptibility contract (inverting the old pin): the page/tab clocks are draw-only overlays whose transforms
     * never move the hit-tested layout, so a running ZOOM/SLIDE switch must
     * NOT swallow pointer input — the bottom bar used to be unclickable until
     * the switch finished. The panel open/close transform and the settings
     * chip transition stay in the gate: there the drawn geometry really does
     * diverge from the hit-tested layout.
     */
    @Test void navigationAnimationsNoLongerGateThePointer() throws Exception {
        for (String file : SCREENS) {
            String src = source(root(), file);
            String gate = method(src, "panelAnimating");
            assertFalse(gate.contains("director.pageNavActive()"), file);
            assertFalse(gate.contains("director.rootTransitionActive()"), file);
            assertTrue(gate.contains("PanelOpenMotion.animating(panelProgress, closing)"), file);
            assertFalse(gate.contains("settingsSectionPage.chipTransitionActive()"),
                    file + ": unit E2 — the chip crossfade is decorative, not a gate");
        }
    }
    @Test void ownPublicIdentityAloneNeverSuppressesAnExternalSend() throws Exception {
        for (String platform : List.of("1.20.1-forge", "1.21.1-neoforge", "1.21.1-fabric")) {
            String src = source(root(), "platforms/" + platform
                    + "/src/main/java/com/atom/chat/mixin/ChatHudMixin.java");
            src = method(src.substring(src.indexOf("private void atomchat$captureAndStore")), "atomchat$captureAndStore");
            assertFalse(src.contains("if (ownIdentity || ownEchoText)"), platform);
            assertFalse(src.contains("if (own) {"), "own messages without a local echo must enter the feed: " + platform);
        }
    }
    @Test void transitionReleaseClearsCapturesWithoutFiringChoicesOrLinks() throws Exception {
        for (String file : SCREENS) {
            String src = source(root(), file);
            String cancel = method(src, "cancelTransitionPointer");
            assertTrue(src.contains("@Override public void cancelPointer(int button) {\n            cancelTransitionPointer(button);"), file);
            for (String cleanup : List.of("settingsSectionPage.cancelPointer()", "bottomTabBar.setPressedTab(-1)",
                    "messageWiring.takePending()", "composerPointer.setDragging(false)")) assertTrue(cancel.contains(cleanup), file);
            assertFalse(cancel.contains("endThemeStrip()"), "cancel must not commit a card: " + file);
            assertFalse(cancel.contains("handleComponentClicked"), file);
        }
    }
}
