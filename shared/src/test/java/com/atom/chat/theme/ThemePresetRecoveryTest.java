package com.atom.chat.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Non-default bezel colours recovered from the SHA-verified 17:00 build. */
class ThemePresetRecoveryTest {
    @Test void palettesKeepTheirConfirmedDistinctBorders() {
        assertEquals(0xFF4A6285, ThemeService.MINIMAL.panelOutlineColor(), "MINIMAL");
        assertEquals(0xFF5A732A, ThemeService.SUMMER.panelOutlineColor(), "SUMMER");
        assertEquals(0xFF806445, ThemeService.ELEGANT.panelOutlineColor(), "ELEGANT");
        assertEquals(0xFFAAB5C4, ThemeService.RAVEN.panelOutlineColor(), "RAVEN");
        assertEquals(0xFFC0CAD5, ThemeService.HUB.panelOutlineColor(), "HUB");
        assertEquals(0xFF8C526B, ThemeService.VIVID.panelOutlineColor(), "VIVID");
        assertEquals(0xFFB9B2D5, ThemeService.DUSK.panelOutlineColor(), "DUSK");
        assertEquals(0xFF9EDCCB, ThemeService.MINT.panelOutlineColor(), "MINT");
        assertEquals(0xFFA9C6E8, ThemeService.AZURE_GLASS.panelOutlineColor(), "AZURE_GLASS");
        assertEquals(0xFFD9B6E0, ThemeService.PLUM_GLASS.panelOutlineColor(), "PLUM_GLASS");
        assertEquals(0xFF536E7D, ThemeService.ICE_GLASS.panelOutlineColor(), "ICE_GLASS");
        assertEquals(0xFF826C50, ThemeService.SAND_GLASS.panelOutlineColor(), "SAND_GLASS");
    }
}
