package com.atom.chat.config;

import com.atom.chat.platform.Platform;
import com.atom.chat.util.CacheDirs;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigReloadIoTest {
    @TempDir Path dir;
    private Path settings;
    private Object oldProvider, oldConfig;
    private java.lang.reflect.Field provider, instance;
    private Runnable onGameDirectory = () -> {};

    @BeforeEach void install() throws Exception {
        provider = Platform.class.getDeclaredField("provider");
        provider.setAccessible(true);
        oldProvider = provider.get(null);
        instance = AtomChatConfig.class.getDeclaredField("instance");
        instance.setAccessible(true);
        oldConfig = instance.get(null);
        instance.set(null, null);
        settings = dir.resolve("config/atomchat/atomchat-client.json");
        Platform.install(new Platform.Provider() {
            public Path gameDir() {
                onGameDirectory.run();
                return dir;
            }
            public Path configDir() { return dir.resolve("config"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @AfterEach void restore() throws Exception {
        instance.set(null, oldConfig);
        provider.set(null, oldProvider);
        com.atom.chat.util.DataMigration.takeWarning();
    }

    @Test void unchangedReloadDoesNotRewriteSettingsOrPersonalData() throws Exception {
        AtomChatConfig.get();
        Path personal = CacheDirs.clientDataPath();
        String personalBefore = Files.readString(personal);
        FileTime sentinel = FileTime.fromMillis(1_680_000_000_000L);
        Files.setLastModifiedTime(settings, sentinel);
        Files.setLastModifiedTime(personal, sentinel);
        AtomChatConfig.reload();
        AtomChatConfig.reload();
        assertEquals(personalBefore, Files.readString(personal));
        assertEquals(sentinel, Files.getLastModifiedTime(settings));
        assertEquals(sentinel, Files.getLastModifiedTime(personal));
        AtomChatConfig.get().feedbackVolume = .31F;
        assertTrue(AtomChatConfig.saveChecked(AtomChatConfig.get()));
        assertNotEquals(sentinel, Files.getLastModifiedTime(settings));
        assertEquals(sentinel, Files.getLastModifiedTime(personal));
    }

    @Test void unchangedReloadReadsTheMainFileOnce() throws Exception {
        AtomChatConfig.get();
        Path trace = dir.resolve("config-reload.jfr");
        try (Recording recording = new Recording()) {
            recording.enable("jdk.FileRead").withThreshold(Duration.ZERO).withStackTrace();
            recording.start();
            AtomChatConfig.reload();
            recording.stop();
            recording.dump(trace);
        }
        var reads = RecordingFile.readAllEvents(trace).stream()
                .filter(event -> event.getEventType().getName().equals("jdk.FileRead"))
                .filter(event -> event.getLong("bytesRead") > 0 && event.getString("path") != null)
                .filter(event -> Path.of(event.getString("path")).equals(settings))
                .toList();
        assertEquals(1, reads.size(), "unchanged reload should read the main config once");
    }

    @Test void externalReplacementDuringLoadWinsEvenWithTheSameSizeAndTimestamp() throws Exception {
        String before = "{\"feedbackVolume\":0.21,\"quickPhrases\":[\"before\"]}";
        String edited = "{\"feedbackVolume\":0.87,\"quickPhrases\":[\"edited\"]}";
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, before);
        AtomicBoolean replaced = replaceDuring(settings, edited, "load");
        AtomChatConfig.reload();
        assertTrue(replaced.get(), "external editor must run after the first primary read");
        assertEquals(edited, Files.readString(settings));
        assertEquals(.87F, AtomChatConfig.get().feedbackVolume);
        assertTrue(AtomChatConfig.get().quickPhrases.contains("edited"));
        assertFalse(Files.exists(CacheDirs.clientDataPath()), "deferred migration must not commit stale personal data");
    }

    @Test void newerPersonalDataIsNotOverwrittenByTheOldLoadedValues() throws Exception {
        String main = "{\"feedbackVolume\":0.31}";
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, main);
        Path personal = CacheDirs.clientDataPath();
        Files.createDirectories(personal.getParent());
        Files.writeString(personal, "{\"quickPhrases\":[\"before\"]}");
        String edited = "{\"quickPhrases\":[\"edited\"]}";
        AtomicBoolean replaced = replaceDuring(personal, edited, "saveAndStrip");
        AtomChatConfig.reload();
        assertTrue(replaced.get(), "external editor must run after the personal data load");
        assertEquals(edited, Files.readString(personal));
        assertEquals(main, Files.readString(settings));
        assertTrue(AtomChatConfig.get().quickPhrases.contains("edited"));
    }

    private AtomicBoolean replaceDuring(Path target, String edited, String method) throws IOException {
        assertEquals(edited.length(), Files.size(target));
        Path replacement = dir.resolve("editor.tmp");
        Files.writeString(replacement, edited);
        Files.setLastModifiedTime(replacement, Files.getLastModifiedTime(target));
        AtomicBoolean replaced = new AtomicBoolean();
        onGameDirectory = () -> {
            boolean loadingPersonal = StackWalker.getInstance().walk(frames -> frames.anyMatch(
                    frame -> frame.getClassName().equals(PersonalDataStore.class.getName())
                            && frame.getMethodName().equals(method)));
            if (loadingPersonal && replaced.compareAndSet(false, true)) {
                CompletableFuture.runAsync(() -> {
                    try {
                        Files.move(replacement, target, StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                }).join();
            }
        };
        return replaced;
    }

    @Test void invalidMainConfigIsPreservedOnLoadAndSave() throws Exception {
        Files.createDirectories(settings.getParent());
        for (String invalid : new String[]{"broken-json", "[]", "null"}) {
            Files.writeString(settings, invalid);
            AtomChatConfig.reload();
            assertEquals(1F, AtomChatConfig.get().feedbackVolume);
            assertEquals(invalid, Files.readString(settings));
            assertFalse(AtomChatConfig.saveChecked(AtomChatConfig.get()));
            assertEquals(invalid, Files.readString(settings));
        }
        assertFalse(Files.exists(CacheDirs.clientDataPath()));
    }
}
