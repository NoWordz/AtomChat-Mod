package com.atom.chat.api.card;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Pure-Java compact codec for {@link ShareCard} minimap terrain previews stored in
 * {@code customData.get(DATA_KEY)}.
 *
 * <p>Wire format (Base64 without padding):
 * {@code [uint8 width][uint8 height][zlib-compressed RGB565 big-endian pixels]}.
 * A typical {@code 64×64} real terrain preview compresses to ~1.5–3.5 KB.
 */
public final class MinimapPreviewCodec {
    public static final String DATA_KEY = "mm";
    public static final int DEFAULT_WIDTH = 64;
    public static final int DEFAULT_HEIGHT = 64;
    public static final int MIN_DIM = 16;
    public static final int MAX_WIDTH = 96;
    public static final int MAX_HEIGHT = 72;
    public static final int MAX_COMPRESSED_BYTES = 7_500;
    public static final int MAX_ENCODED_CHARS = 10_200;

    private MinimapPreviewCodec() {
    }

    public record Decoded(int width, int height, byte[] rgbaPixels) {
    }

    /**
     * Encodes an {@code 0xAARRGGBB} pixel array into a compact Base64 string,
     * or returns {@code null} if inputs are invalid or exceed wire limits.
     */
    public static String encode(int width, int height, int[] argbPixels) {
        if (argbPixels == null || width < MIN_DIM || height < MIN_DIM
                || width > MAX_WIDTH || height > MAX_HEIGHT
                || argbPixels.length != width * height) {
            return null;
        }
        byte[] raw565 = new byte[width * height * 2];
        for (int i = 0; i < argbPixels.length; i++) {
            int argb = argbPixels[i];
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            int rgb565 = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >>> 3);
            raw565[i * 2] = (byte) ((rgb565 >>> 8) & 0xFF);
            raw565[i * 2 + 1] = (byte) (rgb565 & 0xFF);
        }

        byte[] compressed = deflate(raw565);
        if (compressed == null || compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) {
            return null;
        }
        byte[] packet = new byte[2 + compressed.length];
        packet[0] = (byte) width;
        packet[1] = (byte) height;
        System.arraycopy(compressed, 0, packet, 2, compressed.length);

        String encoded = Base64.getEncoder().withoutPadding().encodeToString(packet);
        return encoded.length() <= MAX_ENCODED_CHARS ? encoded : null;
    }

    /**
     * Validates a Base64 minimap payload (DoS-safe: bounds dimensions, compressed size
     * and decompressed exact byte count). Returns {@code encoded} if valid, else {@code null}.
     */
    public static String validateEncoded(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_CHARS) {
            return null;
        }
        return decode(encoded) != null ? encoded : null;
    }

    /**
     * Decodes a Base64 minimap payload into {@code RGBA_8888} premultiplied bytes ready for Skija.
     */
    public static Decoded decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_CHARS) {
            return null;
        }
        final byte[] packet;
        try {
            packet = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (packet.length < 4 || packet.length - 2 > MAX_COMPRESSED_BYTES) {
            return null;
        }
        int width = packet[0] & 0xFF;
        int height = packet[1] & 0xFF;
        if (width < MIN_DIM || height < MIN_DIM || width > MAX_WIDTH || height > MAX_HEIGHT) {
            return null;
        }
        int expectedRaw = width * height * 2;
        byte[] raw565 = inflateExact(packet, 2, packet.length - 2, expectedRaw);
        if (raw565 == null) {
            return null;
        }

        byte[] rgba = new byte[width * height * 4];
        for (int i = 0; i < width * height; i++) {
            int hi = raw565[i * 2] & 0xFF;
            int lo = raw565[i * 2 + 1] & 0xFF;
            int rgb565 = (hi << 8) | lo;
            int r5 = (rgb565 >>> 11) & 0x1F;
            int g6 = (rgb565 >>> 5) & 0x3F;
            int b5 = rgb565 & 0x1F;
            int r = (r5 << 3) | (r5 >>> 2);
            int g = (g6 << 2) | (g6 >>> 4);
            int b = (b5 << 3) | (b5 >>> 2);
            int out = i * 4;
            rgba[out] = (byte) r;
            rgba[out + 1] = (byte) g;
            rgba[out + 2] = (byte) b;
            rgba[out + 3] = (byte) 0xFF;
        }
        return new Decoded(width, height, rgba);
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(raw.length, 2048));
            byte[] buf = new byte[1024];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                if (n <= 0) break;
                out.write(buf, 0, n);
                if (out.size() > MAX_COMPRESSED_BYTES) {
                    return null;
                }
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflateExact(byte[] src, int off, int len, int expectedBytes) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(src, off, len);
            byte[] out = new byte[expectedBytes];
            int total = 0;
            while (total < expectedBytes && !inflater.finished()) {
                int n = inflater.inflate(out, total, expectedBytes - total);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        return null;
                    }
                    break;
                }
                total += n;
            }
            if (total != expectedBytes) {
                return null;
            }
            // Reject trailing excess data (zip-bomb guard)
            byte[] probe = new byte[1];
            if (!inflater.finished() && inflater.inflate(probe) > 0) {
                return null;
            }
            return out;
        } catch (DataFormatException ignored) {
            return null;
        } finally {
            inflater.end();
        }
    }
}
