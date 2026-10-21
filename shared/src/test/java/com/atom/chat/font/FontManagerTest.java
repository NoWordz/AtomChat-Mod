package com.atom.chat.font;

import io.github.humbleui.skija.Typeface;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class FontManagerTest {
    @Test
    void rejectedFontDataThrowsSoTheCallerCanFallBack() {
        assertThrows(IOException.class, () -> FontManager.decodeTypeface(new byte[]{1, 2, 3}));
    }

    @Test
    void bundledFontsStillDecodeWithChineseGlyphs() throws Exception {
        for (String name : new String[]{"bundled.otf", "bundled-bold.otf"}) {
            try (InputStream in = getClass().getResourceAsStream("/assets/atomchat/font/" + name)) {
                assertNotNull(in, name);
                try (Typeface face = FontManager.decodeTypeface(in.readAllBytes())) {
                    assertNotEquals(0, face.getUTF32Glyph('聊'), name);
                }
            }
        }
    }
}
