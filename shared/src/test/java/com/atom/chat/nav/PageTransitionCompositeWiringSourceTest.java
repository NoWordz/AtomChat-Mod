package com.atom.chat.nav;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guard for the arrival composite and its screen wiring (the
 * one-stamp rewrite of the old transition-frame compositor guard).
 *
 * <p><b>What changed, and why the old guard is gone.</b> The composite used to
 * composite two page layers per frame over a director state machine — its guard
 * asserted that two-layer flow, the nine-step port and the
 * {@code suppressHeader} field. All of that is deleted: the composite is now the
 * <em>arrival stamp</em> (one {@code UiStagger.Entrance}, armed by a page-key
 * change) plus the <em>arriving page's envelope</em>, and the screen keeps no
 * transition state at all. The failure shapes this guard still exists for:
 * a second stamp or transition state growing back on the screen, the envelope
 * leaking its style read to a second resolver, or the composite keeping a
 * leaving-page concept that must not exist.</p>
 *
 * <p>Same standard as its siblings: the three screen copies differ only in
 * Minecraft API spellings, so one set of assertions runs against all three.</p>
 */
class PageTransitionCompositeWiringSourceTest {

    private static final String COMPOSITE =
            "shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java";

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

    // --------------------------------------------------- the composite itself

    /**
     * The composite owns exactly one piece of transition state — the arrival
     * stamp — and the overlay reads ride it. No director type, no leaving page,
     * no second clock anywhere in the class.
     */
    @Test
    void theCompositeIsTheStampAndTheEnvelope() throws IOException {
        String src = source(COMPOSITE);
        assertEquals(1, occurrences(src, "private final UiStagger.Entrance entrance"),
                "exactly one arrival stamp — the only transition state there is");
        assertEquals(1, occurrences(src, "AtomChatConfig.get().pageNavStyle"),
                "the style is read from the config exactly once — no second resolver");
        for (String face : List.of("public void beginFrame(long nowMs)",
                "public boolean consumeArrivalEvent()",
                "public void drawArrivingFrame(", "public boolean overlaySettled(long nowMs)",
                "public float overlayAlpha(long nowMs)", "public float overlaySlideDx(long nowMs)",
                "public float overlayZoomScale(long nowMs)", "public AtomChatConfig.PageNavStyle style()")) {
            assertTrue(src.contains(face), "the composite lost " + face);
        }
        for (String dead : List.of("PageTransitionDirector", "NavFrame", "suppressHeader",
                "viewFor", "drawChatPageBody", "pageNavFrom", "navMovingLayer")) {
            assertFalse(src.contains(dead),
                    "the old two-page composite left \"" + dead + "\" behind");
        }
    }

    /**
     * The envelope skips outright when the stamp settled (or when the arriving
     * surface staggers its own rows), wraps only the BODY in the layer when it
     * plays, and closes every save point it opens. The header draws after the
     * envelope, untransformed: it is chrome and must not move with the page.
     */
    @Test
    void theEnvelopeWrapsTheBodyAndLeavesTheHeaderFixed() throws IOException {
        String src = source(COMPOSITE);
        String envelope = body(src, "public void drawArrivingFrame(Canvas canvas, UiLayout layout");
        assertTrue(envelope.contains("if (!overlaySettled(nowMs) && !host.arrivalStaggersSurfaces())"),
                "a settled stamp, or a self-staggering surface, must skip the envelope outright");
        assertTrue(envelope.indexOf("drawArrivingBody(") < envelope.indexOf("drawArrivingHeader("),
                "the header draws after the body");
        // The header must be OUTSIDE the layer: the restore/close pair must all
        // precede it, or the header would fade and drift with the page again.
        int headerAt = envelope.indexOf("drawArrivingHeader(");
        int lastRestore = envelope.lastIndexOf("canvas.restore();", headerAt);
        int layerClose = envelope.lastIndexOf("layer.close();", headerAt);
        assertTrue(layerClose > 0 && lastRestore > layerClose,
                "the header must draw after the layer is restored (it never moves with the page)");
        assertTrue(envelope.contains("setAlphaf(alpha)"),
                "the envelope fades through the layer's alpha");
    }

    // ------------------------------------------------------------ the screens

    /**
     * The screen keeps none of the composite's state: no stamp, no entrance, no
     * transition floats of its own. It reaches the stamp only through the
     * composite's reads.
     */
    @Test
    void theScreenKeepsNoTransitionStateOfItsOwn() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            for (String dead : List.of("UiStagger.Entrance entrance", "enteredAtMs",
                    "navFastProgress", "navEnterAlpha", "navExitAlpha", "rootZoomPhase",
                    "rootTabFrom", "rootTabTo", "pageNavZoom", "pageNavFast")) {
                assertFalse(src.contains(dead),
                        screen + ": transition state \"" + dead + "\" grew back on the screen");
            }
        }
    }

    /**
     * One composite, built once, with exactly the four-method port. The port is
     * what makes the arriving frame assertable offline, so a port that lost a
     * method (or grew a second implementation) is a wiring change.
     */
    @Test
    void theCompositeIsBuiltOnceWithTheFourMethodPort() throws IOException {
        Map<String, String> portWork = new LinkedHashMap<>();
        portWork.put("topNav", "navStack.topNav()");
        portWork.put("drawArrivingBody", "drawChatPageBody(canvas, layout, mouseX, mouseY, chatPageViewFor(topNav()))");
        portWork.put("drawArrivingHeader", "headerChrome.backButton()");
        portWork.put("arrivalStaggersSurfaces", "topPage() == AppPage.SETTINGS_SECTION");
        for (String screen : SCREENS) {
            String src = source(screen);
            assertEquals(1, occurrences(src, "new PageTransitionComposite("),
                    screen + ": expected exactly one composite construction");
            assertEquals(1, occurrences(src, "new PageTransitionComposite.Host()"),
                    screen + ": expected exactly one port implementation");
            for (Map.Entry<String, String> work : portWork.entrySet()) {
                String method = body(src, "public " + methodSignature(work.getKey()));
                assertTrue(method.contains(work.getValue()),
                        screen + ": the port method " + work.getKey() + "() no longer performs "
                                + work.getValue() + "\n" + method);
            }
        }
    }

    private static String methodSignature(String name) {
        return switch (name) {
            case "topNav" -> "NavPage topNav()";
            case "drawArrivingBody" -> "void drawArrivingBody(Canvas canvas, UiLayout layout";
            case "drawArrivingHeader" -> "void drawArrivingHeader(Canvas canvas, UiLayout layout";
            case "arrivalStaggersSurfaces" -> "boolean arrivalStaggersSurfaces()";
            default -> throw new AssertionError(name);
        };
    }
}
