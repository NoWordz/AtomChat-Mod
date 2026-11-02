package com.atom.chat.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins for the device-pixel alignment helpers (unit V). The values come
 * straight from the sharpness probe's findings: a fractional density leaves
 * logical geometry on arbitrary subpixel phases, and a centred 1px stroke
 * straddles two pixels at half intensity each.
 */
class UiPixelSnapTest {

    @Test
    void snapLandsEdgesOnTheDeviceGrid() {
        UiPixelSnap.beginFrame(4.0F / 3.0F);
        // 100 logical px = 133.33 device px -> rounds to 133 -> 99.75 logical.
        assertEquals(99.75F, UiPixelSnap.snapToDevice(100.0F), 1e-4F);
        // An already-aligned coordinate is untouched.
        assertEquals(99.0F, UiPixelSnap.snapToDevice(99.0F), 1e-4F);
    }

    @Test
    void noFrameMeansNoOp() {
        UiPixelSnap.beginFrame(1.0F);
        assertEquals(100.0F, UiPixelSnap.snapToDevice(100.0F), 0.0F);
        assertEquals(0.5F, UiPixelSnap.halfDevicePx(), 1e-4F);
    }

    @Test
    void halfDevicePxIsHalfADevicePixelInLogicalUnits() {
        UiPixelSnap.beginFrame(2.0F);
        assertEquals(0.25F, UiPixelSnap.halfDevicePx(), 1e-4F);
        UiPixelSnap.beginFrame(4.0F / 3.0F);
        assertEquals(0.375F, UiPixelSnap.halfDevicePx(), 1e-4F);
    }

    @Test
    void aDegenerateDensityBehavesAsOne() {
        UiPixelSnap.beginFrame(0.0F);
        assertEquals(1.0F, UiPixelSnap.density(), 0.0F);
        assertEquals(100.0F, UiPixelSnap.snapToDevice(100.0F), 0.0F);
    }
}
