package com.atom.chat.ui;

import io.github.humbleui.skija.Path;

/** Outline actions rendered with the same stroke treatment as the existing header icons. */
public final class EditorActionIcons {
    private EditorActionIcons() { }
    public static final Path MOVE = Path.makeFromSVGString(
            "M10 2 V18 M2 10 H18 M10 2 L7 5 M10 2 L13 5 M10 18 L7 15 M10 18 L13 15 M2 10 L5 7 M2 10 L5 13 M18 10 L15 7 M18 10 L15 13");
    public static final Path RESTORE = Path.makeFromSVGString(
            "M3 8 A7 7 0 1 1 4 15 M3 3 V8 H8");
}
