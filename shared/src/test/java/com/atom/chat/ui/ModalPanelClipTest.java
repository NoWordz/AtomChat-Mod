package com.atom.chat.ui;

import com.atom.chat.avatar.ColorPickerOverlay;
import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImagePreviewOverlay;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsColor;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ModalPanelClipTest {
    @TempDir Path tmp;
    private java.lang.reflect.Field provider, config;
    private Object previousProvider, previousConfig;

    @BeforeEach void preserveState() throws Exception {
        provider = Platform.class.getDeclaredField("provider");
        config = AtomChatConfig.class.getDeclaredField("instance");
        provider.setAccessible(true);
        config.setAccessible(true);
        previousProvider = provider.get(null);
        previousConfig = config.get(null);
    }

    @AfterEach void restoreState() throws Exception {
        provider.set(null, previousProvider);
        config.set(null, previousConfig);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void everyModalStaysInsideTheActualPanelInnerEdge(int modal) throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp.resolve("config"); }
            public Path gameDir() { return tmp.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        AtomChatConfig.reload();
        AtomChatConfig cfg = AtomChatConfig.get();
        cfg.animationEnabled = false;
        UiLayout.Rect panel = new UiLayout.Rect(10, 10, 400, 780);
        try (Surface surface = Surface.makeRasterN32Premul(420, 800);
             Surface source = Surface.makeRasterN32Premul(40, 30);
             var image = source.makeImageSnapshot(); Bitmap pixels = new Bitmap()) {
            pixels.allocN32Pixels(420, 800);
            ImagePreviewOverlay preview = new ImagePreviewOverlay();
            ColorPickerOverlay picker = new ColorPickerOverlay(s -> {});
            ImageCropper crop = new ImageCropper(new ImageCropper.Callback() {
                public void onConfirm(String id, byte[] png) {}
                public void onCancel(String id) {}
            });
            for (float radius : new float[]{20F, 0F, 40F}) {
                cfg.cornerRadius = radius;
                preview.open(image);
                picker.open(new SettingsColor("test", "test", "ui", new int[0], () -> 0xFF778899, v -> {}));
                crop.open(null, true, 160, 160, "test");
                java.util.List<java.util.function.Consumer<Canvas>> draws = java.util.List.of(
                    c -> preview.render(c, panel, -100, -100),
                    c -> picker.render(c, panel, -100, -100, 0xFF778899),
                    c -> crop.render(c, panel, -100, -100));
                    Canvas canvas = surface.getCanvas();
                    canvas.clear(0);
                    int stack = canvas.getSaveCount();
                    draws.get(modal).accept(canvas);
                    assertEquals(stack, canvas.getSaveCount());
                    assertTrue(surface.readPixels(pixels, 0, 0));
                    byte[] data = pixels.readPixels();
                    // Outside the inner frame, including all four corners.
                    for (int x : new int[]{10, 409}) for (int y : new int[]{10, 400, 789}) {
                        assertEquals(0, alpha(data, x, y), "frame bleed modal=" + modal + " radius=" + radius);
                    }
                    if (radius > 0) {
                        for (int x : new int[]{14, 405}) for (int y : new int[]{14, 785}) {
                            assertEquals(0, alpha(data, x, y), "corner bleed modal=" + modal + " radius=" + radius);
                        }
                    }
                    assertTrue(alpha(data, 210, 80) > 0, "modal must actually paint");
                    if (radius == 20F) {
                        Path out = Path.of(System.getProperty("atomchat.repoRoot", "."),
                                ".work/0.3.2-preview/modal-" + modal + ".png");
                        java.nio.file.Files.createDirectories(out.getParent());
                        try (var snapshot = surface.makeImageSnapshot();
                             var encoded = snapshot.encodeToData(io.github.humbleui.skija.EncodedImageFormat.PNG)) {
                            java.nio.file.Files.write(out, encoded.getBytes());
                        }
                    }
            }
        }
    }

    private static int alpha(byte[] data, int x, int y) { return data[(y * 420 + x) * 4 + 3] & 255; }
}
