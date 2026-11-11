package com.atom.chat.wallpaper;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.AtomChat;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Raster-only static preset generation; no GPU object crosses frames or threads. */
public final class WallpaperPresets {
    public record Preset(String id, int mode, int previewColor) { }
    private static final Preset[] PRESETS = {
        new Preset("cobalt",0,0xFF173B88), new Preset("planes",1,0xFFAD7055),
        new Preset("fluid",2,0xFF185648), new Preset("ribbons",3,0xFF863D65),
        new Preset("strata",4,0xFFAC804A), new Preset("chalk",5,0xFFE5DDC5),
        new Preset("silk",6,0xFF938D9E)
    };
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r,"AtomChat-WallpaperPreset"); thread.setDaemon(true); return thread;
    });
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final Map<String, Image> PREVIEWS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final WallpaperViewport VIEWPORT = new WallpaperViewport();
    private static volatile WallpaperViewport.Size renderSize = new WallpaperViewport.Size(576,1024,false);
    private static final java.util.concurrent.atomic.AtomicBoolean SIZE_REFRESH_QUEUED = new java.util.concurrent.atomic.AtomicBoolean();
    private static String requestedId="";
    private static volatile boolean loading;
    private static volatile String error = "";
    private static final WallpaperGeneration QUEUE = new WallpaperGeneration(WallpaperStore.class, WORKER,
            WallpaperPresets::renderCurrentSize, (id,png) -> {
                boolean ok=WallpaperStore.setPresetPng(id,png,GENERATION.get());
                if(!ok)error="atomchat.feedback.wallpaperPresetFailed";
                loading=false;return ok;
            }, failure -> {
                error="atomchat.feedback.wallpaperPresetFailed";loading=false;
                AtomChat.LOGGER.warn("[AtomChat/wallpaper] Static preset generation failed",failure);
            });
    private WallpaperPresets() { }
    public static Preset[] presets() { return PRESETS.clone(); }
    public static boolean loading() { return loading; }
    public static String error() { return error; }
    public static String selectedId() { return WallpaperStore.presetId(); }
    public static void cancelPending() {
        synchronized (WallpaperStore.class) {
            GENERATION.incrementAndGet(); QUEUE.cancel(); requestedId=""; loading=false; error="";
        }
    }
    static boolean isCurrentRequest(int generation) { return GENERATION.get()==generation; }

    private static Preset find(String id) {
        for (Preset p : PRESETS) if (p.id().equals(id)) return p;
        throw new IllegalArgumentException("Unknown wallpaper preset: " + id);
    }

    /** Compiles and renders once to a CPU raster and returns an encoded PNG. */
    public static byte[] renderPng(String id, int width, int height) {
        Preset preset=find(id);
        if(width<1 || height<1 || width>WallpaperViewport.MAX_SIDE || height>WallpaperViewport.MAX_SIDE || (long)width*height>WallpaperViewport.MAX_PIXELS) throw new IllegalArgumentException("Invalid raster dimensions");
        byte[] values=ByteBuffer.allocate(12).order(ByteOrder.nativeOrder())
                .putFloat(width).putFloat(height).putFloat(preset.mode()).array();
        try (RuntimeEffect effect=RuntimeEffect.makeForShader(WallpaperShader.SOURCE);
             Data data=Data.makeFromBytes(values);
             Shader shader=effect.makeShader(data,new Shader[0]);
             Surface surface=Surface.makeRasterN32Premul(width,height);
             Paint paint=new Paint().setShader(shader)) {
            surface.getCanvas().drawRect(Rect.makeWH(width,height),paint);
            try(Image image=surface.makeImageSnapshot();Data png=image.encodeToData()) {
                if(png==null) throw new IllegalStateException("Wallpaper PNG encoding failed");
                return png.getBytes();
            }
        }
    }

    /** Coalesce rapid selections without queueing stale full-size renders. */
    public static void apply(String id) {
        find(id);
        synchronized (WallpaperStore.class) {
            GENERATION.incrementAndGet(); requestedId=id; loading=true; error=""; QUEUE.request(id);
        }
    }

    private static byte[] renderCurrentSize(String id) {
        WallpaperViewport.Size size=renderSize;
        return renderPng(id,size.width(),size.height());
    }

    /** Resting physical panel size only. Hot frames never acquire the publication/IO lock. */
    public static void viewport(int physicalWidth,int physicalHeight,long nowMs) {
        WallpaperViewport.Size size=VIEWPORT.update(physicalWidth,physicalHeight,nowMs);
        if(size==null)return;
        renderSize=size;
        if(SIZE_REFRESH_QUEUED.compareAndSet(false,true))WORKER.execute(() -> {
            SIZE_REFRESH_QUEUED.set(false);
            synchronized(WallpaperStore.class) {
                String id=loading?requestedId:WallpaperStore.presetId();
                if(!id.isEmpty())apply(id);
            }
        });
    }
    public static boolean resolutionLimited() { return renderSize.limited(); }

    /** Shell init only. Eligibility, cancellation and publication share the store lock. */
    public static void ensureFirstUse(AtomChatConfig config) {
        synchronized (WallpaperStore.class) {
            if (!config.wallpaperFirstUsePending) return;
            config.wallpaperFirstUsePending=false;
            if (!AtomChatConfig.saveChecked(config)) return;
            // First use deliberately keeps the theme background without a wallpaper.
        }
    }

    /** Small immutable raster thumbnails share the strip's existing card interaction. */
    public static Image preview(String id) {
        find(id);
        Image ready=PREVIEWS.get(id);
        if(ready!=null || !REQUESTED.add(id)) return ready;
        WORKER.execute(() -> {
            try { PREVIEWS.put(id,Image.makeFromEncoded(renderPng(id,144,256))); }
            catch(RuntimeException failure) {
                AtomChat.LOGGER.warn("[AtomChat/wallpaper] Preset preview failed: {}",id,failure);
            }
        });
        return null;
    }
}
