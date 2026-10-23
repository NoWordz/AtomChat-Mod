package com.atom.chat.image;

import io.github.humbleui.skija.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageLoaderTest {
    @Test void linkedCacheCannotReadWriteTrimOrClearFilesInsideConfig() throws Exception {
        Path target = Files.createDirectories(tempDir.resolve("config/images"));
        Path original = target.resolve("existing.bin"); Files.writeString(original,"private cache");
        Files.setLastModifiedTime(original, FileTime.fromMillis(1));
        Path link = tempDir.resolve("image-cache");
        try { Files.createSymbolicLink(link,target); }
        catch (java.nio.file.FileSystemException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
            Process process = new ProcessBuilder("cmd.exe","/d","/c","mklink","/J",link.toString(),target.toString())
                    .redirectErrorStream(true).start();
            assertTrue(process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0,process.exitValue());
        }
        try {
            ImageLoader loader = ImageLoader.forTest(url -> png(2,2),System::currentTimeMillis,Runnable::run);
            loader.init(link);
            assertNotNull(loader.get("http://test/new",true),"unsafe disk paths still allow memory-only images");
            loader.trimDiskCache(); loader.clearDiskCache();
            assertEquals("private cache",Files.readString(original));
            try (var files = Files.list(target)) { assertEquals(1,files.count()); }
        } finally { Files.deleteIfExists(link); }
    }
    @TempDir
    Path tempDir;

    // The direct executor runs every load synchronously inside get(), so all
    // cache/eviction ordering is deterministic.

    private static byte[] png(int w, int h) {
        try (var surface = io.github.humbleui.skija.Surface.makeRasterN32Premul(w, h)) {
            return surface.makeImageSnapshot().encodeToData().getBytes();
        }
    }

    private ImageLoader loader(java.util.function.LongSupplier clock, ImageLoader.Fetcher fetcher) {
        ImageLoader loader = ImageLoader.forTest(fetcher, clock, Runnable::run);
        loader.init(tempDir);
        return loader;
    }

    // ---- tests ----

    @Test
    void lruEvictsLeastRecentlyUsed() {
        ImageLoader l = loader(System::currentTimeMillis, url -> png(2, 2));
        String[] urls = new String[ImageLoader.MAX_CACHED + 1];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = "http://test/img" + i;
            assertNotNull(l.get(urls[i], true));
        }
        // Assertions use the non-triggering single-arg get(): a visible get on
        // an evicted URL would legitimately start a fresh download.
        assertNull(l.get(urls[0]));
        // Touching index 1 keeps it alive over index 2 when one more is added.
        assertNotNull(l.get(urls[1]));
        l.get("http://test/extra", true);
        assertNull(l.get(urls[2]));
        assertNotNull(l.get(urls[1]));
        assertNotNull(l.get(urls[urls.length - 1]));
    }

    @Test
    void failedFetchEntersNegativeCacheUntilTtl() {
        AtomicInteger calls = new AtomicInteger();
        AtomicLong now = new AtomicLong(0);
        ImageLoader l = loader(now::get, url -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        String url = "http://test/dead";
        assertNull(l.get(url, true));
        assertEquals(1, calls.get());
        now.set(ImageLoader.FAILURE_TTL_MS / 2);
        // Within the TTL window the fetch is not retried.
        assertNull(l.get(url, true));
        assertEquals(1, calls.get());
        // After the TTL it retries once.
        now.set(ImageLoader.FAILURE_TTL_MS + 1);
        assertNull(l.get(url, true));
        assertEquals(2, calls.get());
    }

    @Test
    void invisibleGetNeverTriggersDownload() {
        AtomicInteger calls = new AtomicInteger();
        ImageLoader l = loader(System::currentTimeMillis, url -> {
            calls.incrementAndGet();
            return png(2, 2);
        });
        assertNull(l.get("http://test/hidden"));
        assertNull(l.get("http://test/hidden", false));
        assertEquals(0, calls.get());
    }

    @Test
    void diskCacheSurvivesLoaderRecreation() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        byte[] bytes = png(3, 3);
        ImageLoader first = loader(System::currentTimeMillis, url -> {
            calls.incrementAndGet();
            return bytes;
        });
        String url = "http://test/pic";
        assertNotNull(first.get(url, true));
        assertEquals(1, calls.get());
        Path cached = Files.list(tempDir).findFirst().orElseThrow();
        assertEquals(ImageLoader.sha256Hex(url) + ".bin", cached.getFileName().toString());

        // A fresh loader with a dead fetcher still serves the image from disk.
        ImageLoader second = loader(System::currentTimeMillis, url2 -> {
            throw new IllegalStateException("no network");
        });
        assertNotNull(second.get(url, true));
        assertEquals(1, calls.get()); // never hit the network again
    }

    @Test
    void diskCacheStoresProcessedImageNotTheOriginalDownload() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        byte[] raw = png(1200, 800);
        ImageLoader l = loader(System::currentTimeMillis, url -> {
            calls.incrementAndGet();
            return raw;
        });
        String url = "http://test/big";
        assertNotNull(l.get(url, true));
        assertEquals(1, calls.get());
        Path cached = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().endsWith(".bin"))
                .findFirst().orElseThrow();
        // The cache stores the display-size re-encode, not the downloaded raw.
        byte[] cachedBytes = Files.readAllBytes(cached);
        assertTrue(cachedBytes.length > 0);
        assertTrue(!java.util.Arrays.equals(cachedBytes, raw));
        Image decoded = Image.makeFromEncoded(cachedBytes);
        try {
            assertNotNull(decoded);
            assertTrue(decoded.getWidth() <= ImageLoader.MAX_DIM);
            assertTrue(decoded.getHeight() <= ImageLoader.MAX_DIM);
        } finally {
            if (decoded != null) {
                decoded.close();
            }
        }
    }

    @Test
    void largeImagesAreDownsampledToMaxDim() {
        // downscale() closes the source when it actually rescales, so each
        // case constructs its own image and closes only what it still owns.
        Image scaled = ImageLoader.downscale(Image.makeFromEncoded(png(1000, 200)));
        assertEquals(ImageLoader.MAX_DIM, scaled.getWidth());
        assertEquals(154, scaled.getHeight()); // 200 * 768/1000
        scaled.close();

        Image same = ImageLoader.downscale(Image.makeFromEncoded(png(64, 32)));
        // Never upscaled: identical dimensions (the source is returned as-is).
        assertEquals(64, same.getWidth());
        assertEquals(32, same.getHeight());
        same.close();
    }

    /** Lays a cache file down by hand, stamped {@code ageDays} in the past. */
    private Path staleCacheFile(String url, byte[] bytes, int ageDays) throws IOException {
        Path file = tempDir.resolve(ImageLoader.sha256Hex(url) + ".bin");
        Files.write(file, bytes);
        Files.setLastModifiedTime(file,
                FileTime.fromMillis(System.currentTimeMillis() - ageDays * 86_400_000L));
        return file;
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = ImageLoaderTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "missing test resource " + name);
            return in.readAllBytes();
        }
    }

    @Test
    void diskCacheDropsEntriesUnusedForAWeek() throws Exception {
        // Files this old exist before the loader is built, so the startup trim
        // is what has to remove them.
        Path stale = staleCacheFile("http://test/stale", png(2, 2), 8);
        Path fresh = staleCacheFile("http://test/fresh", png(2, 2), 6);

        loader(System::currentTimeMillis, url -> {
            throw new IllegalStateException("no network");
        });

        assertFalse(Files.exists(stale), "eight days unused must not survive the seven day cap");
        assertTrue(Files.exists(fresh), "six days unused is still inside the cap");
    }

    @Test
    void readingADiskCacheEntryRefreshesItsAge() throws Exception {
        // The animated path is the one that matters: unlike the static path it
        // does not rewrite the cache file on a hit, so only the last-used stamp
        // can keep it alive here.
        ImageLoader l = loader(System::currentTimeMillis, url -> {
            throw new IllegalStateException("no network");
        });
        String seen = "http://test/anim.gif";
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        Path kept = staleCacheFile(seen, gif, 8);
        Path idle = staleCacheFile("http://test/idle", gif, 8);

        // Served from disk (the fetcher only throws), which stamps it as used.
        assertNotNull(l.get(seen, true));

        l.trimDiskCache();

        assertTrue(Files.exists(kept), "an entry read just now must survive the age cap");
        assertFalse(Files.exists(idle), "an entry nobody asked for ages out");
    }

    @Test
    void sha256HexIsStableAndWellFormed() {
        String a = ImageLoader.sha256Hex("http://example.com/a.png");
        String b = ImageLoader.sha256Hex("http://example.com/a.png");
        String c = ImageLoader.sha256Hex("http://example.com/b.png");
        assertEquals(a, b);
        assertEquals(64, a.length());
        assertTrue(a.matches("[0-9a-f]{64}"));
        assertTrue(!a.equals(c));
    }
}
