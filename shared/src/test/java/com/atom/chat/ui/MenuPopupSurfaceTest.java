package com.atom.chat.ui;

import com.atom.chat.render.SkiaDraw;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MenuPopupSurfaceTest {
    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            @Override public Path configDir() { return root.resolve("config"); }
            @Override public Path gameDir() { return root.resolve("game"); }
            @Override public boolean isModLoaded(String modId) { return false; }
        });
    }

    @Test
    void popupKeepsTheAcceptedEmojiPanelEdgeAtEveryMenuHeight() {
        for (float height : new float[]{80, 120, 160}) {
            try (Surface expected = Surface.makeRasterN32Premul(240, 240);
                 Surface actual = Surface.makeRasterN32Premul(240, 240)) {
                Canvas reference = expected.getCanvas();
                reference.clear(0xFF15171A);
                actual.getCanvas().clear(0xFF15171A);
                // Accepted pre-unification menu and current emoji panel: fill then shadow.
                SkiaDraw.drawRoundedRect(reference, 30, 30, 160, height,
                        UiTokens.radius(MenuPopup.SURFACE_RADIUS), MenuPopup.SURFACE_COLOR);
                SkiaDraw.drawRoundedShadow(reference, 30, 30, 160, height,
                        UiTokens.radius(MenuPopup.SURFACE_RADIUS),
                        UiTokens.s(MenuPopup.SURFACE_SHADOW_BLUR), UiTokens.CHROME_SHADOW);
                MenuPopup.drawSurface(actual.getCanvas(), 30, 30, 160, height);
                assertArrayEquals(pixels(expected), pixels(actual),
                        "Menu surface lost the accepted edge gradient at height " + height);
            }
        }
    }

    private static byte[] pixels(Surface surface) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(240, 240);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }
}
