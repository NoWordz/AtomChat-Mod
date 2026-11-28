package bench;

import atomchatprobe.ReplayActions;
import com.atom.chat.diagnostics.FrameProfile;
import com.atom.chat.diagnostics.GpuTimerQuery;
import com.atom.chat.render.ChatHudRenderer;
import java.util.List;
import net.minecraft.client.gui.screen.AtomChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class ReplayMeasurements {
    private ReplayMeasurements() {}

    @Mixin(value = FrameProfile.class, remap = false)
    public static class Panel {
        @Inject(method = "frame(JJJ)Z", at = @At("HEAD"))
        private static void record(long started, long before, long id, CallbackInfoReturnable<Boolean> result) {
            if (started != 0 && id != 0) ReplayActions.cpu("panel", id, started, before, true);
        }
    }

    @Mixin(value = AtomChatScreen.class, remap = false)
    public static class PanelScreen {
        @Unique private static final boolean MEASURE = Boolean.getBoolean("atomchat.replay.measurePanel");
        @Unique private long replayStart, replayAllocated, replayId;

        @Inject(method = "method_25394", at = @At("HEAD"))
        private void begin(CallbackInfo result) {
            if (!MEASURE) return;
            replayAllocated = FrameProfile.allocatedBytes();
            replayStart = System.nanoTime();
        }

        @Inject(method = "method_25394", at = @At("RETURN"))
        private void end(CallbackInfo result) {
            if (MEASURE) ReplayActions.cpu("panel_screen", ++replayId, replayStart, replayAllocated, true);
        }
    }

    @Mixin(value = GpuTimerQuery.class, remap = false)
    public static class Queries {
        @Inject(method = "poll", at = @At("RETURN"))
        private void record(CallbackInfoReturnable<List<GpuTimerQuery.Sample>> result) {
            String scope = (Object) this == ReplayActions.HUD_GPU ? "chat_hud" : "panel";
            for (var sample : result.getReturnValue()) ReplayActions.gpu(scope, sample);
        }
    }

    @Mixin(value = ChatHudRenderer.class, remap = false)
    public static class ChatHud {
        @Unique private long replayStart, replayAllocated, replayId;

        @Inject(method = "render", at = @At("HEAD"))
        private void begin(CallbackInfoReturnable<Boolean> result) {
            ReplayActions.HUD_GPU.poll();
            replayId = ++ReplayActions.hudId;
            ReplayActions.HUD_GPU.beginFrame(replayId);
            replayAllocated = FrameProfile.allocatedBytes();
            replayStart = System.nanoTime();
        }

        @Inject(method = "render", at = @At("RETURN"))
        private void end(CallbackInfoReturnable<Boolean> result) {
            ReplayActions.cpu("chat_hud", replayId, replayStart, replayAllocated, result.getReturnValue());
            ReplayActions.HUD_GPU.endFrame();
        }
    }
}
