package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;

/** The message lane shared by wrapping, image sizing, reactions and hit geometry. */
public final class MessageGeometry {
    private MessageGeometry() { }

    public static float avatarInset() {
        float hat = AtomChatConfig.get().avatarStyle == AtomChatConfig.AvatarStyle.E33CHAT
                ? UiTokens.AVATAR_SIZE * UiTokens.E33CHAT_HAT_OUTSET_RATIO : 0F;
        return Math.max(UiTokens.MESSAGE_AVATAR_RIM_INSET, hat);
    }

    public static float maxBubbleWidth(float rowWidth) {
        return maxBubbleWidth(rowWidth, 0F);
    }

    public static float maxBubbleWidth(float rowWidth, float minimumOpposite) {
        float avatarLane = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP + avatarInset();
        float opposite = Math.max(minimumOpposite, Math.min(UiTokens.MESSAGE_OPPOSITE_MAX_PAD,
                Math.max(0F, rowWidth) * UiTokens.MESSAGE_OPPOSITE_FRACTION));
        return Math.max(0F, Math.min(rowWidth * UiTokens.MESSAGE_MAX_WIDTH_FRACTION,
                rowWidth - avatarLane - opposite));
    }

    public static float textWidth(float rowWidth) {
        return Math.max(1F, maxBubbleWidth(rowWidth) - UiTokens.BUBBLE_PAD * 2F);
    }
}
