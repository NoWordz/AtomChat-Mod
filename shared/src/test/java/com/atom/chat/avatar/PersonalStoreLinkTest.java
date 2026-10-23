package com.atom.chat.avatar;

import com.atom.chat.banner.BannerStore;
import com.atom.chat.wallpaper.WallpaperStore;
import com.atom.chat.emote.EmoteStore;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PersonalStoreLinkTest {
    @TempDir Path root;
    @Test void privateStoresCannotReadWriteOrClearThroughALinkIntoConfig() throws Exception {
        Path config = Files.createDirectories(root.resolve("config/atomchat"));
        Files.createDirectories(config.resolve("avatar"));
        Files.createDirectories(config.resolve("wallpaper"));
        Files.createDirectories(config.resolve("emotes"));
        Files.writeString(config.resolve("avatar/avatar.png"), "original");
        Files.writeString(config.resolve("wallpaper/wallpaper.png"), "original");
        Files.writeString(config.resolve("banner.png"), "original");
        Files.writeString(config.resolve("emotes/private.png"), "original");
        Path link = root.resolve("atomchat");
        try { Files.createSymbolicLink(link, config); }
        catch (FileSystemException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
            Process process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J", link.toString(), config.toString())
                    .redirectErrorStream(true).start();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
        }
        Path picked = root.resolve("picked.png"); Files.writeString(picked, "new");
        AvatarStore avatar = new AvatarStore(link.resolve("avatar"));
        assertNull(avatar.current(), "unsafe files must not become implicit uploads");
        assertFalse(avatar.setPng(new byte[]{1}));
        assertFalse(avatar.set(picked));
        assertFalse(avatar.clear());
        BannerStore.init(link);
        assertNull(BannerStore.current());
        assertFalse(BannerStore.setPng(new byte[]{1}));
        assertFalse(BannerStore.clear());
        WallpaperStore.init(link.resolve("wallpaper"));
        assertNull(WallpaperStore.current());
        assertFalse(WallpaperStore.setPng(new byte[]{1}));
        assertFalse(WallpaperStore.set(picked));
        assertFalse(WallpaperStore.clear());
        EmoteStore emotes = new EmoteStore(link.resolve("emotes"));
        assertEquals(0, emotes.count());
        assertFalse(emotes.add(picked.toFile()));
        assertFalse(emotes.remove(link.resolve("emotes/private.png").toFile()));
        for (String file : new String[]{"avatar/avatar.png", "wallpaper/wallpaper.png", "banner.png", "emotes/private.png"}) {
            assertEquals("original", Files.readString(config.resolve(file)), file);
        }
    }
}
