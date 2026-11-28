package atomchatprobe;

import com.atom.chat.card.BuiltInCardAdapter;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.diagnostics.FrameProfile;
import com.atom.chat.diagnostics.GlGpuTimer;
import com.atom.chat.diagnostics.GpuTimerQuery;
import com.atom.chat.nav.AppPage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.client.gui.screen.AtomChatScreen;
import java.util.concurrent.CompletableFuture;

/** Intermediary-named driver for the isolated Fabric 1.21.1 CCB replay only. */
public final class ReplayActions {
    private static final boolean ITEM_CARD = Boolean.getBoolean("atomchat.replay.itemCard");
    private static final boolean CAPTURE_CHECK = Boolean.getBoolean("atomchat.replay.captureCheck");
    private static final boolean RELOAD_CHECK = Boolean.getBoolean("atomchat.replay.reloadCheck");
    private static final boolean ENDURANCE = Boolean.getBoolean("atomchat.replay.endurance");
    private static final boolean STEADY_SETTINGS = Boolean.getBoolean("atomchat.replay.steadySettings");
    private static final int HISTORY_COUNT = Integer.getInteger("atomchat.replay.historyCount", 0);
    private static final String ADDON_SETTINGS = System.getProperty("atomchat.replay.addonSettings", "");
    private static final long NAVIGATION_INTERVAL_NANOS = Boolean.getBoolean("atomchat.replay.interruptNavigation")
            ? 80_000_000L : 650_000_000L;
    private static String scene = "startup";
    private static long sceneStart, lastAction, actionCount, previousFrame;
    private static long openingStarted;
    private static int scrollDirection, tabs;
    private static boolean hudSeeded;
    private static boolean itemCardSeeded;
    private static boolean captureChecked;
    private static CompletableFuture<Void> pendingReload;
    private static int reloads;
    private static boolean addonSettingsChecked;
    private static boolean shadersTurnedOff;
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
        if (ReplayMotionRoute.ENABLED) ReplayMotionRoute.reset(name);
    }

    public static long sceneDuration(long normal) {
        if (ENDURANCE && scene.equals("appearance")) return 540_000_000_000L;
        if (RELOAD_CHECK && scene.equals("appearance")) return 45_000_000_000L;
        if (STEADY_SETTINGS && scene.equals("appearance")) return 27_000_000_000L;
        return ReplayMatrix.ENABLED && (scene.equals("media") || scene.equals("text"))
                ? (ReplayMatrix.STEPS + 1L) * ReplayMatrix.STEP_NANOS : normal;
    }

    public static void resetChatForScene() {
        if (HISTORY_COUNT > 0 && (scene.equals("closed") || scene.equals("appearance"))) return;
        ChatStore.reset();
    }

    public static void switchReplayRoot(AtomChatScreen screen, AppPage page) {
        if (HISTORY_COUNT == 0 || !scene.equals("appearance")) screen.switchRoot(page);
    }

    public static void openSettingsOrHistory(AtomChatScreen screen, SettingsSection section) {
        if (HISTORY_COUNT == 0 || !scene.equals("appearance")) {
            screen.openSettingsSection(section);
            return;
        }
        long loaded = ChatStore.get().rawSnapshot().stream()
                .filter(row -> row.getContentText().startsWith("History replay ")).count();
        if (loaded != HISTORY_COUNT) {
            throw new IllegalStateException("Expected " + HISTORY_COUNT + " saved rows on the first open, found " + loaded);
        }
        screen.openWorldChat();
        System.out.println("V098_REPLAY_HISTORY_FIRST_OPEN " + loaded);
    }

    public static AppPage expectedAppearancePage() {
        return HISTORY_COUNT > 0 ? AppPage.WORLD_CHAT : AppPage.SETTINGS_SECTION;
    }

    public static AppPage expectedPublicPage() {
        return ReplayMotionRoute.ENABLED && ReplayMotionRoute.showingList()
                ? AppPage.CHAT_LIST : AppPage.WORLD_CHAT;
    }

    public static void startOpenStage() {
        openingStarted = System.nanoTime();
    }

    public static void beginPanelConstructor() {
        openingStarted = sceneStart;
        endOpenStage("setupBeforePanel");
        startOpenStage();
    }

    public static void endOpenStage(String stage) {
        long now = System.nanoTime();
        var event = new OpenStage();
        event.scene = scene;
        event.stage = stage;
        event.elapsedNanos = now - openingStarted;
        event.commit();
    }

    @Name("bench.AtomChatReplayOpenStage")
    @StackTrace(false)
    public static final class OpenStage extends Event {
        public String scene, stage;
        public long elapsedNanos;
    }

    public static void tick(class_310 client) {
        long now = System.nanoTime();
        long elapsed = now - sceneStart;
        if (previousFrame != 0) {
            var interval = new FrameInterval();
            String matrixScene = ReplayMatrix.key;
            interval.scene = matrixScene == null ? scene : matrixScene;
            interval.sceneNanos = now - (matrixScene == null ? sceneStart : ReplayMatrix.started);
            interval.elapsedNanos = now - previousFrame;
            interval.endedAtNanos = now;
            interval.commit();
        }
        previousFrame = now;
        if (ReplayMotionRoute.ENABLED && Boolean.getBoolean("atomchat.replay.copiedWorld")) {
            ReplayWorld.confirmCopiedWorld(client);
        }
        if (!shadersTurnedOff && scene.equals("appearance") && elapsed >= 3_000_000_000L
                && Boolean.getBoolean("atomchat.replay.toggleShaders")) {
            shadersTurnedOff = true;
            ReplayWorld.turnOffShaders();
        }
        ReplayMatrix.tick(client, scene, elapsed, now);
        if (reloadInProgress(client, elapsed)) return;
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
        if (ReplayMotionRoute.ENABLED && ReplayMotionRoute.tick(client, screen, scene, now)) return;
        if (!ADDON_SETTINGS.isEmpty() && !addonSettingsChecked && scene.equals("appearance")) {
            checkAddonSettings(screen);
            addonSettingsChecked = true;
        }
        if (CAPTURE_CHECK && !captureChecked && scene.equals("appearance")) {
            var chat = client.field_1705.method_1743();
            chat.method_1812(class_2561.method_43470("Preview diagnostic before capture [11, 22, 33]"));
            if (!screen.method_25404(295, 0, 2)) throw new IllegalStateException("capture hotkey unavailable");
            chat.method_1812(class_2561.method_43470("Preview diagnostic during capture [44, 55, 66]"));
            if (!screen.method_25404(295, 0, 2)) throw new IllegalStateException("capture stop hotkey unavailable");
            chat.method_1812(class_2561.method_43470("Preview diagnostic after capture [77, 88, 99]"));
            captureChecked = true;
        }
        if (ITEM_CARD && scene.equals("text")) {
            if (!itemCardSeeded) {
                var card = BuiltInCardAdapter.fromItemStack(new class_1799(class_1802.field_8378), "Replay");
                ChatStore.get().add(new ChatMessage(class_2561.method_43470(card.fallbackPlainText()), false)
                        .withCard(card));
                itemCardSeeded = true;
                actionCount++;
            }
            return;
        }
        if (scene.equals("text") && now - lastAction >= 75_000_000L) {
            // The game coordinates are scaled to match the pointer entry point.
            var window = client.method_22683();
            double x = window.method_4486() * .25, y = window.method_4502() * .52;
            double amount = (scrollDirection++ / 12 % 2) == 0 ? 4 : -4;
            if (screen.method_25401(x, y, 0, amount)) actionCount++;
            lastAction = now;
        } else if (!STEADY_SETTINGS && HISTORY_COUNT == 0 && scene.equals("appearance") && elapsed >= 4_500_000_000L
                && now - lastAction >= NAVIGATION_INTERVAL_NANOS) {
            screen.switchRoot(AppPage.SETTINGS);
            screen.openSettingsSection(tabs++ % 2 == 0 ? SettingsSection.ABOUT : SettingsSection.APPEARANCE);
            actionCount++;
            lastAction = now;
        }
    }

    private static boolean reloadInProgress(class_310 client, long elapsed) {
        if (!RELOAD_CHECK || !scene.equals("appearance")) return false;
        if (pendingReload != null) {
            if (!pendingReload.isDone()) return true;
            pendingReload.join();
            pendingReload = null;
            if (!(client.field_1755 instanceof AtomChatScreen)) {
                throw new IllegalStateException("AtomChat screen was lost during resource reload");
            }
            System.out.println("V098_REPLAY_RESOURCE_RELOAD_COMPLETE " + ++reloads);
        }
        long nextReloadSeconds = ENDURANCE ? 45L + 195L * reloads : 2L + 12L * reloads;
        if (reloads < 3 && elapsed >= nextReloadSeconds * 1_000_000_000L) {
            pendingReload = client.method_1521();
            return true;
        }
        return false;
    }

    private static void checkAddonSettings(AtomChatScreen screen) {
        try {
            var field = AtomChatScreen.class.getDeclaredField("settingsSectionPage");
            field.setAccessible(true);
            var page = (SettingsSectionPage) field.get(screen);
            var rows = page.rows(SettingsSection.APPEARANCE);
            if (rows.stream().anyMatch(row -> "atomchat.settings.appearance.parts.group".equals(row.labelKey())
                    || row.actionId() != null && row.actionId().startsWith("appearance_part_"))) {
                throw new IllegalStateException("AtomChat supplied an add-on picker");
            }
            boolean groupExpected = ADDON_SETTINGS.equals("present");
            for (String addon : new String[]{"atomchat_harbor", "atomchat_signal"}) {
                boolean actual = rows.stream().anyMatch(row -> (addon + ".settings.appearance").equals(row.labelKey()));
                if (actual != groupExpected) throw new IllegalStateException("Incorrect group for " + addon);
            }
            if (groupExpected) {
                checkAddonToggle(page, "atomchat_signal:apply_bubble");
                checkAddonToggle(page, "atomchat_harbor:apply_avatar");
                AtomChatConfig.reload();
                System.out.println("V098_REPLAY_ADDON_TOGGLES_COMPLETE");
            }
            var config = AtomChatConfig.get();
            if (!"atomchat_signal:capsule".equals(config.bubbleAppearanceId)
                    || !"atomchat_harbor:tide_ring".equals(config.avatarFrameAppearanceId)) {
                throw new IllegalStateException("The saved appearance was cleared");
            }
            System.out.println("V098_REPLAY_ADDON_SETTINGS_COMPLETE " + ADDON_SETTINGS);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void checkAddonToggle(SettingsSectionPage page, String id) {
        var rows = page.rows(SettingsSection.APPEARANCE);
        var row = rows.stream().filter(candidate -> candidate.item() != null && id.equals(candidate.item().id()))
                .findFirst().orElseThrow();
        var hit = new SettingsSectionPage.RowHit(row, rows.indexOf(row), 0, 0, 10, 10, 0);
        if (!row.item().value() || !page.perform(hit) || row.item().value()) {
            throw new IllegalStateException("Could not turn off the add-on setting " + id);
        }
        if (!page.perform(hit) || !row.item().value()) {
            throw new IllegalStateException("Could not turn on the add-on setting " + id);
        }
    }

    @Name("bench.AtomChatReplayFrameInterval")
    @StackTrace(false)
    public static final class FrameInterval extends Event {
        public String scene;
        public long sceneNanos, elapsedNanos, endedAtNanos;
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
