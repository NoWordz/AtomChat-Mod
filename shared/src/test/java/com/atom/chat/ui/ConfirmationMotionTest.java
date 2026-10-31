package com.atom.chat.ui;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmationMotionTest {
    @BeforeAll static void setup(@TempDir Path root) throws Exception { UiFeatureRenderTest.setup(root); }
    @Test void openAndHoverAdvanceAcrossFramesInsteadOfAppearingInstantly() {
        var dialog = new ConfirmationDialog(); var panel = new UiLayout.Rect(10, 10, 430, 800);
        dialog.open("清空记录", "删除当前聊天记录？", "取消", "确认", () -> {});
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            var canvas = surface.getCanvas();
            canvas.clear(0); dialog.render(canvas, panel, -1, -1); var early = pixels(surface);
            for (int i = 0; i < 12; i++) { canvas.clear(0); dialog.render(canvas, panel, -1, -1); }
            assertFalse(Arrays.equals(early, pixels(surface)), "modal entrance must animate");
            var button = dialog.geometry().confirm();
            canvas.clear(0); dialog.render(canvas, panel, button.x() + 20, button.y() + 10); var hover = pixels(surface);
            for (int i = 0; i < 12; i++) { canvas.clear(0); dialog.render(canvas, panel, button.x() + 20, button.y() + 10); }
            assertFalse(Arrays.equals(hover, pixels(surface)), "button hover must fade and bounce");
        }
    }
    @Test void exitKeepsOwningInputAndNeverExecutesAnActionTwice() {
        var dialog = new ConfirmationDialog(); var count = new java.util.concurrent.atomic.AtomicInteger();
        var input = new ConfirmationInput(() -> dialog, x -> (float)x, y -> (float)y);
        dialog.open("清空", "删除？", "取消", "确认", count::incrementAndGet);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            var canvas = surface.getCanvas(); var panel = new UiLayout.Rect(10, 10, 430, 800);
            for (int i = 0; i < 12; i++) dialog.render(canvas, panel, -1, -1);
            var button = dialog.geometry().confirm();
            input.onClick(button.x() + 10, button.y() + 10, 0);
            assertEquals(1, count.get());
            assertTrue(input.onChar('x', 0), "exit animation still owns input");
            input.onClick(button.x() + 10, button.y() + 10, 0); assertEquals(1, count.get());
            for (int i = 0; i < 12; i++) dialog.render(canvas, panel, -1, -1);
            assertFalse(dialog.isActive()); assertFalse(input.onChar('x', 0));
        }
    }
    @Test void openingCannotHitTheUnscaledDestructiveButton() {
        var dialog = new ConfirmationDialog(); var count = new java.util.concurrent.atomic.AtomicInteger();
        dialog.open("清空", "删除？", "取消", "确认", count::incrementAndGet);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            var panel = new UiLayout.Rect(10, 10, 430, 800);
            dialog.render(surface.getCanvas(), panel, -1, -1);
            var button = dialog.geometry().confirm();
            dialog.click(button.right() - 1, button.bottom() - 1, 0);
            assertEquals(0, count.get()); assertTrue(dialog.hasPendingAction());
        }
    }
    @Test void pickerStyleDialogDoesNotChangeWithThemeBlur() {
        var config = com.atom.chat.config.AtomChatConfig.get(); boolean wasBlur = config.blurEnabled;
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            byte[][] frames = new byte[2][];
            for (int pass = 0; pass < 2; pass++) {
                config.blurEnabled = pass == 1;
                var canvas = surface.getCanvas(); canvas.clear(0xFFFFFFFF);
                for (int y = 0; y < 820; y += 8) com.atom.lib.design.atomchat.SkiaDraw.drawRoundedRect(canvas, 0, y, 460, 4, 0, 0xFF000000);
                var dialog = new ConfirmationDialog(); dialog.open("清空", "删除？", "取消", "确认", () -> {});
                dialog.render(canvas, new UiLayout.Rect(10, 10, 430, 800), -1, -1, UiMotion.POPUP_MS / 2F);
                frames[pass] = pixels(surface);
            }
            assertArrayEquals(frames[0], frames[1], "the accepted picker material does not use theme blur");
        } finally { config.blurEnabled = wasBlur; }
    }
    private static byte[] pixels(Surface surface) {
        try (var bitmap = new Bitmap()) { bitmap.allocN32Pixels(460, 820); assertTrue(surface.readPixels(bitmap, 0, 0)); return bitmap.readPixels(); }
    }
}
