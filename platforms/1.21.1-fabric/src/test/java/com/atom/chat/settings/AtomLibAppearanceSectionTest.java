package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.ConfirmationDialog;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.input.PointerEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production appearance chip using the Yarn-mapped platform build. */
class AtomLibAppearanceSectionTest extends AtomLibSettingsTestSupport {
    @Test void productionLayoutSliderPersistsOnceAndConfirmUsesAcModal(){
        var width=catalogWidth();var mode=SettingsCatalog.items(SettingsSection.APPEARANCE).stream().filter(i->i.id().equals("half_screen")).findFirst().orElseThrow();
        var saves=new AtomicInteger();var confirmed=new AtomicInteger();var dialog=new ConfirmationDialog();
        var item=new SettingsItem("reset","title","subtitle",()->true,v->{});
        var section=new AtomLibAppearanceSection(host,width,mode,List.of(),item,()->true,
                key->key,dialog,confirmed::incrementAndGet,()->{saves.incrementAndGet();width.persist();});
        try(section){
            section.setSlot(new UiLayout.Rect(20,30,480,600),new UiLayout.Rect(20,30,480,600));section.frame();
            var track=section.widthTrack();float sx=20,sy=30;
            section.pointer(PointerEvent.Type.PRESS,sx+track.x()+track.width()/2,sy+track.y(),0);
            section.setSlot(new UiLayout.Rect(20,30,AtomChatConfig.get().activePanelWidth(),600),new UiLayout.Rect(20,30,AtomChatConfig.get().activePanelWidth(),600));
            var moved=section.widthTrack();section.pointer(PointerEvent.Type.MOVE,sx+moved.right()-1,sy+moved.y(),0);
            section.pointer(PointerEvent.Type.RELEASE,sx+moved.right()-1,sy+moved.y(),0);
            assertEquals(1,saves.get());assertNull(section.captureOwner());
            var confirm=section.frame().box(AtomLibAppearanceSection.CONFIRM).border();
            section.pointer(PointerEvent.Type.PRESS,sx+confirm.x()+2,sy+confirm.y()+2,0);
            section.pointer(PointerEvent.Type.RELEASE,sx+confirm.x()+2,sy+confirm.y()+2,0);
            assertEquals(1,confirmed.get());
        }
    }

    /**
     * The half-screen toggle cancels its own gesture. That pause must not outlive
     * the transition: afterwards the width slider keeps working, while a genuinely
     * open AC dialog still owns input until its closing animation ends.
     */
    @Test void halfScreenToggleLeavesTheWidthSliderInteractive(){
        var width=catalogWidth();
        var mode=SettingsCatalog.items(SettingsSection.APPEARANCE).stream().filter(i->i.id().equals("half_screen")).findFirst().orElseThrow();
        var saves=new AtomicInteger();var dialog=new ConfirmationDialog();
        var item=new SettingsItem("reset","title","subtitle",()->true,v->{});
        var section=new AtomLibAppearanceSection(host,width,mode,List.of(),item,()->true,
                key->key,dialog,()->dialog.open("确认","说明","取消","确认",()->{}),
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
            var confirm=section.frame().box(AtomLibAppearanceSection.CONFIRM).border();
            section.pointer(PointerEvent.Type.PRESS,sx+confirm.x()+2,sy+confirm.y()+2,0);
            section.pointer(PointerEvent.Type.RELEASE,sx+confirm.x()+2,sy+confirm.y()+2,0);
            assertTrue(dialog.isActive(),"the confirm row still opens the AC dialog");
            clickTrack(section,sx,sy,0.75f);
            assertEquals(1,saves.get(),"the open modal still owns input");
            dialog.reset();section.frame();
            clickTrack(section,sx,sy,0.5f);
            assertEquals(2,saves.get(),"closing the dialog resumes the slider");
        }
    }
    private static void clickTrack(AtomLibAppearanceSection section,float sx,float sy,float fraction){
        var track=section.widthTrack();
        float px=sx+track.x()+track.width()*fraction,py=sy+track.y();
        section.pointer(PointerEvent.Type.PRESS,px,py,0);
        section.pointer(PointerEvent.Type.RELEASE,px,py,0);
    }
}
