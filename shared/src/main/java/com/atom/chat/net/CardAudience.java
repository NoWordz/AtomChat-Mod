package com.atom.chat.net;

import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.ShareCard;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Remembers who received a card so later server updates follow the same audience. */
public final class CardAudience {
    private static final int HISTORY_LIMIT = 512;
    private static final Map<Object, LinkedHashMap<String, Map<UUID, Delivery>>> SERVERS = new WeakHashMap<>();

    public record Delivery(boolean system, boolean privatePassword) {
        public CardState applyPrivacy(CardState state) {
            return privatePassword ? state.hidePrivateRedPacketPassword() : state;
        }
    }

    private CardAudience() {}

    public static synchronized void delivered(Object server, UUID recipient, ShareCard card, boolean system) {
        String cardId = card.cardId();
        if (server == null || recipient == null || cardId == null || cardId.isBlank()) return;
        var history = SERVERS.computeIfAbsent(server, key -> new LinkedHashMap<>(16, 0.75F, true));
        history.computeIfAbsent(cardId, key -> new HashMap<>())
                .put(recipient, new Delivery(system, card.hasPrivateRedPacketPassword()));
        if (history.size() > HISTORY_LIMIT) history.remove(history.keySet().iterator().next());
    }

    public static synchronized Delivery find(Object server, UUID recipient, String cardId) {
        var history = SERVERS.get(server);
        if (history == null) return null;
        var recipients = history.get(cardId);
        return recipients == null ? null : recipients.get(recipient);
    }
}
