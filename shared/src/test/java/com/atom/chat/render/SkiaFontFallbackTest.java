package com.atom.chat.render;

import org.junit.jupiter.api.Test;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.impl.Stats;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字形回退候选清单的契约。
 *
 * <p>为什么这些断言值钱：候选字体的<b>顺序</b>决定同一台机器上谁来画一个字。
 * Windows 名单必须排在前面（否则 Windows 玩家会用上另一张脸，属于回归），
 * 而 Linux 名单必须存在（否则 Linux 玩家没有系统里真正装着的字体可退 ——
 * 这正是"表情全是方框"的根因）。挑选靠纯函数 {@code firstFamilyWithGlyph}，
 * 于是这条契约不依赖这台机器装了什么字体就能钉住。
 *
 * <p>环境无关：不碰 Skija 原生库，只读静态清单与一个纯函数，在哪台机器上跑
 * 都是同一结果。
 */
class SkiaFontFallbackTest {

    /** 挑第一个命中的；命中顺序就是清单顺序（不是最后一个命中）。 */
    @Test
    void picksTheFirstFamilyThatHasTheGlyph() {
        String[] families = {"A", "B", "C"};
        assertEquals("A", SkiaFontRenderer.firstFamilyWithGlyph(families, "A"::equals));
        assertEquals("B", SkiaFontRenderer.firstFamilyWithGlyph(families, f -> f.equals("B") || f.equals("C")));
        // A 与 C 都命中时仍取 A —— 挡住"返回最后一个命中"的写法。
        assertEquals("A", SkiaFontRenderer.firstFamilyWithGlyph(families, f -> f.equals("A") || f.equals("C")));
    }

    /** 一个都没命中就返回 null，上层据此退回主字体，而不是乱选一个。 */
    @Test
    void returnsNullWhenNothingHasTheGlyph() {
        assertNull(SkiaFontRenderer.firstFamilyWithGlyph(new String[]{"A", "B"}, f -> false));
        assertNull(SkiaFontRenderer.firstFamilyWithGlyph(new String[0], f -> true));
    }

    /** 清单里的空位不能崩，也不能被当成命中。 */
    @Test
    void aNullEntryIsSkippedNotMatched() {
        assertEquals("B", SkiaFontRenderer.firstFamilyWithGlyph(new String[]{"A", null, "B"}, "B"::equals));
    }

    /**
     * emoji 分支必须先问 Windows 的 Segoe UI Emoji、再问 Linux 的 Noto Color Emoji：
     * 顺序反了 Windows 换脸（回归）；少了 Noto 那条，Linux 玩家永远命中不了系统里
     * 真正装着的彩色 emoji 字体。
     */
    @Test
    void emojiFamiliesKeepWindowsFirstAndIncludeLinux() {
        List<String> emoji = Arrays.asList(SkiaFontRenderer.EMOJI_FAMILIES);
        assertEquals("Segoe UI Emoji", emoji.get(0), "Segoe UI Emoji 必须仍在第一位，否则 Windows 换脸");
        assertTrue(emoji.contains("Noto Color Emoji"), "Linux 的 Noto Color Emoji 必须在 emoji 候选里");
        assertTrue(emoji.indexOf("Segoe UI Emoji") < emoji.indexOf("Noto Color Emoji"),
                "Windows emoji 候选要排在 Linux 候选之前");
    }

    /**
     * 通用回退清单：原有 Windows 名字一个不能少，且**所有**非 Windows 家族都排在
     * 最后一个 Windows 名之后。少一个 Windows 名字 = Windows 回归；任何 Linux/跨平台
     * 名被插进 Windows 段内 = 同一码位在 Windows 上换脸（这正是变异测试要抓的）。
     */
    @Test
    void fallbackFamiliesKeepEveryWindowsNameAheadOfAnyNonWindowsFamily() {
        List<String> families = Arrays.asList(SkiaFontRenderer.FALLBACK_FAMILIES);
        List<String> windowsNames = List.of("Microsoft YaHei", "DengXian", "Segoe UI", "Segoe UI Symbol",
                "MS Gothic", "Yu Gothic UI", "Malgun Gothic", "Leelawadee UI",
                "Cambria", "Calibri", "Arial");
        for (String name : windowsNames) {
            assertTrue(families.contains(name), "原 Windows 回退字体 " + name + " 不能丢");
        }
        int lastWindows = families.lastIndexOf("Arial");
        assertTrue(lastWindows >= 0, "Arial 是 Windows 段的收尾，必须在清单里");
        for (String name : windowsNames) {
            assertTrue(families.indexOf(name) <= lastWindows,
                    name + " 属于 Windows 段，索引不应超过 Arial");
        }
        // 每个非 Windows 家族都必须落在 Windows 段之后 —— 这条能抓住"把某个 Linux
        // 名插到 Windows 名前/中间"的任何变体，而只锚 DejaVu 会漏掉插在前半段的。
        for (int i = 0; i < families.size(); i++) {
            String name = families.get(i);
            if (!windowsNames.contains(name)) {
                assertTrue(i > lastWindows,
                        "非 Windows 家族 " + name + " 排在了 Windows 段内（索引 " + i + " <= " + lastWindows + "）");
            }
        }
        assertTrue(families.contains("Noto Sans CJK SC"), "Linux 的 Noto Sans CJK SC 必须在清单里");
        assertTrue(families.contains("DejaVu Sans"), "Linux 的 DejaVu Sans 必须在清单里");
    }

    /**
     * Each system-font candidate lookup creates a native Typeface wrapper;
     * the probe must close it instead of waiting for the cleaner.
     */
    @Test
    void familyGlyphProbeReleasesItsTypeface() throws Exception {
        FontMgr manager = FontMgr.getDefault();
        assertTrue(manager.getFamiliesCount() > 0, "测试环境必须提供至少一个系统字体");
        String family = manager.getFamilyName(0);
        Typeface sample = manager.matchFamilyStyle(family, FontStyle.NORMAL);
        assertNotNull(sample, "FontMgr 返回的首个家族必须可匹配，确保后续探针会分配 Typeface");
        sample.close();

        var probe = SkiaFontRenderer.class.getDeclaredMethod(
                "familyCarriesGlyph", FontMgr.class, String.class, int.class);
        probe.setAccessible(true);

        boolean previousStatsEnabled = Stats.enabled;
        Stats.enabled = true;
        try {
            int before = Stats.allocated.getOrDefault("Typeface", 0);
            probe.invoke(null, manager, family, (int) 'A');
            int after = Stats.allocated.getOrDefault("Typeface", 0);
            assertEquals(before, after, "临时 Typeface 必须在 glyph 检查后立即释放");
        } finally {
            Stats.enabled = previousStatsEnabled;
        }
    }
}
