package com.atom.chat.diagnostics;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 首开预算探针：**只**回答一个问题 —— 从第一次构造面板到第一帧画完，
 * 渲染线程上到底把时间花在哪一段，以及这些段里有多少本来能提前做掉。
 *
 * <p>纯 Java（只 import {@code java.*}），不引用 Minecraft / Skija / GL / SLF4J；
 * 宿主负责在首开路径上打点、在首帧末尾把 {@link #endFrame()} 的行写进日志。
 *
 * <p><b>口径。</b>{@code total} 从 {@code AtomChatScreen} 构造入口（{@code super()} 之后、
 * {@code AtomChatConfig.reload()} 之前）到首帧 {@code render()} 末尾。它不是单帧耗时，
 * 而是「按下键 → 第一帧画完」的整段首开关键路径；构造、{@code init()}、首帧都算在内，
 * 所以它比 {@code frame_probe} 的相邻间隔大，二者不可相减。
 *
 * <p><b>六个必需字段</b>（与任务契约一致）：
 * <ul>
 *   <li>{@code font} —— 首开帧内同步加载字体 Typeface 的墙钟（预热命中时应为 0）；</li>
 *   <li>{@code emoji} —— 首开帧内解析 emoji 字体 / 栅格化 emoji 的墙钟（预热命中时应为 0）；</li>
 *   <li>{@code blur_init} —— 首开帧内创建模糊 shader / FBO / 纹理的墙钟（预热命中时应为 0）；</li>
 *   <li>{@code icons} —— 首开帧内 {@code ItemIconRenderer.prepare} 的墙钟；</li>
 *   <li>{@code other} —— {@code total - (font+emoji+blur_init+icons)}，钳制 ≥0；</li>
 *   <li>{@code total} —— 见上。</li>
 * </ul>
 *
 * <p><b>三个预热字段</b>（用于判断「挪走」是否真的发生在首开之前）：
 * {@code prewarm_font / prewarm_emoji / prewarm_gl}，单位 ms；{@code -1.000}
 * 表示首开结束时该预热尚未完成（下次真机据此判断是否开得太早）。
 *
 * <p><b>门控。</b>只有第一次构造、且构造时 {@code debug=on} 才激活。debug=off 时
 * 调用点只做一次 {@link #active()} 的 volatile 读与 {@code 0} 分支，不取
 * {@code nanoTime}、不分配、不输出。首开一旦结束（{@link #endFrame()}），
 * 本进程内不再激活；测试用 {@link #resetForTest()} 隔离。
 */
public final class FirstOpenBudget {

    public static final String PREFIX = "[AtomChat][probe] ";

    /**
     * {@code first_open_budget} 行契约。末三个预热字段允许 {@code -1.000}（未完成）。
     * 解析脚本按此正则匹配；字段顺序固定。
     */
    public static final String LINE_RE =
            "^\\[AtomChat\\]\\[probe\\] first_open_budget "
                    + "total=([0-9]+\\.[0-9]{3}) font=([0-9]+\\.[0-9]{3}) "
                    + "emoji=([0-9]+\\.[0-9]{3}) blur_init=([0-9]+\\.[0-9]{3}) "
                    + "icons=([0-9]+\\.[0-9]{3}) other=([0-9]+\\.[0-9]{3}) first_draw=([0-9]+\\.[0-9]{3}) "
                    + "prewarm_font=(-?[0-9]+\\.[0-9]{3}) prewarm_emoji=(-?[0-9]+\\.[0-9]{3}) "
                    + "prewarm_gl=(-?[0-9]+\\.[0-9]{3})$";

    private static final AtomicBoolean CLAIMED = new AtomicBoolean();
    private static final AtomicBoolean LIVE = new AtomicBoolean();
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static final AtomicLong START = new AtomicLong(-1L);
    private static final AtomicLong FONT = new AtomicLong();
    private static final AtomicLong EMOJI = new AtomicLong();
    private static final AtomicLong BLUR_INIT = new AtomicLong();
    private static final AtomicLong ICONS = new AtomicLong();
    /** Wall clock of the first render() call; the rest of the path is the rest. */
    private static final AtomicLong FIRST_DRAW = new AtomicLong();
    private static final AtomicLong PREWARM_FONT = new AtomicLong(-1_000_000L);
    private static final AtomicLong PREWARM_EMOJI = new AtomicLong(-1_000_000L);
    private static final AtomicLong PREWARM_GL = new AtomicLong(-1_000_000L);

    private FirstOpenBudget() {
    }

    /**
     * 第一次构造面板时调用。只有第一次构造会被接受；debug=off 时直接不激活。
     *
     * @return true 表示这次构造被计量（debug=on 且是进程内第一次）
     */
    public static boolean beginFirstOpen(boolean debugOn) {
        if (!CLAIMED.compareAndSet(false, true)) {
            return false;
        }
        if (!debugOn) {
            return false;
        }
        START.set(System.nanoTime());
        LIVE.set(true);
        return true;
    }

    /** 首开测量是否仍在进行。debug=off / 已结束时只值一次 volatile 读。 */
    public static boolean active() {
        return LIVE.get();
    }

    public static void addFont(long nanos) {
        if (LIVE.get()) FONT.addAndGet(Math.max(0L, nanos));
    }

    public static void addEmoji(long nanos) {
        if (LIVE.get()) EMOJI.addAndGet(Math.max(0L, nanos));
    }

    public static void addBlurInit(long nanos) {
        if (LIVE.get()) BLUR_INIT.addAndGet(Math.max(0L, nanos));
    }

    /**
     * Times a block and files it under {@code blur_init}.
     *
     * <p>The clock lives here, not at the call site, so that the renderers keep
     * their {@code System.nanoTime()} count at exactly 13 — {@code PrepassProbeTest}
     * pins that number to prove the probe structure was not reshaped. The call
     * site only asks {@link #active()} and, when the measurement is live, hands
     * over a closure.
     */
    public static void timeBlurInit(Runnable action) {
        if (!LIVE.get()) {
            action.run();
            return;
        }
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            BLUR_INIT.addAndGet(Math.max(0L, System.nanoTime() - start));
        }
    }

    public static void addIcons(long nanos) {
        if (LIVE.get()) ICONS.addAndGet(Math.max(0L, nanos));
    }

    /**
     * Marks the start of the first render() call. Returns 0 when no measurement
     * is live, so the caller can skip the clock entirely.
     */
    public static long markDrawStart() {
        return LIVE.get() ? System.nanoTime() : 0L;
    }

    /** Files the first render() call's wall clock under {@code first_draw}. */
    public static void addFirstDraw(long startNanos) {
        if (startNanos == 0L) {
            return;
        }
        FIRST_DRAW.addAndGet(Math.max(0L, System.nanoTime() - startNanos));
    }

    /** 预热线程完成后的总墙钟；可在首开前/后任意时刻写入，-1 表示尚未完成。 */
    public static void setPrewarmFont(long nanos) {
        PREWARM_FONT.set(Math.max(0L, nanos));
    }

    public static void setPrewarmEmoji(long nanos) {
        PREWARM_EMOJI.set(Math.max(0L, nanos));
    }

    public static void setPrewarmGl(long nanos) {
        PREWARM_GL.set(Math.max(0L, nanos));
    }

    /**
     * 首帧末尾调用：结束窗口并返回日志行（仅 debug=on 且尚未报告过）。
     * 返回 null 时宿主不打印任何东西。
     */
    public static String endFrame() {
        if (!LIVE.compareAndSet(true, false)) {
            return null;
        }
        long end = System.nanoTime();
        long start = START.get();
        long total = start <= 0L ? 0L : Math.max(0L, end - start);
        long font = FONT.get();
        long emoji = EMOJI.get();
        long blur = BLUR_INIT.get();
        long icons = ICONS.get();
        long other = Math.max(0L, total - (font + emoji + blur + icons));
        if (!REPORTED.compareAndSet(false, true)) {
            return null;
        }
        return PREFIX + "first_open_budget"
                + String.format(Locale.ROOT,
                " total=%.3f font=%.3f emoji=%.3f blur_init=%.3f icons=%.3f other=%.3f"
                        + " first_draw=%.3f prewarm_font=%.3f prewarm_emoji=%.3f prewarm_gl=%.3f",
                total / 1e6, font / 1e6, emoji / 1e6, blur / 1e6, icons / 1e6, other / 1e6,
                FIRST_DRAW.get() / 1e6,
                PREWARM_FONT.get() / 1e6, PREWARM_EMOJI.get() / 1e6, PREWARM_GL.get() / 1e6);
    }

    /** 首帧没有走到末尾（例如面板被立即关闭）时放弃本次计量，不输出半截数据。 */
    public static void abort() {
        LIVE.set(false);
    }

    /** 测试隔离：把单例状态清回「从未构造过面板」。 */
    public static void resetForTest() {
        CLAIMED.set(false);
        LIVE.set(false);
        REPORTED.set(false);
        START.set(-1L);
        FONT.set(0L);
        EMOJI.set(0L);
        BLUR_INIT.set(0L);
        ICONS.set(0L);
        FIRST_DRAW.set(0L);
        PREWARM_FONT.set(-1_000_000L);
        PREWARM_EMOJI.set(-1_000_000L);
        PREWARM_GL.set(-1_000_000L);
    }
}
