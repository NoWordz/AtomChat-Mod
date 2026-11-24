package com.atom.chat.render;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.page.MessageListView;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.ui.ScrollController;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Native Skija previews. Classic/round join proposals deliberately stay outside production. */
class BubbleV096PreviewTest {
    @BeforeAll static void platform(@TempDir Path directory) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return directory; }
            public Path gameDir() { return directory; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    private static void text(Canvas canvas, String text, float x, float baseline, int color) {
        SkiaFontRenderer.drawText(canvas, FontManager.font(UiTokens.FONT_BODY), text, x, baseline, color);
    }

    @Test void compareNativeBubbleDesigns() throws Exception {
        try (Surface surface = Surface.makeRasterN32Premul(1440, 1030)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFF10141B);
            String[] titles = {"单尖 · 本版实现", "经典 · 分组候选", "全圆 · 分组候选"};
            for (int column = 0; column < 3; column++) {
                float left = column * 480F;
                CornerGeometry.drawRoundedRect(canvas, left + 12, 12, 456, 1006, 20, 0xFF191F29);
                text(canvas, titles[column], left + 32, 60, 0xFFF4F7FC);
                text(canvas, "他人                   自己", left + 32, 112, 0xFFAAB8CB);
                for (int count = 1; count <= 3; count++) {
                    float top = count == 1 ? 205 : count == 2 ? 400 : 685;
                    text(canvas, count + " 条连续消息", left + 32, top - 28, 0xFFB8C7DB);
                    for (int i = 0; i < count; i++) for (boolean own : new boolean[]{false, true}) {
                        var group = ChatBubble.Group.of(i > 0, i + 1 < count);
                        float x = left + (own ? 254 : 32), y = top + i * 62;
                        float width = i == 1 ? 154 : 180;
                        if (own) x += 180 - width;
                        int color = own ? 0xFF1989FA : 0xFF303D52;
                        if (column == 0) {
                            ChatBubble.draw(canvas, x, y, width, 52, AtomChatConfig.BubbleStyle.CORNER, own, color, group);
                        } else {
                            proposal(canvas, x, y, width, 52, column == 1, own, group, color);
                        }
                        String label = count == 1 ? "单条消息" : i == 0 ? "第一条" : i + 1 == count ? "最后一条" : "中间一条";
                        SkiaFontRenderer.drawTextCentered(canvas, FontManager.font(UiTokens.FONT_BODY),
                                label, x + width / 2, y + 26, 0xFFFFFFFF);
                    }
                }
                text(canvas, column == 0 ? "本体尖角：首尾相向，中间小圆弧" : column == 1
                        ? "保留小圆角，连接侧进一步收小" : "保留饱满外缘，连接侧收为小圆角", left + 32, 945, 0xFFAAB8CB);
            }
            text(canvas, "Skija 原生组件实绘 · 后两列仅预览，未改变对应样式的正式行为", 32, 995, 0xFF8092AC);
            save(surface, "bubble-styles.png");
        }
    }

    @Test void compareTheMiddleJoinAgainstTheUserReference() throws Exception {
        try (var surface = Surface.makeRasterN32Premul(1000, 480)) {
            var canvas = surface.getCanvas();
            canvas.clear(0xFF10141B);
            for (int side = 0; side < 2; side++) {
                float start = side * 500F;
                CornerGeometry.drawRoundedRect(canvas, start + 12, 12, 476, 456, 20, 0xFF191F29);
                text(canvas, side == 0 ? "上一版：通用圆角" : "修正：与首尾共用圆弧", start + 32, 60, 0xFFF4F7FC);
                text(canvas, "头像侧在右边", start + 250, 120, 0xFFAAB8CB);
                String[] labels = {"外貌描写", "1234234", "会话"};
                float[] widths = {180, 152, 100};
                float height = SkiaFontRenderer.getHeight(FontManager.font(UiTokens.FONT_BODY)) + UiTokens.BUBBLE_PAD_Y;
                for (int i = 0; i < 3; i++) {
                    var group = ChatBubble.Group.of(i > 0, i < 2);
                    float x = start + 440 - widths[i], y = 152 + i * (height + com.atom.chat.ui.MessageGeometry.groupedGap());
                    if (i == 1) {
                        if (side == 0) proposal(canvas, x, y, widths[i], height, false, true, group, 0xFF1989FA);
                        else ChatBubble.draw(canvas, x, y, widths[i], height, AtomChatConfig.BubbleStyle.CORNER,
                                true, 0xFF1989FA, group);
                    } else {
                        ChatBubble.draw(canvas, x, y, widths[i], height, AtomChatConfig.BubbleStyle.CORNER,
                                true, 0xFF1989FA, group);
                    }
                    SkiaFontRenderer.drawTextCentered(canvas, FontManager.font(UiTokens.FONT_BODY),
                            labels[i], x + widths[i] / 2, y + height / 2, 0xFFFFFFFF);
                }
                text(canvas, side == 0 ? "通用连续曲线让左侧显得更平" : "左侧圆弧与首尾一致，右侧连接角收小",
                        start + 32, 406, 0xFFAAB8CB);
            }
            float height = SkiaFontRenderer.getHeight(FontManager.font(UiTokens.FONT_BODY)) + UiTokens.BUBBLE_PAD_Y;
            var path = output("middle-circle-v4-geometry.txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, "height=" + height + "\nradius=" + UiTokens.BUBBLE_ROUND_RADIUS
                    + "\noutline=" + java.util.Arrays.toString(ChatBubble.outline(0, 0, 152, height,
                    AtomChatConfig.BubbleStyle.CORNER, true, 1, ChatBubble.Group.MIDDLE)._radii));
            save(surface, "middle-circle-v4.png");
        }
    }

    private static void proposal(Canvas canvas, float x, float y, float width, float height, boolean classic,
                                 boolean own, ChatBubble.Group group, int color) {
        float radius = Math.min(height / 2, classic ? UiTokens.BUBBLE_RADIUS : UiTokens.BUBBLE_ROUND_RADIUS);
        float joined = Math.min(radius, classic ? 3F : 6F);
        boolean previous = group == ChatBubble.Group.MIDDLE || group == ChatBubble.Group.LAST;
        boolean next = group == ChatBubble.Group.FIRST || group == ChatBubble.Group.MIDDLE;
        float top = previous ? joined : radius, bottom = next ? joined : radius;
        float tl = own ? radius : top, tr = own ? top : radius;
        float bl = own ? radius : bottom, br = own ? bottom : radius;
        CornerGeometry.fill(canvas, RRect.makeComplexLTRB(x, y, x + width, y + height,
                new float[]{tl, tl, tr, tr, br, br, bl, bl}), color);
    }

    @Test void renderTheProductionPanelAndHud() throws Exception {
        var cfg = AtomChatConfig.get();
        var old = AppearancePresetStore.Snapshot.capture(cfg);
        boolean oldCompact = cfg.compactMessagesEnabled, oldPerformance = cfg.performanceMode;
        int oldInterval = cfg.timestampIntervalMinutes;
        var hud = new ChatHudRenderer();
        try (Surface surface = Surface.makeRasterN32Premul(1440, 950)) {
            cfg.bubbleStyle = AtomChatConfig.BubbleStyle.CORNER;
            cfg.compactMessagesEnabled = true;
            cfg.timestampIntervalMinutes = 0;
            cfg.performanceMode = false;
            cfg.ownBubbleColor = 0xFF1989FA;
            cfg.otherBubbleColor = 0xFF303D52;
            cfg.bubbleTextColor = cfg.otherBubbleTextColor = 0xFFFFFFFF;
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFF10141B);
            CornerGeometry.drawRoundedRect(canvas, 16, 16, 692, 918, 24, 0xFF1A202B);
            CornerGeometry.drawRoundedRect(canvas, 732, 16, 692, 918, 24, 0xFF1A202B);
            text(canvas, "聊天面板 · 实际列表渲染", 40, 60, 0xFFF4F7FC);
            text(canvas, "聊天 HUD · 实际排版渲染", 756, 60, 0xFFF4F7FC);
            text(canvas, "自己与他人的气泡左右镜像", 40, 105, 0xFFAAB8CB);
            text(canvas, "所有头像保持在左侧，尖角跟随头像侧", 756, 105, 0xFFAAB8CB);
            var rows = new ArrayList<ChatMessage>();
            String[] labels = {"这是第一条消息", "第二条紧靠同一组", "中间气泡没有尖角", "这是这一组的最后一条"};
            for (boolean own : new boolean[]{false, true}) for (int i = 0; i < labels.length; i++) {
                String name = own ? "E33EPUS" : "Player";
                rows.add(new ChatMessage(RichText.literal(labels[i]).toText(), own, false,
                        null, null, new UUID(0, own ? 1 : 2), name, name, labels[i], 1000L + rows.size()));
            }
            var host = new MessageListView.Host() {
                public UUID ownUuid() { return new UUID(0, 1); }
                public String ownName() { return "E33EPUS"; }
                public String senderName(ChatMessage message) { return message.getSenderName(); }
                public long openStart() { return System.currentTimeMillis(); }
            };
            var view = new MessageListView(host);
            try {
                view.draw(canvas, 40, 150, 644, 760, rows, new ScrollController());
                assertEquals(8, view.hits().size());
            } finally { view.dispose(); }
            int saved = canvas.save();
            canvas.translate(756, 150);
            var newestFirst = new ArrayList<>(rows);
            java.util.Collections.reverse(newestFirst);
            hud.paint(canvas, newestFirst, new ChatHudRenderer.Viewport(4, 750, 635, 745, 1, 1),
                    new UUID(0, 1), "E33EPUS", "Player", cfg, 8, 255, 1, false, 0);
            canvas.restoreToCount(saved);
            save(surface, "panel-hud.png");
        } finally {
            hud.clear();
            old.applyTo(cfg);
            cfg.compactMessagesEnabled = oldCompact;
            cfg.performanceMode = oldPerformance;
            cfg.timestampIntervalMinutes = oldInterval;
        }
    }

    private static void save(Surface surface, String name) throws Exception {
        Path path = output(name);
        Files.createDirectories(path.getParent());
        try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) {
            Files.write(path, data.getBytes());
        }
        assertTrue(Files.size(path) > 1000);
    }

    private static Path output(String name) {
        return Path.of(System.getProperty("atomchat.repoRoot", "../.."), "build/v096/previews",
                System.getProperty("atomchat.golden.target", "local"), name);
    }
}
