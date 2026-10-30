// SPDX-License-Identifier: MIT
package com.atom.chat.ui.atomlib;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.settings.SettingsSlider;
import com.atom.lib.component.ValueBinding;
import java.util.function.Supplier;
import java.util.Objects;

/** AC owns persistence and the two remembered widths; Lib owns the gesture. */
public final class AtomLibSettingsBinding implements ValueBinding<Float> {
    private final SettingsSlider slider;
    private final Supplier<AtomChatConfig> config;
    private final Runnable persist;
    private AtomChatConfig owner;
    private boolean halfScreen,active;
    public AtomLibSettingsBinding(SettingsSlider slider,Supplier<AtomChatConfig> config,Runnable persist) {
        this.slider=Objects.requireNonNull(slider);this.config=Objects.requireNonNull(config);this.persist=Objects.requireNonNull(persist);
    }
    public AtomLibSettingsBinding(SettingsSlider slider) { this(slider,AtomChatConfig::get,slider::persist); }
    @Override public Float read() { return slider.value(); }
    @Override public void preview(Float value) {
        if(!active){owner=config.get();halfScreen=owner.halfScreenMode;active=true;}
        apply(value);
    }
    @Override public void commit(Float value) {
        preview(value);
        // Retain the original mode until persistence succeeds: Slider rolls
        // back through cancel(before) if a host save throws.
        persist.run();active=false;owner=null;
    }
    @Override public void cancel(Float before) {
        if(!active)return;
        try{apply(before);}finally{active=false;owner=null;}
    }
    private void apply(float value) {
        if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite setting value");
        value=slider.clamp(value);
        if("width".equals(slider.id())) {
            if(halfScreen)owner.halfScreenWidth=value;else owner.panelWidth=value;
        } else slider.apply(value);
    }
}
