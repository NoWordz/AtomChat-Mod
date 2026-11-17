package com.atom.chat.config;

import com.atom.chat.platform.Platform;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigPersistenceTest {
    @Test void failedWriteCannotPublishAConfigOrReportSuccess(@TempDir Path root) throws Exception {
        var provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);Object previousProvider=provider.get(null);
        var instance=AtomChatServerConfig.class.getDeclaredField("instance");instance.setAccessible(true);Object previousConfig=instance.get(null);
        try {
            Path config=root.resolve("config");
            Platform.install(new Platform.Provider(){public Path gameDir(){return root;}public Path configDir(){return config;}public boolean isModLoaded(String id){return false;}});
            var original=new AtomChatServerConfig();original.packName="original";instance.set(null,original);
            Files.writeString(config,"a regular file prevents creating the config directory");
            var edited=new ServerConfigValues(true,true,2048,512,64,7,3000,32,8,"edited",List.of("hello"));
            assertFalse(AtomChatServerConfig.saveValues(edited));
            assertSame(original,AtomChatServerConfig.get());assertEquals("original",AtomChatServerConfig.get().packName);
            Files.delete(config);
            assertTrue(AtomChatServerConfig.saveValues(edited));
            assertEquals("edited",AtomChatServerConfig.get().packName);
            AtomChatServerConfig.reload();assertEquals("edited",AtomChatServerConfig.get().packName);
        } finally {instance.set(null,previousConfig);provider.set(null,previousProvider);}
    }
}
