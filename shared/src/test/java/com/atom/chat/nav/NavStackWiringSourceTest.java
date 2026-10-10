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
 * Source-level guard for the screen's navigation wiring (D24a).
 *
 * <p><b>Why a source probe.</b> {@link NavStackControllerTest} covers the
 * controller's own cascade with a recording port, but it cannot see whether the
 * screen ever <em>calls</em> the controller — or whether the port it hands over
 * actually does the domain work. Nothing offline can: {@code AtomChatScreen}
 * needs the Minecraft client to load, the golden frames render page objects and
 * never run a nav cascade, and the store flags it writes have no getter. Block
 * D23a shipped exactly that class of defect: a second chain was declared beside
 * the first, the gate's own router stayed empty, the whole input layer fell
 * through to {@code super} — and the build, the unit tests and every golden
 * frame stayed green, because none of them look at the wiring.
 *
 * <p>So the wiring is asserted where it is written: one stack holder, one port,
 * one construction, every door still delegating, and every port method still
 * reaching the domain it stands for. A stub port, a second holder or a door
 * that stopped routing is a failure here on all three platforms.
 *
 * <p>The same reasoning as {@code PageNavStyleSourceTest} and
 * {@code InputWiringSourceTest}: the three screen copies differ only in
 * Minecraft API spellings, so one set of assertions runs against all three and
 * a one-platform fix is a failure.
 */
class NavStackWiringSourceTest {

    /** The three screen sources, repo-root relative, in a fixed order. */
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

    /** The controller itself, so the steps the port cannot observe stay pinned. */
    private static final String CONTROLLER =
            "shared/src/main/java/com/atom/chat/nav/NavStackController.java";

    private static final String HOST_DECLARATION =
            "private final NavStackController.Host navHost = ";

    private static final String CONTROLLER_DECLARATION =
            "private final NavStackController navStack;";

    /** Every door the screen keeps, and the controller call its body must make. */
    private static final Map<String, String> DOORS = new LinkedHashMap<>();

    static {
        DOORS.put("private NavPage topNav()", "navStack.topNav()");
        // The @Override prefix is part of the anchor: the same signature also
        // appears inside this screen's inner port classes, and body() takes the
        // first occurrence.
        DOORS.put("@Override\n    public AppPage topPage()", "navStack.topPage()");
        DOORS.put("private boolean isPrivateReadOnly()", "navStack.isPrivateReadOnly()");
        DOORS.put("private boolean isPrivateReadOnlyFor(NavPage page)", "navStack.isPrivateReadOnlyFor(page)");
        DOORS.put("private String readOnlyHintForNav(NavPage page)", "navStack.readOnlyHintForNav(page)");
        DOORS.put("@Override\n    public PlayerRef activePrivateTarget()", "navStack.activePrivateTarget()");
        DOORS.put("private boolean isWorldChatPage()", "navStack.isWorldChatPage()");
        DOORS.put("private void pushNav(NavPage page)", "navStack.pushNav(page)");
        DOORS.put("@Override\n    public void popPage()", "navStack.popPage()");
        DOORS.put("private void popNow()", "navStack.popNow()");
        DOORS.put("@Override\n    public void switchRoot(AppPage root)", "navStack.switchRoot(root)");
    }

    /** The doors whose bodies stay on the screen (they only resolve their own argument). */
    private static final Map<String, String> ARGUMENT_DOORS = new LinkedHashMap<>();

    static {
        ARGUMENT_DOORS.put("public void pushPage(AppPage page)", "pushNav(NavPage.of(page))");
        ARGUMENT_DOORS.put("public void openWorldChat()", "pushPage(AppPage.WORLD_CHAT)");
        ARGUMENT_DOORS.put("public void openPrivateChat(PlayerRef target)", "pushNav(NavPage.privateChat(target))");
        ARGUMENT_DOORS.put("public void openSettingsSection(SettingsSection section)",
                "pushNav(NavPage.settingsSection(section))");
    }

    /**
     * Every port method and the domain call its body must still make. The
     * failure this catches is the D23a shape one level down: a port that is
     * declared, implemented and never does the work — the cascade would run,
     * every unit test above would still pass (the recorder is the port), and
     * the screen would do nothing.
     */
    private static final Map<String, String> PORT_WORK = new LinkedHashMap<>();

    static {
        PORT_WORK.put("signatureEditLive", "profilePage.isSignatureEditing()");
        PORT_WORK.put("abortSignatureEdit", "profilePage.endSignatureEdit(false)");
        PORT_WORK.put("persistCurrentDraft", "saveCurrentDraft()");
        PORT_WORK.put("persistPrivateDraft", "savePrivateDraft(target, text)");
        PORT_WORK.put("adoptDraft", "loadDraft(target)");
        PORT_WORK.put("adoptWorldDraft", "loadWorldDraft()");
        PORT_WORK.put("composerTextNow", "composer.text()");
        PORT_WORK.put("armReplyFor", "loadReplyFor(page)");
        PORT_WORK.put("clearWorldScroll", "scrollSync.worldScroll().reset()");
        PORT_WORK.put("clearDetailScroll", "scrollSync.detailScroll().reset()");
        PORT_WORK.put("clearRootScroll", "scrollSync.rootScroll().reset()");
        PORT_WORK.put("clearSettingsPageState", "resetSettingsUi()");
        PORT_WORK.put("clearTransientWorldUi", "resetTransientWorldUi()");
        PORT_WORK.put("isPlayerOnline", "contextActions.isOnlinePlayer(target)");
        PORT_WORK.put("dropProfileSubject", "profilePage.resetSubject()");
        PORT_WORK.put("copy", "tr(key)");
        PORT_WORK.put("beginPageNav", "startPageNav(from, to, popPending)");
        PORT_WORK.put("abandonPageNav", "pageNavFrom = null");
        PORT_WORK.put("abandonRootTransition", "clearRootTransition()");
        PORT_WORK.put("rootTransitionLive", "rootTransitionActive()");
        PORT_WORK.put("navStyleIsZoom", "pageNavStyle() == AtomChatConfig.PageNavStyle.ZOOM");
        PORT_WORK.put("pinRootTabCapsule", "rootTabAnim.setValue(rootIndex(from))");
        PORT_WORK.put("markRootTabSpan", "rootTabFrom = rootIndex(from)");
        PORT_WORK.put("applyRootTabMotion", "rootTabZoom = zoom");
        PORT_WORK.put("resetRootTabCapsule", "rootTabAnim.setValue(rootTabFrom)");
        PORT_WORK.put("selectTabNow", "bottomTabBar.setSelectedImmediate(rootIndex(root))");
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

    /** A member body: declaration up to the first 4-space closing brace. */
    private static String body(String source, String declaration) {
        int from = source.indexOf(declaration);
        assertTrue(from >= 0, "declaration not found: " + declaration);
        int to = source.indexOf("\n    }", from);
        assertTrue(to > from, "closing brace not found for " + declaration);
        return source.substring(from, to);
    }

    /**
     * The whole field declaration statement from {@code anchor} to the first
     * {@code ;} outside every {@code (} and {@code &#123;} — so the anonymous
     * port's own semicolons and every override inside it are included. This is
     * what lets "is the port really 26 delegations" be asserted at all.
     */
    private static String statement(String source, String anchor) {
        int from = source.indexOf(anchor);
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
        throw new AssertionError("the declaration is never terminated by ';': " + anchor);
    }

    /** One {@code @Override} method of the port: name -> its text, keyed by method name. */
    private static Map<String, String> portMethods(String portStatement) {
        Map<String, String> found = new LinkedHashMap<>();
        String[] parts = portStatement.split("@Override");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int open = part.indexOf('(');
            assertTrue(open > 0, "an @Override in the port has no parameter list");
            int nameStart = part.lastIndexOf(' ', open - 1) + 1;
            found.put(part.substring(nameStart, open).trim(), part);
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

    // ------------------------------------------------------------ assertions

    /**
     * The screen keeps no stack of its own. One holder is the whole point of the
     * extraction: a second field or a leftover {@code navigation} accessor is
     * how two views of the same stack start to diverge.
     */
    @Test
    void theScreenKeepsNoStackOfItsOwn() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertFalse(src.contains("NavigationStack"),
                    screen + ": the stack type must not be named in the screen — the controller owns"
                            + " the stack. A second holder is the defect this guard exists for.");
            for (String accessor : List.of("navigation.peek(", "navigation.push(", "navigation.pop(",
                    "navigation.size(", "navigation.snapshot(", "navigation.replaceWithRoot(")) {
                assertFalse(src.contains(accessor),
                        screen + ": " + accessor + ") survived in the screen; every stack access must"
                                + " go through the controller.");
            }
            assertEquals(1, occurrences(src, CONTROLLER_DECLARATION),
                    screen + ": expected exactly one navStack field declaration");
        }
    }

    /** Every door still routes into the controller, and does nothing else. */
    @Test
    void everyStackDoorDelegatesToTheController() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            for (Map.Entry<String, String> door : DOORS.entrySet()) {
                String body = body(src, door.getKey());
                assertEquals(1, occurrences(body, door.getValue()),
                        screen + ": " + door.getKey() + " must delegate through exactly one "
                                + door.getValue() + " call.\n" + body);
                long lines = body.lines().count();
                assertTrue(lines <= 5,
                        screen + ": " + door.getKey() + " is no longer a delegation (" + lines
                                + " lines); the cascade belongs in the controller.\n" + body);
            }
            for (Map.Entry<String, String> door : ARGUMENT_DOORS.entrySet()) {
                String body = body(src, door.getKey());
                assertTrue(body.contains(door.getValue()),
                        screen + ": " + door.getKey() + " must keep resolving its own argument and"
                                + " funnel into the one push cascade.\n" + body);
            }
        }
    }

    /**
     * The screen is still the {@code PageHost} and the banner service's
     * {@code NavPort}: those interfaces are how every page and the jump service
     * navigate, so the delegations have to carry the same signatures and the
     * same {@code @Override}s.
     */
    @Test
    void theScreenStillImplementsItsNavigationInterfaces() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertTrue(src.contains("implements PageHost"),
                    screen + ": the screen must stay the PageHost implementation");
            assertTrue(src.contains("NotificationJumpService.NavPort"),
                    screen + ": the screen must stay the banner service's NavPort implementation");
            for (String override : List.of("public void pushPage(AppPage page)",
                    "public void popPage()", "public void switchRoot(AppPage root)",
                    "public AppPage topPage()", "public PlayerRef activePrivateTarget()")) {
                assertTrue(src.contains("@Override\n    " + override),
                        screen + ": " + override + " must stay an override on the screen, so every"
                                + " page that navigates through PageHost keeps working");
            }
            for (String door : List.of("public void openWorldChat()", "public void openPrivateChat(PlayerRef target)",
                    "public void openSettingsSection(SettingsSection section)")) {
                assertTrue(src.contains("    " + door + " {"),
                        screen + ": the screen must keep " + door + " as its own door");
            }
            // The banner service is handed the screen itself as its nav port; that
            // only keeps working while the delegations above exist.
            assertTrue(src.contains("new NotificationJumpService(\n            this,"),
                    screen + ": the banner service must still receive this screen as its NavPort");
        }
    }

    /**
     * One port, declared once, implemented once, with the whole step surface.
     * The port is what makes the cascade assertable, so a port that lost a
     * method (or grew a second implementation) is a wiring change, not a
     * refactor.
     */
    @Test
    void thePortIsImplementedExactlyOnceWithTheWholeSurface() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertEquals(1, occurrences(src, "new NavStackController.Host()"),
                    screen + ": expected exactly one port implementation");
            String port = statement(src, HOST_DECLARATION);
            Map<String, String> methods = portMethods(port);
            assertEquals(PORT_WORK.size(), methods.size(),
                    screen + ": the port must carry exactly the frozen step surface, got "
                            + methods.keySet());
            for (String name : PORT_WORK.keySet()) {
                assertTrue(methods.containsKey(name),
                        screen + ": the port lost " + name + "()");
            }
        }
    }

    /**
     * And every port method still reaches its domain. This is the assertion that
     * would have caught D23a's empty chain: a port that is declared, wired and
     * inert passes every other test in this file.
     */
    @Test
    void everyPortMethodDoesItsDomainWork() throws IOException {
        for (String screen : SCREENS) {
            Map<String, String> methods = portMethods(statement(source(screen), HOST_DECLARATION));
            for (Map.Entry<String, String> work : PORT_WORK.entrySet()) {
                String body = methods.get(work.getKey());
                assertTrue(body != null && body.contains(work.getValue()),
                        screen + ": the port method " + work.getKey() + "() no longer performs "
                                + work.getValue() + " — the cascade would run and do nothing.\n" + body);
            }
        }
    }

    /** The controller is built once, from the port, inside the constructor. */
    @Test
    void theControllerIsBuiltOnceFromThePort() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertEquals(1, occurrences(src, "new NavStackController(navHost,"),
                    screen + ": expected exactly one construction of the controller");
            String ctor = body(src, "public AtomChatScreen(String originalChatText, AtomChatOpenMode mode)");
            assertTrue(ctor.contains("new NavStackController(navHost,"),
                    screen + ": the controller must be built in the constructor, where every field"
                            + " the port reads is already initialised.\n" + ctor);
        }
    }

    /**
     * The steps the port cannot observe keep their positions in the controller's
     * own source: the two active-store writes happen between the stack change
     * and the arriving page's draft load (they are what {@code ChatHudMixin}'s
     * echo suppression reads), and the subject reset happens after the pop.
     */
    @Test
    void theUnobservableStepsKeepTheirPositions() throws IOException {
        String src = source(CONTROLLER);
        String pushNav = body(src, "public void pushNav(NavPage page)");
        int push = pushNav.indexOf("navigation.push(page)");
        int store = pushNav.indexOf("ChatStore.setPublicActive(");
        int adopt = pushNav.indexOf("host.adoptWorldDraft()");
        assertTrue(push >= 0 && store > push && adopt > store,
                "pushNav must push, then point the public store, then load the arriving draft");

        String popNow = body(src, "public void popNow()");
        int save = popNow.indexOf("host.persistPrivateDraft(");
        int pop = popNow.indexOf("navigation.pop()");
        int subject = popNow.indexOf("host.dropProfileSubject()");
        assertTrue(save >= 0 && pop > save && subject > pop,
                "popNow must save the leaving draft, then pop, then drop the subject");
        assertTrue(popNow.indexOf("host.clearTransientWorldUi()") < pop,
                "the transient reset must run while the page is still on the stack");

        String switchRoot = body(src, "public void switchRoot(AppPage root)");
        assertTrue(switchRoot.indexOf("navigation.replaceWithRoot(") < switchRoot.indexOf("host.markRootTabSpan("),
                "the tab switch must land on the stack before the span is recorded");
        assertTrue(switchRoot.indexOf("host.applyRootTabMotion(") < switchRoot.indexOf("host.resetRootTabCapsule()"),
                "the capsule resets last, so it starts from the source cell");
    }
}
