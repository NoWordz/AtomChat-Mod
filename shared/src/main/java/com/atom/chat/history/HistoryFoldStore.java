package com.atom.chat.history;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ConversationKeys;
import com.atom.chat.nav.NavPage;
import com.atom.chat.page.HistoryWindow;
import com.atom.chat.page.MessageListView;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-conversation history fold depth: how far back each conversation has been
 * unfolded, and the reveal that follows a "load earlier" click.
 *
 * <p>One {@link HistoryWindow} per conversation, keyed the same way the reply
 * slots and scroll controllers are ({@code ""} for the public feed, the
 * partner's {@link com.atom.chat.chat.PlayerRef#key()} for a private chat) — a
 * shared key is how one conversation's fold depth would end up applied to
 * another's rows.
 *
 * <p>Deliberately not remembered across screen instances: closing the panel
 * forgets how far a conversation was unfolded, matching the public scroll reset.
 * {@link #resetHistoryWindows} is the explicit wipe (world switch, history
 * clear, disconnect).
 *
 * <p>The reveal is the subtle half. Revealing a batch inserts rows <em>above</em>
 * the row that was topmost, so the list would jump by exactly that height; the
 * store therefore asks the view how tall the inserted range is and scrolls by
 * it, keeping the previously visible content still. {@link MessageListView} is a
 * seam type every target provides (same name on both mappings), which is why
 * this can live in {@code shared/} — its own offset maths stay in the view,
 * where the row metrics live.
 */
public final class HistoryFoldStore {
    /** Rows revealed per "load earlier" click (and the initial visible window). */
    private static final int HISTORY_WINDOW_BATCH = 100;

    /** Per-conversation fold depth (world="", private=PlayerRef.key()). */
    private final Map<String, HistoryWindow> historyWindows = new HashMap<>();

    /** Fold window for a conversation, created on first view and kept across in-place growth. */
    public HistoryWindow historyWindowForNav(NavPage page, int total) {
        String key = ConversationKeys.of(page);
        HistoryWindow window = historyWindows.get(key);
        if (window == null) {
            window = new HistoryWindow(total, HISTORY_WINDOW_BATCH);
            historyWindows.put(key, window);
        } else {
            // Follow sends/evictions without collapsing an unfolded history.
            window.syncTotal(total);
        }
        return window;
    }

    /** Forgets every conversation's fold depth (world switch, history clear, disconnect). */
    public void resetHistoryWindows() {
        historyWindows.clear();
    }

    /**
     * Reveals one more batch when the button was clicked this frame, then
     * scrolls by exactly the height the reveal inserted above the row that was
     * topmost, so the previously visible content does not jump.
     *
     * @param view the message list the button lives in and the row metrics come
     *             from — passed in rather than held, because the screen owns it
     */
    public void applyLoadEarlierRequest(NavPage page, List<ChatMessage> messages, HistoryWindow window,
                                        UiLayout.Rect list, ScrollController scroll, MessageListView view) {
        if (!view.consumeLoadEarlierRequest()) {
            return;
        }
        int oldStart = window.visibleStart();
        if (!window.loadEarlier()) {
            return;
        }
        float inserted = view.offsetBetween(messages, oldStart, window.visibleStart(), list.w());
        scroll.scrollTo(scroll.getScrollY() + inserted, false);
    }
}