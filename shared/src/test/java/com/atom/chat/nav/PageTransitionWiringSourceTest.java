package com.atom.chat.nav;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guard for the screen's transition wiring (D24b-1).
 *
 * <p><b>Why a source probe.</b> {@code PageTransitionDirectorTest} covers the
 * two clocks by value, but it cannot see whether the screen ever <em>reaches</em>
 * them — or whether the port it hands over does the domain work. Nothing offline
 * can: {@code AtomChatScreen} needs the Minecraft client to load, and the golden
 * scenes never composite a transition. Block D23a shipped exactly that class of
 * defect (a second chain beside the first, an empty router, every event falling
 * through to {@code super}) and the whole battery stayed green.
 *
 * <p>So the wiring is asserted where it is written: the screen keeps no
 * transition state of its own, every read point in its draw and input code goes
 * through the director, the director is built once from the bar's animator and
 * the port, and — the part that carries the D24a lesson one level down — the
 * steps that moved into the director still do their work at their new home. A
 * stub port, a second holder, an unprefixed read or a director method that
 * stopped writing its fields is a failure here on all three platforms.
 */
class PageTransitionWiringSourceTest {

    /** The three screen sources, repo-root relative, in a fixed order. */
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

    /** Where both clocks now live -- asserted to still do what the port forwards to it. */
    private static final String DIRECTOR =
            "shared/src/main/java/com/atom/chat/nav/PageTransitionDirector.java";

    private static final String DIRECTOR_DECLARATION =
            "private final PageTransitionDirector director = new PageTransitionDirector(";

    private static final String HOST_DECLARATION = "new PageTransitionDirector.Host() {";

    /**
     * The whole field declaration, used to scan out the port statement: the
     * anonymous class has to be scanned from the *field*, because from its own
     * {@code new ...(} the parentheses of the constructor call never balance.
     */
    private static final String DIRECTOR_STATEMENT = "private final PageTransitionDirector director = ";

    /** Every state member that must have left the screen. */
    private static final List<String> MOVED_STATE = List.of(
            "navSlideProgress", "pageNavFrom", "pageNavTo", "pageNavPopPending", "pageNavZoom",
            "navZoomPhase", "navExitAlpha", "navEnterAlpha",
            "rootTabZoom", "rootZoomPhase", "rootZoomExitAlpha", "rootZoomEnterAlpha",
            "rootTabProgress", "rootTabFrom", "rootTabTo",
            "NAV_ZOOM_EXIT", "NAV_ZOOM_ENTER", "ROOT_ZOOM_EXIT", "ROOT_ZOOM_ENTER", "rootTabAnim");

    /**
     * Calls the screen must still make through the director (never unprefixed).
     *
     * <p>This list used to carry every read point in the transition code. Block
     * D24b-2a moved the composite out of the screen, so the reads it owned went
     * with it — and they were moved to {@link #COMPOSITE_READS} rather than
     * dropped: the same fact, asserted at the code's new home.
     */
    private static final List<String> SHELL_READS = List.of(
            "director.stepRootNav(", "director.rootIndex(", "director.rootPageForIndex(",
            "director.rootZoomExiting(", "director.pageNavFrom(", "director.pageNavTo(",
            "director.rootTabZoom(", "director.rootTabFrom(", "director.rootTabTo(",
            "director.rootZoomExitAlpha(", "director.rootZoomEnterAlpha(", "director.rootTabProgress(",
            "director.rootTransitionActive(", "director.clearRootTransition(",
            "director.startPageNav(", "director.abandonPageNav(", "director.pinTabCapsule(",
            "director.markTabSpan(", "director.applyTabMotion(", "director.resetTabCapsule(",
            "director.navStyleIsZoom(");

    /**
     * The read points that left the screen with block D24b-2a: the page-nav clock
     * and both zoom channels are consumed by {@code PageTransitionComposite} now,
     * before any page is drawn. Asserted on the composite's source, because the
     * screen no longer contains them at all.
     */
    private static final List<String> COMPOSITE_READS = List.of(
            "director.pageNavActive(", "director.stepZoomNav(", "director.stepSlideNav(",
            "director.finishPageNav(", "director.pageNavDx(", "director.zoomExiting(",
            "director.navSlideProgress(", "director.pageNavFrom(", "director.pageNavTo(",
            "director.pageNavPopPending(", "director.pageNavZoom(", "director.navExitAlpha(",
            "director.navEnterAlpha(", "PageTransitionDirector.navLayerSkippable(",
            "PageTransitionDirector.applyNavScale(");

    /** The composite the transition reads moved into. */
    private static final String COMPOSITE =
            "shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java";

    /** The director's own port: three steps, each of which must still do its work. */
    private static final Map<String, String> PORT_WORK = new LinkedHashMap<>();

    static {
        PORT_WORK.put("persistCurrentDraft", "saveCurrentDraft()");
        PORT_WORK.put("captureReplySlot", "syncReplySlot()");
        PORT_WORK.put("applyPendingPop", "popNow()");
    }

    /**
     * What each director member must still do. These are the facts that used to
     * be asserted on the screen's port before block 24b-1 moved the work; they
     * are asserted here, unchanged, at the code's new home.
     */
    private static final Map<String, List<String>> DIRECTOR_WORK = new LinkedHashMap<>();

    static {
        DIRECTOR_WORK.put("public void abandonPageNav() {", List.of(
                "pageNavFrom = null", "pageNavTo = null",
                "pageNavPopPending = false", "navSlideProgress = 0.0F"));
        DIRECTOR_WORK.put("public void pinTabCapsule(AppPage from) {", List.of(
                "capsule.setValue(rootIndex(from))"));
        DIRECTOR_WORK.put("public void markTabSpan(AppPage from, AppPage to) {", List.of(
                "rootTabFrom = rootIndex(from)", "rootTabTo = rootIndex(to)"));
        DIRECTOR_WORK.put("public void applyTabMotion(boolean zoom) {", List.of(
                "rootTabZoom = zoom", "rootZoomPhase = ROOT_ZOOM_EXIT",
                "rootZoomExitAlpha = 1.0F", "rootZoomEnterAlpha = 0.0F", "rootTabProgress = 0.0F"));
        DIRECTOR_WORK.put("public void resetTabCapsule() {", List.of(
                "capsule.setValue(rootTabFrom)"));
        DIRECTOR_WORK.put("public boolean navStyleIsZoom() {", List.of("pageNavStyle()"));
        DIRECTOR_WORK.put("public void finishPageNav() {", List.of(
                "if (pageNavPopPending) {", "host.applyPendingPop();",
                "pageNavFrom = null", "pageNavTo = null",
                "pageNavPopPending = false", "navSlideProgress = 0.0F"));
        DIRECTOR_WORK.put("public void startPageNav(NavPage from, NavPage to, boolean popPending) {", List.of(
                "host.persistCurrentDraft();", "host.captureReplySlot();",
                "pageNavFrom = from;", "pageNavTo = to;", "pageNavPopPending = popPending;",
                "pageNavZoom = style == AtomChatConfig.PageNavStyle.ZOOM;"));
        DIRECTOR_WORK.put("public void stepRootNav(float dtMs) {", List.of(
                "capsule.setValue(rootTabFrom + (rootTabTo - rootTabFrom) * rootTabProgress)",
                "capsule.setValue(UiMotion.expApproach(capsule.getValue(), rootTabTo, dtMs, tau))",
                "if (!rootTransitionActive()) {", "clearRootTransition();"));
    }

    /** The two state fields the tab bar owns stay, and are read where they always were. */
    private static final List<String> STAYING_STATE = List.of("bottomTabBar", "rootMouseX", "rootMouseY");

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

    /** A member body: declaration up to the first 4-space closing brace. */
    private static String body(String source, String declaration) {
        int from = source.indexOf(declaration);
        assertTrue(from >= 0, "declaration not found: " + declaration);
        int to = source.indexOf("\n    }", from);
        assertTrue(to > from, "closing brace not found for " + declaration);
        return source.substring(from, to);
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

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    /**
     * Uses of {@code name} that are neither {@code director.name(...)} nor a
     * method of that name -- i.e. a surviving field read or write of state that
     * is supposed to live in the director now.
     */
    private static int unprefixedUses(String source, String name) {
        Matcher m = Pattern.compile("(?<![\\w.])" + Pattern.quote(name) + "\\b(?!\\s*\\()").matcher(source);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------ assertions

    /**
     * The screen keeps no transition state. One holder is the point of the
     * extraction: a leftover field or phase constant is how two views of the same
     * clock start to diverge, and a second capsule animator would let the pill
     * and its pages drift apart mid-switch.
     */
    @Test
    void theScreenKeepsNoTransitionStateOfItsOwn() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            for (String name : MOVED_STATE) {
                assertEquals(0, unprefixedUses(src, name),
                        screen + ": " + name + " is still used unprefixed. Every read of a transition"
                                + " channel must go through the director; a second holder is the defect"
                                + " this guard exists for.");
            }
            assertEquals(1, occurrences(src, DIRECTOR_DECLARATION),
                    screen + ": expected exactly one director field declaration");
            // Both needles, because a second holder can be built either way: the
            // anonymous port (`new PageTransitionDirector.Host()`) or a second
            // construction (`new PageTransitionDirector(`). Counting one of them
            // let a second director through once -- see the block report.
            assertEquals(1, occurrences(src, "new PageTransitionDirector."),
                    screen + ": expected exactly one director port implementation");
            assertEquals(1, occurrences(src, "new PageTransitionDirector("),
                    screen + ": expected exactly one construction of the director; a second one"
                            + " is a second holder of the same clocks");
            for (String name : STAYING_STATE) {
                assertTrue(src.contains(name),
                        screen + ": " + name + " belongs to the screen (its consumers are the root"
                                + " draw/input groups) and must not have moved");
            }
        }
    }

    /**
     * Every read point reaches the director: the ones the screen still owns in the
     * screen, the ones block D24b-2a moved into the composite in the composite.
     */
    @Test
    void everyReadPointGoesThroughTheDirector() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            for (String call : SHELL_READS) {
                assertTrue(src.contains(call),
                        screen + ": the screen must reach the director with " + call + "...");
            }
            for (String name : List.of("pageNavActive", "stepZoomNav", "stepSlideNav", "finishPageNav",
                    "pageNavDx", "stepRootNav", "rootTransitionActive", "clearRootTransition")) {
                Pattern bare = Pattern.compile("(?<![\\w.])" + name + "\\(");
                assertFalse(bare.matcher(src).find(),
                        screen + ": " + name + "( is called without the director prefix");
            }
            for (String name : List.of("navLayerSkippable(", "applyNavScale(")) {
                Pattern bare = Pattern.compile("(?<![\\w.])" + Pattern.quote(name));
                assertFalse(bare.matcher(src).find(),
                        screen + ": " + name + " is called without the PageTransitionDirector prefix");
            }
        }
        // The page-nav reads left the screen with the composite (block D24b-2a): the
        // same fact, asserted where the code is now. Dropping them instead of moving
        // them is how a guard goes quiet.
        String composite = source(COMPOSITE);
        for (String call : COMPOSITE_READS) {
            assertTrue(composite.contains(call),
                    COMPOSITE + ": the composite must reach the director with " + call + "...");
        }
        for (String name : List.of("pageNavActive", "stepZoomNav", "stepSlideNav", "finishPageNav",
                "pageNavDx", "pageNavFrom", "pageNavTo", "zoomExiting")) {
            Pattern bare = Pattern.compile("(?<![\\w.])" + name + "\\(");
            assertFalse(bare.matcher(composite).find(),
                    COMPOSITE + ": " + name + "( is called without the director prefix");
        }
        for (String name : List.of("navLayerSkippable(", "applyNavScale(")) {
            Pattern bare = Pattern.compile("(?<![\\w.])" + Pattern.quote(name));
            assertFalse(bare.matcher(composite).find(),
                    COMPOSITE + ": " + name + " is called without the PageTransitionDirector prefix");
        }
    }

    /**
     * One port, built once, with the three steps -- and each of them still
     * reaching the screen method it stands for. This is the D23a lesson at the
     * port's new home: a port that is declared, wired and inert passes every
     * value assertion in the numeric suite.
     */
    @Test
    void thePortIsImplementedOnceAndDoesItsWork() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertEquals(1, occurrences(src, HOST_DECLARATION),
                    screen + ": expected exactly one port implementation for the director");
            int from = src.indexOf(DIRECTOR_STATEMENT);
            assertTrue(from >= 0, screen + ": the director field declaration was not found");
            int depth = 0;
            int to = -1;
            for (int i = from; i < src.length(); i++) {
                char c = src.charAt(i);
                if (c == '(' || c == '{') {
                    depth++;
                } else if (c == ')' || c == '}') {
                    depth--;
                } else if (c == ';' && depth == 0) {
                    to = i + 1;
                    break;
                }
            }
            assertTrue(to > from, screen + ": the director's port statement is never terminated");
            String port = src.substring(from, to);
            Map<String, String> methods = portMethods(port);
            assertEquals(PORT_WORK.size(), methods.size(),
                    screen + ": the port must carry exactly the frozen three steps, got " + methods.keySet());
            for (Map.Entry<String, String> work : PORT_WORK.entrySet()) {
                String body = methods.get(work.getKey());
                assertTrue(body != null && body.contains(work.getValue()),
                        screen + ": the port step " + work.getKey() + "() no longer performs "
                                + work.getValue() + " -- the clocks would run and the screen would do"
                                + " nothing.\n" + body);
            }
        }
    }

    /**
     * And the director still does what the port forwards to it. Every fact here
     * was asserted on the screen's port before block 24b-1; it moved with the
     * code rather than being dropped.
     */
    @Test
    void theDirectorStillDoesTheWork() throws IOException {
        String director = source(DIRECTOR);
        for (Map.Entry<String, List<String>> entry : DIRECTOR_WORK.entrySet()) {
            String body = body(director, entry.getKey());
            for (String fact : entry.getValue()) {
                assertTrue(body.contains(fact),
                        entry.getKey() + " no longer performs '" + fact + "'.\n" + body);
            }
        }
        // The whole state lives here, not just the methods that touch it.
        for (String name : MOVED_STATE) {
            if (name.equals("rootTabAnim")) {
                assertTrue(director.contains("private final Animator capsule;"),
                        "the capsule animator must be held by the director");
                continue;
            }
            assertTrue(director.contains(name), "the director must own " + name);
        }
    }

    /**
     * The page zoom hands over inside one step, so the EXIT test has to be the
     * sequential if that runs first: reordering the two would cost the arriving
     * page a frame, which no value test on a single phase would notice.
     */
    @Test
    void theZoomKeepsItsSequentialHandOver() throws IOException {
        String director = source(DIRECTOR);
        String zoom = body(director, "public void stepZoomNav(float dtMs) {");
        int exit = zoom.indexOf("if (navZoomPhase == NAV_ZOOM_EXIT) {");
        int enter = zoom.indexOf("if (navZoomPhase == NAV_ZOOM_ENTER && navEnterAlpha < 1.0F) {");
        assertTrue(exit >= 0 && enter > exit,
                "stepZoomNav must test EXIT first and ENTER second, as two sequential ifs, so a fast"
                        + " exit hands over within the same step.\n" + zoom);
        assertTrue(zoom.contains("if (navExitAlpha <= 0.02F) {"),
                "the hand-over point is 0.02 and must stay a named constant of the code.\n" + zoom);
        assertTrue(body(director, "public void stepSlideNav(float dtMs) {").contains("Animations.enabled()"),
                "both clocks must zero their tau when decorative motion is off");
    }

    /**
     * Field declaration order: the director is built on the tab bar's animator
     * and the nav port forwards to the director, so the bar must come first and
     * the director before the port (the D21a/D22/D23a forward-reference lesson,
     * asserted rather than remembered).
     */
    @Test
    void theDirectorIsDeclaredBetweenTheBarAndThePort() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            int bar = src.indexOf("private final BottomTabBar bottomTabBar = new BottomTabBar();");
            int director = src.indexOf(DIRECTOR_DECLARATION);
            int port = src.indexOf("private final NavStackController.Host navHost = ");
            assertTrue(bar >= 0, screen + ": the tab bar declaration was not found");
            assertTrue(director > bar,
                    screen + ": the director is built on bottomTabBar.indicatorAnimator(), so it must"
                            + " be declared after the bar");
            assertTrue(port > director,
                    screen + ": the nav port forwards its transition steps to the director, so the"
                            + " director must be declared before it");
            assertTrue(src.contains("bottomTabBar.indicatorAnimator());"),
                    screen + ": the director must be handed the tab bar's own animator, so the capsule"
                            + " and its pages ride one value holder");
        }
    }
}
