package com.atom.chat.ui;

import com.atom.chat.tools.ComposerTools;
import com.atom.chat.tools.ToolLayout;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolMenuInteractionTest {
    @org.junit.jupiter.api.BeforeAll static void setup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        UiFeatureRenderTest.setup(root);
    }
    @Test void registeredMenuEditsDriveTheSamePaintedAndClickableRows() {
        var entries = new com.atom.chat.api.ui.UiContributions<com.atom.chat.api.ui.ComposerUi.MenuItem>();
        entries.remove("example:hide_search", com.atom.chat.api.ui.ComposerUi.SEARCH);
        entries.add("example:custom", new com.atom.chat.api.ui.ComposerUi.MenuItem(
                "example.insert", TablerIcons.PHOTO, context -> context.replaceDraft("Addon content")),
                com.atom.chat.api.ui.UiContributions.Position.first());
        var host = new UiFeatureRenderTest.Host() {
            @Override public com.atom.chat.api.ui.UiContributions<com.atom.chat.api.ui.ComposerUi.MenuItem> menuExtensions() {
                return entries;
            }

            @Override public String tr(String key) {
                return key.equals("example.insert") ? "Insert example" : super.tr(key);
            }
        };
        var tools = new ComposerTools(host);
        tools.toggle();
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            tools.render(surface.getCanvas(), -1, -1, 200);
            assertNull(tools.tutorialActionBounds(0), "Removed built-in search has no painted slot");
            var custom = tools.menuActionBounds("example:custom");
            assertNotNull(custom);
            assertTrue(tapTool(tools, custom.x() + custom.w() / 2, custom.y() + custom.h() / 2, 0));
            assertEquals("Addon content", host.text);
            assertFalse(tools.active());
        }
    }

    @Test void composerActionCannotSubmitAfterTheConversationOrInvocationEnds() {
        var entries = new com.atom.chat.api.ui.UiContributions<com.atom.chat.api.ui.ComposerUi.MenuItem>();
        var submitted = new java.util.concurrent.atomic.AtomicInteger();
        var draftAfterSubmit = new java.util.concurrent.atomic.AtomicReference<String>();
        var leaked = new java.util.concurrent.atomic.AtomicReference<com.atom.chat.api.ui.ComposerUi.Context>();
        entries.add("example:scope", new com.atom.chat.api.ui.ComposerUi.MenuItem(
                "example.scope", TablerIcons.PHOTO, context -> {
                    leaked.set(context);
                    context.submitDraft();
                    draftAfterSubmit.set(context.draft());
                    context.replaceDraft("must not replace a restored draft");
                    context.insertText("must not insert");
                    context.submitDraft();
                    context.openPage("example:page");
                    context.submitDraft();
                    context.replaceDraft("must not change the departed route");
                }), com.atom.chat.api.ui.UiContributions.Position.first());
        var host = new UiFeatureRenderTest.Host() {
            private String route = "private:alice";
            @Override public Object searchScope() { return route; }
            @Override public void openExtensionPage(String id) { route = id; }
            @Override public void submitExtensionDraft() { submitted.incrementAndGet(); }
            @Override public String tr(String key) {
                return key.equals("example.scope") ? "Open example" : super.tr(key);
            }
            @Override public com.atom.chat.api.ui.UiContributions<com.atom.chat.api.ui.ComposerUi.MenuItem> menuExtensions() {
                return entries;
            }
        };
        var tools = new ComposerTools(host);
        tools.toggle();
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            tools.render(surface.getCanvas(), -1, -1, 200);
            var row = tools.menuActionBounds("example:scope");
            assertTrue(tapTool(tools, row.x() + row.w()/2, row.y() + row.h()/2, 0));
        }
        assertEquals(1, submitted.get());
        assertEquals("", draftAfterSubmit.get());
        assertEquals("draft", host.text);
        leaked.get().submitDraft();
        assertEquals(1, submitted.get());
    }

    @Test void openingClosingAndHoverRemainOnThePopupTimeline() {
        var host = new UiFeatureRenderTest.Host(); var tools = new ComposerTools(host);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            var canvas = surface.getCanvas();
            tools.toggle();
            canvas.clear(0); tools.render(canvas, -1, -1, 1);
            var early = pixels(surface);
            canvas.clear(0); tools.render(canvas, -1, -1, 200);
            assertFalse(java.util.Arrays.equals(early, pixels(surface)), "opening must fade and scale like sibling popups");
            var row = ToolLayout.menu(host.layout);
            canvas.clear(0); tools.render(canvas, row.x() + 40, row.y() + 20, 1);
            var hoverEarly = pixels(surface);
            canvas.clear(0); tools.render(canvas, row.x() + 40, row.y() + 20, 90);
            assertFalse(java.util.Arrays.equals(hoverEarly, pixels(surface)), "hover must fade and bounce, not jump to a flat wash");
            tapTool(tools, -20, -20, 0);
            canvas.clear(0); tools.render(canvas, -1, -1, 1);
            assertTrue(nonzero(pixels(surface)), "closing must keep painting the outgoing menu");
            canvas.clear(0); tools.render(canvas, -1, -1, 200);
            assertFalse(nonzero(pixels(surface)), "outgoing popup must release its surface when closed");
        }
    }
    @Test void toolsAndRowsActivateOnPressWithoutClickingThrough() {
        var host = new UiFeatureRenderTest.Host();
        var tools = new ComposerTools(host);
        var gate = new com.atom.chat.ui.input.InputGate(() -> false).add(tools);
        var button = host.layout.imageBtn;
        double x = button.x() + button.w() / 2;
        double y = button.y() + button.h() / 2;
        assertTrue(gate.click(x, y, 0));
        assertEquals(1, host.presses, "the button responds to the press");
        assertTrue(tools.active(), "the menu starts opening on press");
        assertTrue(gate.release(x, y, 0));
        assertTrue(tools.active());

        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            tools.render(surface.getCanvas(), -1, -1, 200);
            var image = tools.tutorialActionBounds(2);
            var players = tools.tutorialActionBounds(1);
            double rowX = image.x() + image.w() / 2;
            double rowY = image.y() + image.h() / 2;
            assertTrue(gate.click(rowX, rowY, 0));
            assertEquals(1, host.picks);
            assertTrue(gate.release(rowX, players.y() + players.h() / 2, 0));
            assertEquals(1, host.picks);
            assertFalse(tools.active());
            gate.release(rowX, rowY, 0);
            assertEquals(1, host.picks);
        }
    }

    @Test void popupWidensForTheHeldItemLabelWithinThePanel() {
        var host = new UiFeatureRenderTest.Host() {
            @Override public String tr(String key) {
                return key.equals("atomchat.tools.share_item") ? "Share the held equipment with everyone" : super.tr(key);
            }
        };
        var tools = new ComposerTools(host);
        tools.toggle();
        var item = tools.tutorialActionBounds(4);
        assertTrue(item.w() > ToolLayout.menu(host.layout).w());
        assertTrue(item.right() <= host.layout.rect().right());
    }

    @Test void theOperatorOnlyRowUnderBackSendsImmediatelyAndDoesNotInsertADraft() {
        var host = new UiFeatureRenderTest.Host() {
            @Override public boolean canMentionAll() { return true; }
            @Override public void mentionAll() { presses++; }
        };
        var tools = new ComposerTools(host);
        tools.toggle();
        var players = tools.tutorialActionBounds(1);
        assertTrue(tapTool(tools, players.x() + players.w() / 2, players.y() + players.h() / 2, 0));
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            tools.render(surface.getCanvas(), -1, -1, 200);
            var menu = ToolLayout.menu(host.layout, 4);
            int initialPresses = host.presses;
            var gate = new com.atom.chat.ui.input.InputGate(() -> false).add(tools);
            double x = menu.x() + 30;
            double y = menu.y() + 1.5 * ToolLayout.MENU_ROW_H;
            assertTrue(gate.click(x, y, 0));
            assertEquals(initialPresses + 1, host.presses);
            assertEquals("draft", host.text);
            assertNull(host.mentioned);
            assertTrue(gate.release(x, y, 0));
            assertEquals(initialPresses + 1, host.presses);
        }
    }

    @Test void playersReplaceTheMenuAndDoNotTakeOverTheConversation() {
        var host = new UiFeatureRenderTest.Host(); var tools = new ComposerTools(host);
        tools.toggle();
        var bounds = ToolLayout.menu(host.layout);
        tapTool(tools, bounds.x() + 30, bounds.y() + ToolLayout.MENU_ROW_H * 1.5, 0);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            surface.getCanvas().clear(0); tools.render(surface.getCanvas(), -1, -1, 200);
            var bytes = pixels(surface);
            int x = (int) (host.layout.header.x() + 20), y = (int) (host.layout.header.y() + 20);
            assertEquals(0, bytes[(y * 460 + x) * 4 + 3], "the tools popup must not replace the conversation header");
        }
        assertTrue(tools.active());
    }
    @Test void searchBorrowsNativeTypingAndConsumesEverySendAction() {
        var host = new UiFeatureRenderTest.Host(); var tools = new ComposerTools(host);
        tools.toggle(); var bounds = ToolLayout.menu(host.layout);
        tapTool(tools, bounds.x() + 30, bounds.y() + ToolLayout.MENU_ROW_H / 2, 0);
        assertTrue(tools.searching()); assertEquals("", host.text);
        assertFalse(tools.onChar('中', 0));
        host.text = "中文关键词"; tools.update();
        assertTrue(tools.onKey(257, 0, 0)); assertEquals("中文关键词", host.text);
        assertTrue(tapTool(tools, host.layout.sendBtn.x() + 5, host.layout.sendBtn.y() + 5, 0));
        assertEquals(0, host.picks);
        assertTrue(tools.onKey(256, 0, 0)); assertFalse(tools.searching());
        assertEquals("draft", host.text); assertEquals(2, host.caret);
    }
    @Test void openingAnotherComposerBorrowRestoresTheDraftFirst() {
        var host = new UiFeatureRenderTest.Host(); var tools = new ComposerTools(host);
        tools.toggle(); var bounds = ToolLayout.menu(host.layout);
        tapTool(tools, bounds.x() + 30, bounds.y() + ToolLayout.MENU_ROW_H / 2, 0);
        host.text = "query";
        assertFalse(tapTool(tools, host.layout.phraseBtn.x() + 5, host.layout.phraseBtn.y() + 5, 0));
        assertFalse(tools.searching()); assertEquals("draft", host.text);
    }
    private static boolean tapTool(ComposerTools tools, double x, double y, int button) {
        return UiFeatureRenderTest.tapTool(tools, x, y, button);
    }
    private static byte[] pixels(Surface surface) {
        try (var bitmap = new io.github.humbleui.skija.Bitmap()) {
            bitmap.allocN32Pixels(460, 820); assertTrue(surface.readPixels(bitmap, 0, 0)); return bitmap.readPixels();
        }
    }
    private static boolean nonzero(byte[] pixels) { for (byte b : pixels) if (b != 0) return true; return false; }
}
