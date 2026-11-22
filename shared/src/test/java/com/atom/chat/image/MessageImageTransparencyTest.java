package com.atom.chat.image;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class MessageImageTransparencyTest {
    @Test void decodedPixelsDetermineOpacityEvenWithMismatchedFileSuffixes(@TempDir Path disk) throws Exception {
        String transparent = "http://test/png-with-alpha.jpg";
        String opaque = "http://test/opaque-rgba.png";
        String jpeg = "http://test/jpeg.png";
        byte[] alphaPng = encode("png", true);
        byte[] opaquePng = encode("png", false);
        byte[] opaqueJpeg = encode("jpg", false);
        ImageLoader loader = ImageLoader.forTest(url -> switch (url) {
            case "http://test/png-with-alpha.jpg" -> alphaPng;
            case "http://test/opaque-rgba.png" -> opaquePng;
            default -> opaqueJpeg;
        }, () -> 0L, Runnable::run);
        loader.init(disk);
        assertTrue(loader.getMessageImage(transparent, true).transparent());
        assertFalse(loader.getMessageImage(opaque, true).transparent());
        assertFalse(loader.getMessageImage(jpeg, true).transparent());
        assertSame(loader.getMessageImage(transparent, false), loader.getHudMessageImage(transparent, false));
        assertSame(loader.getMessageImage(transparent, false).image(), loader.get(transparent));

        var reload = ImageLoader.forTest(url -> fail("the bytes should already be on disk"), () -> 0L, Runnable::run);
        reload.init(disk);
        assertTrue(reload.getMessageImage(transparent, true).transparent());
        assertFalse(reload.getMessageImage(opaque, true).transparent());
        assertFalse(reload.getMessageImage(jpeg, true).transparent());
    }

    @Test void animationReportsOneDecisionAcrossTransparentAndOpaqueFrames(@TempDir Path disk) {
        AtomicLong clock = new AtomicLong();
        String url = "http://test/image.png";
        var loader = ImageLoader.forTest(ignored -> ChatHudGifFixture.MIXED_ALPHA_GIF, clock::get, Runnable::run);
        loader.init(disk);
        loader.rememberLocallySent(url);
        var start = loader.getHudMessageImage(url, true);
        assertTrue(start.transparent());
        assertEquals(255, alphaAtCorner(start.image()));
        clock.set(150L);
        var next = loader.getHudMessageImage(url, true);
        assertTrue(next.transparent());
        assertEquals(0, alphaAtCorner(next.image()));
        assertNotSame(start, next);
        assertSame(next, loader.getHudMessageImage(url, true));
        assertTrue(loader.getMessageImage(url, true).transparent());
    }

    @Test void downsamplingCanRemoveAlphaAndStillStoresOnlyTheDisplayBitmap(@TempDir Path disk) throws Exception {
        var image = new BufferedImage(4096, 64, BufferedImage.TYPE_INT_ARGB);
        int[] line = new int[image.getWidth()];
        java.util.Arrays.fill(line, 0xFF1988BD);
        for (int y = 0; y < image.getHeight(); y++) image.setRGB(0, y, line.length, 1, line, 0, line.length);
        image.setRGB(0, 0, 0);
        byte[] source = encode("png", image);
        String url = "http://test/subpixel.png";
        var loader = ImageLoader.forTest(ignored -> source, () -> 0L, Runnable::run);
        loader.init(disk);
        MessageImage result = loader.getMessageImage(url, true);
        assertEquals(ImageLoader.MAX_DIM, result.image().getWidth());
        assertEquals(255, alphaAtCorner(result.image()));
        assertFalse(result.transparent());
        byte[] diskBytes = Files.readAllBytes(disk.resolve(ImageLoader.sha256Hex(url) + ".bin"));
        try (Image copy = Image.makeFromEncoded(diskBytes)) {
            assertEquals(ImageLoader.MAX_DIM, copy.getWidth());
        }
        var reload = ImageLoader.forTest(ignored -> fail("display-sized disk copy should be reused"), () -> 0L, Runnable::run);
        reload.init(disk);
        assertFalse(reload.getMessageImage(url, true).transparent());
    }

    private static byte[] encode(String format, boolean transparent) throws Exception {
        boolean png = format.equals("png");
        var image = new BufferedImage(1500, 75, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        int[] line = new int[image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            java.util.Arrays.fill(line, transparent && y < 20 ? 0 : 0xFF1988BD);
            image.setRGB(0, y, line.length, 1, line, 0, line.length);
        }
        return encode(format, image);
    }

    private static byte[] encode(String format, BufferedImage image) throws Exception {
        var encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, encoded));
        return encoded.toByteArray();
    }

    private static int alphaAtCorner(Image image) {
        try (var pixel = new Bitmap()) {
            assertTrue(pixel.allocN32Pixels(1, 1));
            assertTrue(image.readPixels(pixel, 0, 0));
            return pixel.getColor(0, 0) >>> 24;
        }
    }
}
