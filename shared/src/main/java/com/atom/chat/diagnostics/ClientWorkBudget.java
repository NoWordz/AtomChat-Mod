package com.atom.chat.diagnostics;

import java.util.Arrays;
import java.util.Locale;

/**
 * 非渲染链路的每 tick 总墙钟观测（全链路覆盖地图里的盲区②最小切片）。
 *
 * <p>只量三端 {@code END_CLIENT_TICK} 注册体里那一整段非绘制工作的总耗时。
 * 它不区分具体子项，但足以先回答「非渲染是不是瓶颈」这个前置问题：总量远小于
 * 帧预算就不必细分；总量显著再按子项下钻。这样一次启动即可采全，不返工。
 *
 * <p>门控复用 {@link FrameProfile#enabled()}（= debug &amp;&amp; !measureOnly）。
 * 产品默认 debug=off 时 {@link #startTick()} 返回 0，配对调用点只做一次长比较，
 * 不取时钟、不写内存、不输出日志，非渲染路径与改动前逐字节等价。
 *
 * <p>纯 Java：不引用 Minecraft / Skija / GL / SLF4J。日志行由本类生成完整字符串，
 * 宿主只转发，与 frame_probe / frame_pump 的既有约定一致。
 */
public final class ClientWorkBudget {

    public static final int REPORT_EVERY = 600;

    /** 一行稳定可解析的日志契约；字段顺序与 p50/p95/max 单位均为毫秒。 */
    public static final String LINE_RE =
            "^\\[AtomChat\\]\\[probe\\] client_work n=([0-9]+) "
            + "p50=([0-9]+\\.[0-9]+) p95=([0-9]+\\.[0-9]+) "
            + "max=([0-9]+\\.[0-9]+)$";

    private static final int WINDOW = 1024;
    /** 相邻 tick 间隔上界（1s）：跨会话/暂停的巨值不计入样本。 */
    private static final long MAX_TICK_NANOS = 1_000_000_000L;
    private static final ClientWorkBudget INSTANCE = new ClientWorkBudget();

    public static ClientWorkBudget get() {
        return INSTANCE;
    }

    private final long[] samples = new long[WINDOW];
    private final long[] sortBuf = new long[WINDOW];
    private int head;
    private int size;
    private int sinceReport;

    public ClientWorkBudget() {
    }

    /** 关掉时返回 0，调用点不必自己写 if，也就不会漏判开关。 */
    public long startTick() {
        return FrameProfile.enabled() ? System.nanoTime() : 0L;
    }

    /** token 为 0 时静默（产品默认零开销）。 */
    public void endTick(long token) {
        if (token == 0L) return;
        record(System.nanoTime() - token);
    }

    /** 包内可见：让离屏测试能以固定纳秒数喂样本，不必依赖真实时钟。 */
    void record(long nanos) {
        if (!FrameProfile.enabled()) return;
        if (nanos <= 0L || nanos >= MAX_TICK_NANOS) return;
        samples[head] = nanos;
        head = head + 1 == WINDOW ? 0 : head + 1;
        if (size < WINDOW) size++;
        sinceReport++;
    }

    /** 每 {@value #REPORT_EVERY} 个样本返回一行；无数据或门控关闭时返回 null。 */
    public String summaryLine() {
        if (!FrameProfile.enabled() || sinceReport < REPORT_EVERY || size == 0) return null;
        sinceReport = 0;
        int n = Math.min(REPORT_EVERY, size);
        for (int i = 0; i < n; i++) {
            int idx = head - 1 - i;
            while (idx < 0) idx += WINDOW;
            sortBuf[i] = samples[idx];
        }
        Arrays.sort(sortBuf, 0, n);
        double p50 = sortBuf[percentile(n, 0.50)] / 1_000_000.0;
        double p95 = sortBuf[percentile(n, 0.95)] / 1_000_000.0;
        double max = sortBuf[n - 1] / 1_000_000.0;
        return "[AtomChat][probe] client_work n=" + n
                + " p50=" + String.format(Locale.ROOT, "%.3f", p50)
                + " p95=" + String.format(Locale.ROOT, "%.3f", p95)
                + " max=" + String.format(Locale.ROOT, "%.2f", max);
    }

    private static int percentile(int n, double p) {
        int idx = (int) Math.ceil(p * n) - 1;
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return idx;
    }
}
