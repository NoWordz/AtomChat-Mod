package com.atom.chat.image;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.ActionToast;
import com.atom.chat.util.ImageFileNames;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ImageUploadPipeline}'s offline half: the name a url maps to, the CICode
 * the chain builds, the order that chain moves its state in, the fallback to the
 * external host, the save path's worker and the hint's timestamp.
 *
 * <p><b>Scope, stated up front.</b> Two seams stand in for the things that need
 * a live game or a network: the render-thread hop (production:
 * {@code host.client().execute}) and the external uploader (production:
 * {@link ImageUploader}, i.e. an HTTP POST to uguu). With both replaced the
 * chain runs inline and the statement order — raise the flag, upload, hop, clear
 * the flag, append — becomes observable. Everything past a real dialog or a real
 * download is NOT covered here: the server-media branch (it needs the companion
 * handshake that flips {@code MediaCompanionClient.hostingEnabled()}),
 * {@code ImageSaver}'s completion callbacks, and the iconify/focus plumbing the
 * shell's {@link ImageUploadPipeline.Host#pickSavePath} performs. Those stay on
 * the real-machine probe list: drop a file on the window, Ctrl+V an image, the
 * image button, and right-click → save image.</p>
 *
 * <p>The host stub exists to record the call order across the seam, and its
 * {@code client()} deliberately throws: every test here goes through the
 * injected hop, so a call to the client would be a bug, not a missing stub.</p>
 */
class ImageUploadPipelineTest {
    /** Keys pass straight through: this file asserts order and text, not copy. */
    private static final ActionToast.Translator KEYS = key -> key;

    @TempDir
    static Path platformRoot;

    @BeforeAll
    static void installTemporaryPlatform() {
        Platform.install(new Platform.Provider() {
            @Override public Path configDir() { return platformRoot.resolve("config"); }
            @Override public Path gameDir() { return platformRoot.resolve("game"); }
            @Override public boolean isModLoaded(String modId) { return false; }
        });
    }

    @TempDir
    Path tmp;

    /** Records what the pipeline asked of the screen, in order. */
    private static final class StubHost implements ImageUploadPipeline.Host {
        final List<String> calls = new ArrayList<>();
        final CountDownLatch pickerCalled = new CountDownLatch(1);
        boolean composerEmpty = true;
        Path pickResult;
        String suggestedName;

        @Override
        public Minecraft client() {
            throw new AssertionError("the pipeline must hop through the injected renderHop, not the client");
        }

        @Override
        public boolean composerEmpty() {
            calls.add("composerEmpty");
            return composerEmpty;
        }

        @Override
        public void appendToComposer(String text) {
            calls.add("append:" + text);
        }

        @Override
        public void toast(String key, boolean success) {
            calls.add("toast:" + key + ":" + success);
        }

        @Override
        public Path pickSavePath(String suggestedName) {
            this.suggestedName = suggestedName;
            pickerCalled.countDown();
            return pickResult;
        }
    }

    /** The external host: logs into the host's own call list, then answers. */
    private static final class StubUploader implements ImageUploadPipeline.Uploader {
        private final List<String> calls;
        private final String url;
        private final String error;
        ImageUploadPipeline pipeline;
        boolean uploadingWhenCalled;

        StubUploader(List<String> calls, String url, String error) {
            this.calls = calls;
            this.url = url;
            this.error = error;
        }

        @Override
        public void upload(Path file, Consumer<String> onSuccess, Consumer<String> onError) {
            calls.add("upload:" + file.getFileName());
            uploadingWhenCalled = pipeline.uploading();
            if (error != null) {
                onError.accept(error);
            } else {
                onSuccess.accept(url);
            }
        }
    }

    private ImageUploadPipeline pipeline(StubHost host, StubUploader uploader) {
        ImageUploadPipeline pipeline = new ImageUploadPipeline(host, KEYS, uploader, Runnable::run);
        uploader.pipeline = pipeline;
        return pipeline;
    }

    /** A real PNG of the given size, so {@code ImageFiles.dimensions} has something to read. */
    private Path png(int width, int height) throws Exception {
        Path file = tmp.resolve("shot.png");
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        return file;
    }

    @Test
    void fileNameFromUrlKeepsTheLastSegmentAndDropsQueryAndFragment() {
        assertEquals("photo.png", ImageUploadPipeline.fileNameFromUrl("https://uguu.se/photo.png"));
        assertEquals("photo.png", ImageUploadPipeline.fileNameFromUrl("https://uguu.se/a/b/photo.png?token=abc"));
        assertEquals("photo.png", ImageUploadPipeline.fileNameFromUrl("https://uguu.se/photo.png?token=abc#frag"));
        // Hosted media carries no path segment, and a colon is illegal on Windows.
        assertEquals("abcd.png", ImageUploadPipeline.fileNameFromUrl("atomchat-media:abcd.png"));
        assertEquals("a_b.png", ImageUploadPipeline.fileNameFromUrl("https://host/a:b.png"));
        // No extension is still a name; nothing usable falls back.
        assertEquals("noextension", ImageUploadPipeline.fileNameFromUrl("https://host/noextension"));
        assertEquals("image.png", ImageFileNames.FALLBACK);
        assertEquals(ImageFileNames.FALLBACK, ImageUploadPipeline.fileNameFromUrl("https://host/"));
        assertEquals(ImageFileNames.FALLBACK, ImageUploadPipeline.fileNameFromUrl("   "));
        assertEquals(ImageFileNames.FALLBACK, ImageUploadPipeline.fileNameFromUrl(null));
    }

    @Test
    void uploadSuccessAppendsTheCodeWithItsSizeAfterOneHop() throws Exception {
        StubHost host = new StubHost();
        StubUploader uploader = new StubUploader(host.calls, "https://uguu.se/abc.png", null);
        ImageUploadPipeline pipeline = pipeline(host, uploader);

        pipeline.uploadAndAppend(png(120, 80));

        assertTrue(uploader.uploadingWhenCalled, "the flag must be up before the upload starts");
        assertFalse(pipeline.uploading(), "the hop clears the flag");
        assertEquals(List.of(
                "upload:shot.png",
                "composerEmpty",
                "append:[[CICode,url=https://uguu.se/abc.png,name=shot.png,w=120,h=80]]"), host.calls);
    }

    @Test
    void anOccupiedDraftGetsTheSeparatorAndDimensionsForAValidImage() throws Exception {
        StubHost host = new StubHost();
        host.composerEmpty = false;
        StubUploader uploader = new StubUploader(host.calls, "https://uguu.se/abc.png", null);
        ImageUploadPipeline pipeline = pipeline(host, uploader);

        pipeline.uploadAndAppend(png(120, 80));

        assertEquals(List.of(
                "upload:shot.png",
                "composerEmpty",
                "append: [[CICode,url=https://uguu.se/abc.png,name=shot.png,w=120,h=80]]"), host.calls);
        assertFalse(pipeline.uploading());
    }

    @Test
    void uploadFailureClearsTheFlagAndAppendsNothing() throws Exception {
        StubHost host = new StubHost();
        StubUploader uploader = new StubUploader(host.calls, null, "HTTP 500");
        ImageUploadPipeline pipeline = pipeline(host, uploader);

        pipeline.uploadAndAppend(png(1, 1));

        assertEquals(List.of("upload:shot.png"), host.calls);
        assertFalse(pipeline.uploading(), "a failed upload must not pin the placeholder on 'uploading'");
    }

    @Test
    void oversizeLocalImageNeverReachesEitherUploaderOrTheComposer() throws Exception {
        Path file = png(1, 1);
        java.nio.file.Files.write(file, java.util.Arrays.copyOf(
                java.nio.file.Files.readAllBytes(file), 16 * 1024 * 1024 + 1));
        assertRejectedBeforeUpload(file, "atomchat.input.image_too_large");
    }

    @Test
    void excessivePixelsAndInvalidHeadersAreReportedWithoutUpload() throws Exception {
        Path file = tmp.resolve("large.png");
        java.nio.file.Files.write(file, ImageBoundsFixtures.png(32000001, 1));
        assertRejectedBeforeUpload(file, "atomchat.input.image_too_many_pixels");
        java.nio.file.Files.write(file, new byte[64]);
        assertRejectedBeforeUpload(file, "atomchat.input.image_invalid");
    }

    private void assertRejectedBeforeUpload(Path file, String errorKey) {
        for (boolean hosting : new boolean[]{false, true}) {
            StubHost host = new StubHost();
            ImageUploadPipeline.Uploader forbidden = (path, success, error) -> {
                throw new AssertionError("invalid local file reached an uploader; hosting=" + hosting);
            };
            // Additional injection seam: external uploader, render hop, hosting flag, hosted uploader.
            ImageUploadPipeline pipeline = new ImageUploadPipeline(host, key -> "translated:" + key,
                    forbidden, Runnable::run, () -> hosting, forbidden);
            pipeline.uploadAndAppend(file);
            assertFalse(pipeline.uploading());
            assertEquals(List.of("toast:" + errorKey + ":false"), host.calls,
                    "invalid input must not append a CICode or fall back to the external host");
            assertEquals("translated:" + errorKey, pipeline.transientHint());
        }
    }

    @Test
    void showTransientHintStampsTheMomentItWasSet() {
        StubHost host = new StubHost();
        ImageUploadPipeline pipeline = pipeline(host, new StubUploader(host.calls, "u", null));

        long before = System.currentTimeMillis();
        pipeline.showTransientHint("save failed");
        long after = System.currentTimeMillis();

        assertEquals("save failed", pipeline.transientHint());
        assertTrue(pipeline.transientHintSetAt() >= before, "the stamp is taken when the hint is raised");
        assertTrue(pipeline.transientHintSetAt() <= after);
        assertFalse(pipeline.uploading());
    }

    @Test
    void saveImageIgnoresAMessageWithoutAnImageCode() {
        StubHost host = new StubHost();
        ImageUploadPipeline pipeline = pipeline(host, new StubUploader(host.calls, "u", null));

        pipeline.saveImage(new ChatMessage(Component.literal("<Alice> no picture here"), false));

        assertEquals(1L, host.pickerCalled.getCount(), "no url means no worker and no dialog");
        assertNull(host.suggestedName);
    }

    @Test
    void saveImageAsksForTheUrlDerivedNameAndStopsWhenTheDialogIsCancelled() throws Exception {
        StubHost host = new StubHost();
        host.pickResult = null;
        ImageUploadPipeline pipeline = pipeline(host, new StubUploader(host.calls, "u", null));

        pipeline.saveImage(new ChatMessage(
                Component.literal("[[CICode,url=https://uguu.se/abc.png?t=1,name=abc.png]]"), false));

        assertTrue(host.pickerCalled.await(5, TimeUnit.SECONDS), "the dialog runs on the save worker");
        assertEquals("abc.png", host.suggestedName);
        assertTrue(host.calls.isEmpty(), "a cancelled dialog downloads nothing and reports nothing");
    }
}
