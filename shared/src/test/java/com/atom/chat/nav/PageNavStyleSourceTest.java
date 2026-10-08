package com.atom.chat.nav;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 */
class PageNavStyleSourceTest {

    /** The three screen sources, repo-root relative, in a fixed order. */
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

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
