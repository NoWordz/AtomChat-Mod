package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatStore {
    private static final ChatStore INSTANCE = new ChatStore();
    private final List<ChatMessage> messages = new ArrayList<>();
    /**
     * Immutable view of {@link #messages}, rebuilt on the first read after a
     * mutation. The list view asks for a snapshot every frame (twice, for the
     * conversation-list preview), and copying up to 500 references each time was
     * pure churn; a published snapshot makes the steady state allocation-free.
     */
    private List<ChatMessage> published;
    /**
     * Bumped on every mutation. The message list keys its cached content height
     * on this, so a new or folded message re-measures while a plain repaint of
     * the same rows does not.
     */
    private static volatile long generation;
    private static volatile boolean publicActive;
    private static volatile int publicUnread;
    private static volatile int mentionUnread;

    public static ChatStore get() {
        return INSTANCE;
    }

    /**
     * Adds a public/feed message. Returns true when the message was folded into
     * the previous identical one by anti-spam (no new row, no new unread).
     */
    public synchronized boolean add(ChatMessage message) {
        if (message == null) {
            return false;
        }
        if (MessageMerge.antiSpamEnabled() && !messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            if (MessageMerge.canMerge(last, message)) {
                messages.set(messages.size() - 1, MessageMerge.merge(last, message));
                changed();
                com.atom.chat.history.ChatHistory.markDirty();
                return true;
            }
        }
        messages.add(message);
        changed();
        com.atom.chat.fun.ChatEffectEvents.publish(message);
        com.atom.chat.history.ChatHistory.markDirty();
        if (!message.isOwn() && !publicActive) {
            publicUnread++;
        }
        if (messages.size() > 500) {
            messages.remove(0);
        }
        return false;
    }

    /**
     * Bulk insert used when a world's saved history is loaded. Deliberately
     * never bumps the unread counters: yesterday's messages are not unread.
     */
    public synchronized void addLoaded(List<ChatMessage> loaded) {
        if (loaded == null || loaded.isEmpty()) {
            return;
        }
        messages.addAll(loaded);
        while (messages.size() > 500) {
            messages.remove(0);
        }
        changed();
    }

    /** Late disk history precedes live rows without clearing unread, active-page state, or effects. */
    public synchronized void prependLoaded(List<ChatMessage> loaded) {
        if (loaded == null || loaded.isEmpty()) return;
        int room = 500 - messages.size();
        if (room <= 0) return;
        messages.addAll(0, loaded.subList(Math.max(0, loaded.size() - room), loaded.size()));
        changed();
    }

    /** Whether any message is currently held (used by the clear-history action). */
    public synchronized boolean isEmpty() {
        return messages.isEmpty();
    }

    /** Counts an @-mention of the local player (badge on the Public card). */
    public static synchronized void noteMention() {
        mentionUnread++;
    }

    public static synchronized int mentionUnread() {
        return mentionUnread;
    }

    public synchronized List<ChatMessage> snapshot() {
        List<ChatMessage> current = published;
        if (current == null) {
            current = Collections.unmodifiableList(new ArrayList<>(messages));
            published = current;
        }
        return current;
    }

    /** Mutation counter for readers that cache derived geometry. */
    public static long generation() {
        return generation;
    }

    /** Invalidates the published snapshot and bumps the generation. Caller holds the monitor. */
    private void changed() {
        published = null;
        generation++;
    }

    /** Marks the public channel as the currently viewed page. */
    public static synchronized void setPublicActive(boolean active) {
        publicActive = active;
        if (active) {
            publicUnread = 0;
            mentionUnread = 0;
        }
    }

    public static synchronized int publicUnread() {
        return publicUnread;
    }

    public static synchronized void markPublicRead() {
        publicUnread = 0;
        mentionUnread = 0;
    }

    public static synchronized void resetUnread() {
        publicUnread = 0;
        mentionUnread = 0;
    }

    /** Clears all public history and unread when leaving a server/world. */
    public static synchronized void reset() {
        INSTANCE.messages.clear();
        INSTANCE.changed();
        publicActive = false;
        publicUnread = 0;
        mentionUnread = 0;
    }
}
