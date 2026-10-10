package com.atom.chat.history;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.LocalEcho;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.page.HistoryWindow;
import com.atom.chat.page.MessageListView;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HistoryFoldStore} offline: the per-conversation fold contract and the
 * reveal compensation, against a real {@link MessageListView} but no client.
 *
 * <p>The reveal needs the view's own row metrics (it scrolls by exactly the
 * height the reveal inserted), so it is driven through the real view here:
 * {@code requestLoadEarlier()} arms the one-shot request, and the store must
 * consume it, advance the window by one batch and compensate the scroll. What
 * stays on the real-machine probe list is only what needs a live client — the
 * button's click path through the screen's input handler.
 *
 * <p>Messages come from {@link LocalEcho#build} so this file never names a
 * Minecraft type (it lives in {@code shared/} and compiles on both mappings).
 */
class HistoryFoldStoreTest {
    private static final UUID OWN = UUID.nameUUIDFromBytes("owner".getBytes(StandardCharsets.UTF_8));
    private static final PlayerRef ALICE = PlayerRef.of(
            UUID.nameUUIDFromBytes("alice".getBytes(StandardCharsets.UTF_8)), "Alice");
    private static final PlayerRef BOB = PlayerRef.of(
            UUID.nameUUIDFromBytes("bob".getBytes(StandardCharsets.UTF_8)), "Bob");
    private static final float LIST_W = 400.0F;

    @TempDir
    static Path platformRoot;

    /**
     * The row metrics reach the font layer, which goes through {@link Platform}
     * for its paths — the same temporary provider the offscreen render tests
     * install, so this test never depends on the machine's real config.
     */
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

    private static NavPage world() {
        return NavPage.of(AppPage.WORLD_CHAT);
    }

    private static NavPage priv(PlayerRef target) {
        return NavPage.privateChat(target);
    }

    private static List<ChatMessage> feed(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(LocalEcho.build("line " + i, "line " + i, null, null, OWN, "Tester",
                    RichText.empty()));
        }
        return messages;
    }

    private static MessageListView view() {
        return new MessageListView(new MessageListView.Host() {
            @Override
            public UUID ownUuid() {
                return OWN;
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
    }

    private static UiLayout.Rect list() {
        return new UiLayout.Rect(0.0F, 0.0F, LIST_W, 600.0F);
    }

    @Test
    void aFreshWindowOpensOneBatchShortOfTheTotal() {
        HistoryFoldStore store = new HistoryFoldStore();
        HistoryWindow window = store.historyWindowForNav(world(), 500);
        // 500 rows with a 100-row batch: the newest 100 are visible, 400 stay folded.
        assertEquals(400, window.visibleStart(), "the initial window is one batch (100 rows)");
    }

    @Test
    void growthFollowsTheTotalWithoutUnfoldingWhatWasRevealed() {
        HistoryFoldStore store = new HistoryFoldStore();
        HistoryWindow window = store.historyWindowForNav(world(), 500);
        assertTrue(window.loadEarlier());
        assertEquals(300, window.visibleStart(), "one click reveals one more batch");

        HistoryWindow again = store.historyWindowForNav(world(), 520);
        assertSame(window, again, "the same conversation keeps its window");
        assertEquals(300, again.visibleStart(),
                "five new rows must not collapse an unfolded history back to the default window");
    }

    @Test
    void everyConversationKeepsItsOwnFoldDepth() {
        HistoryFoldStore store = new HistoryFoldStore();
        HistoryWindow alice = store.historyWindowForNav(priv(ALICE), 500);
        HistoryWindow bob = store.historyWindowForNav(priv(BOB), 500);
        HistoryWindow world = store.historyWindowForNav(world(), 500);
        assertNotSame(alice, bob);
        assertNotSame(alice, world);
        assertNotSame(bob, world);

        alice.loadEarlier();
        assertEquals(300, alice.visibleStart());
        assertEquals(400, bob.visibleStart(), "unfolding one conversation leaves the others alone");
        assertEquals(400, world.visibleStart(), "the feed is its own conversation");
        assertSame(bob, store.historyWindowForNav(priv(BOB), 500));
    }

    @Test
    void resetForgetsEveryConversation() {
        HistoryFoldStore store = new HistoryFoldStore();
        HistoryWindow before = store.historyWindowForNav(priv(ALICE), 500);
        before.loadEarlier();
        assertEquals(300, before.visibleStart());

        store.resetHistoryWindows();

        HistoryWindow after = store.historyWindowForNav(priv(ALICE), 500);
        assertNotSame(before, after, "a reset window is built fresh");
        assertEquals(400, after.visibleStart(), "the fold depth is forgotten, not restored");
    }

    @Test
    void aRevealConsumesTheRequestAndAdvancesTheWindow() {
        HistoryFoldStore store = new HistoryFoldStore();
        List<ChatMessage> messages = feed(500);
        HistoryWindow window = store.historyWindowForNav(world(), messages.size());
        MessageListView view = view();
        ScrollController scroll = new ScrollController();
        UiLayout.Rect list = list();
        // A real scroll range the compensation fits inside, so "the scroll moved
        // by exactly what was inserted" is asserted rather than a clamp.
        scroll.setContent(20000.0F, 600.0F);
        float before = 10.0F;   // "load earlier" is reached from near the top
        scroll.scrollTo(before, false);
        assertEquals(before, scroll.getScrollY(), 0.01F);

        view.requestLoadEarlier();
        store.applyLoadEarlierRequest(world(), messages, window, list, scroll, view);

        assertEquals(300, window.visibleStart(), "the click revealed exactly one batch");

        // ---- The compensation, as the code behaves since the fix ----
        //
        // The store asks for offsetBetween(newStart=300, oldStart=400): the range
        // that was inserted ABOVE the old top row, which is what the method's own
        // javadoc defines it to measure. It used to pass the same two indices the
        // other way round — a REVERSED range, which offsetBetween clamps away
        // (`limit = max(from, toIndex)`) so that it measured nothing and returned
        // 0. The scroll never moved and the list jumped when older rows were
        // revealed: the "anchor compensation" had never actually run.
        //
        // The two assertions that pinned the defect — "a reversed range measures
        // nothing" and "today the scroll does not move at all" — were removed
        // together with it. This is a deliberate behaviour change (a bug fix),
        // not a mechanical extraction, so the contract is now the intended one:
        float inserted = view.offsetBetween(messages, 300, 400, list.w());
        assertTrue(inserted > 0.0F,
                "the range that WAS revealed above the old top row has a real height");
        assertEquals(before + inserted, scroll.getScrollY(), 0.01F,
                "the reveal scrolls by exactly the height it inserted, it does not jump");

        // One-shot: the same request must not reveal twice.
        float after = scroll.getScrollY();
        store.applyLoadEarlierRequest(world(), messages, window, list, scroll, view);
        assertEquals(300, window.visibleStart());
        assertEquals(after, scroll.getScrollY(), 0.01F);
    }

    @Test
    void withoutARequestNothingMoves() {
        HistoryFoldStore store = new HistoryFoldStore();
        List<ChatMessage> messages = feed(500);
        HistoryWindow window = store.historyWindowForNav(world(), messages.size());
        MessageListView view = view();
        ScrollController scroll = new ScrollController();

        store.applyLoadEarlierRequest(world(), messages, window, list(), scroll, view);

        assertEquals(400, window.visibleStart(), "no armed request, no reveal");
        assertEquals(0.0F, scroll.getScrollY(), 0.01F);
    }

    @Test
    void aRevealAtTheTopOfTheHistoryDoesNotScroll() {
        HistoryFoldStore store = new HistoryFoldStore();
        List<ChatMessage> messages = feed(50);          // shorter than one batch
        HistoryWindow window = store.historyWindowForNav(world(), messages.size());
        assertEquals(0, window.visibleStart(), "a short history is fully visible from the start");
        MessageListView view = view();
        ScrollController scroll = new ScrollController();

        view.requestLoadEarlier();
        store.applyLoadEarlierRequest(world(), messages, window, list(), scroll, view);

        assertEquals(0, window.visibleStart());
        assertEquals(0.0F, scroll.getScrollY(), 0.01F, "nothing was inserted, so nothing moves");
    }
}