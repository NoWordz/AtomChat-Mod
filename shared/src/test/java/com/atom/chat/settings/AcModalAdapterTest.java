package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.ConfirmationDialog;
import com.atom.chat.ui.atomlib.*;
import com.atom.lib.component.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.view.UiView;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcModalAdapterTest extends AtomLibSettingsTestSupport {
    @Test void openingTheExistingDialogCancelsPreviewImmediatelyAndBlocksUntilItsVisibilityEnds() {
        var saves=new AtomicInteger();var setting=catalogWidth();
        var slider=new Slider(new NodeId("width"),new SliderSpec(setting.min(),setting.max(),setting.step()),new AtomLibSettingsBinding(setting,AtomChatConfig::get,saves::incrementAndGet),false);
        try(var view=new UiView(host,new Section("root",Section.Kind.GROUP,List.of(slider)),new Viewport(480,300,1))) {
            var dialog=new ConfirmationDialog();var modal=new AcModalAdapter(view,dialog::isActive);
            view.refresh();var track=slider.track();view.pointer(new PointerEvent(PointerEvent.Type.PRESS,track.right(),track.y(),0));
            assertNotEquals(480,setting.value());assertNotNull(view.captureOwner());
            modal.open(()->dialog.open("确认","长说明","取消","确认",()->fail("must not confirm")));
            assertTrue(dialog.isActive());assertNull(view.captureOwner());assertEquals(480,setting.value());assertEquals(0,saves.get());
            assertEquals(InputResult.CONSUMED,view.key(PanelWidthGestureTest.key(KeyEvent.Key.RIGHT)));assertEquals(480,setting.value());
            dialog.cancel();modal.sync();assertTrue(dialog.isActive(),"closing animation still owns input");
            view.key(PanelWidthGestureTest.key(KeyEvent.Key.RIGHT));assertEquals(480,setting.value());
            dialog.reset();modal.sync();view.key(PanelWidthGestureTest.key(KeyEvent.Key.RIGHT));assertEquals(490,setting.value());assertEquals(1,saves.get());
        }
    }
    @Test void suspensionKeepsAValidFocusAndNeverRestoresRemovedControls() {
        var first=new Button("first","First",()->{});var second=new Button("second","Second",()->{});var dialog=new ConfirmationDialog();
        try(var view=new UiView(host,new Section("root",Section.Kind.GROUP,List.of(first,second)),new Viewport(480,300,1))) {
            var modal=new AcModalAdapter(view,dialog::isActive);
            view.key(PanelWidthGestureTest.key(KeyEvent.Key.TAB));view.key(PanelWidthGestureTest.key(KeyEvent.Key.TAB));var focus=view.focused();
            modal.open(()->dialog.open("确认","说明","取消","确认",()->{}));dialog.reset();modal.sync();assertEquals(focus,view.focused());
            modal.open(()->dialog.open("确认","说明","取消","确认",()->{}));
            view.setRoot(new Section("root",Section.Kind.GROUP,List.of(first)));dialog.reset();modal.sync();
            assertNotEquals(focus,view.focused());
        }
    }
    @Test void leavingThePageCancelsWithoutPersistingAndDoesNotResumeBehindAModal() {
        var saves=new AtomicInteger();var setting=catalogWidth();var dialog=new ConfirmationDialog();
        var slider=new Slider(new NodeId("width"),new SliderSpec(setting.min(),setting.max(),setting.step()),new AtomLibSettingsBinding(setting,AtomChatConfig::get,saves::incrementAndGet),false);
        try(var view=new UiView(host,new Section("root",Section.Kind.GROUP,List.of(slider)),new Viewport(480,300,1))) {
            var modal=new AcModalAdapter(view,dialog::isActive);view.refresh();var track=slider.track();
            view.pointer(new PointerEvent(PointerEvent.Type.PRESS,track.right(),track.y(),0));modal.setHostActive(false);
            assertEquals(480,setting.value());assertNull(view.captureOwner());assertEquals(0,saves.get());
            modal.open(()->dialog.open("确认","说明","取消","确认",()->{}));modal.setHostActive(true);view.key(PanelWidthGestureTest.key(KeyEvent.Key.RIGHT));assertEquals(480,setting.value());
            dialog.reset();modal.sync();view.key(PanelWidthGestureTest.key(KeyEvent.Key.RIGHT));assertEquals(490,setting.value());
        }
    }
}
