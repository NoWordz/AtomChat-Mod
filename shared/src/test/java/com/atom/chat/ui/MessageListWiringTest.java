package com.atom.chat.ui;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.WorldFeedFilter;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.page.MessageListView;
import com.atom.chat.platform.Platform;
import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MessageListWiring}, offline: the per-page message lookup, the view's four
 * needs, the click/drag flow's release verdict and the hover span's page rule.
 *
 * <p><b>The span is a plain value here.</b> The wiring is generic over its span
 * type precisely so the click/drag flow can be driven without one: the real spans
 * carry a Minecraft style, a value no offline test can build. Every "span" below is
 * a string, and the click-event test is a stub fact.</p>
 *
 * <p><b>Scope, stated up front.</b> The message lists are marker instances handed
 * in by the stub, so the per-page routing and the D5 filter delegation are
 * asserted by identity; real message content cannot be built offline
 * ({@code ChatMessage} wants a chat component). NOT covered: the real view's hit
 * geometry, the vanilla tooltip render itself and the event routing that calls
 * these (D23) — the D22 probe list.</p>
 */
class MessageListWiringTest {
    private java.lang.reflect.Field provider, configInstance;
    private Object previousProvider, previousConfig;

    @BeforeEach
    void installTemporaryPlatform() throws Exception {
        provider = Platform.class.getDeclaredField("provider");
        configInstance = AtomChatConfig.class.getDeclaredField("instance");
        provider.setAccessible(true);
        configInstance.setAccessible(true);
        previousProvider = provider.get(null);
        previousConfig = configInstance.get(null);
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return tmp.resolve("config");
            }

            @Override
            public Path gameDir() {
                return tmp.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
        AtomChatConfig.reload();
    }

    @AfterEach void restoreState() throws Exception {
        provider.set(null, previousProvider);
        configInstance.set(null, previousConfig);
    }

    @TempDir
    Path tmp;

    private static final UUID SELF = new UUID(0x11L, 0x22L);
    private static final PlayerRef ALICE = PlayerRef.of(new UUID(0x33L, 0x44L), "Alice");
    private static final PlayerRef BOB = PlayerRef.of(new UUID(0x55L, 0x66L), "Bob");

    /** What the wiring asked of the screen, in order. */
    private static final class Recorder implements MessageListWiring.Host<String> {
        final List<String> calls = new ArrayList<>();
        final List<ChatMessage> worldSnapshot = new ArrayList<>();
        final List<ChatMessage> aliceMessages = new ArrayList<>();
        final List<ChatMessage> bobMessages = new ArrayList<>();
        final WorldFeedFilter feed = new WorldFeedFilter();
        String spanAtPoint;
        boolean clickEvent = true;
        int spanLookups;

        @Override
        public UUID ownUuid() {
            return SELF;
        }

        @Override
        public String ownName() {
            return "Me";
        }

        @Override
        public String messageSenderName(ChatMessage message) {
            calls.add("senderName:" + (message == null ? "null" : "msg"));
            return "sender";
        }

        @Override
        public long openStart() {
            return 4242L;
        }

        @Override
        public List<ChatMessage> worldSnapshot() {
            calls.add("worldSnapshot");
            return worldSnapshot;
        }

        @Override
        public List<ChatMessage> privateMessages(PlayerRef target) {
            calls.add("private:" + (target == null ? "null" : target.realName()));
            if (target == null) {
                return new ArrayList<>();
            }
            return ALICE.equals(target) ? aliceMessages : bobMessages;
        }

        @Override
        public WorldFeedFilter worldFeed() {
            return feed;
        }

        @Override
        public String clickableSpanAt(float vmx, float vmy) {
            calls.add("spanAt:" + (int) vmx + "," + (int) vmy);
            spanLookups++;
            return spanAtPoint;
        }

        @Override
        public boolean hasClickEvent(String span) {
            calls.add("hasClickEvent:" + span);
            return clickEvent;
        }
    }

    private Recorder host;
    private MessageListWiring<String> wiring;

    private void setUp() {
        host = new Recorder();
        wiring = new MessageListWiring<>(host);
    }

    // ------------------------------------------------------- 1. the view's host

    @Test
    void theViewHostAnswersTheListsFourQuestions() {
        setUp();
        MessageListView.Host viewHost = wiring.viewHost();
        assertEquals(SELF, viewHost.ownUuid());
        assertEquals("Me", viewHost.ownName());
        assertEquals(4242L, viewHost.openStart());
        assertEquals("sender", viewHost.senderName(null));
        assertTrue(host.calls.contains("senderName:null"), "the sender label is asked of the screen");
    }

    @Test
    void theViewHostIsBuiltOncePerCallAndIsIndependent() {
        setUp();
        assertNotSame(wiring.viewHost(), wiring.viewHost(),
                "each view build gets its own host object; neither holds state");
    }

    // --------------------------------------------------- 2. the page's messages

    @Test
    void theWorldPageIsTheFilteredSnapshot() {
        setUp();
        assertSame(host.worldSnapshot, wiring.messagesForNav(NavPage.of(AppPage.WORLD_CHAT)),
                "with nothing filtered the store's own list is handed straight back");
        assertTrue(host.calls.contains("worldSnapshot"), "and the snapshot came from the screen");
    }

    @Test
    void theWorldFilterIsAppliedToTheSnapshot() {
        setUp();
        host.feed.cycle();
        host.feed.cycle();
        List<ChatMessage> filtered = wiring.messagesForNav(NavPage.of(AppPage.WORLD_CHAT));
        assertNotSame(host.worldSnapshot, filtered,
                "a non-default filter produces its own list instead of handing the snapshot back");
        assertTrue(filtered.isEmpty(), "and with an empty snapshot that list is empty");
    }

    @Test
    void aPrivatePageIsThatConversationsOwnMessages() {
        setUp();
        assertSame(host.aliceMessages, wiring.messagesForNav(NavPage.privateChat(ALICE)),
                "Alice's conversation is Alice's list");
        assertSame(host.bobMessages, wiring.messagesForNav(NavPage.privateChat(BOB)),
                "and Bob's is Bob's — the two sides of a transition ask separately");
        assertTrue(host.calls.contains("private:Alice"));
        assertTrue(host.calls.contains("private:Bob"));
    }

    @Test
    void anyOtherPageHasNoList() {
        setUp();
        assertEquals(List.of(), wiring.messagesForNav(NavPage.of(AppPage.CHAT_LIST)));
        assertEquals(List.of(), wiring.messagesForNav(NavPage.of(AppPage.PROFILE)));
        assertEquals(List.of(), wiring.messagesForNav(null));
        assertTrue(host.calls.isEmpty(), "a page with no list asks the screen for nothing");
    }

    // ------------------------------------------------------- 3. the drag/click

    @Test
    void aPressArmsTheSpanUnderThePointer() {
        setUp();
        host.spanAtPoint = "link";
        wiring.armClickAt(10.0F, 20.0F);
        assertTrue(wiring.hasPendingClick());
        assertTrue(host.calls.contains("spanAt:10,20"), "the arm looks the span up itself");
    }

    @Test
    void aPressWithNothingUnderItArmsNothing() {
        setUp();
        host.spanAtPoint = null;
        wiring.armClickAt(10.0F, 20.0F);
        assertFalse(wiring.hasPendingClick());
    }

    @Test
    void theReleaseFiresOnlyForTheSameSpanWithNoDragAndAClickEvent() {
        setUp();
        host.spanAtPoint = "link";
        wiring.armClickAt(1.0F, 2.0F);
        assertTrue(wiring.shouldFireClick(wiring.takePending(), 1.0F, 2.0F),
                "same span, no drag, has a click event: fire");
    }

    @Test
    void aDragSuppressesTheClick() {
        setUp();
        host.spanAtPoint = "link";
        wiring.armClickAt(1.0F, 2.0F);
        wiring.markMoved();
        String pending = wiring.takePending();
        assertFalse(wiring.shouldFireClick(pending, 1.0F, 2.0F), "a drag suppresses the click-on-release");
    }

    @Test
    void aSpanWithoutAClickEventNeverFires() {
        setUp();
        host.spanAtPoint = "link";
        host.clickEvent = false;
        wiring.armClickAt(1.0F, 2.0F);
        assertFalse(wiring.shouldFireClick(wiring.takePending(), 1.0F, 2.0F),
                "a hover-only span must not fire a click");
    }

    @Test
    void aReleaseOverADifferentSpanDoesNotFire() {
        setUp();
        host.spanAtPoint = "link";
        wiring.armClickAt(1.0F, 2.0F);
        String pending = wiring.takePending();
        host.spanAtPoint = "other";
        assertFalse(wiring.shouldFireClick(pending, 1.0F, 2.0F),
                "the release only counts when the pointer is still on the same span");
    }

    @Test
    void aReleaseWithNoCandidateDoesNotFire() {
        setUp();
        host.spanAtPoint = "link";
        assertFalse(wiring.shouldFireClick(null, 1.0F, 2.0F), "no press, no click");
        assertFalse(wiring.shouldFireClick(wiring.takePending(), 1.0F, 2.0F),
                "and a taken empty candidate is null too");
    }

    @Test
    void theReleaseLooksTheSpanUpInTheSamePositionAsTheInlineCode() {
        setUp();
        host.spanAtPoint = "link";
        wiring.armClickAt(1.0F, 2.0F);
        host.calls.clear();
        wiring.shouldFireClick(wiring.takePending(), 7.0F, 8.0F);
        assertEquals(List.of("spanAt:7,8", "hasClickEvent:link"), host.calls,
                "the lookup happens before the verdict, and the click-event test after it");
    }

    @Test
    void clearingDropsTheCandidateAndTheDragFlag() {
        setUp();
        host.spanAtPoint = "link";
        wiring.armClickAt(1.0F, 2.0F);
        wiring.markMoved();
        wiring.clearClick();
        assertFalse(wiring.hasPendingClick(), "the candidate is gone (a later branch claimed the press)");
        assertNull(wiring.takePending(), "and taking it yields nothing");
        assertFalse(wiring.shouldFireClick(wiring.takePending(), 1.0F, 2.0F),
                "so the release has nothing left to fire");
    }

    @Test
    void theDragFlagSurvivesTheTakeAndIsClearedOnRelease() {
        setUp();
        host.spanAtPoint = "link";
        wiring.armClickAt(1.0F, 2.0F);
        wiring.markMoved();
        String pending = wiring.takePending();
        assertFalse(wiring.hasPendingClick(), "the release owns the candidate now");
        assertFalse(wiring.shouldFireClick(pending, 1.0F, 2.0F), "the drag flag still suppresses this release");

        wiring.clearMoved();
        wiring.armClickAt(1.0F, 2.0F);
        assertTrue(wiring.shouldFireClick(wiring.takePending(), 1.0F, 2.0F),
                "and the next press starts clean");
    }

    @Test
    void armingAgainForgetsThePreviousDrag() {
        setUp();
        host.spanAtPoint = "link";
        wiring.armClickAt(1.0F, 2.0F);
        wiring.markMoved();
        wiring.armClickAt(3.0F, 4.0F);
        assertTrue(wiring.shouldFireClick(wiring.takePending(), 3.0F, 4.0F),
                "a fresh press resets the drag flag, exactly like the inline pair did");
    }

    // ------------------------------------------------------- 4. the hover span

    @Test
    void theWorldPageAnswersTheSpanUnderThePointer() {
        setUp();
        host.spanAtPoint = "link";
        assertEquals("link", wiring.hoverSpan(true, 5.0F, 6.0F));
        assertTrue(host.calls.contains("spanAt:5,6"));
    }

    @Test
    void aRootPageNeverTakesASpan() {
        setUp();
        host.spanAtPoint = "link";
        assertNull(wiring.hoverSpan(false, 5.0F, 6.0F),
                "a root page keeps its own hover model: a stale world span must not show");
        assertTrue(host.calls.isEmpty(), "and the view is not even asked");
    }

    @Test
    void aWorldPageWithNothingUnderThePointerAnswersNothing() {
        setUp();
        host.spanAtPoint = null;
        assertNull(wiring.hoverSpan(true, 5.0F, 6.0F));
    }
}
