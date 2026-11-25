package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class GraphicsQualityRenderingTest {
    @BeforeAll static void installPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root; }
            public boolean isModLoaded(String modId) { return false; }
        });
    }

    @Test void weakerDotsKeepTheirTierAcrossTheEdgeAndFieldCaches() {
        var bar = new UiLayout.Rect(20, 136, 300, 50);
        lowerInkAndRestore(canvas -> DotTexture.drawEdge(canvas, bar, 20, true, 0xFFFFFFFF));
        var halo = new UiLayout.Rect(20, 25, 300, 140);
        lowerInkAndRestore(canvas -> DotTexture.drawHalo(canvas, halo, 0xFFFFFFFF));
    }

    @Test void lowerDensityShadowsKeepTheirTierAcrossThePanelAndHudCaches() {
        lowerInkAndRestore(canvas -> SoftShadow.drawPanel(canvas, 34, 44, 260, 134, 20, true));
        var message = RRect.makeXYWH(70, 60, 140, 53, 12);
        lowerInkAndRestore(canvas -> HudShadow.drawPanel(canvas, message));
    }

    private static void lowerInkAndRestore(Consumer<Canvas> paint) {
        var config = AtomChatConfig.get();
        var original = config.graphicsQuality;
        try {
            byte[] ultra = pixels(config, GraphicsQuality.ULTRA, paint);
            byte[] balanced = pixels(config, GraphicsQuality.BALANCED, paint);
            long ultraInk = alphaSum(ultra);
            long balancedInk = alphaSum(balanced);
            System.out.printf("QUALITY_ALPHA ultra=%d balanced=%d%n", ultraInk, balancedInk);
            assertTrue(balancedInk > 0 && balancedInk < ultraInk, "the lower tier retains a visibly softer effect");
            assertArrayEquals(balanced, pixels(config, GraphicsQuality.BALANCED, paint));
            assertArrayEquals(ultra, pixels(config, GraphicsQuality.ULTRA, paint));
            assertEquals(0, alphaSum(pixels(config, GraphicsQuality.PERFORMANCE, paint)));
        } finally {
            config.graphicsQuality = original;
        }
    }

    private static byte[] pixels(AtomChatConfig config, GraphicsQuality quality, Consumer<Canvas> paint) {
        config.graphicsQuality = quality;
        try (var surface = Surface.makeRasterN32Premul(360, 220); var bitmap = new Bitmap()) {
            Canvas canvas = surface.getCanvas();
            // The second identical frame takes the settled device-cache route where available.
            canvas.clear(0);
            paint.accept(canvas);
            canvas.clear(0);
            paint.accept(canvas);
            bitmap.allocN32Pixels(360, 220);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }

    private static long alphaSum(byte[] argb) {
        long sum = 0;
        for (int i = 3; i < argb.length; i += 4) sum += argb[i] & 255;
        return sum;
    }
}
