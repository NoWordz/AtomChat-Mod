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
 * Source-level guard for the page-nav animation style on all three platforms,
 * rewritten to the Dango semantics.
 *
 * <p><b>The failure mode this test exists to prevent.</b> Detail-to-detail used
 * to be pinned to SLIDE whatever the config said, and the root-tab content had a
 * second, private style channel that ignored the config too — the user report
 * was "the global ZOOM style still slid the tab bar hard". In the inverted
 * semantics the style is read from the config in exactly one place (the
 * arrival composite's {@code style()}), every motion branch consumes it through
 * that one read, and no page pair, direction or surface carries a rule of its
 * own. A second resolver or a hardcoded variant is a failure here.</p>
 *
 * <p>Why a source probe and not only behaviour tests: the drawing side lives on
 * {@code AtomChatScreen}, which cannot be loaded without the Minecraft client.
 * The composite's own numbers are pinned in {@code PageTransitionCompositeTest};
 * this file pins the wiring — the one config read, the consumers that must go
 * through it, and the surfaces that must not branch on anything else.</p>
 *
 * <p>The three screen copies differ only in Minecraft API spellings, so one set
 * of assertions runs against all three; a one-platform fix is a failure here.</p>
 */
class PageNavStyleSourceTest {

    private static final String COMPOSITE =
            "shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java";

    /** The three screen sources, repo-root relative, in a fixed order. */
    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

    /**
     * The surfaces that own a per-item entrance and therefore need the motion
     * axis (SLIDE = horizontal, ZOOM = vertical). Each of these used to carry a
     * private {@code axis()} duplicating the config branch.
     */
    private static final List<String> ITEM_SURFACES = List.of(
            "layers/mapping/official/src/main/java/com/atom/chat/page/ConversationListPage.java",
            "layers/mapping/official/src/main/java/com/atom/chat/page/ProfilePage.java",
            "layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsHomePage.java",
            "layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/page/ConversationListPage.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/page/ProfilePage.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsHomePage.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java",
            "shared/src/main/java/com/atom/chat/ui/BottomTabBar.java");

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
     * The one resolution: the composite's {@code style()} reads the config with
     * no page-pair exception — no {@code isRoot}, no direction, no hardcoded
     * variant. The old "detail&lt;-&gt;detail is always SLIDE" bug cannot be
     * written back without failing here.
     */
    @Test
    void theStyleIsReadFromTheConfigWithNoPerPairException() throws IOException {
        String src = source(COMPOSITE);
        String style = body(src, "public AtomChatConfig.PageNavStyle style()");
        assertTrue(style.contains("AtomChatConfig.get().pageNavStyle"),
                "the resolver must read the config");
        assertFalse(style.contains("isRoot"), "no page pair is special");
        assertFalse(style.contains("PageNavStyle.SLIDE") && style.contains("return"),
                "the resolver returns the config's value, not a hardcoded variant");
        // The whole shell reads the style through this one method: no second
        // direct config read of the field in the nav layer.
        assertEquals(1, occurrences(src, "AtomChatConfig.get().pageNavStyle"),
                "the composite must hold the only nav-layer style read");
    }

    /**
     * Both envelope variants hang off that one read: SLIDE is the horizontal
     * drift, ZOOM the scale — the arriving page and the root tab content both
     * branch on the composite's reads, never on a private clock.
     */
    @Test
    void bothVariantsConsumeTheOneRead() throws IOException {
        String src = source(COMPOSITE);
        String envelope = body(src, "public void drawArrivingFrame(Canvas canvas, UiLayout layout");
        assertTrue(envelope.contains("PageNavStyle.ZOOM"),
                "the envelope branches on the resolved style");
        assertTrue(envelope.contains("overlayZoomScale(nowMs)"),
                "ZOOM rides the scale variant");
        assertTrue(envelope.contains("overlaySlideDx(nowMs)"),
                "SLIDE rides the drift variant");
    }

    /**
     * The style has exactly ONE consumer left — the pushed-page envelope in the
     * composite. Root pages are lists: they stagger their own items
     * (UiStagger.itemAnim) and deliberately do not ride the whole-page envelope,
     * so a root page cannot disagree with the global style by having a second
     * channel of its own. Bug regression: "全局 ZOOM 时底栏 tab 却播 SLIDE 且生硬".
     */
    @Test
    void theOnlyStyleConsumerIsThePushedPageEnvelope() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            String draw = body(src, "private void drawRootPage(Canvas canvas, UiLayout layout");
            assertFalse(draw.contains("overlayZoomScale("),
                    screen + ": the root page must not scale (it staggers per item)");
            assertFalse(draw.contains("overlaySlideDx("),
                    screen + ": the root page must not drift (it staggers per item)");
            assertFalse(draw.contains("director."),
                    screen + ": no private root-tab clock may come back");
            assertTrue(src.contains("arrivalStaggersSurfaces()"),
                    screen + ": the pushed-page envelope is the one style consumer");
        }
    }

    /**
     * The tab bar's capsule is the bar's own animation now: the screen selects
     * through {@code setSelectedIndex} (which snaps itself with motion off), and
     * nothing outside the bar writes the capsule's animator directly.
     */
    @Test
    void theCapsuleIsTheTabBarsOwnAnimation() throws IOException {
        String bar = source("shared/src/main/java/com/atom/chat/ui/BottomTabBar.java");
        String setSelected = body(bar, "public void setSelectedIndex(int index)");
        assertTrue(setSelected.contains("Animations.enabled()"),
                "the bar snaps itself when decorative motion is off");
        assertTrue(setSelected.contains("animateTo("), "and tweens itself otherwise");
        for (String screen : SCREENS) {
            String src = source(screen);
            assertFalse(src.contains("setSelectedImmediate"),
                    screen + ": the screen must select through the tweening entry point");
            assertFalse(src.contains("indicatorAnimator()"),
                    screen + ": the capsule's animator is not shared out any more");
        }
    }

    /**
     * The style reaches every motion consumer through ONE resolver
     * ({@link PageNavAxis}). Re-deriving the style→axis mapping inside a page is
     * how the surfaces start disagreeing again — the earlier defect of the same
     * shape was "全局 zoom 和 slide 变得没区别了".
     */
    @Test
    void theAxisIsResolvedOnceForEverySurface() throws IOException {
        String resolver = source("shared/src/main/java/com/atom/chat/nav/PageNavAxis.java");
        assertTrue(resolver.contains("AtomChatConfig.PageNavStyle.ZOOM"),
                "the one resolver holds the style→axis mapping");
        assertEquals(1, occurrences(resolver, "PageNavStyle.ZOOM"),
                "the mapping is a single comparison");
        for (String surface : ITEM_SURFACES) {
            String src = source(surface);
            // A surface may still READ the style (the settings page labels the
            // current choice); what it may not do is turn it into an axis.
            assertFalse(src.contains("UiStagger.Axis.VERTICAL : UiStagger.Axis.HORIZONTAL"),
                    surface + ": the surface must not re-derive the axis from the style");
            assertFalse(src.contains("UiStagger.Axis axis()"),
                    surface + ": a private axis() helper is a second resolver");
            assertTrue(src.contains("PageNavAxis.current()"),
                    surface + ": the surface must read the shared resolver");
        }
    }
}
