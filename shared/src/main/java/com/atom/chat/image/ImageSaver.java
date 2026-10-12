package com.atom.chat.image;

import com.atom.chat.AtomChat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Saves an image url to a local path as raw bytes.
 *
 * <p>Two transports meet here. External hosts are plain HTTP; server-hosted
 * media ({@code atomchat-media:}) is served by the companion channel, the same
 * one the renderer uses, so the saved file is byte-identical to what was shown.
 *
 * <p>This deliberately does not round-trip through Skia/ImageIO: GIF animation,
 * WebP and transparent PNGs must stay byte-identical. The future-based API is
 * intentionally small so a future e33chat-style banner system can be wired to
 * {@link CompletableFuture#whenComplete} without changing callers.
 */
public final class ImageSaver {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "AtomChat-ImageSave");
        t.setDaemon(true);
        return t;
    });

    private ImageSaver() {
    }

    public static CompletableFuture<Path> save(String url, Path target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Hosted media travels over the server companion, not HTTP, so
                // the byte source has to be picked by scheme. See
                // ImageLoader#fetchBytes.
                byte[] body = url.startsWith(ImageLoader.MEDIA_URL_PREFIX)
                        ? ImageLoader.fetchBytes(url)
                        : download(url);
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.write(target, body);
                return target;
            } catch (Exception e) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to save image {} to {}"), url, target, e);
                throw new RuntimeException("Failed to save image", e);
            }
        }, EXECUTOR);
    }

    /** Plain HTTP download. Redirects are followed: image hosts hand out links that move. */
    private static byte[] download(String url) throws Exception {
        HttpResponse<byte[]> response = CLIENT.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build(),
                info -> new BoundedImageBodySubscriber(info.headers().firstValueAsLong("Content-Length").orElse(-1)>ImageLoader.MAX_SOURCE_BYTES ? -1 : ImageLoader.MAX_SOURCE_BYTES));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }
}
