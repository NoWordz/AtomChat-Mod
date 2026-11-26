package com.atom.chat.api.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Preview API for changes to an ordered UI surface. Register edits during client
 * initialization. Within one surface, edit IDs are unique and determine the
 * order of conflicting edits, regardless of loader initialization order.
 */
public final class UiContributions<T> {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public record Entry<T>(String id, T value) {
        public Entry {
            requireId(id);
            Objects.requireNonNull(value);
        }
    }

    public record Position(Side side, String anchor) {
        public enum Side { FIRST, LAST, BEFORE, AFTER }

        public Position {
            Objects.requireNonNull(side);
            if (side == Side.BEFORE || side == Side.AFTER) requireId(anchor);
            else if (anchor != null) throw new IllegalArgumentException("Only relative positions take an anchor");
        }

        public static Position first() { return new Position(Side.FIRST, null); }
        public static Position last() { return new Position(Side.LAST, null); }
        public static Position before(String id) { return new Position(Side.BEFORE, id); }
        public static Position after(String id) { return new Position(Side.AFTER, id); }
    }

    private enum Kind { ADD, REMOVE, REPLACE, MOVE }

    private record Edit<T>(Kind kind, String target, T value, Position position) {}

    private final Map<String, Edit<T>> edits = new TreeMap<>();
    private volatile boolean hasEdits;
    private List<Entry<T>> cachedDefaults;
    private List<Entry<T>> cachedResult;

    public boolean hasEdits() {
        return hasEdits;
    }

    public synchronized void add(String id, T value, Position position) {
        put(id, new Edit<>(Kind.ADD, id, Objects.requireNonNull(value), Objects.requireNonNull(position)));
    }

    public synchronized void remove(String editId, String target) {
        put(editId, new Edit<>(Kind.REMOVE, target, null, null));
    }

    public synchronized void replace(String editId, String target, T value) {
        put(editId, new Edit<>(Kind.REPLACE, target, Objects.requireNonNull(value), null));
    }

    public synchronized void move(String editId, String target, Position position) {
        put(editId, new Edit<>(Kind.MOVE, target, null, Objects.requireNonNull(position)));
    }

    /**
     * Resolves an immutable list to share between layout, rendering and input.
     * Replacements keep the original slot ID. A missing anchor leaves the
     * element at its current position (at the end for a newly added entry).
     */
    public synchronized List<Entry<T>> resolve(List<Entry<T>> defaults) {
        if (edits.isEmpty()) return List.copyOf(defaults);
        if (cachedResult != null && cachedDefaults == defaults) return cachedResult;
        List<Entry<T>> entries = new ArrayList<>(defaults);
        Map<String, Position> positions = new LinkedHashMap<>();
        // Install all additions before processing edits, including edits supplied by another mod.
        for (Edit<T> edit : edits.values()) {
            if (edit.kind() == Kind.ADD && indexOf(entries, edit.target()) < 0) {
                entries.add(new Entry<>(edit.target(), edit.value()));
                positions.put(edit.target(), edit.position());
            }
        }
        for (Edit<T> edit : edits.values()) {
            if (edit.kind() == Kind.ADD) continue;
            int index = indexOf(entries, edit.target());
            if (index < 0) continue;
            switch (edit.kind()) {
                case REMOVE -> {
                    entries.remove(index);
                    positions.remove(edit.target());
                }
                case REPLACE -> entries.set(index, new Entry<>(edit.target(), edit.value()));
                case MOVE -> positions.put(edit.target(), edit.position());
                case ADD -> throw new IllegalStateException();
            }
        }
        Set<String> handled = new HashSet<>();
        for (String target : positions.keySet()) place(entries, positions, target, handled);
        cachedDefaults = List.copyOf(defaults);
        cachedResult = List.copyOf(entries);
        return cachedResult;
    }

    /** Removes a registered edit. Existing screen snapshots pick up changes when rebuilt. */
    public synchronized boolean unregister(String editId) {
        boolean removed = edits.remove(editId) != null;
        hasEdits = !edits.isEmpty();
        if (removed) cachedResult = null;
        return removed;
    }

    private void put(String editId, Edit<T> edit) {
        requireId(editId);
        requireId(edit.target());
        if (edits.putIfAbsent(editId, edit) != null) {
            throw new IllegalArgumentException("Duplicate UI edit ID: " + editId);
        }
        hasEdits = true;
        cachedResult = null;
    }

    private static <T> void place(List<Entry<T>> entries, Map<String, Position> positions,
                                  String target, Set<String> handled) {
        Position position = positions.get(target);
        if (position == null || !handled.add(target)) return;
        String anchor = position.anchor();
        if (anchor != null) {
            if (anchor.equals(target)) return;
            place(entries, positions, anchor, handled);
        }
        int source = indexOf(entries, target);
        int destination = switch (position.side()) {
            case FIRST -> 0;
            case LAST -> entries.size();
            case BEFORE -> indexOf(entries, anchor);
            case AFTER -> {
                int index = indexOf(entries, anchor);
                yield index < 0 ? index : index + 1;
            }
        };
        if (source >= 0 && destination >= 0) {
            Entry<T> entry = entries.remove(source);
            entries.add(destination > source ? destination - 1 : destination, entry);
        }
    }

    private static int indexOf(List<? extends Entry<?>> entries, String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    private static void requireId(String id) {
        if (!ID.matcher(Objects.requireNonNull(id)).matches()) {
            throw new IllegalArgumentException("Expected a namespaced UI ID: " + id);
        }
    }
}
