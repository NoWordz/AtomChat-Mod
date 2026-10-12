package com.atom.chat.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Pure dimension parsing: neither AWT nor Skija nor pixel decompression is required. */
class ImageBoundsTest {
    @TempDir Path tmp;

    @Test void readsAllHostedFormatsAndWebpVariants() {
        byte[][] headers = {
                ImageBoundsFixtures.png(321, 123), ImageBoundsFixtures.jpeg(321, 123),
                ImageBoundsFixtures.gif(321, 123), ImageBoundsFixtures.bmp(321, 123),
                ImageBoundsFixtures.webpExtended(321, 123),
                ImageBoundsFixtures.webpLossless(321, 123), ImageBoundsFixtures.webpLossy(321, 123)
        };
        for (byte[] header : headers) assertArrayEquals(new int[]{321, 123}, ImageBounds.read(header));
    }
    @Test void rejectsEveryTruncatedDimensionHeader() {
        byte[][] headers = {
                ImageBoundsFixtures.png(321, 123), ImageBoundsFixtures.jpeg(321, 123),
                ImageBoundsFixtures.gif(321, 123), ImageBoundsFixtures.bmp(321, 123),
                ImageBoundsFixtures.webpExtended(321, 123),
                ImageBoundsFixtures.webpLossless(321, 123), ImageBoundsFixtures.webpLossy(321, 123)
        };
        // JPEG dimensions are available when its complete SOF segment ends at byte 21.
        // RIFF dimension chunks include their declared payload and odd-length padding.
        int[] minimum = {33, 21, 13, 54, 30, 26, 30};
        for (int n = 0; n < headers.length; n++) {
            for (int size = 0; size < minimum[n]; size++) {
                assertNull(ImageBounds.read(Arrays.copyOf(headers[n], size)),
                        "format " + n + " accepted truncated size " + size);
            }
        }
    }
    @Test void rejectsUnsupportedMagicAndInvalidDimensions() {
        assertNull(ImageBounds.read(null));
        assertNull(ImageBounds.read(new byte[64]));
        assertNull(ImageBounds.read(ImageBoundsFixtures.png(0, 1)));
        assertNull(ImageBounds.read(ImageBoundsFixtures.gif(1, 0)));
        assertNull(ImageBounds.read(ImageBoundsFixtures.bmp(-1, 1)));
        assertNull(ImageBounds.read(ImageBoundsFixtures.bmp(1, Integer.MIN_VALUE)));
        byte[] broken = ImageBoundsFixtures.png(10, 10);
        broken[7] = 0;
        assertNull(ImageBounds.read(broken), "full PNG signature is required");
    }
    @Test void handlesTopDownBmpAndUnsignedGifDimensions() {
        assertArrayEquals(new int[]{10, 20}, ImageBounds.read(ImageBoundsFixtures.bmp(10, -20)));
        assertArrayEquals(new int[]{65535, 2}, ImageBounds.read(ImageBoundsFixtures.gif(65535, 2)));
    }
    @Test void malformedChunkAndJpegSegmentLengthsCannotEscapeBoundsChecks() {
        byte[] webp = ImageBoundsFixtures.webpExtended(10, 10);
        Arrays.fill(webp, 16, 20, (byte) 0xff);
        assertNull(ImageBounds.read(webp));
        byte[] jpeg = ImageBoundsFixtures.jpeg(10, 10);
        jpeg[4] = 0; jpeg[5] = 1;
        assertNull(ImageBounds.read(jpeg));
    }

    @Test void pathInspectionSkipsLargeJpegAndRiffChunksAcrossItsHeaderCache() throws Exception {
        Path jpeg = tmp.resolve("metadata.jpg");
        Files.write(jpeg, ImageBoundsFixtures.jpegWithLargeAppSegment(321, 123, 5000));
        ImageBounds.Inspection jpegResult = ImageBounds.inspect(jpeg);
        assertNull(jpegResult.rejection());
        assertArrayEquals(new int[]{321, 123}, jpegResult.dimensions());

        Path webp = tmp.resolve("metadata.webp");
        Files.write(webp, ImageBoundsFixtures.webpWithLargeUnknownChunk(321, 123, 5001));
        ImageBounds.Inspection webpResult = ImageBounds.inspect(webp);
        assertNull(webpResult.rejection());
        assertArrayEquals(new int[]{321, 123}, webpResult.dimensions());
    }
}
