package atomchatprobe;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.class_310;
import net.minecraft.class_318;

/** Visits all quality, theme and scale combinations while the real panel renders. */
final class ReplayMatrix {
    static final boolean ENABLED = Boolean.getBoolean("atomchat.replay.matrix");
    static final long STEP_NANOS = 1_500_000_000L;
    static final int STEPS = 18;
    private static int index = -1;
    private static int panelDark, cardDark, primaryDark, secondaryDark;
    private static double originalScale;
    private static GraphicsQuality originalQuality;
    private static boolean captured;
    static String key;
    static long started;

    private ReplayMatrix() {}

    static void reset() {
        index = -1;
        key = null;
    }

    static void tick(class_310 client, String scene, long elapsed, long now) {
        if (!ENABLED || (!scene.equals("media") && !scene.equals("text"))) return;
        int step = (int) (elapsed / STEP_NANOS);
        if (step != index && key != null && !captured) {
            // The last frame belongs to the outgoing step, even after a long stall.
            capture(client);
        }
        if (step >= STEPS) {
            if (key != null) restore(client);
            return;
        }
        if (step != index) {
            var config = AtomChatConfig.get();
            if (index < 0) {
                originalScale = client.method_22683().method_4495();
                originalQuality = config.graphicsQuality;
                panelDark = config.panelBgColor;
                cardDark = config.cardColor;
                primaryDark = config.textPrimaryColor;
                secondaryDark = config.textSecondaryColor;
            }
            index = step;
            int scale = step / 6 + 1;
            boolean light = (step / 3) % 2 == 1;
            var quality = GraphicsQuality.values()[step % 3];
            config.panelBgColor = light ? 0xFFEFF3F7 : panelDark;
            config.cardColor = light ? 0xFFF7FAFC : cardDark;
            config.textPrimaryColor = light ? 0xFF19242F : primaryDark;
            config.textSecondaryColor = light ? 0xFF354555 : secondaryDark;
            config.graphicsQuality = quality;
            resize(client, scale);
            key = scene + "-" + (light ? "light" : "dark") + "-" + scale + "-" + quality.id();
            started = now;
            captured = false;
            System.out.printf("V098_MATRIX %s actualScale=%.2f%n", key, client.method_22683().method_4495());
        } else if (!captured && now - started >= 1_100_000_000L) {
            capture(client);
        }
    }

    private static void capture(class_310 client) {
        int expected = index / 6 + 1;
        if (client.method_22683().method_4495() != expected) {
            throw new IllegalStateException("GUI scale drift before " + key);
        }
        captured = true;
        try (var screenshot = class_318.method_1663(client.method_1522())) {
            var directory = Path.of("probe-screenshots");
            Files.createDirectories(directory);
            screenshot.method_4314(directory.resolve(key + ".png"));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void restore(class_310 client) {
        var config = AtomChatConfig.get();
        config.panelBgColor = panelDark;
        config.cardColor = cardDark;
        config.textPrimaryColor = primaryDark;
        config.textSecondaryColor = secondaryDark;
        config.graphicsQuality = originalQuality;
        resize(client, originalScale);
        reset();
    }

    private static void resize(class_310 client, double scale) {
        var window = client.method_22683();
        if (window.method_4495() != scale) {
            // GameOptions may clamp GUI scale differently per pack. Exercise the actual target density.
            window.method_15997(scale);
            client.field_1755.method_25410(client, window.method_4486(), window.method_4502());
        }
    }
}
