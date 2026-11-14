package com.atom.chat.image;

import com.atom.chat.AtomChat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Uploads local images to uguu.se and returns the public URL.
 */
public final class ImageUploader {
    private static final String UGUU_ENDPOINT = "https://uguu.se/upload";
    private static final String BOUNDARY = "----AtomChat" + UUID.randomUUID();

    /**
     * One uploader for the process. It is created per screen otherwise, and
     * each instance owns an HttpClient (a selector thread plus a connection
     * pool) and its own executor; the screen is rebuilt on every open and every
     * resize, so the old instances' threads were never released.
     */
    private static volatile ImageUploader shared;

    public static synchronized ImageUploader shared() {
        if (shared == null) shared = new ImageUploader();
        return shared;
    }

    public static void cancelInvalidShared() {
        ImageUploader uploader = shared;
        if (uploader != null) uploader.cancelInvalid();
    }

    private final HttpClient client;
    private final Executor executor;
    private final Semaphore slots = new Semaphore(8);
    private final java.util.Set<UploadJob> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ImageUploader() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(7), r -> {
                    Thread t = new Thread(r, "AtomChat-Upload");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy()));
    }

    ImageUploader(HttpClient client, Executor executor) {
        this.client = client;
        this.executor = executor;
    }

    public void upload(Path file, Consumer<String> onSuccess, Consumer<String> onError) {
        upload(file,onSuccess,onError,()->true);
    }

    public void upload(Path file, Consumer<String> onSuccess, Consumer<String> onError,
                       java.util.function.BooleanSupplier valid) {
        if (!valid.getAsBoolean()) { onError.accept("cancelled"); return; }
        if (!slots.tryAcquire()) { onError.accept("busy"); return; }
        UploadJob job=new UploadJob(file,onSuccess,onError,valid);
        pending.add(job);
        try { executor.execute(job); }
        catch (RejectedExecutionException rejected) { job.finish(null,"busy"); }
    }

    /** Drops obsolete queued work and interrupts its blocking HTTP call, if already running. */
    public void cancelInvalid() {
        for(UploadJob job:pending) if(!job.valid.getAsBoolean()) job.cancel();
    }

    private final class UploadJob implements Runnable {
        final Path file;
        final Consumer<String> success,error;
        final java.util.function.BooleanSupplier valid;
        final java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean();
        private Thread running;
        UploadJob(Path file,Consumer<String> success,Consumer<String> error,java.util.function.BooleanSupplier valid) {
            this.file=file;this.success=success;this.error=error;this.valid=valid;
        }
        @Override public void run() {
            synchronized(this) { if(done.get())return;running=Thread.currentThread(); }
            try {
                if(!valid.getAsBoolean()) {finish(null,"cancelled");return;}
                byte[] body=buildMultipart(file);
                if(!valid.getAsBoolean()) {finish(null,"cancelled");return;}
                HttpRequest request=HttpRequest.newBuilder().uri(URI.create(UGUU_ENDPOINT))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type","multipart/form-data; boundary="+BOUNDARY)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
                if(!valid.getAsBoolean()) {finish(null,"cancelled");return;}
                String url=response.statusCode()==200 ? parseUrl(response.body()) : null;
                finish(url,url!=null ? null : "ERROR: "+(response.statusCode()==200?"unexpected response":"HTTP "+response.statusCode()));
            } catch(Exception failure) {
                if(done.get() || !valid.getAsBoolean() || failure instanceof InterruptedException) finish(null,"cancelled");
                else {
                    AtomChat.LOGGER.error(com.atom.chat.diagnostics.AtomChatLog.message("resource","Failed to upload image"),failure);
                    finish(null,"ERROR: "+failure.getMessage());
                }
            } finally { synchronized(this) {running=null;} }
        }
        synchronized void cancel() {
            if(done.get())return;
            if(executor instanceof ThreadPoolExecutor pool) pool.remove(this);
            if(running!=null && running!=Thread.currentThread()) running.interrupt();
            finish(null,"cancelled");
        }
        void finish(String url,String reason) {
            if(!done.compareAndSet(false,true))return;
            pending.remove(this);slots.release();
            if(reason==null)success.accept(url);else error.accept(reason);
        }
    }

    private byte[] buildMultipart(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        byte[] fileBytes = Files.readAllBytes(file);
        StringBuilder head = new StringBuilder();
        head.append("--").append(BOUNDARY).append("\r\n");
        head.append("Content-Disposition: form-data; name=\"files[]\"; filename=\"").append(fileName).append("\"\r\n");
        head.append("Content-Type: application/octet-stream\r\n\r\n");
        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headBytes.length + fileBytes.length + tailBytes.length];
        System.arraycopy(headBytes, 0, body, 0, headBytes.length);
        System.arraycopy(fileBytes, 0, body, headBytes.length, fileBytes.length);
        System.arraycopy(tailBytes, 0, body, headBytes.length + fileBytes.length, tailBytes.length);
        return body;
    }

    private String parseUrl(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("files") && obj.get("files").isJsonArray()) {
                    JsonArray files = obj.getAsJsonArray("files");
                    if (files.size() > 0 && files.get(0).isJsonObject()) {
                        JsonObject first = files.get(0).getAsJsonObject();
                        if (first.has("url")) {
                            return first.get("url").getAsString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to parse uguu response: {}"), json, e);
        }
        return null;
    }
}
