package com.atom.chat.ui;

import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.DrawPrimitives;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ContextMenuController}, offline: the one row source behind drawing and
 * every hit test, the closing hand-over that must never recurse, the three modes'
 * row semantics, the offline dimming and the menu's real offscreen rendering.
 *
 * <p><b>How "drawing and hit-testing share one source" is nailed.</b> The host
 * records every icon the drawing blits — one per row, at that row's centre, with
 * the colour that row's label gets. The row centres therefore come out of the
 * <em>drawing</em>, and the hit test is then asked for exactly those points. A
 * drift between the two (the historical bug: three drawn rows, two hittable)
 * breaks either the icon count or the row the pointer lands on. The same
 * recording carries the icon family and the label keys.</p>
 *
 * <p><b>Scope, stated up front.</b> A recording host stands in for the screen and
 * a real raster {@link Surface} for the canvas, so the geometry, label, icon and
 * dimming paths are asserted on what is actually drawn. NOT covered: the pointer
 * plumbing that calls {@code hitRow} (the shell's input handlers), the actions a
 * row triggers (D19b's half), and the menu's look on a real screen — the D19a
 * probe list.</p>
 */
class ContextMenuControllerTest {
    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;

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
    private static final PlayerRef BOB = PlayerRef.of(new UUID(0x33L, 0x44L), "Bob");

    /** One icon the drawing blitted, with the geometry and colour it used. */
    private record Icon(io.github.humbleui.skija.Path path, float cx, float cy, float size, int color) {
    }

    /** What the menu asked of the screen, in order. */
    private static final class Recorder implements ContextMenuController.Host {
        final List<String> labelKeys = new ArrayList<>();
        final List<Icon> icons = new ArrayList<>();
        UiLayout.Rect panel = new UiLayout.Rect(10.0F, 10.0F, 400.0F, 780.0F);
        float dt = 16.0F;
        boolean aliceOnline = true;

        @Override
        public String label(String key) {
            labelKeys.add(key);
            return key;
        }

        @Override
        public boolean isPlayerOnline(PlayerRef player) {
            return player != null && player.equals(ALICE) && aliceOnline;
        }

        @Override
        public UiLayout.Rect panelRect() {
            return panel;
        }

        @Override
        public float frameDt() {
            return dt;
        }

        @Override
        public void drawIcon(Canvas canvas, io.github.humbleui.skija.Path icon, float cx, float cy, float size, int color) {
            icons.add(new Icon(icon, cx, cy, size, color));
        }

        void reset() {
            labelKeys.clear();
            icons.clear();
        }

        /** The row height the drawing itself used: the gap between icon centres. */
        float rowHeight() {
            return icons.size() < 2 ? UiTokens.MENU_H / 2.0F : icons.get(1).cy() - icons.get(0).cy();
        }

        /** The menu box the drawing used, read back out of the icons it blitted. */
        float boxLeft() {
            return icons.get(0).cx() - UiTokens.s(18);
        }

        float rowCentreY(int row) {
            return icons.get(0).cy() + rowHeight() * row;
        }
    }

    private Recorder host;
    private ContextMenuController<String> menu;

    private void setUp() {
        host = new Recorder();
        menu = new ContextMenuController<>(host);
    }

    private static ContextMenuController.Subject<String> subject(PlayerRef sender, boolean image, String row) {
        return ContextMenuController.Subject.of(sender, image, row);
    }

    private static Surface raster() {
        return Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
    }

    /** Settles the popup animation at full opacity with no hover, recording cleared. */
    private void settle(Surface surface) {
        for (int i = 0; i < 40; i++) {
            menu.draw(surface.getCanvas(), -1.0F, -1.0F);
        }
        host.reset();
        menu.draw(surface.getCanvas(), -1.0F, -1.0F);
    }

    // ------------------------------------------------------------- 1. row source

    @Test
    void theRowCountIsOneSourceForAllThreeModes() {
        assertEquals(2, ContextMenuController.rows(ContextMenuController.ContextMenuMode.BUBBLE, false));
        assertEquals(3, ContextMenuController.rows(ContextMenuController.ContextMenuMode.BUBBLE, true));
        assertEquals(4, ContextMenuController.rows(ContextMenuController.ContextMenuMode.AVATAR, false));
        assertEquals(4, ContextMenuController.rows(ContextMenuController.ContextMenuMode.AVATAR, true),
                "the avatar menu has no image row: still four");
        assertEquals(3, ContextMenuController.rows(ContextMenuController.ContextMenuMode.PLAYER_CARD, false));
        assertEquals(3, ContextMenuController.rows(ContextMenuController.ContextMenuMode.PLAYER_CARD, true));
    }

    @Test
    void theCancelBlockRowIsHittable() {
        setUp();
        Surface surface = raster();
        try {
            menu.openPlayerCard(ALICE, 100.0F, 100.0F);
            settle(surface);
            assertEquals(3, host.icons.size(), "the player-card menu draws three rows");
            assertEquals(2, menu.hitPlayerCardRow(host.boxLeft() + 5.0F, host.rowCentreY(2)),
                    "the third row (block/unblock) must be clickable — this is the old bug");
            assertEquals(1, menu.hitPlayerCardRow(host.boxLeft() + 5.0F, host.rowCentreY(1)));
            assertEquals(0, menu.hitPlayerCardRow(host.boxLeft() + 5.0F, host.rowCentreY(0)));
        } finally {
            surface.close();
        }
    }

    @Test
    void everyDrawnRowIsHittableForEveryMode() {
        Surface surface = raster();
        try {
            setUp();
            menu.openBubble(subject(ALICE, false, "bubble"), 100.0F, 100.0F);
            assertEveryDrawnRowHits(surface, "bubble (2 rows)");

            setUp();
            menu.openBubble(subject(ALICE, true, "image"), 100.0F, 100.0F);
            assertEveryDrawnRowHits(surface, "bubble with an image (3 rows)");

            setUp();
            menu.openAvatar(subject(ALICE, false, "avatar"), 100.0F, 100.0F);
            assertEveryDrawnRowHits(surface, "avatar (4 rows)");

            setUp();
            menu.openPlayerCard(ALICE, 100.0F, 100.0F);
            settle(surface);
            for (int row = 0; row < host.icons.size(); row++) {
                assertEquals(row, menu.hitPlayerCardRow(host.boxLeft() + 5.0F, host.rowCentreY(row)),
                        "player card: the pointer on the drawn row " + row + " must hit row " + row);
            }
        } finally {
            surface.close();
        }
    }

    /** The drawn rows and the hittable rows must be the same set, row for row. */
    private void assertEveryDrawnRowHits(Surface surface, String what) {
        settle(surface);
        assertTrue(host.icons.size() >= 2, what + ": the menu must draw something to measure against");
        for (int row = 0; row < host.icons.size(); row++) {
            assertEquals(row, menu.hitRow(host.boxLeft() + 5.0F, host.rowCentreY(row)),
                    what + ": the pointer on the drawn row " + row + " must hit row " + row);
        }
    }

    @Test
    void aPointerOutsideTheBoxHitsNoRow() {
        setUp();
        Surface surface = raster();
        try {
            menu.openPlayerCard(ALICE, 100.0F, 100.0F);
            settle(surface);

            float left = host.boxLeft();
            float top = host.rowCentreY(0) - host.rowHeight() / 2.0F;
            assertEquals(-1, menu.hitPlayerCardRow(left - 1.0F, top + 1.0F), "left of the box");
            assertEquals(-1, menu.hitPlayerCardRow(left + UiTokens.MENU_W + 1.0F, top + 1.0F), "right of it");
            assertEquals(-1, menu.hitPlayerCardRow(left + 5.0F, top - 1.0F), "above it");
            assertEquals(-1, menu.hitPlayerCardRow(left + 5.0F, top + host.rowHeight() * 3.0F + 1.0F),
                    "below it");
        } finally {
            surface.close();
        }
    }

    @Test
    void theBoxClampsToThePanel() {
        setUp();
        Surface surface = raster();
        try {
            // Anchored at the panel's bottom-right corner: the menu must be pulled
            // back inside, and the hit test must follow the clamped box.
            menu.openPlayerCard(ALICE, 500.0F, 900.0F);
            settle(surface);

            float left = host.boxLeft();
            assertTrue(left + UiTokens.MENU_W <= host.panel.x() + host.panel.w(),
                    "the drawn box must fit inside the panel");
            assertEquals(2, menu.hitPlayerCardRow(left + 5.0F, host.rowCentreY(2)),
                    "and the clamp is what the hit test measures too");
        } finally {
            surface.close();
        }
    }

    @Test
    void aClosedMenuHitsNothing() {
        setUp();
        assertEquals(-1, menu.hitRow(100.0F, 100.0F));
        assertEquals(-1, menu.hitPlayerCardRow(100.0F, 100.0F));
    }

    // --------------------------------------------------- 2. close must not recurse

    @Test
    void closeNeverRecurses() {
        setUp();
        menu.openBubble(subject(ALICE, false, "row"), 100.0F, 100.0F);
        // A self-recursive close would overflow the stack on the very first call;
        // the loop makes that certain rather than likely. Nothing here may throw.
        for (int i = 0; i < 200_000; i++) {
            menu.close();
        }
        assertFalse(menu.hasMessageMenu(), "the open target is gone");
        assertNull(menu.currentRow());
        assertFalse(menu.hasPlayerCardMenu(), "and no player target was invented");
    }

    @Test
    void closeIsIdempotentAndKeepsTheLastTarget() {
        setUp();
        Surface surface = raster();
        try {
            menu.openBubble(subject(ALICE, false, "row"), 100.0F, 100.0F);
            settle(surface);
            menu.close();
            menu.close();
            menu.close();
            // Nothing left to hand over, so the closing target survived the repeats
            // and still has frames to draw.
            host.reset();
            menu.draw(surface.getCanvas(), -1.0F, -1.0F);
            assertFalse(host.icons.isEmpty(), "the closing menu still draws (it fades, it does not vanish)");
        } finally {
            surface.close();
        }
    }

    @Test
    void theClosingMenuFadesOutAndIsThenDropped() {
        setUp();
        boolean animations = AtomChatConfig.get().animationEnabled;
        AtomChatConfig.get().animationEnabled = true;
        Surface surface = raster();
        try {
            menu.openBubble(subject(ALICE, false, "row"), 100.0F, 100.0F);
            settle(surface);
            menu.close();
            assertFalse(menu.hasMessageMenu(), "closing clears the current target at once");
            assertTrue(menu.currentRow() == null);

            int paintedFrames = 0;
            for (int i = 0; i < 400; i++) {
                host.reset();
                menu.draw(surface.getCanvas(), -1.0F, -1.0F);
                if (host.icons.isEmpty()) {
                    break;
                }
                paintedFrames++;
            }
            assertTrue(paintedFrames > 0, "the closing frames keep drawing the menu while it fades");
            for (int i = 0; i < 5; i++) {
                host.reset();
                menu.draw(surface.getCanvas(), -1.0F, -1.0F);
            }
            assertTrue(host.icons.isEmpty(), "a fully faded menu paints nothing");
        } finally {
            surface.close();
            AtomChatConfig.get().animationEnabled = animations;
        }
    }

    // ------------------------------------------------------ 3. the mode semantics

    @Test
    void theBubbleMenuHasNoImageRowUnlessTheTargetCarriesAnImage() {
        Surface surface = raster();
        try {
            setUp();
            menu.openBubble(subject(ALICE, false, "plain"), 100.0F, 100.0F);
            settle(surface);
            assertEquals(2, host.icons.size());
            assertEquals(List.of("atomchat.context.copy", "atomchat.context.quote"), host.labelKeys);
            assertSame(DrawPrimitives.ICON_COPY_PATH, host.icons.get(0).path());
            assertSame(DrawPrimitives.ICON_QUOTE_PATH, host.icons.get(1).path());

            setUp();
            menu.openBubble(subject(ALICE, true, "picture"), 100.0F, 100.0F);
            settle(surface);
            assertEquals(3, host.icons.size());
            assertEquals(List.of("atomchat.context.copy", "atomchat.context.quote",
                    "atomchat.context.save"), host.labelKeys);
            assertSame(DrawPrimitives.ICON_SAVE_PATH, host.icons.get(2).path());
        } finally {
            surface.close();
        }
    }

    @Test
    void theAvatarMenuCarriesTheFourSenderActions() {
        setUp();
        Surface surface = raster();
        try {
            menu.openAvatar(subject(ALICE, true, "avatar"), 100.0F, 100.0F);
            settle(surface);
            assertEquals(4, host.icons.size(), "the avatar menu ignores the image flag");
            assertEquals(List.of("atomchat.context.mention", "atomchat.context.whisper",
                    "atomchat.context.tp", "atomchat.context.block"), host.labelKeys);
            assertSame(DrawPrimitives.ICON_MENTION_PATH, host.icons.get(0).path());
            assertSame(DrawPrimitives.ICON_WHISPER_PATH, host.icons.get(1).path());
            assertSame(DrawPrimitives.ICON_TP_PATH, host.icons.get(2).path());
            assertSame(DrawPrimitives.ICON_BLOCK_PATH, host.icons.get(3).path());
        } finally {
            surface.close();
        }
    }

    @Test
    void thePlayerCardMenuCarriesProfileTeleportAndBlock() {
        setUp();
        Surface surface = raster();
        try {
            menu.openPlayerCard(ALICE, 100.0F, 100.0F);
            settle(surface);
            assertEquals(3, host.icons.size());
            assertEquals(List.of("atomchat.context.profile", "atomchat.context.tp",
                    "atomchat.context.block"), host.labelKeys);
            assertSame(AppIcons.ICON_TAB_PROFILE_PATH, host.icons.get(0).path());
            assertSame(DrawPrimitives.ICON_TP_PATH, host.icons.get(1).path());
            assertSame(DrawPrimitives.ICON_BLOCK_PATH, host.icons.get(2).path());
        } finally {
            surface.close();
        }
    }

    @Test
    void theBlockRowSaysUnblockOnceThePlayerIsBlocked() {
        Surface surface = raster();
        try {
            setUp();
            menu.openPlayerCard(ALICE, 100.0F, 100.0F);
            settle(surface);
            assertEquals("atomchat.context.block", host.labelKeys.get(2));

            BlockList.setBlocked(ALICE, true);

            setUp();
            menu.openPlayerCard(ALICE, 100.0F, 100.0F);
            settle(surface);
            assertEquals("atomchat.context.unblock", host.labelKeys.get(2),
                    "the same row flips its label once the player is blocked");

            setUp();
            menu.openAvatar(subject(ALICE, false, "avatar"), 100.0F, 100.0F);
            settle(surface);
            assertEquals("atomchat.context.unblock", host.labelKeys.get(3),
                    "and so does the avatar menu's fourth row");
        } finally {
            BlockList.setBlocked(ALICE, false);
            AtomChatConfig.get().blockedPlayers = new ArrayList<>();
            surface.close();
        }
    }

    // -------------------------------------------------------- 4. offline dimming

    @Test
    void anOfflineTeleportRowIsDimmedAndAnOnlineOneIsNot() {
        setUp();
        Surface surface = raster();
        try {
            menu.openPlayerCard(ALICE, 100.0F, 100.0F);
            settle(surface);
            assertTrue(alpha(host.icons.get(1).color()) > 200, "an online player's teleport row is solid");

            host.aliceOnline = false;
            host.reset();
            menu.draw(surface.getCanvas(), -1.0F, -1.0F);
            assertTrue(alpha(host.icons.get(1).color()) < 200, "an offline player's teleport row is dimmed");
            assertTrue(alpha(host.icons.get(0).color()) > 200, "the profile row is not");
            assertTrue(alpha(host.icons.get(2).color()) > 200, "and neither is the block row");
        } finally {
            surface.close();
        }
    }

    @Test
    void anOfflineAvatarsTeleportRowIsDimmed() {
        Surface surface = raster();
        try {
            setUp();
            menu.openAvatar(subject(ALICE, false, "avatar"), 100.0F, 100.0F);
            settle(surface);
            host.aliceOnline = false;
            host.reset();
            menu.draw(surface.getCanvas(), -1.0F, -1.0F);
            assertTrue(alpha(host.icons.get(2).color()) < 200, "the avatar menu's tp row is dimmed offline");
            assertTrue(alpha(host.icons.get(0).color()) > 200, "the @ mention row is not");

            // The shell always hands over a PlayerRef (PlayerRef.of never returns
            // null), so "no identity" arrives as an empty one — and that is offline.
            setUp();
            menu.openAvatar(subject(PlayerRef.of(null, null), false, "avatar"), 100.0F, 100.0F);
            settle(surface);
            assertTrue(alpha(host.icons.get(2).color()) < 200,
                    "a target with no identity is offline by definition");
        } finally {
            surface.close();
        }
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    // ------------------------------------------------------------ 5. state wiring

    @Test
    void theModesReportThemselvesAndLeaveTheOtherSlotAlone() {
        setUp();
        menu.openPlayerCard(ALICE, 1.0F, 2.0F);
        assertTrue(menu.hasPlayerCardMenu());
        assertFalse(menu.hasMessageMenu());
        assertFalse(menu.isAvatarMode());
        assertEquals(ALICE, menu.currentOrLastPlayer());

        menu.openAvatar(subject(BOB, false, "row"), 3.0F, 4.0F);
        assertTrue(menu.hasMessageMenu());
        assertTrue(menu.isAvatarMode());
        assertEquals("row", menu.currentRow());
        assertTrue(menu.hasPlayerCardMenu(),
                "opening a message menu does not clear the player slot — carried over as-is");

        menu.openBubble(subject(BOB, false, "row2"), 5.0F, 6.0F);
        assertTrue(menu.hasMessageMenu());
        assertFalse(menu.isAvatarMode());
        assertEquals("row2", menu.currentRow());
    }

    @Test
    void openingThePlayerCardMenuClearsTheMessageSlot() {
        setUp();
        menu.openBubble(subject(ALICE, false, "row"), 1.0F, 2.0F);
        menu.openPlayerCard(BOB, 3.0F, 4.0F);
        assertFalse(menu.hasMessageMenu(), "the message target is dropped, as the inline code did");
        assertNull(menu.currentRow());
        assertEquals(BOB, menu.currentOrLastPlayer());
    }

    @Test
    void thePlayerTargetSurvivesTheClose() {
        setUp();
        menu.openPlayerCard(ALICE, 1.0F, 2.0F);
        menu.close();
        assertEquals(ALICE, menu.currentOrLastPlayer(), "the closing menu still has a target to act on");
        assertTrue(menu.hasPlayerCardMenu());
        assertFalse(menu.hasMessageMenu());
    }

    @Test
    void resetTransientDropsEverythingIncludingTheClosingAnimation() {
        setUp();
        Surface surface = raster();
        try {
            menu.openBubble(subject(ALICE, false, "row"), 1.0F, 2.0F);
            menu.resetTransient();
            assertFalse(menu.hasMessageMenu());
            assertFalse(menu.hasPlayerCardMenu(), "the closing slot goes too: no stale fade on the next page");
            assertNull(menu.currentRow());

            for (int i = 0; i < 5; i++) {
                host.reset();
                menu.draw(surface.getCanvas(), -1.0F, -1.0F);
            }
            assertTrue(host.icons.isEmpty(), "nothing is left to draw");
        } finally {
            surface.close();
        }
    }

    // ---------------------------------------------------------- 6. real rendering

    @Test
    void eachModeReallyPaintsInsideItsBox() {
        Surface surface = raster();
        try {
            for (ContextMenuController.ContextMenuMode mode : ContextMenuController.ContextMenuMode.values()) {
                setUp();
                if (mode == ContextMenuController.ContextMenuMode.BUBBLE) {
                    menu.openBubble(subject(ALICE, false, "row"), 60.0F, 60.0F);
                } else if (mode == ContextMenuController.ContextMenuMode.AVATAR) {
                    menu.openAvatar(subject(ALICE, false, "row"), 60.0F, 60.0F);
                } else {
                    menu.openPlayerCard(ALICE, 60.0F, 60.0F);
                }
                settle(surface);
                int[] bounds = paintedBounds(surface);
                assertNotNull(bounds, mode + ": the menu must actually paint pixels");
                float rowH = UiTokens.MENU_H / 2.0F;
                int expected = (int) Math.ceil(rowH * ContextMenuController.rows(mode, false));
                assertTrue(bounds[3] - bounds[1] + 1 >= expected - 2,
                        mode + ": the painted plate must be at least as tall as its rows ("
                                + (bounds[3] - bounds[1] + 1) + " vs " + expected + ")");
                assertTrue(bounds[2] - bounds[0] + 1 >= UiTokens.MENU_W - 2,
                        mode + ": and at least as wide as the menu token");
            }
        } finally {
            surface.close();
        }
    }

    @Test
    void theClosingMenuFadesOutFromTheLastSource() {
        setUp();
        boolean animations = AtomChatConfig.get().animationEnabled;
        AtomChatConfig.get().animationEnabled = true;
        Surface scratch = raster();
        Surface open = raster();
        Surface closing = raster();
        try {
            menu.openBubble(subject(ALICE, false, "row"), 100.0F, 100.0F);
            // The animation advances on a scratch surface; each frame that is
            // measured is composited alone onto a clean one, because repeated
            // draws over the same opaque plate would saturate the alpha.
            for (int i = 0; i < 40; i++) {
                menu.draw(scratch.getCanvas(), -1.0F, -1.0F);
            }
            host.reset();
            menu.draw(open.getCanvas(), -1.0F, -1.0F);
            float openLeft = host.boxLeft();
            int openWidth = plateWidth(open);

            menu.close();
            host.reset();
            menu.draw(closing.getCanvas(), -1.0F, -1.0F);
            assertEquals(2, host.icons.size(), "the closing frames still draw the whole menu");
            assertEquals(openLeft, host.boxLeft(), "at the same place: it is the last source being drawn");
            assertTrue(alphaSum(closing) < alphaSum(open),
                    "and lighter than the open menu — the fade is the whole animation ("
                            + alphaSum(closing) + " vs " + alphaSum(open) + ")");
            // The popup's 0.94 -> 1 scale rides the same anim channel and is carried
            // over verbatim; at a visible alpha it moves the plate by well under a
            // pixel (0.94 is only reached as the menu becomes invisible), so the
            // plate is asserted here to still land in the same place and width.
            assertTrue(Math.abs(openWidth - plateWidth(closing)) <= 2,
                    "and the plate keeps its width (" + openWidth + " vs " + plateWidth(closing) + ")");
        } finally {
            scratch.close();
            open.close();
            closing.close();
            AtomChatConfig.get().animationEnabled = animations;
        }
    }

    /** The widest opaque run on the surface: the plate's own width. */
    private static int plateWidth(Surface surface) {
        byte[] px = readPixels(surface);
        int widest = 0;
        for (int y = 0; y < SURFACE_H; y++) {
            int run = 0;
            for (int x = 0; x < SURFACE_W; x++) {
                if ((px[(y * SURFACE_W + x) * 4 + 3] & 0xFF) > 90) {
                    widest = Math.max(widest, ++run);
                } else {
                    run = 0;
                }
            }
        }
        return widest;
    }

    /** Total alpha the frame painted: how bright the menu is this frame. */
    private static long alphaSum(Surface surface) {
        byte[] px = readPixels(surface);
        long sum = 0L;
        for (int i = 3; i < px.length; i += 4) {
            sum += px[i] & 0xFF;
        }
        return sum;
    }

    @Test
    void aHoveredRowIsPaintedDifferentlyFromAnIdleOne() {
        int[] strip = new int[4];
        long idle;
        long hovered;
        Surface surface = raster();
        try {
            setUp();
            menu.openPlayerCard(ALICE, 60.0F, 60.0F);
            settle(surface);
            float left = host.boxLeft();
            float centre = host.rowCentreY(2);
            strip = new int[]{(int) left + 2, (int) (centre - host.rowHeight() / 2.0F) + 2,
                    (int) UiTokens.MENU_W - 4, (int) host.rowHeight() - 4};
            idle = signature(readPixels(surface), strip);

            for (int i = 0; i < 30; i++) {
                menu.draw(surface.getCanvas(), left + 5.0F, centre);
            }
            hovered = signature(readPixels(surface), strip);
        } finally {
            surface.close();
        }
        assertNotEquals(idle, hovered,
                "the hovered row's wash and bounce must change the pixels it occupies");
    }

    private static int[] paintedBounds(Surface surface) {
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
        return x2 < 0 ? null : new int[]{x1, y1, x2, y2};
    }

    /** A cheap fingerprint of one rect's pixels. */
    private static long signature(byte[] px, int[] rect) {
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
