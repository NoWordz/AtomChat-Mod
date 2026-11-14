package com.atom.chat.ui;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Guards the native boundary that unit tests of shared state cannot execute. */
class TutorialLifecycleWiringSourceTest {
    private static Path root(){for(var p=Path.of("").toAbsolutePath();p!=null;p=p.getParent())if(Files.isDirectory(p.resolve("platforms")))return p;throw new AssertionError();}
    @Test void hudEditorRequestsAnimatedCloseAndInvokesHostOnlyAfterItFinishes() throws Exception {
        for(String path:List.of(
                "layers/mapping/official/src/main/java/com/atom/chat/notification/NotificationHudEditorScreen.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/notification/NotificationHudEditorScreen.java")) {
            String src=Files.readString(root().resolve(path));
            assertTrue(src.contains("editor.requestClose()"),path+" must use the editor's exit animation");
            assertTrue(src.contains("if(editor.closed())")&&src.contains("returned.run()"),
                    path+" must return to the parent before resuming the tutorial");
        }
    }
    @Test void everyScreenLoadsTheSandboxPictureWhenTheImageStepAppears() throws Exception {
        for(String path:List.of(
                "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java")) {
            String src=Files.readString(root().resolve(path));
            assertTrue(src.contains("tutorialSandbox.showImage()"),path+" must populate the real image step");
        }
    }
    @Test void childEditorSuspendsTheLiveTutorialAndParentRemovalDuringThatChildKeepsItsState() throws Exception {
        for(String path:List.of(
                "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java")) {
            String src=Files.readString(root().resolve(path));
            assertTrue(src.contains("tutorial.suspendForChild()"),path);
            assertTrue(src.contains("tutorial::resumeFromChild")&&src.contains("tutorial::reset"),path);
            assertTrue(src.contains("if(!tutorial.suspended())tutorial.reset()"),path+" must not end a parked tutorial");
            assertTrue(src.contains("if(!tutorial.suspended())AtomChatState.save"),path+" must not overwrite saved navigation");
        }
    }
    @Test void leavingTheTutorialRestoresPreviewDraftRepliesAndComposerFocus() throws Exception {
        for(String path:List.of(
                "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java")) {
            String src=Files.readString(root().resolve(path));
            assertTrue(src.contains("ConfigPreviewTransaction.begin"),path);
            assertTrue(src.contains("tutorialPreview.close()")&&src.contains("tutorialPreview.keep()"),path);
            assertTrue(src.contains("draftStore=tutorialSavedDraftStore"),path);
            assertTrue(src.contains("replySlots.restore(tutorialSavedReplies)"),path);
            assertTrue(src.contains("composer.setText(tutorialSavedText)")&&src.contains("composer.setFocused(tutorialSavedFocus)"),path);
        }
    }
}
