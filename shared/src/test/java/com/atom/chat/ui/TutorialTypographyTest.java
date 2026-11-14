package com.atom.chat.ui;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Normal-size tutorial copy is part of the product contract, not a rendering suggestion. */
class TutorialTypographyTest {
    private static Path root(){for(var p=Path.of("").toAbsolutePath();p!=null;p=p.getParent())if(Files.isDirectory(p.resolve("shared")))return p;throw new AssertionError();}
    private static String read(String path)throws Exception{return Files.readString(root().resolve(path));}

    @Test void annotationBodyProgressAndNoticeNeverUseTheSmallSubtitleToken()throws Exception {
        String src=read("shared/src/main/java/com/atom/chat/onboarding/TutorialOverlay.java");
        assertFalse(src.contains("FontManager.font(UiTokens.SETTINGS_TILE_SUB)"),"tutorial copy must stay at normal size");
        assertTrue(src.contains("TutorialOverlay.BODY_FONT_SIZE")||src.contains("private static final float BODY_FONT_SIZE=UiTokens.FONT_BUTTON"));
        assertTrue(src.contains("public boolean scroll("),"a cramped annotation card keeps its copy scrollable");
    }

    @Test void hudEditorHintUsesTheNormalButtonFont()throws Exception {
        String src=read("shared/src/main/java/com/atom/chat/notification/NotificationHudEditor.java");
        assertTrue(src.contains("translate.apply(\"atomchat.tutorial.hud_editor\")"));
        assertTrue(src.contains("UiTokens.FONT_BUTTON"),"the hint must use the 16px button font");
        assertFalse(src.contains("translate.apply(\"atomchat.tutorial.hud_editor\"):text(\"hint\"),cx,toolbar.y()+UiTokens.ACTION_BUTTON_SIZE,toolbar.w()-UiTokens.PANEL_BOTTOM_PAD*2,UiTokens.SETTINGS_TILE_SUB"),
                "the tutorial hint must not stay on the 12px subtitle font");
    }

    @Test void tutorialPromptBypassesShrinkingWhileDangerousDialogsKeepTheirFitLoop()throws Exception {
        String src=read("shared/src/main/java/com/atom/chat/ui/ConfirmationDialog.java");
        assertTrue(src.contains("tutorial ? ConfirmationText.wrap(message,bodyWidth,UiTokens.FONT_BUTTON)"),
                "the started tutorial prompt uses fixed normal-size wrapping");
        assertTrue(src.contains("while (!tutorial && bodySize > UiTokens.DIALOG_MIN_TEXT_SIZE"),
                "only non-tutorial confirmations may enter the shrink loop");
        assertTrue(src.contains("public boolean scroll("),"the tutorial prompt can scroll at normal size");
    }
}
