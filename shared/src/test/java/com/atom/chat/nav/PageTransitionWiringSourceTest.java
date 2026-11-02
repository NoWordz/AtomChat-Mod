package com.atom.chat.nav;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guard for the screen's arrival flow (the one-stamp rewrite of
 * the old transition wiring).
 *
 * <p><b>Why a source probe.</b> The screens need the Minecraft client to load,
 * so no offline test can run their frame flow; the golden frames render page
 * objects and never run a navigation. The flow is therefore asserted where it
 * is written: one arrival stamp per frame, one arriving-page envelope, the
 * header drawn <em>inside</em> that envelope from the current page's own state,
 * and no residue of the deleted two-page choreography. The regression this
 * pins first is the users' "back arrow lingers" report: the old composite drew
 * the header as a fixed layer above the transition, so a leaving page's back
 * arrow outlived its page. In the inverted semantics the header belongs to the
 * arriving page's envelope — same layer, same stamp, gone the frame the page
 * is.</p>
 *
 * <p>The three screen copies differ only in Minecraft API spellings, so one set
 * of assertions runs against all three; a one-platform fix is a failure here.</p>
 */
class PageTransitionWiringSourceTest {

    /** The three screen sources, repo-root relative, in a fixed order. */
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("platforms"))) {
                return p;
            }
        }
        throw new AssertionError("repo root (the directory holding platforms/) not found above " + dir);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(repoRoot().resolve(relative), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace('\r', '\n');
    }

    /** A member body: declaration up to the first 4-space closing brace. */
    private static String body(String source, String declaration) {
        int from = source.indexOf(declaration);
        assertTrue(from >= 0, "declaration not found: " + declaration);
        int to = source.indexOf("\n    }", from);
        assertTrue(to > from, "closing brace not found for " + declaration);
        return source.substring(from, to);
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------ assertions

    /**
     * One stamp, one arrival event, one arriving frame per frame — and nothing
     * of the deleted two-page machine anywhere in the screen.
     */
    @Test
    void theFrameFlowIsOneStampOneArrivalOneEnvelope() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertEquals(1, occurrences(src, "composite.beginFrame("),
                    screen + ": exactly one arrival stamp per frame");
            assertEquals(1, occurrences(src, "composite.consumeArrivalEvent()"),
                    screen + ": exactly one arrival-event consumer per frame");
            assertEquals(1, occurrences(src, "composite.drawArrivingFrame("),
                    screen + ": exactly one arriving-frame envelope per frame");
            for (String dead : List.of("PageTransitionDirector", "NavFrame", "suppressHeader",
                    "composite.end(", "composite.drawRootFrame(", "composite.begin(",
                    "navSlideProgress", "navZoomPhase", "rootZoomExitAlpha", "pageNavDx",
                    "startPageNav", "finishPageNav", "applyNavScale", "navLayerSkippable")) {
                assertFalse(src.contains(dead),
                        screen + ": the deleted choreography left \"" + dead + "\" behind");
            }
        }
    }

    /**
     * Bug regression ("设置页返回图标不消失" and "返回箭头中断帧消失"): the
     * arriving header draws inside the arriving page's envelope, from the
     * current page's own state — never as a fixed layer above a transition.
     */
    @Test
    void theHeaderBelongsToTheArrivingPage() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            String header = body(src, "public void drawArrivingHeader(Canvas canvas, UiLayout layout");
            assertTrue(header.contains("ShellHeader.render("),
                    screen + ": the chat pages' header (back arrow, online status, filter) draws "
                            + "through ShellHeader inside the envelope");
            assertTrue(header.contains("headerChrome.backButton()"),
                    screen + ": the back button must come from the current page's chrome");
            assertTrue(header.contains("headerChrome.drawPushedHeader("),
                    screen + ": the settings/profile header draws through the pushed-header path");
            assertFalse(header.contains("activePrivateTarget()"),
                    screen + ": the header must read the arriving page's own target, not the stack "
                            + "top's — a stale subject would paint the wrong online status");
        }
    }

    /** The arriving body dispatches the same three page kinds the settled path had. */
    @Test
    void theArrivingBodyDispatchesEveryPageKind() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            String body = body(src, "public void drawArrivingBody(Canvas canvas, UiLayout layout");
            assertTrue(body.contains("drawSettingsSection("), screen + ": settings sections");
            assertTrue(body.contains("drawProfileDetail("), screen + ": profile details");
            assertTrue(body.contains("drawChatPageBody("), screen + ": chat pages");
        }
    }

    /**
     * The overlays (context menu, emoji panel, quick phrases, image preview) draw
     * OUTSIDE the envelope, after it — they belong to the screen, not to the
     * entering page, and must not ride its transform or its fade.
     */
    @Test
    void theScreenOverlaysDrawOutsideTheEnvelope() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            int envelope = src.indexOf("composite.drawArrivingFrame(");
            assertTrue(envelope >= 0, screen + ": the arriving-frame envelope is missing");
            // lastIndexOf: the context menu also draws on the root branch, before
            // the envelope; the pushed-page occurrence is the one that must follow.
            int emoji = src.lastIndexOf("emojiPanel.render(canvas, layout, vmx, vmy, frameDt)");
            int context = src.lastIndexOf("contextMenu.draw(canvas, vmx, vmy);");
            int preview = src.lastIndexOf("imagePreview.render(canvas, layout.rect(), vmx, vmy)");
            assertTrue(emoji > envelope && context > envelope && preview > envelope,
                    screen + ": the overlays must draw after the arriving-frame envelope");
        }
    }

    /**
     * The input gates keep their narrowed form: the entrance overlay is
     * draw-only, so nothing consults a page-nav clock (there is none left).
     */
    @Test
    void noInputPathConsultsANavClock() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertFalse(src.contains("pageNavActive"), screen);
            assertFalse(src.contains("rootTransitionActive"), screen);
            assertFalse(src.contains("chipTransitionActive"),
                    screen + ": the chip crossfade is decorative (unit E2 completed that)");
        }
    }
}
