// SPDX-License-Identifier: MIT
package com.atom.chat.ui.atomlib;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.UiTokens;
import com.atom.lib.AtomLibRuntime;
import com.atom.lib.RuntimeHandle;
import com.atom.lib.font.*;
import com.atom.lib.text.TextService;
import com.atom.lib.style.*;
import com.atom.lib.view.HostContext;
import java.io.IOException;

/** AC supplies font bytes and snapshots its existing tokens; Lib owns no AC theme state. */
public final class AtomLibThemeAdapter implements AutoCloseable {
    private static final FontId BODY=new FontId("atomchat.settings.body");
    private final RuntimeHandle runtime;
    private final FontRegistry fonts;
    private final TextService text;
    private final Runnable feedback;
    public AtomLibThemeAdapter(FontSource body,Runnable feedback) {
        runtime=AtomLibRuntime.open("atomchat-settings");this.feedback=java.util.Objects.requireNonNull(feedback);
        try{fonts=new FontRegistry(runtime);fonts.register(BODY,body);text=new TextService(fonts);}
        catch(RuntimeException|Error failure){runtime.close();throw failure;}
    }
    /** The resource remains in AC. This is the same host resource selected by FontManager. */
    public static FontSource bundledBody() {
        return () -> {
            try(var in=AtomLibThemeAdapter.class.getResourceAsStream("/assets/atomchat/font/bundled.otf")) {
                if(in==null)throw new IOException("AC settings font resource is missing");
                return in.readAllBytes();
            }
        };
    }
    public HostContext host(){return new HostContext(fonts,BODY,text,snapshot(AtomChatConfig.get().accentColor),feedback);}
    public static ThemeSnapshot snapshot(int accent) {
        var cfg=AtomChatConfig.get();
        return new ThemeSnapshot(new Palette(cfg.panelBgColor,UiTokens.cardFill(),UiTokens.trackRest(),accent,UiTokens.onAccent(accent),
            cfg.textPrimaryColor,cfg.textSecondaryColor,UiTokens.dangerColor()),
            new TypeScale(UiTokens.SETTINGS_TILE_TITLE,UiTokens.FONT_TITLE,UiTokens.SETTINGS_TILE_SUB),
            new SpacingScale(UiTokens.SETTINGS_COMPACT_GAP,UiTokens.SETTINGS_ROW_GAP,UiTokens.SETTINGS_ROW_PAD,UiTokens.SETTINGS_VALUE_BAND),
            new RadiusScale(UiTokens.settingsRowRadius(),UiTokens.buttonRadius()),new MotionPolicy(!cfg.animationEnabled));
    }
    @Override public void close(){try{text.close();}finally{runtime.close();}}
}
