package com.atom.chat.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ImageUploaderTest {
    @TempDir Path temp;

    @Test void obsoleteQueuedUploadsReleaseSlotsWithoutReadingOrSending() throws Exception {
        QueueExecutor executor=new QueueExecutor();FakeClient client=new FakeClient();
        ImageUploader uploader=new ImageUploader(client,executor);
        var valid=new java.util.concurrent.atomic.AtomicBoolean(true);
        List<String> errors=new ArrayList<>(),success=new ArrayList<>();
        for(int i=0;i<8;i++)uploader.upload(temp.resolve("does-not-exist"),success::add,errors::add,valid::get);
        valid.set(false);uploader.cancelInvalid();uploader.cancelInvalid();
        assertEquals(java.util.Collections.nCopies(8,"cancelled"),errors);
        Path file=Files.write(temp.resolve("new-connection.png"),new byte[]{1});
        for(int i=0;i<8;i++)uploader.upload(file,success::add,errors::add);
        executor.runAll();
        assertEquals(8,client.calls.get(),"only new-connection jobs may reach HTTP");
        assertEquals(8,success.size());assertEquals(8,errors.size());
    }

    @Test void invalidatingARunningUploadInterruptsItAndNotifiesOnce() throws Exception {
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            FakeClient client=new FakeClient();client.started=new CountDownLatch(1);client.release=new CountDownLatch(1);
            ImageUploader uploader=new ImageUploader(client,executor);
            var valid=new java.util.concurrent.atomic.AtomicBoolean(true);
            List<String> errors=new java.util.concurrent.CopyOnWriteArrayList<>();AtomicInteger success=new AtomicInteger();
            Path file=Files.write(temp.resolve("image.png"),new byte[]{1});
            uploader.upload(file,u->success.incrementAndGet(),errors::add,valid::get);
            assertTrue(client.started.await(5,TimeUnit.SECONDS));valid.set(false);
            uploader.cancelInvalid();uploader.cancelInvalid();
            client.release.countDown();executor.shutdown();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));
            assertEquals(List.of("cancelled"),errors);assertEquals(0,success.get());
        } finally {executor.shutdownNow();}
    }

    @Test void rejectsNinthBeforeReadingAndReleasesEveryCompletedReservation() throws Exception {
        QueueExecutor executor = new QueueExecutor();
        FakeClient client = new FakeClient();
        ImageUploader uploader = new ImageUploader(client, executor);
        Path file = temp.resolve("created-after-admission.png");
        List<String> success = new ArrayList<>(), errors = new ArrayList<>();
        for (int i = 0; i < 9; i++) uploader.upload(file, success::add, errors::add);
        assertEquals(List.of("busy"), errors);
        assertEquals(8, executor.tasks.size());
        assertEquals(0, client.calls.get());
        Files.write(file, new byte[]{1, 2, 3});
        executor.runAll();
        assertEquals(8, success.size());
        for (int i = 0; i < 8; i++) uploader.upload(file, success::add, errors::add);
        executor.runAll();
        assertEquals(16, success.size());
        assertEquals(List.of("busy"), errors);
    }

    @Test void concurrentAdmissionWithRunningHttpAllowsOnlySevenQueued() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        FakeClient client = new FakeClient();
        client.started = new CountDownLatch(1);
        client.release = new CountDownLatch(1);
        ImageUploader uploader = new ImageUploader(client, executor);
        List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger successes = new AtomicInteger();
        Path file = Files.write(temp.resolve("image.png"), new byte[]{1});
        var submitters = java.util.concurrent.Executors.newFixedThreadPool(12);
        try {
            uploader.upload(file, s -> successes.incrementAndGet(), errors::add);
            assertTrue(client.started.await(5, TimeUnit.SECONDS));
            List<java.util.concurrent.Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 31; i++) calls.add(submitters.submit(() ->
                    uploader.upload(file, s -> successes.incrementAndGet(), errors::add)));
            for (var call : calls) call.get(5, TimeUnit.SECONDS);
            assertEquals(24, errors.size());
            assertTrue(errors.stream().allMatch("busy"::equals));
            client.release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(8, successes.get());
        } finally {
            client.release.countDown();
            executor.shutdownNow();
            submitters.shutdownNow();
        }
    }

    @Test void rejectionNotifiesBusyOnceAndDoesNotLeakReservations() {
        Executor rejected = r -> { throw new java.util.concurrent.RejectedExecutionException(); };
        ImageUploader uploader = new ImageUploader(new FakeClient(), rejected);
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < 16; i++) uploader.upload(Path.of("not-read"), s -> fail(), errors::add);
        assertEquals(java.util.Collections.nCopies(16, "busy"), errors);
    }

    @Test void preservesHttpMultipartTimeoutResponseAndErrorBehaviourWithoutNetwork() throws Exception {
        FakeClient client = new FakeClient();
        ImageUploader uploader = new ImageUploader(client, Runnable::run);
        Path file = Files.writeString(temp.resolve("image.png"), "image-bytes");
        List<String> success = new ArrayList<>(), errors = new ArrayList<>();
        uploader.upload(file, success::add, errors::add);
        assertEquals(List.of("https://example.invalid/upload.png"), success);
        HttpRequest request = client.request;
        assertEquals(URI.create("https://uguu.se/upload"), request.uri());
        assertEquals("POST", request.method());
        assertEquals(Duration.ofSeconds(60), request.timeout().orElseThrow());
        String contentType = request.headers().firstValue("Content-Type").orElseThrow();
        assertTrue(contentType.startsWith("multipart/form-data; boundary="));
        String body = readBody(request);
        assertTrue(body.contains("name=\"files[]\"; filename=\"image.png\"\r\n"));
        assertTrue(body.contains("\r\n\r\nimage-bytes\r\n"));
        assertTrue(body.endsWith("--" + contentType.substring(contentType.indexOf("boundary=") + 9) + "--\r\n"));
        client.status = 503;
        uploader.upload(file, success::add, errors::add);
        client.status = 200;
        client.body = "{}";
        uploader.upload(file, success::add, errors::add);
        client.failure = true;
        uploader.upload(file, success::add, errors::add);
        assertEquals(List.of("ERROR: HTTP 503", "ERROR: unexpected response", "ERROR: offline probe"), errors);
        assertEquals(1, success.size());
    }

    private static String readBody(HttpRequest request) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) { while (item.hasRemaining()) bytes.write(item.get()); }
            public void onError(Throwable error) { fail(error); }
            public void onComplete() {}
        });
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static class QueueExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable runnable) { tasks.add(runnable); }
        void runAll() { for (Runnable r; (r = tasks.poll()) != null;) r.run(); }
    }

    private static class FakeClient extends HttpClient {
        final AtomicInteger calls = new AtomicInteger();
        HttpRequest request;
        int status = 200;
        String body = "{\"files\":[{\"url\":\"https://example.invalid/upload.png\"}]}";
        boolean failure;
        CountDownLatch started, release;
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
            this.request = request;
            calls.incrementAndGet();
            if (started != null) started.countDown();
            if (release != null && !release.await(5, TimeUnit.SECONDS)) throw new IOException("probe timed out");
            if (failure) throw new IOException("offline probe");
            return new HttpResponse<>() {
                public int statusCode() { return status; }
                public HttpRequest request() { return request; }
                public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
                @SuppressWarnings("unchecked") public T body() { return (T) body; }
                public Optional<SSLSession> sslSession() { return Optional.empty(); }
                public URI uri() { return request.uri(); }
                public Version version() { return Version.HTTP_1_1; }
            };
        }
        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(15)); }
        public Redirect followRedirects() { return Redirect.NORMAL; }
        public Optional<ProxySelector> proxy() { return Optional.empty(); }
        public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        public SSLParameters sslParameters() { return new SSLParameters(); }
        public Optional<Authenticator> authenticator() { return Optional.empty(); }
        public Version version() { return Version.HTTP_1_1; }
        public Optional<Executor> executor() { return Optional.empty(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h) { throw new UnsupportedOperationException(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) { throw new UnsupportedOperationException(); }
    }
}
