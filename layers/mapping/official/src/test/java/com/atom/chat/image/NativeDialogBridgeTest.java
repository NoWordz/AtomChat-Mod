package com.atom.chat.image;

import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NativeDialogBridge}'s offline half: the clipboard failure contract, the
 * iconify pairing, the bounded render-thread wait and the focus hand-back order.
 *
 * <p><b>Scope, stated up front.</b> One seam stands in for the native surfaces —
 * {@link NativeDialogBridge.NativeWindow}, production being the five GLFW /
 * Minecraft calls themselves — plus the render hop, so nothing here needs a
 * window, a GLFW context or a real clipboard. What is NOT covered: whether GLFW
 * really applies the attribute or really focuses the window, whether
 * {@code KeyMapping.releaseAll()} clears a genuinely held key, and whether AWT
 * accepts the clipboard text. Those stay on the real-machine probe list: open a
 * picker in fullscreen (the game must not minimise), close it and click once
 * (the first click must land), and copy a message from the context menu.</p>
 *
 * <p>The host stub's {@code client()} throws: every test here goes through the
 * injected seams, so asking the client for anything would be a bug.</p>
 */
class NativeDialogBridgeTest {
    /** Records what the domain asked of the native surfaces, in order. */
    private static final class StubWindow implements NativeDialogBridge.NativeWindow {
        final List<String> calls = new ArrayList<>();
        /** Counted down by the last call of {@link NativeDialogBridge#refocusWindow()}. */
        final CountDownLatch focused = new CountDownLatch(1);
        boolean clipboardFails;

        @Override
        public void setAutoIconify(boolean value) {
            calls.add("autoIconify:" + value);
        }

        @Override
        public void focus() {
            calls.add("focus");
            focused.countDown();
        }

        @Override
        public void releaseKeys() {
            calls.add("releaseKeys");
        }

        @Override
        public void clearMousePress() {
            calls.add("clearMousePress");
        }

        @Override
        public void setClipboard(String text) {
            calls.add("clipboard:" + text);
            if (clipboardFails) {
                throw new IllegalStateException("clipboard owned by another application");
            }
        }
    }

    private static final NativeDialogBridge.Host NO_HOST = new NativeDialogBridge.Host() {
        @Override
        public Minecraft client() {
            throw new AssertionError("the bridge must go through its injected seams");
        }
    };

    /** Runs the hop on a thread of its own and returns immediately — the landing is what is awaited. */
    private static Consumer<Runnable> detachedHop(List<String> order) {
        return task -> {
            order.add("hop");
            Thread thread = new Thread(task, "stub-render-thread");
            thread.setDaemon(true);
            thread.start();
        };
    }

    @Test
    void copyToClipboardReportsSuccess() {
        StubWindow window = new StubWindow();
        NativeDialogBridge bridge = new NativeDialogBridge(NO_HOST, window, task -> task.run());

        assertTrue(bridge.copyToClipboard("hello"));
        assertEquals(List.of("clipboard:hello"), window.calls);
    }

    @Test
    void copyToClipboardReturnsFalseInsteadOfThrowing() {
        StubWindow window = new StubWindow();
        window.clipboardFails = true;
        NativeDialogBridge bridge = new NativeDialogBridge(NO_HOST, window, task -> task.run());

        assertFalse(bridge.copyToClipboard("hello"), "a clipboard failure is reported, not thrown");
        assertEquals(List.of("clipboard:hello"), window.calls);
    }


    @Test
    void theIconifySuspensionIsAPair() {
        StubWindow window = new StubWindow();
        NativeDialogBridge bridge = new NativeDialogBridge(NO_HOST, window, task -> task.run());

        bridge.suppressAutoIconify();
        bridge.restoreAutoIconify();

        assertEquals(List.of("autoIconify:false", "autoIconify:true"), window.calls,
                "suspension and restore must come out as a pair, through the render hop");
    }

    @Test
    void runOnRenderWaitsForAnAsynchronousLanding() {
        AtomicBoolean ran = new AtomicBoolean();
        List<String> order = new ArrayList<>();
        NativeDialogBridge bridge = new NativeDialogBridge(NO_HOST, new StubWindow(), detachedHop(order));

        bridge.runOnRender(() -> {
            ran.set(true);
            order.add("task");
        });

        assertTrue(ran.get(), "the call must not return before the task has landed");
        assertEquals(List.of("hop", "task"), order);
    }

    @Test
    void runOnRenderGivesUpAfterTwoSeconds() {
        NativeDialogBridge bridge = new NativeDialogBridge(NO_HOST, new StubWindow(), task -> {
            // The render thread never gets there: the wait must be bounded.
        });

        long start = System.currentTimeMillis();
        bridge.runOnRender(() -> {
            throw new AssertionError("the task must not run");
        });
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed >= 1900L, "the wait holds for the two-second bound: " + elapsed + "ms");
        assertTrue(elapsed < 6000L, "and then gives up: " + elapsed + "ms");
    }

    @Test
    void anInterruptedWaitRestoresTheInterruptFlag() throws Exception {
        NativeDialogBridge bridge = new NativeDialogBridge(NO_HOST, new StubWindow(), task -> {
            // never lands, so the wait is interruptible
        });
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);

        Thread waiter = new Thread(() -> {
            bridge.runOnRender(() -> {
            });
            interrupted.set(Thread.currentThread().isInterrupted());
            finished.countDown();
        }, "stub-picker-thread");
        waiter.setDaemon(true);
        waiter.start();
        Thread.sleep(50L);
        waiter.interrupt();

        assertTrue(finished.await(5, TimeUnit.SECONDS), "an interrupt must end the wait immediately");
        assertTrue(interrupted.get(), "the interrupt status must be handed back to the caller");
    }

    @Test
    void refocusClearsKeysAndButtonBeforeAskingForFocus() throws Exception {
        StubWindow window = new StubWindow();
        List<String> order = new ArrayList<>();
        NativeDialogBridge bridge = new NativeDialogBridge(NO_HOST, window, detachedHop(order));

        bridge.refocusWindow();

        // The hop is asynchronous (like client.execute): the third call is the signal.
        assertTrue(window.focused.await(5, TimeUnit.SECONDS), "the hop must land: " + window.calls);
        assertEquals(List.of("releaseKeys", "clearMousePress", "focus"), window.calls,
                "the swallowed press must be cleared before focus is asked for");
        assertEquals(List.of("hop"), order);
    }
}
