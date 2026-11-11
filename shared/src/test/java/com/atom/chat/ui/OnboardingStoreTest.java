package com.atom.chat.ui;
import com.atom.chat.onboarding.OnboardingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class OnboardingStoreTest {
    @TempDir Path root;
    @Test void eitherChoiceIsDurableWithoutTouchingConfigOrCache() throws Exception {
        for (String choice : new String[]{"watch", "dismissed"}) {
            Path file = root.resolve(choice).resolve("atomchat/client/onboarding.json");
            var state = new OnboardingStore(file);
            assertTrue(state.needsPrompt());
            state.choose(choice);
            assertFalse(state.needsPrompt());
            assertFalse(new OnboardingStore(file).needsPrompt());
            assertFalse(Files.exists(root.resolve(choice).resolve("config")));
            assertFalse(Files.exists(root.resolve(choice).resolve("atomchat/cache")));
            String saved = Files.readString(file);
            assertTrue(saved.contains(choice));
            assertFalse(saved.contains("step"), "replay never persists a resume cursor");
        }
    }
    @Test void corruptStateDoesNotCrashOrLoseOriginalBytes() throws Exception {
        Path file=root.resolve("onboarding.json"); Files.writeString(file,"broken original");
        var state=new OnboardingStore(file);
        assertTrue(state.needsPrompt());
        state.choose("dismissed");
        assertFalse(new OnboardingStore(file).needsPrompt());
        try(var files=Files.list(root)) {
            assertTrue(files.anyMatch(p->{try{return Files.readString(p).equals("broken original");}catch(Exception e){return false;}}));
        }
    }
    @Test void failedPersistenceStillSuppressesRepeatedOffersInThisScreen() throws Exception {
        Path parent=root.resolve("not-a-directory"); Files.writeString(parent,"keep");
        var state=new OnboardingStore(parent.resolve("onboarding.json"));
        state.choose("watch");
        assertFalse(state.needsPrompt());
        assertEquals("keep",Files.readString(parent));
    }
    @Test void reopeningRetriesATemporaryWriteFailureWithoutOfferingAgain() throws Exception {
        Path parent=root.resolve("temporarily-blocked");Files.writeString(parent,"locked");
        Path file=parent.resolve("onboarding.json");
        var state=new OnboardingStore(file);state.choose("watch");
        assertTrue(state.saveFailed());
        Files.delete(parent);Files.createDirectory(parent);
        var reopened=new OnboardingStore(file);
        assertFalse(reopened.needsPrompt());assertFalse(reopened.saveFailed());
        assertTrue(Files.readString(file).contains("watch"));
    }
}
