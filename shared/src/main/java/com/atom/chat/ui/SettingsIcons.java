package com.atom.chat.ui;

import io.github.humbleui.skija.Path;

/** Tabler outline paths, drawn by the existing settings icon painter with round joins. */
public final class SettingsIcons {
    private SettingsIcons() {}

    public static final Path PALETTE = Path.makeFromSVGString(
            "M12 21a9 9 0 0 1 0 -18c4.97 0 9 3.582 9 8c0 1.06 -.474 2.078 -1.318 2.828c-.844 .75 -1.989 1.172 -3.182 1.172h-2.5a2 2 0 0 0 -1 3.75a1.3 1.3 0 0 1 -1 2.25 M7.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0 M11.5 7.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0 M15.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0");
    public static final Path MESSAGES = Path.makeFromSVGString(
            "M21 14l-3 -3h-7a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1h9a1 1 0 0 1 1 1v10 M14 15v2a1 1 0 0 1 -1 1h-7l-3 3v-10a1 1 0 0 1 1 -1h2");
    public static final Path SHIELD_LOCK = Path.makeFromSVGString(
            "M12 3a12 12 0 0 0 8.5 3a12 12 0 0 1 -8.5 15a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3 M11 11a1 1 0 1 0 2 0a1 1 0 1 0 -2 0 M12 12l0 2.5");
    public static final Path INFO_CIRCLE = Path.makeFromSVGString(
            "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0 M12 9h.01 M11 12h1v4h1");
    public static final Path EYE_OFF = Path.makeFromSVGString(
            "M10.585 10.587a2 2 0 0 0 2.829 2.828 M16.681 16.673a8.717 8.717 0 0 1 -4.681 1.327c-3.6 0 -6.6 -2 -9 -6c1.272 -2.12 2.712 -3.678 4.32 -4.674m2.86 -1.146a9.055 9.055 0 0 1 1.82 -.18c3.6 0 6.6 2 9 6c-.666 1.11 -1.379 2.067 -2.138 2.87 M3 3l18 18");
    public static final Path PLUG_CONNECTED = Path.makeFromSVGString(
            "M7 12l5 5l-1.5 1.5a3.536 3.536 0 1 1 -5 -5l1.5 -1.5 M17 12l-5 -5l1.5 -1.5a3.536 3.536 0 1 1 5 5l-1.5 1.5 M3 21l2.5 -2.5 M18.5 5.5l2.5 -2.5 M10 11l-2 2 M13 14l-2 2");
    public static final Path KEYBOARD = Path.makeFromSVGString(
            "M2 8a2 2 0 0 1 2 -2h16a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2l0 -8 M6 10l0 .01 M10 10l0 .01 M14 10l0 .01 M18 10l0 .01 M6 14l0 .01 M18 14l0 .01 M10 14l4 .01");
    public static final Path PHOTO = Path.makeFromSVGString(
            "M15 8h.01 M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12 M3 16l5 -5c.928 -.893 2.072 -.893 3 0l5 5 M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3");
    public static final Path VOLUME = Path.makeFromSVGString(
            "M15 8a5 5 0 0 1 0 8 M17.7 5a9 9 0 0 1 0 14 M6 15h-2a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1h2l3.5 -4.5a.8 .8 0 0 1 1.5 .5v14a.8 .8 0 0 1 -1.5 .5l-3.5 -4.5");
    public static final Path TRASH = Path.makeFromSVGString(
            "M4 7l16 0 M10 11l0 6 M14 11l0 6 M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12 M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3");
    public static final Path DEVICE_DESKTOP = Path.makeFromSVGString(
            "M3 5a1 1 0 0 1 1 -1h16a1 1 0 0 1 1 1v10a1 1 0 0 1 -1 1h-16a1 1 0 0 1 -1 -1v-10 M7 20h10 M9 16v4 M15 16v4");
    public static final Path BAN = Path.makeFromSVGString(
            "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0 M5.7 5.7l12.6 12.6");
}
