package com.atom.chat.ui;

import com.atom.lib.design.atomchat.UiTokens;

/** Host layout limits in the same drawing units as the existing component tokens. */
public final class LayoutTokens {
    private static final float TOAST_MAX_LOGICAL=560, SETTINGS_TILE_MAX_LOGICAL=200;
    private static final float JUMP_SIZE_LOGICAL=38, JUMP_EDGE_INSET_LOGICAL=12;
    private static final float CHIP_COMPACT_SLOT_LOGICAL=64, CHIP_COMPACT_INSET_LOGICAL=3, CHIP_COMPACT_FONT_LOGICAL=10;
    private static final float CHIP_LABEL_PADDING_LOGICAL=10;
    public static final float TOAST_MAX_WIDTH = UiTokens.s(TOAST_MAX_LOGICAL);
    public static final float SETTINGS_TILE_MAX = UiTokens.s(SETTINGS_TILE_MAX_LOGICAL);
    public static final float JUMP_SIZE = UiTokens.s(JUMP_SIZE_LOGICAL);
    public static final float JUMP_EDGE_INSET = UiTokens.s(JUMP_EDGE_INSET_LOGICAL);
    public static final float CHIP_COMPACT_SLOT = UiTokens.s(CHIP_COMPACT_SLOT_LOGICAL);
    public static final float CHIP_COMPACT_INSET = UiTokens.s(CHIP_COMPACT_INSET_LOGICAL);
    public static final float CHIP_COMPACT_FONT = UiTokens.s(CHIP_COMPACT_FONT_LOGICAL);
    public static final float CHIP_LABEL_PADDING = UiTokens.s(CHIP_LABEL_PADDING_LOGICAL);
    // HUD units follow Minecraft's GUI scale and deliberately bypass the panel's scale.
    public static final float HUD_ICON_GUI=13, HUD_FONT_GUI=8, HUD_MARGIN_GUI=6;
    public static final float HUD_MESSAGE_GAP_GUI = 6F;
    public static final float HUD_GAP_GUI=3.2F, HUD_SLIDE_GUI=16, HUD_SHADOW_PAD_GUI=8;
    public static final float HUD_SHADOW_BLUR_GUI=1.5F, HUD_SHADOW_DROP_GUI=.8F;
    public static final int HUD_SHADOW_COLOR=0xB3000000;
    private LayoutTokens() { }
}
