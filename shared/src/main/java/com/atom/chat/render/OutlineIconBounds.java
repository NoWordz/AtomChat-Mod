package com.atom.chat.render;

import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;
import java.util.IdentityHashMap;
import java.util.Map;

/** Tight curve bounds, cached on the UI/render thread; icon paths remain caller-owned. */
public final class OutlineIconBounds {
    private static final int MAX_ICONS = 256;
    private record Entry(int generation, Rect bounds) { }
    private static final Map<Path, Entry> BOUNDS = new IdentityHashMap<>();
    private OutlineIconBounds() { }
    public static Rect of(Path icon) {
        int generation = icon.getGenerationId();
        Entry entry = BOUNDS.get(icon);
        if (entry == null || entry.generation() != generation) {
            entry = new Entry(generation, icon.computeTightBounds());
            if (BOUNDS.size() >= MAX_ICONS) BOUNDS.clear();
            BOUNDS.put(icon, entry);
        }
        return entry.bounds();
    }
}
