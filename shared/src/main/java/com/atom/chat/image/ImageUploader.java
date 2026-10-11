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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Uploads local images to uguu.se and returns the public URL.
 */
public final class ImageUploader {
    private static final String UGUU_ENDPOINT = "https://uguu.se/upload";
    private static final String BOUNDARY = "----AtomChat" + UUID.randomUUID();

    private final HttpClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AtomChat-Upload");
        t.setDaemon(true);
        return t;
    });

    public ImageUploader() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void upload(Path file, Consumer<String> onSuccess, Consumer<String> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] body = buildMultipart(file);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(UGUU_ENDPOINT))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String url = parseUrl(response.body());
                    if (url != null) {
                        return url;
                    }
                    return "ERROR: unexpected response";
                }
                return "ERROR: HTTP " + response.statusCode();
            } catch (Exception e) {
                AtomChat.LOGGER.error(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to upload image"), e);
                return "ERROR: " + e.getMessage();
            }
        }, executor).whenComplete((url, throwable) -> {
            if (throwable != null) {
                onError.accept("上传失败: " + throwable.getMessage());
            } else if (url.startsWith("ERROR")) {
                onError.accept(url);
            } else {
                onSuccess.accept(url);
            }
        });
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
