package com.atom.chat.render;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.onboarding.TutorialSandbox;
import com.atom.chat.platform.Platform;

import java.util.Objects;

/** Resolves the same client choice for the chat panel and gameplay HUD. */
public final class ChatAppearance {
    private static final AppearanceUi.Resolver RESOLVER = new AppearanceUi.Resolver();
    private static final AppearanceUi.Selection BUILT_IN = new AppearanceUi.Selection(null, null, null, null);
    private static volatile AppearanceUi.Selection selection = BUILT_IN;

    private ChatAppearance() {}

    public static AppearanceUi.Resolved current() {
        return current(Platform.isInstalled() ? AtomChatConfig.get() : AtomChatConfig.DEFAULT);
    }

    public static AppearanceUi.Resolved current(AtomChatConfig config) {
        if (TutorialSandbox.active()) return RESOLVER.resolve(BUILT_IN);
        var ids = selection;
        if (!Objects.equals(ids.bubbleId(), config.bubbleAppearanceId)
                || !Objects.equals(ids.panelOutlineId(), config.panelOutlineAppearanceId)
                || !Objects.equals(ids.avatarFrameId(), config.avatarFrameAppearanceId)
                || !Objects.equals(ids.dynamicWallpaperId(), config.dynamicWallpaperAppearanceId)) {
            ids = new AppearanceUi.Selection(config.bubbleAppearanceId, config.panelOutlineAppearanceId,
                    config.avatarFrameAppearanceId, config.dynamicWallpaperAppearanceId);
            selection = ids;
        }
        return RESOLVER.resolve(ids);
    }
}
