package com.atom.chat.image;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.AnimationFrameInfo;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Codec;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes an animated GIF into an {@link AnimatedImage} (0.2.7). Deliberately
 * narrow: only GIFs with more than one frame take this path — single-frame GIFs
 * and every other format return null so {@link ImageLoader} keeps using the
 * static decode it already trusts.
 *
 * <p>Bounds the decoded footprint before allocating: a frame count cap and a
 * per-image decoded-pixel budget, both trimmed rather than failing (a long GIF
 * plays its first {@link ImageLoader#MAX_ANIM_FRAMES} frames). If even two
 * frames would blow the pixel budget the caller falls back to a static first
 * frame.
 */
public final class GifDecoder {
    private static final int DEFAULT_FRAME_MS = 100;

    private GifDecoder() {
    }

    /** @return the decoded animation, or null when this is not an animated GIF. */
    static AnimatedImage decode(byte[] bytes, long nowMs) {
        if (!isGif(bytes)) {
            return null;
        }
        List<Image> frames = new ArrayList<>();
        Data data = null;
        Codec codec = null;
        try {
            data = Data.makeFromBytes(bytes);
            codec = Codec.makeFromData(data);
            if (codec == null) {
                return null;
            }
            int frameCount = codec.getFrameCount();
            if (frameCount <= 1) {
                return null;
            }
            ImageInfo info = codec.getImageInfo();
            int width = info.getWidth();
            int height = info.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }
            long pixelsPerFrame = (long) width * height;
            int pixelBudgetFrames = (int) Math.min(Integer.MAX_VALUE,
                    ImageLoader.MAX_ANIM_PIXELS_PER_IMAGE / pixelsPerFrame);
            int usable = Math.min(frameCount, Math.min(ImageLoader.MAX_ANIM_FRAMES, pixelBudgetFrames));
            if (usable <= 1) {
                // Two frames would already exceed the per-image budget.
                return null;
            }
            AnimationFrameInfo[] infos = codec.getFramesInfo();
            int[] durations = new int[usable];
            for (int i = 0; i < usable; i++) {
                Bitmap bitmap = new Bitmap();
                try {
                    if (!bitmap.allocN32Pixels(width, height)) {
                        break;
                    }
                    codec.readPixels(bitmap, i);
                    Image frame = Image.makeFromBitmap(bitmap);
                    if (frame == null) {
                        break;
                    }
                    frames.add(ImageLoader.downscale(frame, ImageLoader.MAX_ANIM_DIM));
                    durations[i] = (infos != null && i < infos.length && infos[i].getDuration() > 0)
                            ? infos[i].getDuration() : DEFAULT_FRAME_MS;
                } finally {
                    bitmap.close();
                }
            }
            if (frames.size() <= 1) {
                closeFrames(frames);
                return null;
            }
            int[] trimmed = new int[frames.size()];
            System.arraycopy(durations, 0, trimmed, 0, trimmed.length);
            return new AnimatedImage(frames, trimmed, codec.getRepetitionCount(), nowMs);
        } catch (Throwable t) {
            closeFrames(frames);
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to decode animated GIF ({} bytes)"),
                    bytes == null ? 0 : bytes.length, t);
            return null;
        } finally {
            if (codec != null) {
                codec.close();
            }
            if (data != null) {
                data.close();
            }
        }
    }

    static boolean isGif(byte[] bytes) {
        return bytes != null && bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
    }

    private static void closeFrames(List<Image> frames) {
        for (Image frame : frames) {
            try {
                frame.close();
            } catch (Throwable ignored) {
                // Best-effort cleanup on the failure path.
            }
        }
        frames.clear();
    }
}
