package com.atom.chat.font;

import io.github.humbleui.skija.Typeface;
import com.atom.lib.font.FontSource;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class FontManagerTest {
    @Test
    void rejectedFontDataThrowsSoTheCallerCanFallBack() {
        assertThrows(IOException.class, () -> FontManager.decodeTypeface(new byte[]{1, 2, 3}));
    }

    @Test
    void atomLibFontsDecodeAndContainChineseGlyphs() throws Exception {
        for (String name : new String[]{"bundled-default.otf", "bundled-default-bold.otf"}) {
            byte[] bytes = FontSource.resource("/com/atom/lib/fonts/" + name).read();
            try (Typeface face = FontManager.decodeTypeface(bytes)) {
                assertNotEquals(0, face.getUTF32Glyph('聊'), name);
            }
        }
        assertNull(getClass().getResource("/assets/atomchat/font/bundled.otf"));
        assertNull(getClass().getResource("/assets/atomchat/font/bundled-bold.otf"));
    }
    @Test
    void managerUsesMediumRegularAndRealBoldWithChinese() {
        var regular = FontManager.getDefaultTypeface();
        assertEquals(500, regular.getFontStyle().getWeight());
        assertNotEquals(0, regular.getUTF32Glyph('聊'));
        var bold = FontManager.boldFont(18).getTypeface();
        assertNotNull(bold);
        assertTrue(bold.getFontStyle().getWeight() >= 700);
        assertNotEquals(0, bold.getUTF32Glyph('聊'));
    }

}
