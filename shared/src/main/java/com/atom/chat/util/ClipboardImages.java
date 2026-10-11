package com.atom.chat.util;

import com.atom.chat.AtomChat;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads an image out of the system clipboard so Ctrl+V can paste one.
 *
 * <p>Minecraft's clipboard API only exposes {@code String}, so a screenshot or a
 * copied picture is completely invisible to it — that is why the old input hint
 * could promise "paste a file or image" without lying technically: nothing ever
 * looked. AWT is the only view that carries the image and file-list flavours.
 */
public final class ClipboardImages {
    private ClipboardImages() {
    }

    /**
     * Which paste flavour the clipboard currently holds, or {@code null} when it
     * is plain text and the vanilla text paste should handle it.
     *
     * <p>Only inspects the flavour list, so it is cheap enough to call on the
     * render thread. The actual payload is fetched off-thread by {@link #read}.
     */
    public static DataFlavor peek() {
        try {
            Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents == null) {
                return null;
            }
            if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return DataFlavor.imageFlavor;
            }
            if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return DataFlavor.javaFileListFlavor;
            }
        } catch (Throwable t) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Could not inspect the clipboard, falling back to a text paste"), t);
        }
        return null;
    }

    /**
     * Materialises the clipboard image as a real file the uploader can read.
     * Screenshots and copied pictures exist only in memory, so they are written
     * to a temp PNG first; copied files are used in place. Returns {@code null}
     * when the clipboard changed underneath us or holds nothing usable.
     */
    public static Path read(DataFlavor flavor) {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(flavor);
            if (DataFlavor.imageFlavor.equals(flavor) && data instanceof Image image) {
                return writeTemp(image);
            }
            if (DataFlavor.javaFileListFlavor.equals(flavor) && data instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof File file && ImageFiles.isImage(file.toPath())) {
                        return file.toPath();
                    }
                }
            }
        } catch (Throwable t) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Could not read the clipboard image"), t);
        }
        return null;
    }

    private static Path writeTemp(Image image) throws Exception {
        BufferedImage buffered = image instanceof BufferedImage b ? b : toBuffered(image);
        Path file = Files.createTempFile("atomchat-clipboard-", ".png");
        ImageIO.write(buffered, "png", file.toFile());
        file.toFile().deleteOnExit();
        return file;
    }

    private static BufferedImage toBuffered(Image image) {
        BufferedImage buffered = new BufferedImage(
                Math.max(1, image.getWidth(null)), Math.max(1, image.getHeight(null)),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffered.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return buffered;
    }
}
