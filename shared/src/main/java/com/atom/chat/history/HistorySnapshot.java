package com.atom.chat.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Select references first; expensive, registry-dependent conversion is limited to the saved tail. */
public final class HistorySnapshot {
    public static final int MAX_ENTRIES = 500;

    private HistorySnapshot() {}

    public static <T, P, R> List<R> capture(List<T> publicMessages, List<P> partners,
                                         Function<P, List<T>> privateMessages, BiFunction<P, T, R> convert) {
        List<Selected<P, T>> selected = new ArrayList<>(MAX_ENTRIES);
        for (int p = partners.size() - 1; p >= 0 && selected.size() < MAX_ENTRIES; p--) {
            P partner = partners.get(p);
            List<T> messages = privateMessages.apply(partner);
            for (int i = messages.size() - 1; i >= 0 && selected.size() < MAX_ENTRIES; i--) {
                selected.add(new Selected<>(partner, messages.get(i)));
            }
        }
        for (int i = publicMessages.size() - 1; i >= 0 && selected.size() < MAX_ENTRIES; i--) {
            selected.add(new Selected<>(null, publicMessages.get(i)));
        }
        Collections.reverse(selected);
        List<R> result = new ArrayList<>(selected.size());
        for (Selected<P, T> item : selected) result.add(convert.apply(item.partner(), item.message()));
        return List.copyOf(result);
    }

    /** Keep loaded-before-live order and let the newest live items win at the cap. */
    public static <T> List<T> prepend(List<T> loaded, List<T> live) {
        int liveStart = Math.max(0, live.size() - MAX_ENTRIES);
        int loadedStart = Math.max(0, loaded.size() - (MAX_ENTRIES - (live.size() - liveStart)));
        List<T> result = new ArrayList<>(MAX_ENTRIES);
        result.addAll(loaded.subList(loadedStart, loaded.size()));
        result.addAll(live.subList(liveStart, live.size()));
        return List.copyOf(result);
    }

    private record Selected<P, T>(P partner, T message) {}
}
