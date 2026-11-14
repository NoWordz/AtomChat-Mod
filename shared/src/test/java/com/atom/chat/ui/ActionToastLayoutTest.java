package com.atom.chat.ui;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.lib.design.atomchat.UiTokens;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import io.github.humbleui.skija.Surface;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ActionToastLayoutTest {
    @Test void longTextWrapsAndRowsStackWithoutOverlap() {
        var font = FontManager.font(UiTokens.FONT_NAME);
        var longRow = ActionToast.layout(font, "教程预览：此入口只讲解用途，不上传、删除或修改真实文件", 220);
        assertTrue(longRow.lines().size() > 1);
        for (var line : longRow.lines()) {
            assertTrue(SkiaFontRenderer.getStringWidth(font, line) <= longRow.textWidth() + 1);
        }
        assertTrue(longRow.height() > UiTokens.s(36));
        assertTrue(longRow.width() <= 220 - UiTokens.s(32));
    }

    @Test void rendersLongAndShortMessagesTogether(@TempDir Path tmp) throws Exception {
        var field = Platform.class.getDeclaredField("provider");
        field.setAccessible(true);
        var previous = field.get(null);
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp; }
            public Path gameDir() { return tmp; }
            public boolean isModLoaded(String id) { return false; }
        });
        try {
        var feedback = new ActionFeedback();
        feedback.show("long", "教程预览：此入口只讲解用途，不上传、删除或修改真实文件", ActionFeedback.Outcome.ERROR, 1000);
        feedback.show("short", "已保存", ActionFeedback.Outcome.SUCCESS, 1000);
        try (var surface = Surface.makeRasterN32Premul(440, 320)) {
            var canvas = surface.getCanvas();
            canvas.clear(0xFF454B55);
            ActionToast.render(canvas, feedback, 20, 400, 0, 300, 1500, key -> key);
            Path out = Path.of(System.getProperty("atomchat.repoRoot", "../.."), ".work/evidence/2026-11-13-followup/toast-render.png");
            Files.createDirectories(out.getParent());
            try (var image = surface.makeImageSnapshot(); var png = image.encodeToData()) {
                Files.write(out, png.getBytes());
            }
        }
        } finally { field.set(null, previous); }
    }
}
