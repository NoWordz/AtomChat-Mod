package com.atom.chat.render;

import com.atom.chat.diagnostics.FrameProfile;
import com.atom.chat.font.FontManager;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离屏微基准：**只画图元**，回答两个问题 —— 时间花在哪一类图元上、以及
 * {@code SkiaDraw} 每个 helper 现造原生对象这件事值多少。
 *
 * <p>为什么单独有它：全树唯一一处**有代码依据**的性能嫌疑点就在这里 ——
 * {@code drawRoundedRect} / {@code drawEdgeHighlight} / {@code drawRoundedImage} /
 * {@code drawVerticalGradient} 每次调用各造一个 {@code Paint}，{@code drawRoundedShadow}
 * 还每次造一个 {@code MaskFilter.makeBlur}，而它们被逐图元、逐帧调用。
 *
 * <p><strong>A/B 那一对</strong>：同一组图元画两遍，第二遍只把圆角矩形与阴影用到的
 * {@code Paint}（以及阴影那个模糊滤镜）提到每次绘制之外 —— 图元、颜色、顺序、模糊半径全都一样，
 * 差的只有"这些对象是谁造的、造了几次"。渐变两条路都照旧现造（它的 shader 每次坐标都不同，
 * 提不动），所以两行的差**就是**这部分分配值多少。**这是一次测量，不是改法提议。**
 *
 * <p><strong>分组那几行</strong>是各自独立的负载，不是"整体的组成部分"：分别只画一类图元，
 * 用来定位时间花在哪一类上。它们加起来不等 {@code all}（裁剪与覆盖不一样），别去对账。
 *
 * <p>默认**跳过**，要 {@code ATOMCHAT_BENCH=1} 才跑：计时不该进 CI。共享 runner 的负载会让它
 * 忽红忽绿，而一个会假红的测试最后一定被人关掉 —— 那还不如没有。用环境变量而不是系统属性：
 * Gradle 默认把环境变量带给测试 JVM，而 {@code -D} 不会（那要改三份 build.gradle）。
 *
 * <p>量的是 **CPU 光栅**，与 {@code SkiaGraphics} 的 GL 后端是两条路：这里的数字能用来比较
 * 改动前后、定位是哪一类图元贵，**不能**当成玩家的帧率。
 */
class SkiaDrawBenchmark {

    private static final String SKIP_HINT =
            "微基准默认不跑：ATOMCHAT_BENCH=1 ./gradlew test --tests '*SkiaDrawBenchmark*'";

    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;
    private static final int WARMUP_FRAMES = 20;
    private static final int MEASURED_FRAMES = 80;

    /** 一"帧"的图元构成：按面板的观感取的量级，不是精确还原某一页。 */
    private static final int RECTS = 400;
    private static final int SHADOWS = 60;
    private static final int GRADIENTS = 40;
    private static final int TEXTS = 40;

    private static final float SHADOW_BLUR = 8.0F;
    private static final int SHADOW_COLOR = 0x60000000;

    /** 画哪一类图元。{@link #ALL} 是全套，其余各自独立成一次负载。 */
    private enum Group {
        ALL("all", "per-call Paint", "hoisted Paint"),
        RECT("rrect only", null, null),
        SHADOW("shadow only", null, null),
        GRADIENT("gradient only", null, null),
        TEXT("text only", null, null);

        private final String label;
        private final String currentLabel;
        private final String hoistedLabel;

        Group(String label, String currentLabel, String hoistedLabel) {
            this.label = label;
            this.currentLabel = currentLabel;
            this.hoistedLabel = hoistedLabel;
        }
    }

    @Test
    void primitiveCostPerFrame() {
        Assumptions.assumeTrue("1".equals(System.getenv("ATOMCHAT_BENCH")), SKIP_HINT);

        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            Measurement perCall = measure(canvas, Group.ALL, false);
            Measurement hoisted = measure(canvas, Group.ALL, true);

            StringBuilder sb = new StringBuilder();
            sb.append("AtomChat bench: SkiaDraw primitives, CPU raster, no GL");
            sb.append("\nAtomChat bench:   ").append(MEASURED_FRAMES).append(" frames, ")
                    .append(WARMUP_FRAMES).append(" warmup, surface ")
                    .append(SURFACE_W).append('x').append(SURFACE_H);
            sb.append("\nAtomChat bench:   full frame: ").append(RECTS).append(" rrect + ")
                    .append(SHADOWS).append(" shadow + ").append(GRADIENTS).append(" gradient + ")
                    .append(TEXTS).append(" text");
            sb.append('\n').append(perCall.row("all (per-call)"));
            sb.append('\n').append(hoisted.row("all (hoisted)"));
            sb.append("\nAtomChat bench:   the two rows above differ only in who allocates the rrect/shadow");
            sb.append("\nAtomChat bench:   Paint and the blur filter; that is a measurement, not a proposal");
            for (Group group : new Group[]{Group.RECT, Group.SHADOW, Group.GRADIENT, Group.TEXT}) {
                sb.append('\n').append(measure(canvas, group, false).row(group.label));
            }
            sb.append("\nAtomChat bench:   the group rows are separate workloads, not parts of a whole");
            // 一次性成本：真机上面板第一帧的 max 是 174 ms，而稳定态 p50 只有 1.12 ms。
            // 最像的是"两个 OTF 一共 7.2 MB，第一次用到才解析" —— 那就把它量出来，别猜。
            sb.append("\nAtomChat bench:   one-time first-touch costs (per session)");
            sb.append('\n').append(parseRow("/assets/atomchat/font/bundled.otf"));
            sb.append('\n').append(parseRow("/assets/atomchat/font/bundled-bold.otf"));
            System.out.println(sb);

            assertTrue(drawnBytes(surface) > 0, "基准画了个空 —— 那量出来的数没有意义");
        }
    }

    /**
     * 画一"帧"。{@code hoisted} 为真时，圆角矩形与阴影各用**一个**在帧内复用的
     * {@code Paint}（阴影那个挂着一个模糊滤镜），其余完全照旧。
     */
    private static void drawFrame(Canvas canvas, Group group, boolean hoisted) {
        Paint fill = null;
        Paint shadow = null;
        MaskFilter blur = null;
        try {
            if (hoisted) {
                blur = MaskFilter.makeBlur(FilterBlurMode.NORMAL, SHADOW_BLUR);
                fill = new Paint().setAntiAlias(true);
                shadow = new Paint().setAntiAlias(true).setMaskFilter(blur);
            }
            if (group == Group.ALL || group == Group.RECT) {
                for (int i = 0; i < RECTS; i++) {
                    float x = (i % 20) * 20.0F;
                    float y = (i / 20) * 36.0F;
                    int color = 0xFF000000 | (i % 2 == 0 ? 0x334455 : 0x667788);
                    if (hoisted) {
                        canvas.drawRRect(RRect.makeXYWH(x, y, 18.0F, 34.0F, 4.0F), fill.setColor(color));
                    } else {
                        SkiaDraw.drawRoundedRect(canvas, x, y, 18.0F, 34.0F, 4.0F, color);
                    }
                }
            }
            if (group == Group.ALL || group == Group.SHADOW) {
                for (int i = 0; i < SHADOWS; i++) {
                    float x = (i % 8) * 50.0F;
                    float y = (i / 8) * 90.0F;
                    if (hoisted) {
                        // 与 SkiaDraw.drawRoundedShadow 逐字等价：同样的偏移、同样的半径。
                        canvas.drawRRect(RRect.makeXYWH(x + SHADOW_BLUR * 0.5F, y + SHADOW_BLUR * 0.5F,
                                40.0F, 30.0F, 6.0F), shadow.setColor(SHADOW_COLOR));
                    } else {
                        SkiaDraw.drawRoundedShadow(canvas, x, y, 40.0F, 30.0F, 6.0F, SHADOW_BLUR, SHADOW_COLOR);
                    }
                }
            }
            if (group == Group.ALL || group == Group.GRADIENT) {
                // 渐变两条路都现造：它的 shader 每次坐标都不同，提不动 —— 所以它不参与那个差。
                for (int i = 0; i < GRADIENTS; i++) {
                    float x = (i % 5) * 80.0F;
                    float y = 620.0F + (i / 5) * 24.0F;
                    SkiaDraw.drawVerticalGradient(canvas, x, y, 70.0F, 20.0F, 8.0F, 0x22FFFFFF, 0x08FFFFFF);
                }
            }
            if (group == Group.ALL || group == Group.TEXT) {
                Font font = FontManager.font(15.0F);
                for (int i = 0; i < TEXTS; i++) {
                    SkiaFontRenderer.drawText(canvas, font, "bench line " + i, 8.0F, 700.0F + i * 3.0F, 0xFF222222);
                }
            }
        } finally {
            if (fill != null) {
                fill.close();
            }
            if (shadow != null) {
                shadow.close();
            }
            if (blur != null) {
                blur.close();
            }
        }
    }

    private static Measurement measure(Canvas canvas, Group group, boolean hoisted) {
        for (int i = 0; i < WARMUP_FRAMES; i++) {
            drawFrame(canvas, group, hoisted);
        }
        long[] nanos = new long[MEASURED_FRAMES];
        long allocatedTotal = 0L;
        int allocatedSamples = 0;
        for (int i = 0; i < MEASURED_FRAMES; i++) {
            long allocatedBefore = FrameProfile.allocatedBytes();
            long startedAt = System.nanoTime();
            drawFrame(canvas, group, hoisted);
            nanos[i] = System.nanoTime() - startedAt;
            long allocatedAfter = FrameProfile.allocatedBytes();
            if (allocatedBefore >= 0L && allocatedAfter >= allocatedBefore) {
                allocatedTotal += allocatedAfter - allocatedBefore;
                allocatedSamples++;
            }
        }
        return new Measurement(nanos, allocatedTotal, allocatedSamples);
    }

    /**
     * 解析一个打包字体的耗时。这是一次性成本：`FontManager` 是懒加载的，而第一次用到它
     * 就在绘制路径里 —— 所以它落在真机的 `panel` 桶里，而不是 `layout` 桶里。
     */
    private static String parseRow(String resource) {
        try (java.io.InputStream in = SkiaDrawBenchmark.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "AtomChat bench:   " + resource + " (not on the classpath)";
            }
            byte[] bytes = in.readAllBytes();
            long startedAt = System.nanoTime();
            io.github.humbleui.skija.Typeface face = io.github.humbleui.skija.FontMgr.getDefault().makeFromData(
                    io.github.humbleui.skija.Data.makeFromBytes(bytes));
            long millis = (System.nanoTime() - startedAt) / 1_000_000L;
            if (face != null) {
                face.close();
            }
            return String.format(java.util.Locale.ROOT, "AtomChat bench:   parse %-16s %d ms  (%.2f MB)",
                    resource.substring(resource.lastIndexOf('/') + 1), millis, bytes.length / 1048576.0);
        } catch (Exception e) {
            return "AtomChat bench:   parse " + resource + " failed: " + e;
        }
    }

    /** 非零字节数：确认基准真的画了东西，而不是量了一个空循环。 */
    private static int drawnBytes(Surface surface) {
        io.github.humbleui.skija.Bitmap bitmap = new io.github.humbleui.skija.Bitmap();
        try {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "读不回位图");
            byte[] pixels = bitmap.readPixels();
            int nonZero = 0;
            for (byte b : pixels) {
                if (b != 0) {
                    nonZero++;
                }
            }
            return nonZero;
        } finally {
            bitmap.close();
        }
    }

    private record Measurement(long[] nanos, long allocatedTotal, int allocatedSamples) {

        private String row(String label) {
            long[] sorted = Arrays.copyOf(nanos, nanos.length);
            Arrays.sort(sorted);
            long total = 0L;
            for (long v : sorted) {
                total += v;
            }
            StringBuilder sb = new StringBuilder("AtomChat bench:   ");
            sb.append(String.format(java.util.Locale.ROOT, "%-16s", label));
            sb.append(" p50=").append(ms(sorted[sorted.length / 2]));
            sb.append("  p95=").append(ms(sorted[(int) Math.ceil(0.95 * sorted.length) - 1]));
            sb.append("  max=").append(ms(sorted[sorted.length - 1]));
            sb.append("  mean=").append(ms(total / sorted.length));
            sb.append("  alloc=");
            sb.append(allocatedSamples == 0 ? "n/a"
                    : String.format(java.util.Locale.ROOT, "%.1f KB/frame",
                            allocatedTotal / (double) allocatedSamples / 1024.0));
            return sb.toString();
        }

        private static String ms(long nanos) {
            return String.format(java.util.Locale.ROOT, "%.3fms", nanos / 1_000_000.0);
        }
    }
}
