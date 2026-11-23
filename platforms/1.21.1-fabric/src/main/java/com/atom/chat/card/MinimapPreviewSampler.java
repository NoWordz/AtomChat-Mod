package com.atom.chat.card;

import com.atom.chat.api.card.MinimapPreviewCodec;
import com.atom.chat.api.card.ShareCard;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Client-only Skija {@link Image} cache &amp; decoder for {@link ShareCard} minimap previews.
 */
public final class MinimapPreviewSampler {
    private static final int MAX_CACHE = 48;

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

    public static String sampleFromLevel(World level, int centerX, int centerY, int centerZ, String dimension) {
        return MinimapTerrainSampler.sampleFromLevel(level, centerX, centerY, centerZ, dimension);
    }

    public static synchronized void clearCache() {
        for (Image img : IMAGE_CACHE.values()) {
            closeQuietly(img);
        }
        IMAGE_CACHE.clear();
    }

    public static synchronized Image resolveImage(ShareCard card) {
        if (card == null || card.location() == null) return null;

        String embedded = card.customData().get(MinimapPreviewCodec.DATA_KEY);
        if (embedded != null && !embedded.isBlank()) {
            Image existing = IMAGE_CACHE.get(embedded);
            if (existing != null) {
                return existing;
            }
            if (IMAGE_CACHE.containsKey(embedded)) {
                return null;
            }
            Image img = decodeToImage(embedded);
            putReplacing(embedded, img);
            return img;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return null;
        ShareCard.LocationPayload loc = card.location();
        String currentDim = mc.world.getRegistryKey().getValue().toString();
        if (!currentDim.equalsIgnoreCase(loc.dimension())) {
            return null;
        }
        int bx = loc.blockX();
        int by = loc.blockY();
        int bz = loc.blockZ();
        if (mc.world.getChunkManager().getWorldChunk(bx >> 4, bz >> 4) == null) {
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
        String sampled = MinimapTerrainSampler.sampleFromLevel(mc.world, bx, by, bz, loc.dimension());
        if (sampled == null) {
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
