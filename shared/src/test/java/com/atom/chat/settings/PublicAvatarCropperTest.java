package com.atom.chat.settings;

import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PublicAvatarCropperTest {
    private static final UiLayout.Rect PANEL = new UiLayout.Rect(0, 0, 300, 450);

    @BeforeAll static void setup(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    private static ImageCropper cropper() {
        return new ImageCropper(new ImageCropper.Callback() {
            public void onConfirm(String id, byte[] png) { fail("legacy callback"); }
            public void onCancel(String id) { fail("legacy callback"); }
        });
    }

    @Test
    void invalidDecodeClearsBusyAndUnblocksInput(@TempDir Path root) throws Exception {
        var invalid = Files.writeString(root.resolve("broken.png"), "corrupt image");
        var completions = new AtomicInteger();
        var cropper = cropper();
        boolean oldMotion = AtomChatConfig.get().animationEnabled;
        try (var surface = Surface.makeRasterN32Premul(300, 450)) {
            AtomChatConfig.get().animationEnabled = false;
            cropper.openPublicChatAvatar(invalid, bytes -> {
                assertNull(bytes);
                completions.incrementAndGet();
            });
            long until = System.nanoTime() + 1_000_000_000L;
            while (cropper.isActive() && System.nanoTime() < until) {
                cropper.render(surface.getCanvas(), PANEL, 0, 0);
                Thread.sleep(5);
            }
            assertFalse(cropper.isActive(), "a failed decode must dismiss the overlay");
            assertEquals(1, completions.get());
        } finally {
            AtomChatConfig.get().animationEnabled = oldMotion;
            cropper.release();
        }
    }

    @Test
    void leavingBeforeDecodeCompletesCancelsExactlyOnce(@TempDir Path root) {
        var completions = new AtomicInteger();
        var cropper = cropper();
        cropper.openPublicChatAvatar(root.resolve("pending.png"), bytes -> {
            assertNull(bytes);
            completions.incrementAndGet();
        });
        cropper.release();
        cropper.release();
        assertFalse(cropper.isActive());
        assertEquals(1, completions.get());
    }

    @Test
    void publicAvatarConfirmReturnsAnImageAndFadeFinishesWithoutASecondCallback(@TempDir Path root) throws Exception {
        var file = root.resolve("valid.png");
        ImageIO.write(new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        var cropper = cropper();
        var result = new AtomicReference<byte[]>();
        var calls = new AtomicInteger();
        var config = AtomChatConfig.get();
        boolean oldMotion = config.animationEnabled;
        var oldQuality = config.graphicsQuality;
        try (var surface = Surface.makeRasterN32Premul(300, 450)) {
            config.animationEnabled = true;
            config.graphicsQuality = com.atom.chat.config.GraphicsQuality.BALANCED;
            cropper.openPublicChatAvatar(file, bytes -> {
                result.set(bytes);
                calls.incrementAndGet();
            });
            var x = ImageCropper.class.getDeclaredMethod("confirmCx", UiLayout.Rect.class);
            var y = ImageCropper.class.getDeclaredMethod("btnCy", UiLayout.Rect.class);
            x.setAccessible(true);
            y.setAccessible(true);
            float buttonX = (float) x.invoke(cropper, PANEL);
            float buttonY = (float) y.invoke(cropper, PANEL);
            long until = System.nanoTime() + 2_000_000_000L;
            while (result.get() == null && System.nanoTime() < until) {
                cropper.render(surface.getCanvas(), PANEL, buttonX, buttonY);
                cropper.clickTarget(buttonX, buttonY, 0, PANEL).activate().run();
                Thread.sleep(5);
            }
            assertNotNull(result.get());
            var png = ImageIO.read(new java.io.ByteArrayInputStream(result.get()));
            assertEquals(com.atom.chat.pack.PublicChatAvatar.SIZE, png.getWidth());
            assertEquals(png.getWidth(), png.getHeight());
            assertTrue(cropper.isActive(), "the closing overlay retains input until its fade finishes");
            cropper.cancel();
            while (cropper.isActive() && System.nanoTime() < until) {
                cropper.render(surface.getCanvas(), PANEL, 0, 0);
                Thread.sleep(5);
            }
            assertFalse(cropper.isActive());
            assertEquals(1, calls.get());
        } finally {
            config.animationEnabled = oldMotion;
            config.graphicsQuality = oldQuality;
            cropper.release();
        }
    }
}
