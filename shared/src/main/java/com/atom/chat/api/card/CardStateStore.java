package com.atom.chat.api.card;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side live state store for interactive cards keyed by {@code cardId}.
 *
 * <p>Uses a bounded LRU map so old states evict smoothly instead of wiping all
 * claimed cards, and bumps {@link #revision()} on every mutation so list caches
 * refresh on the next frame.
 */
public final class CardStateStore {
    private static final int MAX_ENTRIES = 512;
    private static final Map<String, CardState> STATES = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CardState> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });
    private static final AtomicLong REVISION = new AtomicLong();
    private static final Map<String, Boolean> DELIVERED = new LinkedHashMap<>(64, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public static synchronized void received(ShareCard card) {
        String cardId = card.cardId();
        if (cardId == null || cardId.isBlank()) return;
        DELIVERED.put(cardId, card.hasPrivateRedPacketPassword());
    }

    public static synchronized void updateFromServer(CardState state) {
        Boolean credential = DELIVERED.get(state.cardId());
        if (credential == null) return;
        update(credential ? state.hidePrivateRedPacketPassword() : state);
    }


    private CardStateStore() {
    }

    public static void update(CardState state) {
        if (state == null || state.cardId() == null || state.cardId().isBlank()) {
            return;
        }
        STATES.put(state.cardId(), state);
        REVISION.incrementAndGet();
    }

    public static CardState get(String cardId) {
        if (cardId == null || cardId.isBlank()) {
            return null;
        }
        return STATES.get(cardId);
    }

    public static long revision() {
        return REVISION.get();
    }

    public static synchronized void clear() {
        DELIVERED.clear();
        STATES.clear();
        REVISION.incrementAndGet();
    }
}
