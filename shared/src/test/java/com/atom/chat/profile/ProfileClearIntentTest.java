package com.atom.chat.profile;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
class ProfileClearIntentTest {
    private static void installRuntime() {
        Path runtime = Path.of("build/profile-intent-runtime").toAbsolutePath();
        Platform.install(new Platform.Provider() {
            public Path configDir() { return runtime.resolve("config"); }
            public Path gameDir() { return runtime.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }
    private static final class Fake implements ProfileTransport {
        final ArrayList<ProfilePatch> submissions = new ArrayList<>();
        String error = "disconnected";
        public boolean available() { return true; }
        public boolean hostingEnabled() { return true; }
        public void request(UUID uuid) {}
        public void submit(ProfilePatch patch, Consumer<String> result) { submissions.add(patch); result.accept(error); }
        public void uploadHosted(Path file, Consumer<String> success, Consumer<String> error) {}
        public void uploadExternal(Path file, Consumer<String> success, Consumer<String> error) {}
        public void execute(Runnable task) { task.run(); }
        public void feedback(boolean success) {}
    }
    @Test void failedClearIsRememberedAndReplayedAfterReconnect() {
        installRuntime();
        var cfg = AtomChatConfig.get();
        boolean avatar = cfg.profileAvatarCleared, banner = cfg.profileBannerCleared;
        var transport = new Fake();
        ProfileSync.install(transport);
        try {
            cfg.profileAvatarCleared = false; cfg.profileBannerCleared = false;
            ProfileSync.imageCleared(ProfileField.AVATAR);
            ProfileSync.imageCleared(ProfileField.BANNER);
            assertTrue(cfg.profileAvatarCleared && cfg.profileBannerCleared,
                    "the failed clear intent must survive connection state reset");
            ProfileSync.clearConnection(); transport.submissions.clear(); transport.error = "";
            ProfileSync.synchronizeImages();
            assertTrue(transport.submissions.stream().anyMatch(patch -> "".equals(patch.avatar())));
            assertTrue(transport.submissions.stream().anyMatch(patch -> "".equals(patch.banner())));
        } finally {
            cfg.profileAvatarCleared = avatar; cfg.profileBannerCleared = banner;
            ProfileSync.uninstall();
        }
    }
    @Test void importedSignatureIsNotPublishedIntoAnotherWorld() {
        installRuntime();
        var cfg = AtomChatConfig.get();
        String old = cfg.playerSignature;
        boolean edited = cfg.profileSignatureEdited, migrated = cfg.profileSignatureMigrationDone;
        var transport = new Fake(); ProfileSync.install(transport);
        try {
            cfg.playerSignature = "learned-from-world-a";
            cfg.profileSignatureEdited = false; cfg.profileSignatureMigrationDone = true;
            ProfileSync.synchronizeSignature(ProfileRecord.empty(UUID.randomUUID()));
            assertTrue(transport.submissions.isEmpty(), "server-imported text is not a user global choice");
            assertEquals("", cfg.playerSignature, "world B's explicit empty signature must replace the projection");
        } finally {
            cfg.playerSignature = old; cfg.profileSignatureEdited = edited; cfg.profileSignatureMigrationDone = migrated;
            ProfileSync.uninstall();
        }
    }
    @Test void localNewAvatarReplacesRememberedClearChoice() {
        installRuntime();
        var cfg = AtomChatConfig.get();
        boolean old = cfg.profileAvatarCleared;
        var transport = new Fake(); ProfileSync.install(transport);
        try {
            cfg.profileAvatarCleared = true;
            ProfileSync.imageEdited(ProfileField.AVATAR, Path.of("new-avatar.png"));
            assertFalse(cfg.profileAvatarCleared);
        } finally {
            cfg.profileAvatarCleared = old; ProfileSync.uninstall();
        }
    }
}
