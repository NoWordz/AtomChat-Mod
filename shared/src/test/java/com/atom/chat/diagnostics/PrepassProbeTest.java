package com.atom.chat.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 背景模糊 pre-pass 分段计时探针的行为与源码契约测试。全部在 shared 源集里跑，
 * 不启动游戏、不需要 GL/Minecraft 运行时；数值一律离屏/受控，非真机测量。
 */
class PrepassProbeTest {

    private static final List<String> RENDERERS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/PanelBlurRenderer.java");
    private static final String PROBE = "shared/src/main/java/com/atom/chat/diagnostics/PrepassProbe.java";

    /** total=1.000ms, entry=0.100, capture=0.050, passes=0.400, state=0.080, composite=0.120 → unacc=0.250 */
    private static final long[] PARTS = {1_000_000L, 100_000L, 50_000L, 400_000L, 80_000L, 120_000L};

    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("platforms"))) p = p.getParent();
        return java.util.Objects.requireNonNull(p);
    }

    private static String src(String rel) throws Exception {
        return Files.readString(root().resolve(rel));
    }

    private static int count(String s, String needle) {
        return s.split(Pattern.quote(needle), -1).length - 1;
    }

    private static Matcher match(String regex, String line) {
        Matcher m = Pattern.compile(regex).matcher(line);
        assertTrue(m.matches(), "不可解析: " + line);
        return m;
    }

    /** 模拟真实帧序：每帧先 isAvailable() 的 onFrame tick，再 render() 的 record。 */
    private static String emit(PrepassProbe p, int mode, long[] parts, int frames) {
        String out = null;
        for (int i = 0; i < frames; i++) {
            String s = p.onFrame(mode, true);
            if (s != null) out = s;
            p.record(mode, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
        }
        return out;
    }

    private static double g(Matcher m, int group) {
        return Double.parseDouble(m.group(group));
    }

    // --- 1. 分段不重不漏：total >= sum(parts)（容差内） ----------------------

    @Test
    void totalCoversAllPartsWithinTolerance() {
        PrepassProbe p = new PrepassProbe();
        String line = emit(p, 0, PARTS, PrepassProbe.REPORT_EVERY + 1);
        assertNotNull(line, "攒满 600 帧必须输出一行");
        Matcher m = match(PrepassProbe.PREPASS_LINE_RE, line);
        double total = g(m, 4);
        double sum = g(m, 5) + g(m, 6) + g(m, 7) + g(m, 8) + g(m, 9);
        assertTrue(total + 0.001 >= sum, "total >= sum(parts): " + line);
    }

    // --- 2. 切档重置窗口 / 数据不串 ----------------------------------------

    @Test
    void modeSwitchResetsWindowSoModesNeverMix() {
        PrepassProbe p = new PrepassProbe();
        assertNull(emit(p, 0, PARTS, 300), "A 档不足 600 帧不输出");
        assertNull(p.onFrame(1, true), "A→B 切档当帧不输出，且清空窗口");
        String line = null;
        for (int i = 0; i < 599; i++) {
            String s = p.onFrame(1, true);
            if (s != null) line = s;
        }
        assertNotNull(line, "B 档攒满 600 帧输出（不含 A 档 300 个样本）");
        Matcher m = match(PrepassProbe.PREPASS_LINE_RE, line);
        assertEquals("B", m.group(1));
        assertEquals("0", m.group(2), "切档重置后 B 档窗口无 A 档样本");
    }

    // --- 3. B/C 档 skipped（不伪造数据） ------------------------------------

    @Test
    void modesBAndCEmitSkippedWithoutFabricatedData() {
        for (int mode : new int[]{1, 2}) {
            PrepassProbe p = new PrepassProbe();
            String line = null;
            for (int i = 0; i < PrepassProbe.REPORT_EVERY; i++) {
                String s = p.onFrame(mode, true);
                if (s != null) line = s;
            }
            assertNotNull(line, "B/C 档跳过 pre-pass 也必须按节奏输出");
            Matcher m = match(PrepassProbe.PREPASS_LINE_RE, line);
            assertEquals(mode == 1 ? "B" : "C", m.group(1));
            assertEquals("0", m.group(2), "n=0");
            assertEquals("1", m.group(3), "skipped=1");
            for (int group = 4; group <= 10; group++) {
                assertEquals("0.000", m.group(group), "不得伪造任何分段数值");
            }
        }
    }

    // --- 4. 日志格式可解析 / 字段稳定 --------------------------------------

    @Test
    void prepassLineIsParseableAndCarriesStableFields() {
        PrepassProbe p = new PrepassProbe();
        String line = emit(p, 0, PARTS, PrepassProbe.REPORT_EVERY + 1);
        assertNotNull(line);
        Matcher m = match(PrepassProbe.PREPASS_LINE_RE, line);
        assertEquals("A", m.group(1));
        assertTrue(Integer.parseInt(m.group(2)) >= 1, "n 为窗口内实际样本数");
        assertEquals("0", m.group(3), "A 档 skipped=0");
        assertEquals("1.000", m.group(4), "total ms");
        assertEquals("0.100", m.group(5), "entry ms");
        assertEquals("0.050", m.group(6), "capture ms");
        assertEquals("0.400", m.group(7), "passes ms");
        assertEquals("0.080", m.group(8), "state ms");
        assertEquals("0.120", m.group(9), "composite ms");
        assertEquals("0.250", m.group(10), "unaccounted ms");
    }

    // --- 5. debug=off 完全静默 ---------------------------------------------

    @Test
    void debugOffIsCompletelySilentAndResumesClean() {
        PrepassProbe p = new PrepassProbe();
        assertNull(p.onFrame(0, false));
        for (int i = 0; i < 2000; i++) {
            assertNull(p.onFrame(0, false), "debug=off 不得输出任何实验日志");
        }
        String line = emit(p, 0, PARTS, PrepassProbe.REPORT_EVERY + 1);
        assertNotNull(line, "恢复 debug=on 后仍能正常出数");
        Matcher m = match(PrepassProbe.PREPASS_LINE_RE, line);
        assertEquals("A", m.group(1));
        assertEquals("1.000", m.group(4), "恢复后窗口干净，不被 off 期间污染");
    }

    // --- 6. unaccounted 计算正确 / 逐样本钳制 ------------------------------

    @Test
    void unaccountedEqualsTotalMinusSumOfPartsAndIsClampedPerSample() {
        PrepassProbe p = new PrepassProbe();
        String line = emit(p, 0, PARTS, PrepassProbe.REPORT_EVERY + 1);
        Matcher m = match(PrepassProbe.PREPASS_LINE_RE, line);
        double total = g(m, 4);
        double sum = g(m, 5) + g(m, 6) + g(m, 7) + g(m, 8) + g(m, 9);
        assertEquals(total - sum, g(m, 10), 0.001, "mean(unaccounted) = mean(total) − Σ mean(parts)");

        // 异常样本（sum > total，如早退路径残留）：逐样本钳制，unacc 不得为负、不得伪造。
        long[] bad = {100_000L, 90_000L, 90_000L, 90_000L, 90_000L, 90_000L};
        PrepassProbe q = new PrepassProbe();
        String badLine = emit(q, 0, bad, PrepassProbe.REPORT_EVERY + 1);
        Matcher bm = match(PrepassProbe.PREPASS_LINE_RE, badLine);
        assertTrue(g(bm, 10) >= 0.0, "unaccounted 不得为负");
        assertEquals(0.0, g(bm, 10), 0.001, "逐样本钳制到 0");
    }

    // --- 7. 不产生无界分配 -------------------------------------------------

    @Test
    void ringWindowIsBoundedAndNeverReallocated() throws Exception {
        PrepassProbe p = new PrepassProbe();
        java.lang.reflect.Field f = PrepassProbe.class.getDeclaredField("ring");
        f.setAccessible(true);
        long[][] ring = (long[][]) f.get(p);
        assertEquals(7, ring.length, "7 个字段的环形窗口");
        for (long[] row : ring) assertEquals(PrepassProbe.WINDOW, row.length);
        p.onFrame(0, true);
        for (int i = 0; i < 5000; i++) {
            p.record(0, PARTS[0], PARTS[1], PARTS[2], PARTS[3], PARTS[4], PARTS[5]);
        }
        assertSame(ring, f.get(p), "环形窗口不得被替换/扩容");
    }

    // --- 8. 三端接线语义一致 + 红线 + 探针纯 Java ---------------------------

    @Test
    void allThreeRenderersWireTheProbeIdenticallyAndKeepRedLines() throws Exception {
        List<String> baseline = null;
        for (String rel : RENDERERS) {
            String s = src(rel);
            // 红线：产品默认每帧新鲜毛玻璃 / P0-C / P0-A / 不动 P1 GPU 计时
            assertFalse(s.contains("BlurMotionGate"), rel);
            assertEquals(1, count(s, "boolean full = true;"), rel);
            assertTrue(s.contains("errorCheckDue"), rel + " P0-C 必须保留");
            assertTrue(s.contains("capacityMode"), rel + " P0-A 必须保留");
            assertTrue(s.contains("nextPow2"), rel);
            assertTrue(s.contains("extendEdge"), rel);
            assertEquals(2, count(s, "GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)"),
                    rel + " P0-C 削减后入口查询调用点应恰为 2");
            // 探针接线
            assertEquals(13, count(s, "System.nanoTime()"), rel
                    + " 计时点数量（原 render() 11 个 + renderReuse() 的 prM0/prM1 2 个）");
            assertTrue(s.contains("public static boolean renderReuse("), rel + " D 档复用合成入口");
            assertTrue(s.contains("prM1 - prM0"), rel + " D 复用帧 composite 样本");
            assertTrue(s.contains("PrepassProbe.get().record("), rel);
            assertTrue(s.contains("PrepassProbe.get().onFrame("), rel);
            assertTrue(s.contains("ppM9 - ppM0"), rel + " ⭐ total 统计（变异红灯锚点）");
            assertTrue(s.contains("ppM9 - ppM8"), rel + " state 段");
            assertTrue(s.contains("ppM7 - ppM6"), rel + " composite 段");
            assertFalse(s.contains("glFinish"), rel + " 禁止阻塞式同步");
            assertFalse(s.contains("System.currentTimeMillis"), rel + " 必须用 nanoTime");
            assertFalse(s.contains("GpuTimerQuery"), rel + " 不碰 P1 GPU 计时");

            // 三端探针代码块逐行一致（平台差异行不含这些关键词，天然被排除）。
            List<String> lines = new ArrayList<>();
            for (String l : s.split("\\R")) {
                String t = l.trim();
                if (t.contains("ppM") || t.contains("PrepassProbe")
                        || t.contains("probeOn") || t.contains("prepass")) {
                    lines.add(t);
                }
            }
            if (baseline == null) baseline = lines;
            else assertEquals(baseline, lines, "三端探针接线语义必须一致: " + rel);
        }

        String probe = src(PROBE);
        for (String l : probe.split("\\R")) {
            if (l.trim().startsWith("import ")) {
                assertTrue(l.trim().startsWith("import java."), "探针不得依赖 MC/Skija/GL: " + l);
            }
        }
        assertFalse(probe.contains("FrameSegments."));
        assertFalse(probe.contains("FrameProfile."));
        assertFalse(probe.contains("GlGpuTimer."));
        assertFalse(probe.contains("GpuTimerQuery."));
        assertFalse(probe.contains("System.currentTimeMillis"));
        assertFalse(probe.contains("glFinish"));
        assertTrue(probe.contains("public static final int WINDOW = 1024;"));
        assertTrue(probe.contains("public static final int REPORT_EVERY = 600;"));
    }
}
