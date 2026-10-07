package com.atom.chat.ui;

import io.github.humbleui.skija.Path;

/**
 * Shared shell icon paths (bottom tabs, header back affordance and the
 * conversation-list globe).
 *
 * <p>All icons use a hand-written 20x20 line-icon language. The paths are
 * drawn as clean geometric line work with rounded joins/caps at render time:
 * consistent optical weight, no filled silhouettes, no tool exports.</p>
 *
 * <p>The globe deliberately follows the earlier Lucide-style globe that tested
 * well: a circle with a horizontal equator and an elliptical meridian, which
 * reads as a globe at small sizes better than a plain inner circle.</p>
 */
public final class AppIcons {
    private static final String ICON_BACK_SVG = "M4 10 L10 4 M4 10 L10 16 M4 10 L18 10";

    // Rounded chat bubble: softer corners than the old square bubble, with no
    // interior text lines so the glyph stays calm and perfectly centred at
    // larger tab-icon sizes.
    private static final String ICON_TAB_CHAT_SVG =
            "M5 3 h8 a2 2 0 0 1 2 2 v5 a2 2 0 0 1 -2 2 h-4 l-3 3 v-3 h-1 a2 2 0 0 1 -2 -2 v-5 a2 2 0 0 1 2 -2 z";
    // User: a larger head and a softer shoulder curve; no outer circle so it
    // stays in the same open line language as the rest of the shell.
    private static final String ICON_TAB_PROFILE_SVG =
            "M10 3.2 a3.4 3.4 0 1 1 0 6.8 a3.4 3.4 0 1 1 0 -6.8"
            + " M4.5 16.8 C4.5 13.6 6.9 11.8 10 11.8 C13.1 11.8 15.5 13.6 15.5 16.8";
    // Settings: Lucide's proper gear (ISC-licensed, no-copyright line art).
    // It has real gear teeth instead of a sun/ray look.
    private static final String ICON_TAB_SETTINGS_SVG =
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6"
            + " M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0"
            + "l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2"
            + " 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06"
            + "a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82"
            + " 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0"
            + " 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0"
            + "l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2"
            + " 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06"
            + "a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9"
            + "a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z";
    // The good historical globe: outer circle + equator + elliptical meridian.
    private static final String ICON_GLOBE_SVG =
            "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20"
            + " M2 12h20"
            + " M12 2a15.3 15.3 0 0 1 4 10a15.3 15.3 0 0 1-4 10a15.3 15.3 0 0 1-4-10a15.3 15.3 0 0 1 4-10z";

    // Feed filter button, ALL state: a plain funnel — the affordance that a
    // filter lives here while nothing is currently filtered.
    private static final String ICON_FILTER_SVG =
            "M4 4.6 L16 4.6 L11.6 9.8 L11.6 14.2 L8.4 16 L8.4 9.8 Z";
    // Feed filter button, SYSTEM state: an announcement speaker with one sound
    // arc — server broadcasts read as announcements, not as chat bubbles.
    private static final String ICON_ANNOUNCE_SVG =
            "M3.6 8.2 L6.2 8.2 L11.4 4.9 L11.4 15.1 L6.2 11.8 L3.6 11.8 Z"
            + " M14.6 7.6 A3.7 3.7 0 0 1 14.6 12.4";

    // Settings home tiles. Same 20x20 open line language as the shell icons,
    // just rendered larger; each glyph must read at a glance behind a label.
    // Appearance: three adjustment sliders — the section is about tuning the
    // look, and the palette dots were too faint to survive the tile size.
    private static final String ICON_SETTINGS_APPEARANCE_SVG =
            "M3 4.5h8.8 M16.2 4.5h1.8"
            + " M15 2.8a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0 -3.4"
            + " M3 10h2.3 M9.3 10h8.7"
            + " M7.5 8.3a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0 -3.4"
            + " M3 15.5h8.8 M16.2 15.5h1.8"
            + " M15 13.8a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0 -3.4";
    // Privacy: a shield outline, the universal "protected" silhouette.
    private static final String ICON_SETTINGS_PRIVACY_SVG =
            "M12 3l7 2.8v4.6c0 4.2-2.9 7.7-7 9.1c-4.1-1.4-7-4.9-7-9.1V5.8z";
    // About: an information circle; the dot is a short round-capped dash so it
    // survives stroke-width scaling at every size.
    private static final String ICON_SETTINGS_ABOUT_SVG =
            "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18"
            + " M12 7.2v1.2"
            + " M12 10.6v5.4";

    public static final Path ICON_BACK_PATH = Path.makeFromSVGString(ICON_BACK_SVG);
    public static final Path ICON_TAB_CHAT_PATH = Path.makeFromSVGString(ICON_TAB_CHAT_SVG);
    public static final Path ICON_TAB_PROFILE_PATH = Path.makeFromSVGString(ICON_TAB_PROFILE_SVG);
    public static final Path ICON_TAB_SETTINGS_PATH = Path.makeFromSVGString(ICON_TAB_SETTINGS_SVG);
    public static final Path ICON_GLOBE_PATH = Path.makeFromSVGString(ICON_GLOBE_SVG);
    public static final Path ICON_FILTER_PATH = Path.makeFromSVGString(ICON_FILTER_SVG);
    public static final Path ICON_ANNOUNCE_PATH = Path.makeFromSVGString(ICON_ANNOUNCE_SVG);
    public static final Path ICON_SETTINGS_APPEARANCE_PATH = Path.makeFromSVGString(ICON_SETTINGS_APPEARANCE_SVG);
    public static final Path ICON_SETTINGS_PRIVACY_PATH = Path.makeFromSVGString(ICON_SETTINGS_PRIVACY_SVG);
    public static final Path ICON_SETTINGS_ABOUT_PATH = Path.makeFromSVGString(ICON_SETTINGS_ABOUT_SVG);

    /**
     * "Nothing here" glyph for an empty list: a tall panel with three text
     * lines beside a shorter one — the same shape language as e33chat's
     * no_online texture, redrawn as open line work.
     */
    private static final String ICON_NO_PLAYERS_SVG =
            "M4.5 3.5H9.5A1.5 1.5 0 0 1 11 5V15A1.5 1.5 0 0 1 9.5 16.5H4.5A1.5 1.5 0 0 1 3 15V5A1.5 1.5 0 0 1 4.5 3.5Z"
            + " M5.5 6.5H8.5"
            + " M5.5 9H8.5"
            + " M5.5 11.5H8.5"
            + " M14.5 8H16A1.5 1.5 0 0 1 17.5 9.5V15A1.5 1.5 0 0 1 16 16.5H14.5A1.5 1.5 0 0 1 13 15V9.5A1.5 1.5 0 0 1 14.5 8Z";

    public static final Path ICON_NO_PLAYERS_PATH = Path.makeFromSVGString(ICON_NO_PLAYERS_SVG);

    // Edit affordance for the profile avatar: a classic pencil at 45° with a
    // short base dash, same open line language as the rest of the shell.
    private static final String ICON_EDIT_SVG =
            "M13.2 4.1l2.7 2.7"
            + " M6.8 16.4l-3 .8.8-3 9.3-9.3a1.9 1.9 0 0 1 2.7 0l.5.5a1.9 1.9 0 0 1 0 2.7z";

    public static final Path ICON_EDIT_PATH = Path.makeFromSVGString(ICON_EDIT_SVG);

    // Cropper confirm/cancel: a plain checkmark and a round-terminated cross,
    // same open line language at 20x20.
    private static final String ICON_CHECK_SVG = "M4.5 10.5 L8.5 14.5 L15.5 6.5";
    private static final String ICON_CLOSE_SVG = "M6 6 L14 14 M14 6 L6 14";

    public static final Path ICON_CHECK_PATH = Path.makeFromSVGString(ICON_CHECK_SVG);
    public static final Path ICON_CLOSE_PATH = Path.makeFromSVGString(ICON_CLOSE_SVG);

    /** Add affordance for the colour-palette "custom colour" cell. */
    private static final String ICON_PLUS_SVG = "M10 4.5 L10 15.5 M4.5 10 L15.5 10";
    public static final Path ICON_PLUS_PATH = Path.makeFromSVGString(ICON_PLUS_SVG);

    // Profile stat-tile glyphs, same 20x20 open line language as the shell
    // icons, rendered small at the tile's left edge.
    // Ping: three ascending signal bars.
    private static final String ICON_SIGNAL_SVG =
            "M5 15.5V12 M10 15.5V8.5 M15 15.5V5";
    // Session: a clock — the face circle plus two round-capped hands.
    private static final String ICON_CLOCK_SVG =
            "M10 3a7 7 0 1 0 0 14a7 7 0 1 0 0-14"
            + " M10 6.8V10l2.6 1.8";
    // Stats: a rising trend line with the arrowhead kept as one corner.
    private static final String ICON_STATS_SVG =
            "M4 15.5L8.5 11L11.5 13.5L16 7.5"
            + " M12.8 7.5H16V10.7";

    public static final Path ICON_SIGNAL_PATH = Path.makeFromSVGString(ICON_SIGNAL_SVG);
    public static final Path ICON_CLOCK_PATH = Path.makeFromSVGString(ICON_CLOCK_SVG);
    public static final Path ICON_STATS_PATH = Path.makeFromSVGString(ICON_STATS_SVG);

    private AppIcons() {
    }
}
