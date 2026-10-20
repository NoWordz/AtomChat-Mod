package com.atom.chat.settings;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.banner.BannerStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.NativeDialogBridge;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import com.atom.chat.wallpaper.WallpaperStore;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SettingsActions}' offline half: which store each cropper target writes,
 * what each of the seven action cards answers, and the three crop geometries.
 *
 * <p><b>Scope, stated up front.</b> The wallpaper and banner stores are real
 * objects pointed at a temp directory, so the branches that write them are
 * asserted end to end. NOT covered here: the three native pickers' dialogs and
 * their crop being handed to a live cropper (they need a running game — the
 * picker's press-clear reads {@code client.mouseHandler} and the dialog is real
 * AWT), the avatar branch's companion push (needs a non-null client with a
 * player), and the two registrations the constructor performs (the section
 * page's action handler and the custom-avatar source attach). Those stay on the
 * real-machine probe list: pick a wallpaper, an avatar and a banner, confirm each
 * crop, and watch the avatar reach the server relay.</p>
 *
 * <p>The host stub answers {@code client()} with null on purpose: it is what a
 * headless test can offer, and it is how {@link #theWallpaperCardRoutesIntoThePicker()}
 * proves the routing without ever opening a dialog.</p>
 */
class SettingsActionsTest {
    @BeforeAll
    static void installPlatform(@TempDir Path dir) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return dir; }
            public Path gameDir() { return dir; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @TempDir
    Path tmp;

    /** Records what the domain asked of the shell, in order. */
    private static final class StubHost implements SettingsActions.Host {
        final List<String> calls = new ArrayList<>();
        final AvatarStore avatarStore;
        final ImageCropper cropper;
        final SettingsSectionPage sectionPage = new SettingsSectionPage();
        boolean historyWindowsReset;

        StubHost(Path avatarDir) {
            this.avatarStore = new AvatarStore(avatarDir);
            this.cropper = new ImageCropper(SettingsActions.cropperCallback(avatarStore, null));
        }

        @Override
        public AvatarStore avatarStore() {
            return avatarStore;
        }

        @Override
        public ImageCropper imageCropper() {
            return cropper;
        }

        @Override
        public SettingsSectionPage sectionPage() {
            return sectionPage;
        }

        @Override
        public Minecraft client() {
            return null;
        }

        @Override
        public NativeDialogBridge dialogs() {
            throw new AssertionError("no dialog bridge in an offline test");
        }

        @Override
        public void feedback(String key, boolean success) {
            calls.add("feedback:" + key + ":" + success);
        }

        @Override
        public void resetHistoryWindows() {
            historyWindowsReset = true;
        }

        @Override
        public UiLayout layout() {
            throw new AssertionError("no panel layout in an offline test");
        }

        @Override
        public UiLayout listLayout() {
            throw new AssertionError("no list layout in an offline test");
        }
    }

    @AfterEach
    void releaseStores() {
        // Both stores now guard a null directory in clear(): the asymmetry (the
        // banner store walked straight into Files.isDirectory(null)) was fixed in
        // the phase-3 defect batch, so neither call needs a "was it initialised"
        // flag. Neither store's file is touched when the directory is null.
        WallpaperStore.clear();
        BannerStore.clear();
        // Hand the statics back exactly as they were found: uninitialised (both
        // refresh() implementations guard a null directory).
        WallpaperStore.init(null);
        BannerStore.init(null);
    }

    /** A real PNG, because the stores only care that the bytes are non-empty. */
    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void theCropperCallbackRoutesEachTargetToItsOwnStore() throws Exception {
        WallpaperStore.init(tmp.resolve("wallpaper"));
        BannerStore.init(tmp.resolve("banner"));
        AvatarStore avatar = new AvatarStore(tmp.resolve("avatar"));
        ImageCropper.Callback callback = SettingsActions.cropperCallback(avatar, null);

        callback.onConfirm("wallpaper", png());
        assertTrue(WallpaperStore.isSet(), "wallpaper target writes the wallpaper store");
        assertFalse(BannerStore.isSet(), "and nothing else");
        assertFalse(avatar.isSet());

        callback.onConfirm("banner", png());
        assertTrue(BannerStore.isSet(), "banner target writes the banner store");

        // Null bytes are rejected by every store, so this only proves the target
        // is routed to the avatar branch — the success path needs a live client
        // for the companion push, and is a probe.
        callback.onConfirm("avatar", null);
        assertFalse(avatar.isSet());

        callback.onConfirm("something-else", png());
        assertFalse(avatar.isSet(), "an unknown target writes nothing");

        callback.onCancel("wallpaper");
    }

    @Test
    void theWallpaperClearCardReportsWhatTheStoreSaid() throws Exception {
        WallpaperStore.init(tmp.resolve("wallpaper"));
        assertTrue(WallpaperStore.setPng(png()));
        StubHost host = new StubHost(tmp.resolve("avatar"));
        SettingsActions actions = new SettingsActions(host);

        actions.handleSettingsAction("wallpaper_clear");
        assertEquals(List.of("feedback:atomchat.feedback.wallpaperCleared:true"), host.calls);

        actions.handleSettingsAction("wallpaper_clear");
        assertEquals("feedback:atomchat.feedback.wallpaperClearFailed:false", host.calls.get(1),
                "a store that had nothing to remove must not be reported as a success");
    }

    @Test
    void theHistoryCardAnswersAndDropsTheHistoryWindows() throws Exception {
        StubHost host = new StubHost(tmp.resolve("avatar"));
        new SettingsActions(host).handleSettingsAction("history_clear");

        assertEquals(List.of("feedback:atomchat.feedback.historyCleared:true"), host.calls);
        assertTrue(host.historyWindowsReset, "the per-window views are dropped with the session");
    }

    @Test
    void theCacheCardReportsSuccessForAnEmptyCache() throws Exception {
        StubHost host = new StubHost(tmp.resolve("avatar"));
        new SettingsActions(host).handleSettingsAction("cache_clear");

        assertEquals(List.of("feedback:atomchat.feedback.cacheCleared:true"), host.calls);
    }

    @Test
    void theTeleportCardCyclesAutoTpTpaAndComesBack() throws Exception {
        AtomChatConfig cfg = AtomChatConfig.get();
        String original = cfg.teleportCommandMode;
        StubHost host = new StubHost(tmp.resolve("avatar"));
        SettingsActions actions = new SettingsActions(host);
        try {
            cfg.teleportCommandMode = "auto";
            actions.handleSettingsAction("teleport_mode");
            assertEquals("tp", AtomChatConfig.get().teleportCommandMode);
            actions.handleSettingsAction("teleport_mode");
            assertEquals("tpa", AtomChatConfig.get().teleportCommandMode);
            actions.handleSettingsAction("teleport_mode");
            assertEquals("auto", AtomChatConfig.get().teleportCommandMode, "the cycle returns to auto");
            assertEquals(List.of(), host.calls, "the cycle answers nothing");
        } finally {
            AtomChatConfig.get().teleportCommandMode = original;
        }
    }

    @Test
    void bubbleStyleDirectSelectionsPersist() throws Exception {
        AtomChatConfig cfg = AtomChatConfig.get();
        AtomChatConfig.BubbleStyle original = cfg.bubbleStyle;
        SettingsActions actions = new SettingsActions(new StubHost(tmp.resolve("avatar")));
        try {
            cfg.bubbleStyle = AtomChatConfig.BubbleStyle.CLASSIC;
            for (AtomChatConfig.BubbleStyle expected : new AtomChatConfig.BubbleStyle[]{
                    AtomChatConfig.BubbleStyle.ROUND, AtomChatConfig.BubbleStyle.CORNER, AtomChatConfig.BubbleStyle.CLASSIC}) {
                actions.handleSettingsAction("bubble_style:" + expected.name());
                AtomChatConfig.reload();
                assertEquals(expected, AtomChatConfig.get().effectiveBubbleStyle());
            }
        } finally {
            AtomChatConfig.get().bubbleStyle = original;
        }
    }

    @Test
    void thePageNavStyleCardTogglesAndComesBack() throws Exception {
        AtomChatConfig cfg = AtomChatConfig.get();
        AtomChatConfig.PageNavStyle original = cfg.pageNavStyle;
        SettingsActions actions = new SettingsActions(new StubHost(tmp.resolve("avatar")));
        try {
            cfg.pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
            actions.handleSettingsAction("page_nav_style");
            assertEquals(AtomChatConfig.PageNavStyle.ZOOM, AtomChatConfig.get().pageNavStyle);
            actions.handleSettingsAction("page_nav_style");
            assertEquals(AtomChatConfig.PageNavStyle.SLIDE, AtomChatConfig.get().pageNavStyle);
        } finally {
            AtomChatConfig.get().pageNavStyle = original;
        }
    }

    @Test
    void anUnknownCardAnswersNothing() throws Exception {
        StubHost host = new StubHost(tmp.resolve("avatar"));
        new SettingsActions(host).handleSettingsAction("no_such_card");

        assertEquals(List.of(), host.calls);
        assertFalse(host.historyWindowsReset);
    }

    @Test
    void theWallpaperCardRoutesIntoThePicker() throws Exception {
        StubHost host = new StubHost(tmp.resolve("avatar"));
        SettingsActions actions = new SettingsActions(host);

        // The picker starts by clearing the press through the client handle, so
        // with a headless stub the routing shows up as exactly that: it went to
        // the picker and not to another card. The dialog itself is a probe.
        assertThrows(NullPointerException.class, () -> actions.handleSettingsAction("wallpaper_pick"));
        assertEquals(List.of(), host.calls, "the picker answers nothing by itself");
    }

    @Test
    void theThreeCropGeometriesArePinned() {
        // Wallpaper: the whole panel's aspect, handed over unchanged.
        assertEquals(900.0F, SettingsActions.wallpaperFrame(900.0F, 600.0F)[0], 0.001F);
        assertEquals(600.0F, SettingsActions.wallpaperFrame(900.0F, 600.0F)[1], 0.001F);

        // Avatar: square (circle mode), so the ratio arguments are the ignore
        // values the cropper expects.
        assertEquals(0.0F, SettingsActions.avatarFrame()[0], 0.001F);
        assertEquals(0.0F, SettingsActions.avatarFrame()[1], 0.001F);

        // Banner: the card's width minus both clip insets, and the banner card's
        // fixed height.
        float[] banner = SettingsActions.bannerFrame(900.0F);
        assertEquals(900.0F - UiTokens.ROW_CLIP_INSET * 2.0F, banner[0], 0.001F);
        assertEquals(UiTokens.PROFILE_BANNER_H, banner[1], 0.001F);

        // A degenerate layout cannot ask the cropper for a zero-width frame.
        assertEquals(1.0F, SettingsActions.bannerFrame(1.0F)[0], 0.001F);
        assertNotNull(SettingsActions.bannerFrame(0.0F));
    }
}
