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
    /** Minimum gap between two emoji warmup polls, so a busy chat cannot thrash the list. */
    private static final long EMOJI_POLL_INTERVAL_NANOS = 2_000_000_000L;

    private static final AtomicBoolean FONT_STARTED = new AtomicBoolean();
    private static final AtomicBoolean EMOJI_RUNNING = new AtomicBoolean();
    private static final AtomicBoolean GL_PENDING = new AtomicBoolean();
    private static final AtomicBoolean GL_DONE = new AtomicBoolean();
    private static volatile CountDownLatch fontReady = new CountDownLatch(1);

    /**
     * The {@code ChatStore} generation the running/last emoji prewarm covers.
     * A new generation means the backlog changed, so a fresh warmup is worth
     * doing; the same generation means there is nothing new to rasterize.
     */
    private static volatile long emojiGeneration = -1L;
    /** Wall clock of the last poll, so a busy chat cannot rebuild the list every tick. */
    private static volatile long lastEmojiPollNanos;

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
     * The colors the emoji glyphs are drawn with. Must match the render path
     * exactly: the prewarmed bitmap is the bitmap the panel will draw, so a
     * different color here is a different picture.
     */
    private static int[] emojiColors() {
        try {
            AtomChatConfig cfg = AtomChatConfig.get();
            return new int[]{cfg.bubbleTextColor, cfg.otherBubbleTextColor, 0xFFFFFF,
                    cfg.textPrimaryColor, cfg.textSecondaryColor};
        } catch (Throwable unavailable) {
            // No config (unit test, config not loaded yet): fall back to the
            // neutral colour. The product always has a config at this point.
            return new int[]{0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF};
        }
    }

    /**
     * Warms the emoji faces and glyphs used by the current backlog.
     *
     * <p><b>Why this is polled instead of fired once.</b> The first version of
     * this ran once on world join. At that moment the chat was still empty, so
     * the spec list came back empty and the warmup silently did nothing — the
     * first panel open then rasterized every emoji on the render thread, which
     * is exactly the 150 ms spike the warmup was supposed to remove (the
     * budget line showed {@code prewarm_emoji=-1.000}). The backlog only exists
     * <em>after</em> join, so the trigger has to follow the content.
     *
     * <p>The caller polls from the client tick with the cheap
     * {@code ChatStore.generation()} counter; the expensive part (building the
     * spec list) only runs when the generation moved, and at most once every
     * {@link #EMOJI_POLL_INTERVAL_NANOS}.
     *
     * @param generation the chat generation the caller is asking about
     * @param texts      supplies the message/reaction text; only called when a
     *                   warmup is actually going to run
     * @return true when a warmup was started by this call
     */
    public static boolean pollEmojiPrewarm(long generation,
                                           java.util.function.Supplier<List<String>> texts) {
        if (generation == emojiGeneration) {
            return false;
        }
        long now = System.nanoTime();
        long last = lastEmojiPollNanos;
        if (now - last < EMOJI_POLL_INTERVAL_NANOS) {
            return false;
        }
        if (!EMOJI_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        List<String> current;
        try {
            current = texts.get();
        } catch (Throwable ignored) {
            EMOJI_RUNNING.set(false);
            return false;
        }
        if (current == null || current.isEmpty()) {
            // Nothing to rasterize yet. Record the generation so we do not ask
            // again until the backlog actually changes, and let the caller see
            // that the warmup did run (0 ms) instead of never happening.
            emojiGeneration = generation;
            lastEmojiPollNanos = now;
            EMOJI_RUNNING.set(false);
            FirstOpenBudget.setPrewarmEmoji(0L);
            return false;
        }
        List<EmojiPrewarmSpec> specs = EmojiPrewarmScanner.collect(current,
                new float[]{UiTokens.FONT_BODY, UiTokens.FONT_QUOTE, UiTokens.FONT_EMOJI},
                emojiColors());
        if (specs.size() > MAX_EMOJI_SPECS) {
            specs = specs.subList(0, MAX_EMOJI_SPECS);
        }
        if (specs.isEmpty()) {
            emojiGeneration = generation;
            lastEmojiPollNanos = now;
            EMOJI_RUNNING.set(false);
            FirstOpenBudget.setPrewarmEmoji(0L);
            return false;
        }
        emojiGeneration = generation;
        lastEmojiPollNanos = now;
        final List<EmojiPrewarmSpec> work = specs;
        startColdThread("AtomChat-EmojiPrewarm", () -> {
            long started = System.nanoTime();
            try {
                fontReady.await(10L, TimeUnit.SECONDS);
                SkiaFontRenderer.prewarmEmojiGlyphs(work);
            } catch (Throwable ignored) {
                // Best effort: a failed warmup only means the old cold path pays.
            } finally {
                FirstOpenBudget.setPrewarmEmoji(System.nanoTime() - started);
                EMOJI_RUNNING.set(false);
            }
        });
        return true;
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
        emojiGeneration = -1L;
        EMOJI_RUNNING.set(false);
        lastEmojiPollNanos = 0L;
        GL_PENDING.set(false);
    }

    // ------------------------------------------------------------ test seams

    static boolean claimFontPrewarm() {
        return FONT_STARTED.compareAndSet(false, true);
    }

    /** The chat generation the emoji warmup currently covers. */
    public static long emojiGeneration() {
        return emojiGeneration;
    }

    static Thread startColdThread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    static void resetForTest() {
        FONT_STARTED.set(false);
        EMOJI_RUNNING.set(false);
        emojiGeneration = -1L;
        lastEmojiPollNanos = 0L;
        GL_PENDING.set(false);
        GL_DONE.set(false);
        fontReady = new CountDownLatch(1);
    }
}
