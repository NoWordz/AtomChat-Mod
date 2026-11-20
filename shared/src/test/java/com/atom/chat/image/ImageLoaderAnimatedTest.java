package com.atom.chat.image;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageLoaderAnimatedTest {

    @TempDir
    Path tempDir;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = ImageLoaderAnimatedTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "missing test resource " + name);
            return in.readAllBytes();
        }
    }

    private static int argb(Image image, int x, int y) {
        Bitmap bitmap = new Bitmap();
        try {
            bitmap.allocN32Pixels(image.getWidth(), image.getHeight());
            image.readPixels(bitmap);
            return bitmap.getColor(x, y);
        } finally {
            bitmap.close();
        }
    }

    @Test
    void animatedGifAdvancesWithTheClock() throws IOException {
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        AtomicLong now = new AtomicLong(0L);
        // Direct executor: every load completes inside get(), like the other
        // ImageLoader tests, so frame assertions are deterministic.
        ImageLoader loader = ImageLoader.forTest(url -> gif, now::get, Runnable::run);
        loader.init(tempDir);

        Image first = loader.get("http://test/anim.gif", true);
        assertNotNull(first);
        assertEquals(0xFFFF0000, argb(first, 0, 0));

        now.set(150L);
        Image second = loader.get("http://test/anim.gif");
        assertNotNull(second);
        assertEquals(0xFF00FF00, argb(second, 0, 0));
    }

    @Test
    void animatedDiskCacheKeepsTheAnimation() throws IOException {
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        AtomicLong now = new AtomicLong(0L);
        ImageLoader first = ImageLoader.forTest(url -> gif, now::get, Runnable::run);
        first.init(tempDir);
        assertNotNull(first.get("http://test/anim2.gif", true));
        try (var stream = Files.list(tempDir)) {
            assertTrue(stream.anyMatch(p -> p.getFileName().toString().endsWith(".bin")),
                    "disk cache should hold the original GIF bytes");
        }

        // A second loader with a fetcher that must never run proves the disk
        // copy (verbatim GIF, not a flattened WebP) can be replayed.
        ImageLoader second = ImageLoader.forTest(url -> {
            throw new AssertionError("must not fetch when the disk cache has the bytes");
        }, now::get, Runnable::run);
        second.init(tempDir);
        Image replayed = second.get("http://test/anim2.gif", true);
        assertNotNull(replayed);
        assertEquals(0xFFFF0000, argb(replayed, 0, 0));
    }

    @Test
    void tenHudGifsRemainAnimatedWithoutReloadingPastThePanelLimit() throws IOException {
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        AtomicLong now = new AtomicLong();
        AtomicInteger fetches = new AtomicInteger();
        ImageLoader loader = ImageLoader.forTest(url -> {
            fetches.incrementAndGet();
            return gif;
        }, now::get, Runnable::run);

        List<String> urls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String url = "https://images.test/" + i + ".gif";
            urls.add(url);
            // Exercise both locally sent URLs and items warmed by the panel.
            if (i < 3) assertNotNull(loader.get(url, true));
            else loader.rememberLocallySent(url);
            assertEquals(0xFFFF0000, argb(loader.getHudImage(url, true), 0, 0));
        }

        for (long elapsed : new long[]{150, 50, 150}) {
            now.set(elapsed);
            for (String url : urls) {
                assertEquals(elapsed == 50 ? 0xFFFF0000 : 0xFF00FF00,
                        argb(loader.getHudImage(url, true), 0, 0));
            }
        }
        assertEquals(10, fetches.get(), "retained HUD GIFs never requeue or restart playback");
    }

    @Test
    void hudMissNeverFetchesAnExternalUrlOrCancelsThePanelsPendingWork() throws IOException {
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        String external = "https://images.test/untrusted.gif";
        String copy = "https://images.test/disk-replay.gif";
        AtomicInteger fetches = new AtomicInteger();
        var tasks = new ArrayList<Runnable>();
        AtomicInteger submissions = new AtomicInteger();
        var loader = ImageLoader.forTest(url -> {
            fetches.incrementAndGet();
            return gif;
        }, () -> 0L, work -> {
            submissions.incrementAndGet();
            tasks.add(work);
        });
        loader.init(tempDir);
        Files.write(tempDir.resolve(ImageLoader.sha256Hex(copy) + ".bin"), gif);
        while (!tasks.isEmpty()) tasks.remove(0).run();
        int initialSubmissions = submissions.get();

        for (int i = 0; i < 20; i++) {
            assertNull(loader.getHudImage(external, true));
            while (!tasks.isEmpty()) tasks.remove(0).run();
        }
        assertEquals(0, fetches.get());
        assertEquals(1, submissions.get() - initialSubmissions, "a cold disk miss is throttled across HUD frames");
        assertNull(loader.getHudImage(copy, true));
        while (!tasks.isEmpty()) tasks.remove(0).run();
        assertNotNull(loader.getHudImage(copy, true));
        assertEquals(0, fetches.get(), "a cached external GIF replays from disk");

        assertNull(loader.get(external, true));
        assertNull(loader.getHudImage(external, false));
        assertNull(loader.getHudImage(external, true));
        while (!tasks.isEmpty()) tasks.remove(0).run();
        assertNotNull(loader.getHudImage(external, true));
        assertEquals(1, fetches.get(), "the panel's explicit load completes exactly once");
    }

    @Test
    void serverMediaCanLoadForHudAndLosesItsThumbnailOnDisconnect() throws IOException {
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        AtomicInteger reads = new AtomicInteger();
        var loader = ImageLoader.forTest(url -> {
            reads.incrementAndGet();
            return gif;
        }, () -> 0L, Runnable::run);
        String url = com.atom.chat.net.MediaIds.urlFor(com.atom.chat.net.MediaIds.idFor(gif, "gif"));
        assertNotNull(loader.getHudImage(url, true));
        loader.invalidateMedia();
        assertNull(loader.getHudImage(url, false));
        assertNotNull(loader.getHudImage(url, true));
        assertEquals(2, reads.get());
    }

    @Test
    void laterHudFramesCannotExtendAnExternalPanelsFetchPermission() throws IOException {
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        AtomicLong now = new AtomicLong();
        AtomicInteger fetches = new AtomicInteger();
        var tasks = new ArrayList<Runnable>();
        var loader = ImageLoader.forTest(url -> {
            fetches.incrementAndGet();
            return gif;
        }, now::get, tasks::add);
        loader.init(tempDir);
        while (!tasks.isEmpty()) tasks.remove(0).run();
        String url = "https://images.test/delayed-external.gif";

        assertNull(loader.getHudImage(url, true)); // Disk-only work.
        assertNull(loader.get(url, true)); // The panel joins the queued work.
        now.set(2_501L);
        assertNull(loader.getHudImage(url, true)); // The panel has closed.
        tasks.remove(0).run();
        assertEquals(0, fetches.get(), "a recent HUD frame cannot renew a closed panel's permission");

        assertNull(loader.get(url, true)); // Opening the panel again authorizes a fetch.
        while (!tasks.isEmpty()) tasks.remove(0).run();
        assertEquals(1, fetches.get());
        assertNotNull(loader.get(url, true));
    }

    @Test
    void publicationBeforeAdmissionDoesNotQueueAnotherDecodeOrResetTheClock() throws Exception {
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        AtomicLong now = new AtomicLong();
        AtomicInteger fetches = new AtomicInteger();
        var tasks = new ArrayList<Runnable>();
        var loader = ImageLoader.forTest(url -> {
            fetches.incrementAndGet();
            return gif;
        }, now::get, tasks::add);
        String url = "https://images.test/published-concurrently.gif";
        loader.rememberLocallySent(url);
        assertNull(loader.getHudImage(url, true));

        var field = ImageLoader.class.getDeclaredField("pending");
        field.setAccessible(true);
        Object admissionMonitor = field.get(loader);
        var result = new FutureTask<>(() -> loader.getHudImage(url, true));
        var reader = new Thread(result, "HUD cache race test");
        reader.setDaemon(true);
        synchronized (admissionMonitor) {
            reader.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (reader.getState() != Thread.State.BLOCKED && reader.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            assertEquals(Thread.State.BLOCKED, reader.getState(), "reader reached the admission lock");
            tasks.remove(0).run();
            now.set(150L);
        }
        Image frame = result.get(3, TimeUnit.SECONDS);
        assertNotNull(frame);
        assertEquals(0xFF00FF00, argb(frame, 0, 0), "reader uses the published GIF's original clock");
        assertTrue(tasks.isEmpty(), "the reader must not submit a second decode");
        assertEquals(1, fetches.get());
    }

    @Test
    void thumbnailFitsThePixelBudgetWithoutTrimmingAnimation() {
        try (var surface = Surface.makeRasterN32Premul(384, 384)) {
            var canvas = surface.getCanvas();
            canvas.clear(0xFFFF0000);
            try (Image red = surface.makeImageSnapshot()) {
                canvas.clear(0xFF00FF00);
                try (Image green = surface.makeImageSnapshot()) {
                    var frames = new ArrayList<Image>();
                    for (int i = 0; i < 120; i++) frames.add(i % 2 == 0 ? red : green);
                    int[] durations = new int[frames.size()];
                    Arrays.fill(durations, 100);
                    var full = new AnimatedImage(frames, durations, -1, 3000L);
                    var thumbnail = full.thumbnail(96, 600_000);
                    assertTrue(thumbnail.pixelCount() <= 600_000);
                    assertEquals(frames.size(), thumbnail.frameCount());
                    assertEquals(0xFFFF0000, argb(thumbnail.frameAt(3000), 0, 0));
                    assertEquals(0xFF00FF00, argb(thumbnail.frameAt(3100), 0, 0));
                    assertEquals(0xFF00FF00, argb(thumbnail.frameAt(3000 + 119 * 100), 0, 0));
                    assertEquals(0xFFFF0000, argb(full.frameAt(3000), 0, 0), "thumbnail leaves full frames live");
                }
            }
        }
    }
}
