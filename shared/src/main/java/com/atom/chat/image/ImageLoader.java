package com.atom.chat.image;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Codec;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
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
 *   <li><b>Disk cache</b> — processed cache files under {@code <gameDir>/atomchat/cache/images/}
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
    public static final int MAX_SOURCE_BYTES = 16 * 1024 * 1024;
    public static final long MAX_SOURCE_PIXELS = 32_000_000L;
    public static final int MAX_FAILURES = 512;
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
    /**
     * How long the settings surface may reuse the last disk-cache total.
     * The value is display metadata, not a cache invariant: files written by
     * this loader refresh it immediately through {@link #trimDiskCache()},
     * while an external file appearing in the directory can wait one second.
     */
    static final long DISK_BYTES_TTL_MS = 1_000L;
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
    private static final int MAX_HUD_ANIM_DIM = 96;
    private static final long MAX_HUD_ANIM_PIXELS = 600_000L;
    private static final int MAX_LOCALLY_SENT = 100;

    /** Byte source for a URL; HTTP by default, injected in tests. */
    public interface Fetcher {
        byte[] fetch(String url) throws Exception;
    }

    private static final Executor MAINTENANCE = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "AtomChat-ImageCache");thread.setDaemon(true);return thread;
    });
    private static final ImageLoader INSTANCE = new ImageLoader(
            ImageLoader::httpFetch, System::currentTimeMillis,
            new java.util.concurrent.ThreadPoolExecutor(3, 3, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(32), r -> {
                        Thread thread=new Thread(r,"AtomChat-ImageLoad");thread.setDaemon(true);return thread;
                    }), MAINTENANCE);

    /** Companion-backed byte source for {@link #MEDIA_URL_PREFIX} URLs. */
    private static volatile Fetcher mediaFetcher;

    private final Fetcher fetcher;
    private final LongSupplier clock;
    private final Executor executor;
    private final Executor maintenance;
    private final java.util.concurrent.atomic.AtomicBoolean maintenanceQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private static final int MAX_PENDING_LOADS = 32;
    private static final long VISIBLE_REQUEST_MS = 2_000L;
    private static final long NEVER_SEEN = Long.MIN_VALUE;
    private final class LoadTask {
        final String url;
        final boolean fromHud;
        final AnimatedImage thumbnailSource;
        volatile long panelSeenAt = NEVER_SEEN;
        volatile long hudSeenAt = NEVER_SEEN;
        LoadTask(String url, boolean hud, AnimatedImage thumbnailSource, long now) {
            this.url = url;
            this.fromHud = hud;
            this.thumbnailSource = thumbnailSource;
            seen(hud, now);
        }
        boolean current() { return pending.get(url)==this; }
        void seen(boolean hud, long now) {
            if (hud) hudSeenAt = now;
            else panelSeenAt = now;
        }
        boolean visible(long now) {
            return recent(panelSeenAt, now) || recent(hudSeenAt, now);
        }
        private static boolean recent(long timestamp, long now) {
            return timestamp != NEVER_SEEN && now - timestamp <= VISIBLE_REQUEST_MS;
        }
    }

    private final LinkedHashMap<String, MessageImage> cache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, MessageImage> eldest) {
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
    // Like the other hot caches, guarded by pending; eviction is independent.
    private final LinkedHashMap<String, AnimatedImage> hudAnimations = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, AnimatedImage> eldest) {
            return size() > com.atom.chat.config.AtomChatConfig.CHAT_HUD_MAX_MESSAGES;
        }
    };
    private final LinkedHashMap<String, Boolean> locallySent = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> hudDiskMisses = new LinkedHashMap<>();
    // Admission, removal and hot cache publication share this monitor. I/O and decode run outside it.
    private final ConcurrentHashMap<String, LoadTask> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> failedUntil = new ConcurrentHashMap<>();
    private volatile Path diskDir;
    private volatile long diskBytes = -1L;
    private volatile long diskBytesAt = Long.MIN_VALUE / 2L;

    private ImageLoader(Fetcher fetcher, LongSupplier clock, Executor executor) {
        this(fetcher, clock, executor, executor);
    }
    private ImageLoader(Fetcher fetcher, LongSupplier clock, Executor executor, Executor maintenance) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.executor = executor;
        this.maintenance = maintenance;
    }

    public static ImageLoader get() {
        return INSTANCE;
    }

    /** A server's queued media must not publish into the next connection. */
    public void invalidateMedia() {
        synchronized (pending) {
            pending.keySet().removeIf(url -> url.startsWith(MEDIA_URL_PREFIX));
            failedUntil.keySet().removeIf(url -> url.startsWith(MEDIA_URL_PREFIX));
            cache.keySet().removeIf(url -> url.startsWith(MEDIA_URL_PREFIX));
            animatedCache.keySet().removeIf(url -> url.startsWith(MEDIA_URL_PREFIX));
            hudAnimations.keySet().removeIf(url -> url.startsWith(MEDIA_URL_PREFIX));
            locallySent.clear();
            hudDiskMisses.keySet().removeIf(url -> url.startsWith(MEDIA_URL_PREFIX));
        }
    }

    /** Enables the disk layer and trims any over-limit leftovers; called once
     *  by the client entrypoint. */
    public void init(Path diskDir) {
        this.diskDir = com.atom.chat.util.DataMigration.allowPersonalPath(diskDir) ? diskDir : null;
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
    private static ImageInfo validateSource(byte[] bytes) {
        if(bytes==null || bytes.length==0 || bytes.length>MAX_SOURCE_BYTES) throw new IllegalArgumentException("image_source_bytes");
        try(Data data=Data.makeFromBytes(bytes); Codec codec=Codec.makeFromData(data)) {
            if(codec==null) throw new IllegalArgumentException("image_source_codec");
            var info=codec.getImageInfo();
            if(info.getWidth()<=0 || info.getHeight()<=0 || (long)info.getWidth()*info.getHeight()>MAX_SOURCE_PIXELS)
                throw new IllegalArgumentException("image_source_pixels");
            return info;
        }
    }
    private static byte[] readSource(java.io.InputStream input) throws java.io.IOException {
        byte[] bytes=input.readNBytes(MAX_SOURCE_BYTES+1);
        if(bytes.length>MAX_SOURCE_BYTES) throw new java.io.IOException("image_source_bytes");
        return bytes;
    }
    public static Image decodeStatic(byte[] bytes, int maxDim) {
        validateSource(bytes);
        return decodeStaticValidated(bytes, maxDim);
    }

    private static Image decodeStaticValidated(byte[] bytes, int maxDim) {
        long startedAt = com.atom.chat.diagnostics.FrameProfile.start();
        try {
            return downscale(Image.makeFromEncoded(bytes), maxDim);
        } finally {
            com.atom.chat.diagnostics.FrameProfile.decode(startedAt);
        }
    }

    /** Inspect the display-sized pixels on the image worker. Opaque codecs keep the lazy path. */
    private static MessageImage decodeMessageImage(byte[] bytes, ImageInfo source) {
        long startedAt = com.atom.chat.diagnostics.FrameProfile.start();
        try {
            Image displayed = downscale(Image.makeFromEncoded(bytes), MAX_DIM);
            if (displayed == null) return null;
            if (source.getColorAlphaType() == ColorAlphaType.OPAQUE) return new MessageImage(displayed, false);
            MessageImage raster = MessageImage.fromRaster(displayed);
            if (raster != null) return raster;

            // Display-sized encoded images can still be lazy. Decode into one
            // bounded CPU bitmap and keep its pixels for subsequent drawing.
            try (var bitmap = new Bitmap()) {
                var info = ImageInfo.makeN32(displayed.getWidth(), displayed.getHeight(), ColorAlphaType.PREMUL,
                        displayed.getImageInfo().getColorSpace());
                if (!bitmap.allocPixels(info) || !displayed.readPixels(bitmap)) return new MessageImage(displayed, true);
                boolean transparent = !bitmap.computeIsOpaque();
                Image image = Image.makeFromBitmap(bitmap.setImmutable());
                if (image == null) return new MessageImage(displayed, true);
                displayed.close();
                return new MessageImage(image, transparent);
            }
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
            byte[] bytes=companion.fetch(url);
            if(bytes==null || bytes.length>MAX_SOURCE_BYTES) throw new java.io.IOException("image_source_bytes");
            return bytes;
        }
        HttpResponse<byte[]> response = HttpClientHolder.CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                info -> new BoundedImageBodySubscriber(info.headers().firstValueAsLong("Content-Length").orElse(-1)>MAX_SOURCE_BYTES ? -1 : MAX_SOURCE_BYTES)
        );
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
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

    /** Seeds the normal draw and preview cache without I/O; tutorial-only state owns removal. */
    public void putMemoryImage(String url, Image image) {
        if(url==null||image==null)return;
        var entry = MessageImage.fromRaster(image);
        synchronized(pending){cache.put(url, entry == null ? new MessageImage(image, true) : entry);clearFailure(url);}
    }

    /** Releases a memory-only seed; the cache holds the sole reference. */
    public void removeMemoryImage(String url) {
        if(url==null)return;
        synchronized(pending){cache.remove(url);}
    }

    /**
     * Returns the decoded image, or null while it loads. Only a
     * {@code visible} call may start a fetch — the message list calls this
     * from its draw path, which already covers the viewport (+80 px preload
     * margin).
     */
    public Image get(String url, boolean visible) {
        return imageOf(getMessageImage(url, visible));
    }

    /** HUD reads cached external images, but only local sends and valid companion media can start another fetch. */
    public Image getHudImage(String url, boolean visible) {
        return imageOf(getHudMessageImage(url, visible));
    }

    public MessageImage getMessageImage(String url, boolean visible) {
        return get(url, visible, false);
    }

    public MessageImage getHudMessageImage(String url, boolean visible) {
        return get(url, visible, true);
    }

    private static Image imageOf(MessageImage attachment) {
        return attachment == null ? null : attachment.image();
    }

    /** Called at the actual local send boundary, independently of how the server labels the echo. */
    public void rememberLocallySent(String url) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
        synchronized (pending) {
            locallySent.put(url, Boolean.TRUE);
            if (locallySent.size() > MAX_LOCALLY_SENT) locallySent.remove(locallySent.keySet().iterator().next());
        }
    }

    private MessageImage get(String url, boolean visible, boolean hud) {
        if (url == null || url.isBlank()) {
            return null;
        }
        AnimatedImage full;
        Path disk;
        LoadTask task;
        synchronized (pending) {
            MessageImage cached = cache.get(url);
            if (cached != null) return cached;
            AnimatedImage thumbnail = hud ? hudAnimations.get(url) : null;
            if (thumbnail != null) return thumbnail.messageFrameAt(clock.getAsLong());
            full = animatedCache.get(url);
            if (full != null && (!hud || !visible)) return full.messageFrameAt(clock.getAsLong());
            if (hud && !visible) return null;
            long now = clock.getAsLong();
            Long failed = failedUntil.get(url);
            if (failed != null && now < failed) return full == null ? null : full.messageFrameAt(now);
            if (failed != null) failedUntil.remove(url, failed);
            task = pending.get(url);
            if (task != null) {
                if (visible) {
                    task.seen(hud, now);
                } else if (!task.fromHud) {
                    pending.remove(url, task);
                }
                return full == null ? null : full.messageFrameAt(now);
            }
            if (!visible || pending.size() >= MAX_PENDING_LOADS) return full == null ? null : full.messageFrameAt(now);
            boolean allowNetwork = !hud || knownNetworkSource(url);
            Long diskMiss = hud && !allowNetwork ? hudDiskMisses.get(url) : null;
            if (full == null && diskMiss != null && now < diskMiss) return null;
            disk = full == null ? diskFile(url) : null;
            if (full == null && disk == null && (!allowNetwork || !isFetchable(url))) return null;
            task = new LoadTask(url, hud, full, now);
            pending.put(url, task);
        }
        final LoadTask scheduled=task;
        try {
            executor.execute(() -> {
                if (!scheduled.current() || !scheduled.visible(clock.getAsLong())) {
                    release(scheduled);
                    return;
                }
                load(url,disk,scheduled);
            });
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            release(scheduled);
            return full == null ? null : full.messageFrameAt(clock.getAsLong());
        }
        {
            // The executor may be direct (tests) and complete synchronously;
            // re-check before reporting a miss so the first visible call can
            // already serve the image.
            synchronized (pending) {
                MessageImage loaded = cache.get(url);
                if (loaded != null) return loaded;
                AnimatedImage animation = hud ? hudAnimations.get(url) : null;
                if (animation == null) animation = animatedCache.get(url);
                if (animation != null) return animation.messageFrameAt(clock.getAsLong());
            }
        }
        return full == null ? null : full.messageFrameAt(clock.getAsLong());
    }

    private void release(LoadTask task) {
        synchronized (pending) {
            pending.remove(task.url, task);
        }
    }

    /** Caller holds the admission monitor. */
    private boolean knownNetworkSource(String url) {
        return locallySent.containsKey(url) || com.atom.chat.net.MediaIds.isSafeId(com.atom.chat.net.MediaIds.idOf(url));
    }

    private boolean mayFetch(LoadTask task) {
        synchronized (pending) {
            return task.current() && (knownNetworkSource(task.url) || LoadTask.recent(task.panelSeenAt, clock.getAsLong()));
        }
    }

    private void load(String url, Path disk, LoadTask task) {
        boolean diskOnly = task.fromHud;
        try {
            if (task.thumbnailSource != null) {
                publishAnimation(task, task.thumbnailSource);
                return;
            }
            boolean fromDisk = disk != null && com.atom.chat.util.DataMigration.allowPersonalPath(disk) && Files.exists(disk);
            // A queued external request still needs recent panel visibility at the actual fetch boundary.
            diskOnly = !mayFetch(task);
            byte[] bytes;
            if(fromDisk) { try(java.io.InputStream input=Files.newInputStream(disk)) { bytes=readSource(input); } }
            else if (!diskOnly) bytes=fetcher.fetch(url);
            else {
                if (task.hudSeenAt != NEVER_SEEN) noteLoadFailure(task, true);
                return;
            }
            if (!task.current()) return;
            ImageInfo source = validateSource(bytes);
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
                publishAnimation(task, animated);
                return;
            }
            MessageImage image = decodeMessageImage(bytes, source);
            if (image != null) {
                if (disk != null && task.current() && (!fromDisk || source.getWidth() > MAX_DIM || source.getHeight() > MAX_DIM)) {
                    // Store only the small display version, never the original
                    // download. Existing raw-format entries are converted to
                    // the processed format on their next load.
                    writeDiskCache(disk, encodeForDisk(image.image()));
                }
                synchronized (pending) {
                    if (!task.current()) { image.image().close();return; }
                    cache.put(url, image);
                    clearFailure(url);
                }
            } else noteLoadFailure(task, diskOnly);
        } catch (Exception e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to load chat image {}"), url, e);
            noteLoadFailure(task, diskOnly);
        } finally {
            release(task);
        }
    }

    private void publishAnimation(LoadTask task, AnimatedImage image) {
        AnimatedImage thumbnail = task.hudSeenAt != NEVER_SEEN ? image.thumbnail(MAX_HUD_ANIM_DIM, MAX_HUD_ANIM_PIXELS) : null;
        synchronized (pending) {
            if (!task.current()) return;
            if (thumbnail != null) hudAnimations.put(task.url, thumbnail);
            if (task.panelSeenAt != NEVER_SEEN || thumbnail == null) putAnimated(task.url, image);
            clearFailure(task.url);
        }
    }

    private void noteLoadFailure(LoadTask task, boolean diskOnly) {
        synchronized (pending) {
            if (!task.current()) return;
            if (!diskOnly) {
                fail(task.url);
                return;
            }
            hudDiskMisses.put(task.url, clock.getAsLong() + FAILURE_TTL_MS);
            if (hudDiskMisses.size() > MAX_FAILURES) hudDiskMisses.remove(hudDiskMisses.keySet().iterator().next());
        }
    }

    private void clearFailure(String url) {
        failedUntil.remove(url);
        hudDiskMisses.remove(url);
    }

    /** Inserts an animation and trims the LRU. Caller holds the admission monitor. */
    private void putAnimated(String url, AnimatedImage image) {
        animatedCache.put(url, image);
        while (animatedCache.size() > 1
                && (animatedCache.size() > MAX_ANIMATED_CACHED
                    || animatedPixelsLocked() > MAX_ANIM_PIXELS_TOTAL)) {
            animatedCache.remove(animatedCache.keySet().iterator().next());
        }
    }

    /** Caller must hold the admission monitor. */
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
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Could not encode image for disk cache"));
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
            com.atom.chat.util.DataMigration.noLinks(disk);
            Path stage = disk.resolveSibling(disk.getFileName() + ".tmp");
            com.atom.chat.util.DataMigration.noLinks(stage);
            Files.createDirectories(disk.getParent());
            tmp = stage;
            Files.write(tmp, bytes);
            Files.move(tmp, disk, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to write image cache for {}"), disk, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // A stale temp is harmless; the next write replaces it.
                }
            }
        }
        requestDiskMaintenance();
    }

    private void fail(String url) {
        synchronized(failedUntil) {
            long now=clock.getAsLong();
            failedUntil.entrySet().removeIf(entry -> entry.getValue()<=now);
            while(failedUntil.size()>=MAX_FAILURES) {
                var iterator=failedUntil.keySet().iterator();
                if(!iterator.hasNext()) break;
                failedUntil.remove(iterator.next());
            }
            failedUntil.put(url,now+FAILURE_TTL_MS);
        }
    }

    /** Refreshes a cache file's last-used stamp; failure only ages it sooner. */
    private void touchDiskCache(Path disk) {
        long now = clock.getAsLong();
        if (now <= 0L) {
            // A test clock parked at 0 would stamp the epoch and read as expired.
            return;
        }
        try {
            com.atom.chat.util.DataMigration.noLinks(disk);
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
        if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir) || !Files.isDirectory(dir)) {
            diskBytes = 0L;
            diskBytesAt = clock.getAsLong();
            return;
        }
        long cutoff = System.currentTimeMillis() - MAX_DISK_AGE_MS;
        try (var stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>();
            long total = 0L;
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!com.atom.chat.util.DataMigration.allowPersonalPath(p)
                        || !p.getFileName().toString().endsWith(".bin")) {
                    continue;
                }
                long modified = modifiedOrZero(p);
                if (modified > 0L && modified < cutoff) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to expire image cache file {}"), p, e);
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
                    AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to trim image cache file {}"), oldest, e);
                }
            }
            diskBytes = total;
            diskBytesAt = clock.getAsLong();
        } catch (IOException e) {
            diskBytes = -1L;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to scan image cache directory {}"), dir, e);
        }
    }

    /** Total bytes currently stored in the disk cache (0 when not initialised). */
    public long diskCacheBytes() {
        long now=clock.getAsLong();
        if(diskBytes<0 || now<diskBytesAt || now-diskBytesAt>=DISK_BYTES_TTL_MS) requestDiskMaintenance();
        return Math.max(0L,diskBytes);
    }

    private void requestDiskMaintenance() {
        if(!maintenanceQueued.compareAndSet(false,true)) return;
        try {
            maintenance.execute(() -> {
                try { trimDiskCache(); }
                finally { maintenanceQueued.set(false); }
            });
        } catch(java.util.concurrent.RejectedExecutionException full) { maintenanceQueued.set(false); }
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
        if (dir == null) return 0;
        if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir)) return -1;
        if (!Files.isDirectory(dir)) return 0;
        int deleted = 0;
        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!com.atom.chat.util.DataMigration.allowPersonalPath(p)
                        || !p.getFileName().toString().endsWith(".bin")) {
                    continue;
                }
                try {
                    if (Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to delete image cache file {}"), p, e);
                }
            }
        } catch (IOException e) {
            diskBytes = -1L;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to clear image cache directory {}"), dir, e);
            return -1;
        }
        diskBytes = 0L;
        diskBytesAt = clock.getAsLong();
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
        synchronized (pending) {
            cache.clear();
            animatedCache.clear();
            hudAnimations.clear();
            hudDiskMisses.clear();
            locallySent.clear();
            failedUntil.clear();
            pending.clear();
        }
    }

    private Path diskFile(String url) {
        Path dir = diskDir;
        return dir == null ? null : dir.resolve(sha256Hex(url) + ".bin");
    }

    /** Fits the image inside {@link #MAX_DIM} without ever upscaling it. */
    static Image downscale(Image source) {
        return downscale(source, MAX_DIM);
    }

    /** Fits the image inside {@code maxDim} without ever upscaling it. */
    static Image downscale(Image source, int maxDim) {
        Image copy = scaledCopy(source, maxDim);
        if (source != copy) source.close();
        return copy;
    }

    /** Returns the borrowed source when it already fits. */
    static Image scaledCopy(Image source, int maxDim) {
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
