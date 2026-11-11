package com.atom.chat.render;

import com.atom.lib.design.atomchat.UiTokens;

/** Host-owned shadow measurements; all consumers use already scaled drawing units. */
final class ShadowTokens {
    private static final float PANEL_SIGMA_LOGICAL=14, PANEL_DROP_LOGICAL=6;
    private static final float CONTACT_SIGMA_LOGICAL=3, CONTACT_DROP_LOGICAL=2;
    private static final float FAB_BLUR_LOGICAL=8, FAB_PAD_LOGICAL=36;
    static final float PANEL_SIGMA=UiTokens.s(PANEL_SIGMA_LOGICAL);
    static final float PANEL_DROP=UiTokens.s(PANEL_DROP_LOGICAL);
    static final float CONTACT_SIGMA=UiTokens.s(CONTACT_SIGMA_LOGICAL);
    static final float CONTACT_DROP=UiTokens.s(CONTACT_DROP_LOGICAL);
    static final float FAB_BLUR=UiTokens.s(FAB_BLUR_LOGICAL);
    static final float FAB_PAD=UiTokens.s(FAB_PAD_LOGICAL);
    private ShadowTokens() { }
}
