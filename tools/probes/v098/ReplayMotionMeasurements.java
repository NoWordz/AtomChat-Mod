package bench;

import atomchatprobe.ReplayMotionRoute;
import com.atom.chat.render.CardBackdrop;
import com.atom.chat.render.ScrollEdgeFade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Durations collected only by the isolated, opt-in CCB replay probe. */
public final class ReplayMotionMeasurements {
    private ReplayMotionMeasurements() {}

    @Mixin(targets = "com.atom.chat.nav.PageTransitionComposite", remap = false)
    public static class Page {
        @Unique private long prepareStarted;

        @Inject(method = "beginFrame(J)V", at = @At("HEAD"))
        private void prepareStart(CallbackInfo info) {
            prepareStarted = System.nanoTime();
        }

        @Inject(method = "beginFrame(J)V", at = @At("RETURN"))
        private void prepareEnd(CallbackInfo info) {
            ReplayMotionRoute.call("PageTransitionComposite.beginFrame", prepareStarted);
        }

        @Unique private long compactStarted;

        @Inject(method = "compact", at = @At("HEAD"))
        private void compactStart(CallbackInfo info) {
            compactStarted = System.nanoTime();
        }

        @Inject(method = "compact", at = @At("RETURN"))
        private void compactEnd(CallbackInfo info) {
            ReplayMotionRoute.call("PageTransitionComposite.compact", compactStarted);
        }

        @Unique private long bodyStarted;

        @Inject(method = "drawArrivingFrame(Lio/github/humbleui/skija/Canvas;Lcom/atom/chat/ui/UiLayout;IIFFJZ)V", at = @At("HEAD"))
        private void bodyStart(CallbackInfo info) {
            bodyStarted = System.nanoTime();
        }

        @Inject(method = "drawArrivingFrame(Lio/github/humbleui/skija/Canvas;Lcom/atom/chat/ui/UiLayout;IIFFJZ)V", at = @At("RETURN"))
        private void bodyEnd(CallbackInfo info) {
            ReplayMotionRoute.call("PageTransitionComposite.drawArrivingFrame", bodyStarted);
        }

        @Unique private long pairStarted;

        @Inject(method = "drawTransition", at = @At("HEAD"))
        private void pairStart(CallbackInfo info) {
            pairStarted = System.nanoTime();
        }

        @Inject(method = "drawTransition", at = @At("RETURN"))
        private void pairEnd(CallbackInfo info) {
            ReplayMotionRoute.call("PageTransitionComposite.drawTransition", pairStarted);
        }

        @Unique private long layerStarted;

        @Inject(method = "drawLayer", at = @At("HEAD"))
        private void layerStart(CallbackInfo info) {
            layerStarted = System.nanoTime();
        }

        @Inject(method = "drawLayer", at = @At("RETURN"))
        private void layerEnd(CallbackInfo info) {
            ReplayMotionRoute.call("PageTransitionComposite.drawLayer", layerStarted);
        }

        @Unique private long freezeStarted;

        @Inject(method = "freezeLastFrame", at = @At("HEAD"))
        private void freezeStart(CallbackInfo info) {
            freezeStarted = System.nanoTime();
        }

        @Inject(method = "freezeLastFrame", at = @At("RETURN"))
        private void freezeEnd(CallbackInfo info) {
            ReplayMotionRoute.call("PageTransitionComposite.freezeLastFrame", freezeStarted);
        }
    }

    @Mixin(targets = "com.atom.chat.render.ScrollEdgeFade", remap = false)
    public static class Fades {
        @Unique private static long clipStarted;

        @Inject(method = "clip", at = @At("HEAD"))
        private static void clipStart(CallbackInfoReturnable<ScrollEdgeFade> info) {
            clipStarted = System.nanoTime();
        }

        @Inject(method = "clip", at = @At("RETURN"))
        private static void clipEnd(CallbackInfoReturnable<ScrollEdgeFade> info) {
            ReplayMotionRoute.call("ScrollEdgeFade.clip", clipStarted);
        }

        @Unique private long closeStarted;

        @Inject(method = "close", at = @At("HEAD"))
        private void closeStart(CallbackInfo info) {
            closeStarted = System.nanoTime();
        }

        @Inject(method = "close", at = @At("RETURN"))
        private void closeEnd(CallbackInfo info) {
            ReplayMotionRoute.call("ScrollEdgeFade.close", closeStarted);
        }
    }

    @Mixin(targets = "com.atom.chat.render.CardBackdrop", remap = false)
    public static class Glass {
        @Unique private static long maskStarted;

        @Inject(method = "applyMasks", at = @At("HEAD"))
        private static void maskStart(CallbackInfo info) {
            maskStarted = System.nanoTime();
        }

        @Inject(method = "applyMasks", at = @At("RETURN"))
        private static void maskEnd(CallbackInfo info) {
            ReplayMotionRoute.call("CardBackdrop.applyMasks", maskStarted);
        }

        @Unique private static long beginStarted;

        @Inject(method = "begin(Lio/github/humbleui/skija/Surface;Lio/github/humbleui/skija/Canvas;Lcom/atom/chat/ui/UiLayout$Rect;Lio/github/humbleui/skija/Image;Lio/github/humbleui/skija/Image;Lcom/atom/chat/config/AtomChatConfig;Z)Lcom/atom/chat/render/CardBackdrop;", at = @At("HEAD"))
        private static void beginStart(CallbackInfoReturnable<CardBackdrop> info) {
            beginStarted = System.nanoTime();
        }

        @Inject(method = "begin(Lio/github/humbleui/skija/Surface;Lio/github/humbleui/skija/Canvas;Lcom/atom/chat/ui/UiLayout$Rect;Lio/github/humbleui/skija/Image;Lio/github/humbleui/skija/Image;Lcom/atom/chat/config/AtomChatConfig;Z)Lcom/atom/chat/render/CardBackdrop;", at = @At("RETURN"))
        private static void beginEnd(CallbackInfoReturnable<CardBackdrop> info) {
            ReplayMotionRoute.call("CardBackdrop.begin", beginStarted);
        }
    }

    @Mixin(targets = "com.atom.chat.ui.EmojiPanel", remap = false)
    public static class Stickers {
        @Unique private long refreshStarted;

        @Inject(method = "refreshEmotes", at = @At("HEAD"))
        private void refreshStart(CallbackInfo info) {
            refreshStarted = System.nanoTime();
        }

        @Inject(method = "refreshEmotes", at = @At("RETURN"))
        private void refreshEnd(CallbackInfo info) {
            ReplayMotionRoute.call("EmojiPanel.refreshEmotes", refreshStarted);
        }

        @Unique private long drawStarted;

        @Inject(method = "render", at = @At("HEAD"))
        private void drawStart(CallbackInfo info) {
            drawStarted = System.nanoTime();
        }

        @Inject(method = "render", at = @At("RETURN"))
        private void drawEnd(CallbackInfo info) {
            ReplayMotionRoute.call("EmojiPanel.render", drawStarted);
        }
    }

    @Mixin(targets = "com.atom.chat.wallpaper.WallpaperStore", remap = false)
    public static class Wallpaper {
        @Unique private static long replaceStarted;

        @Inject(method = "replace", at = @At("HEAD"))
        private static void replaceStart(CallbackInfoReturnable<Object> info) {
            replaceStarted = System.nanoTime();
        }

        @Inject(method = "replace", at = @At("RETURN"))
        private static void replaceEnd(CallbackInfoReturnable<Object> info) {
            ReplayMotionRoute.call("WallpaperStore.replace", replaceStarted);
        }
    }

    @Mixin(targets = "com.atom.chat.audio.FeedbackSoundController", remap = false)
    public static class Sound {
        @Unique private static long flushStarted;

        @Inject(method = "endAction", at = @At("HEAD"))
        private static void flushStart(CallbackInfo info) {
            flushStarted = System.nanoTime();
        }

        @Inject(method = "endAction", at = @At("RETURN"))
        private static void flushEnd(CallbackInfo info) {
            ReplayMotionRoute.call("FeedbackSoundController.endAction", flushStarted);
        }
    }
}
