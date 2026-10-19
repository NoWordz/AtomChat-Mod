package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;

/**
 * Client-session owner for composer drafts shared by successive chat-screen
 * instances. The platform entry point resets it on JOIN and DISCONNECT; it is
 * deliberately memory-only and never reaches disk.
 */
public final class DraftSessionStore {
    private static DraftStore current = new DraftStore();
    private static long generation;

    private DraftSessionStore() {
    }

    public static synchronized DraftStore current() {
        return current;
    }

    /** Token a screen captures when it opens; reset invalidates late writers. */
    public static synchronized long generation() {
        return generation;
    }

    public static synchronized void reset() {
        current = new DraftStore();
        generation++;
    }

    /**
     * Saves a draft at a navigation/screen boundary. The screen's own store
     * always keeps the draft: turning off cross-screen restoration must not
     * break switching between pages inside the panel. Only the optional
     * connection-session copy is gated by {@code retainAcrossScreens}.
     */
    public static synchronized long saveForScreen(DraftStore screenDrafts,
                                                  AppPage page, PlayerRef target,
                                                  DraftStore.ComposerTextPort composer,
                                                  boolean retainAcrossScreens,
                                                  long screenGeneration) {
        screenDrafts.saveCurrentDraft(page, target, composer);
        // A screen that witnessed an earlier connection must never publish
        // into (or reset) the session that replaced it.
        if (screenGeneration != generation) {
            return -1L;
        }
        if (!retainAcrossScreens) {
            reset();
            return generation;
        }
        DraftStore session = current();
        if (session != screenDrafts) {
            session.saveCurrentDraft(page, target, composer);
        }
        return generation;
    }
}
