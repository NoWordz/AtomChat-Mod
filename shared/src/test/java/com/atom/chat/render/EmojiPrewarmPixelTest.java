package com.atom.chat.render;

import com.atom.chat.font.FontManager;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预热路径与直接栅格路径的逐像素等价。先走预热（必须命中预热 glyph），
 * 清空缓存后再走原来的「现场解析字体 + 现场栅格」路径，两张位图逐字节比较。
 */
class EmojiPrewarmPixelTest {

    private static final int W = 400;
    private static final int H = 300;

    @BeforeAll static void setup(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @BeforeEach void clear() {
        SkiaFontRenderer.clearEmojiGlyphCache();
    }

    @Test void prewarmedGlyphMatchesTheColdRasterPixels() {
        Font primary = FontManager.font(27.5F);
        long hitsBefore = SkiaFontRenderer.emojiPrewarmHits();
        SkiaFontRenderer.prewarmEmojiGlyphs(List.of(
                new EmojiPrewarmSpec(27.5F, "\uD83D\uDE00", 0xFFFFFF)));

        byte[] warmed = render(primary);
        assertTrue(SkiaFontRenderer.emojiPrewarmHits() > hitsBefore,
                "首帧必须真的拿走预热的 glyph，否则等价性只证明了冷路径等于自己");

        // Drop the prewarmed bitmap only; the next draw rebuilds it the old way.
        SkiaFontRenderer.clearEmojiGlyphCache();
        byte[] cold = render(primary);

        assertArrayEquals(cold, warmed, "预热 glyph 与冷栅格 glyph 的像素必须完全一致");
    }

    private static byte[] render(Font primary) {
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            var canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(2.0F, 2.0F);
            SkiaFontRenderer.drawText(canvas, primary, "\uD83D\uDE00", 20, 60, 0xFFFFFFFF);
            try (Bitmap bitmap = new Bitmap()) {
                bitmap.allocN32Pixels(W, H);
                assertTrue(surface.readPixels(bitmap, 0, 0));
                return bitmap.readPixels();
            }
        }
    }
}
