package com.atom.chat.image;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.Cicodes;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.net.MediaCompanionClient;
import com.atom.chat.ui.ActionToast;
import com.atom.chat.util.ImageFileNames;
import com.atom.chat.util.ImageFiles;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Outgoing images (D12): the upload chain, the two destinations behind it, the
 * CICode it drops into the draft, and the save path that starts from a received
 * one.
 *
 * <p>{@link #uploadAndAppend} is the single entry point for "turn this file
 * into a message". The server hosts media when it says so, anything else (or
 * any failure) falls back to the external host, and whichever answers appends
 * the CICode. Both callbacks arrive off the render thread, so the draft is only
 * touched after a hop back.</p>
 *
 * <p>Four things in here are order, not style:</p>
 * <ul>
 *   <li>{@code imageUploading} is raised <em>before</em> the upload starts and
 *       lowered inside the hop that appends the code. D21's placeholder reads
 *       the flag every frame, so raising it later or clearing it earlier turns
 *       the "uploading" hint into a flicker (or a stuck hint);</li>
 *   <li>the fallback decision itself: server upload first, then the external
 *       host on error, or straight to the external host when the server does
 *       not host media — the branch conditions and both log lines are the
 *       original ones, word for word;</li>
 *   <li>inside the hop, clear the flag and <em>then</em> append. Whether the
 *       code needs a leading space is decided by the composer's own text, and
 *       that read has to happen on the render thread, next to the write;</li>
 *   <li>the save path reports failure on two surfaces (hint + toast), because
 *       the hint is only visible with an empty draft.</li>
 * </ul>
 *
 * <p>Everything it touches is shared ({@link ImageUploader}, {@link ImageSaver},
 * {@link ImageFiles}, {@link ImageFileNames}, {@link Cicodes},
 * {@link MediaCompanionClient}) or a port on {@link Host}: the hop back to the
 * render thread — through the screen's own client handle, never
 * {@code Minecraft.getInstance()} (D3's precedent) — D21's composer, D7's
 * action feedback and D14's save dialog. The hint's state stays here: it is
 * D12's, and D21 reads it back through {@link #uploading()},
 * {@link #transientHint()} and {@link #transientHintSetAt()}.</p>
 *
 * <p>Both mappings: this file is the official-name copy and
 * {@code platforms/1.21.1-fabric/…/ImageUploadPipeline.java} is its yarn twin —
 * the two bodies differ only in Minecraft names (the client type and its
 * {@code getInstance()}), never in what they do.</p>
 */
public final class ImageUploadPipeline {
    /**
     * What the screen hands over: one fact it owns (the client handle), D21's
     * two composer calls, D7's toast and D14's save dialog. Nothing here is D12
     * state.
     */
    public interface Host {
        /**
         * The screen's own client handle — deliberately not
         * {@code Minecraft.getInstance()}: the screen's field is null before
         * {@code init()}, and the upload callbacks keep their exact behaviour
         * only if they see the same null (and the same exception) they saw
         * before. One thing is asked of it: {@code execute}, the hop back to the
         * render thread.
         */
        Minecraft client();

        /** D21: whether the draft is empty right now — it decides the separator. */
        boolean composerEmpty();

        /** D21: the one write entry into the draft. */
        void appendToComposer(String text);

        /** D7: the action-feedback toast, by key. */
        void toast(String key, boolean success);

        /**
         * D14: suspends auto-iconify, runs the save dialog and hands OS focus
         * back — one blocking call from the worker thread, the exact sequence
         * this screen's own save path used to spell out (and it still does: the
         * port's body is that sequence, moved, not rewritten). Returns null when
         * the dialog was cancelled.
         */
        Path pickSavePath(String suggestedName);
    }

    /**
     * The external host upload (uguu). The production value is the one
     * {@link ImageUploader} this screen used to own; the interface exists so the
     * callbacks can be driven offline, since a real upload needs a network.
     */
    public interface Uploader {
        void upload(Path file, Consumer<String> onSuccess, Consumer<String> onError);
    }

    private final Host host;
    private final ActionToast.Translator translator;
    private final Uploader imageUploader;
    /**
     * The hop back to the render thread. The production value is
     * {@code host.client().execute(task)} — same handle, same null before
     * {@code init()}, same timing (read when the callback fires, not when the
     * upload starts).
     */
    private final Consumer<Runnable> renderHop;

    /** Set while an upload is in flight; the input placeholder reads it. */
    private volatile boolean imageUploading;
    /** Short-lived hint shown in the empty input placeholder (save errors etc). */
    private volatile String transientHint;
    private long transientHintSetAt;

    public ImageUploadPipeline(Host host, ActionToast.Translator translator) {
        this(host, translator, new ImageUploader()::upload, task -> host.client().execute(task));
    }

    /**
     * The two collaborators above, spelled out. Package-private and test-facing:
     * the production defaults are the ones the public constructor passes in.
     */
    ImageUploadPipeline(Host host, ActionToast.Translator translator, Uploader imageUploader,
                        Consumer<Runnable> renderHop) {
        this.host = host;
        this.translator = translator;
        this.imageUploader = imageUploader;
        this.renderHop = renderHop;
    }

    /**
     * Uploads an image and drops its CICode into the draft. Shared by the file
     * picker and by Ctrl+V pastes.
     */
    public void uploadAndAppend(Path file) {
        int[] size = ImageFiles.dimensions(file);
        imageUploading = true;
        // The server decides: when it hosts media, upload there first; any
        // failure (or a server without the companion) falls back to uguu.
        if (MediaCompanionClient.hostingEnabled()) {
            MediaCompanionClient.uploadFile(file,
                    url -> appendImageCode(url, file, size),
                    error -> {
                        AtomChat.LOGGER.warn("Server media upload failed ({}), falling back to uguu", error);
                        uploadToUguu(file, size);
                    });
        } else {
            if (AtomChatConfig.get().debug) {
                AtomChat.LOGGER.info("[media] server media hosting unavailable; using the external host");
            }
            uploadToUguu(file, size);
        }
    }

    private void uploadToUguu(Path file, int[] size) {
        imageUploader.upload(file,
                url -> {
                    // Keep the choice auditable: server-hosted uploads already
                    // log "Stored hosted media", so the external path logs too.
                    AtomChat.LOGGER.info("Uploaded chat image to the external host: {}", url);
                    appendImageCode(url, file, size);
                },
                error -> {
                    AtomChat.LOGGER.warn("Image upload failed: {}", error);
                    renderHop.accept(() -> imageUploading = false);
                });
    }

    private void appendImageCode(String url, Path file, int[] size) {
        StringBuilder code = new StringBuilder("[[CICode,url=").append(url)
                .append(",name=").append(file.getFileName());
        // Carry the intrinsic size so any client receiving this can lay the
        // bubble out at the right aspect ratio before the download lands —
        // that is what keeps the height from jumping when it arrives.
        if (size != null) {
            code.append(",w=").append(size[0]).append(",h=").append(size[1]);
        }
        code.append("]]");
        String text = code.toString();
        // The upload callbacks run off the render thread; the chat field is
        // only safe to touch from the render thread.
        renderHop.accept(() -> {
            imageUploading = false;
            host.appendToComposer(host.composerEmpty() ? text : " " + text);
        });
    }

    /**
     * Saves another player's CICode image to a local file. The save dialog runs
     * on the same worker-thread pattern as the open picker; the actual download
     * happens off-thread via {@link ImageSaver} so the raw bytes stay identical.
     */
    public void saveImage(ChatMessage message) {
        String url = Cicodes.extractImageUrl(message.getRawText());
        if (url == null) {
            return;
        }
        Thread worker = new Thread(() -> {
            Path target = host.pickSavePath(fileNameFromUrl(url));
            if (target == null) {
                return;
            }
            ImageSaver.save(url, target).whenComplete((path, throwable) -> {
                if (throwable != null) {
                    // Both surfaces: the composer hint is only visible with an
                    // empty draft, so the toast carries the failure when a
                    // draft is present.
                    renderHop.accept(() -> {
                        showTransientHint(translator.text("atomchat.input.save_failed"));
                        host.toast("atomchat.feedback.saveFailed", false);
                    });
                } else {
                    AtomChat.LOGGER.info("Saved chat image to {}", path);
                    renderHop.accept(() -> host.toast("atomchat.feedback.saved", true));
                }
            });
        }, "AtomChat-ImageSavePicker");
        worker.setDaemon(true);
        worker.start();
    }

    /** URL last path segment, stripped of query/fragment; falls back to image.png. */
    public static String fileNameFromUrl(String url) {
        return ImageFileNames.fromUrl(url);
    }

    /** Raises the placeholder hint and stamps it; D21 expires it after 4s. */
    public void showTransientHint(String text) {
        transientHint = text;
        transientHintSetAt = System.currentTimeMillis();
    }

    /** D21's read side: the placeholder picks its hint from these three. */
    public boolean uploading() {
        return imageUploading;
    }

    public String transientHint() {
        return transientHint;
    }

    public long transientHintSetAt() {
        return transientHintSetAt;
    }
}
