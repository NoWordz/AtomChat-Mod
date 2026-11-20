package com.atom.chat.render;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Associates capture with the actual vanilla entry, including a rewritten re-post. */
public final class ChatHudHistory<K, C, V> {
    private static final int DISPLAY_TICKS = 200;
    private final Map<K, V> presentations = new IdentityHashMap<>();
    private C pendingContent;
    private V pendingPresentation;

    public void stage(C content, V presentation) {
        pendingContent = content;
        pendingPresentation = presentation;
    }

    public void bind(K entry, C content, List<K> liveEntries) {
        if (pendingContent == null || pendingContent != content) return;
        presentations.put(entry, pendingPresentation);
        stage(null, null);
        if (presentations.size() > liveEntries.size()) {
            var live = Collections.newSetFromMap(new IdentityHashMap<K, Boolean>());
            live.addAll(liveEntries);
            presentations.keySet().retainAll(live);
        }
    }

    /** A stored null is a deliberately suppressed message. An unknown entry may be a deletion marker. */
    public V presentation(K entry, Function<K, V> vanillaFallback) {
        if (presentations.containsKey(entry)) return presentations.get(entry);
        return presentations.computeIfAbsent(entry, vanillaFallback);
    }

    /** Vanilla entries already run newest-first. Limit logical, unsuppressed messages. */
    public void copyRecent(List<K> liveEntries, int limit, Function<K, V> vanillaFallback, List<V> output) {
        copyRecent(liveEntries, limit, vanillaFallback, null, 0, output);
    }

    public void copyRecent(List<K> liveEntries, int limit, Function<K, V> vanillaFallback,
                           ToIntFunction<K> receivedTick, int currentTick, List<V> output) {
        output.clear();
        for (int i = 0; i < liveEntries.size() && output.size() < limit; i++) {
            K entry = liveEntries.get(i);
            // Even a vanilla deletion marker keeps the original tick and its place in the list.
            if (receivedTick != null && currentTick - receivedTick.applyAsInt(entry) >= DISPLAY_TICKS) break;
            V presentation = presentation(entry, vanillaFallback);
            if (presentation != null) output.add(presentation);
        }
    }

    public void clear() {
        presentations.clear();
        stage(null, null);
    }
}
