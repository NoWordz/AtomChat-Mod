package com.atom.chat.render;

import com.atom.chat.font.FontManager;
import com.atom.lib.font.FontSource;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Typeface;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 离屏测量：把「首开帧里原来要付的两笔账」单独测出来，给下次真机的
 * {@code first_open_budget} 行一个可对照的量级。它不启动游戏、不碰 GL，
 * 因此只能证明 CPU/文件 IO 的部分，不能替代真机首帧。
 */
class FirstOpenOffscreenMeasureTest {

    @Test void coldTypefaceAndEmojiRasterAreMeasuredOffscreen() throws Exception {
        String resource = "/com/atom/lib/fonts/bundled-default.otf";
        // Warm the JVM/JIT/Skija class path so the reported number is a repeat cost.
        try (Typeface ignored = FontManager.decodeTypeface(FontSource.resource(resource).read())) {
            // warmup only
        }

        long[] read = new long[5];
        long[] decode = new long[5];
        for (int i = 0; i < 5; i++) {
            long t0 = System.nanoTime();
            byte[] bytes = FontSource.resource(resource).read();
            long t1 = System.nanoTime();
            try (Typeface face = FontManager.decodeTypeface(bytes)) {
                assertNotNull(face);
            }
            long t2 = System.nanoTime();
            read[i] = t1 - t0;
            decode[i] = t2 - t1;
        }

        Font emoji = FontManager.font(20.0F);
        long[] raster = new long[5];
        long firstRaster = -1L;
        for (int i = 0; i < 5; i++) {
            // Drop the glyph bitmap so this measures the draw path the first frame
            // used to pay: resolve the emoji face (cached after iteration 0) and
            // rasterize the colour glyph.
            SkiaFontRenderer.clearEmojiGlyphCache();
            long t0 = System.nanoTime();
            try (Surface surface = Surface.makeRasterN32Premul(96, 96)) {
                surface.getCanvas().clear(0);
                surface.getCanvas().scale(2.0F, 2.0F);
                SkiaFontRenderer.drawText(surface.getCanvas(), emoji, "\uD83D\uDE00", 8, 35, 0xFFFFFFFF);
            }
            raster[i] = System.nanoTime() - t0;
            if (i == 0) {
                firstRaster = raster[i];
            }
        }

        Arrays.sort(read);
        Arrays.sort(decode);
        Arrays.sort(raster);
        double readMs = read[2] / 1e6;
        double decodeMs = decode[2] / 1e6;
        double rasterMs = raster[2] / 1e6;

        Path out = repoRoot().resolve("build/first-open-20261106/offscreen-measure.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out,
                "[first-open-offscreen] resource_read_median_ms=" + fmt(readMs)
                        + " typeface_decode_median_ms=" + fmt(decodeMs)
                        + " emoji_raster_median_ms=" + fmt(rasterMs)
                        + " emoji_first_raster_ms=" + fmt(firstRaster / 1e6) + "\n",
                StandardCharsets.UTF_8);

        assertTrue(readMs > 0.0);
        assertTrue(decodeMs > 0.0);
        assertTrue(rasterMs > 0.0);
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("platforms"))) p = p.getParent();
        return java.util.Objects.requireNonNull(p);
    }
}
