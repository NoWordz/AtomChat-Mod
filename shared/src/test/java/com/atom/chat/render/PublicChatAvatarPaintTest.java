package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.pack.PackKeys;
import com.atom.chat.pack.PackManifestFile;
import com.atom.chat.pack.PublicChatAvatar;
import com.atom.chat.pack.ServerPack;
import com.atom.chat.pack.ServerPackStore;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PublicChatAvatarPaintTest {
    @TempDir Path root;

    @Test
    void publishedImageAndFallbackUseThemeCornersAndRespectReceiveSwitch() throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        var config = AtomChatConfig.get();
        var originalRadius = config.cornerRadius;
        var originalReceive = config.serverPacksEnabled;
        int photo = 0xFFDD2200;
        var source = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 96; y++) {
            for (int x = 0; x < 96; x++) {
                if (x < 30 || x >= 66 || y < 30 || y >= 66) source.setRGB(x, y, photo);
            }
        }
        var upload = new ByteArrayOutputStream();
        ImageIO.write(source, "png", upload);
        Path published = root.resolve("server/public-chat-avatar.png");
        PublicChatAvatar.save(published, "", upload.toByteArray());
        byte[] png = PublicChatAvatar.read(published);
        byte[] legacyIcon = upload.toByteArray();
        var pack = new ServerPack(ServerPack.hashOf(ServerPack.canonical(List.of(), List.of(), "", legacyIcon)),
                List.of(), List.of(), "", legacyIcon);
        String key = PackKeys.serverKey("first.example");
        PackManifestFile.install(root, key, pack, Map.of());
        ServerPackStore.clearPublicAvatar();
        ServerPackStore.refresh(root, key);
        assertNull(ServerPackStore.publicAvatar(), "disk cache alone cannot publish an avatar");
        assertFalse(ServerPackStore.installPublicAvatar("wrong hash", png));
        assertTrue(ServerPackStore.installPublicAvatar(ServerPack.sha256Hex(png), png));
        assertArrayEquals(legacyIcon, ServerPackStore.current().icon());
        ServerPackStore.refresh(root, key);
        assertArrayEquals(png, ServerPackStore.publicAvatar().png(), "legacy pack refresh leaves the avatar alone");
        try (var surface = Surface.makeRasterN32Premul(128, 128); var pixels = new Bitmap()) {
            var canvas = surface.getCanvas();
            canvas.scale(1.25F, 1.25F);
            pixels.allocN32Pixels(128, 128);
            float defaultRadius = new AtomChatConfig().cornerRadius;
            float[] corners = {0F, defaultRadius, AtomChatConfig.MAX_CORNER_RADIUS};
            for (int background : new int[]{0xFF222222, 0xFFEEEEEE}) {
                int ink = background == 0xFF222222 ? 0xFFFFFFFF : 0xFF111111;
                for (int i = 0; i < corners.length; i++) {
                    config.cornerRadius = corners[i];
                    config.serverPacksEnabled = true;
                    canvas.clear(0);
                    PublicChatAvatarPaint.draw(canvas, 10.4F, 10.4F, 64, background, ink);
                    assertTrue(surface.readPixels(pixels, 0, 0));
                    assertEquals(i == 0 ? photo : 0, pixels.getColor(14, 14));
                    assertEquals(photo, pixels.getColor(52, 20));
                    assertEquals(background, pixels.getColor(52, 52), "transparent center reveals the same background");
                    saveEvidence(surface, "photo-" + Integer.toHexString(background) + "-" + i);

                    config.serverPacksEnabled = false;
                    canvas.clear(0);
                    PublicChatAvatarPaint.draw(canvas, 10.4F, 10.4F, 64, background, ink);
                    assertTrue(surface.readPixels(pixels, 0, 0));
                    assertEquals(i == 0 ? background : 0, pixels.getColor(14, 14));
                    assertEquals(background, pixels.getColor(52, 20), "off switch takes effect in the next draw");
                    boolean hasGlobeInk = false;
                    for (int y = 36; y < 68; y++) {
                        for (int x = 36; x < 68; x++) hasGlobeInk |= pixels.getColor(x, y) != background;
                    }
                    assertTrue(hasGlobeInk);
                    saveEvidence(surface, "globe-" + Integer.toHexString(background) + "-" + i);
                }
            }
            ServerPackStore.clearPublicAvatar();
            ServerPackStore.refresh(root, PackKeys.serverKey("second.example"));
            assertNull(ServerPackStore.publicAvatar());
        } finally {
            config.cornerRadius = originalRadius;
            config.serverPacksEnabled = originalReceive;
            ServerPackStore.clear();
            ServerPackStore.clearPublicAvatar();
            PublicChatAvatarPaint.clear();
        }
    }

    private static void saveEvidence(Surface surface, String name) throws Exception {
        String directory = System.getenv("ATOMCHAT_CARDS_RENDER_EVIDENCE");
        if (directory == null) return;
        Path target = Files.createDirectories(Path.of(directory)).resolve(name + ".png");
        try (var image = surface.makeImageSnapshot(); var data = image.encodeToData(EncodedImageFormat.PNG)) {
            Files.write(target, data.getBytes());
        }
    }
}
