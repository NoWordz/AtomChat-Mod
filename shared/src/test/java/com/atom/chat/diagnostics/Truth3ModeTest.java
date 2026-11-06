package com.atom.chat.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 「四档归零对照」实验包的行为与源码契约测试。全部在 shared 源集里跑，
 * 不启动游戏、不需要 GL/Minecraft 运行时。
 *
 * <p>本文件是原 11 个 Truth3ModeTest 的【四档扩展版】：原 11 个 @Test 全部保留，
 * 仅把「三档循环」断言同步更新为 A→B→C→D→A，并补 D 档接线 / 决策点断言。
 * 新增的 D 档限频 / cadence / 首次降级 / D→A 视觉恢复 / 复用不画脏等专项测试
 * 放在 {@code ModeDCadenceTest}。
 */
class Truth3ModeTest {

    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java");
    private static final String CONFIG = "shared/src/main/java/com/atom/chat/config/AtomChatConfig.java";
    private static final String PROBE = "shared/src/main/java/com/atom/chat/diagnostics/Truth3Probe.java";
    private static final String FRAME_PROFILE = "shared/src/main/java/com/atom/chat/diagnostics/FrameProfile.java";
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

    /** 按固定步长喂 n 帧，返回本次喂帧过程中最后一条非 null 日志。 */
    private static String feed(Truth3Probe p, int frames, long startNanos) {
        String out = null;
        for (int i = 0; i < frames; i++) {
            String line = p.onRenderFrame(startNanos + (long) i * STEP_NS);
            if (line != null) out = line;
        }
        return out;
    }

    private static Matcher match(String regex, String line) {
        Matcher m = Pattern.compile(regex).matcher(line);
        assertTrue(m.matches(), "不可解析: " + line);
        return m;
    }

    // --- 1. 默认恒为 A -----------------------------------------------------

    @Test
    void parseModeFallsBackToAForAnythingInvalid() {
        assertEquals(Truth3Probe.Mode.A, Truth3Probe.parseMode(null));
        assertEquals(Truth3Probe.Mode.A, Truth3Probe.parseMode(""));
        assertEquals(Truth3Probe.Mode.A, Truth3Probe.parseMode("  "));
        assertEquals(Truth3Probe.Mode.A, Truth3Probe.parseMode("X"));
        assertEquals(Truth3Probe.Mode.A, Truth3Probe.parseMode("b")); // 大小写严格，非法即 A
        assertEquals(Truth3Probe.Mode.A, Truth3Probe.parseMode("d")); // 大小写严格，非法即 A
        assertEquals(Truth3Probe.Mode.B, Truth3Probe.parseMode("B"));
        assertEquals(Truth3Probe.Mode.C, Truth3Probe.parseMode("C"));
        assertEquals(Truth3Probe.Mode.D, Truth3Probe.parseMode("D"));
    }

    @Test
    void freshProbeIsModeAWithFullProductRenderPath() {
        Truth3Probe p = new Truth3Probe();
        assertEquals(Truth3Probe.Mode.A, p.mode());
        assertTrue(p.drawPanel());
        assertTrue(p.blurPrepass());
        assertFalse(p.blurComposite(), "A 档绝不进入 D 的复用合成路径");
        assertFalse(p.isDebug());
    }

    /** 变异红灯锚点：全新配置字段默认值必须是 "A"。把 "A" 装回缺失（落到 "C"/"D"）此断言变红。 */
    @Test
    void freshConfigDefaultsToAAndDebugOffForcesAAndIsSilent() throws Exception {
        assertTrue(src(CONFIG).contains("public String diagnosticRenderMode = \"A\";"),
                "全新安装 / 缺失字段必须默认 A");

        Truth3Probe p = new Truth3Probe();
        assertNull(p.configure(false, "C"), "debug=off 必须完全静默");
        assertEquals(Truth3Probe.Mode.A, p.mode(), "debug=off 恒为 A");
        assertTrue(p.drawPanel());
        assertTrue(p.blurPrepass());
        assertFalse(p.blurComposite());
        assertNull(p.cycle(), "debug=off 时热键不得切档");
        assertEquals(Truth3Probe.Mode.A, p.mode());
        assertNull(feed(p, 1300, START), "debug=off 不得输出任何实验日志");
    }

    // --- 2. 四档切换日志格式可解析 -----------------------------------------

    @Test
    void modeSwitchLinesAreParseableAndCycleAToBToCToDToA() {
        Truth3Probe p = new Truth3Probe();
        String boot = p.configure(true, "A");
        assertNotNull(boot);
        match(Truth3Probe.SWITCH_LINE_RE, boot);
        assertTrue(boot.contains("from=A to=A"));

        String[][] expect = {{"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}};
        for (String[] e : expect) {
            String line = p.cycle();
            assertNotNull(line);
            Matcher m = match(Truth3Probe.SWITCH_LINE_RE, line);
            assertEquals(e[0], m.group(1));
            assertEquals(e[1], m.group(2));
            assertTrue(m.group(3).matches("[0-9]+"), "t 必须是 nanoTime 十进制单调值");
        }
    }

    @Test
    void frameProbeLineIsParseableAndCarriesModeAndStableFields() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        String line = feed(p, Truth3Probe.REPORT_EVERY + 1, START);
        assertNotNull(line);
        Matcher m = match(Truth3Probe.FRAME_LINE_RE, line);
        assertEquals("A", m.group(1));
        assertEquals("600", m.group(2));
        assertEquals("3.000", m.group(3), "p50 ms");
        assertEquals("3.000", m.group(4), "p95 ms");
        assertEquals("333.3", m.group(5), "fps_p50 = 1000/p50");
        assertEquals("3.00", m.group(6), "max ms");
    }

    @Test
    void frameProbeLineIsParseableForModeD() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "D");
        String line = feed(p, Truth3Probe.REPORT_EVERY + 1, START);
        assertNotNull(line);
        Matcher m = match(Truth3Probe.FRAME_LINE_RE, line);
        assertEquals("D", m.group(1));
    }

    // --- 3. 切档重置窗口 / 数据不串 ----------------------------------------

    @Test
    void modeSwitchResetsTheWindowSoModesNeverMix() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        assertNull(feed(p, 300, START), "不足 600 帧不输出");
        assertNotNull(p.cycle()); // A -> B，重置窗口
        assertNull(feed(p, 300, START + 300L * STEP_NS), "重置后仅 300 样本，不得输出");
        String line = feed(p, 301, START + 600L * STEP_NS);
        assertNotNull(line, "再攒满 600 帧才输出，且不含切档前的样本");
        assertEquals("B", match(Truth3Probe.FRAME_LINE_RE, line).group(1));
    }

    // --- 4. C 档采样器仍出数 ------------------------------------------------

    @Test
    void modeCStillEmitsFrameProbeLinesEvenThoughDrawPanelIsSkipped() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        p.cycle(); // B
        p.cycle(); // C
        assertEquals(Truth3Probe.Mode.C, p.mode());
        assertFalse(p.drawPanel());
        assertFalse(p.blurPrepass());
        assertFalse(p.blurComposite());
        String line = feed(p, Truth3Probe.REPORT_EVERY + 1, START);
        assertNotNull(line, "C 档旁路 drawPanel 后，独立采样器必须仍然出数");
        assertEquals("C", match(Truth3Probe.FRAME_LINE_RE, line).group(1));
    }

    // --- 5. B 档只跳 pre-pass / D 档复用合成 --------------------------------

    @Test
    void modeBKeepsDrawPanelAndOnlySkipsTheBlurPrePassAndModeDComposites() throws Exception {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        assertTrue(p.drawPanel());
        assertTrue(p.blurPrepass());
        assertFalse(p.blurComposite());
        p.cycle(); // B
        assertTrue(p.drawPanel(), "B 档 graphics.drawPanel 必须照常调用");
        assertFalse(p.blurPrepass(), "B 档只跳过背景模糊 pre-pass");
        assertFalse(p.blurComposite());
        p.cycle(); // C
        assertFalse(p.drawPanel());
        assertFalse(p.blurPrepass());
        assertFalse(p.blurComposite());
        p.cycle(); // D
        assertTrue(p.drawPanel(), "D 档仍画面板");
        assertFalse(p.blurPrepass(), "D 档不走 A 的 blurPrepass 判定");
        assertTrue(p.blurComposite(), "D 档走复用合成判定");

        // 三端同源：决策点、热键、采样入口、D 档接线在三个屏幕上逐字存在。
        for (String s : SCREENS) {
            String text = src(s);
            assertTrue(text.contains("Truth3Probe.get().drawPanel()"), s);
            // 档位句柄取一次、三处复用：A/B 与 D 走同一条 pre-pass 构造，
            // capture 只一处推导（PanelShellWiringSourceTest 的 one-transform 契约）。
            assertTrue(text.contains("Truth3Probe truth3Mode = com.atom.chat.diagnostics.Truth3Probe.get();"), s);
            assertTrue(text.contains("truth3Mode.blurPrepass()"), s);
            assertTrue(text.contains("truth3Mode.blurComposite()"), s);
            assertTrue(text.contains("truth3Mode.blurRefreshDue(nowNanos)"), s);
            assertTrue(text.contains("PanelBlurRenderer.renderReuse("), s);
            assertTrue(text.contains("probe.cadenceLine()"), s);
            assertTrue(text.contains("TRUTH3_KEY_F6 = 295"), s);
            assertTrue(text.contains("probe.onRenderFrame(nowNanos)"), s);
            assertTrue(text.contains("probe.configure(truth3Cfg.debug, truth3Cfg.diagnosticRenderMode)"), s);
        }
        // 产品默认未被改动：三端渲染器仍是每帧新鲜毛玻璃。
        for (String target : List.of("1.20.1-forge", "1.21.1-neoforge", "1.21.1-fabric")) {
            String blur = src("platforms/" + target + "/src/main/java/com/atom/chat/render/PanelBlurRenderer.java");
            assertFalse(blur.contains("BlurMotionGate"), target);
            assertTrue(blur.contains("boolean full = true;"), target);
            assertTrue(blur.contains("public static boolean renderReuse("), target);
        }
    }

    // --- 6. 切回 A 无损 -----------------------------------------------------

    @Test
    void switchingBackToAIsLossless() throws Exception {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        boolean draw0 = p.drawPanel();
        boolean blur0 = p.blurPrepass();
        boolean composite0 = p.blurComposite();
        p.cycle(); // B
        p.cycle(); // C
        p.cycle(); // D
        p.cycle(); // A
        assertEquals(Truth3Probe.Mode.A, p.mode());
        assertEquals(draw0, p.drawPanel(), "切回 A 后 drawPanel 决策与初始一致");
        assertEquals(blur0, p.blurPrepass(), "切回 A 后背景模糊 pre-pass 决策与初始一致");
        assertEquals(composite0, p.blurComposite(), "切回 A 后 D 合成决策与初始一致（均无残留）");

        // 离屏证明：C 档不进入任何 Skija / GL / 面板 profile 路径 —— 采样器是纯 Java，
        // 没有任何非 java.* 的 import，因此不存在 canvas save 栈 / borrowed surface /
        // PanelBlurRenderer FBO 状态可被 C 档改动；切回 A 时 drawPanel 自包含地完整执行。
        // D 档的状态全部封装在 Truth3Probe 的纯 Java 标量里，切档 resetBlurCadence 清空，
        // 不触碰任何 GL / Skija 资源，切回 A 无残留。
        String probeSrc = src(PROBE);
        for (String line : probeSrc.split("\\R")) {
            if (line.trim().startsWith("import ")) {
                assertTrue(line.trim().startsWith("import java."), "采样器不得依赖 MC/Skija/GL: " + line);
            }
        }
        assertFalse(probeSrc.contains("FrameSegments."), "不得触碰分段计时");
        assertFalse(probeSrc.contains("FrameProfile."), "不得触碰面板 profile");
        assertFalse(probeSrc.contains("GlGpuTimer."), "不得触碰 P1 GPU 计时");
        assertFalse(probeSrc.contains("System.currentTimeMillis"), "帧间隔必须用 nanoTime");

        String line = feed(p, Truth3Probe.REPORT_EVERY + 1, START);
        assertNotNull(line);
        assertEquals("A", match(Truth3Probe.FRAME_LINE_RE, line).group(1));
    }

    // --- 7. 档位不影响 FrameProfile 原有桶语义 ------------------------------

    @Test
    void modesLeaveFrameProfileBucketsUntouched() throws Exception {
        String fp = src(FRAME_PROFILE);
        assertTrue(fp.contains("static final int CAPACITY = 4096;"));
        assertTrue(fp.contains("static final int REPORT_EVERY_FRAMES = 600;"));
        assertTrue(fp.contains("private static final String PANEL = \"panel\";"));
        assertTrue(fp.contains("private static final String DECODE = \"decode\";"));
        assertTrue(fp.contains("private static final String LAYOUT = \"layout\";"));
        assertTrue(fp.contains("public enum Surface { CHAT, EMOJI, KAOMOJI, EMOTES, PHRASES, TOOLS, SEARCH, CONFIRMATION, OTHER }"));
        String probeSrc = src(PROBE);
        assertFalse(probeSrc.contains("import com.atom.chat.diagnostics.FrameProfile"));
        assertFalse(probeSrc.contains("FrameProfile.frame("));
    }

    // --- 8. 采样口径：跨会话巨值不得污染 ------------------------------------

    @Test
    void nonContiguousIntervalsAreDiscarded() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        p.onRenderFrame(START);
        p.onRenderFrame(START + 5_000_000_000L);
        String line = null;
        for (int i = 1; i <= Truth3Probe.REPORT_EVERY; i++) {
            String s = p.onRenderFrame(START + 5_000_000_000L + (long) i * STEP_NS);
            if (s != null) line = s;
        }
        assertNotNull(line);
        Matcher m = match(Truth3Probe.FRAME_LINE_RE, line);
        assertEquals("600", m.group(2));
        assertTrue(Double.parseDouble(m.group(6)) < 10.0, "跨会话巨值不得污染 max: " + line);
    }
}
