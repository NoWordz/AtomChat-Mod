package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.text.RichText;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReplySlotStore} offline: the per-conversation reply contract, with no
 * screen, no composer and no client anywhere in sight — which is the whole point
 * of the state living in {@code shared/}.
 *
 * <p>Messages come from {@link LocalEcho#build} (the same MC-free entry the other
 * shared tests use): this file must never name a Minecraft type, or it would only
 * compile on one mapping family.
 *
 * <p>Three properties are pinned here because each has a failure mode the screen
 * cannot show you:
 * <ul>
 *   <li><b>slot keys</b> — private chat keys by the partner, world chat is the
 *       empty string, anything else has no slot at all. A key collision is how
 *       one conversation's reply bar ends up in another's composer.</li>
 *   <li><b>sync writes back</b> — the live field belongs to the page that owns
 *       the composer; leaving a page persists it, and a consumed reply clears its
 *       slot (otherwise navigating back re-arms what was just sent).</li>
 *   <li><b>transition visibility</b> — both sides of a nav share one layout, so
 *       either side's armed reply must keep the row reserved.</li>
 * </ul>
 */
class ReplySlotStoreTest {
    private static final UUID OWN_UUID = UUID.nameUUIDFromBytes("owner".getBytes(StandardCharsets.UTF_8));
    private static final PlayerRef ALICE = PlayerRef.of(
            UUID.nameUUIDFromBytes("alice".getBytes(StandardCharsets.UTF_8)), "Alice");
    private static final PlayerRef BOB = PlayerRef.of(
            UUID.nameUUIDFromBytes("bob".getBytes(StandardCharsets.UTF_8)), "Bob");

    private static NavPage world() {
        return NavPage.of(AppPage.WORLD_CHAT);
    }

    private static NavPage priv(PlayerRef target) {
        return NavPage.privateChat(target);
    }

    private static NavPage settings() {
        return NavPage.of(AppPage.SETTINGS);
    }

    private static ChatMessage message(String text) {
        return LocalEcho.build(text, text, null, null, OWN_UUID, "Tester", RichText.empty());
    }

    @Test
    void slotKeysFollowTheConversationNotTheScreen() {
        ReplySlotStore store = new ReplySlotStore();
        assertEquals("", store.replyKeyForNav(world()));
        assertEquals(ALICE.key(), store.replyKeyForNav(priv(ALICE)));
        assertEquals(BOB.key(), store.replyKeyForNav(priv(BOB)));
        // No conversation, no slot: none of these may share the public feed's "".
        // (A private page can never be built without a target — NavPage rejects it —
        // so the "target == null" arm of the key rule is defensive only.)
        assertEquals("", store.replyKeyForNav(null));
        assertEquals("", store.replyKeyForNav(settings()));
    }

    @Test
    void syncPersistsTheLiveReplyIntoThePageThatOwnsTheComposer() {
        ReplySlotStore store = new ReplySlotStore();
        ChatMessage armed = message("quoted line");
        store.arm(armed);

        store.syncReplySlot(AppPage.PRIVATE_CHAT, priv(ALICE));

        assertTrue(store.replySlotArmed(priv(ALICE)), "Alice's page holds the reply now");
        assertFalse(store.replySlotArmed(priv(BOB)), "Bob's page must not see Alice's reply");
        assertFalse(store.replySlotArmed(world()), "nor the public feed");
        // Leaving and coming back re-arms exactly what was saved.
        store.disarm();
        store.loadReplyFor(priv(ALICE));
        assertSame(armed, store.replyTarget());
        assertSame(armed, store.replyForNav(priv(ALICE), true));
    }

    @Test
    void syncWithNothingArmedClearsThatPagesSlot() {
        ReplySlotStore store = new ReplySlotStore();
        store.arm(message("first"));
        store.syncReplySlot(AppPage.PRIVATE_CHAT, priv(ALICE));
        assertTrue(store.replySlotArmed(priv(ALICE)));

        // The send path: consume the reply, then sync — the slot must go with it,
        // or navigating away and back would re-arm a reply that was already sent.
        store.disarm();
        store.syncReplySlot(AppPage.PRIVATE_CHAT, priv(ALICE));
        assertFalse(store.replySlotArmed(priv(ALICE)), "a consumed reply must not survive in its slot");
        assertNull(store.replyForNav(priv(ALICE), false));
    }

    @Test
    void nonChatPagesHaveNoSlotToPersist() {
        ReplySlotStore store = new ReplySlotStore();
        store.arm(message("armed"));
        store.syncReplySlot(AppPage.SETTINGS, settings());
        assertFalse(store.replySlotArmed(settings()));
        assertNotNull(store.replyTarget(), "the live reply is untouched by a non-chat sync");
    }

    @Test
    void loadForANonChatPageDropsTheLiveReply() {
        ReplySlotStore store = new ReplySlotStore();
        store.arm(message("armed"));
        store.loadReplyFor(settings());
        assertNull(store.replyTarget());
        store.arm(message("armed again"));
        store.loadReplyFor(null);
        assertNull(store.replyTarget());
    }

    @Test
    void replyForNavReadsTheLiveFieldOnlyForThePageThatOwnsTheComposer() {
        ReplySlotStore store = new ReplySlotStore();
        ChatMessage alice = message("alice reply");
        store.arm(alice);
        store.syncReplySlot(AppPage.PRIVATE_CHAT, priv(ALICE));

        // Alice owns the composer: the live field.
        assertSame(alice, store.replyForNav(priv(ALICE), true));
        // Bob does not: his own slot only, which is empty.
        assertNull(store.replyForNav(priv(BOB), false));
        // World chat's slot is its own.
        assertNull(store.replyForNav(world(), false));

        store.arm(message("world reply"));
        store.syncReplySlot(AppPage.WORLD_CHAT, world());
        assertNotNull(store.replyForNav(world(), false));
        assertSame(alice, store.replyForNav(priv(ALICE), false), "Alice's slot is untouched by the feed");
    }

    @Test
    void eitherSideOfATransitionKeepsTheReplyRowVisible() {
        ReplySlotStore store = new ReplySlotStore();
        assertFalse(store.replyBarVisible(null, null), "nothing armed, nothing reserved");

        // Live field (the arriving page owns the composer).
        store.arm(message("live"));
        assertTrue(store.replyBarVisible(null, priv(ALICE)));

        // Slot only (the leaving page's bar is drawn from its slot mid-transition).
        ReplySlotStore slots = new ReplySlotStore();
        slots.arm(message("leaving"));
        slots.syncReplySlot(AppPage.PRIVATE_CHAT, priv(BOB));
        // The composer moved on: the live field is empty again, only the slot is armed.
        slots.disarm();
        assertFalse(slots.replyBarVisible(null, priv(ALICE)),
                "Alice's side is empty and Bob's slot is not on screen");
        assertTrue(slots.replyBarVisible(priv(BOB), priv(ALICE)),
                "the leaving side's armed slot still reserves the row");
        assertTrue(slots.replyBarVisible(priv(ALICE), priv(BOB)),
                "and so does the arriving side's");
    }

    @Test
    void clearAllWipesTheLiveReplyAndEverySlot() {
        ReplySlotStore store = new ReplySlotStore();
        store.arm(message("a"));
        store.syncReplySlot(AppPage.PRIVATE_CHAT, priv(ALICE));
        store.arm(message("b"));
        store.syncReplySlot(AppPage.WORLD_CHAT, world());

        store.clearAll();

        assertNull(store.replyTarget());
        assertFalse(store.replySlotArmed(priv(ALICE)));
        assertFalse(store.replySlotArmed(world()));
        assertFalse(store.replyBarVisible(priv(ALICE), world()));
    }
}