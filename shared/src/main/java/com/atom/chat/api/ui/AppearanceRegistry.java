package com.atom.chat.api.ui;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One typed ID space. Access is serialized with the shared revision in AppearanceUi. */
final class AppearanceRegistry<T> {
    private record Entry<T>(AppearanceUi.Option option, T value) {}

    private final Map<String, Entry<T>> entries = new TreeMap<>();
    private List<AppearanceUi.Option> options = List.of();

    void add(AppearanceUi.Option option, T value) {
        if (entries.putIfAbsent(option.id(), new Entry<>(option, value)) != null) {
            throw new IllegalArgumentException("Duplicate appearance ID for this part: " + option.id());
        }
        options = null;
    }

    boolean remove(String id) {
        if (entries.remove(id) == null) return false;
        options = null;
        return true;
    }

    T find(String id) {
        Entry<T> entry = id == null ? null : entries.get(id);
        return entry == null ? null : entry.value();
    }

    List<AppearanceUi.Option> options() {
        if (options == null) options = entries.values().stream().map(Entry::option).toList();
        return options;
    }
}
