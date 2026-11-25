package com.atom.chat.card;

import com.atom.chat.api.card.MinimapPreviewCodec;
import com.atom.chat.api.card.ShareCard;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Client-only Skija {@link Image} cache &amp; decoder for {@link ShareCard} minimap previews.
 *
 * <p>Delegates pure terrain sampling to server-safe {@link MinimapTerrainSampler} so dedicated
 * servers never need to load this class or any Skija/client types.
 */
public final class MinimapPreviewSampler {
    private static final int MAX_CACHE = 48;
    private static final com.atom.chat.api.card.MinimapRetry RETRY = new com.atom.chat.api.card.MinimapRetry();

    private static final Map<String, Image> IMAGE_CACHE = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            if (size() > MAX_CACHE) {
                closeQuietly(eldest.getValue());
                return true;
            }
            return false;
        }
    };

    private MinimapPreviewSampler() {
    }

    /** Convenience delegate to {@link MinimapTerrainSampler#sampleFromLevel}. */
    public static String sampleFromLevel(Level level, int centerX, int centerY, int centerZ, String dimension) {
        return MinimapTerrainSampler.sampleFromLevel(level, centerX, centerY, centerZ, dimension);
    }

    public static synchronized void clearCache() {
        for (Image img : IMAGE_CACHE.values()) {
            closeQuietly(img);
        }
        IMAGE_CACHE.clear();
        RETRY.clear();
    }

    /**
     * Resolves a Skija {@link Image} for a LOCATION {@link ShareCard}.
     *
     * <p>Priority 1: sharer's embedded terrain preview ({@code customData["mm"]}), keyed directly
     * by payload string (zero hash-collision risk).
     * Priority 2: live local chunk sample if the viewer currently has the area loaded (never
     * negative-cached so chunks still streaming in will render as soon as they finish loading).
     */
    public static synchronized Image resolveImage(ShareCard card) {
        if (card == null || card.location() == null) return null;

        String embedded = card.customData().get(MinimapPreviewCodec.DATA_KEY);
        if (embedded != null && !embedded.isBlank()) {
            Image existing = IMAGE_CACHE.get(embedded);
            if (existing != null) {
                return existing;
            }
            if (IMAGE_CACHE.containsKey(embedded)) {
                return null; // Known-corrupt embedded bytes: negative-cache is valid (immutable payload)
            }
            Image img = decodeToImage(embedded);
            putReplacing(embedded, img);
            return img;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;
        ShareCard.LocationPayload loc = card.location();
        String currentDim = mc.level.dimension().location().toString();
        if (!currentDim.equalsIgnoreCase(loc.dimension())) {
            return null;
        }
        int bx = loc.blockX();
        int by = loc.blockY();
        int bz = loc.blockZ();
        if (mc.level.getChunkSource().getChunk(bx >> 4, bz >> 4, false) == null) {
            return null;
        }
        boolean nether = currentDim.toLowerCase(Locale.ROOT).contains("nether");
        String liveKey = nether
                ? ("live:" + currentDim + ":" + (bx >> 2) + ":" + (by >> 3) + ":" + (bz >> 2))
                : ("live:" + currentDim + ":" + (bx >> 2) + ":" + (bz >> 2));
        Image cached = IMAGE_CACHE.get(liveKey);
        if (cached != null) {
            return cached;
        }
        if (!RETRY.begin(liveKey, System.nanoTime())) return null;
        String sampled = MinimapTerrainSampler.sampleFromLevel(mc.level, bx, by, bz, loc.dimension());
        if (sampled == null) {
            // Do NOT negative-cache null under liveKey: border chunks may finish loading next second.
            return null;
        }
        Image img = decodeToImage(sampled);
        if (img != null) {
            putReplacing(liveKey, img);
        }
        return img;
    }

    private static void putReplacing(String key, Image img) {
        Image prev = IMAGE_CACHE.put(key, img);
        if (prev != null && prev != img) {
            closeQuietly(prev);
        }
    }

    private static void closeQuietly(Image img) {
        if (img != null) {
            try {
                img.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static Image decodeToImage(String encoded) {
        MinimapPreviewCodec.Decoded decoded = MinimapPreviewCodec.decode(encoded);
        if (decoded == null) return null;
        try (Bitmap bitmap = new Bitmap()) {
            ImageInfo info = new ImageInfo(decoded.width(), decoded.height(),
                    ColorType.RGBA_8888, ColorAlphaType.OPAQUE);
            if (!bitmap.installPixels(info, decoded.rgbaPixels(), decoded.width() * 4L)) {
                return null;
            }
            return Image.makeFromBitmap(bitmap);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
