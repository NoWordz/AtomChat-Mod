package com.atom.chat.diagnostics;

import com.atom.chat.AtomChat;

import java.util.Arrays;

/**
 * 渲染耗时尺子：**先有尺，再谈优化**。
 *
 * <p>0.2.11 之前全树唯一的耗时计数是 Skija 加载那一次（{@code EnvironmentSummary}）。帧耗时、
 * 解码耗时、文字塑形耗时、以及渲染路径上的对象分配，一个都没有 —— 于是"这个界面卡不卡"
 * 只能靠感觉，而"要不要优化、优化哪里"就只能靠猜。这个类只解决"量得出来"。
 *
 * <p><strong>它不做优化，也不建议优化。</strong>先拿到基线，再决定有没有东西值得改；
 * 没有基线就动手，那是拿用户的帧率赌自己的直觉。
 *
 * <p>三个桶：
 * <ul>
 *   <li>{@code panel} —— 一帧完整的面板绘制（{@code SkiaGraphics.draw} 的整段），
 *       外加这一帧在渲染线程上新增的**对象分配字节数**（下面解释）；</li>
 *   <li>{@code decode} —— 一次图片解码（静态图与 GIF 首帧）；</li>
 *   <li>{@code layout} —— 一次文字塑形（{@code SkiaFontRenderer} 未命中宽度缓存的那条路）。</li>
 * </ul>
 *
 * <p><strong>分配那一列量的是什么。</strong>是 JVM 侧的对象分配（{@code ThreadMXBean} 的
 * 线程分配计数），也就是 {@code new Paint()} 这类包装对象本身 —— **不是**它们背后的原生内存。
 * 之所以量这个：包里 {@code SkiaDraw} 的每个 helper 都是**每次调用现造原生对象**
 * （{@code drawRoundedRect} / {@code drawEdgeHighlight} / {@code drawRoundedImage} /
 * {@code drawVerticalGradient} 各一个 {@code Paint}，{@code drawRoundedShadow} 还每次
 * {@code MaskFilter.makeBlur} 造一个模糊滤镜），而它们被逐图元、逐帧调用。
 * 这是全树唯一一处**有代码依据**的嫌疑点，所以尺子上要有能看见它的刻度。
 * 那个 JVM 不支持时这一列报 {@code n/a}，不猜。
 *
 * <p><strong>开关不是这里读配置的。</strong>{@code start()} 只读一个 volatile：热路径上多一次
 * 配置读取是没道理的，而客户端每 tick 本来就会读一次 debug 开关（见 {@code EnvironmentSummary}），
 * 让它顺手把这个开关也同步过来。
 *
 * <p>采样是一个**滚动窗口**（最近 {@value #CAPACITY} 次），不是全量：要的是"现在什么样"，
 * 而且不能为了量一个数把内存长起来。
 */
public final class FrameProfile {

    /** 滚动窗口大小。够算出稳定的 p95，又不至于每次报告都排序一大堆。 */
    static final int CAPACITY = 4096;

    /** 每这么多帧报告一次（60 fps 下约十秒一次）。只在 debug 打开时才走到这里。 */
    static final int REPORT_EVERY_FRAMES = 600;

    /** 报告块的抬头。**只用 ASCII**：日志文件按平台默认编码写，而这份会被贴进 issue。 */
    static final String SUMMARY_HEADER =
            "AtomChat render profile (debug) - rolling window, times in ms, alloc in KB per call";

    private static final String PANEL = "panel";
    private static final String DECODE = "decode";
    private static final String LAYOUT = "layout";

    private static volatile boolean enabled;

    private static final Bucket PANEL_BUCKET = new Bucket(true);
    private static final Bucket DECODE_BUCKET = new Bucket(false);
    private static final Bucket LAYOUT_BUCKET = new Bucket(false);

    private static int framesSinceReport;

    private FrameProfile() {
    }

    /** 客户端每 tick 跟着 debug 开关同步一次。 */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    /**
     * 开始记一次。**关掉的时候返回 0**，配对的 {@code panel/decode/layout} 会直接忽略它 ——
     * 于是调用点不必自己写 if，也就不会出现"某一条路忘了判开关"。
     */
    public static long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    /**
     * 记一帧。返回 true 表示这一帧正好到报告点了（调用方负责把 {@link #summary()} 打出去 ——
     * 这个类自己不打日志，于是它的每一行都能在单测里拼出来）。
     *
     * @param startedAt       {@link #start()} 的返回值；0 = 没开，直接忽略
     * @param allocatedBefore {@link #allocatedBytes()} 在帧开始时的值；-1 = 这个 JVM 不支持
     */
    public static boolean frame(long startedAt, long allocatedBefore) {
        if (startedAt == 0L) {
            return false;
        }
        long allocated = allocatedBefore < 0L ? -1L : Math.max(0L, allocatedBytes() - allocatedBefore);
        PANEL_BUCKET.add(System.nanoTime() - startedAt, allocated);
        framesSinceReport++;
        if (framesSinceReport >= REPORT_EVERY_FRAMES) {
            framesSinceReport = 0;
            return true;
        }
        return false;
    }

    /** 记一次图片解码。 */
    public static void decode(long startedAt) {
        if (startedAt != 0L) {
            DECODE_BUCKET.add(System.nanoTime() - startedAt, -1L);
        }
    }

    /** 记一次文字塑形。 */
    public static void layout(long startedAt) {
        if (startedAt != 0L) {
            LAYOUT_BUCKET.add(System.nanoTime() - startedAt, -1L);
        }
    }

    /**
     * 当前线程累计分配字节数；这个 JVM 不支持就给 -1（不假装是 0：0 会读成"没有分配"）。
     */
    public static long allocatedBytes() {
        com.sun.management.ThreadMXBean bean = threads;
        return bean == null ? -1L : bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    /** s/次 → 毫秒，保留两位。 */
    private static String ms(long nanos, int decimals) {
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", nanos / 1_000_000.0);
    }

    /**
     * 这一份分布。抬头 + 一行一个桶，和 S3 的环境摘要同一个读法（整块贴进 issue）。
     */
    public static String summary() {
        StringBuilder sb = new StringBuilder(SUMMARY_HEADER);
        sb.append('\n').append(PANEL_BUCKET.row(PANEL));
        sb.append('\n').append(DECODE_BUCKET.row(DECODE));
        sb.append('\n').append(LAYOUT_BUCKET.row(LAYOUT));
        return sb.toString();
    }

    /** 丢掉窗口里的样本（单测用；客户端不需要 —— 滚动窗口自己会往前走）。 */
    static void reset() {
        PANEL_BUCKET.clear();
        DECODE_BUCKET.clear();
        LAYOUT_BUCKET.clear();
        framesSinceReport = 0;
    }

    /** 一个桶：滚动窗口 + 分配统计。只在渲染线程上写，报告时读。 */
    private static final class Bucket {
        private final long[] nanos = new long[CAPACITY];
        private final boolean trackAllocated;
        private int next;
        private int size;
        private long allocatedTotal;
        private int allocatedSamples;

        private Bucket(boolean trackAllocated) {
            this.trackAllocated = trackAllocated;
        }

        private void add(long value, long allocated) {
            nanos[next] = value;
            next = (next + 1) % CAPACITY;
            if (size < CAPACITY) {
                size++;
            }
            if (trackAllocated && allocated >= 0L) {
                allocatedTotal += allocated;
                allocatedSamples++;
            }
        }

        private void clear() {
            next = 0;
            size = 0;
            allocatedTotal = 0L;
            allocatedSamples = 0;
        }

        private String row(String name) {
            StringBuilder sb = new StringBuilder("  ");
            sb.append(String.format(java.util.Locale.ROOT, "%-7s", name));
            if (size == 0) {
                return sb.append("no samples").toString();
            }
            long[] sorted = Arrays.copyOf(nanos, size);
            Arrays.sort(sorted);
            long total = 0L;
            for (long v : sorted) {
                total += v;
            }
            sb.append("n=").append(size);
            sb.append("  p50=").append(ms(sorted[percentileIndex(0.50, size)], 2));
            sb.append("  p95=").append(ms(sorted[percentileIndex(0.95, size)], 2));
            sb.append("  max=").append(ms(sorted[size - 1], 2));
            sb.append("  mean=").append(ms(total / size, 2));
            if (trackAllocated) {
                sb.append("  alloc=");
                if (allocatedSamples == 0) {
                    sb.append("n/a");
                } else {
                    sb.append(String.format(java.util.Locale.ROOT, "%.1f KB/call",
                            allocatedTotal / (double) allocatedSamples / 1024.0));
                }
            }
            return sb.toString();
        }
    }

    /**
     * 最近秩法：p 分位取排序后第 {@code ceil(p*n)} 个（1-based），再夹进数组范围。
     * 样本少的时候它退化成最大值 —— 这是对的：别让三个样本编出一个漂亮的 p95。
     *
     * <p>抽成纯函数是为了能单独验：**分位数算错是这里最不容易被发现的一种错**，
     * 而它的读数会被拿去决定"要不要优化"。
     */
    static int percentileIndex(double p, int size) {
        if (size <= 0) {
            return 0;
        }
        int rank = (int) Math.ceil(p * size);
        return Math.min(size - 1, Math.max(0, rank - 1));
    }

    private static final com.sun.management.ThreadMXBean threads = threadBean();

    private static com.sun.management.ThreadMXBean threadBean() {
        try {
            java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
            if (bean instanceof com.sun.management.ThreadMXBean sun && sun.isThreadAllocatedMemorySupported()) {
                if (!sun.isThreadAllocatedMemoryEnabled()) {
                    sun.setThreadAllocatedMemoryEnabled(true);
                }
                return sun;
            }
        } catch (Throwable ignored) {
            // 拿不到就没有分配那一列：这把尺子少一刻度，但别的刻度照常。
        }
        return null;
    }

    /** 让"谁在用这把尺子"一眼可见。 */
    static {
        if (threads == null) {
            AtomChat.LOGGER.debug(com.atom.chat.diagnostics.AtomChatLog.message("render", "Thread allocation counting unavailable; the render profile omits alloc"));
        }
    }
}
