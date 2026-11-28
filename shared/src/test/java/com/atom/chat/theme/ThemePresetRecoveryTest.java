package com.atom.chat.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Expected bezel colors for each built-in palette. */
class ThemePresetRecoveryTest {
    @Test void palettesKeepTheirConfirmedDistinctBorders() {
        assertEquals(0xFF4A6285, ThemeService.MINIMAL.panelOutlineColor(), "MINIMAL");
        assertEquals(0xFF466C00, ThemeService.SUMMER.panelOutlineColor(), "SUMMER");
        assertEquals(0xFF806445, ThemeService.ELEGANT.panelOutlineColor(), "ELEGANT");
        assertEquals(0xFF07102A, ThemeService.RAVEN.panelOutlineColor(), "RAVEN");
        assertEquals(0xFF000000, ThemeService.HUB.panelOutlineColor(), "HUB");
        assertEquals(0xFF8C526B, ThemeService.VIVID.panelOutlineColor(), "VIVID");
        assertEquals(0xFF1D124C, ThemeService.DUSK.panelOutlineColor(), "DUSK");
        assertEquals(0xFF12392F, ThemeService.MINT.panelOutlineColor(), "MINT");
        assertEquals(0xFF536E7D, ThemeService.ICE_GLASS.panelOutlineColor(), "ICE_GLASS");
        assertEquals(0xFF826C50, ThemeService.SAND_GLASS.panelOutlineColor(), "SAND_GLASS");
    }
}
