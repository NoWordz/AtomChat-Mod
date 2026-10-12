package com.atom.chat.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Complete dimension headers, not pixel-decode fixtures: no large bitmap allocation. */
public final class ImageBoundsFixtures {
    private ImageBoundsFixtures() {}
    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
    public static byte[] png(int width, int height) {
        ByteBuffer b = ByteBuffer.allocate(33);
        b.put(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        b.putInt(13).putInt(0x49484452).putInt(width).putInt(height);
        b.put(new byte[]{8, 2, 0, 0, 0});
        CRC32 crc = new CRC32();
        crc.update(b.array(), 12, 17);
        b.putInt((int) crc.getValue());
        return b.array();
    }
    public static byte[] gif(int width, int height) {
        ByteBuffer b = le(13);
        b.put("GIF89a".getBytes(StandardCharsets.US_ASCII));
        b.putShort((short) width).putShort((short) height).put(new byte[3]);
        return b.array();
    }
    public static byte[] bmp(int width, int height) {
        ByteBuffer b = le(54);
        b.put((byte) 'B').put((byte) 'M').putInt(54).putInt(0).putInt(54);
        b.putInt(40).putInt(width).putInt(height).putShort((short) 1).putShort((short) 24);
        return b.array();
    }
    public static byte[] jpeg(int width, int height) {
        ByteBuffer b = ByteBuffer.allocate(23);
        b.putShort((short) 0xffd8).putShort((short) 0xffc0).putShort((short) 17);
        b.put((byte) 8).putShort((short) height).putShort((short) width).put((byte) 3);
        b.put(new byte[]{1, 0x11, 0, 2, 0x11, 0, 3, 0x11, 0});
        b.putShort((short) 0xffd9);
        return b.array();
    }
    public static byte[] jpegWithLargeAppSegment(int width, int height, int payloadBytes) {
        byte[] core = jpeg(width, height);
        ByteBuffer b = ByteBuffer.allocate(2 + 4 + payloadBytes + core.length - 2);
        b.put(core, 0, 2).putShort((short) 0xffe1).putShort((short) (payloadBytes + 2));
        b.put(new byte[payloadBytes]).put(core, 2, core.length - 2);
        return b.array();
    }
    private static ByteBuffer webp(String kind, int payloadSize) {
        int padded = (payloadSize + 1) & ~1;
        ByteBuffer b = le(20 + padded);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(12 + padded);
        b.put("WEBP".getBytes(StandardCharsets.US_ASCII));
        b.put(kind.getBytes(StandardCharsets.US_ASCII)).putInt(payloadSize);
        return b;
    }
    private static void u24(ByteBuffer b, int value) {
        b.put((byte) value).put((byte) (value >> 8)).put((byte) (value >> 16));
    }
    public static byte[] webpExtended(int width, int height) {
        ByteBuffer b = webp("VP8X", 10);
        b.putInt(0);
        u24(b, width - 1);
        u24(b, height - 1);
        return b.array();
    }
    public static byte[] webpLossless(int width, int height) {
        ByteBuffer b = webp("VP8L", 5);
        b.put((byte) 0x2f).putInt((width - 1) | ((height - 1) << 14));
        return b.array();
    }
    public static byte[] webpLossy(int width, int height) {
        ByteBuffer b = webp("VP8 ", 10);
        b.put(new byte[]{0, 0, 0, (byte) 0x9d, 1, 0x2a});
        b.putShort((short) width).putShort((short) height);
        return b.array();
    }
    public static byte[] webpWithLargeUnknownChunk(int width, int height, int payloadBytes) {
        byte[] frame = webpExtended(width, height);
        int padded = (payloadBytes + 1) & ~1;
        int size = 12 + 8 + padded + frame.length - 12;
        ByteBuffer b = le(size);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(size - 8);
        b.put("WEBP".getBytes(StandardCharsets.US_ASCII));
        b.put("JUNK".getBytes(StandardCharsets.US_ASCII)).putInt(payloadBytes);
        b.put(new byte[payloadBytes]);
        if ((payloadBytes & 1) != 0) b.put((byte) 0);
        b.put(frame, 12, frame.length - 12);
        return b.array();
    }
}
