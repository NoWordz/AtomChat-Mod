package com.atom.chat.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 同包第四档 D（Muonium 时序策略）专项行为 / 源码契约测试。全部在 shared 源集里跑，
 * 不启动游戏、不需要 GL/Minecraft 运行时；数值一律离屏/受控，非真机测量。
 *
 * <p>覆盖：默认恒 A、四档循环顺序、D 限频按时间而非帧数、首次进入 D 的降级、
 * D→A 视觉恢复、复用旧档不画脏（源码契约 + 状态机）、mode=D 三套日志可解析、
 * 切档重置、C 档仍 skipped、子项默认关、探针纯 Java。
 *
 * <p>「复用旧帧逐像素正确」的实证由离屏 GL 探针
 * {@code build/mode-d-20261106/ModeDCadenceProbe.java} 承担（真实 shader +
 * readback），本文件只做源码级与状态机级契约证明。
 */
class ModeDCadenceTest {

    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java");
    private static final List<String> TARGETS = List.of("1.20.1-forge", "1.21.1-neoforge", "1.21.1-fabric");
    private static final String CONFIG = "shared/src/main/java/com/atom/chat/config/AtomChatConfig.java";
    private static final String PROBE = "shared/src/main/java/com/atom/chat/diagnostics/Truth3Probe.java";
    private static final String PREPASS = "shared/src/main/java/com/atom/chat/diagnostics/PrepassProbe.java";
    private static final long STEP_NS = 3_000_000L;   // 3.0 ms/帧 ≈ 333 FPS（离屏/受控）
    private static final long START = 1_000_000L;

    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("platforms"))) p = p.getParent();
        return java.util.Objects.requireNonNull(p);
    }

    private static String src(String rel) throws Exception {
        return Files.readString(root().resolve(rel));
    }

    private static String renderer(String target) throws Exception {
        return src("platforms/" + target + "/src/main/java/com/atom/chat/render/PanelBlurRenderer.java");
    }

    /** 字面量出现次数（源码契约断言用；不解释正则，避免被转义细节带偏）。 */
    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static Matcher match(String regex, String line) {
        Matcher m = Pattern.compile(regex).matcher(line);
        assertTrue(m.matches(), "不可解析: " + line);
        return m;
    }

    /** 构造一个 D 档 probe，按固定步长喂 frames 帧，返回刷新次数。 */
    private static int refreshCount(long stepNs, int frames) {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "D");
        long t = START;
        int r = 0;
        for (int i = 0; i < frames; i++) {
            if (p.blurRefreshDue(t)) r++;
            t += stepNs;
        }
        return r;
    }

    // --- 1. 默认恒 A（debug=off / 新配置 / 未按键） --------------------------

    @Test
    void defaultsToAWithDebugOffNewConfigAndNoKey() throws Exception {
        String cfg = src(CONFIG);
        assertTrue(cfg.contains("public String diagnosticRenderMode = \"A\";"), "产品默认字段必须 A");
        Truth3Probe p = new Truth3Probe();
        assertEquals(Truth3Probe.Mode.A, p.mode());
        assertTrue(p.drawPanel());
        assertTrue(p.blurPrepass());
        assertFalse(p.blurComposite());
        // debug=off 即使配置字段写成 "D" 也恒 A 且静默
        assertNull(p.configure(false, "D"));
        assertEquals(Truth3Probe.Mode.A, p.mode());
        assertNull(p.cycle());
        assertNull(p.onRenderFrame(START));
        assertNull(p.cadenceLine());
        assertTrue(p.blurRefreshDue(START), "A 档 blurRefreshDue 保守 true");
    }

    // --- 2. 四档循环顺序 A→B→C→D→A -----------------------------------------

    @Test
    void fourModeCycleOrderAndSwitchLinesAreParseable() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        String[] expect = {"B", "C", "D", "A"};
        for (String e : expect) {
            String line = p.cycle();
            assertNotNull(line);
            Matcher m = match(Truth3Probe.SWITCH_LINE_RE, line);
            assertTrue(m.group(2).equals(e), "循环到 " + e + "，实际 " + m.group(2));
        }
        assertEquals(Truth3Probe.Mode.A, p.mode());
        // 配置字段 "D" 可直接进入 D
        Truth3Probe q = new Truth3Probe();
        q.configure(true, "D");
        assertEquals(Truth3Probe.Mode.D, q.mode());
        assertTrue(q.blurComposite());
    }

    // --- 3. D 档限频按时间而非帧数 ------------------------------------------

    @Test
    void blurCadenceIsTimeDrivenNotFrameDriven() {
        assertEquals(33_333_333L, Truth3Probe.BLUR_CADENCE_NS, "阈值 ≥33_333_333ns（≈30Hz）");
        assertEquals(30.0, Truth3Probe.BLUR_CADENCE_HZ, 1e-9);
        // 同帧数(40)、不同墙钟：若按帧数限频则刷新次数相同；按时间则长墙钟更多。
        int fast = refreshCount(1_000_000L, 40);   // 40 帧 × 1.0ms = 40ms
        int slow = refreshCount(2_500_000L, 40);   // 40 帧 × 2.5ms = 100ms
        assertTrue(slow > fast, "同为 40 帧，100ms 墙钟刷新(" + slow + ") 应多于 40ms(" + fast + ") ⇒ 按时间");
        // 同墙钟(≈100ms)、不同帧数：按时间则刷新次数近似相同。
        int a = refreshCount(2_500_000L, 40);      // 100ms / 40 帧
        int b = refreshCount(3_333_333L, 30);      // 100ms / 30 帧
        assertTrue(Math.abs(a - b) <= 1, "同为 ~100ms 墙钟，40 帧(" + a + ") 与 30 帧(" + b + ") 刷新次数近似相同");
        assertTrue(a >= 3 && a <= 5, "30Hz over 100ms -> 3~5 次刷新，实际 " + a);
    }

    // --- 4. 首次进入 D 的降级（首帧强制刷新，不复用空档） -------------------

    @Test
    void firstFrameInDForcesRefreshAsDegradePath() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "A");
        p.cycle(); p.cycle(); p.cycle();  // A→B→C→D
        assertEquals(Truth3Probe.Mode.D, p.mode());
        assertTrue(p.blurRefreshDue(START), "首次进入 D 首帧必须强制刷新（产生第一代结果）");
        assertFalse(p.blurRefreshDue(START + 3_000_000L), "次帧（<33.33ms）复用上一代结果");
        assertTrue(p.blurRefreshDue(START + 34_000_000L), "≥33.33ms 到点刷新");
        // 切回 A 再切回 D：仍走首帧降级（切档 reset）
        p.cycle(); // D→A
        p.cycle(); p.cycle(); p.cycle(); // A→B→C→D
        assertTrue(p.blurRefreshDue(50_000_000_000L), "再次进入 D 仍首帧强制刷新");
    }

    // --- 5. D→A 视觉恢复（每帧完整刷新、无 D 残留） -------------------------

    @Test
    void dToARestoresFullEveryFrameBlurWithNoResidue() throws Exception {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "D");
        p.blurRefreshDue(START);
        p.cycle(); // D→A
        assertEquals(Truth3Probe.Mode.A, p.mode());
        assertTrue(p.blurPrepass(), "A 档每帧完整 pre-pass");
        assertFalse(p.blurComposite(), "A 档不进 D 复用合成");
        assertTrue(p.blurRefreshDue(9_000_000_000L), "A 档保守 true（不计数）");
        assertNull(p.cadenceLine(), "A 档不输出 blur_cadence");

        // 源码级：A 与 D 共用同一条 pre-pass 构造（capture 只有一处推导，见
        // PanelShellWiringSourceTest 的 one-transform 契约），档位只在
        // render（A 每帧新鲜毛玻璃）/ renderReuse（D 复用旧档）之间选择。
        // D 不成立时 reusePrevFrame 恒 false，A 路径绝不触达 renderReuse。
        for (String s : SCREENS) {
            String text = src(s);
            assertTrue(text.contains("truth3Mode.blurPrepass() || blurComposite"), s + " A 与 D 共用同一入口");
            assertTrue(text.contains("PanelBlurRenderer.render("), s + " A 完整 pre-pass");
            assertEquals(1, count(text, "PanelOpenMotion.PanelRect capture = PanelOpenMotion.blurCapture("),
                    s + " capture 只能一处推导（既有 one-transform 契约）");
            int dIdx = text.indexOf("boolean blurComposite = truth3Mode.blurComposite();");
            int gateIdx = text.indexOf("final boolean reusePrevFrame = blurComposite");
            assertTrue(dIdx >= 0 && gateIdx > dIdx, s + " D 判定先于复用门控");
            // D→A：切档后 blurComposite=false，复用门控短路为 false，A 每帧完整刷新。
            assertTrue(text.contains("blurComposite && panelSettled"), s + " 复用须同时满足 D 与面板已稳定");
        }
        for (String t : TARGETS) {
            String blur = renderer(t);
            assertTrue(blur.contains("boolean full = true;"), t + " full=true 原样");
            assertFalse(blur.contains("BlurMotionGate"), t + " 禁止恢复 BlurMotionGate");
        }
    }

    // --- 6. 复用旧档不画脏（源码契约 + 状态机） -------------------------------

    @Test
    void reuseFrameCannotSampleUninitializedOrStaleDestroyedTexture() throws Exception {
        for (String t : TARGETS) {
            String blur = renderer(t);
            // 二级降级：无有效旧档 / 纹理重建 → 强制完整 render，绝不采样空档
            assertTrue(blur.contains("if (lastBlurTex == -1 || recreated)"), t + " 复用前降级检查");
            assertTrue(blur.contains("return render(pose, x, y, width, height, radius, alpha, strength);"),
                    t + " 降级走完整 render");
            // 复用帧只做圆角合成，不重跑 blit + 5 pass
            assertTrue(blur.contains("drawRoundedQuad(pose, x, y, width, height, radius, alpha, lastBlurTex)"),
                    t + " 复用上一代结果纹理合成");
            assertTrue(blur.contains("prM1 - prM0, 0L, 0L, 0L, 0L, prM1 - prM0"),
                    t + " 复用帧 passes=0 样本");
            assertTrue(blur.contains("refreshBlur("), t + " refreshBlur 仍存在（A 路径）");
            // 不改 P0-A 容量复用 / 半径 / 尺寸
            assertTrue(blur.contains("capacityMode") && blur.contains("nextPow2") && blur.contains("extendEdge"),
                    t + " P0-A 容量复用语义保留");
        }
        // blurDrawnThisFrame 语义：三端把 renderReuse / render 的返回值写入它，
        // Skia 靠它决定半透明 tint（玻璃不消失）还是不透明实色 fallback。
        for (String s : SCREENS) {
            String text = src(s);
            assertTrue(text.contains("blurDrawnThisFrame = reusePrevFrame"), s + " 分档结果写入 blurDrawnThisFrame");
            assertTrue(text.contains("? PanelBlurRenderer.renderReuse("), s + " 非刷新帧复用合成");
            assertTrue(text.contains(": PanelBlurRenderer.render("), s + " 刷新帧走完整 pre-pass");
            assertTrue(text.contains("PanelBackground.tintFor"), s + " tintFor 仍按 blurDrawnThisFrame 决策");
        }
    }

    // --- 7. mode=D 三套日志 + cadence 格式可解析 -----------------------------

    @Test
    void modeDLogsAreParseableAcrossAllThreeProbeFamilies() {
        // mode_switch
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "C");
        Matcher sw = match(Truth3Probe.SWITCH_LINE_RE, p.cycle()); // C→D
        assertEquals("C", sw.group(1));
        assertEquals("D", sw.group(2));
        // frame_probe
        Truth3Probe f = new Truth3Probe();
        f.configure(true, "D");
        String frame = null;
        for (int i = 0; i < Truth3Probe.REPORT_EVERY + 1; i++) {
            String s = f.onRenderFrame(START + (long) i * STEP_NS);
            if (s != null) frame = s;
        }
        assertNotNull(frame);
        assertEquals("D", match(Truth3Probe.FRAME_LINE_RE, frame).group(1));
        // prepass_probe mode=D
        PrepassProbe pp = new PrepassProbe();
        String pl = null;
        for (int i = 0; i < PrepassProbe.REPORT_EVERY; i++) {
            String s = pp.onFrame(3, true);
            if (s != null) pl = s;
            pp.record(3, 28_000L, 0L, 0L, 0L, 0L, 28_000L);
        }
        assertNotNull(pl);
        Matcher pm = match(PrepassProbe.PREPASS_LINE_RE, pl);
        assertEquals("D", pm.group(1));
        assertEquals("0", pm.group(3), "D 档 skipped=0");
        // blur_cadence
        Truth3Probe c = new Truth3Probe();
        c.configure(true, "D");
        long t = START;
        String cad = null;
        for (int i = 0; i < Truth3Probe.REPORT_EVERY; i++) {
            c.blurRefreshDue(t);
            t += STEP_NS;
            String s = c.cadenceLine();
            if (s != null) cad = s;
        }
        assertNotNull(cad);
        Matcher cm = match(Truth3Probe.CADENCE_LINE_RE, cad);
        assertEquals("30.0", cm.group(1));
        int n = Integer.parseInt(cm.group(2));
        int r = Integer.parseInt(cm.group(3));
        int u = Integer.parseInt(cm.group(4));
        assertEquals(Truth3Probe.REPORT_EVERY, n);
        assertEquals(n, r + u, "refresh + reuse == n");
        assertTrue(r > 0 && u > 0 && u > r, "刷新与复用并存、复用占多数");
        assertEquals(Truth3Probe.BLUR_CADENCE_NS, Long.parseLong(cm.group(5)));
        double eff = Double.parseDouble(cm.group(6));
        assertTrue(eff > 25.0 && eff < 35.0, "eff_hz ~30，实际 " + eff);
        // 三套正则均接受 mode=D 字面量
        assertTrue(Pattern.compile(PrepassProbe.PREPASS_LINE_RE).matcher(
                "[AtomChat][probe] prepass_probe mode=D n=600 skipped=0 total=0.044 entry=0.002 "
                        + "capture=0.000 passes=0.014 state=0.000 composite=0.028 unaccounted=0.000").matches());
    }

    // --- 8. 切档重置（不串档、cadence 窗口清空） -----------------------------

    @Test
    void switchingResetsAllSamplingWindowsAndCadenceState() {
        Truth3Probe p = new Truth3Probe();
        p.configure(true, "D");
        long t = START;
        for (int i = 0; i < 300; i++) { p.blurRefreshDue(t); t += STEP_NS; }
        // 未满 600 帧不得输出 cadence
        assertNull(p.cadenceLine(), "D 档不足 600 帧不输出 cadence");
        String line = p.cycle(); // D→A，重置
        assertNotNull(line);
        assertNull(p.cadenceLine(), "切到 A 后 cadence 窗口已清空、A 不输出");
        // 再切回 D：从头累计，不会带出上一段 D 的样本
        p.cycle(); p.cycle(); p.cycle(); // A→B→C→D
        assertNull(p.cadenceLine(), "重新进入 D，cadence 从干净窗口开始");
        for (int i = 0; i < Truth3Probe.REPORT_EVERY; i++) { p.blurRefreshDue(t); t += STEP_NS; }
        assertNotNull(p.cadenceLine(), "重新攒满 600 帧才输出");

        // frame_probe 窗口也在切档时重置（不串档）
        Truth3Probe q = new Truth3Probe();
        q.configure(true, "A");
        assertNull(q.onRenderFrame(START));
        for (int i = 1; i < 300; i++) assertNull(q.onRenderFrame(START + (long) i * STEP_NS));
        q.cycle(); // A→B
        for (int i = 0; i < 300; i++) assertNull(q.onRenderFrame(START + 1000L * STEP_NS + (long) i * STEP_NS));
    }

    // --- 9. C 档仍 skipped（B 亦 skipped） ----------------------------------

    @Test
    void modesBAndCStillEmitSkippedWhileDIsMeasured() {
        for (int mode : new int[]{1, 2}) {
            PrepassProbe q = new PrepassProbe();
            String s = null;
            for (int i = 0; i < PrepassProbe.REPORT_EVERY; i++) {
                String x = q.onFrame(mode, true);
                if (x != null) s = x;
            }
            assertNotNull(s);
            Matcher m = match(PrepassProbe.PREPASS_LINE_RE, s);
            assertEquals(mode == 1 ? "B" : "C", m.group(1));
            assertEquals("0", m.group(2));
            assertEquals("1", m.group(3), "skipped=1");
        }
        // D 档：每帧一个样本（刷新帧完整 + 复用帧 passes=0），skipped=0、passes 显著低于 A
        long[] A = {214_000L, 22_000L, 0L, 164_000L, 0L, 28_000L};
        long[] REUSE = {28_000L, 0L, 0L, 0L, 0L, 28_000L};
        PrepassProbe d = new PrepassProbe();
        String dl = null;
        for (int i = 0; i < PrepassProbe.REPORT_EVERY; i++) {
            String x = d.onFrame(3, true);
            if (x != null) dl = x;
            long[] v = (i % 12 == 0) ? A : REUSE;  // 模拟 30Hz @ ~2.5ms/帧
            d.record(3, v[0], v[1], v[2], v[3], v[4], v[5]);
        }
        assertNotNull(dl);
        Matcher m = match(PrepassProbe.PREPASS_LINE_RE, dl);
        assertEquals("D", m.group(1));
        assertEquals("0", m.group(3), "D 档 skipped=0");
        double passes = Double.parseDouble(m.group(7));
        assertTrue(passes < 0.030, "D 档 passes=" + passes + "ms 显著低于 A 的 0.164ms");
    }

    // --- 10. 子项默认关（配置桩存在、本轮未实现） ----------------------------

    @Test
    void subOptionsAreDefaultOffConfigStubsOnly() throws Exception {
        String cfg = src(CONFIG);
        assertTrue(cfg.contains("public boolean modeDBlurRadiusSlots = false;"));
        assertTrue(cfg.contains("public boolean modeDStaleGenerationBorrow = false;"));
        assertTrue(cfg.contains("public boolean modeDOpaqueDuringAnimation = false;"));
        String probe = src(PROBE);
        // 三个子项本轮不接线（只留配置桩），D 档主策略一次一个因素
        assertFalse(probe.contains("modeDBlurRadiusSlots"), "本轮未实现半径槽，探针不读该桩");
        assertFalse(probe.contains("modeDStaleGenerationBorrow"), "本轮未实现旧代借用");
        assertFalse(probe.contains("modeDOpaqueDuringAnimation"), "本轮未实现动画期实色");
    }

    // --- 11. 探针纯 Java（与渲染路径解耦，C/D 档仍出数） ---------------------

    @Test
    void probesArePureJavaAndDecoupledFromRenderPath() throws Exception {
        for (String rel : List.of(PROBE, PREPASS)) {
            String text = src(rel);
            for (String line : text.split("\\R")) {
                if (line.trim().startsWith("import ")) {
                    assertTrue(line.trim().startsWith("import java."), rel + " 不得依赖 MC/Skija/GL: " + line);
                }
            }
            assertFalse(text.contains("FrameSegments."), rel);
            assertFalse(text.contains("FrameProfile."), rel);
            assertFalse(text.contains("GlGpuTimer."), rel);
            assertFalse(text.contains("GpuTimerQuery."), rel);
            assertFalse(text.contains("System.currentTimeMillis"), rel);
            assertFalse(text.contains("glFinish"), rel);
        }
    }
}
