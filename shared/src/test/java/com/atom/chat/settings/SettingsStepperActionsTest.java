package com.atom.chat.settings;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SettingsStepperActionsTest {
    @BeforeAll static void setup(@TempDir Path dir) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return dir; }
            public Path gameDir() { return dir; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test void arrowActionsPersistBothDirectionsAndDelegateTheHudEditor(@TempDir Path dir) throws Exception {
        var configField = AtomChatConfig.class.getDeclaredField("instance"); configField.setAccessible(true);
        Object old = configField.get(null);
        var cfg = new AtomChatConfig(); configField.set(null, cfg);
        var page = new SettingsSectionPage(); var avatars = new AvatarStore(dir);
        var edits = new AtomicInteger();
        SettingsActions.Host host = (SettingsActions.Host) Proxy.newProxyInstance(
                SettingsActions.Host.class.getClassLoader(), new Class<?>[]{SettingsActions.Host.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "avatarStore" -> avatars;
                    case "sectionPage" -> page;
                    case "openNotificationHudEditor" -> { edits.incrementAndGet(); yield null; }
                    default -> throw new AssertionError("unexpected host call: " + method.getName());
                });
        try {
            var actions = new SettingsActions(host);
            cfg.teleportCommandMode = "auto";
            for (String expected : new String[]{"tpa", "tp", "auto"}) {
                actions.handleSettingsAction("teleport_mode:prev");
                assertEquals(expected, cfg.teleportCommandMode);
            }
            for (String expected : new String[]{"tp", "tpa", "auto"}) {
                actions.handleSettingsAction("teleport_mode");
                assertEquals(expected, cfg.teleportCommandMode);
            }
            cfg.avatarStyle = AtomChatConfig.AvatarStyle.ATOMCHAT;
            actions.handleSettingsAction("avatar_style:prev");
            assertEquals(AtomChatConfig.AvatarStyle.E33CHAT, cfg.avatarStyle);
            actions.handleSettingsAction("avatar_style");
            assertEquals(AtomChatConfig.AvatarStyle.ATOMCHAT, cfg.avatarStyle);
            cfg.pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
            actions.handleSettingsAction("page_nav_style:prev");
            assertEquals(AtomChatConfig.PageNavStyle.ZOOM, cfg.pageNavStyle);
            actions.handleSettingsAction("page_nav_style");
            assertEquals(AtomChatConfig.PageNavStyle.SLIDE, cfg.pageNavStyle);
            actions.handleSettingsAction("hud_edit"); assertEquals(1, edits.get());
        } finally { configField.set(null, old); }
    }
}
