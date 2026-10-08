package com.atom.chat.image;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/**
 * Async image loader/cache for chat images (CICode/URL). Hardened after the
 * 0.1.10 grill — the original fetched every URL the moment a message arrived,
 * kept full-resolution bitmaps in an unbounded map and had no disk layer:
 * a busy image chat could pin hundreds of MB of RAM and burst-download a
 * whole scrollback.
 *
 * <p>Four layers now:
 * <ol>
 *   <li><b>Viewport gating</b> — downloads only start for images actually
 *       drawn ({@code get(url, true)}); the message list already skips
 *       off-screen messages, so scrolling fast no longer queues the entire
 *       history.</li>
 *   <li><b>Downsampling</b> — decoded bitmaps are scaled once to a
 *       {@value #MAX_DIM}px long edge (a 20 MP photo drops from ~80 MB to
 *       ~2 MB, far sharper than the bubble ever displays).</li>
 *   <li><b>Bounded memory</b> — an access-order LRU of {@value #MAX_CACHED}
 *       images; evicted entries rely on Skija's managed finalisation rather
 *       than an eager {@code close()} so a bitmap drawn in the current frame
 *       can never be pulled from under the renderer.</li>
 *   <li><b>Disk cache</b> — processed cache files under {@code <gameDir>/atomchat-data/image-cache/}
 *       (SHA-256 of the URL), so re-entering a world does not re-download.
 *       Files are re-encoded as WebP/PNG at the {@value #MAX_DIM}px display
 *       size instead of storing the original download, so the disk layer is
 *       both small and fast to reload. It is capped at
 *       {@value #MAX_DISK_FILES} files / {@value #MAX_DISK_MB} MB and trims
 *       oldest entries by mtime on startup and after every write.</li>
 * </ol>
 *
 * <p>Failed fetches enter a short negative cache so a dead URL is not
 * retried every frame.
 */
public final class ImageLoader {
    /** LRU capacity in decoded images. */
    public static final int MAX_CACHED = 48;
    /** Longest allowed side of a cached bitmap, in pixels. */
    public static final int MAX_DIM = 768;
    /** How long a failed URL stays blacklisted. */
    public static final long FAILURE_TTL_MS = 60_000L;
    /** Disk cache: maximum number of processed files kept. */
    public static final int MAX_DISK_FILES = 500;
    /** Disk cache: maximum total processed bytes kept (100 MB). */
    public static final long MAX_DISK_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_DISK_MB = MAX_DISK_BYTES / (1024L * 1024L);
    /**
     * Entries that have not been used for this long are dropped even while the
     * count and size caps have room. The cache is a convenience copy, never the
     * only copy of anything the mod owns: the local emotes, wallpaper and avatar
     * are user files and do not live here.
     */
    public static final long MAX_DISK_AGE_MS = 7L * 86_400_000L;
    /** WebP quality for the processed disk cache (80 keeps photos small). */
    private static final int DISK_CACHE_QUALITY = 80;
    /** Custom CICode scheme whose bytes come from the server media companion. */
    public static final String MEDIA_URL_PREFIX = "atomchat-media:";
    /** Animated frames: longest side kept (the bubble only ever shows 220x140). */
    public static final int MAX_ANIM_DIM = 384;
    /** Animated frames: cap on frames decoded per image; longer GIFs are trimmed. */
    public static final int MAX_ANIM_FRAMES = 120;
    /** Animated frames: decoded-pixel budget for one image (about 32 MB). */
    public static final long MAX_ANIM_PIXELS_PER_IMAGE = 8L * 1024L * 1024L;
    /** Animated frames: how many animated images the LRU keeps. */
    public static final int MAX_ANIMATED_CACHED = 8;
    /** Animated frames: decoded-pixel budget across the animated LRU (about 96 MB). */
    public static final long MAX_ANIM_PIXELS_TOTAL = 24L * 1024L * 1024L;

    /** Byte source for a URL; HTTP by default, injected in tests. */
    public interface Fetcher {
        byte[] fetch(String url) throws Exception;
    }

    private static final ImageLoader INSTANCE = new ImageLoader(
            ImageLoader::httpFetch, System::currentTimeMillis,
            Executors.newFixedThreadPool(3));

    /** Companion-backed byte source for {@link #MEDIA_URL_PREFIX} URLs. */
    private static volatile Fetcher mediaFetcher;

    private final Fetcher fetcher;
    private final LongSupplier clock;
    private final Executor executor;

    private final LinkedHashMap<String, Image> cache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Image> eldest) {
            // No eager close(): the renderer may still hold this Image for the
            // current frame; Skija finalises the native memory instead.
            return size() > MAX_CACHED;
        }
    };
    /**
     * Animated images keyed by url. Same no-eager-close rule as the static
     * cache; the budget only stops the map (and so future allocations) from
     * growing without bound - evicted entries free on Skija's finalizer.
     */
    private final LinkedHashMap<String, AnimatedImage> animatedCache =
            new LinkedHashMap<>(16, 0.75F, true);
    private final ConcurrentHashMap<String, Boolean> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> failedUntil = new ConcurrentHashMap<>();
    private volatile Path diskDir;

    private ImageLoader(Fetcher fetcher, LongSupplier clock, Executor executor) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.executor = executor;
    }

    public static ImageLoader get() {
        return INSTANCE;
    }

    /** Enables the disk layer and trims any over-limit leftovers; called once
     *  by the client entrypoint. */
    public void init(Path diskDir) {
        this.diskDir = diskDir;
        trimDiskCache();
    }

    /** Registers the companion-backed fetcher for {@link #MEDIA_URL_PREFIX} URLs. */
    public static void setMediaFetcher(Fetcher fetcher) {
        mediaFetcher = fetcher;
    }

    private static boolean isFetchable(String url) {
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith(MEDIA_URL_PREFIX);
    }

    /**
     * The raw bytes behind an image url, whichever transport owns it: http(s)
     * goes out directly, {@link #MEDIA_URL_PREFIX} urls come from the server
     * media companion.
     *
     * <p>Anything that needs original bytes (the save-image action) has to come
     * through here rather than building its own HTTP request: the hosted-media
     * scheme has no HTTP transport at all, so an independent request rejects it
     * outright ("invalid URI scheme").
     *
     * <p>Blocking; call it off the render thread, like the loader does.
     */
    public static byte[] fetchBytes(String url) throws Exception {
        return httpFetch(url);
    }

    /**
     * Decodes a single frame (a GIF's first frame) and fits it inside
     * {@code maxDim}. The emote grid renders at cell size, so it uses this
     * instead of the animated path.
     */
    public static Image decodeStatic(byte[] bytes, int maxDim) {
        long startedAt = com.atom.chat.diagnostics.FrameProfile.start();
        try {
            return downscale(Image.makeFromEncoded(bytes), maxDim);
        } finally {
            com.atom.chat.diagnostics.FrameProfile.decode(startedAt);
        }
    }

    private static byte[] httpFetch(String url) throws Exception {
        if (url.startsWith(MEDIA_URL_PREFIX)) {
            Fetcher companion = mediaFetcher;
            if (companion == null) {
                throw new IllegalStateException("No media companion fetcher registered");
            }
            return companion.fetch(url);
        }
        HttpResponse<byte[]> response = HttpClientHolder.CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** Static holder keeps the HttpClient lazy: tests never build one. */
    private static final class HttpClientHolder {
        static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Memory/disk hit only; never triggers a download. */
    public Image get(String url) {
        return get(url, false);
    }

    /**
     * Returns the decoded image, or null while it loads. Only a
     * {@code visible} call may start a fetch — the message list calls this
     * from its draw path, which already covers the viewport (+80 px preload
     * margin).
     */
    public Image get(String url, boolean visible) {
        if (url == null || url.isBlank()) {
            return null;
        }
        synchronized (cache) {
            Image cached = cache.get(url);
            if (cached != null) {
                return cached;
            }
        }
        Image animated = animatedFrame(url);
        if (animated != null) {
            return animated;
        }
        Long failed = failedUntil.get(url);
        if (failed != null && clock.getAsLong() < failed) {
            return null;
        }
        Path disk = diskFile(url);
        if (visible && (disk != null || isFetchable(url))
                && pending.putIfAbsent(url, Boolean.TRUE) == null) {
            executor.execute(() -> load(url, disk));
            // The executor may be direct (tests) and complete synchronously;
            // re-check before reporting a miss so the first visible call can
            // already serve the image.
            synchronized (cache) {
                Image loaded = cache.get(url);
                if (loaded != null) {
                    return loaded;
                }
            }
            Image justLoaded = animatedFrame(url);
            if (justLoaded != null) {
                return justLoaded;
            }
        }
        return null;
    }

    /** Current frame of a cached animation, or null when the url is not one. */
    private Image animatedFrame(String url) {
        synchronized (animatedCache) {
            AnimatedImage animated = animatedCache.get(url);
            return animated == null ? null : animated.frameAt(clock.getAsLong());
        }
    }

    private void load(String url, Path disk) {
        try {
            boolean fromDisk = disk != null && Files.exists(disk);
            byte[] bytes = fromDisk ? Files.readAllBytes(disk) : fetcher.fetch(url);
            if (fromDisk) {
                // The stamp means "last used", not "first downloaded": a picture
                // the player keeps scrolling past must not expire on age alone.
                touchDiskCache(disk);
            }
            long gifStarted = com.atom.chat.diagnostics.FrameProfile.start();
            AnimatedImage animated;
            try {
                animated = GifDecoder.decode(bytes, clock.getAsLong());
            } finally {
                com.atom.chat.diagnostics.FrameProfile.decode(gifStarted);
            }
            if (animated != null) {
                if (disk != null && !fromDisk) {
                    // Keep the original bytes: re-encoding to WebP would flatten
                    // the animation, so the disk layer stores GIFs verbatim and
                    // the next load detects them by magic again.
                    writeDiskCache(disk, bytes);
                }
                putAnimated(url, animated);
                failedUntil.remove(url);
                return;
            }
            Image image = decodeStatic(bytes, MAX_DIM);
            if (image != null) {
                if (disk != null) {
                    // Store only the small display version, never the original
                    // download. Existing raw-format entries are converted to
                    // the processed format on their next load.
                    writeDiskCache(disk, encodeForDisk(image));
                }
                synchronized (cache) {
                    cache.put(url, image);
                }
                failedUntil.remove(url);
            } else {
                fail(url);
            }
        } catch (Exception e) {
            AtomChat.LOGGER.warn("Failed to load chat image {}", url, e);
            fail(url);
        } finally {
            pending.remove(url);
        }
    }

    /** Inserts an animation and trims the LRU to its count/pixel budget. */
    private void putAnimated(String url, AnimatedImage image) {
        synchronized (animatedCache) {
            animatedCache.put(url, image);
            while (animatedCache.size() > 1
                    && (animatedCache.size() > MAX_ANIMATED_CACHED
                        || animatedPixelsLocked() > MAX_ANIM_PIXELS_TOTAL)) {
                java.util.Iterator<java.util.Map.Entry<String, AnimatedImage>> it =
                        animatedCache.entrySet().iterator();
                if (!it.hasNext()) {
                    break;
                }
                it.next();
                it.remove();
            }
        }
    }

    /** Caller must hold the animated cache monitor. */
    private long animatedPixelsLocked() {
        long pixels = 0L;
        for (AnimatedImage image : animatedCache.values()) {
            pixels += image.pixelCount();
        }
        return pixels;
    }

    /** Re-encodes a decoded image for disk at display size (WebP, PNG fallback). */
    private static byte[] encodeForDisk(Image image) {
        try (Data data = image.encodeToData(EncodedImageFormat.WEBP, DISK_CACHE_QUALITY)) {
            if (data != null && data.getSize() > 0) {
                return data.getBytes();
            }
        } catch (Throwable ignored) {
            // Some Skia builds may not ship WebP; fall back to PNG below.
        }
        try (Data data = image.encodeToData(EncodedImageFormat.PNG)) {
            if (data != null && data.getSize() > 0) {
                return data.getBytes();
            }
        } catch (Throwable ignored) {
            AtomChat.LOGGER.warn("Could not encode image for disk cache");
        }
        return null;
    }

    /** Atomically replaces the disk cache file, then trims the whole cache. */
    private void writeDiskCache(Path disk, byte[] bytes) {
        if (disk == null || bytes == null || bytes.length == 0) {
            return;
        }
        Path tmp = null;
        try {
            Files.createDirectories(disk.getParent());
            tmp = disk.resolveSibling(disk.getFileName() + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, disk, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AtomChat.LOGGER.warn("Failed to write image cache for {}", disk, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // A stale temp is harmless; the next write replaces it.
                }
            }
        }
        trimDiskCache();
    }

    private void fail(String url) {
        failedUntil.put(url, clock.getAsLong() + FAILURE_TTL_MS);
    }

    /** Refreshes a cache file's last-used stamp; failure only ages it sooner. */
    private void touchDiskCache(Path disk) {
        long now = clock.getAsLong();
        if (now <= 0L) {
            // A test clock parked at 0 would stamp the epoch and read as expired.
            return;
        }
        try {
            Files.setLastModifiedTime(disk, FileTime.fromMillis(now));
        } catch (IOException ignored) {
            // Nothing to do: the entry keeps its previous stamp.
        }
    }

    /**
     * Keeps the disk cache inside the file-count / byte caps and drops anything
     * unused for {@link #MAX_DISK_AGE_MS}. Oldest files by last-modified time
     * are deleted first. Cache loss is always acceptable — a deleted entry is
     * simply downloaded again on the next visible request.
     */
    public void trimDiskCache() {
        Path dir = diskDir;
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - MAX_DISK_AGE_MS;
        try (var stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>();
            long total = 0L;
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!p.getFileName().toString().endsWith(".bin")) {
                    continue;
                }
                long modified = modifiedOrZero(p);
                if (modified > 0L && modified < cutoff) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        AtomChat.LOGGER.warn("Failed to expire image cache file {}", p, e);
                    }
                    continue;
                }
                total += sizeOrZero(p);
                files.add(p);
            }
            files.sort(Comparator.comparingLong(ImageLoader::modifiedOrZero));
            int removeFrom = 0;
            while (files.size() - removeFrom > MAX_DISK_FILES || total > MAX_DISK_BYTES) {
                if (removeFrom >= files.size()) {
                    break;
                }
                Path oldest = files.get(removeFrom++);
                long size = sizeOrZero(oldest);
                try {
                    Files.deleteIfExists(oldest);
                    total -= size;
                } catch (IOException e) {
                    AtomChat.LOGGER.warn("Failed to trim image cache file {}", oldest, e);
                }
            }
        } catch (IOException e) {
            AtomChat.LOGGER.warn("Failed to scan image cache directory {}", dir, e);
        }
    }

    /** Total bytes currently stored in the disk cache (0 when not initialised). */
    public long diskCacheBytes() {
        Path dir = diskDir;
        if (dir == null || !Files.isDirectory(dir)) {
            return 0L;
        }
        long total = 0L;
        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (p.getFileName().toString().endsWith(".bin")) {
                    total += sizeOrZero(p);
                }
            }
        } catch (IOException e) {
            AtomChat.LOGGER.warn("Failed to size image cache directory {}", dir, e);
        }
        return total;
    }

    /**
     * Deletes every cached image file. In-memory images are untouched.
     *
     * @return the number of cache files deleted, or {@code -1} if the cache
     *         directory itself could not be enumerated — the caller needs the
     *         difference to report a truthful result instead of claiming
     *         success on a locked directory.
     */
    public int clearDiskCache() {
        Path dir = diskDir;
        if (dir == null || !Files.isDirectory(dir)) {
            return 0;
        }
        int deleted = 0;
        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!p.getFileName().toString().endsWith(".bin")) {
                    continue;
                }
                try {
                    if (Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    AtomChat.LOGGER.warn("Failed to delete image cache file {}", p, e);
                }
            }
        } catch (IOException e) {
            AtomChat.LOGGER.warn("Failed to clear image cache directory {}", dir, e);
            return -1;
        }
        return deleted;
    }

    private static long sizeOrZero(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long modifiedOrZero(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Test seam: injectable byte source, clock and executor (direct = sync). */
    static ImageLoader forTest(Fetcher fetcher, LongSupplier clock, Executor executor) {
        return new ImageLoader(fetcher, clock, executor);
    }

    /** Test seam: drops all memory/negative state (disk cache untouched). */
    void resetMemory() {
        synchronized (cache) {
            cache.clear();
        }
        synchronized (animatedCache) {
            animatedCache.clear();
        }
        failedUntil.clear();
        pending.clear();
    }

    private Path diskFile(String url) {
        Path dir = diskDir;
        if (dir == null) {
            return null;
        }
        return dir.resolve(sha256Hex(url) + ".bin");
    }

    /** Fits the image inside {@link #MAX_DIM} without ever upscaling it. */
    static Image downscale(Image source) {
        return downscale(source, MAX_DIM);
    }

    /** Fits the image inside {@code maxDim} without ever upscaling it. */
    static Image downscale(Image source, int maxDim) {
        if (source == null) {
            return null;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        float scale = Math.min(1.0F, Math.min((float) maxDim / w, (float) maxDim / h));
        if (scale >= 0.999F) {
            return source;
        }
        int tw = Math.max(1, Math.round(w * scale));
        int th = Math.max(1, Math.round(h * scale));
        try (Surface surface = Surface.makeRasterN32Premul(tw, th)) {
            Canvas canvas = surface.getCanvas();
            try (Paint paint = new Paint().setAntiAlias(true)) {
                canvas.drawImageRect(source,
                        Rect.makeXYWH(0, 0, w, h),
                        Rect.makeXYWH(0, 0, tw, th),
                        SamplingMode.LINEAR, paint, false);
            }
            source.close();
            return surface.makeImageSnapshot();
        }
    }

    static String sha256Hex(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 is mandatory on every JVM; unreachable.
            throw new IllegalStateException(e);
        }
    }
}
