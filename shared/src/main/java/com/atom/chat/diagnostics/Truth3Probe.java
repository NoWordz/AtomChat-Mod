package com.atom.chat.diagnostics;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * 「四档归零对照」诊断实验：档位状态机 + 与面板渲染路径完全解耦的帧间隔采样器
 * + D 档（Muonium 时序策略）30Hz 背景模糊限频状态机。
 *
 * <p>本类是纯 Java：不引用 Minecraft / Skija / GL / SLF4J，也不引用
 * {@code FrameProfile} / {@code FrameSegments} / {@code GlGpuTimer}。因此 C 档
 * （整帧不调用 {@code SkiaGraphics.drawPanel}）时它仍然出数，档位切换也不会改动
 * 原有 profile 的任何桶语义。日志行由本类生成完整字符串，宿主只负责
 * {@code LOGGER.info(line)}，三端因此共用同一份格式。
 *
 * <p>采样对象是【屏幕 render 入口相邻两次调用的 {@code System.nanoTime()} 差值】，
 * 即游戏帧间隔。它<strong>不是</strong> GPU 完成时间、<strong>不是</strong> CPU 渲染耗时、
 * <strong>也不是</strong> F3 的 FPS 时钟；三者口径不同，不可混用、不可相减。
 * nanoTime 由宿主在 render 入口取得后传入，本类不额外取时钟。
 *
 * <p>四档（A/B/C 语义逐字节不变，D 为本轮新增）：
 * <ul>
 *   <li><b>A</b>：完整 = 当前产品行为，每帧新鲜毛玻璃，视觉逐字节不变；</li>
 *   <li><b>B</b>：只跳过背景模糊 pre-pass（{@link #blurPrepass()} 对 B 为 false）；</li>
 *   <li><b>C</b>：整个 {@code drawPanel} 不调用（{@link #drawPanel()} 对 C 为 false）；</li>
 *   <li><b>D</b>：画面板 + 画玻璃，但背景采集/模糊按 <b>30Hz 单调时间</b>限频；
 *       非刷新帧复用上一代模糊结果纹理（{@link #blurRefreshDue(long)} 返回 false）。
 *       产品默认恒 A，D 只在 debug=on 且切到 D 时存在。</li>
 * </ul>
 *
 * <p>debug 门控：切档（热键 / 配置字段）与日志输出<strong>只在 debug=on 时生效</strong>；
 * debug=off 时档位恒 A、采样器静默（仍记录、不打印任何实验日志），
 * 因此全新安装 / debug=off / 未按键的产品渲染路径与日志行为逐字节不变。
 *
 * <p>D 档限频是<strong>按单调时间</strong>（{@code System.nanoTime()}，
 * 阈值 {@link #BLUR_CADENCE_NS}）而非按帧数——帧率在 300–400 之间变化，按帧数会漂移。
 * 首次 / 切档进入 D 时 {@code blurReusable=false}，{@link #blurRefreshDue(long)} 强制
 * 返回 true（首帧刷新产生第一代结果，绝不复用空档）；切回 A 时 reset 全部 D 状态，
 * A 路径每帧完整刷新、无残留。
 *
 * <p>每帧开销上界（静态推理 / 离屏，非真机测量，不代表 FPS 收益）：
 * <ul>
 *   <li>稳态每帧 {@link #onRenderFrame(long)}：1 次 long 减法、2 次比较、1 次 long[] 写、
 *       1 次取模（或条件自增）、4 个计数器自增 —— 约十余条指令，<strong>0 字节分配</strong>；</li>
 *   <li>D 档每帧 {@link #blurRefreshDue(long)}：2 次比较、1 次差值、几次自增 —— 约十余条指令，
 *       0 字节分配；{@link #cadenceLine()} 每帧仅 3 次比较（不足 600 帧直接返回）；</li>
 *   <li>debug=on 时每 {@value #REPORT_EVERY} 帧额外：一次至多 600 个 long 的拷贝 +
 *       {@code Arrays.sort}（上界 600·⌈log2 600⌉ ≈ 6·10³ 次 long 比较/交换）、
 *       6 次 {@code String.format}（每次 1 个 Formatter + 1 个 StringBuilder + 若干 String，
 *       全部短命）、1 行日志，D 档再多 1 行 blur_cadence + 1 次 String.format；
 *       摊还每帧 ≤ 10 次 long 比较、≤ 0.02 个短命对象量级、1/{@value #REPORT_EVERY} 次日志 I/O；</li>
 *   <li>debug=off 时：无日志 I/O、无格式化、无排序，只有稳态每帧那十余条指令与 0 分配；</li>
 *   <li>内存：环形窗口 {@value #WINDOW}×8 B + 预分配排序缓冲 {@value #WINDOW}×8 B =
 *       固定 16 KiB，D 档额外 5 个 long/int 标量（< 64 B），与运行时长/帧数无关，无界增长为 0。</li>
 * </ul>
 */
public final class Truth3Probe {

    public enum Mode { A, B, C, D }

    public static final String PREFIX = "[AtomChat][probe] ";
    /** 有界环形窗口：只保留最近这么多帧间隔，绝不随运行时长增长。 */
    public static final int WINDOW = 1024;
    /** 每这么多帧输出一行稳定可解析日志。 */
    public static final int REPORT_EVERY = 600;
    /**
     * 帧间隔上界（1 s）：超过它的相邻 render 间隔视为非连续帧
     * （屏幕关闭/重开、会话切换、系统暂停），不计入样本，避免污染 p50/p95/max。
     */
    public static final long MAX_FRAME_INTERVAL_NS = 1_000_000_000L;

    /** D 档：背景采集/模糊 30Hz 限频阈值（单调时间；33_333_333ns ≈ 30.0000003Hz ≤ 30Hz 上限）。 */
    public static final long BLUR_CADENCE_NS = 33_333_333L;
    /** blur_cadence 行里的目标频率标签（30.0Hz）。 */
    public static final double BLUR_CADENCE_HZ = 30.0;

    /** frame_probe 行契约（解析脚本按同一形状匹配；四档同一采样器）。 */
    public static final String FRAME_LINE_RE =
            "^\\[AtomChat\\]\\[probe\\] frame_probe mode=([ABCD]) n=([0-9]+) "
            + "p50=([0-9]+\\.[0-9]+) p95=([0-9]+\\.[0-9]+) "
            + "fps_p50=([0-9]+\\.[0-9]+) max=([0-9]+\\.[0-9]+)$";
    /** mode_switch 行契约（四档循环 A→B→C→D→A）。 */
    public static final String SWITCH_LINE_RE =
            "^\\[AtomChat\\]\\[probe\\] mode_switch from=([ABCD]) to=([ABCD]) t=([0-9]+)$";
    /**
     * blur_cadence 行契约（D 档每 {@value #REPORT_EVERY} 帧一行，稳定可解析）。
     * 字段：hz=目标频率标签；n=窗口帧数(=refresh+reuse)；refresh=本窗口刷新帧数；
     * reuse=本窗口复用旧档帧数；period_ns=限频阈值；eff_hz=本窗口实测有效刷新频率。
     */
    public static final String CADENCE_LINE_RE =
            "^\\[AtomChat\\]\\[probe\\] blur_cadence mode=D hz=([0-9]+\\.[0-9]+) "
            + "n=([0-9]+) refresh=([0-9]+) reuse=([0-9]+) "
            + "period_ns=([0-9]+) eff_hz=([0-9]+\\.[0-9]+)$";

    private static final Truth3Probe INSTANCE = new Truth3Probe();

    public static Truth3Probe get() { return INSTANCE; }

    private final long[] samples = new long[WINDOW];
    private final long[] sortBuf = new long[WINDOW];
    private int size;
    private int head;
    private long lastNanos = -1L;
    private long sinceReport;
    private Mode mode = Mode.A;
    private boolean debug;
    private boolean configured;
    private boolean lastDebug;
    private String lastConfigMode;

    // ---- D 档 30Hz 时序状态（纯 Java，可单测；切档即 resetBlurCadence） ----
    private long lastBlurRefreshNanos = -1L;   // 上次刷新时刻（单调时钟）
    private boolean blurReusable = false;      // 是否已有可复用的上一代结果纹理
    private long cadenceWindowStartNanos = -1L;
    private long cadenceWindowEndNanos = -1L;
    private int cadenceFrames;                 // 本窗口帧数
    private int cadenceRefresh;                // 本窗口刷新帧数
    private int cadenceReuse;                  // 本窗口复用帧数

    /** 生产用单例；测试可 new 独立实例，互不干扰。 */
    public Truth3Probe() { }

    /** 非法 / 缺失 / 大小写出入的档位值一律回退 A（产品默认逐字节不变）。 */
    public static Mode parseMode(String raw) {
        if (raw == null) return Mode.A;
        String s = raw.trim();
        if ("A".equals(s)) return Mode.A;
        if ("B".equals(s)) return Mode.B;
        if ("C".equals(s)) return Mode.C;
        if ("D".equals(s)) return Mode.D;
        return Mode.A;
    }

    /**
     * 每帧由屏幕 render 入口灌入 config。debug=false 时恒为 A 且完全静默；
     * 仅当 debug 状态或配置字段<strong>发生变化</strong>时才把档位设为配置值并重置窗口，
     * 否则保留热键切出来的档（热键结果不会被每帧覆盖）。返回 mode_switch 行或 null。
     */
    public String configure(boolean debug, String configMode) {
        boolean changed = !configured || debug != lastDebug
                || !Objects.equals(configMode, lastConfigMode);
        configured = true;
        lastDebug = debug;
        lastConfigMode = configMode;
        this.debug = debug;
        if (!changed) return null;
        Mode from = mode;
        mode = debug ? parseMode(configMode) : Mode.A;
        resetWindow();
        if (!debug) return null;  // debug=off：产品默认静默，不打实验日志
        return switchLine(from, mode);
    }

    /** 热键循环 A→B→C→D→A；仅 debug=on 生效。返回 mode_switch 行或 null。 */
    public String cycle() {
        if (!debug) return null;
        Mode from = mode;
        mode = next(mode);
        resetWindow();
        return switchLine(from, mode);
    }

    private static Mode next(Mode m) {
        if (m == Mode.A) return Mode.B;
        if (m == Mode.B) return Mode.C;
        if (m == Mode.C) return Mode.D;
        return Mode.A;
    }

    /** A: 全画；B: 只跳背景模糊 pre-pass；C: 整个 drawPanel 不调用；D: 画面板 + 玻璃但背景模糊 30Hz 限频。 */
    public boolean drawPanel() { return mode != Mode.C; }
    /** 只有 A 执行背景模糊 pre-pass（B / C 均跳过）。语义逐字节不变（D 不走本判定）。 */
    public boolean blurPrepass() { return mode == Mode.A; }
    /** D 档也构造 preUi 画玻璃（刷新帧走完整 render，非刷新帧复用上一代结果纹理）。 */
    public boolean blurComposite() { return mode == Mode.D; }

    public Mode mode() { return mode; }
    public boolean isDebug() { return debug; }

    /**
     * D 档每帧调用（其它档不调用；宿主在构造 preUi 之前按 {@link #blurComposite()} 门控）：
     * 按【单调时间】而非帧数决定是否刷新背景模糊。帧率在 300–400 之间变化，按帧数会漂移。
     * <ul>
     *   <li>首次 / 切档后 {@code blurReusable=false} → 强制刷新一次（降级路径：产生第一代
     *       结果纹理，绝不复用空档 / 未初始化纹理）；</li>
     *   <li>距上次刷新 ≥ {@link #BLUR_CADENCE_NS} → 刷新；</li>
     *   <li>否则 → 返回 false，宿主走 {@code PanelBlurRenderer.renderReuse} 复用上一代结果纹理。</li>
     * </ul>
     * 计数供 {@link #cadenceLine()} 核对限频是否真的生效、复用次数是否符合预期。
     */
    public boolean blurRefreshDue(long nowNanos) {
        if (mode != Mode.D || !debug) {
            return true;  // 非 D 档保守返回 true（A 档每帧新鲜毛玻璃），不计数
        }
        if (cadenceWindowStartNanos < 0L) cadenceWindowStartNanos = nowNanos;
        cadenceWindowEndNanos = nowNanos;
        cadenceFrames++;
        boolean refresh;
        if (!blurReusable || lastBlurRefreshNanos < 0L
                || nowNanos - lastBlurRefreshNanos >= BLUR_CADENCE_NS) {
            refresh = true;
            lastBlurRefreshNanos = nowNanos;
            blurReusable = true;   // 本帧完整刷新后，后续帧有旧档可复用
        } else {
            refresh = false;
        }
        if (refresh) cadenceRefresh++; else cadenceReuse++;
        return refresh;
    }

    /**
     * D 档每帧由宿主调用；攒满 {@value #REPORT_EVERY} 帧返回一行 blur_cadence，其余 null。
     * 输出后只重置窗口计数（不触碰跨窗口连续的 lastBlurRefreshNanos / blurReusable）。
     */
    public String cadenceLine() {
        if (!debug || mode != Mode.D || cadenceFrames < REPORT_EVERY) return null;
        double windowSec = (cadenceWindowEndNanos - cadenceWindowStartNanos) / 1_000_000_000.0;
        double effHz = windowSec > 0.0 ? cadenceRefresh / windowSec : 0.0;
        String line = PREFIX + "blur_cadence mode=D"
                + " hz=" + String.format(Locale.ROOT, "%.1f", BLUR_CADENCE_HZ)
                + " n=" + cadenceFrames
                + " refresh=" + cadenceRefresh
                + " reuse=" + cadenceReuse
                + " period_ns=" + BLUR_CADENCE_NS
                + " eff_hz=" + String.format(Locale.ROOT, "%.1f", effHz);
        cadenceFrames = 0;
        cadenceRefresh = 0;
        cadenceReuse = 0;
        cadenceWindowStartNanos = -1L;
        cadenceWindowEndNanos = -1L;
        return line;
    }

    /**
     * 屏幕 render 入口每帧调用，传入该帧已取的 {@code System.nanoTime()}。
     * debug=on 时每 {@value #REPORT_EVERY} 帧返回一行 frame_probe，否则返回 null。
     */
    public String onRenderFrame(long nowNanos) {
        if (lastNanos >= 0L) {
            long dt = nowNanos - lastNanos;
            if (dt > 0L && dt < MAX_FRAME_INTERVAL_NS) {
                samples[head] = dt;
                head = head + 1 == WINDOW ? 0 : head + 1;
                if (size < WINDOW) size++;
                sinceReport++;
            }
        }
        lastNanos = nowNanos;
        // debug=off 时采样器仍记录（与渲染路径解耦），但不输出任何实验日志。
        if (debug && sinceReport >= REPORT_EVERY && size > 0) {
            sinceReport = 0;
            return frameLine();
        }
        return null;
    }

    /** 切档：清空统计窗口并断开上一帧时间戳，保证每档数据不串；同时重置 D 档时序状态。 */
    private void resetWindow() {
        size = 0;
        head = 0;
        sinceReport = 0;
        lastNanos = -1L;
        resetBlurCadence();
    }

    /** 切档（含 D→A / A→D）重置 D 档时序状态，保证首次进入 D 强制刷新、切回 A 无残留。 */
    private void resetBlurCadence() {
        lastBlurRefreshNanos = -1L;
        blurReusable = false;
        cadenceWindowStartNanos = -1L;
        cadenceWindowEndNanos = -1L;
        cadenceFrames = 0;
        cadenceRefresh = 0;
        cadenceReuse = 0;
    }

    private String frameLine() {
        int n = Math.min(REPORT_EVERY, size);
        for (int i = 0; i < n; i++) {
            int idx = head - 1 - i;
            while (idx < 0) idx += WINDOW;
            sortBuf[i] = samples[idx];
        }
        Arrays.sort(sortBuf, 0, n);
        long p50 = percentile(sortBuf, n, 0.50);
        long p95 = percentile(sortBuf, n, 0.95);
        long max = sortBuf[n - 1];
        double p50ms = p50 / 1_000_000.0;
        double p95ms = p95 / 1_000_000.0;
        double maxMs = max / 1_000_000.0;
        double fps = p50ms > 0.0 ? 1000.0 / p50ms : 0.0;
        return PREFIX + "frame_probe mode=" + mode.name()
                + " n=" + n
                + " p50=" + String.format(Locale.ROOT, "%.3f", p50ms)
                + " p95=" + String.format(Locale.ROOT, "%.3f", p95ms)
                + " fps_p50=" + String.format(Locale.ROOT, "%.1f", fps)
                + " max=" + String.format(Locale.ROOT, "%.2f", maxMs);
    }

    private static long percentile(long[] sorted, int n, double p) {
        int idx = (int) Math.ceil(p * n) - 1;
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return sorted[idx];
    }

    private static String switchLine(Mode from, Mode to) {
        // 时间标记用 nanoTime 单调时钟（非墙钟），仅供排序/去重；解析按 from/to 切段，不依赖其语义。
        return PREFIX + "mode_switch from=" + from.name()
                + " to=" + to.name() + " t=" + System.nanoTime();
    }
}
