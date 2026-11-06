package com.atom.chat.image;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryUtil;

/** Reads Minecraft skin textures into the normalized 64x64 avatar grid. */
public final class SkinTextureReader {
    private static final int GRID_SIZE = 64;
    private static final int MAX_SIZE = 4096;
    private static final int MAX_PENDING = 8;
    private static final long MAX_PENDING_NANOS = 2_000_000_000L;
    private static final long MAX_PENDING_BYTES = 96L * 1024L * 1024L;
    private static final Object LOCK = new Object();
    private static final Map<Integer, Pending> PENDING = new LinkedHashMap<>();
    private static long pendingBytes;

    private SkinTextureReader() {}

    public enum State { PENDING, READY, INVALID }

    public record Result(State state, ByteBuffer pixels) {
        private static Result pending() { return new Result(State.PENDING, null); }
        private static Result invalid() { return new Result(State.INVALID, null); }
        private static Result ready(ByteBuffer pixels) { return new Result(State.READY, pixels); }
    }

    private record Geometry(int width, int height) {
        long bytes() {
            return Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        }
    }

    private record Pending(int pbo, long fence, int width, int height, long bytes, long startedNanos) {}

    /**
     * Starts a pixel-pack-buffer read, or polls the one already in flight for
     * this texture. A pending result deliberately leaves the avatar blank for
     * a frame; {@link AvatarRenderer#face} retries on the next draw.
     */
    public static Result readAsync(int texture) {
        synchronized (LOCK) {
            Pending pending = PENDING.get(texture);
            if (pending != null) {
                return poll(texture, pending);
            }

            Geometry geometry = geometry(texture);
            if (geometry == null) {
                return Result.invalid();
            }
            expireStale(System.nanoTime());
            if (!supportsAsyncReadback()) {
                ByteBuffer pixels = read(texture, geometry);
                return pixels == null ? Result.invalid() : Result.ready(pixels);
            }

            if (geometry.bytes() > MAX_PENDING_BYTES) {
                ByteBuffer pixels = read(texture, geometry);
                return pixels == null ? Result.invalid() : Result.ready(pixels);
            }
            // Keep queued work stable. Evicting the oldest PBO here makes a
            // nine-avatar page restart the same read every frame and starves
            // every row; a new texture waits until a slot is delivered.
            if (PENDING.size() >= MAX_PENDING || pendingBytes + geometry.bytes() > MAX_PENDING_BYTES) {
                return Result.pending();
            }

            Pending started = begin(texture, geometry);
            if (started == null) {
                return Result.invalid();
            }
            PENDING.put(texture, started);
            pendingBytes += started.bytes();
            return Result.pending();
        }
    }

    /** Drops every in-flight PBO and fence. Call while the render GL context is current. */
    public static void release() {
        synchronized (LOCK) {
            for (Pending pending : PENDING.values()) {
                delete(pending);
            }
            PENDING.clear();
            pendingBytes = 0L;
        }
    }

    /** Blocking compatibility path used by the real-GL readback tests and the synchronous fallback. */
    public static ByteBuffer read(int texture) {
        Geometry geometry = geometry(texture);
        return geometry == null ? null : read(texture, geometry);
    }

    private static ByteBuffer read(int texture, Geometry geometry) {
        int binding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        int alignment = GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT);
        int rowLength = GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH);
        int skipRows = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS);
        int skipPixels = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS);
        int packBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        ByteBuffer raw = MemoryUtil.memAlloc(Math.toIntExact(geometry.bytes()));
        try {
            // Foreign renderers can leave row padding, skips or a PBO active.
            GL21C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            resetPackState();
            GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, raw);
            return normalize(raw, geometry.width(), geometry.height());
        } finally {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, alignment);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, rowLength);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, skipRows);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, skipPixels);
            GL21C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, packBuffer);
            MemoryUtil.memFree(raw);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, binding);
        }
    }

    private static Pending begin(int texture, Geometry geometry) {
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldPackBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT);
        int rowLength = GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH);
        int skipRows = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS);
        int skipPixels = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS);
        int pbo = GL15C.glGenBuffers();
        if (pbo == 0) {
            return null;
        }
        long fence = 0L;
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pbo);
            GL15C.glBufferData(GL21C.GL_PIXEL_PACK_BUFFER, geometry.bytes(), GL15C.GL_STREAM_READ);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            resetPackState();
            GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L);
            fence = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (fence == 0L) {
                GL15C.glDeleteBuffers(pbo);
                return null;
            }
            return new Pending(pbo, fence, geometry.width(), geometry.height(), geometry.bytes(), System.nanoTime());
        } finally {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, alignment);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, rowLength);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, skipRows);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, skipPixels);
            GL21C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, oldPackBuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
        }
    }

    private static Result poll(int texture, Pending pending) {
        int status;
        try {
            status = GL32C.glClientWaitSync(pending.fence(), GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 0L);
        } catch (Throwable ignored) {
            finish(texture, pending);
            return Result.invalid();
        }
        if (status == GL32C.GL_TIMEOUT_EXPIRED) {
            if (System.nanoTime() - pending.startedNanos() >= MAX_PENDING_NANOS) {
                finish(texture, pending);
            }
            return Result.pending();
        }
        if (status == GL32C.GL_WAIT_FAILED) {
            finish(texture, pending);
            return Result.invalid();
        }

        int oldPackBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        ByteBuffer mapped = null;
        ByteBuffer normalized = null;
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pending.pbo());
            mapped = GL15C.glMapBuffer(GL21C.GL_PIXEL_PACK_BUFFER, GL15C.GL_READ_ONLY);
            if (mapped == null) {
                return Result.pending();
            }
            normalized = normalize(mapped, pending.width(), pending.height());
        } finally {
            if (mapped != null) {
                if (!GL15C.glUnmapBuffer(GL21C.GL_PIXEL_PACK_BUFFER)) {
                    normalized = null;
                }
            }
            GL21C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, oldPackBuffer);
            finish(texture, pending);
        }
        return normalized == null ? Result.pending() : Result.ready(normalized);
    }

    private static Geometry geometry(int texture) {
        int binding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        try {
            int width = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
            int height = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);
            if (width < GRID_SIZE || width > MAX_SIZE || width % GRID_SIZE != 0
                    || (height != width && height * 2L != width)) {
                return null;
            }
            return new Geometry(width, height);
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, binding);
        }
    }

    private static ByteBuffer normalize(ByteBuffer raw, int width, int height) {
        ByteBuffer grid = ByteBuffer.allocate(GRID_SIZE * GRID_SIZE * 4);
        int scale = width / GRID_SIZE;
        for (int y = 0; y < height / scale; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                int src = (y * scale * width + x * scale) * 4;
                int dst = (y * GRID_SIZE + x) * 4;
                for (int channel = 0; channel < 4; channel++) {
                    grid.put(dst + channel, raw.get(src + channel));
                }
            }
        }
        return grid;
    }

    private static boolean supportsAsyncReadback() {
        return GL.getCapabilities() != null && GL.getCapabilities().OpenGL32;
    }

    private static void resetPackState() {
        GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 1);
        GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, 0);
    }

    /** Reclaim work whose owner stopped drawing before another texture arrived. */
    private static void expireStale(long now) {
        java.util.Iterator<Map.Entry<Integer, Pending>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next().getValue();
            if (now - pending.startedNanos() >= MAX_PENDING_NANOS) {
                delete(pending);
                iterator.remove();
                pendingBytes -= pending.bytes();
            }
        }
    }

    private static void finish(int texture, Pending pending) {
        if (PENDING.remove(texture, pending)) {
            pendingBytes -= pending.bytes();
        }
        delete(pending);
    }

    private static void delete(Pending pending) {
        if (pending.fence() != 0L) {
            GL32C.glDeleteSync(pending.fence());
        }
        if (pending.pbo() != 0) {
            GL15C.glDeleteBuffers(pending.pbo());
        }
    }
}
