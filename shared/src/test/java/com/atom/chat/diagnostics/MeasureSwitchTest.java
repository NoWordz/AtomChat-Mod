package com.atom.chat.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E0 运行时仪表开关（Shift+F6）的行为与源码契约测试。
 *
 * <p>为什么需要它：`measureOnly` 若只能靠改配置 + 重开面板/重启来切换，E0 的
 * ABAB 交替（每档 10–12 秒、4–6 个来回）就无法在同一会话内完成；而两次启动之间的
 * 漂移（0.4ms 量级）远大于要测的效应（0.02–0.05ms）。热键把切换成本压到一次按键，
 * 并同时重置两个采样窗口，保证数据不跨仪表配置混合。
 *
 * <p>全部在 shared 源集里跑，不启动游戏、不需要 GL/Minecraft 运行时。
 */
class MeasureSwitchTest {

    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java");
    private static final String CONFIG = "shared/src/main/java/com/atom/chat/config/AtomChatConfig.java";
    private static final String ENV = "shared/src/main/java/com/atom/chat/diagnostics/EnvironmentSummary.java";
    private static final String PROBE = "shared/src/main/java/com/atom/chat/diagnostics/Truth3Probe.java";
    private static final long STEP_NS = 3_000_000L; // 3.0 ms/帧 ≈ 333 FPS（离屏/受控）
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

    private static String feedRender(Truth3Probe p, int frames, long start) {
        String out = null;
        for (int i = 0; i < frames; i++) {
            String s = p.onRenderFrame(start + (long) i * STEP_NS);
            if (s != null) out = s;
        }
        return out;
    }

    private static String feedPump(Truth3Probe p, int frames, long start) {
        String out = null;
        for (int i = 0; i < frames; i++) {
            String s = p.onPumpFrame(start + (long) i * STEP_NS, true, "A");
            if (s != null) out = s;
        }
        return out;
    }

    // --- 1. 只在 debug=on 生效 ----------------------------------------------

    @Test
    void shiftF6OnlyWorksWhenDebugIsOn() {
        Truth3Probe p = new Truth3Probe();
        assertFalse(p.isDebug());
        assertNull(p.toggleMeasureOnly(), "debug=off 必须完全无反应");
        assertFalse(p.measureOnly(), "debug=off 不得改变状态");

        p.configure(true, "A");
        String on = p.toggleMeasureOnly();
        assertNotNull(on);
        Matcher m = match(Truth3Probe.MEASURE_LINE_RE, on);
        assertEquals("true", m.group(1));
        assertTrue(m.group(2).matches("[0-9]+"), "t 必须是 nanoTime 十进制值");
        assertTrue(p.measureOnly());

        String off = p.toggleMeasureOnly();
        assertEquals("false", match(Truth3Probe.MEASURE_LINE_RE, off).group(1));
        assertFalse(p.measureOnly());

        // debug 再次关闭后按键仍不得改变状态
        p.configure(true, "A");
        p.toggleMeasureOnly(); // true
        p.configure(false, "A");
        assertNull(p.toggleMeasureOnly());
        assertTrue(p.measureOnly(), "debug=off 按下不得翻转");
    }

    // --- 2. 翻转必须同时重置两个窗口 ----------------------------------------

    @Test
    void toggleResetsBothFrameProbeAndFramePumpWindows() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");

        // 两个窗口各攒 400 帧（399 个样本，不足 600）。
        assertNull(feedRender(p, 400, START));
        assertNull(feedPump(p, 400, START));

        String line = p.toggleMeasureOnly();
        assertNotNull(line);

        // 若不重置：399+399=798 ≥ 600，下一次喂帧就会出数。重置后必须仍是 null。
        assertNull(feedRender(p, 400, START + 10_000_000_000L),
                "frame_probe 窗口必须在翻转时清空");
        assertNull(feedPump(p, 400, START + 10_000_000_000L),
                "frame_pump 窗口必须在翻转时清空");

        // 重置后各自重新攒满 600 个样本才出数（再补 202 次调用 = 201 个样本）。
        String frame = feedRender(p, 202, START + 20_000_000_000L);
        assertNotNull(frame, "重置后满 600 个间隔应重新出 frame_probe");
        assertEquals("600", match(Truth3Probe.FRAME_LINE_RE, frame).group(2));

        String pump = feedPump(p, 202, START + 20_000_000_000L);
        assertNotNull(pump, "重置后满 600 个间隔应重新出 frame_pump");
        assertEquals("600", match(Truth3Probe.PUMP_LINE_RE, pump).group(2));
    }

    // --- 3. 配置字段只做初始值 / 热键不写回 ----------------------------------

    @Test
    void configIsOnlyTheSeedAndTheHotkeyPersistsNothing() throws Exception {
        // 一次性灌入：第二次 seed 不得覆盖运行时状态（否则每 tick 的同步会把热键结果冲掉）。
        Truth3Probe p = new Truth3Probe();
        assertFalse(p.measureOnly(), "默认运行时状态必须与默认配置一致");
        p.seedMeasureOnly(true);
        assertTrue(p.measureOnly());
        p.seedMeasureOnly(false);
        assertTrue(p.measureOnly(), "seed 只能生效一次");

        // 结构性证明：Truth3Probe 是纯 Java，既不认识配置类，也没有任何文件写路径。
        String probe = src(PROBE);
        assertFalse(probe.contains("AtomChatConfig"), "探针不得读取配置对象");
        assertFalse(probe.contains(".save("), "探针不得写配置");
        assertFalse(probe.contains("java.io"), "探针不得做文件 I/O");
        assertFalse(probe.contains("java.nio"), "探针不得做文件 I/O");
        assertTrue(probe.contains("public void seedMeasureOnly(boolean initial)"));
        assertTrue(probe.contains("public String toggleMeasureOnly()"));

        // 屏幕只调用翻转，不碰配置字段（初始值由 EnvironmentSummary 灌入）。
        for (String rel : SCREENS) {
            String s = src(rel);
            assertEquals(1, count(s, "truth3.toggleMeasureOnly()"), rel);
            assertEquals(0, count(s, "diagnosticMeasureOnly"),
                    rel + ": 屏幕不得读取/写回 measureOnly 配置字段");
        }
        assertTrue(src(ENV).contains("truth3.seedMeasureOnly(config.diagnosticMeasureOnly)"),
                "初始值必须在每 tick 的同步点灌入一次");
    }

    // --- 4. 默认值下行为不变 -------------------------------------------------

    @Test
    void defaultConfigAndRuntimeBothMeanFullInstrumentation() throws Exception {
        assertTrue(src(CONFIG).contains("public boolean diagnosticMeasureOnly = false;"),
                "默认必须是 false");
        assertTrue(src(CONFIG).contains("public boolean debug = false;"));

        Truth3Probe p = new Truth3Probe();
        assertFalse(p.measureOnly());
        p.seedMeasureOnly(false);
        assertFalse(p.measureOnly());
        // measureOnly=false 时门控退化为原来的 debug 判断。
        assertEquals(p.isDebug(), Truth3Probe.instrumentEnabled(p.isDebug(), p.measureOnly()));
        assertTrue(Truth3Probe.instrumentEnabled(true, false));
        assertFalse(Truth3Probe.instrumentEnabled(true, true));
    }

    // --- 5. 三端热键同源、且不与 F6 循环抢键 --------------------------------

    @Test
    void allThreeScreensWireShiftF6IdenticallyNextToTheF6Cycle() throws Exception {
        for (String rel : SCREENS) {
            String s = src(rel);
            assertEquals(1, count(s, "boolean measureKey = keyCode == TRUTH3_KEY_F6 && (modifiers & 15) == 1;"),
                    rel + ": Shift+F6 必须只认「仅 Shift」修饰");
            assertEquals(1, count(s, "truth3.toggleMeasureOnly()"), rel);
            assertEquals(1, count(s, "truth3.cycle()"), rel + ": 裸 F6 循环必须原样保留");
            assertTrue(s.contains("if (measureKey && truth3.isDebug())"),
                    rel + ": 热键必须先过 debug 门控");
            assertEquals(1, count(s, "PanelOpenMotion.PanelRect capture = PanelOpenMotion.blurCapture("),
                    rel + ": one-transform 契约仍只能 1 处");
        }
    }
}
