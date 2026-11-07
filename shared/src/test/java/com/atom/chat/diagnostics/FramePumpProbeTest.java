package com.atom.chat.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E0/E1 仪表能力测试：{@code frame_pump} 第二采样入口（自有窗口、与 frame_probe 零共享）
 * 与 {@code diagnosticMeasureOnly} 门控。全部在 shared 源集里跑，不启动游戏、
 * 不需要 GL/Minecraft 运行时；数值一律离屏/受控，非真机测量。
 */
class FramePumpProbeTest {

    private static final List<String> RENDERERS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/PanelBlurRenderer.java");
    private static final List<String> CLIENTS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/AtomChatClient.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/AtomChatClient.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/AtomChatClient.java");
    private static final String CONFIG = "shared/src/main/java/com/atom/chat/config/AtomChatConfig.java";
    private static final String ENV = "shared/src/main/java/com/atom/chat/diagnostics/EnvironmentSummary.java";
    private static final String PROBE = "shared/src/main/java/com/atom/chat/diagnostics/Truth3Probe.java";
    private static final long STEP_NS = 3_000_000L; // 3.0 ms/帧 ≈ 333 FPS（离屏/受控，非真机）
    private static final long START = 1_000_000L;

    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("platforms"))) p = p.getParent();
        return java.util.Objects.requireNonNull(p);
    }

    private static String src(String rel) throws Exception {
        return Files.readString(root().resolve(rel));
    }

    private static int count(String s, String needle) {
        int n = 0;
        for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + needle.length())) n++;
        return n;
    }

    private static Matcher match(String regex, String line) {
        Matcher m = Pattern.compile(regex).matcher(line);
        assertTrue(m.matches(), "不可解析: " + line);
        return m;
    }

    /** 按固定步长喂 n 帧 pump，返回最后一条非 null 日志。 */
    private static String pump(Truth3Probe p, int frames, long startNanos, boolean debug, String mode) {
        String out = null;
        for (int i = 0; i < frames; i++) {
            String line = p.onPumpFrame(startNanos + (long) i * STEP_NS, debug, mode);
            if (line != null) out = line;
        }
        return out;
    }

    // --- 1. frame_pump 行可解析，且窗口与 frame_probe 完全独立 -----------------

    @Test
    void pumpLineIsParseableAndKeepsItsOwnWindow() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");

        // 先让 render 窗口攒 300 个样本（不足 600，不出行）。
        for (int i = 0; i < 300; i++) {
            assertNull(p.onRenderFrame(START + (long) i * STEP_NS));
        }

        String pumpLine = pump(p, Truth3Probe.REPORT_EVERY + 1, START, true, "A");
        assertNotNull(pumpLine, "pump 攒满 600 帧必须出数");
        Matcher pm = match(Truth3Probe.PUMP_LINE_RE, pumpLine);
        assertEquals("A", pm.group(1));
        assertEquals("600", pm.group(2));
        assertEquals("3.000", pm.group(3), "p50 ms");
        assertEquals("3.000", pm.group(4), "p95 ms");
        assertEquals("333.3", pm.group(5), "fps_p50 = 1000/p50");
        assertEquals("3.00", pm.group(6), "max ms");

        // pump 的 601 帧不得污染 render 窗口：它仍只有原先的 300 个样本，
        // 需再补 302 帧（第 601 次 render 调用）才出 frame_probe。
        String frameLine = null;
        for (int i = 300; i < 602; i++) {
            String line = p.onRenderFrame(START + (long) i * STEP_NS);
            if (line != null) frameLine = line;
        }
        assertNotNull(frameLine, "render 窗口必须保留自己的 600 样本节奏");
        assertEquals("600", match(Truth3Probe.FRAME_LINE_RE, frameLine).group(2));
    }

    @Test
    void pumpReportsRuntimeModeNotConfigMode() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        // 第三参故意喂 "C"（配置字段），但运行时档位仍是 A：热键切档不写回配置，
        // 日志若报 C 就会在真机上把 D 档数据误标成 A 档（12:28 那次事故的复现）。
        String line = pump(p, Truth3Probe.REPORT_EVERY + 1, START, true, "C");
        assertNotNull(line);
        assertEquals("A", match(Truth3Probe.PUMP_LINE_RE, line).group(1),
                "frame_pump 必须报真实运行时档位，不得用配置字段伪装");
        assertEquals(Truth3Probe.Mode.A, p.mode(), "pump 不得改写运行时档位状态");
    }

    // --- 2. debug 门控 -------------------------------------------------------

    @Test
    void pumpIsSilentAndSamplesNothingWhenDebugIsOff() {
        Truth3Probe p = new Truth3Probe();
        assertNull(pump(p, Truth3Probe.REPORT_EVERY + 1, START, false, "A"),
                "debug=off 不得输出任何 frame_pump");
        // 重新打开后从干净窗口开始：601 帧正好一行、n=600（没有把关闭期间的帧算进来）。
        String line = pump(p, Truth3Probe.REPORT_EVERY + 1, START + 10_000_000_000L, true, "A");
        assertNotNull(line);
        assertEquals("600", match(Truth3Probe.PUMP_LINE_RE, line).group(2));
    }

    // --- 3. 切档重置 pump 窗口 ----------------------------------------------

    @Test
    void pumpWindowResetsWhenRuntimeModeChanges() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        assertNull(pump(p, 300, START, true, "A"), "不足 600 帧不输出");
        p.cycle(); // 真实热键切档 A -> B（配置字段始终不变，仍是 "A"）
        String line = pump(p, Truth3Probe.REPORT_EVERY + 1, START + 300L * STEP_NS, true, "A");
        assertNotNull(line, "切档后重新攒满 600 帧才输出");
        Matcher m = match(Truth3Probe.PUMP_LINE_RE, line);
        assertEquals("B", m.group(1), "标签必须跟随运行时档位");
        assertEquals("600", m.group(2), "切档前的 300 帧必须被清空");
    }

    @Test
    void pumpDiscardsNonContiguousIntervals() {
        Truth3Probe p = new Truth3Probe();
        assertNull(p.onPumpFrame(START, true, "A"));
        assertNull(p.onPumpFrame(START + 5_000_000_000L, true, "A"), "跨会话巨值不计样本");
        String line = null;
        for (int i = 1; i <= Truth3Probe.REPORT_EVERY + 1; i++) {
            String s = p.onPumpFrame(START + 5_000_000_000L + (long) i * STEP_NS, true, "A");
            if (s != null) line = s;
        }
        assertNotNull(line);
        assertTrue(Double.parseDouble(match(Truth3Probe.PUMP_LINE_RE, line).group(6)) < 10.0,
                "跨会话巨值不得污染 max: " + line);
    }

    // --- 4. measureOnly 门控 -------------------------------------------------

    @Test
    void measureOnlyTurnsOffHeavyInstrumentsButNeverTheFrameProbes() {
        assertFalse(Truth3Probe.instrumentEnabled(false, false), "debug=off 恒关");
        assertTrue(Truth3Probe.instrumentEnabled(true, false), "debug=on 且非 measureOnly → 全仪表");
        assertFalse(Truth3Probe.instrumentEnabled(true, true), "measureOnly 必须关掉重仪表");
        assertFalse(Truth3Probe.instrumentEnabled(false, true), "debug=off 优先");

        // 帧探针不经过 instrumentEnabled：measureOnly 下 frame_probe 照常出数。
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        String line = null;
        for (int i = 0; i < Truth3Probe.REPORT_EVERY + 1; i++) {
            String s = p.onRenderFrame(START + (long) i * STEP_NS);
            if (s != null) line = s;
        }
        assertNotNull(line, "measureOnly 只关重仪表，帧探针必须仍在");
        match(Truth3Probe.FRAME_LINE_RE, line);
    }

    // --- 5. 源码契约：默认值 / 门控点 / 注册点 -------------------------------

    @Test
    void measureOnlyIsOffByDefaultAndWiredAtTheRealGatePoints() throws Exception {
        assertTrue(src(CONFIG).contains("public boolean diagnosticMeasureOnly = false;"),
                "默认必须是 false：产品行为逐字节不变");
        assertTrue(src(CONFIG).contains("public boolean debug = false;"),
                "debug 默认仍未变");

        String env = src(ENV);
        assertTrue(env.contains("Truth3Probe.instrumentEnabled(config.debug, truth3.measureOnly())"),
                "FrameProfile 的开关必须走运行时 measureOnly 门控");
        assertTrue(env.contains("truth3.seedMeasureOnly(config.diagnosticMeasureOnly)"),
                "配置字段只做启动初始值");
        assertFalse(env.contains("FrameProfile.setEnabled(config.debug);"),
                "未加 measureOnly 的旧门控不得残留");

        for (String rel : RENDERERS) {
            String s = src(rel);
            assertEquals(3, count(s, "Truth3Probe.instrumentEnabled("),
                    rel + ": render / renderReuse / isAvailable 三处都必须走同一门控");
            assertTrue(s.contains("public static boolean renderReuse("), rel);
            assertFalse(s.contains("BlurMotionGate"), rel);
            assertEquals(1, count(s, "boolean full = true;"), rel);
        }

        // 三端每帧采样点各注册恰好 1 处，且都转给 FramePump（不重复计数）。
        for (String rel : CLIENTS) {
            assertEquals(1, count(src(rel), "FramePump.onFrame()"), rel + ": 每帧采样点只许注册 1 处");
        }
        assertTrue(src(PROBE).contains("\"frame_pump mode=\""), "日志行名必须是 frame_pump");
        assertTrue(src(PROBE).contains("public static final String PUMP_LINE_RE"));
    }
}
