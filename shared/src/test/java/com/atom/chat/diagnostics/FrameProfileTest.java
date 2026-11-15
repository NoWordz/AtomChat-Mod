package com.atom.chat.diagnostics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 尺子本身也要有刻度：一把读数不可信的尺子比没有尺子更坏 —— 人会拿它去决定改什么。
 *
 * <p>这里量不了"某段代码要跑多久"（那本来就不是单测的事），量的是：
 * 关掉之后是不是真的什么都不做、分位数怎么取、样本怎么排、报告长什么样、以及
 * **分配那一列在拿不到时说的是 n/a 而不是 0**。
 */
class FrameProfileTest {

    @BeforeEach
    void enable() {
        FrameProfile.reset();
        FrameProfile.setEnabled(true);
    }

    @AfterEach
    void disable() {
        FrameProfile.setEnabled(false);
        FrameProfile.reset();
    }

    @Test
    void nothingIsRecordedWhileDisabled() {
        FrameProfile.setEnabled(false);
        long startedAt = FrameProfile.start();
        assertEquals(0L, startedAt, "关掉时 start() 必须返回 0，调用点才好统一写成一对");
        assertFalse(FrameProfile.frame(startedAt, -1L), "关掉时不该有报告点");
        FrameProfile.decode(startedAt);
        FrameProfile.layout(startedAt);
        String summary = FrameProfile.summary();
        assertFalse(summary.contains("n="), summary);
        assertTrue(summary.contains("no samples"), summary);
        assertEquals(0L, FrameProfile.startHud());
        assertFalse(FrameProfile.hudFrame(System.nanoTime()));
        assertTrue(FrameProfile.hudSummary().contains("no samples"));
    }

    @Test void hudHasItsOwnWindowAndReportCadence() {
        for (int i = 1; i <= 600; i++) {
            assertEquals(i == 600, FrameProfile.hudFrame(System.nanoTime() - 1_000_000L));
        }
        assertTrue(FrameProfile.hudSummary().contains("n=600"));
        assertTrue(FrameProfile.summary().contains("panel  no samples"));
        assertFalse(FrameProfile.frame(System.nanoTime() - 1_000_000L, -1L));
    }
    @Test void popupMeasurementsNeverMixWithChatOrSettingsSamples() {
        FrameProfile.surface(FrameProfile.Surface.CHAT); FrameProfile.frame(System.nanoTime() - 1_000_000L, -1);
        FrameProfile.surface(FrameProfile.Surface.EMOJI); FrameProfile.frame(System.nanoTime() - 1_000_000L, -1);
        String summary = FrameProfile.summary();
        assertTrue(rowFor(summary, "chat_render").contains("n=1"), summary);
        assertTrue(rowFor(summary, "emoji_render").contains("n=1"), summary);
        assertFalse(summary.contains("emoji_interval"), "a state transition is not a steady frame interval");
    }

    /**
     * 最近秩法：取排序后第 {@code ceil(p*n)} 个（1-based）。样本少的时候它退化成最大值，
     * 这是有意的 —— 别让三个样本编出一个漂亮的 p95。
     */
    @Test
    void percentileIndexPicksTheNearestRank() {
        assertEquals(4, FrameProfile.percentileIndex(0.50, 10));
        assertEquals(9, FrameProfile.percentileIndex(0.95, 10));
        assertEquals(0, FrameProfile.percentileIndex(0.50, 1));
        assertEquals(0, FrameProfile.percentileIndex(0.95, 1));
        assertEquals(1, FrameProfile.percentileIndex(0.50, 3));
        assertEquals(2, FrameProfile.percentileIndex(0.95, 3));
        assertEquals(99, FrameProfile.percentileIndex(1.0, 100));
        assertEquals(0, FrameProfile.percentileIndex(0.50, 0), "空窗口不该算出负下标");
    }

    @Test
    void theWindowReportsOrderedPercentiles() {
        // 喂 100 帧，耗时分别是 1..100 ms：把 startedAt 往回退，差值就是我们要的毫秒数。
        for (int i = 1; i <= 100; i++) {
            FrameProfile.frame(System.nanoTime() - i * 1_000_000L, -1L);
        }
        String row = rowFor(FrameProfile.summary(), "panel");
        assertTrue(row.contains("n=100"), row);
        double p50 = value(row, "p50");
        double p95 = value(row, "p95");
        double max = value(row, "max");
        double mean = value(row, "mean");
        assertTrue(p50 <= p95, row);
        assertTrue(p95 <= max, row);
        assertTrue(mean <= max, row);
        // 真值就在 1..100 ms 之间；留够余量，免得被一次调度抖动判红。
        assertTrue(p50 >= 45.0 && p50 <= 60.0, row);
        assertTrue(max >= 99.0 && max <= 300.0, row);
    }

    @Test
    void theReportFiresOncePerWindowOfFrames() {
        int fired = 0;
        for (int i = 1; i < FrameProfile.REPORT_EVERY_FRAMES; i++) {
            if (FrameProfile.frame(System.nanoTime() - 1_000_000L, -1L)) {
                fired++;
            }
        }
        assertEquals(0, fired, "第 N 帧之前不该报告");
        assertTrue(FrameProfile.frame(System.nanoTime() - 1_000_000L, -1L), "第 N 帧该报告");
        assertFalse(FrameProfile.frame(System.nanoTime() - 1_000_000L, -1L), "报告之后要重新计数");
    }

    /** 拿不到分配计数时说 n/a：写 0.0 会被读成"这一帧没有分配"，那是另一回事。 */
    @Test
    void theAllocationColumnSaysNotAvailableRatherThanZero() {
        FrameProfile.frame(System.nanoTime() - 1_000_000L, -1L);
        assertTrue(rowFor(FrameProfile.summary(), "panel").contains("alloc=n/a"),
                FrameProfile.summary());
    }

    /** 拿得到的时候这一列要有数（不校验具体值：那是这台机器上这一帧跑了什么决定的）。 */
    @Test
    void theAllocationColumnIsFilledWhenTheJvmSupportsIt() {
        long before = FrameProfile.allocatedBytes();
        if (before < 0L) {
            return;
        }
        FrameProfile.frame(System.nanoTime() - 1_000_000L, before);
        assertTrue(rowFor(FrameProfile.summary(), "panel").contains("KB/call"),
                FrameProfile.summary());
    }

    /**
     * 报告是一整块贴进 issue 的：全程 ASCII（日志文件按平台默认编码写），
     * 抬头一行 + 每个桶一行，谁都不许是空行。
     */
    @Test
    void theSummaryIsAsciiWithOneLinePerBucket() {
        FrameProfile.frame(System.nanoTime() - 1_000_000L, -1L);
        FrameProfile.decode(System.nanoTime() - 500_000L);
        FrameProfile.layout(System.nanoTime() - 100_000L);
        String summary = FrameProfile.summary();
        assertTrue(summary.chars().allMatch(c -> c < 0x80), summary);
        String[] lines = summary.split("\n");
        assertEquals(5, lines.length, summary);
        assertTrue(lines[0].equals(FrameProfile.SUMMARY_HEADER), summary);
        assertTrue(lines[1].startsWith("  panel"), summary);
        assertTrue(lines[2].startsWith("  decode"), summary);
        assertTrue(lines[3].startsWith("  layout"), summary);
        for (String line : lines) {
            assertFalse(line.isBlank(), summary);
        }
    }

    @Test
    void resetDropsTheWindow() {
        FrameProfile.frame(System.nanoTime() - 1_000_000L, -1L);
        FrameProfile.reset();
        assertTrue(FrameProfile.summary().contains("no samples"), FrameProfile.summary());
    }

    private static String rowFor(String summary, String bucket) {
        for (String line : summary.split("\n")) {
            if (line.startsWith("  " + bucket)) {
                return line;
            }
        }
        throw new AssertionError("no row for " + bucket + " in:\n" + summary);
    }

    private static double value(String row, String key) {
        String prefix = key + "=";
        int at = row.indexOf(prefix);
        if (at < 0) {
            throw new AssertionError("no " + key + " in " + row);
        }
        int end = at + prefix.length();
        while (end < row.length()
                && (Character.isDigit(row.charAt(end)) || row.charAt(end) == '.')) {
            end++;
        }
        return Double.parseDouble(row.substring(at + prefix.length(), end));
    }
}
