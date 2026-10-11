package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DraftStore} offline: the per-page draft contract against a stub
 * composer, with no screen and no client anywhere in sight.
 *
 * <p>The stub is the point of the port. It records what the store asked the
 * composer to do, so the load path can be asserted without an {@code EditBox}:
 * a draft load is "set the text, then put the caret at the start" and both
 * halves are visible here.
 *
 * <p>Four properties are pinned because each has a failure mode the screen
 * cannot show you:
 * <ul>
 *   <li><b>page keys</b> — private chat keys by the partner, the world feed has
 *       its own string, anything else has no draft at all. Keying the feed by a
 *       player name is how one conversation's text shows up in another's box.</li>
 *   <li><b>save → load round trip</b> — what a page saved is what it gets back,
 *       caret at the start.</li>
 *   <li><b>no composer is not an empty draft</b> — a nav before {@code init()}
 *       must leave saved drafts alone; persisting "" there would wipe them.</li>
 *   <li><b>transition draws each side's own text</b> — the page that owns the
 *       field shows the live composer, the other shows its saved draft.</li>
 * </ul>
 */
class DraftStoreTest {
    private static final PlayerRef ALICE = PlayerRef.of(
            UUID.nameUUIDFromBytes("alice".getBytes(StandardCharsets.UTF_8)), "Alice");
    private static final PlayerRef BOB = PlayerRef.of(
            UUID.nameUUIDFromBytes("bob".getBytes(StandardCharsets.UTF_8)), "Bob");

    /** Stands in for the shared EditBox: records writes, hands back its text. */
    private static final class StubComposer implements DraftStore.ComposerTextPort {
        String text = "";
        int cursorMoves = 0;

        @Override
        public String get() {
            return text;
        }

        @Override
        public void set(String text) {
            this.text = text;
        }

        @Override
        public void moveCursorToStart() {
            cursorMoves++;
        }
    }

    private static NavPage world() {
        return NavPage.of(AppPage.WORLD_CHAT);
    }

    private static NavPage priv(PlayerRef target) {
        return NavPage.privateChat(target);
    }

    private static NavPage settings() {
        return NavPage.of(AppPage.SETTINGS);
    }

    @Test
    void draftsAreKeyedPerConversationAndTheFeedHasItsOwn() {
        DraftStore store = new DraftStore();
        store.savePrivateDraft(ALICE, "hi Alice");
        store.savePrivateDraft(BOB, "hi Bob");

        assertEquals("hi Alice", store.draftForNav(priv(ALICE), false, null));
        assertEquals("hi Bob", store.draftForNav(priv(BOB), false, null));
        // The feed is not a conversation: no key, no draft, until it saves one.
        assertEquals("", store.draftForNav(world(), false, null));
        // Pages that are not chats never get draft text.
        assertEquals("", store.draftForNav(settings(), false, null));
        assertEquals("", store.draftForNav(null, false, null));
        assertEquals("", store.draftForNav(NavPage.of(AppPage.PROFILE), false, null));
    }

    @Test
    void saveThenLoadRoundTripsTheTextAndParksTheCaretAtTheStart() {
        DraftStore store = new DraftStore();
        StubComposer composer = new StubComposer();
        composer.text = "half-written";
        store.saveCurrentDraft(AppPage.PRIVATE_CHAT, ALICE, composer);

        composer.text = "";
        store.loadDraft(ALICE, composer);

        assertEquals("half-written", composer.text);
        assertTrue(composer.cursorMoves >= 1, "a loaded draft opens with the caret at the start");
    }

    @Test
    void savingTheWorldFeedKeepsItOutOfThePrivateMap() {
        DraftStore store = new DraftStore();
        StubComposer composer = new StubComposer();
        composer.text = "hello world";
        store.saveCurrentDraft(AppPage.WORLD_CHAT, null, composer);

        assertEquals("hello world", store.draftForNav(world(), false, null));
        // Nothing leaked into a conversation, not even for a page that is not private chat.
        assertEquals("", store.draftForNav(priv(ALICE), false, null));
        store.loadWorldDraft(composer);
        assertEquals("hello world", composer.text);
    }

    @Test
    void savingOnANonChatPageWritesNothing() {
        DraftStore store = new DraftStore();
        StubComposer composer = new StubComposer();
        composer.text = "settings text";
        store.saveCurrentDraft(AppPage.SETTINGS, null, composer);

        assertEquals("", store.draftForNav(world(), false, null));
        assertEquals("", store.draftForNav(NavPage.of(AppPage.SETTINGS), false, null));
    }

    @Test
    void noComposerIsNotAnEmptyDraft() {
        DraftStore store = new DraftStore();
        StubComposer composer = new StubComposer();
        composer.text = "keep me";
        store.saveCurrentDraft(AppPage.PRIVATE_CHAT, ALICE, composer);

        // A nav can run before init() (or after disposal): no composer to read.
        store.saveCurrentDraft(AppPage.PRIVATE_CHAT, ALICE, null);
        assertEquals("keep me", store.draftForNav(priv(ALICE), false, null),
                "saving without a composer must not overwrite the saved draft with \"\"");

        // Loading without a composer is a no-op too, not a crash.
        store.loadDraft(ALICE, null);
        store.loadWorldDraft(null);
        assertEquals("keep me", store.draftForNav(priv(ALICE), false, null));
    }

    @Test
    void transitionDrawsTheLiveFieldForOneSideAndTheSavedDraftForTheOther() {
        DraftStore store = new DraftStore();
        StubComposer composer = new StubComposer();
        composer.text = "live text";
        store.savePrivateDraft(BOB, "bob draft");

        // Alice owns the field: she shows what is being typed, Bob shows his slot.
        assertEquals("live text", store.draftForNav(priv(ALICE), true, composer));
        assertEquals("bob draft", store.draftForNav(priv(BOB), false, composer));
        // And the ownership flag alone decides — the same composer is passed twice.
        assertEquals("", store.draftForNav(priv(ALICE), false, composer),
                "a page that does not own the field never shows the live text");
    }

    @Test
    void seededWorldDraftIsWhatTheWorldComposerOpensWith() {
        DraftStore store = new DraftStore();
        store.seedWorld("pre-filled");
        assertEquals("pre-filled", store.draftForNav(world(), false, null));

        StubComposer composer = new StubComposer();
        store.loadWorldDraft(composer);
        assertEquals("pre-filled", composer.text);
    }

    @Test
    void savingWithNoActivePartnerLeavesTheComposerToBeSavedLater() {
        DraftStore store = new DraftStore();
        StubComposer composer = new StubComposer();
        composer.text = "orphan";
        // PRIVATE_CHAT on top but no target resolved: nothing to file it under.
        store.saveCurrentDraft(AppPage.PRIVATE_CHAT, null, composer);

        assertEquals("", store.draftForNav(priv(ALICE), false, null));
        assertEquals("", store.draftForNav(world(), false, null));
        assertFalse(composer.text.isEmpty(), "the text stays in the composer, it is not destroyed");
    }
}