package com.atom.chat.util;

import com.atom.chat.AtomChat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Image picker: a {@link JFileChooser} in a plain {@link JFrame}, skinned with
 * FlatLaf.
 *
 * <p>It took a while to get here, so the constraint is worth writing down: any
 * window <em>we</em> create can be made topmost and will then float above
 * Minecraft's topmost fullscreen window — that is proven. What cannot be done
 * is lifting a native {@code GetOpenFileName} dialog, which inherits neither
 * {@code WS_EX_TOPMOST} nor any hint we set, and sits at the bottom of the
 * z-order below the desktop. So the dialog is ours, and the look comes from
 * FlatLaf instead of Windows.
 *
 * <p>The look and feel is process-global: it has to be installed before any
 * Swing component exists, and it also affects the rest of the JVM. Minecraft
 * barely uses Swing, so that costs nothing in practice.
 *
 * <p>Runs on the EDT; the calling worker thread waits on a latch, so the render
 * thread keeps running and the game does not freeze.
 */
public final class FilePicker {
    private static boolean lookAndFeelInstalled;

    private FilePicker() {
    }

    /**
     * @param beforeShow runs on the EDT immediately before the chooser is shown
     * @param afterShow  runs on the EDT once the chooser is disposed
     */
    public static Path pickImage(Runnable beforeShow, Runnable afterShow) {
        return pickImage(beforeShow, afterShow, null);
    }

    /**
     * @param beforeShow runs on the EDT immediately before the chooser is shown
     * @param afterShow  runs on the EDT once the chooser is disposed
     * @param nameFilter optional file-name filter for the chooser; when null every
     *                   supported image extension is offered. The emote pack passes
     *                   {@code EmoteStore::isSupportedName} so the user can never
     *                   pick a file the store will silently refuse.
     */
    public static Path pickImage(Runnable beforeShow, Runnable afterShow, Predicate<String> nameFilter) {
        // The claim normally happened at client startup, before any other mod
        // could reach AWT; repeating it is free and covers a process where the
        // picker is the first AWT user.
        AwtDisplay.claim();
        AtomicReference<Path> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                if (!AwtDisplay.usable()) {
                    reportHeadless();
                    return;
                }
                if (beforeShow != null) {
                    beforeShow.run();
                }
                result.set(showChooser(nameFilter));
            } catch (Throwable t) {
                AtomChat.LOGGER.warn("Image picker failed", t);
            } finally {
                try {
                    if (afterShow != null) {
                        afterShow.run();
                    }
                } finally {
                    done.countDown();
                }
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return result.get();
    }

    /**
     * Opens a save dialog for downloaded images. Returns the chosen path, or
     * {@code null} when the user cancels.
     */
    public static Path pickSavePath(String suggestedName) {
        AwtDisplay.claim();
        AtomicReference<Path> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                if (!AwtDisplay.usable()) {
                    reportHeadless();
                    return;
                }
                installLookAndFeel();
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle(tr("atomchat.picker.save.title"));
                chooser.setAcceptAllFileFilterUsed(true);
                chooser.setSelectedFile(new File(defaultDirectory(), ImageFileNames.sanitize(suggestedName)));

                JFrame frame = new JFrame(tr("atomchat.picker.save.title"));
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.getContentPane().add(chooser, BorderLayout.CENTER);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                frame.setAlwaysOnTop(true);
                frame.toFront();

                try {
                    if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                        File file = chooser.getSelectedFile();
                        if (file != null) {
                            result.set(file.toPath());
                        }
                    }
                } finally {
                    frame.dispose();
                }
            } catch (Throwable t) {
                AtomChat.LOGGER.warn("Image save dialog failed", t);
            } finally {
                done.countDown();
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return result.get();
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    /**
     * AWT is unusable in this process - either a mod reached it before we did
     * (its headless answer is cached for the life of the JVM; see
     * {@link AwtDisplay}) or the machine has no display. Nothing can be shown,
     * so say why instead of leaving the player staring at a button that does
     * nothing.
     */
    private static void reportHeadless() {
        Throwable failure = AwtDisplay.failure();
        if (failure != null) {
            AtomChat.LOGGER.error("Image picker unavailable: AWT could not be initialised ({}). "
                    + "Headless state: {}", AwtDisplay.outcome(), failure.toString(), failure);
        } else {
            AtomChat.LOGGER.error("Image picker unavailable: AWT is headless ({}). "
                    + "Another mod most likely reached AWT first.", AwtDisplay.outcome());
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> {
                try {
                    if (client.inGameHud != null) {
                        client.inGameHud.getChatHud().addMessage(Text.translatable("atomchat.picker.headless"));
                    }
                } catch (Throwable t) {
                    AtomChat.LOGGER.warn("Could not tell the player the picker is unavailable", t);
                }
            });
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Could not reach the client to report the headless picker", t);
        }
    }

    private static Path showChooser(Predicate<String> nameFilter) {
        installLookAndFeel();

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(tr("atomchat.picker.title"));
        chooser.setAcceptAllFileFilterUsed(false);
        Predicate<String> accept = nameFilter != null ? nameFilter : ImageFiles::isImageName;
        String desc = nameFilter != null
                ? tr("atomchat.picker.filter.emote")
                : tr("atomchat.picker.filter.image");
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || accept.test(file.getName());
            }

            @Override
            public String getDescription() {
                return desc;
            }
        });
        chooser.setCurrentDirectory(defaultDirectory());
        // Details is the more useful default for picking one image out of a
        // folder: it shows name/date/size columns alongside the thumbnails.
        switchToDetailsView(chooser);
        // Inline thumbnails: every image row shows a preview as its file icon,
        // so there is no need to click a file and look at the right-hand pane.
        chooser.setFileView(new ThumbnailFileView(chooser));

        JFrame frame = new JFrame(tr("atomchat.picker.title"));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(chooser, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        // Always-on-top only sticks reliably on a window that is already
        // visible and focusable, and the activation is what actually raises it
        // above Minecraft's topmost fullscreen window.
        frame.setAlwaysOnTop(true);
        frame.toFront();

        Path result = null;
        try {
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (file != null && file.isFile()) {
                    result = file.toPath();
                }
            }
        } finally {
            frame.dispose();
        }
        return result;
    }

    /**
     * FlatLaf is what makes the chooser look modern; without it Swing falls
     * back to Metal, the grey Java look. The system look and feel is the
     * fallback so a FlatLaf failure degrades rather than breaks.
     */
    private static void installLookAndFeel() {
        if (lookAndFeelInstalled) {
            return;
        }
        lookAndFeelInstalled = true;
        try {
            // Loaded by name on purpose: our FlatLaf is bundled under a private
            // package (gradle/atomchat-layers.gradle), so a plain import would
            // name a class this jar does not contain — and a mod shipping the
            // public com.formdev.flatlaf.* would silently win the race instead.
            Class<?> lafClass = Class.forName("com.atom.chat.shaded.flatlaf.FlatLightLaf");
            LookAndFeel laf = (LookAndFeel) lafClass.getDeclaredConstructor().newInstance();
            UIManager.setLookAndFeel(laf);
            // The source jar is part of the line on purpose: the relocation only
            // earns its keep if the class really comes from our own bundle, and
            // that is exactly the claim that used to be false (another mod's
            // copy won the race). One line answers it at the moment of truth.
            AtomChat.LOGGER.info("Image picker look and feel: {} <- {}", lafClass.getName(), origin(lafClass));
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("FlatLaf unavailable, falling back to the system look and feel", t);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Throwable t2) {
                AtomChat.LOGGER.warn("System look and feel unavailable too, keeping the Swing default", t2);
            }
        }
    }

    /** Where a loaded class came from; {@code unknown} beats a stack trace here. */
    private static String origin(Class<?> type) {
        try {
            java.security.CodeSource source = type.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                return source.getLocation().toString();
            }
        } catch (Throwable ignored) {
        }
        ClassLoader loader = type.getClassLoader();
        return loader == null ? "bootstrap" : String.valueOf(loader);
    }

    /** Starts in the folder people actually keep pictures in. */
    private static File defaultDirectory() {
        String home = System.getProperty("user.home");
        File pictures = new File(home, "Pictures");
        if (pictures.isDirectory()) {
            return pictures;
        }
        // Chinese Windows names the shell folder 图片 instead.
        File localized = new File(home, "图片");
        if (localized.isDirectory()) {
            return localized;
        }
        return new File(home);
    }

    /**
     * JFileChooser has no public view-type setter; the FilePane registers
     * standard actions on the chooser's action map, so invoking the details
     * action before the dialog is shown gives us the same effect as the user
     * clicking the toolbar's details toggle.
     */
    private static void switchToDetailsView(JFileChooser chooser) {
        Action details = chooser.getActionMap().get("viewTypeDetails");
        if (details != null) {
            details.actionPerformed(new ActionEvent(chooser, ActionEvent.ACTION_PERFORMED,
                    "viewTypeDetails"));
        }
    }

    /**
     * Supplies inline thumbnails as file icons. The first paint of an image row
     * returns no custom icon (so FlatLaf's file icon is used); a small decoder
     * thread then produces a 48px thumbnail and repaints the chooser. Decoding
     * reuses {@link ImagePreview#decode}'s subsampling path, so huge photos are
     * never read at full resolution on the EDT.
     */
    private static final class ThumbnailFileView extends FileView {
        private static final int THUMB_SIZE = 48;
        private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AtomChat-Thumbnail");
            t.setDaemon(true);
            return t;
        });

        private final JFileChooser chooser;
        private final ConcurrentMap<String, ImageIcon> cache = new ConcurrentHashMap<>();
        private final Set<String> loading = ConcurrentHashMap.newKeySet();

        private ThumbnailFileView(JFileChooser chooser) {
            this.chooser = chooser;
        }

        @Override
        public Icon getIcon(File file) {
            if (file == null || !file.isFile() || !ImageFiles.isImageName(file.getName())) {
                return null;
            }
            String key = file.getAbsolutePath();
            Icon cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            if (!loading.add(key)) {
                return null;
            }
            LOADER.execute(() -> {
                BufferedImage image = ImagePreview.decode(file, THUMB_SIZE, THUMB_SIZE);
                if (image != null) {
                    cache.put(key, new ImageIcon(image));
                }
                loading.remove(key);
                if (image != null) {
                    SwingUtilities.invokeLater(chooser::repaint);
                }
            });
            return null;
        }
    }
}
