package com.atom.chat.net;

import com.atom.chat.image.ImageBounds;
import com.atom.chat.image.ImageBoundsFixtures;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** The shared gate invoked before the server hashes, stores or acknowledges media. */
class MediaImageBoundsTest {
    @Test void acceptsByteAndPixelLimitsInclusivelyWithoutChangingOriginalBytes() {
        byte[] content = Arrays.copyOf(ImageBoundsFixtures.png(8000, 4000), 16 * 1024 * 1024);
        byte[] before = content.clone();
        assertNull(ImageBounds.rejection(content));
        assertArrayEquals(before, content, "validation must preserve original upload bytes");
    }
    @Test void rejectsOneByteOverTheLimitEvenWithReadableDimensions() {
        byte[] content = Arrays.copyOf(ImageBoundsFixtures.png(1, 1), 16 * 1024 * 1024 + 1);
        assertEquals("too_large", ImageBounds.rejection(content));
    }
    @Test void rejectsPixelOverflowInEveryHostedFormat() {
        byte[][] content = {
                ImageBoundsFixtures.png(8001, 4000), ImageBoundsFixtures.jpeg(8001, 4000),
                ImageBoundsFixtures.gif(8001, 4000), ImageBoundsFixtures.bmp(8001, 4000),
                ImageBoundsFixtures.webpExtended(8001, 4000),
                ImageBoundsFixtures.webpLossless(8001, 4000), ImageBoundsFixtures.webpLossy(8001, 4000)
        };
        for (byte[] bytes : content) assertEquals("too_many_pixels", ImageBounds.rejection(bytes));
        assertEquals("too_many_pixels", ImageBounds.rejection(ImageBoundsFixtures.png(32000001, 1)));
        assertEquals("too_many_pixels", ImageBounds.rejection(ImageBoundsFixtures.png(65536, 65536)),
                "pixel multiplication must not overflow a signed int");
    }
    @Test void magicAloneCannotAuthorizeServerStorageAndGifRemainsUnchanged() {
        assertEquals("unsupported", ImageBounds.rejection(new byte[64]));
        byte[] truncated = Arrays.copyOf(ImageBoundsFixtures.png(1, 1), 24);
        assertEquals("png", MediaIds.extensionOf(truncated));
        assertEquals("unsupported", ImageBounds.rejection(truncated));
        byte[] gif = ImageBoundsFixtures.gif(10, 20);
        byte[] before = gif.clone();
        assertNull(ImageBounds.rejection(gif));
        assertArrayEquals(before, gif);
    }
}
