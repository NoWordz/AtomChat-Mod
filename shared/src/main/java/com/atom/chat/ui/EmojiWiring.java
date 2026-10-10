package com.atom.chat.ui;

import com.atom.chat.emote.EmoteStore;
import com.atom.chat.util.FilePicker;

import java.nio.file.Path;

/**
 * What the emoji panel asks the screen to do (D17): insert text, send a sticker,
 * or open the picker for the "+" add slot.
 *
 * <p>Three contracts come with this wiring. <b>Inserting never closes the
 * panel</b> — the whole point of the grid is picking several in a row, so the
 * insert callback only appends. <b>The host's insert goes straight through</b>
 * to D21's composer (the 256-character cap lives inside that append, and is not
 * repeated here). And the mutex with the quick-phrase panel is only carried
 * over, not redesigned: which panel closes which is the shell's business (the
 * toggle and the outside-click decision sit there), and this class must not add
 * or "fix" any of it.</p>
 *
 * <p>The add slot reuses D14 for the auto-iconify suspension around the picker
 * and the focus hand-back after it (three neutral facts whose shell bodies are
 * one call each into the bridge). The two press-clearing
 * lines the old picker opened with are <em>not</em> taken from the bridge: they
 * run synchronously on the calling thread and do not touch window focus, while
 * the bridge's {@code refocusWindow} hops to the render thread and focuses —
 * different order, different conditions, so they stay in the shell (registered
 * in the block report).</p>
 *
 * <p>Ports on {@link Host}: D21's append, D12's sticker upload, the render-thread
 * hop and D14's dialog bridge. Nothing here is D17 state.</p>
 *
 * <p>One copy, in the shared layer: this class names no Minecraft API, and the
 * mapping-layer type it drives ({@link EmojiPanel})
 * exist at these same names on every target — the seam manifest's 甲-group case.
 * A twin pair here would be byte-identical, which {@code verify_targets} rejects.</p>
 */
public final class EmojiWiring {
    /**
     * What the screen hands over: D21's composer, D12's uploader, the hop back to
     * the render thread and D14's dialog bridge.
     */
    public interface Host {
        /**
         * D17's panel: the shell builds it with {@link #panelHost()} and hands it
         * back here, because the add slot's picker lands in the panel again.
         */
        EmojiPanel panel();

        /** D21: appends text at the caret (the 256 cap lives there). */
        void appendToComposer(String text);

        /** D12: uploads a file and drops its CICode into the draft. */
        void uploadSticker(Path file);

        /** The hop back to the render thread (production: {@code client.execute}). */
        void execute(Runnable task);

        /**
         * D14: suspension of the OS auto-iconify around a dialog. The shell's
         * body is one call into its dialog bridge — kept as a plain fact so this
         * shared class (and its test) names no Minecraft-backed type.
         */
        void suspendAutoIconify();

        /** D14: the matching restore. */
        void restoreAutoIconify();

        /** D14: the focus hand-back after the dialog closes. */
        void refocusWindow();
    }

    private final Host host;

    public EmojiWiring(Host host) {
        this.host = host;
    }

    /** The panel's three screen-side effects, for the shell to construct the panel with. */
    public EmojiPanel.Host panelHost() {
        return new EmojiPanel.Host() {
            @Override
            public void insert(String text) {
                host.appendToComposer(text);
            }

            @Override
            public void sendSticker(Path file) {
                host.uploadSticker(file);
            }

            @Override
            public void pickEmoteFile() {
                EmojiWiring.this.pickEmoteFile();
            }
        };
    }

    /**
     * Opens the FlatLaf image picker (same one as the image button) and copies
     * the chosen file into the emote store. The picker blocks its own worker
     * thread; the store mutation hops back to the render thread.
     */
    public void pickEmoteFile() {
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(host::suspendAutoIconify,
                    host::restoreAutoIconify, EmoteStore::isSupportedName);
            host.refocusWindow();
            if (file == null) {
                return;
            }
            host.execute(() -> host.panel().addEmote(file));
        }, "AtomChat-EmotePicker");
        worker.setDaemon(true);
        worker.start();
    }
}
