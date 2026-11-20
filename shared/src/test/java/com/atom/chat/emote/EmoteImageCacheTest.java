package com.atom.chat.emote;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EmoteImageCacheTest {

    @TempDir
    Path tmp;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = EmoteImageCacheTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "missing test resource " + name);
            return in.readAllBytes();
        }
    }

    private static byte[] png(int w, int h) {
        try (Surface surface = Surface.makeRasterN32Premul(w, h)) {
            return surface.makeImageSnapshot().encodeToData().getBytes();
        }
    }

    private File file(String name, byte[] bytes) throws IOException {
        Path path = tmp.resolve(name);
        Files.write(path, bytes);
        return path.toFile();
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
    void staticImageIsCached() throws IOException {
        EmoteImageCache cache = new EmoteImageCache();
        File file = file("dot.png", png(4, 4));

        Image first = cache.image(file);
        assertNotNull(first);
        assertSame(first, cache.image(file), "a cached emote must be reused");
    }

    @Test
    void previewRechecksADirectoryThatBecameALinkAfterScanning() throws Exception {
        Path listed = Files.createDirectory(tmp.resolve("listed"));
        Path outside = Files.createDirectory(tmp.resolve("outside"));
        File original = Files.write(listed.resolve("preview.png"), png(4, 4)).toFile();
        Path other = Files.write(outside.resolve("preview.png"), png(4, 4));
        Path retired = Files.move(listed, tmp.resolve("retired"));
        try {
            Files.createSymbolicLink(listed, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            assumeTrue(System.getProperty("os.name").startsWith("Windows"), "the OS does not permit symlinks");
            Process junction = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J",
                    listed.toString(), outside.toString()).redirectErrorStream(true).start();
            assertTrue(junction.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, junction.exitValue(), new String(junction.getInputStream().readAllBytes()));
        }
        EmoteImageCache cache = new EmoteImageCache();
        try {
            assertTrue(Files.isSameFile(original.toPath(), other));
            assertNull(cache.image(original));
        } finally {
            Files.deleteIfExists(listed);
        }
        Files.move(retired, listed);
        try {
            assertNotNull(cache.image(original), "restoring the real directory must make previews work again");
        } finally {
            cache.clear();
        }
    }

    @Test
    void animatedGifShowsTheFirstFrameOnly() throws IOException {
        EmoteImageCache cache = new EmoteImageCache();
        File file = file("anim.gif", resource("/atomchat/animated_3frames.gif"));

        Image frame = cache.image(file);
        assertNotNull(frame);
        assertEquals(0xFFFF0000, argb(frame, 0, 0), "the grid must show the first frame, not animate");
    }

    @Test
    void oversizedEmoteIsDownscaledToTheCellBudget() throws IOException {
        EmoteImageCache cache = new EmoteImageCache();
        File file = file("big.gif", resource("/atomchat/large_anim.gif"));

        Image frame = cache.image(file);
        assertNotNull(frame);
        assertTrue(frame.getWidth() <= EmoteImageCache.MAX_DIM, "width was " + frame.getWidth());
        assertTrue(frame.getHeight() <= EmoteImageCache.MAX_DIM, "height was " + frame.getHeight());
        assertEquals(0xFFFF0000, argb(frame, 0, 0));
    }

    @Test
    void failedDecodeIsRememberedAndInvalidateRetries() throws IOException {
        EmoteImageCache cache = new EmoteImageCache();
        File file = file("bad.png", new byte[]{1, 2, 3});

        assertNull(cache.image(file));
        assertNull(cache.image(file), "a failed file must not be re-decoded every frame");
        cache.invalidate(file);
        assertNull(cache.image(file));
    }
}
