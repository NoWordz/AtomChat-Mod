package com.atom.chat.settings;

import com.atom.chat.render.CornerGeometry;
import io.github.humbleui.skija.Canvas;

/** Settings-tab entry point and independent coverage comparison switch. */
public final class SettingsCapsuleCoverage {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("atomchat.settingsCapsuleCoverage", "true"));

    private SettingsCapsuleCoverage() { }

    public static void draw(Canvas canvas, float x, float y, float width, float height, float radius, int argb) {
        var rect = CornerGeometry.circularRect(x, y, width, height, radius);
        if (ENABLED) CornerGeometry.fill(canvas, rect, argb);
        else CornerGeometry.fillNative(canvas, rect, argb);
    }
}
