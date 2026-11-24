package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-server-session private conversations. Each partner has an independent
 * message list (incoming + outgoing), unread counter, and latest preview/time.
 *
 * <p>Lifetime is intentionally scoped to the current server/world connection:
 * {@link #reset()} is called on disconnect/world leave. ChatHistory persists
 * original deliveries separately from the anti-spam display projection.
 */
public final class PrivateChatStore {
    public static final int MAX_MESSAGES_PER_CONVERSATION = 500;

    private static final Map<String, Conversation> CONVERSATIONS = new LinkedHashMap<>();
    /** The private page currently open on the AtomChat screen, if any. */
    private static volatile PlayerRef activePartner;

    private PrivateChatStore() {
    }

    private static final class Conversation {
        final PlayerRef partner;
        final List<ChatMessage> messages = new ArrayList<>();
        int unread;
        /** Immutable view of {@link #messages}; see {@link #messages(PlayerRef)}. */
        List<ChatMessage> published;
        List<ChatMessage> originals;
        boolean publishedAntiSpam;

        Conversation(PlayerRef partner) {
            this.partner = partner;
        }

        void changed() {
            published = null;
            originals = null;
            generation++;
        }
    }

    /**
     * Mutation counter across every conversation. The message list keys its
     * cached content height on this, so switching partners or receiving a
     * message re-measures while a repaint of the same rows does not.
     */
    private static volatile long generation;

    public static synchronized int deleteSignature(String signature, com.atom.chat.text.RichText marker) {
        int count=0;
        for(var conversation:CONVERSATIONS.values()) {
            boolean changed=false;
            for(int i=0;i<conversation.messages.size();i++) {
                var message=conversation.messages.get(i);
                if(!message.isDeleted() && signature.equals(message.nativeMeta().signature())) {
                    conversation.messages.set(i,message.asDeleted(marker));changed=true;count++;
                }
            }
            if(changed)conversation.changed();
        }
        return count;
    }

    public static synchronized boolean replace(PlayerRef partner, long id, ChatMessage corrected) {
        var c = CONVERSATIONS.get(partner.key());
        if (c == null) return false;
        for (int i = 0; i < c.messages.size(); i++) if (c.messages.get(i).getId() == id) {
            c.messages.set(i, corrected);
            c.changed();
            com.atom.chat.history.ChatHistory.markDirty();
            return true;
        }
        return false;
    }

    public static synchronized void reset() {
        CONVERSATIONS.clear();
        activePartner = null;
        generation++;
    }

    /** Clears only the active-page marker; messages remain for the session. */
    public static synchronized void clearActive() {
        activePartner = null;
    }

    public static synchronized void setActive(PlayerRef partner) {
        if (partner == null) {
            activePartner = null;
            return;
        }
        activePartner = partner;
        Conversation c = conversation(partner);
        c.unread = 0;
    }

    /**
     * Adds an incoming private message. Returns true when it was folded into an
     * identical previous message by anti-spam (no new row, no new unread).
     */
    public static synchronized boolean addIncoming(PlayerRef partner, ChatMessage message) {
        if (partner == null || message == null) {
            return false;
        }
        if(message.isDeleted()) return true;
        com.atom.chat.reaction.ReactionClient.bind(message);
        Conversation c = conversation(partner);
        boolean folded = MessageMerge.antiSpamEnabled() && !c.messages.isEmpty()
                && MessageMerge.canMerge(c.messages.get(c.messages.size() - 1), message);
        c.messages.add(message);
        c.changed();
        if (!folded) com.atom.chat.fun.ChatEffectEvents.publish(message, partner);
        com.atom.chat.history.ChatHistory.markDirty();
        if (c.messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            c.messages.remove(0);
        }
        if (!folded && !partner.equals(activePartner)) {
            c.unread++;
        }
        return folded;
    }

    /**
     * Adds an outgoing private message. Returns true when it was folded into an
     * identical previous message by anti-spam.
     */
    public static synchronized boolean addOutgoing(PlayerRef partner, ChatMessage message) {
        if (partner == null || message == null) {
            return false;
        }
        if(message.isDeleted()) return true;
        com.atom.chat.reaction.ReactionClient.bind(message);
        Conversation c = conversation(partner);
        boolean folded = MessageMerge.antiSpamEnabled() && !c.messages.isEmpty()
                && MessageMerge.canMerge(c.messages.get(c.messages.size() - 1), message);
        c.messages.add(message);
        c.changed();
        if (!folded) com.atom.chat.fun.ChatEffectEvents.publish(message, partner);
        com.atom.chat.history.ChatHistory.markDirty();
        if (c.messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            c.messages.remove(0);
        }
        return folded;
    }

    /**
     * Bulk insert used when a world's saved private history is loaded. Never
     * bumps the unread counter — reloaded messages are not new.
     */
    public static synchronized void addLoaded(PlayerRef partner, List<ChatMessage> loaded) {
        if (partner == null || loaded == null || loaded.isEmpty()) {
            return;
        }
        Conversation c = conversation(partner);
        c.messages.addAll(loaded);
        c.changed();
        while (c.messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            c.messages.remove(0);
        }
    }

    /** Prepend a late history load while retaining live unread counts and the active partner. */
    public static synchronized void prependLoaded(PlayerRef partner, List<ChatMessage> loaded) {
        if (partner == null || loaded == null || loaded.isEmpty()) return;
        Conversation conversation = conversation(partner);
        int room = MAX_MESSAGES_PER_CONVERSATION - conversation.messages.size();
        if (room <= 0) return;
        conversation.messages.addAll(0, loaded.subList(Math.max(0, loaded.size() - room), loaded.size()));
        conversation.changed();
    }

    /** Mutation counter across every conversation; see {@link Conversation#changed()}. */
    public static long generation() {
        return generation;
    }

    /** @return true when at least one private message exists with this partner. */
    public static synchronized boolean hasHistory(PlayerRef partner) {
        if (partner == null) {
            return false;
        }
        Conversation c = CONVERSATIONS.get(partner.key());
        return c != null && !c.messages.isEmpty();
    }

    public static synchronized List<ChatMessage> messages(PlayerRef partner) {
        if (partner == null) {
            return List.of();
        }
        Conversation c = CONVERSATIONS.get(partner.key());
        if (c == null || c.messages.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> published = c.published;
        boolean antiSpam = MessageMerge.antiSpamEnabled();
        if (published == null || c.publishedAntiSpam != antiSpam) {
            published = MessageMerge.project(rawMessages(partner), antiSpam);
            c.published = published;
            c.publishedAntiSpam = antiSpam;
        }
        return published;
    }

    /** Originals, never the display-only duplicate count row. */
    public static synchronized List<ChatMessage> rawMessages(PlayerRef partner) {
        Conversation c = partner == null ? null : CONVERSATIONS.get(partner.key());
        if (c == null || c.messages.isEmpty()) return List.of();
        if (c.originals == null) c.originals = Collections.unmodifiableList(new ArrayList<>(c.messages));
        return c.originals;
    }

    /** Latest private message with the partner, or null. */
    public static synchronized ChatMessage latest(PlayerRef partner) {
        if (partner == null) {
            return null;
        }
        Conversation c = CONVERSATIONS.get(partner.key());
        if (c == null || c.messages.isEmpty()) {
            return null;
        }
        return c.messages.get(c.messages.size() - 1);
    }

    public static synchronized int unread(PlayerRef partner) {
        if (partner == null) {
            return 0;
        }
        Conversation c = CONVERSATIONS.get(partner.key());
        return c == null ? 0 : c.unread;
    }

    public static synchronized void markRead(PlayerRef partner) {
        if (partner == null) {
            return;
        }
        Conversation c = conversation(partner);
        c.unread = 0;
    }

    public static synchronized int totalUnread() {
        int total = 0;
        for (Conversation c : CONVERSATIONS.values()) {
            total += c.unread;
        }
        return total;
    }

    /** All partners that have ever exchanged a private message this session. */
    public static synchronized List<PlayerRef> knownPartners() {
        List<PlayerRef> out = new ArrayList<>();
        for (Conversation c : CONVERSATIONS.values()) {
            if (!c.messages.isEmpty()) {
                out.add(c.partner);
            }
        }
        return out;
    }

    /** Partners that exchanged messages sorted by latest activity descending. */
    public static synchronized List<PlayerRef> knownPartnersByLatest() {
        List<PlayerRef> out = knownPartners();
        out.sort(Comparator.comparingLong((PlayerRef p) -> {
            ChatMessage m = latest(p);
            return m != null ? m.getTimestamp() : Long.MIN_VALUE;
        }).reversed());
        return out;
    }

    private static Conversation conversation(PlayerRef partner) {
        String key = partner.key();
        Conversation c = CONVERSATIONS.get(key);
        if (c == null) {
            c = new Conversation(partner);
            CONVERSATIONS.put(key, c);
        }
        return c;
    }
}
