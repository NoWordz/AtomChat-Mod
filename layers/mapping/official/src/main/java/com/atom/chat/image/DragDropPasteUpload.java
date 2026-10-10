package com.atom.chat.image;

import com.atom.chat.AtomChat;
import com.atom.chat.util.ClipboardImages;
import com.atom.chat.util.ImageFiles;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.awt.datatransfer.DataFlavor;
import java.nio.file.Path;
import java.util.function.LongSupplier;

/**
 * The two ways bytes reach the uploader without a dialog (D13): files dropped
 * on the window, and Ctrl+V with a picture on the clipboard.
 *
 * <p>Both are thin — the upload itself, the CICode and the hint state all live
 * in {@link ImageUploadPipeline} (D12), and this class only decides <em>what</em>
 * is worth uploading. Three things are semantics, not style:</p>
 * <ul>
 *   <li>the drop callback is <b>window-wide</b>: GLFW's drop event carries no
 *       cursor position, so there is nothing here to hit-test — the page the
 *       composer is on decides, and only the world/private chat pages take a
 *       drop;</li>
 *   <li>a drop uploads the <em>first</em> image it finds and returns: a
 *       multi-file drop appends one code, not several (and the remaining names
 *       are never even decoded);</li>
 *   <li>the callback fires <em>on the render thread</em>, from inside
 *       {@code glfwPollEvents} — that is why {@link #onFilesDropped} may touch
 *       the composer path directly while the clipboard paste goes to a worker
 *       thread of its own.</li>
 * </ul>
 *
 * <p>Lifecycle belongs to the screen (D25): {@link #installDropCallback} runs
 * from {@code init} — which also runs on every resize, hence the guard — and
 * {@link #uninstallDropCallback} from {@code removed}. GLFW hands back the
 * callback it <em>replaced</em> (its contract for every {@code glfwSet*Callback}),
 * and that value is what the field below holds; the guard therefore reads
 * exactly what the old code read, and nothing here changes what it does.</p>
 *
 * <p>Ports on {@link Host}: the screen's client handle — for one thing only, the
 * window pointer the drop callback is installed on, never
 * {@code Minecraft.getInstance()} (D3's precedent) — D24's page test, and D12's
 * uploader. The native call itself sits behind {@link DropSlot} so the lifecycle
 * around it can be driven offline; the clipboard read sits behind
 * {@link ClipboardReader} for the same reason.</p>
 *
 * <p>Both mappings: this file is the official-name copy and
 * {@code platforms/1.21.1-fabric/…/DragDropPasteUpload.java} is its yarn twin —
 * the two bodies differ only in Minecraft names (the client type and the window
 * handle accessor, {@code getWindow} / {@code getHandle}), never in what they
 * do.</p>
 */
public final class DragDropPasteUpload {
    /**
     * What the screen hands over: its client handle, D24's page fact and D12's
     * uploader. Nothing here is D13 state.
     */
    public interface Host {
        /**
         * The screen's own client handle — deliberately not
         * {@code Minecraft.getInstance()}: the screen's field is null before
         * {@code init()}, and the drop path keeps its exact behaviour only if it
         * sees the same null (and the same exception) it saw before. One thing
         * is asked of it: the window the drop callback is installed on.
         */
        Minecraft client();

        /** D24: whether the composer the drop would feed is on screen at all. */
        boolean worldChatPage();

        /** D12: the one upload entry point — this domain never uploads by itself. */
        ImageUploadPipeline uploads();
    }

    /**
     * The window's drop-callback slot: the only native call this domain makes,
     * so the lifecycle around it can be stubbed. The token is whatever the
     * window handed back — GLFW returns the callback it replaced, and nothing
     * here ever looks inside it, it is only ever released.
     */
    public interface DropSlot {
        /** Points {@code window}'s drop callback at {@code handler}; returns what it replaced. */
        Object install(long window, DropHandler handler);

        /** Detaches {@code token} from {@code window} and releases it. */
        void release(long window, Object token);
    }

    /**
     * What the window calls on a drop, with GLFW's own arguments — the cursor
     * position is not among them, because there is none.
     */
    @FunctionalInterface
    public interface DropHandler {
        void dropped(int count, long names);
    }

    /** Fetching the clipboard payload; production reads AWT through {@link ClipboardImages}. */
    @FunctionalInterface
    public interface ClipboardReader {
        Path read(DataFlavor flavor);
    }

    /**
     * The production slot: GLFW's setter and the callback object it returns.
     * The handler runs without the window argument, exactly like the lambda the
     * screen used to pass here.
     */
    private static final DropSlot GLFW_SLOT = new DropSlot() {
        @Override
        public Object install(long window, DropHandler handler) {
            return GLFW.glfwSetDropCallback(window, (win, count, names) -> handler.dropped(count, names));
        }

        @Override
        public void release(long window, Object token) {
            GLFW.glfwSetDropCallback(window, null);
            ((GLFWDropCallback) token).free();
        }
    };

    private final Host host;
    private final DropSlot dropSlot;
    private final ClipboardReader clipboard;

    /** GLFW drop callback, installed while this screen is open (MC sets none). */
    private Object dropCallback;

    public DragDropPasteUpload(Host host) {
        this(host, GLFW_SLOT, ClipboardImages::read);
    }

    /**
     * The two collaborators above, spelled out. Package-private and test-facing:
     * the production values are the ones the public constructor passes in.
     */
    DragDropPasteUpload(Host host, DropSlot dropSlot, ClipboardReader clipboard) {
        this.host = host;
        this.dropSlot = dropSlot;
        this.clipboard = clipboard;
    }

    /** The window the drop callback belongs to — the screen's own client handle. */
    private long window() {
        return host.client().getWindow().getWindow();
    }

    /**
     * GLFW only hands file drops to whoever is listening, and Minecraft
     * registers no drop callback at all — the Win32 backend already calls
     * DragAcceptFiles, so the events have been arriving and being discarded.
     * The callback fires on the render thread, from inside glfwPollEvents.
     */
    public void installDropCallback() {
        installDropCallback(this::window);
    }

    /**
     * The lifecycle half, with the window handed in as a supplier — the shell's
     * {@code init} goes through the public entry point above, tests through this
     * one, so the guard can be driven without a live window. The supplier is read
     * where the client handle used to be read: inside the try, right at the
     * install call.
     */
    void installDropCallback(LongSupplier window) {
        if (dropCallback != null) {
            return;
        }
        try {
            dropCallback = dropSlot.install(window.getAsLong(), this::onFilesDropped);
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Failed to install the file drop callback", t);
        }
    }

    public void uninstallDropCallback() {
        uninstallDropCallback(this::window);
    }

    /** The teardown half of {@link #installDropCallback(LongSupplier)}. */
    void uninstallDropCallback(LongSupplier window) {
        if (dropCallback == null) {
            return;
        }
        try {
            dropSlot.release(window.getAsLong(), dropCallback);
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Failed to remove the file drop callback", t);
        }
        dropCallback = null;
    }

    /** Window-wide: the drop event carries no cursor position we could hit-test with. */
    public void onFilesDropped(int count, long names) {
        // File drops only feed the world-chat composer; root pages must ignore them.
        if (!host.worldChatPage()) {
            return;
        }
        try {
            PointerBuffer buffer = MemoryUtil.memPointerBuffer(names, count);
            for (int i = 0; i < count; i++) {
                Path file = Path.of(MemoryUtil.memUTF8(buffer.get(i)));
                if (ImageFiles.isImage(file)) {
                    host.uploads().uploadAndAppend(file);
                    return;
                }
            }
            if (count > 0) {
                AtomChat.LOGGER.info("Ignored {} dropped file(s): none was an image", count);
            }
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Failed to handle dropped files", t);
        }
    }

    /**
     * Ctrl+V with a picture on the clipboard. The payload is read off the render
     * thread — a screenshot is several megabytes and converting it is not free —
     * then uploaded and appended.
     */
    public void pasteFromClipboard(DataFlavor flavor) {
        Thread worker = new Thread(() -> {
            Path file = clipboard.read(flavor);
            if (file == null) {
                AtomChat.LOGGER.warn("Clipboard held no usable image; Ctrl+V fell back to a text paste");
                return;
            }
            host.uploads().uploadAndAppend(file);
        }, "AtomChat-Paste");
        worker.setDaemon(true);
        worker.start();
    }
}
