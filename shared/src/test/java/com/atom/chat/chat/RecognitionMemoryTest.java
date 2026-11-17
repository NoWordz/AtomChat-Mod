package com.atom.chat.chat;

import com.atom.chat.platform.Platform;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecognitionMemoryTest {
    private Runnable loaded(BlockingQueue<Runnable> callbacks) throws InterruptedException {
        Runnable work=callbacks.poll(5,TimeUnit.SECONDS);assertNotNull(work,"background load completed");return work;
    }
    @Test void realPersistenceIsServerIsolatedAndClearingInvalidatesAnAlreadyReadSnapshot(@TempDir Path root) throws Exception {
        var provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);Object previous=provider.get(null);
        BlockingQueue<Runnable> callbacks=new LinkedBlockingQueue<>();
        try {
            Platform.install(new Platform.Provider(){public Path gameDir(){return root;}public Path configDir(){return root.resolve("config");}public boolean isModLoaded(String id){return false;}});
            RecognitionMemory.join("MP:one.example",callbacks::add);loaded(callbacks).run();
            UUID alice=UUID.randomUUID();SeenPlayers.remember(alice,"Alice","BlueFox");
            LearnedChatFormats.observe("[G] BlueFox: first","BlueFox","first");
            ConfirmedSkinProfiles.remember(alice,"Alice","observed-texture-property","signature");
            RecognitionMemory.disconnect();
            RecognitionMemory.join("MP:two.example",callbacks::add);loaded(callbacks).run();
            assertNull(SeenPlayers.findUuid("Alice"));assertTrue(LearnedChatFormats.snapshot().isEmpty());assertNull(ConfirmedSkinProfiles.find(alice));
            SeenPlayers.remember(UUID.randomUUID(),"Bob","Other");RecognitionMemory.disconnect();
            RecognitionMemory.join("MP:one.example",callbacks::add);loaded(callbacks).run();
            assertEquals(alice,SeenPlayers.findUuid("BlueFox"));assertNull(SeenPlayers.findUuid("Bob"));
            assertEquals("observed-texture-property",ConfirmedSkinProfiles.find(alice).value());
            assertEquals("second",LearnedChatFormats.match("[G] BlueFox: second").orElseThrow().content());
            RecognitionMemory.disconnect();
            RecognitionMemory.join("MP:one.example",callbacks::add);Runnable stale=loaded(callbacks);
            RecognitionMemory.clearCurrent();stale.run();assertNull(SeenPlayers.findUuid("Alice"));
            RecognitionMemory.disconnect();
            RecognitionMemory.join("MP:one.example",callbacks::add);loaded(callbacks).run();assertTrue(SeenPlayers.snapshot().isEmpty());
        } finally {
            // The single IO queue orders this read after all previous writes; leave its callback
            // uncommitted so disconnect cannot enqueue another write during temp-directory cleanup.
            RecognitionMemory.disconnect();RecognitionMemory.join("MP:barrier",callbacks::add);loaded(callbacks);
            RecognitionMemory.disconnect();provider.set(null,previous);
        }
    }
}
