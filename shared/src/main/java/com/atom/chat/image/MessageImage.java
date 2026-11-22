package com.atom.chat.image;

import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Pixmap;

import java.util.Objects;

/** A borrowed decoded frame and its attachment's stable transparency decision. A pending load returns no frame. */
public record MessageImage(Image image, boolean transparent) {
    public MessageImage {
        Objects.requireNonNull(image, "image");
    }

    static MessageImage fromRaster(Image raster) {
        try (var pixels = new Pixmap()) {
            return raster.peekPixels(pixels) ? new MessageImage(raster, !pixels.computeIsOpaque()) : null;
        }
    }
}
