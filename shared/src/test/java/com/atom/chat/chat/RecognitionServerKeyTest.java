package com.atom.chat.chat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecognitionServerKeyTest {
    @Test void differentAddressesNeverShareLearningBecauseTheSavedListTitlesMatch() {
        assertNotEquals(RecognitionServerKey.multiplayer("one.example:25565"),RecognitionServerKey.multiplayer("two.example:25565"));
        assertEquals(RecognitionServerKey.multiplayer("ONE.EXAMPLE:25565"),RecognitionServerKey.multiplayer(" one.example:25565 "));
        assertNull(RecognitionServerKey.multiplayer(" "));
    }
    @Test void worldDirectoryDistinguishesWorldsWithTheSameDisplayName() {
        assertNotEquals(RecognitionServerKey.singleplayer(Path.of("saves/one")),RecognitionServerKey.singleplayer(Path.of("saves/two")));
    }
}
