package com.atom.chat.ui;

import io.github.humbleui.skija.Path;

/** Hand-written settings glyphs on the shell's 20-unit grid. Render only through DrawPrimitives. */
public final class SettingsGroupGlyphs {
    private SettingsGroupGlyphs() { }
    public static final Path EXPERIMENTAL = Path.makeFromSVGString("M3 13C0 8 7 3 13 4C19 5 19 10 13 14C7 18 4 17 3 13Z M7 3C12 0 17 7 16 13C15 19 10 19 6 13C2 7 3 4 7 3Z M10 8V12 M8 10H12 M16 1V5 M14 3H18");
    public static final Path TRASH = Path.makeFromSVGString("M7 3H13 M3 5H17 M5 5L6 17H14L15 5 M8 8V14 M12 8V14");
    public static final Path BANNER = Path.makeFromSVGString(
            "M5 15A3 3 0 0 1 2 12V8A3 3 0 0 1 5 5H15A3 3 0 0 1 18 8V12A3 3 0 0 1 15 15H9 "
                    + "M7 8.5A1.5 1.5 0 1 0 7 11.5A1.5 1.5 0 1 0 7 8.5 M11 8.5H15 M11 11.5H14");
    public static final Path SPEAKER = Path.makeFromSVGString("M3 8H6L11 4V16L6 12H3Z M14 7C16 8.5 16 11.5 14 13 M16 4.5C20 7.5 20 12.5 16 15.5");
    public static final Path CLICK = Path.makeFromSVGString("M4 3L7 17L10 12L15 10Z M12 2V4 M16 4L14.5 5.5 M18 8H16");
    public static final Path KEYBOARD = Path.makeFromSVGString("M3 5H17V15H3Z M6 8H6.2 M10 8H10.2 M14 8H14.2 M6 11H6.2 M9 11H14");
    public static final Path MESSAGE_LIST = Path.makeFromSVGString("M3 3H17V17H3Z M6 7H14 M6 10H14 M6 13H11");
    public static final Path TOUCH = Path.makeFromSVGString("M8 11V4A2 2 0 0 1 12 4V10 M12 7H14A3 3 0 0 1 17 10V14A4 4 0 0 1 13 18H9A3 3 0 0 1 6.6 16.8L3 12A1.8 1.8 0 0 1 5.7 9.7L8 12");
    public static final Path SPARKLES = Path.makeFromSVGString("M10 2L12.3 7.7L18 10L12.3 12.3L10 18L7.7 12.3L2 10L7.7 7.7Z M16 2V6 M14 4H18");
    public static final Path MOTION = Path.makeFromSVGString("M3 6H9 M2 10H6 M3 14H9 M11 4L17 10L11 16");
    public static final Path LAYOUT = Path.makeFromSVGString("M3 3H17V17H3Z M3 7H17 M8 7V17");
    public static final Path OPACITY = Path.makeFromSVGString("M3 3H12V12H3Z M8 8H17V17H8Z");
    public static final Path PRESETS = Path.makeFromSVGString(
            "M10 7H6A3 3 0 0 0 3 10V14A3 3 0 0 0 6 17H12A3 3 0 0 0 15 14V11 "
                    + "M8 4A3 3 0 0 1 10 3H15A3 3 0 0 1 18 6V11");
    public static final Path CORNERS = Path.makeFromSVGString("M3 17V10A7 7 0 0 1 10 3H17 M3 3H6 M3 3V6 M14 17H17V14");
    public static final Path MESSAGE_COLORS = Path.makeFromSVGString("M6 3L15 12L8 19L1 12Z M1 12H15 M4 1L7 4 M17 11C17 11 19 14 19 15A2 2 0 0 1 15 15C15 14 17 11 17 11");
    public static final Path PALETTE = Path.makeFromSVGString("M10 2C5 2 2 5.2 2 10C2 14.5 5.3 18 9 18H11C12.5 18 13.2 16.5 12.2 15.4C11.3 14.4 12 13 13.5 13H15C17.2 13 18 11.5 18 9.5C18 5.5 14.7 2 10 2Z M6 7H6.2 M10 5H10.2 M14 7H14.2 M6 11H6.2");
    public static final Path HIDDEN = Path.makeFromSVGString("M2 10C5 5 8 4 10 4C14 4 16 6 18 10C16 14 14 16 10 16C6 16 4 14 2 10Z M2 2L18 18 M8 8A3 3 0 0 0 12 12");
    public static final Path PLUG = Path.makeFromSVGString("M7 2V6 M13 2V6 M5 6H15V9A5 5 0 0 1 5 9Z M10 14V18 M7 18H10");
}
