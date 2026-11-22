package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.PictureRecorder;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsLabelShadowTest {
    @Test
    void cachedBackdropPreservesTheLiveShadowAcrossDensityPhaseAndTheme() throws Exception {
        var configField = AtomChatConfig.class.getDeclaredField("instance");
        configField.setAccessible(true);
        var oldConfig = configField.get(null);
        var config = new AtomChatConfig();
        configField.set(null, config);
        float previousDensity = UiPixelSnap.density();
        try (var builder = new PathBuilder(); var icon = builder.moveTo(0, 0).lineTo(24, 24).detach()) {
            var font = FontManager.boldFont(17F);
            for (int panel : new int[]{0xFF16191F, 0xFFEEEEEE}) {
                config.panelBgColor = panel;
                for (float density : new float[]{1F, 1.25F, 2F}) {
                    UiPixelSnap.beginFrame(density);
                    for (float phase : new float[]{0F, .37F, .83F}) {
                        Consumer<Canvas> ink = canvas -> SettingsLabelShadow.draw(canvas, font, "折叠标题 Settings",
                                42.3F, 40.7F + phase, icon, 23F, 34.8F + phase, 18F);
                        byte[] live = render(density, ink);
                        Consumer<Canvas> replay = canvas -> {
                            try (var recorder = new PictureRecorder()) {
                                Canvas recording = recorder.beginRecording(Rect.makeWH(240, 96));
                                SettingsLabelShadow.onDeviceGrid(recording, canvas.getLocalToDeviceAsMatrix33(),
                                        () -> ink.accept(recording));
                                try (var picture = recorder.finishRecordingAsPicture()) {
                                    picture.playback(canvas);
                                }
                            }
                        };
                        render(density, replay);
                        byte[] reused = render(density, replay);
                        assertTrue(maxDelta(live, reused) <= 1,
                                "shadow pixels changed at density " + density + " phase " + phase
                                        + " panel " + Integer.toHexString(panel) + ", delta " + maxDelta(live, reused));
                        assertTrue(darkened(reused, density),
                                "the fold icon also casts the backing shadow");
                    }
                }
            }
        } finally {
            UiPixelSnap.beginFrame(previousDensity);
            configField.set(null, oldConfig);
        }
    }

    private static boolean darkened(byte[] pixels, float density) {
        for (int y = (int) (22F * density); y < 45F * density; y++) {
            for (int x = (int) (10F * density); x < 34F * density; x++) {
                if ((pixels[(y * 480 + x) * 4] & 255) < 245) return true;
            }
        }
        return false;
    }

    private static int maxDelta(byte[] left, byte[] right) {
        int max = 0;
        for (int i = 0; i < left.length; i++) max = Math.max(max, Math.abs((left[i] & 255) - (right[i] & 255)));
        return max;
    }

    private static byte[] render(float density, Consumer<Canvas> draw) {
        try (var surface = Surface.makeRasterN32Premul(480, 192); var bitmap = new Bitmap()) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFFFFFFFF);
            canvas.scale(density, density);
            draw.accept(canvas);
            bitmap.allocN32Pixels(480, 192);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }
}
