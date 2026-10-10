package com.atom.chat.ui;

import com.atom.chat.chat.ConversationKeys;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.ReplySlotStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.history.HistoryFoldStore;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.DrawPrimitives;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScrollSyncService} offline: which controller a page gets, the two
 * orderings its drawing depends on, and the hit bands of its two controls.
 *
 * <p>The scrollbar and the pill are drawn for real on a CPU raster surface and
 * then <em>read back</em>: the hit tests are aimed at pixels the frame actually
 * painted (their bounding box), so a band cannot drift away from the control it
 * belongs to without failing here. The pill's drawing also carries the transition
 * contract — the fade, hover and spring are stepped only by the page that owns
 * the composer — so one test drives the non-owning side and asserts it redraws
 * without stepping anything.
 *
 * <p>The composer page is not in the golden set (those twelve pages never draw
 * the chat page body), so this is the coverage for these two controls; the
 * per-frame colour of the panel behind them is not this block's business.
 */
class ScrollSyncServiceTest {
    private static final int SURFACE_W = 480;
    private static final int SURFACE_H = 680;
    private static final UUID ALICE_UUID = UUID.nameUUIDFromBytes("alice".getBytes(StandardCharsets.UTF_8));
    private static final UUID BOB_UUID = UUID.nameUUIDFromBytes("bob".getBytes(StandardCharsets.UTF_8));
    private static final PlayerRef ALICE = PlayerRef.of(ALICE_UUID, "Alice");
    private static final PlayerRef BOB = PlayerRef.of(BOB_UUID, "Bob");

    @TempDir
    static Path platformRoot;

    @BeforeAll
    static void installTemporaryPlatform() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return platformRoot.resolve("config");
            }

            @Override
            public Path gameDir() {
                return platformRoot.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    /** Animations are a global config read; every case puts the switch back. */
    @AfterEach
    void restoreAnimations() {
        AtomChatConfig.get().animationEnabled = true;
    }

    /**
     * The screen-side facts. The primitives only need to exist — the pill asks
     * them for their glyph, which needs no host.
     */
    private static final class Screen implements ScrollSyncService.Host {
        AppPage page = AppPage.WORLD_CHAT;
        PlayerRef partner;
        long dt = 16L;
        final DrawPrimitives primitives = new DrawPrimitives(new DrawPrimitives.Host() {
            @Override
            public float frameDt() {
                return 16.0F;
            }

            @Override
            public boolean emojiPanelOpen() {
                return false;
            }

            @Override
            public boolean phrasePanelOpen() {
                return false;
            }
        });

        @Override
        public long frameDt() {
            return dt;
        }

        @Override
        public AppPage topPage() {
            return page;
        }

        @Override
        public PlayerRef activePrivateTarget() {
            return partner;
        }

        @Override
        public DrawPrimitives primitives() {
            return primitives;
        }
    }

    private static NavPage world() {
        return NavPage.of(AppPage.WORLD_CHAT);
    }

    private static NavPage priv(PlayerRef target) {
        return NavPage.privateChat(target);
    }

    private static UiLayout layout() {
        return UiLayout.of(10.0F, 10.0F, 400.0F, 600.0F);
    }

    /**
     * A controller with something left to scroll: 4000px of content in a 600px
     * list, parked at the top (a fresh controller is bottom-anchored, which is
     * exactly the state the pill hides in).
     */
    private static ScrollController scrollable() {
        ScrollController controller = new ScrollController();
        controller.setContent(4000.0F, layout().list.h());
        controller.scrollTo(0.0F, false);
        return controller;
    }

    // ------------------------------------------------------- who gets what

    @Test
    void aPrivateConversationKeepsItsOwnController() {
        Screen screen = new Screen();
        ScrollSyncService service = new ScrollSyncService(screen);

        screen.page = AppPage.PRIVATE_CHAT;
        screen.partner = ALICE;
        ScrollController alice = service.currentScroll();
        assertSame(alice, service.currentScroll(), "the same partner keeps the same controller");

        screen.partner = BOB;
        ScrollController bob = service.currentScroll();
        assertNotSame(alice, bob, "another partner gets its own");

        screen.page = AppPage.WORLD_CHAT;
        assertSame(service.worldScroll(), service.currentScroll());
        assertNotSame(alice, service.currentScroll(), "the feed is not a private conversation");
    }

    @Test
    void aTransitionSideReadsItsOwnPageNotTheStackTop() {
        Screen screen = new Screen();
        screen.page = AppPage.WORLD_CHAT;
        ScrollSyncService service = new ScrollSyncService(screen);

        ScrollController alice = service.scrollForNav(priv(ALICE));
        assertNotSame(service.worldScroll(), alice,
                "the page being drawn picks its controller even though the feed is on top");
        assertSame(alice, service.scrollForNav(priv(ALICE)), "and keeps it across draws");

        assertSame(service.worldScroll(), service.scrollForNav(world()));
        assertSame(service.worldScroll(), service.scrollForNav(null));
    }

    @Test
    void listScrollFollowsTheTopPage() {
        Screen screen = new Screen();
        ScrollSyncService service = new ScrollSyncService(screen);

        screen.page = AppPage.CHAT_LIST;
        assertSame(service.rootScroll(), service.listScroll(), "a root page draws the root list");

        screen.page = AppPage.SETTINGS_SECTION;
        assertSame(service.detailScroll(), service.listScroll(), "a settings sub-page is a detail page");

        screen.page = AppPage.PROFILE_DETAIL;
        assertSame(service.detailScroll(), service.listScroll());
    }

    @Test
    void theMotionPushReachesControllersCreatedOnDemand() {
        AtomChatConfig.get().animationEnabled = false;
        Screen screen = new Screen();
        screen.page = AppPage.PRIVATE_CHAT;
        screen.partner = ALICE;
        ScrollSyncService service = new ScrollSyncService(screen);

        service.syncScrollMotion();
        ScrollController late = service.currentScroll();   // created after that push
        late.setContent(4000.0F, layout().list.h());
        service.syncScrollMotion();                        // the per-frame push picks it up

        // With decorative motion off a requested animation lands in one step; a
        // controller the push never reached would ease instead.
        late.scrollTo(500.0F, true);
        late.updateAnimation(System.currentTimeMillis());
        assertEquals(500.0F, late.getScrollY(), 0.01F,
                "the lazily created controller received the motion flag");
    }

    // ------------------------------------------------- the shared key convention

    @Test
    void theThreeStoresFileAConversationUnderTheSameKey() {
        assertEquals("", ConversationKeys.of(world()));
        assertEquals(ALICE.key(), ConversationKeys.of(priv(ALICE)));
        assertEquals("", ConversationKeys.of(null), "the strict form answers empty, it does not throw");

        ReplySlotStore replies = new ReplySlotStore();
        assertEquals(ConversationKeys.of(world()), replies.replyKeyForNav(world()));
        assertEquals(ConversationKeys.of(priv(ALICE)), replies.replyKeyForNav(priv(ALICE)));

        // The rule is name-based, so two refs that differ only in UUID are one
        // conversation for every store that files by it.
        PlayerRef sameName = PlayerRef.of(UUID.nameUUIDFromBytes("other".getBytes(StandardCharsets.UTF_8)), "Alice");
        Screen screen = new Screen();
        screen.page = AppPage.PRIVATE_CHAT;
        screen.partner = ALICE;
        ScrollSyncService service = new ScrollSyncService(screen);
        assertSame(service.scrollForNav(priv(ALICE)), service.scrollForNav(priv(sameName)),
                "the scroll controllers file them together");

        HistoryFoldStore folds = new HistoryFoldStore();
        assertSame(folds.historyWindowForNav(priv(ALICE), 500),
                folds.historyWindowForNav(priv(sameName), 500), "and so do the fold windows");
        assertSame(folds.historyWindowForNav(world(), 500), folds.historyWindowForNav(null, 500),
                "a null page lands on the feed's window, which is what the strict form means");
    }

    // ------------------------------------------------------------- the scrollbar

    @Test
    void theScrollbarThumbFollowsTheScrollAndTheFadeGate() {
        Screen screen = new Screen();
        ScrollSyncService service = new ScrollSyncService(screen);
        ScrollController controller = scrollable();
        UiLayout layout = layout();

        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            float trackX = layout.list.right() - UiTokens.s(6) / 2.0F;

            canvas.clear(0);
            service.drawScrollbar(canvas, layout, -10_000.0F, -10_000.0F, controller);
            assertEquals(0, drawnBytes(surface), "a pointer far from the track draws nothing");

            for (int i = 0; i < 6; i++) {
                settle(canvas, surface, () -> service.drawScrollbar(canvas, layout, trackX,
                        layout.list.y() + 50.0F, controller));
            }
            float[] low = pixelBounds(surface);
            assertTrue(drawnBytes(surface) > 0, "a pointer on the track fades the scrollbar in");
            assertTrue(low[2] - low[0] < UiTokens.s(20), "the idle thumb is the track's own width: " + Arrays.toString(low));

            controller.scrollTo(controller.getMaxScroll(), false);
            for (int i = 0; i < 6; i++) {
                settle(canvas, surface, () -> service.drawScrollbar(canvas, layout, trackX,
                        layout.list.y() + 50.0F, controller));
            }
            float[] high = pixelBounds(surface);
            assertTrue(high[1] > low[1] + 1.0F,
                    "the thumb moved down with the scroll: " + low[1] + " -> " + high[1]);

            // The band is the track column with 8px of slack on both sides.
            float midX = (high[0] + high[2]) / 2.0F;
            float midY = (high[1] + high[3]) / 2.0F;
            assertTrue(service.overScrollbarTrack(layout, midX, midY, controller),
                    "the middle of the painted thumb is on the track");
            assertFalse(service.overScrollbarTrack(layout, high[0] - UiTokens.s(8) - 2.0F, midY, controller),
                    "just past the slack is off the track");
            assertFalse(service.overScrollbarTrack(layout, midX, layout.list.y() - 2.0F, controller),
                    "and above the list is off it too");
        }
    }

    // ----------------------------------------------------------------- the FAB

    @Test
    void thePillIsSteppedOnlyByThePageThatOwnsTheComposer() {
        Screen screen = new Screen();
        ScrollSyncService service = new ScrollSyncService(screen);
        ScrollController controller = scrollable();
        UiLayout layout = layout();

        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            Runnable drawOwner = () -> service.drawJumpLatest(canvas, layout, -10_000.0F, -10_000.0F,
                    controller, true);
            Runnable drawOther = () -> service.drawJumpLatest(canvas, layout, -10_000.0F, -10_000.0F,
                    controller, false);

            canvas.clear(0);
            drawOther.run();
            assertEquals(0, drawnBytes(surface),
                    "the non-owning side draws nothing before the owner has faded the pill in");

            for (int i = 0; i < 30; i++) {
                settle(canvas, surface, drawOwner);
            }
            byte[] settled = pixels(surface);
            assertTrue(drawnBytes(surface) > 0, "the owning page draws the pill");

            byte[] redrawOne = frame(canvas, surface, drawOther);
            byte[] redrawTwo = frame(canvas, surface, drawOther);
            assertTrue(Arrays.equals(redrawOne, redrawTwo),
                    "the non-owning side only re-draws: two of its frames are identical");
            assertTrue(Arrays.equals(settled, redrawOne),
                    "and it draws exactly the state the owner left behind");

            service.pressJumpLatest();
            frame(canvas, surface, drawOwner);
            byte[] pressedByOwner = frame(canvas, surface, drawOther);
            assertFalse(Arrays.equals(settled, pressedByOwner),
                    "the owner's press stepped the spring, so both sides now draw a squashed pill");
            service.releaseJumpLatest();
        }
    }

    @Test
    void thePillHitBandMatchesThePixelsItDrew() {
        Screen screen = new Screen();
        ScrollSyncService service = new ScrollSyncService(screen);
        UiLayout layout = layout();
        screen.page = AppPage.WORLD_CHAT;
        ScrollController controller = service.worldScroll();
        controller.setContent(4000.0F, layout.list.h());
        controller.scrollTo(0.0F, false);

        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            for (int i = 0; i < 30; i++) {
                settle(canvas, surface, () -> service.drawJumpLatest(canvas, layout, -10_000.0F,
                        -10_000.0F, controller, true));
            }
            float[] bounds = pixelBounds(surface);
            float midX = (bounds[0] + bounds[2]) / 2.0F;
            float midY = (bounds[1] + bounds[3]) / 2.0F;

            assertTrue(service.overJumpLatest(layout, midX, midY),
                    "the middle of the painted pill is a hit");
            assertFalse(service.overJumpLatest(layout, bounds[0] - 4.0F, midY),
                    "a few pixels left of it is not");
            assertFalse(service.overJumpLatest(layout, midX, bounds[3] + 4.0F),
                    "nor below it");
        }
    }

    @Test
    void thePillHitTestReadsTheCurrentPageWhileThePillDrawsItsOwn() {
        Screen screen = new Screen();
        ScrollSyncService service = new ScrollSyncService(screen);
        UiLayout layout = layout();

        // The page being drawn is scrollable, the page on top of the stack is not.
        ScrollController drawnPage = scrollable();
        screen.page = AppPage.WORLD_CHAT;
        service.worldScroll().setContent(100.0F, layout.list.h());

        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            for (int i = 0; i < 30; i++) {
                settle(canvas, surface, () -> service.drawJumpLatest(canvas, layout, -10_000.0F,
                        -10_000.0F, drawnPage, true));
            }
            assertTrue(drawnBytes(surface) > 0, "the drawn page's pill is painted");
            assertFalse(service.overJumpLatest(layout, 100.0F, 100.0F),
                    "but the hit test asks the current page, which has nothing to scroll — "
                            + "a pre-existing seam, kept as it was");
        }
    }

    // ------------------------------------------------------------- helpers

    /** Draws once, then lets the wall clock move so the fade can advance a step. */
    private static void settle(Canvas canvas, Surface surface, Runnable draw) {
        canvas.clear(0);
        draw.run();
        sleep(20L);
    }

    private static byte[] frame(Canvas canvas, Surface surface, Runnable draw) {
        canvas.clear(0);
        draw.run();
        return pixels(surface);
    }

    private static byte[] pixels(Surface surface) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            return bitmap.readPixels();
        }
    }

    /** Bounding box of the painted pixels: [left, top, right, bottom], or an empty box. */
    private static float[] pixelBounds(Surface surface) {
        byte[] px = pixels(surface);
        int left = SURFACE_W;
        int top = SURFACE_H;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < SURFACE_H; y++) {
            for (int x = 0; x < SURFACE_W; x++) {
                int i = (y * SURFACE_W + x) * 4;
                if (px[i] != 0 || px[i + 1] != 0 || px[i + 2] != 0 || px[i + 3] != 0) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        return new float[]{left, top, right, bottom};
    }

    private static int drawnBytes(Surface surface) {
        int nonZero = 0;
        for (byte b : pixels(surface)) {
            if (b != 0) {
                nonZero++;
            }
        }
        return nonZero;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the fade to advance", e);
        }
    }
}
