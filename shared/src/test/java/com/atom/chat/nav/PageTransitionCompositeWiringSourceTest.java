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
 * Source-level guard for the screen's wiring to the transition composite (D24b-2a).
 *
 * <p><b>Why a source probe.</b> {@code PageTransitionCompositeTest} composites real
 * frames, but it builds the composite itself — it cannot see whether the screen
 * ever <em>reaches</em> it, nor whether the nine ports it hands over still do the
 * work they stand for. Nothing offline can: {@code AtomChatScreen} needs the
 * Minecraft client to load, and the golden scenes never composite a transition.
 * Block D23a shipped exactly that class of defect (a second chain beside the
 * first, an empty router, every event falling through to {@code super}) with the
 * whole battery green.
 *
 * <p>So the wiring is asserted where it is written, on all three copies: the
 * composite's state and its per-frame handles have left the screen entirely, the
 * composite is built exactly once from the one port plus the shared collaborators,
 * every port step still forwards to the screen method it stands for, and the field
 * is declared between the things it is built from and the methods it calls — the
 * forward-reference lesson of D21a/D22/D23a, asserted rather than remembered.
 */
class PageTransitionCompositeWiringSourceTest {

    /** The three screen sources, repo-root relative, in a fixed order. */
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

    /** The composite itself, where the per-frame handles now live. */
    private static final String COMPOSITE =
            "shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java";

    private static final String STATEMENT = "private final PageTransitionComposite composite = ";

    /**
     * The state that left the screen in this block: the moving layer's paint (which
     * belongs to the frame the composite owns), the two per-frame flags, the local
     * clock flag, the E group's record and the input-height helper that moved into
     * the class.
     */
    private static final List<String> MOVED_STATE = List.of(
            "navMovingLayer", "navMovingSlide", "navMovingHidden", "boolean navRunning",
            "private record ChatPageView(", "private float inputExtraFor(",
            "private ChatPageView chatPageViewFor(");

    /** Where each of those lives now. */
    private static final String[] MOVED_STATE_HOME = {
            "Paint navMovingLayer = null;", "boolean navMovingSlide = false;",
            "boolean navMovingHidden = zoom && !movingVisible;",
            // HEAD's `boolean navRunning = director.pageNavActive();` local became the
            // frame's `running` field: the composite asks the director directly.
            "director.pageNavActive()",
            "public record ChatPageView(", "private float inputExtraFor(", "ChatPageView viewFor(",
    };

    /**
     * The nine port steps and the screen method each one must still reach. A port
     * that is declared, wired and inert passes every value assertion there is
     * (D23a), so each body is checked for its one forward.
     */
    private static final Map<String, String> PORT_WORK = new LinkedHashMap<>();

    static {
        PORT_WORK.put("topPage", "return AtomChatScreen.this.topPage();");
        PORT_WORK.put("suppressHeader", "suppressHeader = value;");
        PORT_WORK.put("viewFor", "return chatPageViewFor(page);");
        PORT_WORK.put("drawChatPageBody", "AtomChatScreen.this.drawChatPageBody(canvas, layout, mouseX, mouseY, view);");
        PORT_WORK.put("drawRootScreen", "AtomChatScreen.this.drawRootScreen(canvas, mouseX, mouseY, rootPage);");
        PORT_WORK.put("drawBezel", "AtomChatScreen.this.drawBezel(canvas, layout);");
        PORT_WORK.put("rootLayout", "return AtomChatScreen.this.rootLayout();");
        PORT_WORK.put("detailLayout", "return AtomChatScreen.this.detailLayout();");
        PORT_WORK.put("headerActionFor", "return AtomChatScreen.this.headerActionFor(page);");
    }

    /**
     * What the screen must ask the composite for, in this block's own words. These
     * are the facts that used to be asserted on the screen's nav branch; they moved
     * with the code rather than being dropped.
     */
    private static final List<String> SHELL_CALLS = List.of(
            "composite.drawRootFrame(canvas, layout, mouseX, mouseY, vmx, vmy)",
            "PageTransitionComposite.NavFrame navFrame = composite.begin(",
            "if (navFrame.finished()) {",
            "if (navFrame.running()) {",
            "composite.end(canvas, layout, navFrame)",
            "if (!navFrame.running() && topPage() == AppPage.SETTINGS_SECTION) {",
            "if (!navFrame.running() && topPage() == AppPage.PROFILE_DETAIL) {",
            "if (!navFrame.movingHidden()) {");

    /**
     * The one-fact-one-holder contract of the composite's own frame handles, read
     * at their new home: the invisible side opens no layer, and the sliding side
     * says which save the tail must pop.
     */
    private static final Map<String, List<String>> COMPOSITE_WORK = new LinkedHashMap<>();

    static {
        COMPOSITE_WORK.put("private NavFrame rootToDetail(", List.of(
                "boolean movingVisible = !zoom || movingAlpha > 0.001F;",
                "boolean navMovingHidden = zoom && !movingVisible;",
                "navMovingSlide = true;"));
        COMPOSITE_WORK.put("public void end(Canvas canvas, UiLayout layout, NavFrame frame) {", List.of(
                "if (frame.movingLayer() != null) {", "} else if (frame.movingSlide()) {",
                "host.suppressHeader(false);"));
        COMPOSITE_WORK.put("private NavFrame detailToDetail(", List.of(
                "boolean popping = director.pageNavPopPending();",
                "float fromDx = popping ? slideDx : -travel * progress;",
                "float toDx = popping ? -travel * (1.0F - progress) : slideDx;",
                "host.suppressHeader(true);", "host.suppressHeader(false);"));
    }

    // ---------------------------------------------------------------- helpers

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
        return Files.readString(repoRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    /** A member body: declaration up to the first 4-space closing brace. */
    private static String body(String source, String declaration) {
        int from = source.indexOf(declaration);
        assertTrue(from >= 0, "declaration not found: " + declaration);
        int to = source.indexOf("\n    }", from);
        assertTrue(to > from, "closing brace not found for " + declaration);
        return source.substring(from, to);
    }

    /**
     * The file's code with every comment removed. The bucket fact is about code,
     * not prose: the class javadoc has to be allowed to say which package it does
     * not name.
     */
    private static String code(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    /** A declaration statement, to the first ';' outside every ( and {. */
    private static String statement(String source, String anchor) {        int from = source.indexOf(anchor);
        assertTrue(from >= 0, "declaration not found: " + anchor);
        int depth = 0;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (c == ';' && depth == 0) {
                return source.substring(from, i + 1);
            }
        }
        throw new AssertionError("statement never terminated: " + anchor);
    }

    /** One {@code @Override} method of a port: name -> its text. */
    private static Map<String, String> portMethods(String port) {
        Map<String, String> found = new LinkedHashMap<>();
        String[] parts = port.split("@Override");
        for (int i = 1; i < parts.length; i++) {
            int open = parts[i].indexOf('(');
            assertTrue(open > 0, "an @Override in the port has no parameter list");
            int nameStart = parts[i].lastIndexOf(' ', open - 1) + 1;
            found.put(parts[i].substring(nameStart, open).trim(), parts[i]);
        }
        return found;
    }

    // ------------------------------------------------------------ assertions

    /**
     * The screen keeps none of the composite's state. One holder is the point of the
     * extraction: a leftover flag or a second record is how two views of the same
     * frame start to diverge — and the layer handle in particular is a native
     * resource whose owner must be unambiguous.
     */
    @Test
    void theScreenKeepsNoneOfTheCompositeState() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            for (String moved : MOVED_STATE) {
                assertEquals(0, occurrences(src, moved),
                        screen + ": '" + moved + "' is still in the screen. That state belongs to"
                                + " PageTransitionComposite now, and a second holder is the defect"
                                + " this guard exists for.");
            }
            for (String needed : SHELL_CALLS) {
                assertTrue(src.contains(needed),
                        screen + ": the screen must reach the composite with " + needed);
            }
        }
    }

    /** And it really is at the new home, not deleted. */
    @Test
    void theCompositeOwnsWhatLeftTheScreen() throws IOException {
        String composite = source(COMPOSITE);
        for (String home : MOVED_STATE_HOME) {
            assertTrue(composite.contains(home),
                    "the composite must own '" + home + "' — the state moved, it was not dropped");
        }
        for (Map.Entry<String, List<String>> entry : COMPOSITE_WORK.entrySet()) {
            String body = body(composite, entry.getKey());
            for (String fact : entry.getValue()) {
                assertTrue(body.contains(fact),
                        entry.getKey() + " no longer performs '" + fact + "'.\n" + body);
            }
        }
        // The screen-owned pieces deliberately stayed behind, and the composite
        // reaches them only through the port.
        assertFalse(code(composite).contains("net.minecraft"),
                "the composite is a shared single source: no Minecraft name may appear in its code");
        assertTrue(composite.contains("void drawChatPageBody(Canvas canvas, UiLayout layout"),
                "the chat page body stays on the screen by ruling and is a port");
    }

    /**
     * The composite is built exactly once, from the one port and the shared
     * collaborators, and every port step still reaches the screen method it stands
     * for. This is the D23a lesson at the port's new home.
     */
    @Test
    void theCompositeIsBuiltOnceAndItsPortStillDoesItsWork() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertEquals(1, occurrences(src, "new PageTransitionComposite("),
                    screen + ": expected exactly one construction of the composite; a second one"
                            + " is a second holder of the frame");
            assertEquals(1, occurrences(src, "new PageTransitionComposite.Host() {"),
                    screen + ": expected exactly one port implementation for the composite");
            // (No "no new ContextMenuController<" assertion here: the screen builds its
            // own menu controller for its own panels — the edited-menu anchor lives on
            // the screen — and D24b-2a hands THAT instance to the composite. The
            // handed-over list below is the fact; a blanket ban would have failed on
            // code this block never touched.)

            String port = statement(src, STATEMENT);
            Map<String, String> methods = portMethods(port);
            assertEquals(PORT_WORK.size(), methods.size(),
                    screen + ": the port must carry exactly the nine frozen steps, got "
                            + methods.keySet());
            for (Map.Entry<String, String> work : PORT_WORK.entrySet()) {
                String body = methods.get(work.getKey());
                assertTrue(body != null && body.contains(work.getValue()),
                        screen + ": the port step " + work.getKey() + "() no longer performs '"
                                + work.getValue() + "' — the composite would run and the screen"
                                + " would do nothing.\n" + body);
            }
            // The construction hands over the shared collaborators, not fresh ones:
            // a second composer or header chrome would be a second source of truth.
            for (String handed : List.of("director, composer, headerChrome, primitives, contextMenu,"
                    + " contextActions,", "profileWiring, settingsDetailPage);")) {
                assertTrue(port.contains(handed),
                        screen + ": the construction must be handed " + handed);
            }
        }
    }

    /**
     * Field declaration order: the composite is built from the director and the
     * other shared collaborators, and its port forwards to screen methods declared
     * further down — so it must come after the things it is built from and before
     * the methods it calls. A field initialiser that runs before its argument does
     * is a null at construction time; the order is asserted rather than remembered.
     */
    @Test
    void theCompositeIsDeclaredAfterItsCollaboratorsAndBeforeItsCallees() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            int director = src.indexOf("private final PageTransitionDirector director = new PageTransitionDirector(");
            int composite = src.indexOf(STATEMENT);
            int settingsDetail = src.indexOf("private final SettingsDetailPage settingsDetailPage = ");
            int profileWiring = src.indexOf("private final ProfilePageWiring profileWiring = ");
            int chatPageViewFor = src.indexOf("private PageTransitionComposite.ChatPageView chatPageViewFor(NavPage page)");
            assertTrue(director >= 0, screen + ": the director declaration was not found");
            assertTrue(composite > director,
                    screen + ": the composite takes the director as an argument, so it must be"
                            + " declared after it");
            assertTrue(settingsDetail > 0 && settingsDetail < composite,
                    screen + ": the composite takes the settings detail page as an argument, so it"
                            + " must be declared after it (Java rejects the forward reference)");
            assertTrue(profileWiring > 0 && profileWiring < composite,
                    screen + ": and so must the profile wiring");
            assertTrue(chatPageViewFor > composite,
                    screen + ": the port forwards to chatPageViewFor, so that method must be"
                            + " declared after the field that calls it");
        }
    }

    /**
     * The per-frame handle is a record the screen must not reimplement: if the
     * screen grew its own moving-layer bookkeeping again, the two answers could
     * disagree about which save to pop — the one thing a leaked save needs.
     */
    @Test
    void theFrameHandleIsTheCompositesRecordNotTheScreens() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertEquals(0, occurrences(src, "Paint navMovingLayer"),
                    screen + ": the moving layer's paint is the composite's frame handle now");
            assertEquals(0, occurrences(src, "boolean navMovingSlide"),
                    screen + ": and so is the slide flag");
            assertTrue(src.contains("PageTransitionComposite.NavFrame navFrame = composite.begin("),
                    screen + ": the frame must be the composite's own record");
            assertTrue(src.contains("composite.end(canvas, layout, navFrame)"),
                    screen + ": and the tail must be handed the same record back");
        }
    }
}
