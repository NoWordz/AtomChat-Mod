package com.atom.chat.nav;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guard for the page-nav animation style on all three platforms.
 *
 * <p>Why a source probe and not a behaviour test: the nav clock, the page stack
 * and the layered composite all live on {@code AtomChatScreen}, which cannot be
 * loaded without the Minecraft client (Skija canvas, {@code Screen} superclass,
 * loader APIs). The seam that <em>is</em> loadable is the resolution rule —
 * "which style does this from->to pair use" — and the failure mode this test
 * exists to prevent is exactly a hardcoded exception inside that rule
 * (detail-to-detail used to return SLIDE whatever the config said, which made
 * the setting read as "does nothing" on the public&lt;-&gt;private hop). That rule
 * is a one-line expression, so asserting it at the source is asserting it
 * whole; the drawing side is asserted through the same rule's landmarks
 * ({@code pageNavZoom} / the shared zoom channels) so a pair cannot silently
 * fall back to a private clock again.
 *
 * <p>The three files are byte-identical in the nav regions (they only differ in
 * Minecraft API calls elsewhere), so the same assertions run against all three:
 * a fix in one platform without the twins is a failure here.
 *
 * <p>A second family of guards covers what actually moves on the
 * public&lt;-&gt;private hop. That pair used to send only the message list
 * through the animated layer and then repaint the shared composer outside it,
 * so the messages slid while the input bar sat still. The pair must instead
 * draw the whole chat body (messages, reply bar, composer) inside the layer on
 * both styles, and the fixed chrome — {@code ShellHeader.render} and
 * {@code drawBezel} — must be painted after that layer is restored, outside its
 * transform.
 */
class PageNavStyleSourceTest {

    /** The three screen sources, repo-root relative, in a fixed order. */
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

    /**
     * One slide-offset declaration: {@code float fromDx = popping ? A : B;} —
     * the variable name plus the two arms of the conditional. Whitespace
     * tolerant; the arms are the whole expression, greedily but not across a
     * semicolon.
     */
    private static final Pattern SLIDE_OFFSET = Pattern.compile(
            "float\\s+(fromDx|toDx)\\s*=\\s*popping\\s*\\?\\s*([^;]*?)\\s*:\\s*([^;]*?);");

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

    /** The text from {@code marker} up to (not including) {@code endMarker}. */
    private static String slice(String source, String marker, String endMarker) {
        int from = source.indexOf(marker);
        assertTrue(from >= 0, "marker not found: " + marker);
        int to = source.indexOf(endMarker, from + marker.length());
        assertTrue(to > from, "end marker not found after " + marker + ": " + endMarker);
        return source.substring(from, to);
    }

    /** A member body: from the declaration up to the first 4-space closing brace
     *  (every nested block in these methods closes deeper, so this is exact). */
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

    private static int indexOf(String haystack, String needle) {
        return haystack.indexOf(needle);
    }

    /** The text from {@code from} on, clamped: a -1 search result must not turn
     *  a failure message into a StringIndexOutOfBoundsException. */
    private static String tail(String text, int from) {
        return from < 0 || from > text.length() ? text : text.substring(from);
    }

    /** The last position of any of the needles, or -1 when none is present. */
    private static int lastIndexOfAny(String haystack, String... needles) {
        int last = -1;
        for (String needle : needles) {
            last = Math.max(last, haystack.lastIndexOf(needle));
        }
        return last;
    }

    /** The public&lt;-&gt;private / chat&lt;-&gt;profile detail-to-detail branch. */
    private static String detailToDetailBranch(String source) {
        return slice(source,
                "// Detail-to-detail",
                "// Full-width push/pop: root<->detail transitions.");
    }

    /** Call sites of a method, skipping its own declaration ({@code void name(}). */
    private static List<Integer> callSites(String source, String name) {
        List<Integer> sites = new ArrayList<>();
        for (int i = source.indexOf(name); i >= 0; i = source.indexOf(name, i + 1)) {
            if (!source.substring(Math.max(0, i - 6), i).endsWith("void ")) {
                sites.add(i);
            }
        }
        return sites;
    }

    // ------------------------------------------------------------ assertions

    /**
     * The rule itself: one resolution path, reading the config, with no
     * per-pair exception. This is the assertion that fails on the hardcoded
     * {@code from.isRoot() == to.isRoot() -> SLIDE}.
     */
    @Test
    void resolverReadsTheConfigWithNoPerPairException() throws IOException {
        for (String screen : SCREENS) {
            String resolver = body(source(screen), "AtomChatConfig.PageNavStyle pageNavStyle(");
            assertTrue(resolver.contains("AtomChatConfig.get().pageNavStyle"),
                    screen + ": the resolver must read AtomChatConfig.pageNavStyle");
            assertFalse(resolver.contains("isRoot"),
                    screen + ": the resolver must not special-case page kinds — that is the"
                            + " exception that made detail<->detail ignore the setting.\n" + resolver);
            assertFalse(resolver.contains("PageNavStyle.SLIDE"),
                    screen + ": the resolver must not hardcode any style.\n" + resolver);
        }
    }

    /** The style is resolved at exactly one point, and every switch reads it. */
    @Test
    void styleIsResolvedOncePerNavAndEverySwitchReadsIt() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            String startNav = body(src, "private void startPageNav(");
            assertTrue(startNav.contains("pageNavStyle()"),
                    screen + ": startPageNav must resolve the style through pageNavStyle()");
            assertEquals(1, occurrences(src, "pageNavZoom ="),
                    screen + ": pageNavZoom may only be assigned by the single resolution point");
            assertTrue(startNav.contains("pageNavZoom ="),
                    screen + ": pageNavZoom must be assigned inside startPageNav");
            // Root tab switches never run pushNav/popPage, so they must resolve
            // the style through the same helper rather than reading a second
            // path of their own.
            assertTrue(body(src, "public void switchRoot(AppPage").contains("pageNavStyle()"),
                    screen + ": the root tab switch must resolve the style through pageNavStyle()");
        }
    }

    /** Every entry point funnels into the one animated start helper. */
    @Test
    void everyNavEntryPointSharesTheSameStartHelper() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            String pushNav = body(src, "private void pushNav(NavPage");
            String popPage = body(src, "public void popPage()");
            List<Integer> starts = callSites(src, "startPageNav(");
            assertEquals(3, starts.size(),
                    screen + ": expected exactly the two push call sites plus the pop one, got " + starts);
            int pushAt = indexOf(src, pushNav);
            int popAt = indexOf(src, popPage);
            for (int i : starts) {
                boolean inPush = i >= pushAt && i < pushAt + pushNav.length();
                boolean inPop = i >= popAt && i < popAt + popPage.length();
                assertTrue(inPush || inPop,
                        screen + ": a startPageNav call escaped pushNav/popPage, so it bypasses"
                                + " the single resolution path");
            }
            // The public entry points must delegate, not drive the nav state.
            assertTrue(body(src, "public void openWorldChat()").contains("pushPage(AppPage.WORLD_CHAT)"),
                    screen + ": the banner/root-card world jump must go through pushPage");
            assertTrue(body(src, "public void openPrivateChat(PlayerRef").contains("pushNav("),
                    screen + ": the banner/root-card private jump must go through pushNav");
            assertTrue(body(src, "public void openSettingsSection(").contains("pushNav("),
                    screen + ": the settings push must go through pushNav");
            assertTrue(body(src, "public void pushPage(AppPage").contains("pushNav("),
                    screen + ": the profile/settings push must go through pushNav");
            assertTrue(body(src, "public void openProfileDetail(PlayerRef").contains("pushPage("),
                    screen + ": the profile push must go through pushPage");
        }
    }

    /**
     * detail&lt;-&gt;detail gets the same treatment as root&lt;-&gt;detail: it reads the
     * resolved style and, on ZOOM, rides the shared serial zoom clock instead
     * of a private one. Both the zoom composite and the slide translation must
     * be present, or the setting only changes one of the two styles.
     */
    @Test
    void detailToDetailFollowsTheResolvedStyle() throws IOException {
        for (String screen : SCREENS) {
            String branch = slice(source(screen),
                    "// Detail-to-detail",
                    "// Full-width push/pop: root<->detail transitions.");
            assertTrue(branch.contains("pageNavZoom"),
                    screen + ": the detail<->detail branch must consult the resolved style");
            assertTrue(branch.contains("navZoomPhase") && branch.contains("navExitAlpha")
                            && branch.contains("navEnterAlpha"),
                    screen + ": the detail<->detail zoom must ride the shared serial zoom clock"
                            + " (navZoomPhase / navExitAlpha / navEnterAlpha), not a private one");
            assertTrue(branch.contains("navLayerSkippable") && branch.contains("applyNavScale"),
                    screen + ": the detail<->detail zoom must reuse the nav layer helpers"
                            + " (pure-overdraw skip + panel-centred scale)");
            assertTrue(branch.contains("pageNavDx"),
                    screen + ": the detail<->detail SLIDE must survive the change");
        }
    }

    /**
     * The public&lt;-&gt;private hop moves the whole chat page, on both styles:
     * the animated layer carries the composer with the messages, so nothing
     * repaints the shared input bar outside it. The old shape drew only the
     * message layer and then re-drew the composer fixed, which is why the
     * input bar stayed still while the conversation slid past it.
     */
    @Test
    void worldPrivatePairMovesTheWholeChatBody() throws IOException {
        for (String screen : SCREENS) {
            String branch = detailToDetailBranch(source(screen));
            assertTrue(branch.contains("drawChatPageBody("),
                    screen + ": the world<->private pair must draw the whole chat body"
                            + " (messages + reply bar + composer), not only a message layer.\n" + branch);
            assertEquals(0, occurrences(branch, "drawMessageLayerForNav("),
                    screen + ": the world<->private pair still routes through the lists-only"
                            + " drawMessageLayerForNav, so its composer is left behind while the"
                            + " messages move.\n" + branch);
            assertFalse(branch.contains("layout.inputBar"),
                    screen + ": the shared composer is repainted outside the moving body layer"
                            + " (a fixed input bar drawn next to a sliding page).\n" + branch);
        }
    }

    /**
     * Which side of the slide each offset belongs to, by role and not by
     * position: the page on top of the stack during the nav — the destination on
     * a push, the page being left on a pop — carries the shared
     * {@code pageNavDx} expression, and the other side its mirror.
     *
     * <p>Asserting the presence of the identifier (which the sibling tests do)
     * cannot see a swap: exchanging the two roles slides both pages the wrong
     * way — the composer then leaves the screen edge-first while its own page
     * arrives — and every other assertion here stays green. So the two
     * declarations are parsed and each branch is checked for the role it must
     * hold.
     */
    @Test
    void theSlideOffsetsAreAssignedByRoleNotByPosition() throws IOException {
        for (String screen : SCREENS) {
            String branch = detailToDetailBranch(source(screen));
            Map<String, String[]> roles = slideRoles(branch);
            assertEquals(2, roles.size(),
                    screen + ": expected exactly the fromDx and toDx declarations in the"
                            + " detail<->detail slide, got " + roles.keySet() + "\n" + branch);
            String[] from = roles.get("fromDx");
            String[] to = roles.get("toDx");
            assertNotNull(from, screen + ": no fromDx declaration found in the slide branch");
            assertNotNull(to, screen + ": no toDx declaration found in the slide branch");

            // The page being left must be at the top of the stack during the nav:
            // on a push it slides out to the LEFT (travel*progress is negative in
            // effect — the arriving page holds the shared top-side expression),
            // on a pop it slides out to the RIGHT on the top-side expression.
            assertTrue(from[0].contains("slideDx"),
                    screen + ": on a POP the leaving page must ride the top-side pageNavDx"
                            + " expression, but its popping branch is '" + from[0] + "'\n" + branch);
            assertFalse(from[1].contains("slideDx"),
                    screen + ": on a PUSH the leaving page must take the mirror, not the"
                            + " arriving page's expression; its pushing branch is '"
                            + from[1] + "'\n" + branch);
            assertTrue(to[0].contains("1.0F - progress"),
                    screen + ": on a POP the arriving page must take the mirror offset, but its"
                            + " popping branch is '" + to[0] + "'\n" + branch);
            assertTrue(to[1].contains("slideDx"),
                    screen + ": on a PUSH the arriving page must ride the top-side pageNavDx"
                            + " expression, but its pushing branch is '" + to[1] + "'\n" + branch);

            // ...and the leaving page's mirror really is the travel left over the
            // other way round, so neither side can drift into a shared constant.
            assertTrue(from[1].contains("progress") && !from[1].contains("1.0F - progress"),
                    screen + ": the leaving page's push offset must be proportional to progress"
                            + " (it moves out as the nav advances), was '" + from[1] + "'");
        }
    }

    /**
     * The {@code popping ? … : …} arms of the two slide-offset declarations in a
     * branch, keyed by variable name: {@code {"fromDx": {poppingArm, pushingArm}}}.
     * Whitespace is collapsed, so the assertion is on the tokens, not the
     * formatting; a declaration the regex cannot read is simply absent and the
     * caller's count check fails loudly.
     */
    private static Map<String, String[]> slideRoles(String branch) {
        Map<String, String[]> found = new LinkedHashMap<>();
        Matcher m = SLIDE_OFFSET.matcher(branch);
        while (m.find()) {
            found.put(m.group(1), new String[]{
                    m.group(2).replaceAll("\\s+", " ").trim(),
                    m.group(3).replaceAll("\\s+", " ").trim()});
        }
        return found;
    }

    /**
     * Every side of the composite draws a body. Three page kinds can be
     * composited as a whole page — a chat page (its own view), the profile
     * detail and a settings section — and the last two have no composer, so
     * they are drawn by their own body method. A side that matched neither the
     * view nor a body branch would be painted as nothing, i.e. one page of the
     * transition against an empty panel.
     */
    @Test
    void everyCompositeSideDrawsABody() throws IOException {
        for (String screen : SCREENS) {
            String branch = detailToDetailBranch(source(screen));
            for (String side : List.of("visible", "from", "to")) {
                assertTrue(branch.contains(side + "View != null"),
                        screen + ": the " + side + " side must draw a chat page when it has"
                                + " one.\n" + branch);
                assertTrue(branch.contains(side + "Page.page() == AppPage.PROFILE_DETAIL"),
                        screen + ": the " + side + " side must draw the profile body.\n" + branch);
                assertTrue(branch.contains(side + "Page.page() == AppPage.SETTINGS_SECTION"),
                        screen + ": the " + side + " side must draw the settings body; a"
                                + " settings side otherwise composites as empty space.\n" + branch);
            }
        }
    }

    /**
     * The jump-to-latest pill covers only the page it belongs to. The fade and
     * the bounce are shared screen state stepped once per frame by the page that
     * owns the composer, so the other side of a transition re-draws them — and
     * must nevertheless gate the draw on its own visibility, or the arriving
     * page briefly shows the leaving page's pill.
     *
     * <p>The gate cannot be exercised without a Skija surface, so it is asserted
     * at the source: the guard must consult both {@code advance} (the owner
     * steps the state) and the side's own {@code show}.
     */
    @Test
    void theJumpLatestPillIsOwnerAndVisibilityGated() throws IOException {
        for (String screen : SCREENS) {
            String body = body(source(screen), "private void drawJumpLatest(");
            assertTrue(body.contains("!advance && !show"),
                    screen + ": drawJumpLatest must not draw a non-owner side whose own scroll"
                            + " says there is no pill; the guard reads only the shared animation"
                            + " state.\n" + body);
        }
    }

    /**
     * The header and the bezel are the fixed chrome: they must be painted after
     * the moving body's layer has been restored, so the transform never reaches
     * them. Anchored on the last page-body draw in the pair — if that draw is
     * inside a save/translate/scale, a restore must follow it before
     * {@code ShellHeader.render}, and {@code drawBezel} must close the frame.
     */
    @Test
    void fixedChromeIsPaintedAfterTheBodyLayerRestores() throws IOException {
        for (String screen : SCREENS) {
            String branch = detailToDetailBranch(source(screen));
            int lastBody = lastIndexOfAny(branch,
                    "drawChatPageBody(", "drawMessageLayerForNav(", "drawProfileDetail(",
                    "drawSettingsSection(");
            assertTrue(lastBody >= 0,
                    screen + ": no page-body draw found in the detail<->detail branch");
            int restore = branch.indexOf("canvas.restore()", lastBody);
            assertTrue(restore > lastBody,
                    screen + ": the last page body is drawn with no canvas.restore() after it,"
                            + " so ShellHeader.render / drawBezel would be painted inside the"
                            + " moving layer's transform.\n" + branch.substring(lastBody));
            int header = branch.indexOf("ShellHeader.render(", restore);
            assertTrue(header > restore,
                    screen + ": ShellHeader.render must come after the body layer is restored.\n"
                            + tail(branch, restore));
            int bezel = branch.indexOf("drawBezel(", header);
            assertTrue(bezel > header,
                    screen + ": drawBezel must be the last thing painted, after the header.\n"
                            + tail(branch, header));
        }
    }

    /**
     * The five public doors into a chat/nav push still converge on the one
     * animated start helper, so the style stays resolved in exactly one place
     * whichever door the player came through.
     */
    @Test
    void theFiveChatEntryPointsStillShareTheOneStartHelper() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertTrue(body(src, "private void pushNav(NavPage").contains("startPageNav("),
                    screen + ": pushNav must drive the one animated nav start");
            assertTrue(body(src, "public void popPage()").contains("startPageNav("),
                    screen + ": popPage must drive the one animated nav start");
            assertTrue(body(src, "private void startPageNav(").contains("pageNavStyle()"),
                    screen + ": startPageNav is the one place the nav style is resolved");
            assertTrue(body(src, "public void openWorldChat()").contains("pushPage(AppPage.WORLD_CHAT)"),
                    screen + ": openWorldChat must delegate into pushPage");
            assertTrue(body(src, "public void openPrivateChat(PlayerRef").contains("pushNav("),
                    screen + ": openPrivateChat must delegate into pushNav");
            assertTrue(body(src, "public void openSettingsSection(").contains("pushNav("),
                    screen + ": openSettingsSection must delegate into pushNav");
            assertTrue(body(src, "public void pushPage(AppPage").contains("pushNav("),
                    screen + ": pushPage must delegate into pushNav");
            assertTrue(body(src, "public void openProfileDetail(PlayerRef").contains("pushPage("),
                    screen + ": openProfileDetail must delegate into pushPage");
        }
    }

    /** Decorative motion off still snaps: the clocks gate on Animations. */
    @Test
    void decorativeMotionOffStillSnaps() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            List<String> gated = new ArrayList<>(List.of(
                    body(src, "private void stepZoomNav("),
                    body(src, "private void stepSlideNav(")));
            for (String step : gated) {
                assertTrue(step.contains("Animations.enabled()"),
                        screen + ": both nav clocks must zero their tau when decorative motion is off");
            }
            assertTrue(body(src, "public void popPage()").contains("!Animations.enabled()"),
                    screen + ": a pop with decorative motion off must land without waiting");
            assertTrue(body(src, "public void switchRoot(AppPage").contains("!Animations.enabled()"),
                    screen + ": a tab switch with decorative motion off must land without waiting");
        }
    }
}
