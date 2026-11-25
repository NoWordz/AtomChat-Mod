package com.atom.chat.api.card;

import java.util.LinkedHashMap;
import java.util.Map;

/** Spread retries for previews waiting for adjacent chunks, without caching their absence. */
public final class MinimapRetry {
    private static final long RETRY_NS = 350_000_000L;
    private static final int CACHE_LIMIT = 96;
    private final Map<String, Long> attempted = new LinkedHashMap<>();

    public boolean begin(String key, long now) {
        Long last = attempted.get(key);
        if (last != null && now - last < RETRY_NS) return false;
        attempted.remove(key);
        attempted.put(key, now);
        if (attempted.size() > CACHE_LIMIT) attempted.remove(attempted.keySet().iterator().next());
        return true;
    }

    public void clear() { attempted.clear(); }
}
