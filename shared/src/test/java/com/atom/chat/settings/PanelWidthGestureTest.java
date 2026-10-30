package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.atomlib.AtomLibSettingsBinding;
import com.atom.lib.component.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.view.UiView;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** No copied slider formula: every event is delivered to the real Lib Slider/UiView. */
class PanelWidthGestureTest extends AtomLibSettingsTestSupport {
    @Test void previewRelayoutsTheRealTrackAndReleaseWritesExactlyOnce() {
        var setting=catalogWidth();var saves=new AtomicInteger();
        var slider=new Slider(new NodeId("ac.width"),new SliderSpec(setting.min(),setting.max(),setting.step()),
            new AtomLibSettingsBinding(setting,AtomChatConfig::get,()->{saves.incrementAndGet();setting.persist();}),false);
        try(var view=view(slider)) {
            view.refresh();var first=slider.track();
            pointer(view,PointerEvent.Type.PRESS,first.x()+first.width()/2,first.y());
            assertNotEquals(480,AtomChatConfig.get().activePanelWidth());assertEquals(0,saves.get());
            resize(view);var second=slider.track();assertNotEquals(first,second);
            pointer(view,PointerEvent.Type.MOVE,second.right()-1,second.y());
            resize(view);assertNotEquals(second,slider.track());assertEquals(0,saves.get());
            var finalTrack=slider.track();pointer(view,PointerEvent.Type.RELEASE,finalTrack.right()-1,finalTrack.y());
            pointer(view,PointerEvent.Type.RELEASE,finalTrack.right()-1,finalTrack.y());
            assertEquals(1,saves.get());assertNull(view.captureOwner());
            float committed=setting.value();AtomChatConfig.reload();assertEquals(committed,setting.value());
        }
    }

    @Test void pointerOutsideThePanelStillReleasesAndCancelRestoresWithoutWriting() {
        var setting=catalogWidth();var saves=new AtomicInteger();
        var slider=new Slider(new NodeId("ac.width"),new SliderSpec(setting.min(),setting.max(),setting.step()),new AtomLibSettingsBinding(setting,AtomChatConfig::get,saves::incrementAndGet),false);
        try(var view=view(slider)) {
            view.refresh();var track=slider.track();pointer(view,PointerEvent.Type.PRESS,track.right()-1,track.y());
            pointer(view,PointerEvent.Type.MOVE,10000,10000);assertEquals(600,setting.value());
            pointer(view,PointerEvent.Type.CANCEL,0,0);assertEquals(480,setting.value());assertEquals(0,saves.get());
            pointer(view,PointerEvent.Type.PRESS,track.x(),track.y());pointer(view,PointerEvent.Type.RELEASE,-10000,10000);
            assertEquals(400,setting.value());assertEquals(1,saves.get());assertNull(view.captureOwner());
        }
    }

    @Test void cancellingAfterAnExternalModeSwitchRollsBackOnlyTheOriginalRememberedWidth() {
        var setting=catalogWidth();var saves=new AtomicInteger();var cfg=AtomChatConfig.get();cfg.halfScreenWidth=570;
        var slider=new Slider(new NodeId("ac.width"),new SliderSpec(setting.min(),setting.max(),setting.step()),new AtomLibSettingsBinding(setting,AtomChatConfig::get,saves::incrementAndGet),false);
        try(var view=view(slider)) {
            view.refresh();var track=slider.track();pointer(view,PointerEvent.Type.PRESS,track.right(),track.y());
            cfg.halfScreenMode=true;pointer(view,PointerEvent.Type.CANCEL,0,0);
            assertEquals(480,cfg.panelWidth);assertEquals(570,cfg.halfScreenWidth);assertEquals(0,saves.get());
            view.refresh();track=slider.track();pointer(view,PointerEvent.Type.PRESS,track.x(),track.y());pointer(view,PointerEvent.Type.RELEASE,track.x(),track.y());
            assertEquals(400,cfg.halfScreenWidth);assertEquals(480,cfg.panelWidth);assertEquals(1,saves.get());
        }
    }

    @Test void keyboardUsesTheRealControlAndCommitsEachCompleteNudge() {
        var setting=catalogWidth();var saves=new AtomicInteger();
        var slider=new Slider(new NodeId("ac.width"),new SliderSpec(setting.min(),setting.max(),setting.step()),new AtomLibSettingsBinding(setting,AtomChatConfig::get,saves::incrementAndGet),false);
        try(var view=view(slider)) {
            view.key(key(KeyEvent.Key.TAB));view.key(key(KeyEvent.Key.RIGHT));assertEquals(490,setting.value());assertEquals(1,saves.get());
            view.key(key(KeyEvent.Key.HOME));assertEquals(400,setting.value());assertEquals(2,saves.get());
            view.key(key(KeyEvent.Key.END));assertEquals(600,setting.value());assertEquals(3,saves.get());
        }
    }

    private UiView view(Slider slider) {
        return new UiView(host,new Section("ac.layout",Section.Kind.GROUP,List.of(new SettingRow("ac.row","面板宽度","面板贴边显示；手机模式与半屏模式分别记忆宽度，切回时恢复。",slider))),new Viewport(480,800,1));
    }
    private void resize(UiView view){view.setViewport(new Viewport(AtomChatConfig.get().activePanelWidth(),800,1));view.refresh();}
    static void pointer(UiView view,PointerEvent.Type type,float x,float y){view.pointer(new PointerEvent(type,x,y,0));}
    static KeyEvent key(KeyEvent.Key key){return new KeyEvent(key,true,false,false,false);}
}
