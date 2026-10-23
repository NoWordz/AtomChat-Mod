package com.atom.chat.notification;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.PreviewLanguage;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class NotificationPresentationPreviewTest {
    @Test void renderActualNotificationCardsAcrossOpacityAndPolarity() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance"); field.setAccessible(true);
        Object before = field.get(null);
        var config = new AtomChatConfig(); field.set(null, config);
        try (var language = PreviewLanguage.install("zh_cn");
             var surface = Surface.makeRasterN32Premul(2400, 520); var paint = new Paint()) {
            var canvas = surface.getCanvas();
            canvas.clear(0xFF30353D);
            for (int row = 0; row < 2; row++) {
                config.cardColor = row == 0 ? 0xFF20252E : 0xFFF4F6FA;
                for (int col = 0; col < 4; col++) {
                    config.cardTint = new float[]{0, .5F, .75F, 1}[col];
                    int save = canvas.save();
                    canvas.translate(col * 600 + 20, row * 260 + 20);
                    for (int x = 0; x < 560; x += 8) {
                        paint.setColor((x / 8) % 2 == 0 ? 0xFF447487 : 0xFF253546);
                        canvas.drawRect(Rect.makeXYWH(x, 55, 8, 170), paint);
                    }
                    String label = (row == 0 ? "Dark" : "Light") + " card opacity " + Math.round(config.cardTint * 100) + "%";
                    if (col == 0) label += " (50% floor)";
                    SkiaFontRenderer.drawTextCentered(canvas, FontManager.font(20), label, 280, 24, 0xFFFFFFFF);
                    canvas.translate(10, 105);
                    new NotificationBanner(() -> 1000).renderPreview(canvas, 540);
                    canvas.restoreToCount(save);
                    assertEquals(save, canvas.getSaveCount());
                }
            }
            Path output = Path.of("build/test-artifacts/notification-materials.png");
            Files.createDirectories(output.getParent());
            try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) { Files.write(output, data.getBytes()); }
            assertTrue(Files.size(output) > 1000);
        } finally { field.set(null, before); }
    }
}
