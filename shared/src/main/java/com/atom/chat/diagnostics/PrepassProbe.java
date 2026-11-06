package com.atom.chat.diagnostics;

import java.util.Locale;

/**
 * 背景模糊 pre-pass 真机分段计时探针（诊断专用，只加诊断、不改任何性能逻辑）。
 *
 * <p>纯 Java：只 import {@code java.util.Locale}，不引用 Minecraft / GL / Skija /
 * SLF4J，也不引用 {@code FrameProfile} / {@code FrameSegments} / {@code GlGpuTimer} /
 * {@code GpuTimerQuery}（P1 的 {@code gpuSummary} 本轮一次一个因素，不碰）。
 * 宿主（三端 {@code PanelBlurRenderer}）负责灌入档位、分段耗时与 {@code LOGGER.info}。
 *
 * <p>统计口径：对有界环形窗口 {@value #WINDOW} 内最近 {@value #REPORT_EVERY}
 * 个样本求【算术均值】（不是 p50/p95 —— 分位数不可线性相加）。每
 * {@value #REPORT_EVERY} 帧（由 {@link #onFrame(int, boolean)} 的帧计数驱动，
 * 与 {@code Truth3Probe} 的 {@code frame_probe} 同节奏）输出一行稳定可解析日志，
 * A/B/C/D 四档同格式。
 *
 * <p>D 档（Muonium 时序策略）：每游戏帧恰好灌一个样本。刷新帧走完整
 * {@code render()}（entry/capture/passes/state/composite 全有值）；非刷新帧走
 * {@code renderReuse()}（只做圆角合成，passes=0，仅 composite 有值）。因此 D 档窗口
 * {@code n=600 skipped=0}，且 {@code passes} 均值 = A 档 passes × 刷新帧占比，
 * <strong>显著低于 A</strong>；B/C 档不构造 preUi、窗口无样本 → {@code n=0 skipped=1}。
 *
 * <p>关键设计：{@code unaccounted} = {@code total − (entry+capture+passes+
 * state+composite)}，<strong>逐个样本</strong>计算后再取均值，因此
 * {@code mean(unaccounted) = mean(total) − Σ mean(parts)} 严格成立。
 *
 * <p>debug 门控：{@code onFrame(mode, false)} 直接返回 null 并清空窗口，
 * 不记录、不输出、不格式化；{@code PanelBlurRenderer} 只在 {@code debug=on}
 * 时取 {@code System.nanoTime()} 与调用 {@link #record}。产品默认
 * （debug=off）渲染输出与日志逐字节不变。
 *
 * <p>每帧新增开销上界（静态推理 / 离屏，非真机测量）：debug=on 每帧 10 次
 * {@code System.nanoTime()}（A/D 刷新帧）、7 次 {@code long[]} 写、若干比较/自增，
 * <strong>0 字节分配</strong>；D 复用帧 2 次 nanoTime + 1 次 record。每
 * {@value #REPORT_EVERY} 帧额外 1 次 {@code String.format} + 1 行日志（短命对象）；
 * 内存固定 7×{@value #WINDOW}×8 B = 56 KiB，与运行时长/帧数无关。
 */
public final class PrepassProbe {

    public static final String PREFIX = "[AtomChat][probe] ";
    /** 有界环形窗口：只保留最近这么多帧样本，绝不随运行时长增长。 */
    public static final int WINDOW = 1024;
    /** 每这么多帧输出一行，与 Truth3Probe.frame_probe 同节奏。 */
    public static final int REPORT_EVERY = 600;

    /** prepass_probe 行契约（解析脚本按同一形状匹配）。四档同格式，含 skipped。 */
    public static final String PREPASS_LINE_RE =
            "^\\[AtomChat\\]\\[probe\\] prepass_probe mode=([ABCD]) n=([0-9]+) skipped=([01]) "
            + "total=([0-9]+\\.[0-9]+) entry=([0-9]+\\.[0-9]+) "
            + "capture=([0-9]+\\.[0-9]+) passes=([0-9]+\\.[0-9]+) "
            + "state=([0-9]+\\.[0-9]+) composite=([0-9]+\\.[0-9]+) "
            + "unaccounted=([0-9]+\\.[0-9]+)$";

    /** 环形窗口字段数：total, entry, capture, passes, state, composite, unaccounted */
    private static final int FIELDS = 7;

    private static final PrepassProbe INSTANCE = new PrepassProbe();

    public static PrepassProbe get() { return INSTANCE; }

    private final long[][] ring = new long[FIELDS][WINDOW];
    private int size;
    private int head;
    private int frameCount;
    private int windowMode = -1;   // -1 未初始化；0=A, 1=B, 2=C, 3=D
    private boolean lastDebug;

    /** 生产用单例；测试可 new 独立实例，互不干扰。 */
    public PrepassProbe() { }

    /**
     * 每个游戏帧由 {@code PanelBlurRenderer.isAvailable()} 调用一次（三端
     * {@code AtomChatScreen.render()} 中该调用位于 {@code drawPanel()} 门控之前，
     * A/B/C/D 四档每帧都执行）。推进帧计数、检测切档并重置窗口、每
     * {@value #REPORT_EVERY} 帧返回一行 {@code prepass_probe}；其余帧返回 null。
     *
     * @param mode  Truth3 当前档位序号：0=A, 1=B, 2=C, 3=D（{@code Mode.ordinal()}）
     * @param debug 本帧 {@code config.debug}
     */
    public String onFrame(int mode, boolean debug) {
        boolean debugChanged = debug != lastDebug;
        lastDebug = debug;
        if (!debug) {
            if (debugChanged) resetWindow();  // 关闭 debug 时清空，恢复后从干净窗口开始
            return null;                      // 完全静默
        }
        if (debugChanged || mode != windowMode) {
            resetWindow();                    // 切档即重置，保证不串档
            windowMode = mode;
        }
        frameCount++;
        if (frameCount < REPORT_EVERY) return null;
        frameCount = 0;
        String line = report();
        resetWindow();
        return line;
    }

    /**
     * 每次 {@code PanelBlurRenderer.render()}（A/D 刷新帧）或
     * {@code renderReuse()}（D 复用帧）执行完灌入一个样本。
     * 调用方只在 debug=on 时调用。段值单位 ns，允许为 0（早退路径 / 复用帧未覆盖的段）。
     * 顺序：total, entry, capture, passes, state, composite。
     */
    public void record(int mode, long totalNs, long entryNs, long captureNs,
                       long passesNs, long stateNs, long compositeNs) {
        if (mode != windowMode) {
            resetWindow();
            windowMode = mode;
        }
        long entry = Math.max(0L, entryNs);
        long capture = Math.max(0L, captureNs);
        long passes = Math.max(0L, passesNs);
        long state = Math.max(0L, stateNs);
        long composite = Math.max(0L, compositeNs);
        long total = Math.max(0L, totalNs);
        long parts = entry + capture + passes + state + composite;
        // 逐样本钳制 ≥0：nanoTime 抖动或早退路径留下的负差值不产生伪造的负耗时。
        long unaccounted = Math.max(0L, total - parts);

        ring[0][head] = total;
        ring[1][head] = entry;
        ring[2][head] = capture;
        ring[3][head] = passes;
        ring[4][head] = state;
        ring[5][head] = composite;
        ring[6][head] = unaccounted;
        head = head + 1 == WINDOW ? 0 : head + 1;
        if (size < WINDOW) size++;
    }

    /** 切档 / 输出后重置：与 {@code Truth3Probe.resetWindow()} 等价，保证不串档。 */
    private void resetWindow() {
        size = 0;
        head = 0;
        frameCount = 0;
        // ring 内容惰性覆盖：不逐元素清零，避免每窗口 7*1024 次写。
    }

    /** 窗口内最近 n 个样本的算术均值（ns → ms，3 位小数）。n=0 输出 skipped 行。 */
    private String report() {
        int n = Math.min(REPORT_EVERY, size);
        String mode = modeName(windowMode);
        if (n == 0) {
            // B/C 档：pre-pass 被旁路，窗口内无样本，绝不伪造数据。
            return PREFIX + "prepass_probe mode=" + mode
                    + " n=0 skipped=1"
                    + " total=0.000 entry=0.000 capture=0.000 passes=0.000"
                    + " state=0.000 composite=0.000 unaccounted=0.000";
        }
        long s0 = 0, s1 = 0, s2 = 0, s3 = 0, s4 = 0, s5 = 0, s6 = 0;
        for (int i = 0; i < n; i++) {
            int idx = head - 1 - i;
            while (idx < 0) idx += WINDOW;
            s0 += ring[0][idx];
            s1 += ring[1][idx];
            s2 += ring[2][idx];
            s3 += ring[3][idx];
            s4 += ring[4][idx];
            s5 += ring[5][idx];
            s6 += ring[6][idx];
        }
        double d = n * 1_000_000.0;
        return PREFIX + "prepass_probe mode=" + mode
                + " n=" + n + " skipped=0"
                + String.format(Locale.ROOT,
                        " total=%.3f entry=%.3f capture=%.3f passes=%.3f"
                        + " state=%.3f composite=%.3f unaccounted=%.3f",
                        s0 / d, s1 / d, s2 / d, s3 / d, s4 / d, s5 / d, s6 / d);
    }

    /** 0→A, 1→B, 2→C, 3→D；非法回退 A（产品默认）。 */
    private static String modeName(int mode) {
        if (mode < 0 || mode > 3) return "A";
        return String.valueOf((char) ('A' + mode));
    }
}
