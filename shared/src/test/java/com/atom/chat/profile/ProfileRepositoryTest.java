package com.atom.chat.profile;
import com.atom.chat.net.MediaIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ProfileRepositoryTest {
    @TempDir Path temp;
    private final UUID player = UUID.fromString("12345678-1234-1234-1234-123456789012");
    private ProfileRepository repository(String world, long budget) {
        return new ProfileRepository(temp.resolve(world), temp.resolve("chat-media"), budget);
    }
    private String hostedImage() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", out);
        byte[] png = out.toByteArray();
        String id = MediaIds.idFor(png, "png");
        Files.createDirectories(temp.resolve("chat-media"));
        Files.write(temp.resolve("chat-media").resolve(id), png);
        return MediaIds.urlFor(id);
    }
    @Test void restartAndOtherWorldIsolation() throws Exception {
        var saved = repository("world-a", 1_000_000).update(player, new ProfilePatch("Hello", null, null));
        assertEquals("Hello", saved.signature());
        assertTrue(saved.revision() > 0);
        assertEquals(saved, repository("world-a", 1_000_000).get(player));
        assertEquals("", repository("world-b", 1_000_000).get(player).signature());
    }
    @Test void partialSignatureUpdateAndExplicitClearPreserveImages() throws Exception {
        String url = hostedImage();
        var repo = repository("world-a", 1_000_000);
        repo.update(player, new ProfilePatch("first", url, url));
        var next = repo.update(player, new ProfilePatch("", null, null));
        assertEquals("", next.signature());
        assertEquals(url, next.avatar());
        assertEquals(url, next.banner());
        var cleared = repo.update(player, new ProfilePatch(null, null, ""));
        assertEquals("", cleared.banner());
        assertEquals(url, cleared.avatar());
    }
    @Test void hostedProfileSurvivesTemporaryChatMediaDeletionAndRestart() throws Exception {
        String url = hostedImage();
        var repo = repository("world-a", 1_000_000);
        repo.update(player, new ProfilePatch(null, null, url));
        String id = MediaIds.idOf(url);
        Files.delete(temp.resolve("chat-media").resolve(id));
        var restarted = repository("world-a", 1_000_000);
        assertEquals(url, restarted.get(player).banner());
        assertNotNull(restarted.media(id));
        assertTrue(Files.isRegularFile(restarted.media(id)));
    }
    @Test void rejectedNewImageKeepsOldDocument() throws Exception {
        var repo = repository("world-a", 1_000_000);
        var original = repo.update(player, new ProfilePatch("old", "https://example.org/old.png", ""));
        assertThrows(Exception.class, () -> repo.update(player,
                new ProfilePatch(null, null, "atomchat-media:" + "a".repeat(64) + ".png")));
        assertEquals(original, repo.get(player));
        String url = hostedImage();
        var tiny = repository("world-a", 1);
        assertThrows(Exception.class, () -> tiny.update(player, new ProfilePatch(null, null, url)));
        assertEquals(original, tiny.get(player));
    }
    @Test void rejectsOversizeSignatureAndUnsafeReference() throws Exception {
        var repo = repository("world-a", 1_000_000);
        assertThrows(IllegalArgumentException.class, () -> repo.update(player,
                new ProfilePatch("x".repeat(257), null, null)));
        assertThrows(IllegalArgumentException.class, () -> repo.update(player,
                new ProfilePatch(null, "file:///private.png", null)));
        assertThrows(IllegalArgumentException.class, () -> repo.update(player,
                new ProfilePatch(null, null, "atomchat-media:../escape.png")));
    }

    @Test void signatureOnlyDoesNotClaimAvatarButExplicitClearDoes() throws Exception {
        var repo = repository("world-a", 1_000_000);
        var signature = repo.update(player, new ProfilePatch("new signature", null, null));
        var cleared = repo.update(player, new ProfilePatch(null, "", null));
        assertTrue(cleared.revision() > signature.revision(), "explicit empty avatar is a real published change");
        assertEquals(0, signature.getClass().getMethod("imageFields").invoke(signature));
        assertEquals(1, cleared.getClass().getMethod("imageFields").invoke(cleared));
    }

    @Test void liveQuotaChangeAppliesWithoutRemovingCurrentProfiles() throws Exception {
        var repo = repository("world-a", 1);
        String image = hostedImage();
        repo.setMaxBytes(1_000_000);
        assertDoesNotThrow(() -> repo.update(player, new ProfilePatch("hello", image, null)));
        repo.setMaxBytes(1);
        assertEquals("hello", repo.get(player).signature());
        assertNotNull(repo.media(MediaIds.idOf(image)));
    }
    @Test void startupRecoversOnlyOwnedAbandonedTransactionFiles() throws Exception {
        Path images = temp.resolve("world-a/images"), documents = temp.resolve("world-a/documents");
        Files.createDirectories(images); Files.createDirectories(documents);
        Path imageTemp = images.resolve("image-123.tmp");
        Path documentTemp = documents.resolve(player + "-456.tmp");
        Path manual = images.resolve("manual-recovery.tmp");
        Files.writeString(imageTemp, "partial"); Files.writeString(documentTemp, "partial");
        Files.writeString(manual, "keep");
        repository("world-a", 1_000_000);
        assertFalse(Files.exists(imageTemp)); assertFalse(Files.exists(documentTemp));
        assertTrue(Files.exists(manual), "unrecognized recovery files must remain untouched");
    }
}
