package com.atom.chat.settings;

import com.atom.chat.ui.UiLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsStepperTest {
    @Test void teleportCyclesInBothDirectionsIncludingAutomaticMode() {
        assertEquals("tp", SettingsStepper.teleport("auto", 1));
        assertEquals("tpa", SettingsStepper.teleport("tp", 1));
        assertEquals("auto", SettingsStepper.teleport("tpa", 1));
        assertEquals("tpa", SettingsStepper.teleport("auto", -1));
        assertEquals("tp", SettingsStepper.teleport("tpa", -1));
        assertEquals("auto", SettingsStepper.teleport("tp", -1));
        assertEquals("tp", SettingsStepper.teleport(null, 1));
        assertEquals("tpa", SettingsStepper.teleport("invalid", -1));
    }

    @Test void arrowHitAreasShareThePaintGeometryAndDoNotIncludeTheValue() {
        for (float width : new float[]{50, 100, 400}) {
            var row = new UiLayout.Rect(20, 30, width, 120);
            var geometry = SettingsStepper.layout(row, 200, true);
            assertTrue(geometry.previous().x() >= row.x());
            assertTrue(geometry.next().right() <= row.right());
            assertTrue(geometry.previous().right() <= geometry.value().x());
            assertTrue(geometry.value().right() <= geometry.next().x());
            assertEquals(-1, geometry.directionAt(centerX(geometry.previous()), centerY(geometry.previous())));
            assertEquals(1, geometry.directionAt(centerX(geometry.next()), centerY(geometry.next())));
            if (geometry.value().w() > 0) assertEquals(0,
                    geometry.directionAt(centerX(geometry.value()), centerY(geometry.value())));
            assertEquals(0, geometry.directionAt(row.x(), row.y()));
        }
    }

    private static float centerX(UiLayout.Rect r) { return r.x() + r.w() / 2; }
    private static float centerY(UiLayout.Rect r) { return r.y() + r.h() / 2; }
}
