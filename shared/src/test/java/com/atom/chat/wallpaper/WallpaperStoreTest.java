package com.atom.chat.wallpaper;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WallpaperStoreTest {
    @TempDir Path tmp;

    @AfterEach
    void resetStore() {
        WallpaperStore.init(null);
    }

    @Test
    void clearingAnAbsentWallpaperCommitsWithoutCreatingItsDirectory() {
        Path dir = tmp.resolve("wallpaper");
        WallpaperStore.init(dir);
        var committed = new AtomicBoolean();
        assertTrue(WallpaperStore.replaceWithConfig(null, () -> {
            committed.set(true);
            return true;
        }));
        assertTrue(committed.get());
        assertNull(WallpaperStore.current());
        assertFalse(Files.exists(dir));
    }

    @Test
    void unsuccessfulConfigCommitRestoresTheImageAndItsGeneratedMarker() throws IOException {
        Path dir = tmp.resolve("wallpaper");
        WallpaperStore.init(dir);
        assertTrue(WallpaperStore.set(image("original.png", 1)));
        Path active = WallpaperStore.current();
        byte[] original = Files.readAllBytes(active);
        Path marker = dir.resolve("selected-preset.txt");
        Files.writeString(marker, "silk");
        WallpaperStore.init(dir);

        assertFalse(WallpaperStore.replaceWithConfig(null, () -> false));
        assertFalse(WallpaperStore.replaceWithConfig(image("replacement.jpg", 2), () -> {
            throw new IllegalStateException("config write failed");
        }));
        assertFalse(WallpaperStore.replacePngWithConfig(Files.readAllBytes(image("replacement.png", 3)), () -> false));

        assertEquals(active, WallpaperStore.current());
        assertEquals("silk", WallpaperStore.presetId());
        assertEquals("silk", Files.readString(marker));
        assertArrayEquals(original, Files.readAllBytes(active));
        assertFalse(Files.exists(dir.resolve("wallpaper.jpg")));
        WallpaperStore.init(dir);
        assertEquals("silk", WallpaperStore.presetId());
        assertArrayEquals(original, Files.readAllBytes(WallpaperStore.current()));
    }

    @Test
    void blockedWallpaperWriteDoesNotCallTheConfigCommit() throws IOException {
        Path dir = tmp.resolve("wallpaper");
        WallpaperStore.init(dir);
        assertTrue(WallpaperStore.set(image("original.png", 1)));
        Path active = WallpaperStore.current();
        byte[] original = Files.readAllBytes(active);
        Files.createDirectory(dir.resolve("wallpaper.jpg"));

        assertFalse(WallpaperStore.replaceWithConfig(image("replacement.jpg", 2),
                () -> fail("configuration commit must follow the wallpaper write")));

        assertEquals(active, WallpaperStore.current());
        assertArrayEquals(original, Files.readAllBytes(active));
    }

    @Test
    void customSetterPreservesExistingWallpaperWhenItsMarkerCannotBeRemoved() throws IOException {
        Path dir = tmp.resolve("wallpaper");
        WallpaperStore.init(dir);
        assertTrue(WallpaperStore.set(image("original.png", 1)));
        Path active = WallpaperStore.current();
        byte[] original = Files.readAllBytes(active);
        Path marker = Files.createDirectory(dir.resolve("selected-preset.txt"));
        Files.writeString(marker.resolve("retain"), "block the removal");

        assertFalse(WallpaperStore.set(image("replacement.jpg", 2)));
        assertEquals(active, WallpaperStore.current());
        assertArrayEquals(original, Files.readAllBytes(active));
        assertEquals("block the removal", Files.readString(marker.resolve("retain")));
    }

    private Path image(String name, int width) throws IOException {
        Path file = tmp.resolve(name);
        var image = new BufferedImage(width, 1, BufferedImage.TYPE_INT_RGB);
        assertTrue(ImageIO.write(image, name.endsWith(".jpg") ? "jpeg" : "png", file.toFile()));
        return file;
    }
}
