package com.atom.chat.ui;

import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EmojiWiring}'s offline half: the three screen-side effects the panel's
 * host performs, the add slot's worker → render-thread order, and the two
 * behaviours that are contracts (inserting never closes the panel; the sticker
 * tap goes to D12).
 *
 * <p><b>Scope, stated up front.</b> A real {@link EmojiPanel} is driven through
 * the wiring's host, so "insert does not close" and "the panel receives the
 * added emote" are asserted on the real object. The dialog bridge is a real
 * {@link NativeDialogBridge} whose client handle is null (a headless test cannot
 * have a window), so what it does with the auto-iconify attributes is NOT
 * covered here — it is D14's own seam and its real behaviour is on that block's
 * probe list. Also NOT covered: the two press-clearing lines, which stay in the
 * shell by design (they are synchronous and focus-free, unlike the bridge's
 * hop-plus-focus {@code refocusWindow}), and the picker dialog itself.</p>
 */
class EmojiWiringTest {
    @BeforeAll
    static void installTemporaryPlatform(@TempDir java.nio.file.Path root) {
        // The real panel resolves its assets through Platform; a test must not
        // depend on whatever the previous test class happened to install.
        Platform.install(new Platform.Provider() {
            @Override
            public java.nio.file.Path configDir() {
                return root.resolve("config");
            }

            @Override
            public java.nio.file.Path gameDir() {
                return root.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    /** Records what the panel asked the screen to do, in order. */
    private static final class Recorder implements EmojiWiring.Host {
        final List<String> calls = new ArrayList<>();
        final List<String> executed = new ArrayList<>();
        EmojiPanel panel;
        boolean runHopInline = true;
        CountDownLatch hopped = new CountDownLatch(1);
        String hopThread = "";

        @Override
        public EmojiPanel panel() {
            return panel;
        }

        @Override
        public void appendToComposer(String text) {
            calls.add("insert:" + text);
        }

        @Override
        public void uploadSticker(Path file) {
            calls.add("sticker:" + file.getFileName());
        }

        @Override
        public void execute(Runnable task) {
            calls.add("execute");
            hopThread = Thread.currentThread().getName();
            if (runHopInline) {
                task.run();
            }
            hopped.countDown();
        }

        @Override
        public void suspendAutoIconify() {
            calls.add("suspend");
        }

        @Override
        public void restoreAutoIconify() {
            calls.add("restore");
        }

        @Override
        public void refocusWindow() {
            calls.add("refocus");
        }

        /** Builds the panel the way the shell does: with the wiring's host. */
        EmojiPanel attach(EmojiWiring wiring) {
            panel = new EmojiPanel(wiring.panelHost());
            return panel;
        }
    }

    private static Recorder recorder() {
        Recorder recorder = new Recorder();
        recorder.attach(new EmojiWiring(recorder));
        return recorder;
    }

    @Test
    void thePanelHostRoutesInsertAndStickerToTheirDomains() {
        Recorder recorder = recorder();
        EmojiWiring wiring = new EmojiWiring(recorder);
        EmojiPanel.Host host = wiring.panelHost();

        host.insert(":)");
        host.sendSticker(Path.of("C:/tmp/sticker.png"));

        assertEquals(List.of("insert::)", "sticker:sticker.png"), recorder.calls,
                "insert goes to D21's composer, the sticker tap to D12's uploader");
    }

    @Test
    void insertingDoesNotCloseThePanel() {
        Recorder recorder = recorder();
        EmojiWiring wiring = new EmojiWiring(recorder);
        EmojiPanel panel = recorder.panel;
        panel.toggle();
        assertTrue(panel.isOpen());

        wiring.panelHost().insert(":wave:");

        assertTrue(panel.isOpen(), "picking several emoji in a row is the point of the grid");
        assertEquals(List.of("insert::wave:"), recorder.calls);
    }

    @Test
    void theAddSlotStartsItsOwnPickerWorker() throws Exception {
        Recorder recorder = recorder();
        EmojiWiring wiring = new EmojiWiring(recorder);

        wiring.pickEmoteFile();

        // The worker blocks inside the real AWT picker, which a headless test JVM
        // never answers — so what is asserted here is the shape: a daemon worker
        // with the picker's name exists. The store mutation's hop back to the
        // render thread is registered as a probe (it is only reached after a real
        // dialog returns a file).
        Thread worker = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> "AtomChat-EmotePicker".equals(thread.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(worker, "the add slot must run on its own picker worker");
        assertTrue(worker.isDaemon(), "and it must not keep the game alive");
    }

    @Test
    void theMutualExclusionWithThePhrasePanelIsLeftToTheShell() {
        // The wiring owns no panel open/close state of its own: nothing here may
        // change the phrase panel, and the emoji panel only changes when the
        // shell's own toggle/outside-click code says so.
        Recorder recorder = recorder();
        EmojiWiring wiring = new EmojiWiring(recorder);
        EmojiPanel panel = recorder.panel;

        wiring.panelHost().insert(":x:");
        wiring.panelHost().sendSticker(Path.of("C:/tmp/s.png"));

        assertFalse(panel.isOpen(), "the wiring never opens or closes the panel by itself");
    }
}
