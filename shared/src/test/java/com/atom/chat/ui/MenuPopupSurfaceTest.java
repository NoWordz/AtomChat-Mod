package com.atom.chat.ui;


import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Bitmap;
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
    void popupKeepsItsUpperInsetLighting() {
        for (float height : new float[]{80, 120, 160}) {
            try (Surface actual = Surface.makeRasterN32Premul(240, 240);
                 Bitmap bitmap = new Bitmap()) {
                actual.getCanvas().clear(0xFF15171A);
                MenuPopup.drawSurface(actual.getCanvas(), 30, 30, 160, height);
                bitmap.allocN32Pixels(240,240);assertTrue(actual.readPixels(bitmap,0,0));
                int edge=bitmap.getColor(110,32)&255;
                int center=bitmap.getColor(110,30+(int)height/2)&255;
                assertTrue(edge>center+4,"the accepted popup has a lighter inner upper edge, height="+height);
            }
        }
    }
}
