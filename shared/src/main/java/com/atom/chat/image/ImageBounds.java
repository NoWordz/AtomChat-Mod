package com.atom.chat.image;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/** Bounded, pure image dimension inspection; never decompresses or rewrites pixels. */
public final class ImageBounds {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final long MAX_PIXELS = 32_000_000L;

    private ImageBounds() {}

    /** The local upload gate reads only small cached header ranges, never the entire file. */
    public record Inspection(int[] dimensions, String rejection) {}

    public static Inspection inspect(Path file) throws IOException {
        if (file == null) throw new IOException("missing image");
        if (Files.size(file) > MAX_BYTES) return new Inspection(null, "too_large");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long length = channel.size();
            if (length > MAX_BYTES) return new Inspection(null, "too_large");
            int[] dimensions = read(new FileData(channel, (int) length));
            return new Inspection(dimensions, rejection(dimensions));
        }
    }

    /** null on acceptance; stable rejection codes shared with the server. */
    public static String rejection(byte[] bytes) {
        if (bytes != null && bytes.length > MAX_BYTES) return "too_large";
        int[] size = read(bytes);
        return rejection(size);
    }

    private static String rejection(int[] size) {
        if (size == null) return "unsupported";
        return (long) size[0] * size[1] > MAX_PIXELS ? "too_many_pixels" : null;
    }

    /** Complete dimension headers only: this is not a pixel decoder. */
    public static int[] read(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) return null;
        try {
            return read(new Data() {
                public int length() { return bytes.length; }
                public int byteAt(int p) { return bytes[p] & 255; }
            });
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int[] read(Data b) throws IOException {
        if (b.length() >= 33 && matches(b, 0, new int[]{137,80,78,71,13,10,26,10})) {
            if (u32(b, 8, false) != 13 || !tag(b, 12, "IHDR")) return null;
            CRC32 crc = new CRC32();
            for (int p = 12; p < 29; p++) crc.update(u8(b, p));
            if (crc.getValue() != u32(b, 29, false)) return null;
            return dimensions(u32(b, 16, false), u32(b, 20, false));
        }
        if (b.length() >= 13 && (tag(b, 0, "GIF87a") || tag(b, 0, "GIF89a"))) {
            int tableBytes = (u8(b, 10) & 128) == 0 ? 0 : 3 * (1 << ((u8(b, 10) & 7) + 1));
            if (b.length() < 13 + tableBytes) return null;
            return dimensions(u16(b, 6, true), u16(b, 8, true));
        }
        if (b.length() >= 26 && tag(b, 0, "BM")) {
            long dib = u32(b, 14, true);
            if (dib == 12) return dimensions(u16(b, 18, true), u16(b, 20, true));
            if (dib < 40 || dib > b.length() - 14) return null;
            int width = (int) u32(b, 18, true), height = (int) u32(b, 22, true);
            if (width <= 0 || height == Integer.MIN_VALUE) return null;
            return dimensions(width, Math.abs(height));
        }
        if (b.length() >= 2 && u8(b, 0) == 255 && u8(b, 1) == 216) return jpeg(b);
        if (b.length() >= 12 && tag(b, 0, "RIFF") && tag(b, 8, "WEBP")) return webp(b);
        return null;
    }

    private static int[] jpeg(Data b) throws IOException {
        int p = 2;
        while (p < b.length()) {
            if (u8(b, p++) != 255) return null;
            while (p < b.length() && u8(b, p) == 255) p++;
            if (p >= b.length()) return null;
            int marker = u8(b, p++);
            if (marker == 0 || marker == 216 || marker == 217 || marker == 218) return null;
            if (marker == 1 || marker >= 208 && marker <= 215) continue;
            if (p > b.length() - 2) return null;
            int len = u16(b, p, false);
            if (len < 2 || len > b.length() - p) return null;
            boolean sof = marker >= 192 && marker <= 207 && marker != 196 && marker != 200 && marker != 204;
            if (sof) {
                if (len < 8) return null;
                int components = u8(b, p + 7);
                if (components == 0 || len != 8 + 3 * components) return null;
                return dimensions(u16(b, p + 5, false), u16(b, p + 3, false));
            }
            p += len;
        }
        return null;
    }

    private static int[] webp(Data b) throws IOException {
        long end = u32(b, 4, true) + 8;
        if (end > b.length() || end < 20) return null;
        int p = 12;
        while (p <= end - 8) {
            long len = u32(b, p + 4, true);
            long next = p + 8L + len + (len & 1);
            if (next > end) return null;
            int data = p + 8;
            if (tag(b, p, "VP8X")) {
                if (len != 10) return null;
                return dimensions(u24(b, data + 4) + 1L, u24(b, data + 7) + 1L);
            }
            if (tag(b, p, "VP8L")) {
                if (len < 5 || u8(b, data) != 47) return null;
                long bits = u32(b, data + 1, true);
                if ((bits >>> 29) != 0) return null;
                return dimensions((bits & 16383) + 1, ((bits >>> 14) & 16383) + 1);
            }
            if (tag(b, p, "VP8 ")) {
                if (len < 10 || (u8(b, data) & 1) != 0
                        || !matches(b, data + 3, new int[]{157,1,42})) return null;
                return dimensions(u16(b, data + 6, true) & 16383, u16(b, data + 8, true) & 16383);
            }
            p = (int) next;
        }
        return null;
    }

    private static int[] dimensions(long width, long height) {
        return width > 0 && height > 0 && width <= Integer.MAX_VALUE && height <= Integer.MAX_VALUE
                ? new int[]{(int) width, (int) height} : null;
    }
    private static int u8(Data b, int p) throws IOException { return b.byteAt(p); }
    private static int u16(Data b, int p, boolean le) throws IOException {
        return le ? u8(b,p) | u8(b,p+1) << 8 : u8(b,p) << 8 | u8(b,p+1);
    }
    private static int u24(Data b, int p) throws IOException { return u16(b,p,true) | u8(b,p+2) << 16; }
    private static long u32(Data b, int p, boolean le) throws IOException {
        return le ? (long) u16(b,p,true) | (long) u16(b,p+2,true) << 16
                : (long) u16(b,p,false) << 16 | u16(b,p+2,false);
    }
    private static boolean matches(Data b, int p, int[] signature) throws IOException {
        if (p < 0 || p > b.length() - signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (u8(b,p+i) != signature[i]) return false;
        return true;
    }
    private static boolean tag(Data b, int p, String tag) throws IOException {
        if (p < 0 || p > b.length() - tag.length()) return false;
        for (int i = 0; i < tag.length(); i++) if (u8(b,p+i) != tag.charAt(i)) return false;
        return true;
    }
    private interface Data {
        int length();
        int byteAt(int offset) throws IOException;
    }

    /** A fixed 4 KiB cache permits bounded skipping over large JPEG/RIFF metadata. */
    private static final class FileData implements Data {
        private final FileChannel channel;
        private final int length;
        private final byte[] cache = new byte[4096];
        private int start = -1;
        private int count;

        FileData(FileChannel channel, int length) {
            this.channel = channel;
            this.length = length;
        }

        public int length() { return length; }

        public int byteAt(int offset) throws IOException {
            if (offset < 0 || offset >= length) throw new IOException("truncated image header");
            if (start < 0 || offset < start || offset >= start + count) {
                start = offset - offset % cache.length;
                count = Math.min(cache.length, length - start);
                ByteBuffer buffer = ByteBuffer.wrap(cache, 0, count);
                while (buffer.hasRemaining()) {
                    int read = channel.read(buffer, (long) start + buffer.position());
                    if (read < 0) throw new IOException("truncated image header");
                }
            }
            return cache[offset - start] & 255;
        }
    }
}
