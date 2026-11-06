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
 * <p>第二采样入口 {@link #onPumpFrame(long, boolean, String)}（E1 外部尺子）：
 * 挂在<strong>与当前屏幕无关</strong>的每帧 HUD/界面事件上（三端各注册 1 处），
 * 供 G0 无屏幕 / G1 原版 ChatScreen / G2 原版 InventoryScreen / G3 AC 面板
 * <strong>共用同一个采样点</strong>。它拥有自己的环形窗口与计数器，输出独立的
 * {@code frame_pump} 行，<strong>不读写</strong> {@link #onRenderFrame(long)} 的
 * 任何状态，因此历史 {@code frame_probe} 数据继续可比。AC 面板打开时两者同时存在，
 * 可用于交叉校准：HUD 阶段先于 Screen.render，两者的相邻帧间隔分布应几乎相同
 * （同一帧边界、只差一个固定相位），p50 若明显不同即说明采样点本身有问题。
 *
 * <p>E0 仪表标定：{@link #instrumentEnabled(boolean, boolean)} 是
 * {@code debug && !measureOnly} 的唯一实现，宿主的 FrameProfile / PrepassProbe
 * 门控都用它；帧探针本身不受 measureOnly 影响。measureOnly 是
 * <strong>双层语义</strong>：配置字段 {@code diagnosticMeasureOnly} 只提供启动初始值
 * （{@link #seedMeasureOnly(boolean)} 一次性灌入），之后由 Shift+F6
 * （{@link #toggleMeasureOnly()}，仅 debug=on）在运行时翻转，不写回配置文件。
 * 翻转会同时重置 frame_probe 与 frame_pump 两个窗口，保证数据不跨配置混合。
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
 *   <li>内存：每个采样器（render / pump）各 {@value #WINDOW}×8 B 环形窗口 +
 *       {@value #WINDOW}×8 B 预分配排序缓冲；两者合计固定 32 KiB，D 档额外 5 个
 *       long/int 标量（< 64 B），与运行时长/帧数无关，无界增长为 0。</li>
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
    /**
     * frame_pump 行契约（E1 屏幕无关采样点）。字段形状与 frame_probe 完全一致，
     * 只换行名，方便两个采样点逐字段交叉校准；mode 取自配置字段（与 frame_probe 同源）。
     */
    public static final String PUMP_LINE_RE =
            "^\\[AtomChat\\]\\[probe\\] frame_pump mode=([ABCD]) n=([0-9]+) "
            + "p50=([0-9]+\\.[0-9]+) p95=([0-9]+\\.[0-9]+) "
            + "fps_p50=([0-9]+\\.[0-9]+) max=([0-9]+\\.[0-9]+)$";
    /** mode_switch 行契约（四档循环 A→B→C→D→A）。 */
    public static final String SWITCH_LINE_RE =
            "^\\[AtomChat\\]\\[probe\\] mode_switch from=([ABCD]) to=([ABCD]) t=([0-9]+)$";
    /**
     * measure_switch 行契约（Shift+F6 翻转运行时 measureOnly；仅 debug=on）。
     * 字段：measure_only=翻转后的状态；t=nanoTime 单调时钟（仅供排序/去重）。
     */
    public static final String MEASURE_LINE_RE =
            "^\\[AtomChat\\]\\[probe\\] measure_switch measure_only=(true|false) t=([0-9]+)$";
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

    // ---- E0 运行时仪表开关（Shift+F6）：配置字段只做首帧初始值，之后由热键翻转 ----
    private boolean measureOnly;
    private boolean measureSeeded;

    // ---- 第二采样入口（frame_pump）自有窗口：与上面的 render 窗口零共享 ----
    private final long[] pumpSamples = new long[WINDOW];
    private final long[] pumpSortBuf = new long[WINDOW];
    private int pumpSize;
    private int pumpHead;
    private long pumpLastNanos = -1L;
    private long pumpSinceReport;
    private boolean pumpArmed;
    private Mode pumpMode = Mode.A;

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
     * E0 仪表标定的唯一门控式：{@code debug && !measureOnly}。
     *
     * <p>宿主用它同时关闭 FrameProfile（面板/解码/塑形分桶 + JFR）、FrameSegments
     * （由 {@code FrameProfile} 的 {@code start()} 返回 0 天然旁路）与 PrepassProbe；
     * {@code Truth3Probe} 自己的 {@code frame_probe} / {@code frame_pump} 不经过这里，
     * 因此 measureOnly 下仍然照常出数。纯 Java，无副作用，可单测。
     *
     * <p>{@code measureOnly} 参数应当传 {@link #measureOnly()}（运行时状态），
     * 而不是配置字段 —— 配置字段只是启动时的初始值。
     */
    public static boolean instrumentEnabled(boolean debug, boolean measureOnly) {
        return debug && !measureOnly;
    }

    /** 运行时 measureOnly 状态（配置字段的首值 + 热键翻转的结果）。 */
    public boolean measureOnly() { return measureOnly; }

    /**
     * 把配置字段 {@code diagnosticMeasureOnly} 作为<strong>启动初始值</strong>灌一次；
     * 之后再调用无效（幂等），因此热键翻转结果不会被每帧/每 tick 的配置读取覆盖。
     * 本方法不写文件、不改配置对象 —— 双层语义里「配置=初始值，热键=运行时」的接缝。
     */
    public void seedMeasureOnly(boolean initial) {
        if (measureSeeded) return;
        measureSeeded = true;
        measureOnly = initial;
    }

    /**
     * Shift+F6：仅 debug=on 时翻转运行时 measureOnly，并<strong>同时重置</strong>
     * frame_probe 与 frame_pump 两个采样窗口（否则窗口跨两种仪表配置混合，
     * 与切档污染同一个毛病）。返回一行 measure_switch 或 null。
     *
     * <p>不写配置文件：这是实验开关，只影响本会话的运行时状态。
     */
    public String toggleMeasureOnly() {
        if (!debug) return null;
        measureOnly = !measureOnly;
        resetWindow();      // frame_probe 窗口（含 D 档 cadence 计数）
        resetPumpWindow();  // frame_pump 窗口
        return PREFIX + "measure_switch measure_only=" + measureOnly
                + " t=" + System.nanoTime();
    }

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

    /**
     * 第二采样入口（E1 外部尺子）：由宿主挂在<strong>与当前屏幕无关</strong>的
     * 每帧 HUD/界面事件上，传入该帧的 {@code System.nanoTime()}、本帧 debug 与配置档位。
     *
     * <p>与 {@link #onRenderFrame(long)} <strong>零共享状态</strong>：自己的环形窗口、
     * 排序缓冲与计数器，切档（debug 开关或档位变化）只重置 pump 窗口，绝不触碰
     * frame_probe 的窗口，因此 AC 面板打开时两条线可同时使用并逐字段交叉校准。
     *
     * <p>debug=off 直接返回 null：产品默认下每帧只有一次字段读取与返回，
     * 不取时钟、不采样、不格式化、不打日志。debug=on 时每 {@value #REPORT_EVERY}
     * 帧返回一行 {@code frame_pump}。
     *
     * @param debug      本帧 {@code config.debug}
     * @param configMode 本帧 {@code config.diagnosticRenderMode}（与 frame_probe 同源，
     *                   非法值回退 A；G0/G1/G2 没有 AC 屏幕时也按配置字段报档）
     */
    public String onPumpFrame(long nowNanos, boolean debug, String configMode) {
        if (!debug) {
            if (pumpArmed) {
                resetPumpWindow();
                pumpArmed = false;
            }
            return null;
        }
        Mode want = parseMode(configMode);
        if (!pumpArmed || want != pumpMode) {
            resetPumpWindow();
            pumpMode = want;
            pumpArmed = true;
        }
        if (pumpLastNanos >= 0L) {
            long dt = nowNanos - pumpLastNanos;
            if (dt > 0L && dt < MAX_FRAME_INTERVAL_NS) {
                pumpSamples[pumpHead] = dt;
                pumpHead = pumpHead + 1 == WINDOW ? 0 : pumpHead + 1;
                if (pumpSize < WINDOW) pumpSize++;
                pumpSinceReport++;
            }
        }
        pumpLastNanos = nowNanos;
        if (pumpSinceReport >= REPORT_EVERY && pumpSize > 0) {
            pumpSinceReport = 0;
            return pumpLine();
        }
        return null;
    }

    /** 只清 pump 窗口；不影响 frame_probe / blur_cadence 的任何状态。 */
    private void resetPumpWindow() {
        pumpSize = 0;
        pumpHead = 0;
        pumpSinceReport = 0;
        pumpLastNanos = -1L;
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

    /**
     * frame_pump 行：与 {@link #frameLine()} 同一算法与字段格式（n 取窗口内最近
     * {@value #REPORT_EVERY} 个样本），只读 pump 自己的窗口，避免两条线互相污染。
     */
    private String pumpLine() {
        int n = Math.min(REPORT_EVERY, pumpSize);
        for (int i = 0; i < n; i++) {
            int idx = pumpHead - 1 - i;
            while (idx < 0) idx += WINDOW;
            pumpSortBuf[i] = pumpSamples[idx];
        }
        Arrays.sort(pumpSortBuf, 0, n);
        long p50 = percentile(pumpSortBuf, n, 0.50);
        long p95 = percentile(pumpSortBuf, n, 0.95);
        long max = pumpSortBuf[n - 1];
        double p50ms = p50 / 1_000_000.0;
        double p95ms = p95 / 1_000_000.0;
        double maxMs = max / 1_000_000.0;
        double fps = p50ms > 0.0 ? 1000.0 / p50ms : 0.0;
        return PREFIX + "frame_pump mode=" + pumpMode.name()
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
