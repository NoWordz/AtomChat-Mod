package com.atom.chat.util;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Claims the process-wide AWT toolkit before anything else can lock it down.
 *
 * <p>The problem this exists for: Minecraft's own client entry point
 * ({@code net.minecraft.client.main.Main}) writes
 * {@code java.awt.headless=true} in its static initialiser, and
 * {@link GraphicsEnvironment} caches that answer in a static field the first
 * time any AWT class asks for it. Setting the property back afterwards changes
 * nothing - the cache has already been filled. A mod whose static initialisers
 * reach AWT first (TFC does it through a {@code java.awt.Color} field on one of
 * its enums) therefore poisons the whole process, and every Swing window opened
 * later throws {@link java.awt.HeadlessException}.
 *
 * <p>Being first is the only defence, so {@link #claim()} runs from each
 * platform's client-only mixin config plugin: Mixin prepares plugins before
 * Minecraft's {@code Main} runs its static initialiser, whereas by the time
 * mods are constructed the cached answer may already be locked. A dedicated
 * server never runs the claim - the loader-side plugins skip it when the dist
 * is not client, and Fabric does not even load the config. Issue #19 happened
 * because the shared plugin used to claim on both sides.
 *
 * <p>Deliberately JDK-only - no Minecraft or mod classes - so the earliest
 * possible caller can reach it before class loading has settled.
 */
public final class AwtDisplay {
    /** The toolkit was initialised here, with a display available. */
    public static final String CLAIMED = "claimed";
    /** Something reached AWT first; the headless answer is already cached. */
    public static final String LOCKED_HEADLESS = "locked-headless";
    /** The toolkit could not be initialised at all (no display, broken install). */
    public static final String UNAVAILABLE = "unavailable";
    /** Nobody asked for a claim yet. */
    public static final String UNCLAIMED = "unclaimed";

    private static final AtomicBoolean CLAIMED_ONCE = new AtomicBoolean();
    private static volatile String outcome = UNCLAIMED;
    private static volatile Throwable failure;

    private AwtDisplay() {
    }

    /**
     * Initialises AWT on the first call and reports what happened on every
     * call. Safe from any thread, safe to repeat, and never throws: a picker
     * that cannot open is a degraded feature, not grounds for taking the game
     * down.
     */
    public static String claim() {
        if (!CLAIMED_ONCE.compareAndSet(false, true)) {
            return outcome;
        }
        try {
            System.setProperty("java.awt.headless", "false");
            // Touching the toolkit is what fills GraphicsEnvironment's cached
            // headless flag; the property on its own changes nothing.
            Toolkit.getDefaultToolkit();
            outcome = GraphicsEnvironment.isHeadless() ? LOCKED_HEADLESS : CLAIMED;
        } catch (Throwable t) {
            failure = t;
            outcome = UNAVAILABLE;
        }
        return outcome;
    }

    /** What {@link #claim()} decided, or {@link #UNCLAIMED} before it ran. */
    public static String outcome() {
        return outcome;
    }

    /** The throwable behind {@link #UNAVAILABLE}, or null. */
    public static Throwable failure() {
        return failure;
    }

    /**
     * Whether an AWT window can actually be created right now. This is the
     * question the picker has to ask: {@link #CLAIMED} means we got there
     * first, but a machine can still have no display at all.
     */
    public static boolean usable() {
        try {
            return !GraphicsEnvironment.isHeadless();
        } catch (Throwable t) {
            return false;
        }
    }
}
