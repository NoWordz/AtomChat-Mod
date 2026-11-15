package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelCloseOverlayTest {
    private final PanelCloseOverlay overlay=PanelCloseOverlay.INSTANCE;
    private final Object world=new Object();
    @AfterEach void cleanup(){overlay.clear();}
    private void begin(long now) {
        try(var recorder=new PictureRecorder();var paint=new Paint().setColor(0xFFFFFFFF)) {
            recorder.beginRecording(Rect.makeWH(200,200)).drawRect(Rect.makeXYWH(30,30,80,80),paint);
            overlay.begin(recorder.finishRecordingAsPicture(),new UiLayout.Rect(30,30,80,80),
                    AtomChatConfig.PageNavStyle.SLIDE,
                    new PanelCloseOverlay.Environment(world,7,1,200,200),1,false,now);
        }
    }
    @Test void exitOwnsOnlyFrozenPixelsAndExpiresWithoutAScreen() {
        begin(0);
        try(var surface=Surface.makeRasterN32Premul(200,200);var pixels=new Bitmap()) {
            var canvas=surface.getCanvas();canvas.clear(0);
            int saves=canvas.getSaveCount();
            assertTrue(overlay.active(world,1,200,200,80_000_000L,true));
            overlay.draw(canvas,7,80_000_000L);
            assertEquals(saves,canvas.getSaveCount());
            pixels.allocN32Pixels(200,200);assertTrue(surface.readPixels(pixels,0,0));
            assertTrue((pixels.getColor(70,70)>>>24)>0);
        }
        assertFalse(overlay.active(world,1,200,200,180_000_000L,true));
        assertEquals(0,overlay.progress(180_000_000L));
    }
    @Test void worldResizeAndContextChangesDiscardTheFrame() {
        begin(0);assertFalse(overlay.active(new Object(),1,200,200,1,true));
        begin(0);assertFalse(overlay.active(world,1,201,200,1,true));
        begin(0);
        try(var surface=Surface.makeRasterN32Premul(200,200)) {overlay.draw(surface.getCanvas(),8,1);}
        assertEquals(0,overlay.progress(1));
    }
    @Test void reopeningContinuesAtTheCurrentPositionAndReleasesTheExit() {
        begin(0);
        float progress=overlay.progress(60_000_000L);
        assertEquals(progress,overlay.takeProgress(world,60_000_000L));
        assertEquals(0,overlay.progress(60_000_000L));
        begin(0);assertFalse(overlay.active(world,1,200,200,1,false));
    }
}
