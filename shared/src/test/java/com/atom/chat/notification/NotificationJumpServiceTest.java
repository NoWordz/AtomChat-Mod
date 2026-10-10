package com.atom.chat.notification;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.LocalEcho;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.nav.AppPage;
import com.atom.chat.page.MessageListView;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotificationJumpService} offline: the jump's two routes and how they
 * verify themselves, the click rules that decide whether a banner is dismissed,
 * the draw layer's one hard requirement (the modal guard) and the feedback
 * queue that shares that layer.
 *
 * <p>The service was extracted with its cross-domain touches behind three
 * ports, so every branch can be driven here by a stub screen that records what
 * it was asked to do — no client, no window, no nav stack. The two clicks have
 * to actually hit a banner, so they go through a real render on a CPU raster
 * surface first: the banner fills its hit rectangles during the draw, and the
 * test then asks the banner's own hit test for a point on the send button and
 * one on the body — the same call the input path makes, so nothing about the
 * banner's geometry is re-implemented here.
 *
 * <p>Two timelines matter and are waited out rather than faked: a banner drops
 * in over {@code APPEAR_MS} and is skipped while its alpha is ~0, and an
 * acknowledgement fades in the same way. {@link #SETTLE_MS} is past both, so
 * the frame under test is the settled one.
 *
 * <p>Messages come from {@link LocalEcho#build} and the translator is a lambda,
 * so this file never names a Minecraft type (it lives in {@code shared/} and
 * compiles on both mappings).
 */
class NotificationJumpServiceTest {
    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;
    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;
    /** Past the banner's and the toast's drop-in, so both are drawn at full presence. */
    private static final long SETTLE_MS = 300L;

    private static final UUID ALICE_UUID = UUID.nameUUIDFromBytes("alice".getBytes(StandardCharsets.UTF_8));
    private static final UUID BOB_UUID = UUID.nameUUIDFromBytes("bob".getBytes(StandardCharsets.UTF_8));
    private static final PlayerRef ALICE = PlayerRef.of(ALICE_UUID, "Alice");
    /** Wall clock for a banner built by hand; the jump never reads it. */
    private static final long BORN = 1_800_000_000_000L;

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

    /**
     * The banner queue is a process-wide singleton: this class starts from an
     * empty stack and leaves one, so a leftover banner can neither be hit by
     * these clicks nor drift into another test's frame.
     */
    @BeforeEach
    @AfterEach
    void drainTheBannerStack() {
        for (int i = 0; i < 10; i++) {
            NotificationBanner.Active banner = NotificationBanner.INSTANCE.newest();
            if (banner == null) {
                return;
            }
            NotificationBanner.INSTANCE.dismiss(banner);
        }
    }

    // ------------------------------------------------------------- scenery

    /** The stub screen: the facts the service reads and the effects it asks for. */
    private static final class Screen implements NotificationJumpService.NavPort,
            NotificationJumpService.ScrollPort, NotificationJumpService.ComposerFocusPort {
        AppPage page = AppPage.SETTINGS;
        PlayerRef partner;
        /** False emulates a page selection that did not take. */
        boolean navigationTakes = true;
        boolean composerPresent;
        final List<PlayerRef> openedPrivate = new ArrayList<>();
        int worldOpens;
        List<ChatMessage> messages = List.of();
        final ScrollController scroll = new ScrollController();
        int scrollLookups;
        int focusWrites;

        @Override
        public AppPage topPage() {
            return page;
        }

        @Override
        public PlayerRef activePrivateTarget() {
            return partner;
        }

        @Override
        public void openPrivateChat(PlayerRef target) {
            openedPrivate.add(target);
            if (navigationTakes) {
                page = AppPage.PRIVATE_CHAT;
                partner = target;
            }
        }

        @Override
        public void openWorldChat() {
            worldOpens++;
            if (navigationTakes) {
                page = AppPage.WORLD_CHAT;
            }
        }

        @Override
        public List<ChatMessage> currentMessages() {
            return messages;
        }

        @Override
        public ScrollController currentScroll() {
            scrollLookups++;
            return scroll;
        }

        @Override
        public boolean composerPresent() {
            return composerPresent;
        }

        @Override
        public void focusComposer() {
            focusWrites++;
        }
    }

    private static final class Harness {
        final Screen screen = new Screen();
        final List<String> probes = new ArrayList<>();
        final MessageListView view = new MessageListView(new MessageListView.Host() {
            @Override
            public UUID ownUuid() {
                return ALICE_UUID;
            }

            @Override
            public String ownName() {
                return "Tester";
            }

            @Override
            public String senderName(ChatMessage message) {
                return message.isOwn() ? "Tester" : "Alice";
            }

            @Override
            public long openStart() {
                return 0L;
            }
        });
        final UiLayout layout = UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        final NotificationJumpService service = new NotificationJumpService(screen, screen, screen,
                (where, saveCountBefore, canvas) -> probes.add(where),
                key -> "t:" + key);
    }

    private static ChatMessage line(String content, UUID sender, String name) {
        return LocalEcho.build(content, content, null, null, sender, name, RichText.empty());
    }

    private static List<ChatMessage> feed(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(line("line " + i, ALICE_UUID, "Alice"));
        }
        return messages;
    }

    private static NotificationBanner.Active whisper(ChatMessage message) {
        return new NotificationBanner.Active(NotificationBanner.Type.WHISPER, RichText.empty(),
                RichText.empty(), BORN, message);
    }

    private static NotificationBanner.Active mention(ChatMessage message) {
        return new NotificationBanner.Active(NotificationBanner.Type.MENTION, RichText.empty(),
                RichText.empty(), BORN, message);
    }

    // ----------------------------------------------------------- the jump

    @Test
    void aWhisperWithoutAProfileNameNeverJumps() {
        Harness h = new Harness();
        ChatMessage nameless = line("psst", ALICE_UUID, null);

        assertFalse(h.service.jumpToNotification(whisper(nameless), h.layout, h.view),
                "PlayerRef.key() is name-based: there is no conversation to open");

        assertTrue(h.screen.openedPrivate.isEmpty(), "nothing was opened for a nameless sender");
        assertEquals(0, h.screen.scrollLookups, "and the locate step never ran");
    }

    @Test
    void aWhisperOpensTheConversationAndLandsOnTheMessage() {
        Harness h = new Harness();
        h.screen.messages = feed(40);
        ChatMessage target = h.screen.messages.get(30);

        assertTrue(h.service.jumpToNotification(whisper(target), h.layout, h.view));
        assertEquals(List.of(ALICE), h.screen.openedPrivate, "the sender's page is opened");
        assertEquals(AppPage.PRIVATE_CHAT, h.screen.page);
        assertEquals(1, h.screen.scrollLookups, "the located message needs that page's controller");

        float offset = h.view.offsetOf(h.screen.messages, 30, h.layout.list.w());
        float contentHeight = h.view.offsetOf(h.screen.messages, h.screen.messages.size(),
                h.layout.list.w());
        float maxScroll = Math.max(0.0F, contentHeight - h.layout.list.h());
        float want = Math.max(0.0F, Math.min(offset - h.layout.list.h() / 3.0F, maxScroll));
        assertTrue(maxScroll > 0.0F, "sanity: this feed is long enough to scroll");
        assertEquals(want, h.screen.scroll.getTarget(), 1e-3F,
                "the message sits a third of the way down the viewport");
    }

    @Test
    void aWhisperOnTheSameConversationDoesNotReopenIt() {
        Harness h = new Harness();
        h.screen.page = AppPage.PRIVATE_CHAT;
        h.screen.partner = ALICE;
        ChatMessage target = line("hi", ALICE_UUID, "Alice");
        h.screen.messages = List.of(target);

        assertTrue(h.service.jumpToNotification(whisper(target), h.layout, h.view));
        assertTrue(h.screen.openedPrivate.isEmpty(), "the page is already showing that partner");
    }

    @Test
    void aPageSelectionThatDidNotTakeLeavesTheBannerUp() {
        Harness h = new Harness();
        h.screen.navigationTakes = false;
        ChatMessage target = line("hi", ALICE_UUID, "Alice");
        h.screen.messages = List.of(target);

        assertFalse(h.service.jumpToNotification(whisper(target), h.layout, h.view),
                "an unchanged panel must not dismiss the banner");
        assertEquals(List.of(ALICE), h.screen.openedPrivate, "the open was attempted");
        assertEquals(0, h.screen.scrollLookups, "and there is nothing to scroll to");
    }

    @Test
    void aWorldNotificationOpensTheFeedOnlyWhenItIsNotAlreadyShowing() {
        Harness h = new Harness();
        ChatMessage target = line("hello world", ALICE_UUID, "Alice");
        h.screen.messages = List.of(target);

        assertTrue(h.service.jumpToNotification(mention(target), h.layout, h.view));
        assertEquals(1, h.screen.worldOpens, "the feed is opened when it is not showing");
        assertEquals(AppPage.WORLD_CHAT, h.screen.page);

        assertTrue(h.service.jumpToNotification(mention(target), h.layout, h.view));
        assertEquals(1, h.screen.worldOpens, "and not re-opened while it is");
    }

    @Test
    void aWorldJumpThatDidNotTakeFails() {
        Harness h = new Harness();
        h.screen.navigationTakes = false;
        ChatMessage target = line("hello world", ALICE_UUID, "Alice");
        h.screen.messages = List.of(target);

        assertFalse(h.service.jumpToNotification(mention(target), h.layout, h.view));
        assertEquals(1, h.screen.worldOpens, "the open was attempted");
    }

    @Test
    void aMessageThatLeftTheFeedDoesNotLandAndNeverAsksForAScroller() {
        Harness h = new Harness();
        h.screen.messages = feed(5);
        ChatMessage gone = line("this row is gone", ALICE_UUID, "Alice");

        assertFalse(h.service.jumpToNotification(mention(gone), h.layout, h.view));
        assertEquals(0, h.screen.scrollLookups,
                "the page's controller is created only once a target was located");
    }

    @Test
    void aMergedCopyIsStillTheJumpTarget() {
        Harness h = new Harness();
        ChatMessage stored = line("the same line", ALICE_UUID, "Alice");
        ChatMessage mergedCopy = line("the same line", ALICE_UUID, "Alice");
        h.screen.messages = List.of(line("first", ALICE_UUID, "Alice"), stored);

        assertTrue(h.service.jumpToNotification(mention(mergedCopy), h.layout, h.view),
                "anti-spam replaces the row object, so the jump falls back to the matching row");
    }

    // --------------------------------------------------------- the locate

    @Test
    void anIdentityMissFallsBackToTheLastMatchingRow() {
        ChatMessage first = line("hi", ALICE_UUID, "Alice");
        ChatMessage second = line("hi", ALICE_UUID, "Alice");
        ChatMessage queued = line("hi", ALICE_UUID, "Alice");

        assertEquals(1, NotificationJumpService.findMessageIndex(List.of(first, second), second),
                "the same object resolves to its own slot");
        assertEquals(1, NotificationJumpService.findMessageIndex(List.of(first, second), queued),
                "a merged copy resolves to the last same-content row");
        assertEquals(-1, NotificationJumpService.findMessageIndex(List.of(), queued));
    }

    @Test
    void anotherSendersIdenticalTextIsNotTheTarget() {
        ChatMessage fromAlice = line("hi", ALICE_UUID, "Alice");
        ChatMessage fromBob = line("hi", BOB_UUID, "Bob");

        assertEquals(-1, NotificationJumpService.findMessageIndex(List.of(fromAlice), fromBob),
                "sender identity is part of the match, not just the text");
    }

    // ----------------------------------------------------------- the focus

    @Test
    void theComposerIsFocusedOnlyWithAComposerAndAWritablePage() {
        Harness h = new Harness();

        h.service.focusComposerForReply(false);
        assertEquals(0, h.screen.focusWrites, "no composer (before init), nothing to focus");

        h.screen.composerPresent = true;
        h.service.focusComposerForReply(true);
        assertEquals(0, h.screen.focusWrites, "a read-only page (offline or blocked) keeps the focus");

        h.service.focusComposerForReply(false);
        assertEquals(1, h.screen.focusWrites, "a live composer on a writable page is focused");
    }

    // ----------------------------------------------------------- the click

    @Test
    void aRightClickIsNeverOurs() {
        Harness h = new Harness();
        assertFalse(h.service.onBannerClick(1, 100.0F, 100.0F, false, h.layout, h.view));
        assertEquals(0, h.screen.worldOpens + h.screen.openedPrivate.size(), "and nothing was opened");
    }

    @Test
    void thereIsNoClickToHandleWithoutABanner() {
        Harness h = new Harness();
        assertFalse(h.service.onBannerClick(0, 100.0F, 100.0F, false, h.layout, h.view));
    }

    @Test
    void aClickThatLandsDismissesTheBannerAndFocusesTheComposer() {
        Harness h = new Harness();
        ChatMessage target = line("hi", ALICE_UUID, "Alice");
        h.screen.messages = List.of(target);
        h.screen.composerPresent = true;
        NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.WHISPER, "Alice", "hi", target);
        settleAndRender(h);

        float[] button = sendButtonPoint(h.layout);
        assertTrue(h.service.onBannerClick(0, button[0], button[1], false, h.layout, h.view),
                "the click is consumed by the banner");
        assertFalse(NotificationBanner.INSTANCE.hasActive(), "a landed jump dismisses the banner");
        assertEquals(1, h.screen.focusWrites, "and the send button means reply now");
    }

    @Test
    void aClickThatCannotLandLeavesTheBannerUpAndTheComposerAlone() {
        Harness h = new Harness();
        ChatMessage target = line("hi", ALICE_UUID, "Alice");
        h.screen.messages = List.of();                 // the row is no longer in the feed
        h.screen.composerPresent = true;
        NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.WHISPER, "Alice", "hi", target);
        settleAndRender(h);

        float[] button = sendButtonPoint(h.layout);
        assertTrue(h.service.onBannerClick(0, button[0], button[1], false, h.layout, h.view),
                "the click is consumed either way: the banner owns it");
        assertTrue(NotificationBanner.INSTANCE.hasActive(),
                "a jump that did not land must leave the banner on screen");
        assertEquals(0, h.screen.focusWrites, "and must not steal the composer focus");
    }

    @Test
    void aClickOnTheBannerBodyJumpsWithoutFocusing() {
        Harness h = new Harness();
        ChatMessage target = line("hi", ALICE_UUID, "Alice");
        h.screen.messages = List.of(target);
        h.screen.composerPresent = true;
        NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.WHISPER, "Alice", "hi", target);
        settleAndRender(h);

        float[] body = bannerBodyPoint(h.layout);
        assertTrue(h.service.onBannerClick(0, body[0], body[1], false, h.layout, h.view));
        assertFalse(NotificationBanner.INSTANCE.hasActive(), "the body route dismisses on a landed jump");
        assertEquals(0, h.screen.focusWrites, "only the send button focuses the composer");
        assertEquals(1, h.screen.openedPrivate.size(), "and it jumped");
    }

    @Test
    void aClickThatHitsNothingIsPassedOn() {
        Harness h = new Harness();
        ChatMessage target = line("hi", ALICE_UUID, "Alice");
        h.screen.messages = List.of(target);
        NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.WHISPER, "Alice", "hi", target);
        settleAndRender(h);

        UiLayout.Rect panel = h.layout.rect();
        assertFalse(h.service.onBannerClick(0, panel.x() + 1.0F, panel.bottom() - 1.0F,
                        false, h.layout, h.view),
                "a point under no banner belongs to the next handler");
        assertTrue(NotificationBanner.INSTANCE.hasActive(), "and the banner stays up");
    }

    // ------------------------------------------------------------ the draw

    @Test
    void aModalHidesTheWholeLayer() {
        Harness h = new Harness();
        ChatMessage target = line("hi", ALICE_UUID, "Alice");
        h.screen.messages = List.of(target);
        NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.WHISPER, "Alice", "hi", target);
        h.service.feedback("atomchat.feedback.copied", true);
        sleep(SETTLE_MS);

        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0);
            h.service.drawNotificationBanners(canvas, true, h.layout, -10_000.0F, -10_000.0F);
            assertEquals(0, drawnBytes(surface),
                    "a modal owns the top layer: neither banners nor toasts may paint over it");

            canvas.clear(0);
            h.service.drawNotificationBanners(canvas, false, h.layout, -10_000.0F, -10_000.0F);
            assertTrue(drawnBytes(surface) > 0, "without the modal the layer paints");
        }
    }

    @Test
    void theDrawLeavesTheCanvasSaveStackBalancedAndProbesTheBannerRender() {
        Harness h = new Harness();
        ChatMessage target = line("hi", ALICE_UUID, "Alice");
        h.screen.messages = List.of(target);

        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            int baseline = canvas.getSaveCount();

            h.service.drawNotificationBanners(canvas, false, h.layout, -10_000.0F, -10_000.0F);
            assertTrue(h.probes.isEmpty(), "no banner, nothing to probe");

            NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.WHISPER, "Alice", "hi", target);
            sleep(SETTLE_MS);
            h.service.drawNotificationBanners(canvas, false, h.layout, -10_000.0F, -10_000.0F);

            assertEquals(List.of("banner render"), h.probes,
                    "the leak probe measures the banner render, where it always did");
            assertEquals(baseline, canvas.getSaveCount(), "the draw left the stack unbalanced");
        }
    }

    @Test
    void feedbackReachesTheToastThatSharesTheLayer() {
        Harness h = new Harness();
        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0);
            h.service.drawNotificationBanners(canvas, false, h.layout, -10_000.0F, -10_000.0F);
            assertEquals(0, drawnBytes(surface), "an empty feedback queue paints nothing");

            h.service.feedback("atomchat.feedback.copied", true);
            sleep(SETTLE_MS);
            canvas.clear(0);
            h.service.drawNotificationBanners(canvas, false, h.layout, -10_000.0F, -10_000.0F);
            assertTrue(drawnBytes(surface) > 0, "a raised acknowledgement draws as a toast");

            h.service.feedback("atomchat.feedback.poked", "Alice", false);
            sleep(SETTLE_MS);
            canvas.clear(0);
            h.service.drawNotificationBanners(canvas, false, h.layout, -10_000.0F, -10_000.0F);
            assertTrue(drawnBytes(surface) > 0, "the labelled variant draws too");
        }
    }

    // ------------------------------------------------------------- helpers

    /** Waits out the banner's drop-in, then draws one settled frame so its hit rectangles exist. */
    private static void settleAndRender(Harness h) {
        sleep(SETTLE_MS);
        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            h.service.drawNotificationBanners(surface.getCanvas(), false, h.layout,
                    -10_000.0F, -10_000.0F);
        }
    }

    /**
     * A point on the newest banner's send button, found through the banner's own
     * hit test — the geometry is the banner's business, the test only needs an
     * address to click. The scan band has slack on every side: a banner mid-drop
     * sits outside its resting rect.
     */
    private static float[] sendButtonPoint(UiLayout layout) {
        UiLayout.Rect panel = layout.rect();
        for (float x = panel.right() + 64.0F; x > panel.x() - 64.0F; x -= 1.0F) {
            for (float y = panel.y() - 64.0F; y < panel.bottom() + 64.0F; y += 1.0F) {
                if (NotificationBanner.INSTANCE.buttonHit(x, y) != null) {
                    return new float[]{x, y};
                }
            }
        }
        throw new IllegalStateException("no send button on screen — was the banner rendered?");
    }

    /** A point on the newest banner that is not its send button. */
    private static float[] bannerBodyPoint(UiLayout layout) {
        UiLayout.Rect panel = layout.rect();
        for (float x = panel.x() - 64.0F; x < panel.right() + 64.0F; x += 1.0F) {
            for (float y = panel.y() - 64.0F; y < panel.bottom() + 64.0F; y += 1.0F) {
                if (NotificationBanner.INSTANCE.hitTest(x, y) != null
                        && NotificationBanner.INSTANCE.buttonHit(x, y) == null) {
                    return new float[]{x, y};
                }
            }
        }
        throw new IllegalStateException("no banner body on screen — was the banner rendered?");
    }

    /** Non-zero bytes in the surface's pixels, cleared before the frame under test. */
    private static int drawnBytes(Surface surface) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            byte[] pixels = bitmap.readPixels();
            int nonZero = 0;
            for (byte b : pixels) {
                if (b != 0) {
                    nonZero++;
                }
            }
            return nonZero;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the frame to settle", e);
        }
    }
}
