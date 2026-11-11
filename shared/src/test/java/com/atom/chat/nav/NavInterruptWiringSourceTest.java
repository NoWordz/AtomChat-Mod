package com.atom.chat.nav;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guard for the interruptibility contract: <b>a running page/tab
 * transition never swallows input</b>.
 *
 * <p><b>Why a source probe.</b> The gate mechanism itself
 * ({@code InputGate}) is host-driven and unit-tested; the contract lives in
 * the screen's {@code panelAnimating()} answer, which no offline test can
 * reach (the screen needs the Minecraft client). The regression this pins:
 * the gate's answer used to include {@code director.pageNavActive()} and
 * {@code director.rootTransitionActive()}, so every click during a ZOOM/SLIDE
 * switch — including bottom-tab clicks — was swallowed until the animation
 * finished, while the settings and emoji tabs (whose transitions never fed
 * the gate) stayed smooth. The director's state machines already re-arm
 * mid-flight ({@code switchRoot} snaps a live transition before re-arming);
 * the gate was the only blocker.</p>
 *
 * <p>What stays gated on purpose: the panel open/close transform and the
 * settings chip transition. There the drawn geometry really does diverge from
 * the hit-tested layout (the open zoom scales the whole panel), so a
 * mid-flight click would land somewhere other than where it looks — a
 * different rationale from the page/tab clocks, whose transforms are
 * draw-only overlays. Same for the vanilla-chat interop probes
 * ({@code canUseForeignChatUi}) and the chat-input visibility, which keep
 * their nav clauses: they are about what renders, not about whether the panel
 * answers clicks.</p>
 */
class NavInterruptWiringSourceTest {

    /** The three screen sources, repo-root relative, in a fixed order. */
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !dir.resolve("shared").resolve("PLATFORM-SEAMS.md").toFile().exists()) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("repo root not found");
        }
        return dir;
    }

    /** Reads a source file with line endings normalised to LF; see the repo's {@code text=auto} policy. */
    private static String source(Path root, String relative) throws java.io.IOException {
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8)
                                .replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test
    void panelAnimatingNoLongerCountsTheNavClocks() throws Exception {
        for (String screen : SCREENS) {
            String s = source(root(), screen);
            int gate = s.indexOf("public boolean panelAnimating()");
            assertTrue(gate >= 0, screen + ": panelAnimating host method missing");
            String body = s.substring(gate, s.indexOf('}', gate));
            assertTrue(body.contains("PanelOpenMotion.animating(panelProgress, closing)"),
                    screen + ": the panel open/close gate must stay");
            // The chip crossfade is gone: a chip switch replays the page
            // entrance instead of owning a second clock.
            assertFalse(body.contains("chipTransitionActive"), screen);
            assertFalse(body.contains("pageNavActive"), screen);
            assertFalse(body.contains("rootTransitionActive"), screen);
        }
    }

    @Test
    void clickRipplesPlayDuringPageNav() throws Exception {
        for (String screen : SCREENS) {
            String s = source(root(), screen);
            int ripples = s.indexOf("clickRipples.click(");
            assertTrue(ripples >= 0, screen + ": ripple arm missing");
            // The condition guarding the ripple arm must not consult the page
            // nav clock any more: the ripple is pure feedback for a click the
            // gate no longer swallows.
            int condStart = s.lastIndexOf("if (", ripples);
            String condition = s.substring(condStart, ripples);
            assertFalse(condition.contains("pageNavActive"),
                    screen + ": ripple condition still gates on the page nav: " + condition);
            assertTrue(condition.contains("PanelOpenMotion.animating"),
                    screen + ": the panel-open ripple gate must stay");
        }
    }

    @Test
    void rootTabsStaggerTheirOwnItemsAndKeepTheEnvelopeForPushedPages() throws Exception {
        for(String screen:SCREENS){
            String s=source(root(),screen);
            assertTrue(s.contains("case CHAT_LIST, PROFILE, SETTINGS -> drawRootBody("));
            for(String page:List.of("conversationListPage","settingsHomePage","profilePage","settingsSectionPage"))
                assertFalse(s.contains(page+".markEntered()"),"page composite must not stack item restarts: "+screen);
        }
    }

    @Test
    void returningToARootPageGivesTheBottomBarItsOwnEntrance() throws Exception {
        for(String screen:SCREENS){String s=source(root(),screen);
            assertTrue(s.contains("bottomChrome.beginFrame("));assertTrue(s.contains("bottomChrome.draw("));
            assertFalse(s.contains("bottomTabBar.markEntered()"));
        }
    }

    @Test
    void screenExposesNoPageNavStateAnyMore() throws Exception {
        for (String screen : SCREENS) {
            String s = source(root(), screen);
            // The controller lands every stack change synchronously now; there is
            // no in-flight state to expose, and a reintroduced one would silently
            // re-create the swallowed-input class of bug this file guards against.
            for (String dead : List.of("pageNavLive", "pageNavPopPending", "rootTransitionLive",
                    "navStyleIsZoom", "beginPageNav", "abandonPageNav", "markRootTabSpan")) {
                assertFalse(s.contains(dead),
                        screen + ": the transition port left \"" + dead + "\" behind");
            }
        }
    }

    @Test
    void eachArrivingSurfacePlaysExactlyOneMotion() throws Exception {
        // One transition, one motion. A list page staggers its own rows and skips
        // the shell envelope (the composite's host flag); a chat page keeps the
        // envelope and its rows stay settled under it. Arming both clocks is the
        // "the cards jump up" bug that got the previous wiring rejected.
        for (String screen : SCREENS) {
            String s = source(root(), screen);
            assertFalse(s.contains("conversationListPage.markSettled()"),
                    screen + ": the root list now staggers on arrival");
            assertFalse(s.contains("settingsSectionPage.markSettled()"),
                    screen + ": the settings section now staggers on arrival");
            assertTrue(s.contains("composite.drawArrivingFrame("));
            assertFalse(s.contains("settingsSectionPage.markEntered()"));
        }
        for (String page : List.of(
                "layers/mapping/official/src/main/java/com/atom/chat/page/ConversationListPage.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/page/ConversationListPage.java")) {
            String s = source(root(), page);
            assertTrue(s.contains("UiStagger.itemRise(entrance, staggerIndex, now, axis)"), page
                    + ": the stagger index must be the visible slot");
        }
        for (String page : List.of(
                "layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java")) {
            String s = source(root(), page);
            assertTrue(s.contains("UiStagger.itemRise(entrance, staggerIndex, now, axis)"), page);
            assertFalse(s.contains("chipSwitchFromId"),
                    page + ": the chip crossfade is gone — a switch replays the entrance");
        }
        for (String page : List.of(
                "layers/mapping/official/src/main/java/com/atom/chat/ui/EmojiPanel.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/ui/EmojiPanel.java")) {
            String s = source(root(), page);
            assertTrue(s.contains("entrance.markEntered();"), page + ": the grid arms on open");
            assertTrue(s.contains("UiStagger.itemRise(entrance, row, rowNow, UiStagger.Axis.VERTICAL)"), page);
            assertTrue(s.contains("UiStagger.itemAlpha(entrance, row, rowNow)"), page
                    + ": the row fade rides the same progress as its rise");
        }
    }
}
