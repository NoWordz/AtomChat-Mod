package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.platform.Platform;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.tools.ComposerTools;
import com.atom.chat.tools.ToolLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.EncodedImageFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UiFeatureRenderTest {
    private static JsonObject language;
    @BeforeAll static void setup(@TempDir Path root) throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        try (var in = UiFeatureRenderTest.class.getResourceAsStream("/assets/atomchat/lang/zh_cn.json")) {
            assertNotNull(in);
            language = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    static String tr(String key) { assertTrue(language.has(key), key); return language.get(key).getAsString(); }
    protected static class Host implements ComposerTools.Host {
        UiLayout layout = UiLayout.of(15, 15, 420, 780);
        PlayerRef mentioned;
        int picks, presses, releases;
        String text = "draft"; int caret = 2;
        public UiLayout layout() { return layout; }
        public float x(double x) { return (float) x; }
        public float y(double y) { return (float) y; }
        public boolean chatVisible() { return true; }
        public String tr(String key) { return UiFeatureRenderTest.tr(key); }
        public String clipboard() { return "测试关键词"; }
        public List<PlayerRef> onlinePlayers() { return List.of(PlayerRef.of(null, "Alice"), PlayerRef.of(null, "玩家_测试")); }
        public void mention(PlayerRef player) { mentioned = player; }
        public String composerText() { return text; }
        public int composerCaret() { return caret; }
        public void setComposerText(String text) { this.text = text; }
        public void moveComposerCaret(int caret) { this.caret = caret; }
        public void focusComposer() { }
        public Object searchScope() { return "public"; }
        public List<com.atom.chat.chat.ChatMessage> searchMessages() { return List.of(); }
        public boolean revealSearchMessage(com.atom.chat.chat.ChatMessage message) { return true; }
        public void highlightSearchMessage(com.atom.chat.chat.ChatMessage message) { }
        public void feedback(String key) { }
        public void pressTool() { presses++; }
        public void releaseTool() { releases++; }
        public void pickImage() { picks++; }
        public void dismissOtherPopups() { }
    }
    @Test void toolMenuRoutesToPlayersAndImageWithoutTouchingTheChatField() {
        var host = new Host(); var tools = new ComposerTools(host);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            tools.toggle(); tools.render(surface.getCanvas(), 0, 0, 16);
            assertEquals(1, host.presses); tools.onRelease(0, 0, 0); assertEquals(1, host.releases);
            var menu = ToolLayout.menu(host.layout);
            tools.onClick(menu.x() + menu.w() / 2, menu.y() + ToolLayout.MENU_ROW_H * 1.5, 0);
            assertTrue(tools.active());
            tools.render(surface.getCanvas(), 0, 0, 16);
            tools.onClick(menu.x() + menu.w() / 2, menu.y() + ToolLayout.MENU_ROW_H * 1.5, 0);
            assertEquals("Alice", host.mentioned.realName()); assertFalse(tools.active());
            tools.toggle();
            tools.onClick(menu.x() + menu.w() / 2, menu.y() + ToolLayout.MENU_ROW_H * 2.5, 0);
            assertEquals(1, host.picks); assertFalse(tools.active());
        }
    }
    @Test void modalConsumesInputAndItsPaintedButtonsExecuteOnce() {
        var dialog = new ConfirmationDialog(); var calls = new AtomicInteger();
        var input = new ConfirmationInput(() -> dialog, x -> (float) x, y -> (float) y);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            dialog.open("清空聊天记录", tr("atomchat.confirm.history_clear"), "取消", "确认清除", calls::incrementAndGet);
            dialog.render(surface.getCanvas(), new UiLayout.Rect(15, 15, 420, 780), 0, 0, 200);
            assertEquals(0, calls.get());
            assertTrue(input.onScroll(50, 50, 0, -1)); assertTrue(input.onChar('a', 0));
            var confirm = dialog.geometry().confirm();
            assertTrue(input.onClick(confirm.x() + confirm.w() / 2, confirm.y() + confirm.h() / 2, 0));
            assertEquals(1, calls.get()); assertFalse(dialog.hasPendingAction());
            dialog.render(surface.getCanvas(), hostPanel(), 0, 0, 200); assertFalse(dialog.isActive());
            dialog.click(confirm.x(), confirm.y(), 0); assertEquals(1, calls.get());
            dialog.open("清空聊天记录", "测试", "取消", "确认清除", calls::incrementAndGet);
            dialog.render(surface.getCanvas(), new UiLayout.Rect(15, 15, 420, 780), 0, 0, 200);
            assertTrue(input.onClick(-100, -100, 0)); assertFalse(dialog.hasPendingAction()); assertEquals(1, calls.get());
            dialog.render(surface.getCanvas(), hostPanel(), 0, 0, 200); assertFalse(dialog.isActive());
        }
    }
    private static UiLayout.Rect hostPanel() { return new UiLayout.Rect(15, 15, 420, 780); }
    @Test void repeatedFramesKeepCanvasBalancedAndExportActualComponents() throws Exception {
        var host = new Host(); var tools = new ComposerTools(host);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            var canvas = surface.getCanvas(); int saved = canvas.getSaveCount();
            tools.toggle();
            var menu = ToolLayout.menu(host.layout);
            for (int i = 0; i < 12; i++) { backdrop(canvas, host.layout); tools.render(canvas, menu.x()+menu.w()/2, menu.y()+ToolLayout.MENU_ROW_H/2, 16); assertEquals(saved, canvas.getSaveCount()); }
            save(surface, "tool-menu.png");
            tools.onClick(menu.x() + menu.w() / 2, menu.y() + ToolLayout.MENU_ROW_H / 2, 0);
            assertFalse(tools.onChar('建', 0), "typing reaches the native composer");
            host.text = "建造"; host.layout = UiLayout.of(15, 15, 420, 780, 0, 0, false, true);
            tools.update();
            backdrop(canvas, host.layout); tools.render(canvas, 0, 0, 16); save(surface, "search-composer.png");
            assertEquals(saved, canvas.getSaveCount());
            var dialog = new ConfirmationDialog(); dialog.open("清空聊天记录", tr("atomchat.confirm.history_clear"), "取消", "确认清除", () -> {});
            backdrop(canvas, host.layout); dialog.render(canvas, host.layout.rect(), 0, 0, 200); save(surface, "confirmation.png");
            host.layout = UiLayout.of(15, 15, 260, 310);
            backdrop(canvas, host.layout); dialog.render(canvas, host.layout.rect(), 0, 0); save(surface, "confirmation-narrow.png");
            assertTrue(host.layout.rect().contains(dialog.geometry().surface()));
            assertTrue(dialog.geometry().surface().contains(dialog.geometry().cancel()));
            assertTrue(dialog.geometry().surface().contains(dialog.geometry().confirm()));
            host.layout = UiLayout.of(15, 15, 420, 780);
            backdrop(canvas, host.layout);
            var labels = List.of("gamemode", "gamerule", "give", "help", "list", "locate").stream().map(com.atom.chat.render.FormattedLine::plain).toList();
            var geometry = SuggestionGeometry.of(host.layout, labels.size(), SuggestionPopup.measure(labels));
            var completion = new SuggestionPopup();
            for (int i = 0; i < 12; i++) {
                backdrop(canvas, host.layout);
                completion.draw(canvas, geometry, labels, 0, 0, geometry.bounds().x()+30, geometry.bounds().y()+15, 16);
            }
            save(surface, "command-completion.png"); assertEquals(saved, canvas.getSaveCount());
        }
    }
    @Test void exportAllPresetSurfacesUsingActualConfirmationDialogs() throws Exception {
        AtomChatConfig config = AtomChatConfig.get();
        var original = new Gson().fromJson(new Gson().toJson(config), AtomChatConfig.class);
        try (var surface = Surface.makeRasterN32Premul(1500, 1380)) {
            var canvas = surface.getCanvas(); canvas.clear(0xFF20252D);
            var ids = new java.util.ArrayList<String>(); ids.add(ThemeService.FROSTED);
            for (var preset : ThemeService.presets()) ids.add(preset.id());
            for (int i = 0; i < ids.size(); i++) {
                ThemeService.apply(config, ids.get(i));
                var host = new Host();
                host.layout = UiLayout.of(20 + (i % 4) * 375, 55 + (i / 4) * 340, 335, 260);
                var rect = host.layout.rect();
                SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(), UiTokens.radius(16),
                        PanelBackground.tintFor(true, true, config.panelBgColor, config.panelOpacity));
                var dialog = new ConfirmationDialog();
                dialog.open("清空聊天记录", "将清空当前聊天记录，此操作无法撤销。", "取消", "确认清除", () -> {});
                dialog.render(canvas, rect, -1, -1, 200);
                String name = ids.get(i).equals(ThemeService.FROSTED) ? "默认" : tr("atomchat.settings.theme." + ids.get(i));
                SkiaFontRenderer.drawText(canvas, FontManager.font(UiTokens.FONT_BUTTON), name, rect.x(), rect.y() - 12, 0xFFFFFFFF);
            }
            save(surface, "theme-contact-sheet.png");
        } finally {
            for (var field : AtomChatConfig.class.getFields()) if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) field.set(config, field.get(original));
        }
    }
    private static void backdrop(Canvas canvas, UiLayout layout) {
        canvas.clear(0xFF343D4B);
        SkiaDraw.drawRoundedRect(canvas, layout.panelX, layout.panelY, layout.panelW, layout.panelH,
                UiTokens.radius(16), AtomChatConfig.get().panelBgColor);
    }
    private static void save(Surface surface, String name) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("shared"))) root = root.getParent();
        assertNotNull(root);
        Path dir = root.resolve(".work/2026-10-28-ui").resolve(Path.of("").toAbsolutePath().getFileName());
        Files.createDirectories(dir);
        try (var image = surface.makeImageSnapshot(); var data = image.encodeToData(EncodedImageFormat.PNG)) {
            assertNotNull(data); Files.write(dir.resolve(name), data.getBytes());
        }
    }
}
