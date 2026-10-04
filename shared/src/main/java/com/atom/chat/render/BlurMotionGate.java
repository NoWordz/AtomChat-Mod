package com.atom.chat.render;

import com.atom.chat.AtomChat;

/**
 * Cadence gate for the panel blur pre-pass (r15 render-pressure pass).
 *
 * <p>The Kawase blur re-captures and re-blurs the world every 2 frames while
 * the panel is open — expensive, and pointless when nothing the blur can see
 * has changed. This gate is the shared decision brain: the platform side
 * (AtomChatScreen) feeds it one {@link #noteFrame} per frame with the camera
 * angles and whether panel chrome is animating, and the blur renderer asks
 * {@link #allowFullRefresh} whether its due frame-slot may actually run.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Moving (camera beyond the threshold, or chrome animating) — the blur
 *       refreshes on every due slot, i.e. the original every-2-frames
 *       cadence.</li>
 *   <li>Still — the full refresh falls back to at most one per
 *       {@value #STATIC_FALLBACK_MS} ms, so world changes (mobs walking into
 *       frame, water, block updates behind the player's back) eventually show
 *       up even though no camera motion was measured. The blur texture is
 *       reused between full refreshes, so nothing about the Kawase pipeline
 *       changes.</li>
 * </ul>
 *
 * <p>Deliberately conservative on purpose: the gate cannot see world motion,
 * only camera motion — the fallback interval is what keeps a truly frozen
 * scene from freezing the blur.</p>
 */
public final class BlurMotionGate {
    /**
     * Camera rotation delta (degrees, per frame) counted as motion. Below this
     * the blur can't resolve the difference anyway at Kawase's blur radius.
     */
    private static final float CAMERA_EPS_DEG = 0.15F;
    /**
     * Motion must be absent this long before the slow cadence arms — swinging
     * the camera for one frame between pauses must not look like a still scene.
     */
    private static final long STILL_HOLD_MS = 200L;
    /** Slowest allowed refresh interval while the scene reads as still. */
    private static final long STATIC_FALLBACK_MS = 500L;

    private static boolean primed;
    private static float lastYaw;
    private static float lastPitch;
    private static long lastMotionMs;
    private static long lastFullMs = Long.MIN_VALUE;

    private BlurMotionGate() {
    }

    /**
     * Records one frame of scene activity. {@code chromeAnimating} covers the
     * panel open/close spring and any composer/layout change — anything that
     * moves the panel rect or its fade.
     */
    public static void noteFrame(long nowMs, float yaw, float pitch, boolean chromeAnimating) {
        float dYaw = Math.abs(wrapDegrees(yaw - lastYaw));
        float dPitch = Math.abs(pitch - lastPitch);
        boolean moved = chromeAnimating || !primed
                || dYaw > CAMERA_EPS_DEG || dPitch > CAMERA_EPS_DEG;
        primed = true;
        lastYaw = yaw;
        lastPitch = pitch;
        if (moved) {
            lastMotionMs = nowMs;
        }
    }

    /**
     * Whether the blur renderer's due slot may actually run the full refresh.
     * Returns true immediately when {@code force} is set (texture resize /
     * first frame); otherwise only on a due slot, either because the scene is
     * moving or because the static fallback interval has elapsed.
     */
    public static boolean allowFullRefresh(long nowMs, boolean slotDue, boolean force) {
        if (force) {
            lastFullMs = nowMs;
            return true;
        }
        if (!slotDue) {
            return false;
        }
        boolean moving = nowMs - lastMotionMs < STILL_HOLD_MS;
        logCadenceChange(!moving);
        if (moving) {
            lastFullMs = nowMs;
            return true;
        }
        if (lastFullMs == Long.MIN_VALUE || nowMs - lastFullMs >= STATIC_FALLBACK_MS) {
            lastFullMs = nowMs;
            return true;
        }
        return false;
    }

    /**
     * One INFO line per cadence transition (fast <-> fallback), so a dev log
     * shows the on-demand blur actually throttling. State-only logging: the
     * check runs per frame, the line does not.
     */
    private static void logCadenceChange(boolean still) {
        if (still != lastLoggedStill) {
            lastLoggedStill = still;
            AtomChat.LOGGER.info("AtomChat panel blur cadence: {}",
                    still ? "fallback (scene still, refresh at most every 500ms)"
                          : "fast (motion, refresh every 2 frames)");
        }
    }

    private static boolean lastLoggedStill;

    /** Wraps to [-180, 180) so a yaw crossing the ±180 seam reads as small motion. */
    private static float wrapDegrees(float delta) {
        return ((delta + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F;
    }

    /** Test-only state reset. */
    static void resetForTest() {
        primed = false;
        lastYaw = 0.0F;
        lastPitch = 0.0F;
        lastMotionMs = 0L;
        lastFullMs = Long.MIN_VALUE;
        lastLoggedStill = false;
    }
}
