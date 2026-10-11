package com.atom.chat.image;

import com.atom.chat.platform.Platform;
import com.atom.chat.ui.ActionToast;
import net.minecraft.client.MinecraftClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.system.MemoryUtil;

import java.awt.datatransfer.DataFlavor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DragDropPasteUpload}'s offline half: the drop callback's lifecycle
 * (which callback the field holds, the re-entry guard, uninstall, failure), the
 * three things a drop decides, and the two clipboard branches.
 *
 * <p><b>Scope, stated up front.</b> Two seams stand in for what needs a live
 * window or a real clipboard: {@link DragDropPasteUpload.DropSlot} (production:
 * GLFW's callback object plus the setter that binds it) and
 * {@link DragDropPasteUpload.ClipboardReader} (production:
 * {@code ClipboardImages.read}). The lifecycle is driven through the
 * package-private {@code …DropCallback(long window)} overloads, so no live window
 * is needed; the drop path is driven with a real "count pointers to
 * NUL-terminated names" array built from direct buffers — that is the shape GLFW
 * hands over, and it is built with the same {@link MemoryUtil} the production
 * code reads it with. <b>NOT covered here:</b> whether GLFW really delivers a
 * drop, whether it really frees the callback, and what a real resize does to the
 * window's slot (all three need a running game); the delivered callback is a
 * structural contract in this file, not a pixel or event fact. Those stay on the
 * real-machine probe list: drop a file on the window after a window resize, drop
 * several at once, drop a non-image, then Ctrl+V a screenshot, then close the
 * screen and confirm the window takes no more drops.</p>
 *
 * <p>{@link #uploadsInto} builds a real D12 pipeline with its render hop and its
 * external uploader stubbed, so a drop that reaches D12 lands as a real CICode —
 * the wiring between the two domains is asserted, not mocked. That path reaches
 * {@code AtomChatConfig.get()}, which needs {@link Platform}, so this class
 * installs its own temporary one: run alone, a class that borrowed a sibling's
 * installation fails on the worker thread instead.</p>
 */
class DragDropPasteUploadTest {
    private static final ActionToast.Translator KEYS = key -> key;

    /** Direct buffers are never relocated, but the name strings must outlive the call. */
    private static final List<ByteBuffer> NATIVE_STRINGS = new ArrayList<>();

    @TempDir
    static Path platformRoot;

    /**
     * D12's config read goes through {@link Platform} for its paths — the same
     * temporary provider the offscreen render tests install, so this class never
     * depends on the machine's real config nor on another class installing first.
     */
    @BeforeAll
    static void installTemporaryPlatform() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return platformRoot.resolve("config");
            }

            @Override
            public Path gameDir() {
                return platformRoot.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @TempDir
    Path tmp;

    /** The D12 side of the wiring: the codes it appended, and a signal for the worker thread. */
    private static final class Uploads {
        final List<String> codes = new CopyOnWriteArrayList<>();
        final CountDownLatch landed = new CountDownLatch(1);
    }

    /** D12, with its render hop inline and its external uploader answering a fixed url. */
    private static ImageUploadPipeline uploadsInto(Uploads sink) {
        ImageUploadPipeline.Host host = new ImageUploadPipeline.Host() {
            @Override
            public MinecraftClient client() {
                throw new AssertionError("D12 hops through its own seam here");
            }

            @Override
            public boolean composerEmpty() {
                return true;
            }

            @Override
            public void appendToComposer(String text) {
                sink.codes.add(text);
                sink.landed.countDown();
            }

            @Override
            public void toast(String key, boolean success) {
            }

            @Override
            public Path pickSavePath(String suggestedName) {
                throw new AssertionError("D13 never saves an image");
            }
        };
        ImageUploadPipeline.Uploader uploader =
                (file, onSuccess, onError) -> onSuccess.accept("https://uguu.se/" + file.getFileName());
        return new ImageUploadPipeline(host, KEYS, uploader, Runnable::run);
    }

    private static final class StubHost implements DragDropPasteUpload.Host {
        final ImageUploadPipeline uploads;
        boolean worldChatPage = true;

        StubHost(ImageUploadPipeline uploads) {
            this.uploads = uploads;
        }

        @Override
        public MinecraftClient client() {
            throw new AssertionError("the lifecycle is driven through the (long window) overloads");
        }

        @Override
        public boolean worldChatPage() {
            return worldChatPage;
        }

        @Override
        public ImageUploadPipeline uploads() {
            return uploads;
        }
    }

    /**
     * The window slot: records calls and answers with the tokens the test picks.
     *
     * <p>{@code built} is what the domain asked to be made; {@code replaced} is
     * what {@code bind} claims was already in the window — GLFW's answer, and
     * null in production because Minecraft registers no drop callback. Keeping
     * the two apart is the whole point: the defect was the field holding
     * {@code replaced}.</p>
     */
    private static final class StubSlot implements DragDropPasteUpload.DropSlot {
        final List<String> calls = new ArrayList<>();
        final List<Object> built = new ArrayList<>();
        final List<Object> released = new ArrayList<>();
        Object replaced = "the-callback-glfw-handed-back";
        boolean failBuild;
        boolean failBind;
        /** Fail only the first bind, so a test can show the retry succeeding. */
        boolean failBindOnce;

        @Override
        public Object build(DragDropPasteUpload.DropHandler handler) {
            calls.add("build");
            if (failBuild) {
                throw new IllegalStateException("cannot build a callback");
            }
            Object token = "our-callback-" + built.size();
            built.add(token);
            return token;
        }

        @Override
        public Object bind(long window, Object token) {
            calls.add("bind:" + window + ":" + token);
            if (failBind || failBindOnce) {
                failBindOnce = false;
                throw new IllegalStateException("no window");
            }
            return replaced;
        }

        @Override
        public void release(Object callback) {
            calls.add("release:" + callback);
            released.add(callback);
        }
    }

    /** GLFW's drop argument: {@code count} pointers to NUL-terminated file names. */
    private static long nameArray(String... names) {
        ByteBuffer pointers = ByteBuffer.allocateDirect(names.length * Long.BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < names.length; i++) {
            byte[] bytes = names[i].getBytes(StandardCharsets.UTF_8);
            ByteBuffer name = ByteBuffer.allocateDirect(bytes.length + 1);
            name.put(bytes).put((byte) 0).flip();
            NATIVE_STRINGS.add(name);
            MemoryUtil.memPutLong(MemoryUtil.memAddress(pointers) + (long) i * Long.BYTES, MemoryUtil.memAddress(name));
        }
        return MemoryUtil.memAddress(pointers);
    }

    private Path file(String name) throws Exception {
        return Files.createFile(tmp.resolve(name));
    }

    @Test
    void theInstalledCallbackIsKeptSoAReInstallIsGuarded() {
        // The fix's core nail: `init` runs again on every resize, so the guard is
        // what keeps one callback in the window. It reads the callback THIS
        // domain built — not the one GLFW handed back, which is null on a first
        // install and left the guard permanently dead.
        StubSlot slot = new StubSlot();
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.installDropCallback(() -> 7L);
        upload.installDropCallback(() -> 7L);   // the resize re-entry
        upload.installDropCallback(() -> 7L);

        assertEquals(List.of("build", "bind:7:our-callback-0"), slot.calls,
                "after the first install the guard must fire: one callback, installed once");
        assertTrue(slot.released.isEmpty(), "nothing may be freed while it is installed");
    }

    @Test
    void theGuardDoesNotDependOnWhatGlfwHandedBack() {
        // The reverse control for the test above: even when the slot answers
        // "nothing was there before" (the production answer), the guard still
        // fires. Holding that value was exactly the defect.
        StubSlot slot = new StubSlot();
        slot.replaced = null;
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.installDropCallback(() -> 7L);
        upload.installDropCallback(() -> 7L);

        assertEquals(List.of("build", "bind:7:our-callback-0"), slot.calls);
    }

    @Test
    void uninstallDetachesAndFreesTheCallbackItInstalled() {
        StubSlot slot = new StubSlot();
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.installDropCallback(() -> 7L);
        upload.uninstallDropCallback(() -> 7L);
        upload.uninstallDropCallback(() -> 7L);   // idempotent: nothing left to detach

        assertEquals(List.of("build", "bind:7:our-callback-0", "bind:7:null", "release:our-callback-0"),
                slot.calls,
                "uninstall must clear the window's slot AND free the callback");
        assertEquals(List.of((Object) "our-callback-0"), slot.released);
    }

    @Test
    void uninstallWithoutAnInstalledCallbackTouchesNothing() {
        StubSlot slot = new StubSlot();
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.uninstallDropCallback(() -> 7L);

        assertTrue(slot.calls.isEmpty(), "nothing was installed, so nothing may be released");
    }

    @Test
    void aFailedInstallLeavesNothingInstalledAndFreesWhatItBuilt() {
        StubSlot slot = new StubSlot();
        slot.failBindOnce = true;
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.installDropCallback(() -> 7L);   // bind fails
        upload.installDropCallback(() -> 7L);   // the next init retries and succeeds
        upload.uninstallDropCallback(() -> 7L);

        assertEquals(List.of("build", "bind:7:our-callback-0", "release:our-callback-0",
                "build", "bind:7:our-callback-1", "bind:7:null", "release:our-callback-1"), slot.calls,
                "a failed install frees what it built, stays retryable, and the retry is the one that uninstalls");
        assertEquals(List.of((Object) "our-callback-0", (Object) "our-callback-1"), slot.released,
                "each callback the domain built is freed exactly once");
    }

    @Test
    void aFailedBuildInstallsAndReleasesNothing() {
        StubSlot slot = new StubSlot();
        slot.failBuild = true;
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.installDropCallback(() -> 7L);

        assertEquals(List.of("build"), slot.calls,
                "nothing was built, so there is nothing to bind and nothing to free");
    }

    @Test
    void aDropOnAnyOtherPageIsIgnored() throws Exception {
        Uploads sink = new Uploads();
        StubHost host = new StubHost(uploadsInto(sink));
        host.worldChatPage = false;
        DragDropPasteUpload upload = new DragDropPasteUpload(host, new StubSlot(), flavor -> null);

        upload.onFilesDropped(1, nameArray(file("dropped.png").toString()));

        assertTrue(sink.codes.isEmpty(), "only the composer pages take a drop");
    }

    @Test
    void theFirstImageIsUploadedAndTheRestOfTheDropIsIgnored() throws Exception {
        Uploads sink = new Uploads();
        DragDropPasteUpload upload =
                new DragDropPasteUpload(new StubHost(uploadsInto(sink)), new StubSlot(), flavor -> null);
        String first = file("first.png").toString();

        upload.onFilesDropped(3, nameArray(first, file("second.png").toString(), file("third.png").toString()));

        assertEquals(1, sink.codes.size(), "a multi-file drop appends one code: " + sink.codes);
        assertTrue(sink.codes.get(0).contains("name=first.png"), sink.codes.toString());
        assertFalse(sink.codes.get(0).contains("second"), sink.codes.toString());
    }

    @Test
    void aDropWithoutAnImageUploadsNothing() throws Exception {
        Uploads sink = new Uploads();
        DragDropPasteUpload upload =
                new DragDropPasteUpload(new StubHost(uploadsInto(sink)), new StubSlot(), flavor -> null);

        upload.onFilesDropped(2, nameArray(file("notes.txt").toString(), file("archive.zip").toString()));

        assertTrue(sink.codes.isEmpty());
    }

    @Test
    void pasteUploadsWhatTheClipboardYields() throws Exception {
        Uploads sink = new Uploads();
        Path pasted = file("pasted.png");
        DragDropPasteUpload upload =
                new DragDropPasteUpload(new StubHost(uploadsInto(sink)), new StubSlot(), flavor -> pasted);

        upload.pasteFromClipboard(DataFlavor.imageFlavor);

        // The paste runs on its own worker thread; the CICode landing is the signal.
        assertTrue(sink.landed.await(5, TimeUnit.SECONDS), "the clipboard image must reach D12");
        assertTrue(sink.codes.get(0).contains("name=pasted.png"), sink.codes.toString());
    }

    @Test
    void pasteStopsWhenTheClipboardHoldsNoImage() throws Exception {
        Uploads sink = new Uploads();
        CountDownLatch read = new CountDownLatch(1);
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(uploadsInto(sink)), new StubSlot(),
                flavor -> {
                    read.countDown();
                    return null;
                });

        upload.pasteFromClipboard(DataFlavor.imageFlavor);

        assertTrue(read.await(5, TimeUnit.SECONDS), "the worker must read the clipboard");
        assertTrue(sink.codes.isEmpty(), "no image means no upload");
    }
}
