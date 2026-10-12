package com.atom.chat.profile;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
class ProfilePublicationTest {
    private static final class Fake implements ProfileTransport {
        final ArrayList<ProfilePatch> submitted = new ArrayList<>();
        final ArrayList<Consumer<String>> acknowledgements = new ArrayList<>();
        Consumer<String> hostedSuccess;
        Consumer<String> externalError;
        int externalCalls;
        public boolean available() { return true; }
        public boolean hostingEnabled() { return true; }
        public void request(UUID uuid) {}
        public void submit(ProfilePatch patch, Consumer<String> result) {
            submitted.add(patch); acknowledgements.add(result);
        }
        public void uploadHosted(Path file, Consumer<String> success, Consumer<String> error) { hostedSuccess = success; }
        public void uploadExternal(Path file, Consumer<String> success, Consumer<String> error) {
            externalCalls++; externalError = error;
        }
        public void execute(Runnable task) { task.run(); }
        public void feedback(boolean success) {}
    }
    @Test void clearSupersedesInFlightUpload() {
        var fake = new Fake(); var publisher = new ProfilePublication(fake);
        publisher.image(ProfileField.BANNER, Path.of("banner.png"));
        assertNotNull(fake.hostedSuccess);
        publisher.text(ProfileField.BANNER, "");
        fake.hostedSuccess.accept("https://example.org/stale.png");
        assertEquals(1, fake.submitted.size());
        assertEquals("", fake.submitted.get(0).banner());
    }
    @Test void resetRejectsFormerConnectionsUpload() {
        var fake = new Fake(); var publisher = new ProfilePublication(fake);
        publisher.image(ProfileField.AVATAR, Path.of("avatar.png"));
        assertNotNull(fake.hostedSuccess);
        publisher.reset(); fake.hostedSuccess.accept("https://example.org/stale.png");
        assertTrue(fake.submitted.isEmpty());
        assertFalse(publisher.syncing());
    }
    @Test void failedHostedPublicationFallsBackAndFailedExternalMarksUnsynced() {
        var fake = new Fake(); var publisher = new ProfilePublication(fake);
        publisher.image(ProfileField.BANNER, Path.of("banner.png"));
        assertNotNull(fake.hostedSuccess);
        fake.hostedSuccess.accept("atomchat-media:" + "a".repeat(64) + ".png");
        fake.acknowledgements.get(0).accept("missing_hosted_image");
        assertEquals(1, fake.externalCalls);
        fake.externalError.accept("upload_failed");
        assertTrue(publisher.unsynced());
        assertFalse(publisher.syncing());
    }
    @Test void olderAcknowledgementCannotHideLatestFailure() {
        var fake = new Fake(); var publisher = new ProfilePublication(fake);
        publisher.text(ProfileField.SIGNATURE, "old");
        publisher.text(ProfileField.SIGNATURE, "new");
        assertEquals(2, fake.submitted.size());
        fake.acknowledgements.get(1).accept("sync_failed");
        fake.acknowledgements.get(0).accept("");
        assertTrue(publisher.unsynced());
    }
}
