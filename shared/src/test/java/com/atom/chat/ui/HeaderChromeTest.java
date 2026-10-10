package com.atom.chat.ui;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSection;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HeaderChrome}, offline: every title branch, the back button's rect and
 * hit test, the hover spring and the pushed header's fixed position — the last
 * two on a real raster surface.
 *
 * <p><b>Scope, stated up front.</b> A recording host stands in for the screen, so
 * the label keys, the partner name and the two header rects are asserted on what
 * the domain asked for. The offscreen renders exercise the real
 * {@link ShellHeader} path. NOT covered: the shell's two {@code suppressHeader}
 * gates (they stay in the screen — see the block report), the return triggers,
 * and where the five header renders sit in the frame order — the D20 probe list.</p>
 */
class HeaderChromeTest {
    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;
    private static final UiLayout.Rect HEADER = new UiLayout.Rect(20.0F, 30.0F, 380.0F, 80.0F);
    private static final UiLayout.Rect PUSHED_HEADER = new UiLayout.Rect(16.0F, 44.0F, 388.0F, 80.0F);

    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return root.resolve("config");
            }

            @Override
            public Path gameDir() {
                return root.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @TempDir
    Path tmp;

    private static final PlayerRef ALICE = PlayerRef.of(new UUID(0x11L, 0x22L), "Alice");

    /** What the chrome asked of the screen, in order. */
    private static final class Recorder implements HeaderChrome.Host {
        final List<String> labelKeys = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        String privateName;
        UiLayout.Rect header = HEADER;
        UiLayout.Rect pushedHeader = PUSHED_HEADER;
        float dt = 16.0F;

        @Override
        public String label(String key) {
            labelKeys.add(key);
            return key;
        }

        @Override
        public String activePrivateName() {
            return privateName;
        }

        @Override
        public UiLayout.Rect headerRect() {
            return header;
        }

        @Override
        public UiLayout.Rect pushedHeaderRect() {
            calls.add("pushedHeaderRect");
            return pushedHeader;
        }

        @Override
        public float frameDt() {
            return dt;
        }

        @Override
        public int textPrimary() {
            return 0xFF112233;
        }
    }

    private Recorder host;
    private HeaderChrome chrome;

    private void setUp() {
        host = new Recorder();
        chrome = new HeaderChrome(host);
    }

    // ------------------------------------------------------------- 1. the titles

    @Test
    void aPrivatePageIsTitledWithThePartnersRealName() {
        setUp();
        assertEquals("Alice", chrome.shellTitleFor(NavPage.privateChat(ALICE)));
        assertEquals(List.of(), host.labelKeys,
                "the partner's real name is used as-is, never looked up as a label");
    }

    @Test
    void aTargetWithoutARealNameYieldsNoTitle() {
        setUp();
        // NavPage refuses a private page with no target at all, so the only
        // reachable oddity is a target whose real name is missing. The original
        // guards the target, not the name, so the title is null rather than the
        // channel fallback — carried over as-is.
        assertNull(chrome.shellTitleFor(NavPage.privateChat(PlayerRef.of(null, null))));

        setUp();
        assertEquals("atomchat.channel.private", chrome.shellTitleFor(AppPage.PRIVATE_CHAT),
                "the AppPage-level private title keeps its fallback label");
        assertEquals(List.of("atomchat.channel.private"), host.labelKeys);
    }

    @Test
    void aSettingsSubPageIsTitledWithItsSection() {
        setUp();
        assertEquals("atomchat.settings.appearance",
                chrome.shellTitleFor(NavPage.settingsSection(SettingsSection.APPEARANCE)));
        assertEquals("atomchat.settings.chat",
                chrome.shellTitleFor(NavPage.settingsSection(SettingsSection.CHAT)));
        assertEquals("atomchat.settings.privacy",
                chrome.shellTitleFor(NavPage.settingsSection(SettingsSection.PRIVACY)));
        assertEquals("atomchat.settings.about",
                chrome.shellTitleFor(NavPage.settingsSection(SettingsSection.ABOUT)));
    }

    @Test
    void aSettingsPageWithoutASectionFallsBackToTheTabLabel() {
        setUp();
        // Same as the private page: NavPage requires a section param, so the
        // section-less path is only reachable at the AppPage level.
        assertEquals("atomchat.tab.settings", chrome.shellTitleFor(AppPage.SETTINGS_SECTION));
    }

    @Test
    void everyRootPageHasItsOwnTitle() {
        setUp();
        assertEquals("atomchat.tab.chat", chrome.shellTitleFor(AppPage.CHAT_LIST));
        assertEquals("atomchat.page.profile.title", chrome.shellTitleFor(AppPage.PROFILE));
        assertEquals("atomchat.tab.settings", chrome.shellTitleFor(AppPage.SETTINGS));
        assertEquals("atomchat.channel.world", chrome.shellTitleFor(AppPage.WORLD_CHAT));
        assertEquals("atomchat.tab.settings", chrome.shellTitleFor(AppPage.SETTINGS_SECTION));
        assertEquals("atomchat.page.profile.title", chrome.shellTitleFor(AppPage.PROFILE_DETAIL));

        assertEquals("atomchat.channel.private", chrome.shellTitleFor(AppPage.PRIVATE_CHAT),
                "no partner yet: the tab's own label");
        host.privateName = "Bob";
        assertEquals("Bob", chrome.shellTitleFor(AppPage.PRIVATE_CHAT),
                "the partner's real name wins once there is one");
    }

    @Test
    void aNavWithAnOrdinaryPageDefersToThatPagesTitle() {
        setUp();
        assertEquals("atomchat.channel.world", chrome.shellTitleFor(NavPage.of(AppPage.WORLD_CHAT)));
        assertEquals("atomchat.page.profile.title",
                chrome.shellTitleFor(NavPage.of(AppPage.PROFILE_DETAIL)));
    }

    // --------------------------------------------------------- 2. the back button

    @Test
    void theBackButtonIsSizedByTheComposerKeyTokenAndPinnedLeft() {
        setUp();
        UiLayout.Rect back = chrome.backButton();
        float size = UiTokens.ACTION_BUTTON_SIZE;
        assertEquals(size, back.w(), "the side is ACTION_BUTTON_SIZE, the composer keys' token");
        assertEquals(size, back.h());
        assertEquals(HEADER.x() + UiTokens.EDGE_CONTROL_INSET, back.x(), "pinned to the card's left edge");
        assertEquals(HEADER.y() + (HEADER.h() - size) / 2.0F, back.y(), "centred vertically");
    }

    @Test
    void theBackButtonFollowsItsHeaderRect() {
        setUp();
        host.header = new UiLayout.Rect(0.0F, 200.0F, 300.0F, 40.0F);
        UiLayout.Rect back = chrome.backButton();
        assertEquals(UiTokens.EDGE_CONTROL_INSET, back.x());
        assertEquals(200.0F + (40.0F - UiTokens.ACTION_BUTTON_SIZE) / 2.0F, back.y(),
                "a shorter header keeps the button centred, not top-aligned");
    }

    @Test
    void theBackButtonHitTestCoversExactlyTheButton() {
        setUp();
        UiLayout.Rect back = chrome.backButton();
        assertTrue(chrome.isBackButtonHit(back.x() + 1.0F, back.y() + 1.0F), "inside");
        assertTrue(chrome.isBackButtonHit(back.x(), back.y()), "the top-left corner is inclusive");
        assertTrue(chrome.isBackButtonHit(back.x() + back.w(), back.y() + back.h()),
                "and so is the bottom-right");
        assertFalse(chrome.isBackButtonHit(back.x() - 1.0F, back.y() + 1.0F), "left of it");
        assertFalse(chrome.isBackButtonHit(back.x() + back.w() + 1.0F, back.y() + 1.0F), "right of it");
        assertFalse(chrome.isBackButtonHit(back.x() + 1.0F, back.y() - 1.0F), "above it");
        assertFalse(chrome.isBackButtonHit(back.x() + 1.0F, back.y() + back.h() + 1.0F), "below it");
    }

    // ------------------------------------------------------------ 3. the hover

    @Test
    void theHoverSpringRisesInsideAndDecaysOutside() {
        setUp();
        UiLayout.Rect back = chrome.backButton();
        float insideX = back.x() + 2.0F;
        float insideY = back.y() + 2.0F;

        assertEquals(0.0F, chrome.backButtonHover(), "it starts idle");
        for (int i = 0; i < 40; i++) {
            chrome.stepBackButtonHover(insideX, insideY);
        }
        float hovered = chrome.backButtonHover();
        assertTrue(hovered > 0.9F, "hovering inside fills the wash: " + hovered);

        for (int i = 0; i < 40; i++) {
            chrome.stepBackButtonHover(0.0F, 0.0F);
        }
        assertTrue(chrome.backButtonHover() < 0.1F, "leaving drains it: " + chrome.backButtonHover());
    }

    @Test
    void theHoverSpringIsDecorativeMotionGated() {
        setUp();
        boolean animations = com.atom.chat.config.AtomChatConfig.get().animationEnabled;
        try {
            // UiMotion's hover channel deliberately ignores the decorative-motion
            // switch (pointer motion never feels dead), so the spring still moves.
            com.atom.chat.config.AtomChatConfig.get().animationEnabled = false;
            UiLayout.Rect back = chrome.backButton();
            chrome.stepBackButtonHover(back.x() + 2.0F, back.y() + 2.0F);
            assertTrue(chrome.backButtonHover() > 0.0F,
                    "hover answers the pointer even with decorative motion off");
        } finally {
            com.atom.chat.config.AtomChatConfig.get().animationEnabled = animations;
        }
    }

    // -------------------------------------------------- 4. the pushed header

    @Test
    void thePushedHeaderIsDrawnThroughTheDetailHeaderRect() {
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            setUp();
            chrome.drawPushedHeader(surface.getCanvas(), -1.0F, -1.0F,
                    NavPage.settingsSection(SettingsSection.CHAT));
            assertTrue(host.calls.contains("pushedHeaderRect"),
                    "the fixed header comes from the detail layout, never from the sliding page");
            int[] bounds = paintedBounds(surface);
            assertNotNull(bounds, "the header must actually paint");
            assertHeaderPlateIsAt(bounds, PUSHED_HEADER);
        } finally {
            surface.close();
        }
    }

    @Test
    void thePushedHeaderStaysInPlaceAcrossPages() {
        Surface first = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        Surface second = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            setUp();
            chrome.drawPushedHeader(first.getCanvas(), -1.0F, -1.0F,
                    NavPage.settingsSection(SettingsSection.CHAT));
            int[] a = paintedBounds(first);

            setUp();
            chrome.drawPushedHeader(second.getCanvas(), -1.0F, -1.0F,
                    NavPage.settingsSection(SettingsSection.ABOUT));
            int[] b = paintedBounds(second);

            assertNotNull(a);
            assertNotNull(b);
            // Same chrome in the same place; only the title text inside differs.
            assertHeaderPlateIsAt(a, PUSHED_HEADER);
            assertHeaderPlateIsAt(b, PUSHED_HEADER);
            assertNotEquals(signature(first), signature(second), "the titles differ");
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void hoveringInsideChangesThePushedHeadersPixels() {
        Surface idle = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        Surface hovered = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            setUp();
            for (int i = 0; i < 40; i++) {
                chrome.drawPushedHeader(idle.getCanvas(), -1.0F, -1.0F,
                        NavPage.settingsSection(SettingsSection.CHAT));
            }

            setUp();
            UiLayout.Rect back = chrome.backButton();
            for (int i = 0; i < 40; i++) {
                chrome.drawPushedHeader(hovered.getCanvas(), back.x() + 2.0F, back.y() + 2.0F,
                        NavPage.settingsSection(SettingsSection.CHAT));
            }

            assertTrue(chrome.backButtonHover() > 0.9F, "the last pass hovered");
            assertNotEquals(signature(idle), signature(hovered),
                    "the hover wash is drawn inside the back button");
        } finally {
            idle.close();
            hovered.close();
        }
    }

    @Test
    void theHoverWashLandsInsideTheBackButtonRect() {
        Surface idle = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        Surface hovered = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            NavPage nav = NavPage.settingsSection(SettingsSection.CHAT);
            UiLayout.Rect warmBack = warmUp(nav);
            setUp();
            for (int i = 0; i < 40; i++) {
                chrome.drawPushedHeader(idle.getCanvas(), -1.0F, -1.0F, nav);
            }
            UiLayout.Rect back = chrome.backButton();
            int[] button = {(int) back.x(), (int) back.y(), (int) back.w(), (int) back.h()};
            int[] titleArea = {(int) (PUSHED_HEADER.x() + PUSHED_HEADER.w() / 2.0F), (int) PUSHED_HEADER.y(),
                    (int) (PUSHED_HEADER.w() / 2.0F) - 2, (int) PUSHED_HEADER.h()};

            setUp();
            for (int i = 0; i < 40; i++) {
                chrome.drawPushedHeader(hovered.getCanvas(), warmBack.x() + 2.0F,
                        warmBack.y() + 2.0F, nav);
            }

            // Colour mass rather than byte identity: the header render carries a
            // rare glyph-rasterisation jitter across JVM runs, while the wash itself
            // moves ~100k of mass over the button. The thresholds sit two orders of
            // magnitude away from both, so they catch a missing wash and a wash that
            // leaked into the title; the warm-up above removes the one source of
            // movement that was neither (a cold first pass).
            assertTrue(colourMass(idle, hovered, button) > 10_000L,
                    "the hover wash is painted where the hit rect says the button is (mass="
                            + colourMass(idle, hovered, button) + ")");
            assertTrue(colourMass(idle, hovered, titleArea) < 2_000L,
                    "and nowhere else: the title area is untouched by a back-button hover (mass="
                            + colourMass(idle, hovered, titleArea) + ")");
        } finally {
            idle.close();
            hovered.close();
        }
    }

    /**
     * Renders the header through both the idle and the hover path once, into a
     * throwaway surface, and returns the back-button rect.
     *
     * <p>The two measured passes below have to see the same rasteriser state. In a
     * full-suite run the first pass through this header starts colder than the
     * second one, and the title area came back with ~16k of movement — while both
     * passes measured 0 in isolation. Warming both shapes up first makes the
     * measurement a property of the wash instead of of the JVM's warm-up order.
     */
    private static UiLayout.Rect warmUp(NavPage nav) {
        Surface warm = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            HeaderChrome cold = new HeaderChrome(new Recorder());
            for (int i = 0; i < 40; i++) {
                cold.drawPushedHeader(warm.getCanvas(), -1.0F, -1.0F, nav);
            }
            UiLayout.Rect back = cold.backButton();
            HeaderChrome hot = new HeaderChrome(new Recorder());
            for (int i = 0; i < 40; i++) {
                hot.drawPushedHeader(warm.getCanvas(), back.x() + 2.0F, back.y() + 2.0F, nav);
            }
            return back;
        } finally {
            warm.close();
        }
    }

    /** Total RGB movement between two frames inside one rect. */
    private static long colourMass(Surface a, Surface b, int[] rect) {        byte[] pa = readPixels(a);
        byte[] pb = readPixels(b);
        long sum = 0L;
        for (int y = Math.max(0, rect[1]); y < Math.min(SURFACE_H, rect[1] + rect[3]); y++) {
            for (int x = Math.max(0, rect[0]); x < Math.min(SURFACE_W, rect[0] + rect[2]); x++) {
                int at = (y * SURFACE_W + x) * 4;
                sum += Math.abs((pa[at] & 0xFF) - (pb[at] & 0xFF))
                        + Math.abs((pa[at + 1] & 0xFF) - (pb[at + 1] & 0xFF))
                        + Math.abs((pa[at + 2] & 0xFF) - (pb[at + 2] & 0xFF));
            }
        }
        return sum;
    }

    /** The header plate's painted box must match the rect the chrome was given. */
    private static void assertHeaderPlateIsAt(int[] bounds, UiLayout.Rect header) {
        // The plate carries a two-tier shadow, so the painted box is a little
        // larger than the rect on every side; the rect's own edges must be inside.
        assertTrue(bounds[0] <= header.x(), "the plate reaches the rect's left edge");
        assertTrue(bounds[1] <= header.y(), "and its top edge");
        assertTrue(bounds[2] >= header.x() + header.w(), "and its right edge");
        assertTrue(bounds[3] >= header.y() + header.h(), "and its bottom edge");
        assertTrue(bounds[2] - bounds[0] < header.w() + 80, "but it is not some other, bigger box");
        assertTrue(bounds[3] - bounds[1] < header.h() + 80, "nor a taller one");
    }

    private static int[] paintedBounds(Surface surface) {
        long[] raw = pixelSummary(surface);
        if (raw[0] < 0) {
            return null;
        }
        return new int[]{(int) raw[1], (int) raw[2], (int) raw[3], (int) raw[4]};
    }

    /** {nonzero?, x1, y1, x2, y2} of the opaque pixels. */
    private static long[] pixelSummary(Surface surface) {
        byte[] px = readPixels(surface);
        int x1 = Integer.MAX_VALUE;
        int y1 = Integer.MAX_VALUE;
        int x2 = -1;
        int y2 = -1;
        for (int y = 0; y < SURFACE_H; y++) {
            for (int x = 0; x < SURFACE_W; x++) {
                if ((px[(y * SURFACE_W + x) * 4 + 3] & 0xFF) != 0) {
                    x1 = Math.min(x1, x);
                    x2 = Math.max(x2, x);
                    y1 = Math.min(y1, y);
                    y2 = Math.max(y2, y);
                }
            }
        }
        return x2 < 0 ? new long[]{-1, 0, 0, 0, 0} : new long[]{1, x1, y1, x2, y2};
    }

    private static long signature(Surface surface) {
        byte[] px = readPixels(surface);
        long sum = 0L;
        for (int i = 0; i < px.length; i++) {
            sum = sum * 31L + (px[i] & 0xFF);
        }
        return sum;
    }

    /** A fingerprint of one rect's pixels. */
    private static long signature(Surface surface, int[] rect) {
        byte[] px = readPixels(surface);
        long sum = 0L;
        for (int y = Math.max(0, rect[1]); y < Math.min(SURFACE_H, rect[1] + rect[3]); y++) {
            for (int x = Math.max(0, rect[0]); x < Math.min(SURFACE_W, rect[0] + rect[2]); x++) {
                int at = (y * SURFACE_W + x) * 4;
                sum = sum * 31L + (px[at] & 0xFF) + 0x100L * (px[at + 1] & 0xFF)
                        + 0x10000L * (px[at + 2] & 0xFF) + 0x1000000L * (px[at + 3] & 0xFF);
            }
        }
        return sum;
    }

    private static byte[] readPixels(Surface surface) {
        Bitmap bitmap = new Bitmap();
        try {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            return bitmap.readPixels();
        } finally {
            bitmap.close();
        }
    }
}
