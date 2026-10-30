package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.ConfirmationDialog;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.input.PointerEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production appearance chip using the Mojang-mapped platform build. */
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
}
