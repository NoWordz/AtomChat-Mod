package com.atom.chat.render;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Associates capture with the actual vanilla entry, including a rewritten re-post. */
public final class ChatHudHistory<K, C, V> {
    private static final int DISPLAY_TICKS = 200;
    private static final int MAX_MERGED_ROWS = 100;
    private final Map<K, V> presentations = new IdentityHashMap<>();
    private final Map<V, Merged<V>> mergedRows = new IdentityHashMap<>();
    public record Merger<V>(BiPredicate<V, V> matches, BinaryOperator<V> combine) {}
    private record Merged<V>(V tail, V value) {}
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
        copyRecent(liveEntries, limit, vanillaFallback, receivedTick, currentTick, output, value -> true);
    }

    void copyRecent(List<K> liveEntries, int limit, Function<K, V> vanillaFallback,
                    ToIntFunction<K> receivedTick, int currentTick, List<V> output, Predicate<V> visible) {
        output.clear();
        for (int i = 0; i < liveEntries.size() && output.size() < limit; i++) {
            K entry = liveEntries.get(i);
            // Even a vanilla deletion marker keeps the original tick and its place in the list.
            if (receivedTick != null && currentTick - receivedTick.applyAsInt(entry) >= DISPLAY_TICKS) break;
            V presentation = presentation(entry, vanillaFallback);
            if (presentation != null && visible.test(presentation)) output.add(presentation);
        }
    }

    /** Fold consecutive live presentations without mutating vanilla entries or their expiry. */
    public void copyRecentMerged(List<K> liveEntries, int limit, Function<K, V> vanillaFallback,
                                 ToIntFunction<K> receivedTick, int currentTick, List<V> output, Merger<V> merger) {
        copyRecentMerged(liveEntries, limit, vanillaFallback, receivedTick, currentTick, output, merger, value -> true);
    }

    void copyRecentMerged(List<K> liveEntries, int limit, Function<K, V> vanillaFallback,
                          ToIntFunction<K> receivedTick, int currentTick, List<V> output, Merger<V> merger,
                          Predicate<V> visible) {
        output.clear();
        if (limit <= 0) return;
        V previous = null;
        for (K entry : liveEntries) {
            if (receivedTick != null && currentTick - receivedTick.applyAsInt(entry) >= DISPLAY_TICKS) break;
            V current = presentation(entry, vanillaFallback);
            if (current == null || !visible.test(current)) continue;
            if (previous != null && merger.matches().test(current, previous)) {
                int last = output.size() - 1;
                output.set(last, merge(current, output.get(last), merger.combine()));
            } else {
                if (output.size() >= limit) break;
                output.add(current);
            }
            previous = current;
        }
    }

    private V merge(V head, V tail, BinaryOperator<V> combine) {
        var cached = mergedRows.get(head);
        if (cached != null && cached.tail() == tail) return cached.value();
        V value = combine.apply(head, tail);
        if (mergedRows.size() >= MAX_MERGED_ROWS) mergedRows.clear();
        mergedRows.put(head, new Merged<>(tail, value));
        return value;
    }

    public void invalidate(java.util.function.Predicate<V> affected) {
        presentations.entrySet().removeIf(entry->entry.getValue()!=null && affected.test(entry.getValue()));
        mergedRows.clear();
        if(pendingPresentation!=null && affected.test(pendingPresentation))stage(null,null);
    }

    public void clear() {
        presentations.clear();
        mergedRows.clear();
        stage(null, null);
    }
}
