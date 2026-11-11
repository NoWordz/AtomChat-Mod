package com.atom.chat.ui;

import com.atom.lib.design.atomchat.UiTokens;

/** Wallpaper cards share the theme strip's geometry; both use these exact caption/clip measurements. */
public final class WallpaperTokens {
    private static final float CAPTION_LOGICAL=18, CLIP_PAD_LOGICAL=6, CLIP_EXTRA_LOGICAL=30;
    private static final float RADIUS_LOGICAL=10, SELECTION_LOGICAL=1.5f, LABEL_OFFSET_LOGICAL=11;
    private static final float DOT_RADIUS_LOGICAL=2.5F, DOT_Y_LOGICAL=13, DOT_RIGHT_LOGICAL=12;
    public static final float CAPTION_Y=UiTokens.s(CAPTION_LOGICAL);
    public static final float CLIP_PAD=UiTokens.s(CLIP_PAD_LOGICAL);
    public static final float CLIP_EXTRA=UiTokens.s(CLIP_EXTRA_LOGICAL);
    public static final float RADIUS=UiTokens.radius(RADIUS_LOGICAL);
    public static final float SELECTION_WIDTH=UiTokens.s(SELECTION_LOGICAL);
    public static final float LABEL_OFFSET=UiTokens.s(LABEL_OFFSET_LOGICAL);
    public static final float DOT_RADIUS=UiTokens.s(DOT_RADIUS_LOGICAL);
    public static final float DOT_Y=UiTokens.s(DOT_Y_LOGICAL);
    public static final float DOT_RIGHT=UiTokens.s(DOT_RIGHT_LOGICAL);
    private WallpaperTokens() { }
}
