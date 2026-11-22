package com.atom.chat.fun;

import com.atom.chat.config.AtomChatConfig;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.Shader;
import java.util.Random;
import java.util.regex.Pattern;

/** One playback clock for incoming and outgoing messages and both render entry points. */
public final class NewYearFireworks {
    public static final NewYearFireworks INSTANCE = new NewYearFireworks();
    private static final long DURATION_MS = 7200;
    private static final long COOLDOWN_MS = 9500;
    private static final Pattern GREETING = Pattern.compile(
            "新年快[乐樂]|(?<![\\p{L}\\p{N}_/@.:-])happy\\h+new\\h+year(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern QUOTED_LINE = Pattern.compile("(?m)^\\h*>[^\\r\\n]*");
    private static final int[] BAND_COUNTS = {18, 29, 47};
    private static final ThreadLocal<SparkBand[]> BANDS = ThreadLocal.withInitial(
            () -> new SparkBand[]{new SparkBand(BAND_COUNTS[0]), new SparkBand(BAND_COUNTS[1]), new SparkBand(BAND_COUNTS[2])});
    private static final Shell[] SHELLS = {
            // A paired gold launch opens from the lower corners.
            new Shell(.19F, .30F, .05F, 0, 820, .225F, Palette.GOLD, Shape.PEONY, 11),
            new Shell(.81F, .26F, .95F, 190, 800, .225F, Palette.GOLD, Shape.RING, 27),
            // Alternate color and height across the sky.
            new Shell(.50F, .23F, .43F, 760, 800, .315F, Palette.PINK, Shape.PEONY, 39),
            new Shell(.29F, .38F, .12F, 1170, 760, .250F, Palette.CYAN, Shape.RING, 44),
            new Shell(.73F, .32F, .88F, 1450, 750, .266F, Palette.VIOLET, Shape.PEONY, 58),
            new Shell(.13F, .29F, .05F, 1870, 800, .198F, Palette.CORAL, Shape.PEONY, 62),
            new Shell(.86F, .43F, .96F, 2010, 840, .236F, Palette.MINT, Shape.RING, 71),
            new Shell(.48F, .43F, .57F, 2420, 820, .290F, Palette.GOLD, Shape.WILLOW, 84),
            new Shell(.31F, .16F, .22F, 2740, 860, .235F, Palette.PINK, Shape.RING, 96),
            new Shell(.68F, .15F, .79F, 2950, 850, .243F, Palette.CYAN, Shape.PEONY, 102),
            // Fan the finale across the full width, with gold falling through it.
            new Shell(.09F, .38F, .21F, 3950, 750, .288F, Palette.CORAL, Shape.PEONY, 111),
            new Shell(.27F, .25F, .35F, 3870, 900, .326F, Palette.VIOLET, Shape.RING, 123),
            new Shell(.50F, .25F, .50F, 3830, 980, .345F, Palette.GOLD, Shape.WILLOW, 137),
            new Shell(.73F, .25F, .65F, 3910, 870, .334F, Palette.CYAN, Shape.PEONY, 146),
            new Shell(.91F, .38F, .79F, 3960, 760, .281F, Palette.PINK, Shape.PEONY, 156),
            new Shell(.22F, .51F, .40F, 4370, 800, .296F, Palette.GOLD, Shape.WILLOW, 169),
            new Shell(.78F, .51F, .60F, 4390, 820, .296F, Palette.GOLD, Shape.WILLOW, 172),
            new Shell(.51F, .47F, .50F, 4520, 820, .245F, Palette.CORAL, Shape.RING, 189)
    };

    private volatile long started = Long.MIN_VALUE;
    private long nextAllowed = Long.MIN_VALUE;

    static boolean enabled(AtomChatConfig config) {
        return config.enabled && config.experimentalEffectsEnabled && config.effectiveAnimationEnabled();
    }

    synchronized boolean onMessage(String body, boolean allowed, long nowMs) {
        if (!allowed) {
            started = Long.MIN_VALUE;
            return false;
        }
        if (nowMs < nextAllowed) return false;
        String text = matchableGreeting(body);
        if (text == null || !GREETING.matcher(text).find()) return false;
        started = nowMs;
        nextAllowed = nowMs + COOLDOWN_MS;
        return true;
    }

    static String matchableGreeting(String body) {
        String text = ChatEffectMatcher.matchableText(body);
        return text == null ? null : QUOTED_LINE.matcher(text)
                .replaceAll(match -> "\uFFFC".repeat(match.group().length()));
    }

    public synchronized void reset() {
        started = Long.MIN_VALUE;
        nextAllowed = Long.MIN_VALUE;
    }

    synchronized boolean active(boolean allowed, long nowMs) {
        if (!allowed || started != Long.MIN_VALUE && (nowMs < started || nowMs - started >= DURATION_MS)) {
            started = Long.MIN_VALUE;
        }
        return started != Long.MIN_VALUE;
    }

    public boolean active(long nowMs) {
        return started != Long.MIN_VALUE && active(enabled(AtomChatConfig.get()), nowMs);
    }

    public void draw(Canvas canvas, float width, float height, long nowMs) {
        if (started == Long.MIN_VALUE) return;
        long elapsed;
        synchronized (this) {
            if (!active(nowMs)) return;
            elapsed = nowMs - started;
        }
        drawFrame(canvas, width, height, elapsed);
    }

    static void drawFrame(Canvas canvas, float width, float height, long elapsed) {
        if (elapsed <= 0 || elapsed >= DURATION_MS || width <= 0 || height <= 0) return;
        float unit = Math.min(height, width / 1.08F);
        try (Paint paint = new Paint().setAntiAlias(true).setStrokeCap(PaintStrokeCap.ROUND)) {
            for (Shell shell : SHELLS) {
                float age = elapsed - shell.launch;
                if (age <= 0 || age >= shell.ascent + shell.shape.lifetime) continue;
                if (age < shell.ascent) {
                    drawRocket(canvas, paint, shell, width, height, unit, age / shell.ascent);
                } else {
                    drawBurst(canvas, paint, shell, width * shell.x, height * shell.y, unit,
                            (age - shell.ascent) / shell.shape.lifetime);
                }
            }
        }
    }

    private static void drawRocket(Canvas canvas, Paint paint, Shell shell, float width, float height, float unit, float t) {
        float x = width * shell.x;
        float y = height * shell.y;
        float travelX = width * shell.originX - x;
        float travelY = height * .95F - y;
        float head = rocketTravel(t);
        float headX = x + travelX * head;
        float headY = y + travelY * head;
        float light = Math.min(1F, t * 8F);
        float near = rocketTravel(Math.max(0F, t - .10F));
        float tailX = x + travelX * near;
        float tailY = y + travelY * near;
        stroke(canvas, paint, tailX, tailY, headX, headY, unit * .008F, 0x342219, light * .65F);
        stroke(canvas, paint, tailX, tailY, headX, headY, unit * .004F, Palette.GOLD.outer, light * .92F);
        for (int i = 1; i <= 15; i++) {
            float lag = i / 15F;
            float sample = Math.max(0F, t - .26F * lag);
            float trailing = rocketTravel(sample);
            float side = (float) Math.sin(i * 2.4F + t * 9F) * unit * .016F * lag;
            float emberX = x + travelX * trailing + side;
            float emberY = y + travelY * trailing + unit * .035F * lag * lag;
            spark(canvas, paint, emberX, emberY, unit * (.0036F - .0022F * lag),
                    Palette.GOLD.outer, light * (1F - .88F * lag));
        }
        bloom(canvas, paint, headX, headY, unit * .028F, Palette.GOLD, light * .72F);
        spark(canvas, paint, headX, headY, unit * .006F, Palette.GOLD.inner, light);
    }

    private static float rocketTravel(float t) {
        float rest = 1F - t;
        return rest * (0.32F + 0.68F * rest);
    }

    private static void drawBurst(Canvas canvas, Paint paint, Shell shell, float x, float y, float unit, float t) {
        Palette palette = shell.palette;
        float radius = unit * shell.radius;
        float gravity = unit * shell.shape.gravity;
        float ignition = Math.max(0F, 1F - t * 6F);
        if (ignition > 0F) {
            bloom(canvas, paint, x, y, radius * (.22F + t * 1.2F), palette, ignition * .72F);
        }

        float light = (1F - t) * (1F - .3F * t) * Math.min(1F, .54F + t * 16F);
        SparkBand[] bands = BANDS.get();
        int start = 0;
        for (int band = 0; band < bands.length; band++) {
            var batch = bands[band];
            batch.draw(canvas, paint, shell.rays, start, x, y, radius, gravity, t, unit,
                    band == 1 ? palette.inner : palette.outer, light);
            start += BAND_COUNTS[band];
        }
    }

    private static float spread(float t) {
        float rest = 1F - t;
        return 1F - rest * rest * rest * rest;
    }

    private static void stroke(Canvas canvas, Paint paint, float x, float y, float endX, float endY,
                               float width, int rgb, float alpha) {
        canvas.drawLine(x, y, endX, endY, paint.setColor(color(rgb, alpha)).setStrokeWidth(width));
    }

    private static void spark(Canvas canvas, Paint paint, float x, float y, float radius, int rgb, float alpha) {
        canvas.drawCircle(x, y, radius * 2.3F, paint.setColor(color(rgb, alpha * .22F)));
        canvas.drawCircle(x, y, radius, paint.setColor(color(rgb, alpha)));
        canvas.drawCircle(x, y, radius * .48F, paint.setColor(color(0xFFFAE9, alpha * .94F)));
    }

    private static void bloom(Canvas canvas, Paint paint, float x, float y, float radius, Palette palette, float alpha) {
        int saved = canvas.save();
        try {
            canvas.translate(x, y);
            canvas.scale(radius, radius);
            canvas.drawCircle(0F, 0F, 1F, paint.setShader(palette.bloom()).setColor(color(0xFFFFFF, alpha)));
        } finally {
            paint.setShader(null);
            canvas.restoreToCount(saved);
        }
    }

    private static int color(int rgb, float alpha) {
        return Math.round(alpha * 255F) << 24 | rgb;
    }

    private enum Palette {
        GOLD(0xFFF3BB, 0xFFBB49), PINK(0xFFF0CC, 0xFF60C5), CYAN(0xC6FFFD, 0x35D0FF),
        VIOLET(0xF8D8FF, 0xA089FF), CORAL(0xFFDC9F, 0xFF605D), MINT(0xE0FFCF, 0x42F5AB);

        private final int inner, outer;
        private Shader bloom;

        Palette(int inner, int outer) {
            this.inner = inner;
            this.outer = outer;
        }

        Shader bloom() {
            if (bloom == null) {
                bloom = Shader.makeRadialGradient(0F, 0F, 1F, new int[]{
                        0xFFFFF8E2, color(inner, .7F), color(outer, .18F), outer
                }, new float[]{0F, .16F, .59F, 1F});
            }
            return bloom;
        }
    }

    private enum Shape {
        PEONY(1750, .075F), RING(1650, .085F), WILLOW(1960, .32F);
        private final int lifetime;
        private final float gravity;

        Shape(int lifetime, float gravity) {
            this.lifetime = lifetime;
            this.gravity = gravity;
        }
    }

    private record Ray(float dx, float dy, float trail, float phase) {}

    /** Batch trails and embers by band to bound the render calls at peak overlap. */
    private static final class SparkBand {
        private final float[] heads, near, far;

        SparkBand(int count) {
            heads = new float[count * 2];
            near = new float[count * 4];
            far = new float[count * 4];
        }

        void draw(Canvas canvas, Paint paint, Ray[] rays, int start, float x, float y, float radius, float gravity,
                  float t, float unit, int rgb, float light) {
            float growth = spread(t), fall = gravity * t * t;
            for (int i = 0; i < heads.length / 2; i++) {
                Ray ray = rays[start + i];
                float middle = Math.max(0F, t - .42F * ray.trail);
                float tail = Math.max(0F, t - ray.trail);
                float dx = ray.dx * radius, dy = ray.dy * radius;
                float emberX = x + dx * growth;
                float emberY = y + dy * growth + fall;
                float midX = x + dx * spread(middle);
                float midY = y + dy * spread(middle) + gravity * middle * middle;
                heads[2 * i] = emberX;
                heads[2 * i + 1] = emberY;
                line(near, i, midX, midY, emberX, emberY);
                line(far, i, x + dx * spread(tail), y + dy * spread(tail) + gravity * tail * tail, midX, midY);
            }
            canvas.drawLines(near, paint.setColor(color(0x302236, light * .43F)).setStrokeWidth(unit * .0057F));
            canvas.drawLines(far, paint.setColor(color(rgb, light * .43F)).setStrokeWidth(unit * .0018F));
            canvas.drawLines(near, paint.setColor(color(rgb, light * .93F)).setStrokeWidth(unit * .0031F));
            canvas.drawPoints(heads, paint.setColor(color(rgb, light * .20F)).setStrokeWidth(unit * .015F));
            canvas.drawPoints(heads, paint.setColor(color(rgb, light * .97F)).setStrokeWidth(unit * .0068F));
            canvas.drawPoints(heads, paint.setColor(color(0xFFF8E5, light * .92F)).setStrokeWidth(unit * .0034F));
            if (t < .12F || t > .92F) return;
            for (int i = 2; i < heads.length / 2; i += 7) {
                float pulse = (float) Math.sin(t * 45F + rays[start + i].phase);
                if (pulse < .74F) continue;
                float cx = heads[2 * i], cy = heads[2 * i + 1];
                float size = unit * (.007F + .007F * pulse);
                stroke(canvas, paint, cx - size, cy, cx + size, cy, unit * .0018F, 0xFFF3CE, light);
                stroke(canvas, paint, cx, cy - size, cx, cy + size, unit * .0018F, 0xFFF3CE, light);
            }
        }

        private static void line(float[] lines, int i, float x, float y, float endX, float endY) {
            lines[4 * i] = x;
            lines[4 * i + 1] = y;
            lines[4 * i + 2] = endX;
            lines[4 * i + 3] = endY;
        }
    }

    private record Shell(float x, float y, float originX, int launch, int ascent, float radius,
                         Palette palette, Shape shape, Ray[] rays) {
        Shell(float x, float y, float originX, int launch, int ascent, float radius,
              Palette palette, Shape shape, long seed) {
            this(x, y, originX, launch, ascent, radius, palette, shape, rays(shape, seed));
        }

        private static Ray[] rays(Shape shape, long seed) {
            Random random = new Random(seed);
            Ray[] rays = new Ray[BAND_COUNTS[0] + BAND_COUNTS[1] + BAND_COUNTS[2]];
            float tilt = random.nextBoolean() ? .55F : -.55F;
            float tiltCos = (float) Math.cos(tilt), tiltSin = (float) Math.sin(tilt);
            int index = 0;
            for (int band = 0; band < BAND_COUNTS.length; band++) {
                float scale = .32F + band * .33F;
                for (int i = 0; i < BAND_COUNTS[band]; i++) {
                    double angle = (i + random.nextFloat() * .72F) * Math.PI * 2 / BAND_COUNTS[band] + band * .35;
                    float speed = scale * (.85F + random.nextFloat() * .27F);
                    float dx = speed * (float) Math.cos(angle);
                    float dy = speed * (float) Math.sin(angle);
                    if (shape == Shape.RING && band == 2) {
                        float ellipseY = dy * .44F;
                        dy = dx * tiltSin + ellipseY * tiltCos;
                        dx = dx * tiltCos - ellipseY * tiltSin;
                    }
                    float trail = (shape == Shape.WILLOW ? .19F : .096F) + random.nextFloat() * .095F;
                    rays[index++] = new Ray(dx, dy, trail, random.nextFloat() * 6.283F);
                }
            }
            return rays;
        }
    }
}
