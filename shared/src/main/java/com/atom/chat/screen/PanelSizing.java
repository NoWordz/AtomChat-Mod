package com.atom.chat.screen;

import com.atom.chat.config.AtomChatConfig;
import com.atom.lib.design.atomchat.UiTokens;

/** Panel dimensions before content zoom; the reference is the visible 600 by 1048 frame. */
public final class PanelSizing {
    public record Size(float width, float height) { }
    private static final float ASPECT = 600F / 1048F;
    private PanelSizing() { }

    private static float density(int height, float uiScale) {
        return Math.max(1F, height / 1080F) * Math.max(.5F, uiScale);
    }
    public static float maximumHeight(int height, AtomChatConfig config) {
        float margin = config.panelPositionCustom ? 0F : 32F * Math.max(.8F, Math.min(1.5F, config.contentScale));
        return Math.max(1F, (height / density(height, config.uiScale) - margin) / UiTokens.SCALE);
    }
    public static float maximumWidth(int width, int height, AtomChatConfig config) {
        float margin = config.panelPositionCustom ? 0F : 32F * Math.max(.8F, Math.min(1.5F, config.contentScale));
        return Math.max(1F, (width / density(height, config.uiScale) - margin) / UiTokens.SCALE);
    }
    public static Size automaticSize(int width, int height, AtomChatConfig config) {
        float availableHeight = maximumHeight(height, config);
        float availableWidth = Math.min(maximumWidth(width, height, config),
                Math.max(1F, (width / density(height, config.uiScale) - UiTokens.PANEL_ANCHOR_X * 2F) / UiTokens.SCALE));
        float fittedHeight = Math.min(availableHeight, availableWidth / ASPECT);
        return new Size(fittedHeight * ASPECT, fittedHeight);
    }
    public static void materialize(AtomChatConfig config, int width, int height) {
        if (!config.panelWidthAutomatic && !config.panelHeightAutomatic) return;
        Size size = automaticSize(width, height, config);
        if (config.panelWidthAutomatic) config.panelWidth = size.width();
        if (config.panelHeightAutomatic) config.panelHeight = size.height();
        config.panelWidthAutomatic = false;
        config.panelHeightAutomatic = false;
    }
}
