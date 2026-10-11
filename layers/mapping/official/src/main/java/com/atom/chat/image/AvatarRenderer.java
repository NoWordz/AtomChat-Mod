package com.atom.chat.image;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.humbleui.skija.Bitmap;

import io.github.humbleui.skija.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;

import java.nio.file.Path;

import org.lwjgl.opengl.GL11C;

/**
 * Samples the 8x8 face (plus semi-transparent hat layer) from a 64x64 player
 * skin texture on the GPU and turns it into a small Skia image that the UI can
 * draw as a circular avatar. One GL read per skin, cached forever after.
 */
public final class AvatarRenderer {
    private AvatarRenderer() {
    }

    private static final int SKIN_SIZE = 64;
    private static final int FACE_SIZE = 8;
    private static final int FACE_U = 8;
    private static final int FACE_V = 8;
    private static final int HAT_U = 40;

    private static final Map<ResourceLocation, Image> FACE_CACHE = new HashMap<>();
    private static final Set<ResourceLocation> FAILED = new HashSet<>();
    private static boolean dumped;

    /** Returns the face image for this skin, or null while/unless sampling works. */
    public static Image face(ResourceLocation skin) {
        if (skin == null) {
            return null;
        }
        Image cached = FACE_CACHE.get(skin);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(skin)) {
            return null;
        }
        Image sampled = sample(skin);
        if (sampled == null) {
            FAILED.add(skin);
            return null;
        }
        FACE_CACHE.put(skin, sampled);
        return sampled;
    }

    private static Image sample(ResourceLocation skin) {
        try {
            Minecraft client = Minecraft.getInstance();
            AbstractTexture texture = client.getTextureManager().getTexture(skin);
            int glId = texture.getId();

            ByteBuffer buf = ByteBuffer.allocateDirect(SKIN_SIZE * SKIN_SIZE * 4);
            int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, glId);
            // Driver write first, then mirror: GlStateManager caches the binding
            // per unit and its setter no-ops when the cache already matches, so
            // the raw call is what guarantees the read hits the skin texture.
            GlStateManager._bindTexture(glId);
            GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, buf);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
            GlStateManager._bindTexture(previous);

            // MC uploads skin images top-down without flipping, so buffer row v IS
            // skin row v - no GL flip here. Face blend (hat over face), then a CPU
            // 8x nearest-neighbour upscale so the avatar stays crisp when drawn large.
            int scale = 8;
            byte[] pixels = new byte[FACE_SIZE * scale * FACE_SIZE * scale * 4];
            for (int y = 0; y < FACE_SIZE; y++) {
                int srcRow = (FACE_V + y) * SKIN_SIZE;
                for (int x = 0; x < FACE_SIZE; x++) {
                    int faceIdx = (srcRow + (FACE_U + x)) * 4;
                    int hatIdx = (srcRow + (HAT_U + x)) * 4;
                    int hatA = buf.get(hatIdx + 3) & 0xFF;
                    // N32 is premultiplied BGRA on little-endian; face alpha is opaque.
                    // GL returns true RGBA; Skija N32 upload on this path consumes BGRA
                    // bytes (verified via debug dump: array RGBA rendered blue).
                    byte red = (byte) ((buf.get(hatIdx) & 0xFF) * hatA / 255 + (buf.get(faceIdx) & 0xFF) * (255 - hatA) / 255);
                    byte grn = (byte) ((buf.get(hatIdx + 1) & 0xFF) * hatA / 255 + (buf.get(faceIdx + 1) & 0xFF) * (255 - hatA) / 255);
                    byte blu = (byte) ((buf.get(hatIdx + 2) & 0xFF) * hatA / 255 + (buf.get(faceIdx + 2) & 0xFF) * (255 - hatA) / 255);
                    for (int dy = 0; dy < scale; dy++) {
                        int dstRow = ((y * scale + dy) * FACE_SIZE * scale + x * scale) * 4;
                        for (int dx = 0; dx < scale; dx++) {
                            int dst = dstRow + dx * 4;
                            pixels[dst] = blu;
                            pixels[dst + 1] = grn;
                            pixels[dst + 2] = red;
                            pixels[dst + 3] = (byte) 255;
                        }
                    }
                }
            }

            Bitmap bitmap = new Bitmap();
            bitmap.allocN32Pixels(FACE_SIZE * scale, FACE_SIZE * scale, false);
            bitmap.installPixels(io.github.humbleui.skija.ImageInfo.makeN32Premul(FACE_SIZE * scale, FACE_SIZE * scale),
                    pixels, FACE_SIZE * scale * 4L);
            maybeDump(buf, pixels);
            // Keep the bitmap alive: makeFromBitmap may share the pixel ref.
            return Image.makeFromBitmap(bitmap);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Hunt instrumentation (config debug=true, once per launch): writes the raw
     * GL read and the blended face as PNGs under {@code <config>/atomchat/debug/}
     * so the color pipeline can be verified against the real skin without
     * guessing byte orders.
     */
    private static void maybeDump(ByteBuffer raw, byte[] facePixels) {
        if (dumped || !AtomChatConfig.get().debug) {
            return;
        }
        dumped = true;
        try {
            Path dir = com.atom.chat.platform.Platform.configDir().resolve("atomchat/debug");
            Files.createDirectories(dir);
            NativeImage rawImg = new NativeImage(SKIN_SIZE, SKIN_SIZE, false);
            for (int y = 0; y < SKIN_SIZE; y++) {
                for (int x = 0; x < SKIN_SIZE; x++) {
                    int i = (y * SKIN_SIZE + x) * 4;
                    int r = raw.get(i) & 0xFF;
                    int g = raw.get(i + 1) & 0xFF;
                    int b = raw.get(i + 2) & 0xFF;
                    int a = raw.get(i + 3) & 0xFF;
                    rawImg.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            rawImg.writeToFile(dir.resolve("atomchat-debug-skin-gl.png"));
            NativeImage faceImg = new NativeImage(FACE_SIZE * 8, FACE_SIZE * 8, false);
            for (int y = 0; y < FACE_SIZE * 8; y++) {
                for (int x = 0; x < FACE_SIZE * 8; x++) {
                    int i = (y * FACE_SIZE * 8 + x) * 4;
                    int b = facePixels[i] & 0xFF;
                    int g = facePixels[i + 1] & 0xFF;
                    int r = facePixels[i + 2] & 0xFF;
                    faceImg.setPixelRGBA(x, y, (255 << 24) | (b << 16) | (g << 8) | r);
                }
            }
            faceImg.writeToFile(dir.resolve("atomchat-debug-face.png"));
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "Avatar debug dumps written to {}"), dir);
        } catch (Throwable t) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("avatar", "Avatar debug dump failed"), t);
        }
    }
}
