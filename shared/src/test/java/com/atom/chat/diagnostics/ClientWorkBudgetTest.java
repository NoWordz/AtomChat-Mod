package com.atom.chat.diagnostics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 非渲染链路（client tick）每 tick 总墙钟的观测契约。
 * 全部离屏/受控，不启动游戏、不需要 GL 或 Minecraft 运行时。
 */
class ClientWorkBudgetTest {

    private static final long STEP_NS = 3_000_000L; // 3.0 ms

    private static Matcher match(String line) {
        Matcher m = Pattern.compile(ClientWorkBudget.LINE_RE).matcher(line);
        assertTrue(m.matches(), "不可解析: " + line);
        return m;
    }

    @Test
    void silentWhenInstrumentsAreDisabled() {
        boolean was = FrameProfile.enabled();
        FrameProfile.setEnabled(false);
        try {
            ClientWorkBudget b = new ClientWorkBudget();
            assertEquals(0L, b.startTick(), "门控关闭时不得取时钟");
            for (int i = 0; i < ClientWorkBudget.REPORT_EVERY + 1; i++) {
                b.endTick(0L);
            }
            assertNull(b.summaryLine(), "门控关闭时不得输出任何 client_work 行");
        } finally {
            FrameProfile.setEnabled(was);
        }
    }

    @Test
    void reportsPerTickWallClockEverySixHundredSamples() {
        boolean was = FrameProfile.enabled();
        FrameProfile.setEnabled(true);
        try {
            ClientWorkBudget b = new ClientWorkBudget();
            for (int i = 0; i < ClientWorkBudget.REPORT_EVERY - 1; i++) b.record(STEP_NS);
            assertNull(b.summaryLine(), "不足 600 个样本不输出");

            b.record(STEP_NS);
            String line = b.summaryLine();
            assertNotNull(line, "攒满 600 个样本必须出数");
            Matcher m = match(line);
            assertEquals("600", m.group(1));
            assertEquals("3.000", m.group(2), "p50 ms");
            assertEquals("3.000", m.group(3), "p95 ms");
            assertEquals("3.00", m.group(4), "max ms");

            assertNull(b.summaryLine(), "出数后必须重置报告计数，不得重复输出");
        } finally {
            FrameProfile.setEnabled(was);
        }
    }

    @Test
    void ignoresNonContiguousOrNonPositiveIntervals() {
        boolean was = FrameProfile.enabled();
        FrameProfile.setEnabled(true);
        try {
            ClientWorkBudget b = new ClientWorkBudget();
            b.record(0L);
            b.record(-1L);
            b.record(2_000_000_000L);
            for (int i = 0; i < ClientWorkBudget.REPORT_EVERY - 1; i++) b.record(STEP_NS);
            assertNull(b.summaryLine(), "被丢弃的样本不得凑数");

            b.record(9_000_000L); // 9 ms 尖峰
            Matcher m = match(b.summaryLine());
            assertEquals("3.000", m.group(2), "p50 不受单个尖峰影响");
            assertEquals("9.00", m.group(4), "max 必须保留尖峰");
        } finally {
            FrameProfile.setEnabled(was);
        }
    }
}
