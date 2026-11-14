package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.design.atomchat.SkiaDraw;
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
    void popupPreservesItsInsetLightGradientBehindTheNewSoftShadow() {
        for (float height : new float[]{80, 120, 160}) {
            try (Surface expected = Surface.makeRasterN32Premul(240, 240);
                 Surface actual = Surface.makeRasterN32Premul(240, 240)) {
                Canvas reference = expected.getCanvas();
                reference.clear(0xFF15171A);
                actual.getCanvas().clear(0xFF15171A);
                com.atom.chat.render.SoftShadow.drawFloating(reference,30,30,160,height,UiTokens.radius(MenuPopup.SURFACE_RADIUS));
                SkiaDraw.drawRoundedRect(reference,30,30,160,height,UiTokens.radius(MenuPopup.SURFACE_RADIUS),MenuPopup.SURFACE_COLOR);
                MenuPopup.drawSurface(actual.getCanvas(), 30, 30, 160, height);
                assertPixelsClose(interiorPixels(expected,(int)height),interiorPixels(actual,(int)height),1,
                        "Menu surface lost its inset gradient at height " + height);
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

    /** The popup fill, cropped inside its outline so the intentional outer shadow is excluded. */
    private static byte[] interiorPixels(Surface surface,int height) {
        int inset=20,w=160-inset*2,h=height-inset*2;
        byte[] all=pixels(surface),out=new byte[w*h*4];
        for(int y=0;y<h;y++)
            System.arraycopy(all,((30+inset+y)*240+30+inset)*4,out,y*w*4,w*4);
        return out;
    }

    private static void assertPixelsClose(byte[] expected,byte[] actual,int tolerance,String message){
        assertEquals(expected.length,actual.length,message);
        int max=0;
        for(int i=0;i<expected.length;i++)max=Math.max(max,Math.abs((expected[i]&255)-(actual[i]&255)));
        assertTrue(max<=tolerance,message+", max channel delta="+max);
    }
}
