package com.atom.chat.pack;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** One normalized public chat avatar PNG in the server data directory. */
public final class PublicChatAvatar {
    public static final String FILE_NAME = "public-chat-avatar.png";
    public static final int SIZE = 96;
    private static final int MAX_ENCODED_CHARS = (ServerPack.MAX_ICON_BYTES + 2) / 3 * 4;
    private static final int HEADER_END = 33;
    private static final byte[] PUBLISHED_MARKER = marker();
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private PublicChatAvatar() {}

    public static byte[] decodeUpload(String encoded) {
        if (encoded == null || encoded.isEmpty()) throw new IllegalArgumentException("bad_image");
        if (encoded.length() > MAX_ENCODED_CHARS) throw new IllegalArgumentException("too_large");
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("bad_image");
        }
    }

    public static boolean hasHeader(byte[] png) {
        if (png == null || png.length < HEADER_END || png.length > ServerPack.MAX_ICON_BYTES) return false;
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (png[i] != SIGNATURE[i]) return false;
        }
        var header = ByteBuffer.wrap(png, 8, 16);
        return header.getInt() == 13 && header.getInt() == 0x49484452
                && header.getInt() == SIZE && header.getInt() == SIZE;
    }

    /** Checks the marker written when the server normalizes this PNG. */
    public static boolean isPublished(byte[] png) {
        return hasHeader(png) && png.length >= HEADER_END + PUBLISHED_MARKER.length
                && Arrays.equals(png, HEADER_END, HEADER_END + PUBLISHED_MARKER.length,
                        PUBLISHED_MARKER, 0, PUBLISHED_MARKER.length);
    }

    private static byte[] marker() {
        byte[] body = "tEXtAtomChat\0public-chat-avatar".getBytes(StandardCharsets.US_ASCII);
        var crc = new CRC32();
        crc.update(body);
        return ByteBuffer.allocate(body.length + 8).putInt(body.length - 4)
                .put(body).putInt((int) crc.getValue()).array();
    }

    public static byte[] read(Path file) throws IOException {
        if (Files.notExists(file)) return null;
        try (var stream = Files.newInputStream(file)) {
            byte[] png = stream.readNBytes(ServerPack.MAX_ICON_BYTES + 1);
            return isPublished(png) ? png : null;
        }
    }

    public static String revision(Path file) throws IOException {
        byte[] png = read(file);
        return png == null ? "" : ServerPack.sha256Hex(png);
    }

    public static String save(Path file, String expectedRevision, byte[] png) throws IOException {
        if (!Objects.equals(expectedRevision, revision(file))) throw new IllegalArgumentException("conflict");
        if (png == null) {
            Files.deleteIfExists(file);
            return "";
        }
        byte[] canonical = normalize(png);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "public-chat-avatar-", ".tmp");
        try {
            Files.write(temporary, canonical);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return ServerPack.sha256Hex(canonical);
    }

    private static byte[] normalize(byte[] png) throws IOException {
        if (png.length > ServerPack.MAX_ICON_BYTES) throw new IllegalArgumentException("too_large");
        if (!hasHeader(png)) throw new IllegalArgumentException("bad_image");
        BufferedImage image;
        var readers = ImageIO.getImageReadersByFormatName("PNG");
        if (!readers.hasNext()) throw new IOException("PNG decoder unavailable");
        var reader = readers.next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
            reader.setInput(input, true, true);
            image = reader.read(0);
        } catch (IOException | RuntimeException malformed) {
            throw new IllegalArgumentException("bad_image");
        } finally {
            reader.dispose();
        }
        if (image == null || image.getWidth() != SIZE || image.getHeight() != SIZE)
            throw new IllegalArgumentException("bad_image");
        // The square remains unmasked. Each client clips it using its live theme settings.
        var clean = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        clean.setRGB(0, 0, SIZE, SIZE, image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE), 0, SIZE);
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(clean, "png", output)) throw new IOException("PNG encoder unavailable");
        byte[] cleanPng = output.toByteArray();
        int size = cleanPng.length + PUBLISHED_MARKER.length;
        if (size > ServerPack.MAX_ICON_BYTES) throw new IllegalArgumentException("too_large");
        return ByteBuffer.allocate(size).put(cleanPng, 0, HEADER_END).put(PUBLISHED_MARKER)
                .put(cleanPng, HEADER_END, cleanPng.length - HEADER_END).array();
    }
}
