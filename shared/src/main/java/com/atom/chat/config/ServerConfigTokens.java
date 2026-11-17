package com.atom.chat.config;

import com.atom.lib.design.atomchat.UiTokens;

/** Geometry for the command-only page, reusing the settings input and row proportions. */
public final class ServerConfigTokens {
    private ServerConfigTokens() {}
    public static final float FIELD_H=UiTokens.s(32); // bare-value: approved compact form field height
    public static final float NUMBER_W=UiTokens.s(80),UNIT_W=UiTokens.s(36); // bare-value: compact number and adjacent unit band
    public static final float EDIT_H=UiTokens.s(100); // bare-value: multiline editor viewport preserving complete text with inner scroll
    public static final float ACTION_H=UiTokens.s(30); // bare-value: existing compact settings action family
    public static final float ASSET_H=UiTokens.s(150),THUMB=UiTokens.s(54),IMAGE_H=UiTokens.s(300); // bare-value: media list and preview geometry
    public static final float RULE_H=UiTokens.s(1); // bare-value: existing settings grouped-row divider
    public static final float NUMBER_TEXT_MIN=UiTokens.s(100); // bare-value: readable compact numeric-row text budget before controls move below
    public static final float CONTROL_H = UiTokens.s(36); // bare-value: existing inline settings field height
    public static final float INPUT_PAD = UiTokens.s(10); // bare-value: existing settings field text inset
    public static final float CARET_WIDTH = UiTokens.s(1.5F); // bare-value: existing settings field caret stroke
    public static final float CARET_HALF_H = UiTokens.s(8); // bare-value: existing settings field caret half-height
    public static final float TITLE_LINE_H = UiTokens.s(22); // bare-value: settings title line box for compact wrapping
    public static final float TITLE_HALF_LINE = TITLE_LINE_H / 2;
    public static final float TEXT_LINE_H = UiTokens.s(24); // bare-value: input-font line box for phrase editing
    public static final float INPUT_FIRST_CENTER = UiTokens.s(18); // bare-value: center of the existing 36-high input
    public static final float SELECTION_H = UiTokens.s(20); // bare-value: selection band around the existing input font
    public static final float TEXT_CLIP_INSET = UiTokens.s(6); // bare-value: inset inside the existing 8-radius field
    public static final float STATUS_GAP = UiTokens.s(28); // bare-value: one secondary-label line plus card spacing
    public static final float RESCAN_CARD_H = UiTokens.s(90); // bare-value: existing settings label and 34-high action face
    public static final float RESCAN_CONTENT_H = UiTokens.s(102); // bare-value: rescan card plus settings row breathing room
    public static final float RESCAN_TOP = UiTokens.s(40); // bare-value: secondary label above the existing action face
    public static final float RESCAN_ACTION_H = UiTokens.s(34); // bare-value: existing compact settings action button height
    public static final float TOGGLE_ROW_H = UiTokens.s(50); // bare-value: existing switch footprint plus settings row insets
}
