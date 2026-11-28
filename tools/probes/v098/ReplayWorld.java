package atomchatprobe;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_405;
import net.minecraft.class_4185;

public final class ReplayWorld {
    private ReplayWorld() {}

    public static void confirmCopiedWorld(class_310 client) {
        if (!(client.field_1755 instanceof class_405 confirmation)) return;
        String label = class_2561.method_43471("selectWorld.backupJoinSkipButton").getString();
        for (var widget : confirmation.method_25396()) {
            if (widget instanceof class_4185 button && button.method_25369().getString().equals(label)) {
                System.out.println("ATOMCHAT_MOTION_COPIED_WORLD_CONTINUE");
                button.method_25306();
                return;
            }
        }
    }

    static void turnOffShaders() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> config = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object settings = api.getMethod("getConfig").invoke(instance);
            if (!Boolean.TRUE.equals(config.getMethod("areShadersEnabled").invoke(settings))) {
                throw new IllegalStateException("The Iris toggle scenario must start with shaders enabled");
            }
            System.out.println("ATOMCHAT_MOTION_IRIS_DISABLE");
            config.getMethod("setShadersEnabledAndApply", boolean.class).invoke(settings, false);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot bind the isolated Iris replay", failure);
        }
    }

    public static void controlFrame(long elapsed, long now, long previous) {
        var frame = new ControlFrame();
        frame.scene = "closed";
        frame.sceneNanos = elapsed;
        frame.endedAtNanos = now;
        frame.elapsedNanos = now - previous;
        frame.commit();
    }

    @Name("bench.AtomChatReplayFrameInterval")
    @StackTrace(false)
    static final class ControlFrame extends Event {
        String scene;
        long sceneNanos, endedAtNanos, elapsedNanos;
    }
}
