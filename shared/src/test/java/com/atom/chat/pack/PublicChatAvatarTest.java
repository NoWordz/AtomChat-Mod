package com.atom.chat.pack;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class PublicChatAvatarTest {
    @TempDir Path directory;

    @Test
    void onlyValidSquarePngIsPublishedAndStaleWritesCannotOverwriteIt() throws Exception {
        Path file = directory.resolve("public-chat-avatar.png");
        byte[] image = png(96);
        assertEquals("", PublicChatAvatar.revision(file));
        PublicChatAvatar.save(file, "", image);
        byte[] published = PublicChatAvatar.read(file);
        assertFalse(PublicChatAvatar.isPublished(image));
        assertTrue(PublicChatAvatar.isPublished(published));
        assertEquals(0, ImageIO.read(new java.io.ByteArrayInputStream(published)).getRGB(20, 20));
        assertThrows(IllegalArgumentException.class, () -> PublicChatAvatar.save(file, "", null));
        String current = PublicChatAvatar.revision(file);
        assertThrows(IllegalArgumentException.class, () -> PublicChatAvatar.save(file, current, png(64)));
        assertThrows(IllegalArgumentException.class, () -> PublicChatAvatar.save(file, current, new byte[80]));
        assertArrayEquals(published, PublicChatAvatar.read(file));
        PublicChatAvatar.save(file, current, null);
        assertNull(PublicChatAvatar.read(file));
    }

    private static byte[] png(int size) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }
}
