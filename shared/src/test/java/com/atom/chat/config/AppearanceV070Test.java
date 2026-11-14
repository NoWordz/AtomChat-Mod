package com.atom.chat.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceV070Test {
    private static float width(AtomChatConfig config) throws Exception {
        return ((Number) AtomChatConfig.class.getMethod("effectivePanelOutlineWidth").invoke(config)).floatValue();
    }
    @Test void newOutlineIsFourLogicalPixels() throws Exception {
        assertEquals(4F, width(new AtomChatConfig()));
    }
    @Test void oldOutlineMigratesWithoutChangingItsAppearance() throws Exception {
        var c = new AtomChatConfig();
        AtomChatConfig.class.getMethod("migrateOutlineWidth", boolean.class).invoke(c, false);
        assertEquals(2F, width(c));
        c.panelOutline = false;
        AtomChatConfig.class.getMethod("migrateOutlineWidth", boolean.class).invoke(c, false);
        assertEquals(0F, width(c));
    }
    @Test void actualDesignHostCardFillUsesApprovedInstanceMix() throws Exception {
        var instance=AtomChatConfig.class.getDeclaredField("instance");
        instance.setAccessible(true); Object previous=instance.get(null);
        try {
            instance.set(null,new AtomChatConfig());
            int fill=com.atom.lib.design.atomchat.UiTokens.cardFill(com.atom.lib.design.atomchat.Design.host());
            assertEquals(0x9914161B,fill);
        } finally { instance.set(null,previous); }
    }
    @Test void defaultSurfacesAreLayeredAndTranslucentWithoutChangingBlue() {
        var c = new AtomChatConfig();
        assertEquals(0xFF4A90E2, c.accentColor);
        assertEquals(.6990596F,c.panelOpacity);
        assertEquals(0xFF101418, c.panelBgColor);
        assertEquals(0xFF14161B, c.cardColor);
        assertEquals(0xFF14161B, c.panelOutlineColor);
        float cardAlpha = c.cardTint;
        assertTrue(cardAlpha >= .4F && cardAlpha <= .6F);
    }
}
