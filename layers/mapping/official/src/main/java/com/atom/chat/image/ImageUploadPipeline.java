package com.atom.chat.image;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.Cicodes;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.net.MediaCompanionClient;
import com.atom.chat.ui.ActionToast;
import com.atom.chat.util.ImageFileNames;
import com.atom.chat.util.ImageFiles;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Outgoing image transport with connection-scoped requests and captured draft destinations. */
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

        /** A late native picker/clipboard result cannot start work for a screen that was replaced. */
        default boolean active() { return true; }

        /** Captures the destination before async work starts. */
        default DraftTarget captureDraft() {
            return code -> {
                appendToComposer(composerEmpty() ? code : " " + code);
                return AppendResult.DELIVERED;
            };
        }

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
        default void upload(Path file, Consumer<String> onSuccess, Consumer<String> onError, BooleanSupplier valid) {
            if (valid.getAsBoolean()) upload(file,onSuccess,onError);
            else onError.accept("cancelled");
        }
    }

    public enum AppendResult { DELIVERED, DEFERRED, STALE }
    public interface DraftTarget {
        AppendResult append(String code);
        default void cancel() { }
    }

    private static final java.util.concurrent.atomic.AtomicLong IDS = new java.util.concurrent.atomic.AtomicLong();
    private final Host host;
    private final ActionToast.Translator translator;
    private final Uploader imageUploader;
    private final BooleanSupplier hostingEnabled;
    private final Uploader hostedUploader;
    /**
     * The hop back to the render thread. The production value is
     * {@code host.client().execute(task)} — same handle, same null before
     * {@code init()}, same timing (read when the callback fires, not when the
     * upload starts).
     */
    private final Consumer<Runnable> renderHop;

    /** Set while an upload is in flight; the input placeholder reads it. */
    private final java.util.Set<UploadRequest> requests = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.function.LongSupplier connectionEpoch;
    @FunctionalInterface interface Inspector { ImageBounds.Inspection inspect(Path file) throws IOException; }
    private final Inspector inspector;
    /** Short-lived hint shown in the empty input placeholder (save errors etc). */
    private volatile String transientHint;
    private long transientHintSetAt;

    public ImageUploadPipeline(Host host, ActionToast.Translator translator) {
        this(host, translator, externalUploader(), task -> host.client().execute(task));
    }

    private static Uploader externalUploader() {
        return new Uploader() {
            public void upload(Path file, Consumer<String> ok, Consumer<String> error) { ImageUploader.shared().upload(file,ok,error); }
            public void upload(Path file, Consumer<String> ok, Consumer<String> error, BooleanSupplier valid) {
                ImageUploader.shared().upload(file,ok,error,valid);
            }
        };
    }

    /**
     * The two collaborators above, spelled out. Package-private and test-facing:
     * the production defaults are the ones the public constructor passes in.
     */
    ImageUploadPipeline(Host host, ActionToast.Translator translator, Uploader imageUploader,
                        Consumer<Runnable> renderHop) {
        this(host, translator, imageUploader, renderHop,
                MediaCompanionClient::hostingEnabled, MediaCompanionClient::uploadFile);
    }

    ImageUploadPipeline(Host host, ActionToast.Translator translator, Uploader imageUploader,
                        Consumer<Runnable> renderHop, BooleanSupplier hostingEnabled, Uploader hostedUploader) {
        this(host, translator, imageUploader, renderHop, hostingEnabled, hostedUploader, MediaCompanionClient::connectionEpoch);
    }

    ImageUploadPipeline(Host host, ActionToast.Translator translator, Uploader imageUploader,
                        Consumer<Runnable> renderHop, BooleanSupplier hostingEnabled, Uploader hostedUploader,
                        java.util.function.LongSupplier connectionEpoch) {
        this(host,translator,imageUploader,renderHop,hostingEnabled,hostedUploader,connectionEpoch,ImageBounds::inspect);
    }

    ImageUploadPipeline(Host host, ActionToast.Translator translator, Uploader imageUploader,
                        Consumer<Runnable> renderHop, BooleanSupplier hostingEnabled, Uploader hostedUploader,
                        java.util.function.LongSupplier connectionEpoch, Inspector inspector) {
        this.host = host;
        this.inspector = inspector;
        this.connectionEpoch = connectionEpoch;
        this.translator = translator;
        this.imageUploader = imageUploader;
        this.renderHop = renderHop;
        this.hostingEnabled = hostingEnabled;
        this.hostedUploader = hostedUploader;
    }

    /**
     * Uploads an image and drops its CICode into the draft. Shared by the file
     * picker and by Ctrl+V pastes.
     */
    public void uploadAndAppend(Path file) {
        if(com.atom.chat.onboarding.TutorialSandbox.active()){com.atom.chat.onboarding.TutorialSandbox.action();return;}
        long originEpoch = connectionEpoch.getAsLong();
        if (!host.active()) { rejectImage("atomchat.input.image_cancelled"); return; }
        ImageBounds.Inspection inspection;
        try {
            inspection = inspector.inspect(file);
        } catch (IOException | SecurityException error) {
            rejectImage("atomchat.input.image_invalid");
            return;
        }
        String rejection = inspection.rejection();
        if (rejection != null) {
            rejectImage(switch (rejection) {
                case "too_large" -> "atomchat.input.image_too_large";
                case "too_many_pixels" -> "atomchat.input.image_too_many_pixels";
                default -> "atomchat.input.image_invalid";
            });
            return;
        }
        int[] size = inspection.dimensions();
        renderHop.accept(() -> admitUpload(file,size,originEpoch));
    }

    /** Screen/nav state and the connection identity are checked together on the client thread. */
    private void admitUpload(Path file, int[] size, long originEpoch) {
        if (!host.active() || originEpoch != connectionEpoch.getAsLong()) {
            rejectImage("atomchat.input.image_cancelled"); return;
        }
        DraftTarget destination = host.captureDraft();
        if (destination == null) { rejectImage("atomchat.input.image_busy"); return; }
        UploadRequest request = new UploadRequest(destination, originEpoch);
        requests.add(request);
        com.atom.chat.diagnostics.FaultTrace.upload(request.id,"start",request.epoch);
        if (hostingEnabled.getAsBoolean()) {
            hostedUploader.upload(file, url -> appendImageCode(request, url, file, size), error -> {
                if (cancelled(error) || !request.current()) fail(request, "atomchat.input.image_cancelled");
                else if (request.fallback.compareAndSet(false, true)) uploadToUguu(request, file, size);
            });
        } else uploadToUguu(request, file, size);
    }

    private static boolean cancelled(String error) {
        return "disconnected".equals(error) || "cancelled".equals(error);
    }

    private void rejectImage(String key) {
        renderHop.accept(() -> {
            showTransientHint(translator.text(key));
            host.toast(key, false);
        });
    }

    private void fail(UploadRequest request, String key) {
        renderHop.accept(() -> {
            if (!request.finish()) return;
            request.destination.cancel();
            com.atom.chat.diagnostics.FaultTrace.upload(request.id,"failed",request.epoch);
            if (request.epoch == connectionEpoch.getAsLong()) {
                showTransientHint(translator.text(key));
                host.toast(key, false);
            }
        });
    }

    private void uploadToUguu(UploadRequest request, Path file, int[] size) {
        if (!request.current()) { fail(request, "atomchat.input.image_cancelled"); return; }
        com.atom.chat.diagnostics.FaultTrace.upload(request.id,"external",request.epoch);
        imageUploader.upload(file,
                url -> appendImageCode(request, url, file, size),
                error -> {
                    AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Image upload failed: {}"), error);
                    fail(request, "busy".equals(error) ? "atomchat.input.image_busy" : "atomchat.input.image_failed");
                }, request::current);
    }

    private void appendImageCode(UploadRequest request, String url, Path file, int[] size) {
        StringBuilder code = new StringBuilder("[[CICode,url=").append(url)
                .append(",name=").append(file.getFileName());
        if (size != null) code.append(",w=").append(size[0]).append(",h=").append(size[1]);
        code.append("]]");
        // Filename is optional metadata; drop it before asking the draft to fit a complete code.
        if (code.length() > 256) {
            code = new StringBuilder("[[CICode,url=").append(url);
            if (size != null) code.append(",w=").append(size[0]).append(",h=").append(size[1]);
            code.append("]]");
        }
        if (code.length() > 256) code = new StringBuilder("[[CICode,url=").append(url).append("]]");
        if (code.length() > 256) { fail(request, "atomchat.input.image_code_too_long"); return; }
        String text = code.toString();
        renderHop.accept(() -> {
            if (!request.finish()) return;
            if (request.epoch != connectionEpoch.getAsLong()) { request.destination.cancel(); return; }
            AppendResult result = request.destination.append(text);
            com.atom.chat.diagnostics.FaultTrace.upload(request.id,result.name(),request.epoch);
            if (result == AppendResult.DEFERRED) {
                showTransientHint(translator.text("atomchat.input.image_pending"));
                host.toast("atomchat.input.image_pending", false);
            }
        });
    }

    private final class UploadRequest {
        final long id = IDS.incrementAndGet();
        final DraftTarget destination;
        final long epoch;
        final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean fallback = new java.util.concurrent.atomic.AtomicBoolean();
        UploadRequest(DraftTarget destination, long epoch) { this.destination = destination; this.epoch = epoch; }
        boolean current() { return !done.get() && epoch == connectionEpoch.getAsLong(); }
        boolean finish() {
            if (!done.compareAndSet(false, true)) return false;
            requests.remove(this);
            return true;
        }
    }

    /**
     * Saves another player's CICode image to a local file. The save dialog runs
     * on the same worker-thread pattern as the open picker; the actual download
     * happens off-thread via {@link ImageSaver} so the raw bytes stay identical.
     */
    public void saveImage(ChatMessage message) {
        if(com.atom.chat.onboarding.TutorialSandbox.active()){com.atom.chat.onboarding.TutorialSandbox.action();return;}
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
                    AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Saved chat image to {}"), path);
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
        for (UploadRequest request : requests) {
            if (!request.current()) fail(request, "atomchat.input.image_cancelled");
            else return true;
        }
        return false;
    }

    public String transientHint() {
        return transientHint;
    }

    public long transientHintSetAt() {
        return transientHintSetAt;
    }
}
