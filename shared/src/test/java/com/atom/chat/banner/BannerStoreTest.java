package com.atom.chat.banner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BannerStore}'s clear contract: it deletes the banner file, and it
 * answers "nothing removed" instead of throwing when the store was never
 * initialised.
 *
 * <p>The uninitialised case is the one that regressed once: {@code clear()} used
 * to walk straight into {@code Files.isDirectory(null)} and throw, while its
 * sibling {@link com.atom.chat.wallpaper.WallpaperStore#clear()} carried the
 * guard. The first test is that guard's nail; the next two pin the behaviour the
 * guard must not change on the way in.</p>
 *
 * <p>Pure {@code java.nio}, no Minecraft types: the store has none either, which
 * is why this file lives in {@code shared/}.</p>
 */
class BannerStoreTest {
    @TempDir
    Path tmp;

    @BeforeEach
    @AfterEach
    void leaveTheStoreUninitialised() {
        // Statics: hand them back exactly as this test found them, so the class
        // can run alone or in any order.
        BannerStore.init(null);
    }

    @Test
    void clearOnAnUninitialisedStoreIsRejectedWithoutThrowing() {
        assertFalse(BannerStore.clear(),
                "an uninitialised store has nothing to clear and must say so, not throw");
        assertFalse(BannerStore.isSet());
    }

    @Test
    void clearDeletesTheBannerAndFallsBackToTheGradient() throws Exception {
        Path dir = tmp.resolve("atomchat");
        Files.createDirectories(dir);
        Path banner = dir.resolve("banner.png");
        Files.write(banner, "png-bytes".getBytes(StandardCharsets.UTF_8));
        BannerStore.init(dir);
        assertTrue(BannerStore.isSet(), "a banner.png in the directory is the active banner");

        assertTrue(BannerStore.clear(), "clearing reports the file it removed");
        assertFalse(Files.exists(banner), "the banner file is gone");
        assertFalse(BannerStore.isSet(), "and the profile is back on its accent gradient");
        assertFalse(BannerStore.clear(), "clearing twice removes nothing the second time");
    }

    @Test
    void clearAlsoRemovesTheOtherSupportedExtensions() throws Exception {
        Path dir = tmp.resolve("atomchat");
        Files.createDirectories(dir);
        Files.write(dir.resolve("banner.jpg"), "jpg".getBytes(StandardCharsets.UTF_8));
        BannerStore.init(dir);

        assertTrue(BannerStore.clear());
        try (Stream<Path> left = Files.list(dir)) {
            assertEquals(0, left.count(), "the old-format sweep takes every banner.*");
        }
    }
}
