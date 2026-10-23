package com.atom.chat.settings;

import com.atom.chat.AtomChat;
import com.atom.chat.avatar.AvatarImage;
import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.banner.BannerImage;
import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.banner.BannerStore;
import com.atom.chat.chat.TeleportCommands;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.NativeDialogBridge;
import com.atom.chat.image.OwnPlayerAvatarSource;
import com.atom.chat.mixin.MouseHandlerAccessor;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import com.atom.chat.util.FilePicker;
import com.atom.chat.wallpaper.WallpaperImage;
import com.atom.chat.wallpaper.WallpaperStore;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * The settings screen's actions (D15a): the seven action cards, the three
 * native pickers behind them, and the crop the picked file is framed with
 * before it reaches a store.
 *
 * <p>Three things in here are contracts, not style:</p>
 * <ul>
 *   <li><b>the pickers release the press before opening a dialog.</b> The
 *       native AWT dialog takes OS focus; the key and mouse button that opened
 *       it are still down as far as the game knows, so every picker clears that
 *       state first (same two lines as {@link NativeDialogBridge#refocusWindow()},
 *       which runs when the dialog closes);</li>
 *   <li><b>every card answers.</b> Each of the seven action ids ends in exactly
 *       one {@code feedback} (the clear actions report what the store said, so a
 *       failure cannot be shown as a success) — with the store mutations
 *       happening on the calling thread and the pickers on a worker;</li>
 *   <li><b>the crop follows the surface it will be shown on.</b> Wallpaper is
 *       framed at the whole panel's aspect, the banner at the profile card's,
 *       and the avatar square (circle mode ignores the ratio). The three
 *       geometries live in {@link #wallpaperFrame}, {@link #avatarFrame} and
 *       {@link #bannerFrame} so a change to one is a change to a pinned
 *       number.</li>
 * </ul>
 *
 * <p>Ports on {@link Host}: matters of this shell and of six other domains — the
 * avatar store the shell builds from its platform's config directory (D18;
 * <b>this class never resolves a directory itself</b>), the cropper and the
 * settings page it registers with (D9/D16), the client handle (D25, D3's
 * precedent: never {@code Minecraft.getInstance()}), D14's dialog bridge, D7's
 * action feedback, D4's history windows and D10's two layouts. Everything else
 * it touches is shared or same-layer.</p>
 *
 * <p>Both mappings: this file is the official-name copy and
 * {@code platforms/1.21.1-fabric/…/SettingsActions.java} is its yarn twin — the
 * two bodies differ only in Minecraft names (the client type, the mouse handler
 * field and the key-binding class), never in what they do.</p>
 */
public final class SettingsActions {
    /**
     * What the screen hands over: one object it built (the avatar store), three
     * objects it owns and this domain drives, and five facts owned by other
     * domains. Nothing here is D15 state.
     */
    public interface Host {
        /** D18's store, already pointed at this platform's config directory by the shell. */
        AvatarStore avatarStore();

        /** D9's crop overlay: this domain opens it, the shell renders and feeds it. */
        ImageCropper imageCropper();

        /** D16's section page: this domain registers the action handler on it. */
        SettingsSectionPage sectionPage();

        /**
         * The screen's own client handle — deliberately not
         * {@code Minecraft.getInstance()} (D3's precedent): the screen's field
         * is null before {@code init()}, and the pickers keep the exact
         * behaviour they had when they read that same field.
         */
        Minecraft client();

        /** D14: auto-iconify suspension around a dialog, and the focus hand-back after it. */
        NativeDialogBridge dialogs();

        /** D7: the action acknowledgement, by key. */
        void feedback(String key, boolean success);

        /** D4: drops the per-window history views after the current session is cleared. */
        void resetHistoryWindows();

        /** D10: the detail page's layout — the wallpaper's crop aspect. */
        UiLayout layout();

        /** D10: the list column's layout — the banner card's crop aspect. */
        UiLayout listLayout();

        /** Opens the screen-owned visual HUD layout editor. */
        default void openNotificationHudEditor() { }
    }

    private final Host host;

    public SettingsActions(Host host) {
        this.host = host;
        // Action cards (pick/clear wallpaper) need the shell's file picker,
        // which lives here rather than in the settings package.
        host.sectionPage().setActionHandler(this::handleSettingsAction);
        // The custom-avatar source needs the store instance the shell created.
        OwnPlayerAvatarSource.attach(host.avatarStore());
    }

    /**
     * The cropper's confirm/cancel half: the three targets it can be opened for,
     * and what each one does with the cropped PNG. Reads the store and the client
     * it is handed — the same two the shell's anonymous callback captured.
     */
    public static ImageCropper.Callback cropperCallback(AvatarStore avatarStore, Minecraft client) {
        return new ImageCropper.Callback() {
            @Override
            public void onConfirm(String targetId, byte[] pngBytes) {
                if ("avatar".equals(targetId)) {
                    if (avatarStore.setPng(pngBytes)) {
                        AvatarImage.release();
                        com.atom.chat.profile.ProfileSync.imageSelected(com.atom.chat.profile.ProfileField.AVATAR);
                        // Companion push: skipped only when the server has no
                        // companion channels at all (negotiation, not a timeout).
                        if (client != null && client.player != null) {
                            if (com.atom.chat.profile.ProfileSync.available()) {
                                com.atom.chat.profile.ProfileSync.imageEdited(com.atom.chat.profile.ProfileField.AVATAR, avatarStore.current());
                            } else {
                                com.atom.chat.net.AvatarCompanionClient.uploadOwnAvatar(client.player.getUUID(), pngBytes);
                            }
                        }
                    }
                } else if ("wallpaper".equals(targetId)) {
                    if (WallpaperStore.setPng(pngBytes)) {
                        WallpaperImage.release();
                    }
                } else if ("banner".equals(targetId)) {
                    if (BannerStore.setPng(pngBytes)) {
                        BannerImage.release();
                        com.atom.chat.profile.ProfileSync.imageEdited(com.atom.chat.profile.ProfileField.BANNER, BannerStore.current());
                    }
                }
            }

            @Override
            public void onCancel(String targetId) {
                // Nothing to clean up; the picked file is simply dropped.
            }
        };
    }

    /**
     * Wallpaper is framed at the whole panel's aspect, so what you frame is what
     * the panel shows (cover-fit has nothing left to do).
     */
    static float[] wallpaperFrame(float panelW, float panelH) {
        return new float[]{panelW, panelH};
    }

    /**
     * The avatar is framed square — circle mode, where the cropper ignores the
     * ratio arguments; the zeros are its "no ratio" values.
     */
    static float[] avatarFrame() {
        return new float[]{0.0F, 0.0F};
    }

    /**
     * The banner is framed at the profile card's aspect, floored at one pixel so
     * a degenerate layout cannot ask the cropper for a zero-width frame.
     */
    static float[] bannerFrame(float listW) {
        return new float[]{Math.max(1.0F, listW - UiTokens.ROW_CLIP_INSET * 2.0F),
                UiTokens.PROFILE_BANNER_H};
    }

    /** Settings action cards: the wallpaper pair, the theme presets and the
     *  teleport mode cycle. */
    public void handleSettingsAction(String actionId) {
        if (SettingsSectionPage.ACTION_THEME_PRESET_SAVE.equals(actionId)) {
            saveThemePreset();
        } else if (actionId != null && actionId.startsWith(SettingsSectionPage.ACTION_THEME_PRESET_APPLY)) {
            applyThemePreset(actionId.substring(SettingsSectionPage.ACTION_THEME_PRESET_APPLY.length()));
        } else if (actionId != null && actionId.startsWith(SettingsSectionPage.ACTION_THEME_PRESET_DELETE)) {
            deleteThemePreset(actionId.substring(
                    SettingsSectionPage.ACTION_THEME_PRESET_DELETE.length()));
        } else if ("wallpaper_pick".equals(actionId)) {
            pickWallpaperFile();
        } else if ("wallpaper_clear".equals(actionId)) {
            boolean removed = WallpaperStore.clear();
            WallpaperImage.release();
            host.feedback(removed ? "atomchat.feedback.wallpaperCleared" : "atomchat.feedback.wallpaperClearFailed",
                    removed);
        } else if ("test_sound".equals(actionId)) {
            com.atom.chat.notification.NotificationController.playTestSound();
        } else if ("hud_edit".equals(actionId)) {
            host.openNotificationHudEditor();
        } else if ("teleport_mode".equals(actionId) || "teleport_mode:prev".equals(actionId)) {
            AtomChatConfig cfg = AtomChatConfig.get();
            cfg.teleportCommandMode = SettingsStepper.teleport(cfg.teleportCommandMode,
                    actionId.endsWith(":prev") ? -1 : 1);
            TeleportCommands.reset();
            AtomChatConfig.save(cfg);
        } else if (actionId != null && actionId.startsWith(SettingsSectionPage.ACTION_BUBBLE_STYLE)) {
            String requested = actionId.substring(SettingsSectionPage.ACTION_BUBBLE_STYLE.length());
            for (AtomChatConfig.BubbleStyle style : AtomChatConfig.BubbleStyle.values()) {
                if (style.name().equals(requested)) {
                    AtomChatConfig cfg = AtomChatConfig.get();
                    cfg.bubbleStyle = style;
                    AtomChatConfig.save(cfg);
                    break;
                }
            }
        } else if ("avatar_style".equals(actionId) || "avatar_style:prev".equals(actionId)) {
            AtomChatConfig cfg = AtomChatConfig.get();
            cfg.avatarStyle = cfg.effectiveAvatarStyle() == AtomChatConfig.AvatarStyle.E33CHAT
                    ? AtomChatConfig.AvatarStyle.ATOMCHAT : AtomChatConfig.AvatarStyle.E33CHAT;
            AtomChatConfig.save(cfg);
        } else if ("page_nav_style".equals(actionId) || "page_nav_style:prev".equals(actionId)) {
            // The two-option cycle wraps in either direction. Takes effect on the next nav.
            AtomChatConfig cfg = AtomChatConfig.get();
            cfg.pageNavStyle = cfg.pageNavStyle == AtomChatConfig.PageNavStyle.ZOOM
                    ? AtomChatConfig.PageNavStyle.SLIDE
                    : AtomChatConfig.PageNavStyle.ZOOM;
            AtomChatConfig.save(cfg);
        } else if ("hud_position".equals(actionId)) {
            AtomChatConfig cfg = AtomChatConfig.get();
            var position = cfg.notificationHudPosition;
            cfg.notificationHudPosition = (position == null
                    ? com.atom.chat.notification.NotificationHudLayout.Anchor.TOP_CENTER : position).next();
            AtomChatConfig.save(cfg);
        } else if ("history_clear".equals(actionId)) {
            // Wipes the current session (and the world's saved file when
            // persistence is on). Generation bumped inside: a pending auto-save
            // cannot resurrect what was just deleted. The memory wipe is
            // synchronous and is exactly what the player sees, so the toast
            // reports that; the background file delete only logs on failure.
            com.atom.chat.history.ChatHistory.clearCurrent();
            host.resetHistoryWindows();
            host.feedback("atomchat.feedback.historyCleared", true);
        } else if ("cache_clear".equals(actionId)) {
            // Image downloads are safe to drop; they are re-fetched on demand.
            // A directory that could not be enumerated reports failure rather
            // than a success the player cannot trust.
            int deleted = ImageLoader.get().clearDiskCache();
            host.feedback(deleted < 0 ? "atomchat.feedback.cacheClearFailed" : "atomchat.feedback.cacheCleared",
                    deleted >= 0);
        }
    }

    private void saveThemePreset() {
        boolean ok = AppearancePresetStore.get()
                .saveCurrent(AtomChatConfig.get(), WallpaperStore.current()).ok();
        if (ok) {
            host.sectionPage().requestRevealLatestPreset();
        }
        host.feedback(ok ? "atomchat.feedback.presetSaved" : "atomchat.feedback.presetFailed", ok);
    }

    private void applyThemePreset(String id) {
        boolean ok = AppearancePresetStore.get().apply(id, AtomChatConfig.get()).ok();
        if (ok) {
            // The wallpaper store copies to the same fixed file name, so path
            // equality alone cannot invalidate the cached bitmap.
            WallpaperImage.release();
        }
        host.feedback(ok ? "atomchat.feedback.presetApplied" : "atomchat.feedback.presetFailed", ok);
    }

    private void deleteThemePreset(String id) {
        boolean ok = AppearancePresetStore.get().delete(id).ok();
        host.feedback(ok ? "atomchat.feedback.presetDeleted" : "atomchat.feedback.presetFailed", ok);
    }

    /** Picks a wallpaper image off-thread; the store mutation returns to render. */
    public void pickWallpaperFile() {
        KeyMapping.releaseAll();
        if (host.client().mouseHandler != null) {
            ((MouseHandlerAccessor) host.client().mouseHandler).atomchat$setActiveButton(0);
        }
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(host.dialogs()::suppressAutoIconify,
                    host.dialogs()::restoreAutoIconify, WallpaperStore::isSupportedName);
            host.dialogs().refocusWindow();
            if (file == null) {
                return;
            }
            host.client().execute(() -> {
                UiLayout l = host.layout();
                float[] frame = wallpaperFrame(l.rect().w(), l.rect().h());
                host.imageCropper().open(file, false, frame[0], frame[1], "wallpaper");
            });
        }, "AtomChat-WallpaperPicker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Picks a custom avatar off-thread; the cropper opens on the render side. */
    public void pickAvatarFile() {
        KeyMapping.releaseAll();
        if (host.client().mouseHandler != null) {
            ((MouseHandlerAccessor) host.client().mouseHandler).atomchat$setActiveButton(0);
        }
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(host.dialogs()::suppressAutoIconify,
                    host.dialogs()::restoreAutoIconify, AvatarStore::isSupportedName);
            host.dialogs().refocusWindow();
            if (file == null) {
                return;
            }
            host.client().execute(() -> {
                float[] frame = avatarFrame();
                host.imageCropper().open(file, true, frame[0], frame[1], "avatar");
            });
        }, "AtomChat-AvatarPicker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Picks a profile banner off-thread and crops it to the banner card's
     * aspect before storing; the crop result lands via
     * {@link ImageCropper.Callback#onConfirm} on the render thread, like every
     * other store write.
     */
    public void pickBannerFile() {
        KeyMapping.releaseAll();
        if (host.client().mouseHandler != null) {
            ((MouseHandlerAccessor) host.client().mouseHandler).atomchat$setActiveButton(0);
        }
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(host.dialogs()::suppressAutoIconify,
                    host.dialogs()::restoreAutoIconify, BannerStore::isSupportedName);
            host.dialogs().refocusWindow();
            if (file == null) {
                return;
            }
            host.client().execute(() -> {
                UiLayout l = host.listLayout();
                float[] frame = bannerFrame(l.list.w());
                host.imageCropper().open(file, false, frame[0], frame[1], "banner");
            });
        }, "AtomChat-BannerPicker");
        worker.setDaemon(true);
        worker.start();
    }
}
