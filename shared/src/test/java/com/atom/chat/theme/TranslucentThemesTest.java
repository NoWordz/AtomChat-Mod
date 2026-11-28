package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslucentThemesTest {
    @Test void iceAndSandKeepVisibleTransparencyAndBlurWithoutChangingLayout() {
        for (String id : new String[]{"ice_glass", "sand_glass"}) {
            assertNotNull(ThemeService.byId(id), id);
            var config = new AtomChatConfig(); config.panelWidth = 470;
            ThemeService.apply(config, id);
            assertEquals(0.70F, config.panelOpacity, id);
            assertEquals(0.60F, config.cardTint, id);
            assertTrue(config.blurEnabled, id);
            assertEquals(470, config.panelWidth);
            assertEquals(id, config.themeName);
        }
    }
}
