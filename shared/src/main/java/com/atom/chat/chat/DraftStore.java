package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;

import java.util.HashMap;
import java.util.Map;

/**
 * Composer drafts, one per chat page: a map for the private conversations, a
 * single string for the public feed.
 *
 * <p>Why drafts are per page rather than one shared string: the screen owns a
 * single hidden {@code EditBox} (D21's, the only IME carrier), so the text in it
 * belongs to whichever page put it there. Push and pop load the arriving page's
 * draft into the field, which also means a nav transition draws the leaving and
 * the arriving composer side by side — {@link #draftForNav} answers for one of
 * them (the live field for the page that owns it, its saved text for the other)
 * rather than showing the same text twice.
 *
 * <p>The world draft is kept apart from the private map on purpose: the feed is
 * not a conversation and must never be keyed by a player name. The screen also
 * seeds it from the chat screen's own text ({@link #seedWorld}), which is how
 * the world composer opens pre-filled.
 *
 * <p>Mapping-neutral by construction: the screen passes in the facts it owns
 * ({@code topPage}, {@code activePrivateTarget}, {@code ownsInputField}) and a
 * {@link ComposerTextPort} for the shared field — that is what lets this live in
 * {@code shared/} and be tested with a stub composer instead of a client.
 */
public final class DraftStore {
    /**
     * The composer text the store reads and writes; the shell implements it over
     * its one shared {@code EditBox}.
     *
     * <p>A {@code null} port means "there is no composer right now" (before
     * {@code init()} / after disposal) — the same fact the screen used to test
     * with {@code input == null}. It has to stay distinguishable from an empty
     * draft: {@link #saveCurrentDraft} must do nothing without a composer, where
     * persisting the empty string would wipe the page's saved draft.
     */
    public interface ComposerTextPort {
        /** The composer's current text. */
        String get();

        /** Replaces the composer's text. */
        void set(String text);

        /** Puts the caret at the start, the way a freshly loaded draft opens. */
        void moveCursorToStart();
    }

    /** Saved draft per private conversation, keyed by {@link PlayerRef#key()}. */
    private final Map<String, String> privateDrafts = new HashMap<>();

    /** Draft for the public world channel; the feed has no player key to file under. */
    private String worldDraft = "";

    /**
     * Seeds the world draft — the screen's constructor hands over the text the
     * chat screen was opened with.
     */
    public void seedWorld(String text) {
        this.worldDraft = text;
    }

    /**
     * Draft of one nav page as it should be drawn: the live composer when that
     * page owns the field, else the page's saved draft, else nothing.
     *
     * @param ownsInputField whether {@code page} is the page holding the shared
     *                       composer (the caller's {@code ownsInputField(page)})
     * @param composer       the shared composer, or null when there is none
     */
    public String draftForNav(NavPage page, boolean ownsInputField, ComposerTextPort composer) {
        if (ownsInputField) {
            return composer != null ? composer.get() : "";
        }
        if (page != null && page.page() == AppPage.PRIVATE_CHAT && page.target() != null) {
            return privateDrafts.getOrDefault(page.target().key(), "");
        }
        return page != null && page.page() == AppPage.WORLD_CHAT ? worldDraft : "";
    }

    /**
     * Persists the composer's draft to the page that is currently open.
     *
     * <p>Without a composer this does nothing at all (see
     * {@link ComposerTextPort}) — writing the empty string instead would clear
     * the draft of whichever page is on top.
     *
     * @param topPage             the page kind on top of the nav stack
     * @param activePrivateTarget the private partner on top of the stack, or null
     */
    public void saveCurrentDraft(AppPage topPage, PlayerRef activePrivateTarget, ComposerTextPort composer) {
        if (composer == null) {
            return;
        }
        if (topPage == AppPage.WORLD_CHAT) {
            worldDraft = composer.get();
        } else if (topPage == AppPage.PRIVATE_CHAT) {
            if (activePrivateTarget != null) {
                privateDrafts.put(activePrivateTarget.key(), composer.get());
            }
        }
    }

    /** Saves {@code text} as {@code target}'s draft (used when a page is popped). */
    public void savePrivateDraft(PlayerRef target, String text) {
        if (target != null) {
            privateDrafts.put(target.key(), text);
        }
    }

    /** Loads {@code target}'s draft into the composer, caret at the start. */
    public void loadDraft(PlayerRef target, ComposerTextPort composer) {
        if (target == null) {
            return;
        }
        String draft = privateDrafts.getOrDefault(target.key(), "");
        if (composer != null) {
            composer.set(draft);
            composer.moveCursorToStart();
        }
    }

    /** Loads the world feed's draft into the composer, caret at the start. */
    public void loadWorldDraft(ComposerTextPort composer) {
        if (composer != null) {
            composer.set(worldDraft);
            composer.moveCursorToStart();
        }
    }
}