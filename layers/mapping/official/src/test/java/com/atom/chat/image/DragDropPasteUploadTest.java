package com.atom.chat.image;

import com.atom.chat.ui.ActionToast;
import net.minecraft.client.Minecraft;
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
 * (guard, idempotence, failure), the three things a drop decides, and the two
 * clipboard branches.
 *
 * <p><b>Scope, stated up front.</b> Two seams stand in for what needs a live
 * window or a real clipboard: {@link DragDropPasteUpload.DropSlot} (production:
 * GLFW's setter plus the callback object it hands back) and
 * {@link DragDropPasteUpload.ClipboardReader} (production:
 * {@code ClipboardImages.read}). The lifecycle is driven through the
 * package-private {@code …DropCallback(long window)} overloads, so no live window
 * is needed; the drop path is driven with a real "count pointers to
 * NUL-terminated names" array built from direct buffers — that is the shape GLFW
 * hands over, and it is built with the same {@link MemoryUtil} the production
 * code reads it with. NOT covered here: whether GLFW really delivers a drop (it
 * needs a running game) and AWT's clipboard itself. Those stay on the
 * real-machine probe list: drop a file on the window, drop several at once, drop
 * a non-image, then Ctrl+V a screenshot.</p>
 *
 * <p>{@link #uploadsInto} builds a real D12 pipeline with its render hop and its
 * external uploader stubbed, so a drop that reaches D12 lands as a real CICode —
 * the wiring between the two domains is asserted, not mocked.</p>
 */
class DragDropPasteUploadTest {
    private static final ActionToast.Translator KEYS = key -> key;

    /** Direct buffers are never relocated, but the name strings must outlive the call. */
    private static final List<ByteBuffer> NATIVE_STRINGS = new ArrayList<>();

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
            public Minecraft client() {
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
        public Minecraft client() {
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

    /** The window slot: records calls and answers with the token the test picks. */
    private static final class StubSlot implements DragDropPasteUpload.DropSlot {
        final List<String> calls = new ArrayList<>();
        Object token = "the-callback-glfw-handed-back";
        boolean failInstall;

        @Override
        public Object install(long window, DragDropPasteUpload.DropHandler handler) {
            calls.add("install:" + window);
            if (failInstall) {
                throw new IllegalStateException("no window");
            }
            return token;
        }

        @Override
        public void release(long window, Object released) {
            calls.add("release:" + window + ":" + released);
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
    void installIsGuardedOnceTheSlotHandedBackAToken() {
        StubSlot slot = new StubSlot();
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.installDropCallback(() -> 7L);
        upload.installDropCallback(() -> 7L);

        assertEquals(List.of("install:7"), slot.calls, "an installed callback must not be installed twice");
    }

    @Test
    void installIsNotGuardedWhenTheSlotHandsBackNothing() {
        StubSlot slot = new StubSlot();
        slot.token = null; // what GLFW returns when nothing was registered before
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.installDropCallback(() -> 7L);
        upload.installDropCallback(() -> 7L);

        // The field holds the callback GLFW *replaced*, so with no previous one it
        // stays null and the guard cannot fire — the behaviour the old code had.
        assertEquals(List.of("install:7", "install:7"), slot.calls);
    }

    @Test
    void uninstallWithoutAnInstalledCallbackTouchesNothing() {
        StubSlot slot = new StubSlot();
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.uninstallDropCallback(() -> 7L);

        assertTrue(slot.calls.isEmpty(), "nothing was installed, so nothing may be released");
    }

    @Test
    void uninstallReleasesTheTokenOnce() {
        StubSlot slot = new StubSlot();
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.installDropCallback(() -> 7L);
        upload.uninstallDropCallback(() -> 7L);
        upload.uninstallDropCallback(() -> 7L);

        assertEquals(List.of("install:7", "release:7:the-callback-glfw-handed-back"), slot.calls);
    }

    @Test
    void aFailedInstallLeavesNothingInstalled() {
        StubSlot slot = new StubSlot();
        slot.failInstall = true;
        DragDropPasteUpload upload = new DragDropPasteUpload(new StubHost(null), slot, flavor -> null);

        upload.installDropCallback(() -> 7L);
        upload.uninstallDropCallback(() -> 7L);

        assertEquals(List.of("install:7"), slot.calls, "a failed install must leave the field null");
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
