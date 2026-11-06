package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.diagnostics.FirstOpenBudget;
import com.atom.chat.font.FontManager;
import com.atom.lib.design.atomchat.UiTokens;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Schedules the work that used to run on the render thread simply because the
 * first panel frame was the first time anybody asked for it.
 *
 * <p><b>What is allowed on the cold threads.</b> File IO and pure Skia CPU work:
 * reading the bundled font, {@code FontMgr.makeFromData}, resolving an emoji
 * fallback face and rasterizing a colour glyph into an immutable CPU {@code Image}.
 * None of that touches GL, so none of it needs the render thread. The hand-off
 * back to the render thread is published through concurrent maps/volatiles; the
 * render thread only ever <em>takes</em> finished objects, never participates in
 * their construction.
 *
 * <p><b>What is not here.</b> GL textures, FBOs, shader programs, Skija
 * {@code DirectContext}/{@code Surface}: those are tied to the current GL
 * context, and this class deliberately does not touch them. The platforms call
 * {@link #consumeGlWarmup()} from their client tick (render thread) and run
 * their own GL warmup there.
 *
 * <p><b>Once.</b> Font prewarm runs once per process; GL warmup once per process;
 * emoji prewarm once per world join (reset on disconnect so a second world can
 * warm its own backlog). The gates are {@link AtomicBoolean}s and are covered by
 * tests.
 */
public final class FirstOpenWarmup {

    /** Bounded emoji working set: the visible backlog, not a whole 500-line history. */
    private static final int MAX_WARMUP_MESSAGES = 200;
    /** Hard upper bound for one warmup pass; keeps a pathological backlog bounded. */
    private static final int MAX_EMOJI_SPECS = 192;

    private static final AtomicBoolean FONT_STARTED = new AtomicBoolean();
    private static final AtomicBoolean EMOJI_STARTED = new AtomicBoolean();
    private static final AtomicBoolean GL_PENDING = new AtomicBoolean();
    private static final AtomicBoolean GL_DONE = new AtomicBoolean();
    private static volatile CountDownLatch fontReady = new CountDownLatch(1);

    private FirstOpenWarmup() {
    }

    /**
     * Loads the bundled regular + bold Typefaces on a daemon thread. Call once
     * from client init; safe to call again (no-op).
     */
    public static void prewarmFontsAsync() {
        if (!claimFontPrewarm()) {
            return;
        }
        startColdThread("AtomChat-FontPrewarm", () -> {
            long started = System.nanoTime();
            try {
                FontManager.prewarmBlocking();
            } catch (Throwable ignored) {
                // A missing native library is reported by SkiaSupport; the panel
                // never opens in that case, and the synchronous path stays intact.
            } finally {
                FirstOpenBudget.setPrewarmFont(System.nanoTime() - started);
                fontReady.countDown();
            }
        });
    }

    /**
     * Warms the emoji faces and glyphs used by the current backlog. The scanner
     * itself is pure and runs on the caller (render) thread; only the Skia work
     * is moved to the daemon.
     *
     * @param texts message content and reaction emoji strings, in display order
     */
    public static void prewarmEmojiAsync(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return;
        }
        AtomChatConfig cfg = AtomChatConfig.get();
        List<EmojiPrewarmSpec> specs = EmojiPrewarmScanner.collect(texts,
                new float[]{UiTokens.FONT_BODY, UiTokens.FONT_QUOTE, UiTokens.FONT_EMOJI},
                new int[]{cfg.bubbleTextColor, cfg.otherBubbleTextColor, 0xFFFFFF,
                        cfg.textPrimaryColor, cfg.textSecondaryColor});
        if (specs.isEmpty()) {
            return;
        }
        if (specs.size() > MAX_EMOJI_SPECS) {
            specs = specs.subList(0, MAX_EMOJI_SPECS);
        }
        if (!claimEmojiPrewarm()) {
            return;
        }
        List<EmojiPrewarmSpec> work = specs;
        startColdThread("AtomChat-EmojiPrewarm", () -> {
            long started = System.nanoTime();
            try {
                fontReady.await(10L, TimeUnit.SECONDS);
                SkiaFontRenderer.prewarmEmojiGlyphs(work);
            } catch (Throwable ignored) {
                // Best effort: a failed warmup only means the old cold path pays.
            } finally {
                FirstOpenBudget.setPrewarmEmoji(System.nanoTime() - started);
            }
        });
    }

    /**
     * Builds the warmup text list from the current feed. Platform-neutral: the
     * caller supplies each message's display text and reaction emoji strings.
     */
    public static List<String> warmupTexts(java.util.List<String> messageTexts,
                                           java.util.List<String> reactionEmoji) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (messageTexts != null) {
            int from = Math.max(0, messageTexts.size() - MAX_WARMUP_MESSAGES);
            for (int i = from; i < messageTexts.size(); i++) {
                String text = messageTexts.get(i);
                if (text != null && !text.isEmpty()) out.add(text);
            }
        }
        if (reactionEmoji != null) {
            for (String emoji : reactionEmoji) {
                if (emoji != null && !emoji.isEmpty()) out.add(emoji);
            }
        }
        return out;
    }

    /** Request the render-thread GL warmup for the next client tick. */
    public static void requestGlWarmup() {
        if (!GL_DONE.get()) {
            GL_PENDING.set(true);
        }
    }

    /**
     * Render thread: true exactly once per process, after {@link #requestGlWarmup()}.
     * The caller runs its platform GL warmup and reports the wall clock.
     */
    public static boolean consumeGlWarmup() {
        if (!GL_PENDING.compareAndSet(true, false)) {
            return false;
        }
        return GL_DONE.compareAndSet(false, true);
    }

    /** A new world may have a different emoji backlog; its warmup may run again. */
    public static void onDisconnect() {
        EMOJI_STARTED.set(false);
        GL_PENDING.set(false);
    }

    // ------------------------------------------------------------ test seams

    static boolean claimFontPrewarm() {
        return FONT_STARTED.compareAndSet(false, true);
    }

    static boolean claimEmojiPrewarm() {
        return EMOJI_STARTED.compareAndSet(false, true);
    }

    static Thread startColdThread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    static void resetForTest() {
        FONT_STARTED.set(false);
        EMOJI_STARTED.set(false);
        GL_PENDING.set(false);
        GL_DONE.set(false);
        fontReady = new CountDownLatch(1);
    }
}
