package com.atom.chat.image;

import com.atom.chat.AtomChat;
import com.atom.chat.mixin.MouseHandlerAccessor;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The window plumbing every native picker needs (D14): suspend the OS
 * auto-iconify toggle while a dialog owns input, hand focus back when it
 * closes, and the clipboard write the menus use to report success or failure.
 *
 * <p>A native AWT dialog takes OS focus away from the game. Three consequences
 * are handled here, and each one is a contract, not a detail:</p>
 * <ul>
 *   <li><b>the iconify pairing.</b> GLFW iconifies a fullscreen window as soon
 *       as it loses focus ({@code GLFW_AUTO_ICONIFY} defaults to true and
 *       Minecraft leaves it there), so a picker that takes focus would minimise
 *       the game. {@link #suppressAutoIconify()} and
 *       {@link #restoreAutoIconify()} must stay a pair around exactly the
 *       dialog's lifetime, and they must be balanced: a suspension that never
 *       gets restored leaves the game unable to iconify at all;</li>
 *   <li><b>the focus hand-back order.</b> {@link #refocusWindow()} releases the
 *       keys and mouse button the dialog swallowed <em>before</em> asking GLFW
 *       for focus again — the picker was opened by a press, and that press is
 *       still down as far as the game knows, so clearing it first is what keeps
 *       the first click after the dialog from landing on a held button;</li>
 *   <li><b>the clipboard failure contract.</b> {@link #copyToClipboard} returns
 *       false instead of throwing. It used to throw out of the click handler,
 *       which left the context menu stuck open with nothing to explain why; the
 *       boolean lets the caller report a failure instead of a success.</li>
 * </ul>
 *
 * <p>GLFW window calls are not thread-safe, so every one of them goes through
 * {@link #runOnRender}, which hops onto the render thread and waits — bounded by
 * two seconds, so a render thread that never gets there cannot pin the worker
 * that called it.</p>
 *
 * <p>Ports on {@link Host}: the screen's own client handle — never
 * {@code MinecraftClient.getInstance()} (D3's precedent: the screen's field is null
 * before {@code init()}, and these paths keep the same null and the same
 * exception) — and {@link NativeWindow}, which is where the five native calls
 * live so the pairing, the order and the failure branch can be driven offline.
 * The mouse-button accessor is a mapping-layer twin pair with an identical FQN
 * and an identical method name on both families
 * ({@code com.atom.chat.mixin.MouseHandlerAccessor}), so referencing it here
 * costs nothing at the seam level.</p>
 *
 * <p>Both mappings: this file is the official-name copy and
 * {@code platforms/1.21.1-fabric/…/NativeDialogBridge.java} is its yarn twin —
 * the two bodies differ only in Minecraft names (the client type, the window
 * handle accessor, the key/button state and the clipboard field), never in what
 * they do.</p>
 */
public final class NativeDialogBridge {
    /**
     * What the screen hands over: its own client handle. Nothing here is D14
     * state.
     */
    public interface Host {
        /**
         * The screen's own client handle — deliberately not
         * {@code MinecraftClient.getInstance()}: the screen's field is null before
         * {@code init()}, and every path here keeps the exact behaviour it had
         * when it read that same field.
         */
        MinecraftClient client();
    }

    /**
     * The native surfaces this domain drives: the auto-iconify toggle, the
     * focus request, the key and mouse-button state the dialog leaves behind,
     * and the clipboard write. Production is {@link #GLFW_WINDOW}; the interface
     * exists so the pairing, the call order and the clipboard failure can be
     * asserted offline.
     */
    public interface NativeWindow {
        /** Writes {@code GLFW_AUTO_ICONIFY}; must be called from the render thread. */
        void setAutoIconify(boolean value);

        /** {@code glfwFocusWindow} for this window. */
        void focus();

        /** Drops every key the dialog swallowed. */
        void releaseKeys();

        /** Clears the mouse button the picker was opened with. */
        void clearMousePress();

        /** Puts {@code text} on the system clipboard; throws when the platform refuses. */
        void setClipboard(String text);
    }

    /** The production native surfaces, reading the window through the host's client handle. */
    private static NativeWindow glfwWindow(Host host) {
        return new NativeWindow() {
            @Override
            public void setAutoIconify(boolean value) {
                try {
                    GLFW.glfwSetWindowAttrib(host.client().getWindow().getHandle(),
                            GLFW.GLFW_AUTO_ICONIFY, value ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
                } catch (Throwable t) {
                    AtomChat.LOGGER.warn("Failed to toggle GLFW_AUTO_ICONIFY for the image picker", t);
                }
            }

            @Override
            public void focus() {
                try {
                    GLFW.glfwFocusWindow(host.client().getWindow().getHandle());
                } catch (Throwable t) {
                    AtomChat.LOGGER.warn("Failed to refocus the window after the image picker", t);
                }
            }

            @Override
            public void releaseKeys() {
                KeyBinding.unpressAll();
            }

            @Override
            public void clearMousePress() {
                if (host.client().mouse != null) {
                    ((MouseHandlerAccessor) host.client().mouse).atomchat$setActiveButton(0);
                }
            }

            @Override
            public void setClipboard(String text) {
                host.client().keyboard.setClipboard(text);
            }
        };
    }

    private final Host host;
    private final NativeWindow nativeWindow;
    /**
     * The hop onto the render thread. The production value is
     * {@code host.client().execute(task)} — the same handle, the same null
     * before {@code init()}, the same exception.
     */
    private final Consumer<Runnable> renderHop;

    public NativeDialogBridge(Host host) {
        this(host, glfwWindow(host), task -> host.client().execute(task));
    }

    /**
     * The two collaborators above, spelled out. Package-private and test-facing:
     * the production values are the ones the public constructor passes in.
     */
    NativeDialogBridge(Host host, NativeWindow nativeWindow, Consumer<Runnable> renderHop) {
        this.host = host;
        this.nativeWindow = nativeWindow;
        this.renderHop = renderHop;
    }

    /**
     * Runs a GLFW call on the render thread and waits for it to land. The wait
     * is bounded: a render thread that is not coming back must not pin the
     * worker thread that opened a dialog.
     */
    public void runOnRender(Runnable task) {
        CountDownLatch done = new CountDownLatch(1);
        renderHop.accept(() -> {
            try {
                task.run();
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(2L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * GLFW iconifies a fullscreen window as soon as it loses focus
     * (GLFW_AUTO_ICONIFY defaults to true and Minecraft leaves it there), so a
     * picker that takes focus would minimise the game every time. Suspension
     * lasts exactly as long as the picker is open.
     */
    public void suppressAutoIconify() {
        setAutoIconify(false);
    }

    public void restoreAutoIconify() {
        setAutoIconify(true);
    }

    private void setAutoIconify(boolean value) {
        runOnRender(() -> nativeWindow.setAutoIconify(value));
    }

    /**
     * Returns OS focus to the game window after the native picker closes, and
     * clears any key/button state the dialog left behind. GLFW window calls are
     * not thread-safe, so the work hops onto the render thread.
     */
    public void refocusWindow() {
        renderHop.accept(() -> {
            nativeWindow.releaseKeys();
            nativeWindow.clearMousePress();
            nativeWindow.focus();
        });
    }

    /**
     * Puts {@code text} on the clipboard and reports whether it worked. A
     * failure must not escape: the click handler that reports the copy has
     * nothing to fall back to, and letting it throw used to leave the context
     * menu stuck open.
     */
    public boolean copyToClipboard(String text) {
        try {
            nativeWindow.setClipboard(text);
            return true;
        } catch (Throwable t) {
            // Never let a clipboard failure abort the click handler: it used to
            // leave the menu stuck open with no clue why. The boolean lets the
            // caller show a failure instead of a success.
            AtomChat.LOGGER.warn("Failed to copy message to clipboard", t);
            return false;
        }
    }
}
