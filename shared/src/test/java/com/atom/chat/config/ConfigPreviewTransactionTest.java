package com.atom.chat.config;

import com.atom.chat.platform.Platform;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigPreviewTransactionTest {
    @TempDir Path tmp;
    @BeforeEach void install(){Platform.install(new Platform.Provider(){public Path configDir(){return tmp.resolve("config");}public Path gameDir(){return tmp;}public boolean isModLoaded(String id){return false;}});}
    @Test void previewSaveCannotReachDiskAndRollbackRestoresMutablePersonalData() throws Exception {
        var c=new AtomChatConfig();c.quickPhrases=new java.util.ArrayList<>(java.util.List.of("original"));
        AtomChatConfig.save(c);Path file=tmp.resolve("config/atomchat/atomchat-client.json");byte[] original=Files.readAllBytes(file);
        try(var preview=ConfigPreviewTransaction.begin(c)) {
            c.accentColor=0xFFFF0000;c.quickPhrases.add("temporary");c.notificationHudScale=1.8f;
            assertTrue(AtomChatConfig.saveChecked(c));
            assertArrayEquals(original,Files.readAllBytes(file));
        }
        assertEquals(java.util.List.of("original"),c.quickPhrases);
        assertNotEquals(0xFFFF0000,c.accentColor);assertNotEquals(1.8f,c.notificationHudScale);
    }
    @Test void explicitKeepPersistsOnceAndCloseDoesNotRollback() throws Exception {
        var c=new AtomChatConfig();
        try(var preview=ConfigPreviewTransaction.begin(c)){c.accentColor=0xFF123456;assertTrue(preview.keep());}
        assertEquals(0xFF123456,c.accentColor);
        assertTrue(Files.readString(tmp.resolve("config/atomchat/atomchat-client.json")).contains("-15584170"));
    }
}
