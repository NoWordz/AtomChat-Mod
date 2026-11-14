package com.atom.chat.config;

import com.atom.chat.platform.Platform;
import com.atom.chat.util.CacheDirs;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigReloadIoTest {
    @Test void unchangedReloadDoesNotRewriteSettingsOrPersonalData(@TempDir Path dir) throws Exception {
        var provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);Object oldProvider=provider.get(null);
        var instance=AtomChatConfig.class.getDeclaredField("instance");instance.setAccessible(true);Object oldConfig=instance.get(null);
        try {
            Platform.install(new Platform.Provider(){public Path gameDir(){return dir;}public Path configDir(){return dir.resolve("config");}public boolean isModLoaded(String id){return false;}});
            instance.set(null,null);AtomChatConfig.get();
            Path settings=dir.resolve("config/atomchat/atomchat-client.json"),personal=CacheDirs.clientDataPath();
            String personalBefore=Files.readString(personal);
            FileTime sentinel=FileTime.fromMillis(1_680_000_000_000L);
            Files.setLastModifiedTime(settings,sentinel);Files.setLastModifiedTime(personal,sentinel);
            AtomChatConfig.reload();AtomChatConfig.reload();
            assertEquals(personalBefore,Files.readString(personal),"unchanged personal JSON before timestamp check");
            assertEquals(sentinel,Files.getLastModifiedTime(settings));
            assertEquals(sentinel,Files.getLastModifiedTime(personal));
            AtomChatConfig.get().feedbackVolume=.31F;assertTrue(AtomChatConfig.saveChecked(AtomChatConfig.get()));
            assertNotEquals(sentinel,Files.getLastModifiedTime(settings));
            assertEquals(sentinel,Files.getLastModifiedTime(personal),"changing a setting does not rewrite unchanged personal data");
        } finally {instance.set(null,oldConfig);provider.set(null,oldProvider);}
    }
}
