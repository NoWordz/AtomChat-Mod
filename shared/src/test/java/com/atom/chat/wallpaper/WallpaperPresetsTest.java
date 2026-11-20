package com.atom.chat.wallpaper;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallpaperPresetsTest {
    @Test void everyPresetCompilesAndRendersAStaticPng() throws Exception {
        var presets = WallpaperPresets.presets();
        assertEquals(java.util.List.of("cobalt", "planes", "fluid", "ribbons", "strata", "chalk", "silk"),
                java.util.Arrays.stream(presets).map(WallpaperPresets.Preset::id).toList());
        Path out = Path.of("../../.work/v0.7.0-feedback/wallpaper-previews");
        Files.createDirectories(out);
        for (var preset : presets) {
            String id = preset.id();
            byte[] png = WallpaperPresets.renderPng(id, 480, 860);
            assertTrue(png.length > 1000, id);
            Files.write(out.resolve(id + ".png"), png);
            try (var image = io.github.humbleui.skija.Image.makeFromEncoded(png)) {
                assertEquals(480, image.getWidth());
                assertEquals(860, image.getHeight());
            }
        }
    }
    @Test void unknownPresetAndOversizedRasterAreRejected() throws Exception {
        Class<?> type = Class.forName("com.atom.chat.wallpaper.WallpaperPresets");
        var method = type.getMethod("renderPng", String.class, int.class, int.class);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(null, "../../oops", 480, 860));
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(null, "cobalt", 0, 860));
    }
}
