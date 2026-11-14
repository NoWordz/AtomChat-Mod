package com.atom.chat.ui;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import com.atom.chat.onboarding.TutorialOverlay;
import com.atom.chat.onboarding.TutorialSession;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PickerModalShadowTest {
    @Test void modalHasSoftTailOutsideItsSurface() {
        try(var surface=Surface.makeRasterN32Premul(500,350);var pixels=new Bitmap()) {
            surface.getCanvas().clear(0);
            PickerModalPaint.surface(surface.getCanvas(),new UiLayout.Rect(100,80,300,180),1F);
            pixels.allocN32Pixels(500,350); assertTrue(surface.readPixels(pixels,0,0));
            int near=pixels.getColor(90,170)>>>24, far=pixels.getColor(68,170)>>>24;
            assertTrue(far>0,"shadow reaches beyond the old narrow rim");
            assertTrue(near>far,"shadow fades with distance");
        }
    }

    @Test void realTutorialAndDialogKeepCanvasBalancedAndProduceEvidence(@TempDir Path dir) throws Exception {
        var provider=Platform.class.getDeclaredField("provider"); provider.setAccessible(true); Object old=provider.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return dir;} public Path gameDir(){return dir;} public boolean isModLoaded(String id){return false;}});
        try {
        var session=new TutorialSession(); session.start(); var overlay=new TutorialOverlay();
        var dialog=new ConfirmationDialog(); dialog.open("Tutorial", "A floating explanation", "Cancel", "Continue", () -> {});
        var panel=new UiLayout.Rect(70,40,460,650);
        var layout=UiLayout.ofRoot(panel.x(),panel.y(),panel.w(),panel.h());
        for(int i=0;i<2;i++) try(var surface=Surface.makeRasterN32Premul(600,740)) {
            var c=surface.getCanvas(); c.clear(0xFF525965); int saves=c.getSaveCount();
            if(i==0) overlay.render(c,session,layout,layout,layout.tabBar,k->"Floating explanation and controls",0,0,1000);
            else dialog.render(c,panel,0,0,1000);
            assertEquals(saves,c.getSaveCount());
            Path path=Path.of(System.getProperty("atomchat.repoRoot","../.."),".work/evidence/2026-11-13-followup",i==0?"tutorial-shadow.png":"dialog-shadow.png");
            Files.createDirectories(path.getParent());
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){Files.write(path,data.getBytes());}
        }
        overlay.close();
        } finally { provider.set(null,old); }
    }
}
