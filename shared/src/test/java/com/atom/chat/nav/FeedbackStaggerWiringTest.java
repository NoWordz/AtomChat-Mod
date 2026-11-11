package com.atom.chat.nav;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackStaggerWiringTest {
    @Test void allScreensConsumeArrivalsAndArmTheActualSurface() throws Exception {
        Path root=Path.of(System.getProperty("atomchat.repoRoot","../..")).toAbsolutePath().normalize();
        for(String relative:new String[]{"platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java","platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java","platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java"}) {
            String code=Files.readString(root.resolve(relative));
            assertTrue(code.contains("if (composite.consumeArrivalEvent())"),relative+": discarded arrival event");
            for(String page:new String[]{"conversationListPage","settingsHomePage","profilePage","settingsSectionPage"})
                assertTrue(code.contains(page+".markEntered(composite.entrance().enteredAtMs())"),relative+": "+page+" is never armed");
            assertTrue(code.contains("bodyPointer(x, y)"),relative+": page and item inverses must be composed once");
            int backdrop=code.indexOf("bottomChrome.drawBackdrop(canvas)");
            int body=code.indexOf("composite.drawArrivingFrame(canvas");
            int foreground=code.indexOf("bottomChrome.draw(canvas");
            assertTrue(backdrop>=0 && backdrop<body && body<foreground,relative+": dots must stay below the page body");
            String bezel="drawBezel(canvas, layout);";
            int lastBezel=code.indexOf(bezel);
            assertTrue(lastBezel>=0 && code.indexOf(bezel,lastBezel+bezel.length())<0,
                    relative+": each painted frame must contain exactly one bezel pass");
            assertTrue(lastBezel>code.indexOf("com.atom.chat.fun.ChatEffectRenderer.draw(canvas"),
                    relative+": keep the final bezel above all panel effects");
        }
    }
}
