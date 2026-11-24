package com.atom.chat.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

/** Immutable CPU image of one native glyph, uploaded only in the bridge's vanilla preparation pass. */
public final class NativeGlyphRaster implements AutoCloseable {
    public interface Source {
        int width(); int height(); boolean colored();
        float left(); float right(); float top(); float bottom();
        void upload();
    }
    private final Source source;
    private Image image;
    public NativeGlyphRaster(Source source) { this.source = source; }
    public Source source() { return source; }
    public boolean prepared() { return image != null || source == null || source.width() <= 0 || source.height() <= 0; }

    /** Caller supplies a bridge vanillaPass; this code never runs during Skia paint. */
    public void prepare() {
        if (prepared()) return;
        int width = source.width(), height = source.height();
        int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        int texture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int pack = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int unpack = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int readFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int[] fields = { GL11C.GL_PACK_ALIGNMENT, GL11C.GL_PACK_ROW_LENGTH, GL11C.GL_PACK_SKIP_ROWS,
                GL11C.GL_PACK_SKIP_PIXELS, GL11C.GL_UNPACK_ALIGNMENT, GL11C.GL_UNPACK_ROW_LENGTH,
                GL11C.GL_UNPACK_SKIP_ROWS, GL11C.GL_UNPACK_SKIP_PIXELS };
        int[] previous = new int[fields.length];
        for (int i = 0; i < fields.length; i++) previous[i] = GL11C.glGetInteger(fields[i]);
        int temporary = GL11C.glGenTextures();
        int framebuffer = GL30C.glGenFramebuffers();
        ByteBuffer pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            for (int field : fields) GL11C.glPixelStorei(field, field == GL11C.GL_PACK_ALIGNMENT || field == GL11C.GL_UNPACK_ALIGNMENT ? 1 : 0);
            // Bypass the MC binding cache only for allocation; the platform upload adapter rebinds through MC.
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, temporary);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, source.colored() ? GL11C.GL_RGBA8 : GL30C.GL_R8,
                    width, height, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L);
            NativeGlyphUpload.bind(temporary);
            source.upload();
            // Upload providers change unpack state (including TTF rows); normalize readback independently.
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 1);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, 0);
            // Read only the owned glyph rectangle, never a caller's potentially larger texture.
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, temporary, 0);
            if(GL30C.glCheckFramebufferStatus(GL30C.GL_READ_FRAMEBUFFER)!=GL30C.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete native glyph framebuffer");
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glReadPixels(0, 0, width, height, source.colored() ? GL11C.GL_RGBA : GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, pixels);
            byte[] rgba = new byte[width * height * 4];
            if (source.colored()) pixels.get(rgba);
            else for (int i = 0; i < width * height; i++) {
                rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = (byte) 255;
                rgba[i * 4 + 3] = pixels.get(i);
            }
            image = Image.makeRaster(new ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), rgba, width * 4);
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30C.glDeleteFramebuffers(framebuffer);
            NativeGlyphUpload.bind(texture);
            GL11C.glDeleteTextures(temporary);
            GL13C.glActiveTexture(active);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pack);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, unpack);
            for (int i = 0; i < fields.length; i++) GL11C.glPixelStorei(fields[i], previous[i]);
            MemoryUtil.memFree(pixels);
        }
    }

    public void draw(Canvas canvas, float x, float baseline, float scale, int color, boolean italic, boolean mask) {
        if (image == null) return;
        int save = canvas.save();
        try (Paint paint = new Paint()) {
            canvas.translate(x, baseline - 7F * scale);
            if (italic) { canvas.translate(scale, 0); canvas.skew(-.25F, 0); }
            try (ColorFilter filter = ColorFilter.makeBlend(color, mask ? BlendMode.SRC_IN : BlendMode.MODULATE)) {
                paint.setColorFilter(filter);
                canvas.drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()), Rect.makeLTRB(source.left()*scale, source.top()*scale,
                        source.right()*scale, source.bottom()*scale), SamplingMode.DEFAULT, paint, true);
            }
        } finally { canvas.restoreToCount(save); }
    }
    public void close() { if (image != null) { image.close(); image = null; } }
}
