package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.ConfirmationDialog;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.input.PointerEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production appearance chip using the Mojang-mapped build. */
class AtomLibAppearanceSectionTest extends AtomLibSettingsTestSupport {

    @Test void productionLayoutSliderPersistsOnce(){
        var width=catalogWidth();var mode=mode();
        var saves=new AtomicInteger();var dialog=new ConfirmationDialog();
        var section=new AtomLibAppearanceSection(host,width,mode,List.of(),List.of(),key->key,dialog,
                ()->{saves.incrementAndGet();width.persist();});
        try(section){
            section.setSlot(new UiLayout.Rect(20,30,480,600),new UiLayout.Rect(20,30,480,600));section.frame();
            var track=section.widthTrack();float sx=20,sy=30;
            section.pointer(PointerEvent.Type.PRESS,sx+track.x()+track.width()/2,sy+track.y(),0);
            section.setSlot(new UiLayout.Rect(20,30,AtomChatConfig.get().activePanelWidth(),600),new UiLayout.Rect(20,30,AtomChatConfig.get().activePanelWidth(),600));
            var moved=section.widthTrack();section.pointer(PointerEvent.Type.MOVE,sx+moved.right()-1,sy+moved.y(),0);
            section.pointer(PointerEvent.Type.RELEASE,sx+moved.right()-1,sy+moved.y(),0);
            assertEquals(1,saves.get());assertNull(section.captureOwner());
        }
    }

    /**
     * The half-screen toggle cancels its own gesture. That pause must not outlive
     * the transition: afterwards the width slider keeps working, while a genuinely
     * open AC dialog still owns input until its closing animation ends.
     */
    @Test void halfScreenToggleLeavesTheWidthSliderInteractive(){
        var width=catalogWidth();var mode=mode();
        var saves=new AtomicInteger();var dialog=new ConfirmationDialog();
        var section=new AtomLibAppearanceSection(host,width,mode,List.of(),List.of(),key->key,dialog,
                ()->{saves.incrementAndGet();width.persist();});
        try(section){
            float sx=20,sy=30;
            section.setSlot(new UiLayout.Rect(sx,sy,480,600),new UiLayout.Rect(sx,sy,480,600));section.frame();
            var toggle=section.frame().box(AtomLibAppearanceSection.HALF_SCREEN).border();
            float tx=sx+toggle.x()+toggle.width()/2,ty=sy+toggle.y()+toggle.height()/2;
            section.pointer(PointerEvent.Type.PRESS,tx,ty,0);
            section.pointer(PointerEvent.Type.RELEASE,tx,ty,0);
            assertTrue(mode.value(),"the toggle still commits the half-screen mode");
            section.cancel();
            clickTrack(section,sx,sy,0.25f);
            assertEquals(1,saves.get(),"the width slider must keep receiving gestures after the mode change and reset");
            section.openModal(()->dialog.open("确认","说明","取消","确认",()->{}));
            assertTrue(dialog.isActive(),"the adapter still opens the AC dialog");
            clickTrack(section,sx,sy,0.75f);
            assertEquals(1,saves.get(),"the open modal still owns input");
            dialog.reset();section.frame();
            clickTrack(section,sx,sy,0.5f);
            assertEquals(2,saves.get(),"closing the dialog resumes the slider");
        }
    }

    /** The pilot must replace the chip, not shrink its option set. */
    @Test void migrationKeepsEveryLayoutAndTransparencyOption(){
        var sliders=SettingsCatalog.sliders(SettingsSection.APPEARANCE);
        assertEquals(List.of("scale","contentscale"),
                AtomLibAppearanceSection.layoutSliders(sliders).stream().map(SettingsSlider::id).toList());
        assertEquals(List.of("opacity","cardtint"),
                AtomLibAppearanceSection.transparencySliders(sliders).stream().map(SettingsSlider::id).toList());
        var width=catalogWidth();var mode=mode();
        var section=new AtomLibAppearanceSection(host,width,mode,
                AtomLibAppearanceSection.layoutSliders(sliders),AtomLibAppearanceSection.transparencySliders(sliders),
                key->key,new ConfirmationDialog(),()->{});
        try(section){
            section.setSlot(new UiLayout.Rect(20,30,480,900),new UiLayout.Rect(20,30,480,900));
            var order=section.frame().paintOrder();
            for(String id:List.of("ac.appearance.opacity.row","ac.appearance.cardtint.row")){
                assertTrue(order.contains(new com.atom.lib.layout.NodeId(id)),id);
            }
        }
    }

    private static SettingsItem mode(){
        return SettingsCatalog.items(SettingsSection.APPEARANCE).stream().filter(i->i.id().equals("half_screen")).findFirst().orElseThrow();
    }
    private static void clickTrack(AtomLibAppearanceSection section,float sx,float sy,float fraction){
        var track=section.widthTrack();
        float px=sx+track.x()+track.width()*fraction,py=sy+track.y();
        section.pointer(PointerEvent.Type.PRESS,px,py,0);
        section.pointer(PointerEvent.Type.RELEASE,px,py,0);
    }
    /** Renders the pilot offscreen for visual review; the PNG is a test artifact, not a golden. */
    @Test void renderPreviewForVisualReview() throws Exception {
        // Built through the production factory so the preview carries AC's real
        // palette, type scale and bundled font instead of the test host theme.
        var section=AtomLibAppearanceSection.create(new ConfirmationDialog());
        try(section){
            int w=420,h=1000;
            section.setSlot(new UiLayout.Rect(0,0,w,h),new UiLayout.Rect(0,0,w,h));
            var out=java.nio.file.Path.of("build","appearance-preview.png");
            java.nio.file.Files.createDirectories(out.getParent());
            try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(w,h)){
                var canvas=surface.getCanvas();
                canvas.clear(0xFF1B2230);
                section.paint(canvas,0xFF2E6F77,16f);
                try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){
                    java.nio.file.Files.write(out,data.getBytes());
                }
            }
            assertTrue(java.nio.file.Files.size(out)>1000,"preview stored at "+out.toAbsolutePath());
        }
    }
    /** Same-size render of the pre-pilot renderer, for a side-by-side comparison. */
    @Test void renderLegacyChipForComparison() throws Exception {
        var page=new SettingsSectionPage();
        page.selectChip(SettingsSection.APPEARANCE,2);
        Thread.sleep(200);
        var layout=UiLayout.ofDetail(0,0,420,1000);
        var out=java.nio.file.Path.of("build","appearance-legacy.png");
        java.nio.file.Files.createDirectories(out.getParent());
        try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(420,1000)){
            var canvas=surface.getCanvas();
            canvas.clear(0xFF1B2230);
            page.render(canvas,layout,SettingsSection.APPEARANCE,-1,-1,0,0xFF2E6F77);
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){
                java.nio.file.Files.write(out,data.getBytes());
            }
        }
        assertTrue(java.nio.file.Files.size(out)>1000,"legacy preview stored at "+out.toAbsolutePath());
    }
}
