package com.atom.chat.chat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The store's round-trip tests cannot detect an init() that never restores it. */
class DraftInitializationSourceTest {
    @Test void closingReturnsTheBorrowedPhraseFieldBeforeSavingTheChatDraft() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("platforms"))) root = root.getParent();
        assertNotNull(root);
        for (String path : List.of("com/atom/chat/screen", "net/minecraft/client/gui/screen")) {
            for (String v : List.of("1.20.1-forge", "1.21.1-neoforge", "1.21.1-fabric")) {
                Path file = root.resolve("platforms/"+v+"/src/main/java/"+path+"/AtomChatScreen.java");
                if (!Files.isRegularFile(file)) continue;
                String src = Files.readString(file);
                String removed = src.substring(src.indexOf("public void removed()"));
                removed = removed.substring(0, removed.indexOf("super.removed()"));
                int restore = removed.indexOf("endPhraseEdit(false)");
                assertTrue(restore >= 0 && restore < removed.indexOf("saveCurrentDraft()"), file.toString());
            }
        }
    }
    @Test void everyScreenRestoresItsDraftAfterVanillaCreatesTheInputField() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("platforms"))) root = root.getParent();
        assertNotNull(root);
        for (String screen : List.of(
                "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java")) {
            String src = Files.readString(root.resolve(screen));
            int start = src.indexOf("protected void init()");
            assertTrue(start >= 0, screen);
            String init = src.substring(start, src.indexOf("// ---- quick-phrase", start));
            assertTrue(init.contains("draftStore.draftForNav(topNav(), false, null)"), screen);
            assertTrue(init.contains("String previousText ="), "resize must retain the live field, including borrowed edits: " + screen);
            int restore = init.indexOf(".setValue(restoredText)");
            if (restore < 0) restore = init.indexOf(".setText(restoredText)");
            assertTrue(restore > init.indexOf("super.init()"), screen);
            assertTrue(init.indexOf("dismissSuggestor()") > restore, "restoration must not open command suggestions: " + screen);
        }
    }
}
