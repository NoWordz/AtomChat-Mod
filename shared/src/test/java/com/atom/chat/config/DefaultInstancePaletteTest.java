package com.atom.chat.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DefaultInstancePaletteTest {
    @Test void defaultsMatchTheApprovedCcbAppearanceOnly() {
        var config = new AtomChatConfig();
        assertEquals(-15723496, config.panelBgColor);
        assertEquals(-15460837, config.cardColor);
        assertEquals(-15460837, config.panelOutlineColor);
        assertEquals(-14800581, config.otherBubbleColor);
        assertEquals(-390611510, config.textSecondaryColor);
        assertEquals(.6990596F, config.panelOpacity);
        assertEquals(.59866506F, config.cardTint);
        assertEquals(25F, config.cornerRadius);
        assertEquals(3F, config.panelOutlineWidth);
    }
}
