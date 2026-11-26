package atomchatprobe;

import com.atom.chat.diagnostics.FrameProfile;
import com.atom.chat.diagnostics.GlGpuTimer;
import com.atom.chat.diagnostics.GpuTimerQuery;
import com.atom.chat.nav.AppPage;
import com.atom.chat.settings.SettingsSection;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.client.gui.screen.AtomChatScreen;

/** Intermediary-named driver for the isolated Fabric 1.21.1 CCB replay only. */
public final class ReplayActions {
    private static String scene = "startup";
    private static long sceneStart, lastAction, actionCount, previousFrame;
    private static int scrollDirection, tabs;
    private static boolean hudSeeded;
    public static final GpuTimerQuery HUD_GPU = new GpuTimerQuery(new GlGpuTimer(), 4);
    public static long hudId;

    private ReplayActions() {}

    public static void scene(String name) {
        System.out.printf("V098_REPLAY %s actions=%d -> %s%n", scene, actionCount, name);
        sceneStart = System.nanoTime();
        scene = name;
        lastAction = sceneStart;
        actionCount = 0;
        ReplayMatrix.reset();
    }

    public static long sceneDuration(long normal) {
        return ReplayMatrix.ENABLED && (scene.equals("media") || scene.equals("text"))
                ? (ReplayMatrix.STEPS + 1L) * ReplayMatrix.STEP_NANOS : normal;
    }

    public static void tick(class_310 client) {
        long now = System.nanoTime();
        long elapsed = now - sceneStart;
        if (ReplayMatrix.key != null && previousFrame != 0) {
            var interval = new FrameInterval();
            interval.scene = ReplayMatrix.key;
            interval.sceneNanos = now - ReplayMatrix.started;
            interval.elapsedNanos = now - previousFrame;
            interval.commit();
        }
        previousFrame = now;
        ReplayMatrix.tick(client, scene, elapsed, now);
        if (ReplayMatrix.ENABLED && client.field_1724 != null) {
            if (scene.equals("empty") && elapsed >= 6_000_000_000L) {
                client.field_1724.method_36456((float) (12 * Math.sin(elapsed / 1e9 * 2)));
            } else if (scene.equals("closed_end") && !hudSeeded) {
                client.field_1724.method_36456(0);
            }
        }
        if (elapsed < 1_500_000_000L) return;
        if (scene.equals("closed_end") && !hudSeeded && client.field_1724 != null) {
            hudSeeded = true;
            for (int row = 0; row < 8; row++) {
                client.field_1705.method_1743().method_1812(class_2561.method_43470(
                        "HUD fixture " + row + " — 静止与滚动 / text layout"));
                actionCount++;
            }
        }
        if (!(client.field_1755 instanceof AtomChatScreen screen)) return;
        if (scene.equals("text") && now - lastAction >= 75_000_000L) {
            // The game coordinates are scaled to match the pointer entry point.
            var window = client.method_22683();
            double x = window.method_4486() * .25, y = window.method_4502() * .52;
            double amount = (scrollDirection++ / 12 % 2) == 0 ? 4 : -4;
            if (screen.method_25401(x, y, 0, amount)) actionCount++;
            lastAction = now;
        } else if (scene.equals("appearance") && elapsed >= 4_500_000_000L
                && now - lastAction >= 650_000_000L) {
            screen.switchRoot(AppPage.SETTINGS);
            screen.openSettingsSection(tabs++ % 2 == 0 ? SettingsSection.ABOUT : SettingsSection.APPEARANCE);
            actionCount++;
            lastAction = now;
        }
    }

    @Name("bench.AtomChatReplayFrameInterval")
    @StackTrace(false)
    public static final class FrameInterval extends Event {
        public String scene;
        public long sceneNanos, elapsedNanos;
    }

    @Name("bench.AtomChatReplayCpu")
    @StackTrace(false)
    public static final class Cpu extends Event {
        public String scope, scene;
        public long frameId, elapsedNanos, allocatedBytes, sceneNanos;
        public boolean rendered;
    }

    @Name("bench.AtomChatReplayGpu")
    @StackTrace(false)
    public static final class Gpu extends Event {
        public String scope;
        public long frameId, elapsedNanos;
    }

    public static void cpu(String scope, long id, long started, long allocated, boolean rendered) {
        long now = System.nanoTime();
        var cpu = new Cpu();
        cpu.scope = scope;
        cpu.scene = ReplayMatrix.key == null ? scene : ReplayMatrix.key;
        cpu.frameId = id;
        cpu.elapsedNanos = now - started;
        cpu.allocatedBytes = allocated < 0 ? -1 : FrameProfile.allocatedBytes() - allocated;
        cpu.sceneNanos = now - (ReplayMatrix.key == null ? sceneStart : ReplayMatrix.started);
        cpu.rendered = rendered;
        cpu.commit();
    }

    public static void gpu(String scope, GpuTimerQuery.Sample sample) {
        var gpu = new Gpu();
        gpu.scope = scope;
        gpu.frameId = sample.frameId();
        gpu.elapsedNanos = sample.gpuNanos();
        gpu.commit();
    }
}
