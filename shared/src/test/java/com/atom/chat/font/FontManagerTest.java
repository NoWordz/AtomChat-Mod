package com.atom.chat.font;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
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

    @Test
    void prewarmLoadsTheFaceAwayFromTheSynchronousPath() {
        FontManager.resetLoadCountersForTest();
        Typeface warmed = FontManager.prewarmBlocking();
        assertNotNull(warmed);
        int loadsAfterPrewarm = FontManager.syncLoadCount();
        assertSame(warmed, FontManager.getDefaultTypeface(),
                "预热发布之后，首帧必须拿到同一个 Typeface 实例");
        assertEquals(loadsAfterPrewarm, FontManager.syncLoadCount(),
                "预热命中时不得再走渲染线程的同步加载分支");
    }

    @Test
    void prewarmedFaceRendersIdenticalPixelsToAFreshDecode() throws Exception {
        Typeface warmed = FontManager.prewarmBlocking();
        byte[] bytes = FontSource.resource("/com/atom/lib/fonts/bundled-default.otf").read();
        Typeface fresh = FontManager.mediumWeight(FontManager.decodeTypeface(bytes));
        try {
            assertArrayEquals(render(fresh, "AtomChat 聊天"), render(warmed, "AtomChat 聊天"),
                    "同字节解出的 Medium 字面必须与预热字面逐像素一致");
        } finally {
            fresh.close();
        }
    }

    private static byte[] render(Typeface typeface, String text) {
        int w = 480, h = 80;
        try (Surface surface = Surface.makeRasterN32Premul(w, h);
             Font font = new Font(typeface, 22.0F);
             Paint paint = new Paint().setColor(0xFFFFFFFF)) {
            surface.getCanvas().clear(0);
            surface.getCanvas().drawString(text, 10, 52, font, paint);
            try (Bitmap bitmap = new Bitmap()) {
                bitmap.allocN32Pixels(w, h);
                assertTrue(surface.readPixels(bitmap, 0, 0));
                return bitmap.readPixels();
            }
        }
    }
}
