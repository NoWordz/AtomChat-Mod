// SPDX-License-Identifier: MIT
package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.ConfirmationDialog;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import com.atom.chat.ui.atomlib.*;
import com.atom.lib.component.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.layout.PresentationFrame;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.*;
import io.github.humbleui.skija.Canvas;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.*;

/** Yoga owns everything inside the host slot. AC owns settings, fonts and its modal. */
public final class AtomLibAppearanceSection implements AutoCloseable {
    public static final NodeId WIDTH=new NodeId("ac.appearance.width");
    public static final NodeId HALF_SCREEN=new NodeId("ac.appearance.half-screen");
    private final UiView view;
    private final Slider width;
    private final SettingsItem mode;
    private final AcModalAdapter modal;
    private UiLayout.Rect slot=new UiLayout.Rect(0,0,0,0),clip=slot;
    private AtomLibThemeAdapter services;
    private boolean active=true,lastMode,closed;
    private long frame;

    public AtomLibAppearanceSection(HostContext host,SettingsSlider widthSetting,SettingsItem mode,List<SettingsSlider> extras,
            List<SettingsSlider> transparency,Function<String,String> tr,ConfirmationDialog dialog,Runnable persistWidth) {
        this.mode=Objects.requireNonNull(mode);lastMode=mode.value();
        width=slider(widthSetting,new AtomLibSettingsBinding(widthSetting,AtomChatConfig::get,persistWidth));
        var half=new Toggle(HALF_SCREEN.value(),new ValueBinding<Boolean>() {
            public Boolean read(){return mode.value();}
            public void preview(Boolean value){AtomLibAppearanceSection.this.cancel();}
            public void commit(Boolean value){mode.set(value);lastMode=mode.value();host.feedback().run();}
            public void cancel(Boolean before){}
        });
        var children=new ArrayList<UiElement>();
        children.add(new Label("ac.appearance.heading",tr.apply("atomchat.settings.group.appearance.layout"),Label.Tone.TITLE));
        children.add(new Label("ac.appearance.description",tr.apply(mode.subtitleKey()),Label.Tone.SECONDARY));
        var layoutRows=new ArrayList<UiElement>();
        layoutRows.add(new Row("ac.appearance.mode-row",tr.apply(mode.titleKey()),null,half,Row.Placement.INLINE,false,false));
        layoutRows.add(sliderRow(widthSetting,width,tr,true));
        for(var extra:extras)layoutRows.add(sliderRow(extra,slider(extra,new AtomLibSettingsBinding(extra)),tr,true));
        children.add(new Section("ac.appearance.layout-group",Section.Kind.CARD_LIST,layoutRows));
        if(!transparency.isEmpty()){
            children.add(new Label("ac.appearance.transparency",tr.apply("atomchat.settings.group.appearance.transparency"),Label.Tone.TITLE));
            var transparencyRows=new ArrayList<UiElement>();
            boolean first=true;
            for(var extra:transparency){
                transparencyRows.add(sliderRow(extra,slider(extra,new AtomLibSettingsBinding(extra)),tr,!first));
                first=false;
            }
            children.add(new Section("ac.appearance.transparency-group",Section.Kind.CARD_LIST,transparencyRows));
        }
        var scroll=new Section("ac.appearance.scroll",Section.Kind.SCROLL,children);
        view=new UiView(host,new Section("ac.appearance.root",Section.Kind.GROUP,List.of(scroll)),new Viewport(0,0,1));
        modal=new AcModalAdapter(view,dialog::isActive);
    }
    public static AtomLibAppearanceSection create(ConfirmationDialog dialog) {
        var services=new AtomLibThemeAdapter(AtomLibThemeAdapter.bundledBody(),
            ()->com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON));
        try {
            var sliders=SettingsCatalog.sliders(SettingsSection.APPEARANCE);
            var width=sliders.stream().filter(s->s.id().equals("width")).findFirst().orElseThrow();
            var mode=SettingsCatalog.items(SettingsSection.APPEARANCE).stream().filter(s->s.id().equals("half_screen")).findFirst().orElseThrow();
            var result=new AtomLibAppearanceSection(services.host(),width,mode,layoutSliders(sliders),transparencySliders(sliders),
                KEY_TRANSLATOR,dialog,width::persist);
            result.services=services;return result;
        } catch(RuntimeException|Error failure){services.close();throw failure;}
    }
    private static final Function<String,String> KEY_TRANSLATOR=key->Text.translatable(key).getString();
    /** Layout sliders the pilot owns; kept as a named set so a migration cannot drop one silently. */
    static List<SettingsSlider> layoutSliders(List<SettingsSlider> sliders) {
        return sliders.stream().filter(s->s.id().equals("scale")||s.id().equals("contentscale")).toList();
    }
    /** Transparency sliders of the same chip; the old page always showed both. */
    static List<SettingsSlider> transparencySliders(List<SettingsSlider> sliders) {
        return sliders.stream().filter(s->s.id().equals("opacity")||s.id().equals("cardtint")).toList();
    }
    private static Slider slider(SettingsSlider setting,AtomLibSettingsBinding binding) {
        NodeId id=setting.id().equals("width")?WIDTH:new NodeId("ac.appearance."+setting.id());
        return new Slider(id,new SliderSpec(setting.min(),setting.max(),setting.step()),binding,false)
                .setMetrics(UiTokens.SLIDER_TRACK_H,UiTokens.SLIDER_KNOB);
    }
    /** Title left, value right, track below: the row language the old page used. */
    private static UiElement sliderRow(SettingsSlider setting,Slider control,Function<String,String> tr,boolean divider) {
        return new Row("ac.appearance."+setting.id()+".row",tr.apply(setting.titleKey()),setting::displayValue,control,
                Row.Placement.BELOW,false,divider);
    }
    /** Only the outer slot and clip come from the old shell. No track math is performed here. */
    public void setSlot(UiLayout.Rect slot,UiLayout.Rect clip) {
        this.slot=Objects.requireNonNull(slot);this.clip=Objects.requireNonNull(clip);
        view.setViewport(new Viewport(Math.max(0,slot.w()),Math.max(0,slot.h()),1));sync();
    }
    public void setActive(boolean active){this.active=active;if(!closed)modal.setHostActive(active);}
    public void paint(Canvas canvas,int accent,float dt) {
        if(services!=null)view.setTheme(AtomLibThemeAdapter.snapshot(accent));sync();
        int save=canvas.getSaveCount();canvas.save();
        try {
            canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(clip.x(),clip.y(),Math.max(0,clip.w()),Math.max(0,clip.h())));
            canvas.translate(slot.x(),slot.y());
            view.paint(canvas,new FrameTime(++frame,System.nanoTime(),Math.max(0,dt)/1000.0));
        } finally{canvas.restoreToCount(save);}
    }
    public boolean pointer(PointerEvent.Type type,float x,float y,int button) {
        sync();if(!active)return false;
        boolean captured=view.captureOwner()!=null;
        boolean inside=slot.contains(x,y)&&clip.contains(x,y);
        if(type==PointerEvent.Type.PRESS&&!inside)return false;
        if(type!=PointerEvent.Type.PRESS&&type!=PointerEvent.Type.CANCEL&&!captured)return false;
        var result=view.pointer(new PointerEvent(type,x-slot.x(),y-slot.y(),button));sync();
        return result==InputResult.CONSUMED || type==PointerEvent.Type.PRESS&&inside;
    }
    public boolean key(int key,int modifiers) {
        sync();if(!active||!visible())return false;
        var mapped=switch(key) {
            case 258->KeyEvent.Key.TAB;case 256->KeyEvent.Key.ESCAPE;case 263->KeyEvent.Key.LEFT;case 262->KeyEvent.Key.RIGHT;
            case 265->KeyEvent.Key.UP;case 264->KeyEvent.Key.DOWN;case 268->KeyEvent.Key.HOME;case 269->KeyEvent.Key.END;
            case 257,335->KeyEvent.Key.ENTER;case 32->KeyEvent.Key.SPACE;default->KeyEvent.Key.OTHER;
        };
        boolean handled=view.key(new KeyEvent(mapped,true,(modifiers&1)!=0,(modifiers&2)!=0,(modifiers&4)!=0))==InputResult.CONSUMED;sync();return handled;
    }
    public boolean wheel(float x,float y,float pixels) {
        sync();if(!active)return false;if(view.captureOwner()!=null)return true;
        if(!slot.contains(x,y)||!clip.contains(x,y))return false;
        return view.wheel(x-slot.x(),y-slot.y(),pixels)!=pixels;
    }
    public void openModal(Runnable show){modal.open(show);}
    public void cancel(){if(!closed)modal.cancelGesture();}
    public NodeId captureOwner(){return view.captureOwner();}
    public NodeId focused(){return view.focused();}
    public boolean isDraggingWidth(){return width.active();}
    public com.atom.lib.geometry.Rect widthTrack(){view.refresh();return width.track();}
    public PresentationFrame frame(){sync();return view.frame();}
    private boolean visible(){return slot.w()>0&&slot.h()>0&&clip.w()>0&&clip.h()>0&&slot.right()>clip.x()&&slot.x()<clip.right()&&slot.bottom()>clip.y()&&slot.y()<clip.bottom();}
    private void sync() {
        if(closed)return;
        if(mode.value()!=lastMode){cancel();lastMode=mode.value();}
        modal.sync();
    }
    @Override public void close(){
        if(closed)return;
        try{view.close();}finally{closed=true;if(services!=null)services.close();}
    }
}
