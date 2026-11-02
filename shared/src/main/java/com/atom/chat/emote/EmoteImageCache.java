package com.atom.chat.emote;

import com.atom.chat.AtomChat;
import com.atom.chat.image.ImageLoader;
import io.github.humbleui.skija.Image;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lazily decodes emote files into Skia images, cached by file.
 *
 * <p>The grid shows a single static frame (0.2.7 decision): an animated GIF is
 * decoded to its first frame and every file is fitted to {@link #MAX_DIM}, so a
 * large sticker cannot pin full-resolution pixels behind a {@code s(44)} cell.
 * The sent message still animates — that path goes through the chat image
 * loader, not this cache.
 *
 * <p>Files that vanish (removed externally) are dropped on the next lookup and
 * files that fail to decode are remembered so we neither re-decode nor re-log
 * them every frame.
 */
public final class EmoteImageCache {
    /** Longest side kept for the grid; the cell is only {@code s(44)}. */
    public static final int MAX_DIM = 128;

    /**
     * Every entry owns a native image, so the cache is bounded and closes what
     * it drops. Unbounded it held a whole sticker folder (500 x 128x128x4 is
     * 32 MB) for the lifetime of the screen, and {@link #clear()} dropped the
     * references without closing them.
     */
    private static final int MAX_ENTRIES = 256;
    private final Map<File, Image> cache = new java.util.LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<File, Image> eldest) {
            if (size() <= MAX_ENTRIES) return false;
            eldest.getValue().close();
            return true;
        }
    };
    private final Set<File> failed = new HashSet<>();

    public Image image(File file) {
        if (file == null) {
            return null;
        }
        Image img = cache.get(file);
        if (img != null) {
            return img;
        }
        if (failed.contains(file)) {
            return null;
        }
        if (!file.isFile()) {
            cache.remove(file);
            return null;
        }
        try {
            Image decoded = ImageLoader.decodeStatic(
                    java.nio.file.Files.readAllBytes(file.toPath()), MAX_DIM);
            if (decoded != null) {
                cache.put(file, decoded);
                return decoded;
            }
            failed.add(file);
            return null;
        } catch (Exception e) {
            failed.add(file);
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Emote decode failed: {}"), file.getName(), e);
            return null;
        }
    }

    /** Called after an emote file is deleted or replaced. */
    public void invalidate(File file) {
        if (file == null) {
            return;
        }
        Image dropped = cache.remove(file);
        if (dropped != null) dropped.close();
        failed.remove(file);
    }

    public void clear() {
        for (Image image : cache.values()) image.close();
        cache.clear();
        failed.clear();
    }
}
