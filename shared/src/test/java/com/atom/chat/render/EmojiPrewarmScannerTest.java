package com.atom.chat.render;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯选择规则：只收 emoji 码点、拆成单码点、保留代理对、去重、封顶。
 * 不需要 Skija 原生库。
 */
class EmojiPrewarmScannerTest {

    @Test void keepsOnlyEmojiCodepointsAsSingleGlyphs() {
        List<EmojiPrewarmSpec> out = EmojiPrewarmScanner.collect(
                List.of("abc😀聊❤"),
                new float[]{20.0F},
                new int[]{0xFFFFFFFF});
        assertEquals(2, out.size(), out.toString());
        assertEquals("😀", out.get(0).text());
        assertEquals("❤", out.get(1).text());
        assertEquals(0xFFFFFF, out.get(0).rgb(), "alpha 必须被掩掉，与绘制键一致");
    }

    @Test void deduplicatesAcrossTextsAndColors() {
        List<EmojiPrewarmSpec> out = EmojiPrewarmScanner.collect(
                List.of("😀", "😀", "x😀"),
                new float[]{20.0F, 20.0F},
                new int[]{0xFFFFFFFF, 0xFFFFFFFF});
        assertEquals(1, out.size(), out.toString());
    }

    @Test void keepsSurrogatePairsIntact() {
        List<EmojiPrewarmSpec> out = EmojiPrewarmScanner.collect(
                List.of("😀"),
                new float[]{20.0F},
                new int[]{0xFFFFFF});
        assertEquals(1, out.size());
        assertEquals(2, out.get(0).text().length(), "U+1F600 必须保持为一个代理对");
    }

    @Test void capsTheWorkingSet() {
        StringBuilder many = new StringBuilder();
        for (int cp = 0x1F600; cp < 0x1F600 + 80; cp++) {
            many.appendCodePoint(cp);
        }
        List<EmojiPrewarmSpec> out = EmojiPrewarmScanner.collect(
                List.of(many.toString()),
                new float[]{20.0F, 16.0F, 22.0F},
                new int[]{0xFFFFFF, 0x000000, 0x123456, 0x654321, 0xABCDEF});
        assertEquals(EmojiPrewarmScanner.MAX_SPECS, out.size());
    }
}
