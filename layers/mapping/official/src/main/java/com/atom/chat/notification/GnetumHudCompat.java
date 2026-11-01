package com.atom.chat.notification;

import com.atom.chat.AtomChat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Gnetum (me.decce.gnetum, "distributes HUD updates over multiple frames")
 * compat for the HUD notification banner.
 *
 * <p>What Gnetum does to the HUD pipeline (3.3.6, NeoForge 1.21.1): a mixin
 * wraps {@code GameRenderer.render}'s {@code Gui.render} call, cancels the
 * vanilla {@code GuiLayerManager.render} inside it, and instead renders the
 * HUD layers into its own framebuffer across multiple passes — one pass per
 * frame, each layer assigned a pass ({@code GameRendererMixin.gnetum$wrapGuiRender},
 * {@code GuiMixin.gnetum$render}). The finished cache is composited over the
 * main framebuffer afterwards ({@code FramebufferManager.blit}).</p>
 *
 * <p>Why that flickers the banner: inside a cached pass, Gnetum's framebuffer
 * is the bound target, but our layer's two halves go to different targets —
 * the raw-GL frosted backdrop is drawn into Gnetum's cache framebuffer
 * (whatever is currently bound), while the Skia card is flushed into the main
 * framebuffer (the AtomLib bridge pins its backend render target to
 * {@code Minecraft.getMainRenderTarget()}). The card therefore only lands on
 * the frame our pass runs (every Nth frame) and is wiped by the next frame's
 * world clear, while the stale backdrop keeps being composited — the banner
 * area alternates between "card" and "frosted rect". Sampling the main
 * framebuffer for the blur is also wrong there: the vanilla HUD below us
 * lives in Gnetum's framebuffer during a pass.</p>
 *
 * <p>The fix: mark our layer as uncached in Gnetum's own per-element config
 * model — the same "off" toggle its Elements screen exposes. Uncached layers
 * are skipped by the cached passes and rendered instead in the uncached phase
 * after {@code FramebufferManager.blit()}: every frame, main framebuffer
 * bound, world and cached HUD already composited — exactly the pre-Gnetum
 * timing our raw-GL pre-pass was built for. The pin is refreshed every frame
 * (one reflective map hit) so config reloads cannot resurrect the cached
 * path.</p>
 *
 * <p>Pure reflection, no compile dependency. Gnetum 3.3.6 is NeoForge-only,
 * but this probe is loader-agnostic: on Fabric (or any future version with
 * different internals) it resolves to a permanent no-op and the layer keeps
 * its pre-compat behaviour.</p>
 */
public final class GnetumHudCompat {
    /** Must stay in sync with the id each loader's AtomChatClient registers. */
    public static final String LAYER_ID = "atomchat:notifications";

    private static boolean resolved;
    private static boolean available;
    private static Method getCacheSetting;
    private static Field enabledField;
    private static Field valueField;
    private static Object offValue;

    private GnetumHudCompat() { }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!com.atom.chat.platform.Platform.isModLoaded("gnetum")) {
            return;
        }
        try {
            Class<?> gnetum = Class.forName("me.decce.gnetum.Gnetum");
            getCacheSetting = gnetum.getMethod("getCacheSetting", String.class);
            enabledField = Class.forName("me.decce.gnetum.CacheSetting").getField("enabled");
            valueField = Class.forName("me.decce.gnetum.util.TriStateBoolean").getField("value");
            offValue = Enum.valueOf(Class.forName("me.decce.gnetum.util.AnyBooleanValue").asSubclass(Enum.class), "OFF");
            available = true;
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("compat",
                    "Gnetum detected: the HUD notification layer is pinned to Gnetum's uncached path "
                            + "(inside Gnetum's cached pipeline the raw-GL frosted backdrop and the Skia card "
                            + "land in different framebuffers, which flickers the banner)"));
        } catch (Throwable t) {
            AtomChat.LOGGER.debug(com.atom.chat.diagnostics.AtomChatLog.message("compat",
                    "Gnetum compat unavailable: {}"), t.toString());
        }
    }

    /**
     * Pins the HUD layer to Gnetum's uncached path. No-op (and a cheap early
     * return) wherever Gnetum is absent; safe to call every frame.
     */
    public static void keepOutOfHudCache() {
        resolve();
        if (!available) {
            return;
        }
        try {
            Object setting = getCacheSetting.invoke(null, LAYER_ID);
            if (setting == null) {
                return;
            }
            Object triState = enabledField.get(setting);
            if (triState != null) {
                valueField.set(triState, offValue);
            }
        } catch (Throwable t) {
            // A Gnetum build with different internals: fail open to the
            // pre-compat behaviour rather than throw from inside a render pass.
            available = false;
            AtomChat.LOGGER.debug(com.atom.chat.diagnostics.AtomChatLog.message("compat",
                    "Gnetum compat disabled after failure: {}"), t.toString());
        }
    }
}
