package com.atom.chat.reaction;

import java.util.*;

/**
 * Server-thread reaction ledger, keyed by the content key the reacting client
 * supplied.
 *
 * <p>It self-registers on the first action for a key: with client-computed
 * content keys there is no per-message delivery handshake to wait for, so a key
 * that has never been seen simply starts an empty entry. The server keeps no
 * audience and verifies no message identity — that is the deliberate
 * "transport, not arbiter" model that lets reactions work on servers whose chat
 * reaches the client as an unsigned system line. It still enforces the emoji
 * allowlist, the per-connection rate limit and the per-player / per-message /
 * per-emoji caps.</p>
 */
public final class ReactionLedger {
    private static final int MAX_MESSAGES = 4096;
    private record Window(long started, int count) {}

    private static final class Message {
        final LinkedHashMap<String, LinkedHashMap<UUID, ReactionWire.Member>> entries = new LinkedHashMap<>();
        long revision;
    }

    private final LinkedHashMap<UUID, Message> byKey = new LinkedHashMap<>();
    private final WeakHashMap<Object, Window> windows = new WeakHashMap<>();

    public String apply(UUID player, Object connection, String name, ReactionWire.Action action, long now) {
        Window window = windows.get(connection);
        if (window == null || now - window.started() >= 1000 || now < window.started()) window = new Window(now, 0);
        windows.put(connection, new Window(window.started(), window.count() + 1));
        if (window.count() >= 8) return "rate_limited";
        if (!ReactionEmoji.allowed(action.emoji())) return "invalid_emoji";
        Message message = byKey.computeIfAbsent(action.id(), ignored -> new Message());
        while (byKey.size() > MAX_MESSAGES) {
            var oldest = byKey.entrySet().iterator();
            oldest.next();
            oldest.remove();
        }
        var people = message.entries.get(action.emoji());
        if (action.active()) {
            if (people != null && people.containsKey(player)) return "";
            long own = message.entries.values().stream().filter(p -> p.containsKey(player)).count();
            if (own >= 8) return "per_player_limit";
            if (people == null) {
                if (message.entries.size() >= ReactionWire.MAX_KINDS) return "per_message_limit";
                people = new LinkedHashMap<>();
                message.entries.put(action.emoji(), people);
            }
            if (people.size() >= ReactionWire.MAX_MEMBERS) return "participant_limit";
            people.put(player, new ReactionWire.Member(player, name));
        } else {
            if (people == null || people.remove(player) == null) return "";
            if (people.isEmpty()) message.entries.remove(action.emoji());
        }
        message.revision++;
        return "";
    }

    public long revision(UUID key) {
        Message m = byKey.get(key);
        return m == null ? -1 : m.revision;
    }

    public ReactionWire.Snapshot snapshot(UUID key) {
        Message m = byKey.get(key);
        if (m == null) return null;
        List<ReactionWire.Entry> entries = new ArrayList<>();
        m.entries.forEach((emoji, people) -> entries.add(new ReactionWire.Entry(emoji, new ArrayList<>(people.values()))));
        return new ReactionWire.Snapshot(key, m.revision, entries);
    }
}
