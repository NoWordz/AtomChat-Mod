package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.onboarding.TutorialDemoPanels;
import com.atom.chat.onboarding.TutorialSession;
import com.atom.chat.platform.Platform;
import com.atom.chat.tools.ToolLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Root-cause guard for the onboarding-only control sandbox: demo surfaces must be real and side-effect free. */
class TutorialDemoPanelsSandboxTest {
    @TempDir static Path gameDir;

    @BeforeAll static void installPlatform() {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return gameDir.resolve("config"); }
            public Path gameDir() { return gameDir; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test void sandboxPanelsNeverReadPackFilesOrWriteLivePhrases() throws Exception {
        AtomChatConfig config = AtomChatConfig.get();
        config.quickPhrases = new ArrayList<>(List.of("真实短语"));
        List<String> before = List.copyOf(config.quickPhrases);

        EmojiPanel emoji = EmojiPanel.sandbox(new EmojiPanel.Host() {
            public void insert(String text) { }
            public void sendSticker(Path file) { }
            public void pickEmoteFile() { }
        });
        emoji.toggle();
        emoji.update(200);
        assertTrue(emoji.isOpen());
        assertFalse(Files.exists(gameDir.resolve("atomchat/client/emotes")));

        QuickPhrasePanel phrases = QuickPhrasePanel.sandbox(inserted -> { }, () -> 0xFF4A90E2);
        assertFalse(phrases.phraseAt(0).isEmpty(), "sandbox must own a visible local phrase list");
        phrases.open();
        phrases.deleteAt(0);
        phrases.commitText("仅演示新增");
        assertEquals(before, config.quickPhrases, "sandbox edits must never touch client-data/config");
    }

    @Test void tutorialDemoPanelsDriveRealPopupSurfacesWithoutLiveSideEffects() {
        UiLayout layout = UiLayout.of(15, 15, 420, 780);
        try (TutorialDemoPanels demo = new TutorialDemoPanels()) {
            assertTrue(demo.open());

            TutorialSession session = new TutorialSession();
            seek(session, TutorialSession.Step.EXPRESSIONS);
            assertTrue(demo.click(session, layout, layout.emojiBtn.x() + 4,
                    layout.emojiBtn.y() + 4, 0), "emoji button opens the real demo emoji surface");
            try (Surface surface = Surface.makeRasterN32Premul(460, 820)) {
                demo.render(surface.getCanvas(), session, layout, key -> key, 0, 0, 200);
            }
            assertTrue(demo.bounds(layout).w() > layout.emojiBtn.w(),
                    "bounds must expose the opened real popup instead of the trigger only");
            demo.reset();

            seek(session, TutorialSession.Step.QUICK_PHRASES);
            assertTrue(demo.click(session, layout, layout.phraseBtn.x() + 4,
                    layout.phraseBtn.y() + 4, 0), "phrase button opens the real sandbox phrase panel");
            try (Surface surface = Surface.makeRasterN32Premul(460, 820)) {
                demo.render(surface.getCanvas(), session, layout, key -> key, 0, 0, 200);
            }
            assertTrue(demo.bounds(layout).w() > layout.phraseBtn.w(),
                    "quick phrase bounds must expose the real popup");
        }
    }

    @Test void messagePlayerImageAndSearchStepsUseTheirRealMenusAndMemoryState() {
        UiLayout layout = UiLayout.of(15, 15, 420, 780);
        try (TutorialDemoPanels demo = new TutorialDemoPanels();
             Surface surface = Surface.makeRasterN32Premul(460, 820)) {
            TutorialSession session = new TutorialSession();
            float listX = layout.list.x() + layout.list.w() * .55F;
            float listY = layout.list.y() + layout.list.h() * .45F;

            seek(session, TutorialSession.Step.MESSAGE_ACTIONS);
            assertTrue(demo.click(session, layout, listX, listY, 0),
                    "tutorial primary click opens the same production message menu");
            demo.render(surface.getCanvas(), session, layout, key -> key, 0, 0, 200);
            UiLayout.Rect messageMenu = demo.bounds(layout);
            assertTrue(messageMenu.w() > 0 && messageMenu.w() < layout.rect().w());
            assertTrue(demo.click(session, layout, messageMenu.x() + messageMenu.w() / 2,
                    messageMenu.y() + UiTokens.MENU_H * .75F, 0));
            assertTrue(session.demoPerformed(), "quote row runs the menu's real hit geometry");
            UiLayout.Rect quoteBar = demo.bounds(layout);
            assertTrue(quoteBar.h() > 0 && quoteBar.bottom() <= layout.inputBar.y(),
                    "quote demo reserves its own band above the composer");

            seek(session, TutorialSession.Step.PLAYERS_BLOCK);
            assertTrue(demo.click(session, layout, listX, listY, 0),
                    "tutorial primary click opens the same production player menu");
            demo.render(surface.getCanvas(), session, layout, key -> key, 0, 0, 200);
            UiLayout.Rect playerMenu = demo.bounds(layout);
            assertTrue(demo.click(session, layout, playerMenu.x() + playerMenu.w() / 2,
                    playerMenu.y() + UiTokens.MENU_H * 1.25F, 0));
            assertTrue(session.demoPerformed(), "block row is exercised without block-list mutation");

            seek(session, TutorialSession.Step.IMAGES);
            assertTrue(demo.click(session, layout, centerX(layout.imageBtn), centerY(layout.imageBtn), 0));
            demo.render(surface.getCanvas(), session, layout, key -> key, 0, 0, 200);
            UiLayout.Rect tools = demo.bounds(layout);
            assertTrue(demo.click(session, layout, tools.x() + tools.w() / 2,
                    tools.y() + ToolLayout.MENU_ROW_H * 2.5F, 0));
            assertTrue(session.demoPerformed());
            demo.render(surface.getCanvas(), session, layout, key -> key, 0, 0, 200);
            assertEquals(new UiLayout.Rect(layout.list.x(),layout.list.y(),layout.list.w(),layout.list.h()/2), demo.bounds(layout),
                    "image preview leaves the lower viewport available for tutorial annotations");

            seek(session, TutorialSession.Step.SEARCH_FILTER);
            assertTrue(demo.click(session, layout, centerX(layout.imageBtn), centerY(layout.imageBtn), 0));
            demo.render(surface.getCanvas(), session, layout, key -> key, 0, 0, 200);
            UiLayout.Rect searchTools = demo.bounds(layout);
            assertTrue(demo.click(session, layout, searchTools.x() + searchTools.w() / 2,
                    searchTools.y() + ToolLayout.MENU_ROW_H * .5F, 0));
            assertTrue(demo.type(session, 'A'));
            assertTrue(demo.key(session, 257, 0));
            assertTrue(session.demoPerformed());
            float filterX = layout.header.x() + UiTokens.EDGE_CONTROL_INSET * 3
                    + UiTokens.ACTION_BUTTON_SIZE * 1.5F;
            assertTrue(demo.click(session, layout, filterX, centerY(layout.header), 0));
            demo.render(surface.getCanvas(), session, layout, key -> key, 0, 0, 200);
            assertTrue(demo.bounds(layout).h() < layout.list.h(),
                    "search results expose their own compact demo surface for annotation avoidance");
        }
    }

    private static float centerX(UiLayout.Rect rect) { return rect.x() + rect.w() / 2.0F; }
    private static float centerY(UiLayout.Rect rect) { return rect.y() + rect.h() / 2.0F; }

    private static void seek(TutorialSession session, TutorialSession.Step target) {
        session.start();
        while (session.step() != target) session.next();
    }
}
