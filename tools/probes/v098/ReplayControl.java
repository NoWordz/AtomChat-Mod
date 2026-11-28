package bench;

import atomchatprobe.ReplayWorld;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measures the same copied world with the AtomChat JAR absent. */
@Mixin(class_310.class)
public class ReplayControl {
    @Unique private long controlReady, controlWorld, controlPrevious, controlFrames;
    @Unique private boolean controlRequested, controlMeasured;

    @Inject(method = "method_1523(Z)V", at = @At("RETURN"), remap = false)
    private void measureControl(CallbackInfo info) {
        class_310 client = (class_310) (Object) this;
        long now = System.nanoTime();
        if (client.field_1687 == null || client.field_1724 == null || client.method_1576() == null) {
            if (!client.method_53466()) return;
            if (controlReady == 0) {
                if (FabricLoader.getInstance().isModLoaded("atomchat")) {
                    throw new IllegalStateException("AtomChat was loaded in the no-mod control");
                }
                controlReady = now;
            }
            if (controlRequested) ReplayWorld.confirmCopiedWorld(client);
            else if (now - controlReady >= 2_000_000_000L) {
                controlRequested = true;
                client.method_41735().method_57784("route-world", () -> {
                    throw new IllegalStateException("Control world did not load");
                });
            }
            if (now - controlReady >= 65_000_000_000L) {
                throw new IllegalStateException("Control world did not become ready");
            }
            return;
        }
        if (controlWorld == 0) controlWorld = now;
        if (!controlMeasured) {
            if (now - controlWorld < 12_000_000_000L) return;
            client.method_1507(null);
            controlMeasured = true;
            controlWorld = now;
            controlPrevious = now;
            return;
        }

        long elapsed = now - controlWorld;
        if (elapsed >= 12_000_000_000L) {
            if (controlFrames == 0) throw new IllegalStateException("No closed-world control frames were recorded");
            var window = client.method_22683();
            System.out.println("ATOMCHAT_CONTROL_COMPLETE frames=" + controlFrames
                    + " fb=" + window.method_4489() + "x" + window.method_4506());
            client.method_1592();
        } else if (client.field_1755 == null) {
            ReplayWorld.controlFrame(elapsed, now, controlPrevious);
            controlFrames++;
        }
        controlPrevious = now;
    }
}
