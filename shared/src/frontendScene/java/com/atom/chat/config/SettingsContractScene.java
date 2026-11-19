package com.atom.chat.config;

import com.atom.chat.audio.FeedbackCue;
import com.atom.chat.audio.FeedbackSoundController;
import com.atom.chat.audio.FeedbackSoundPolicy;
import com.atom.chat.nav.PageTransitionComposite;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.frontend.checker.SceneCaptureIO;
import com.atom.lib.frontend.checker.ScenarioCheck.Phase;
import com.atom.lib.frontend.checker.ScenarioCheck.Sample;
import com.atom.lib.frontend.checker.SoundObservation;
import com.atom.lib.frontend.contract.UiSoundIntent;
import com.atom.lib.frontend.contract.UiSoundSink;
import com.atom.lib.frontend.probe.FixedHeaderClockAgent;
import com.google.gson.JsonParser;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Surface;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Software scenes over the real settings controllers and page composite. */
public final class SettingsContractScene {
    private static final String BUTTON = "atomchat:feedback_button";
    private static final String CATEGORY = "MASTER";
    private static final int WIDTH_WIDE = 420;
    private static final int WIDTH_NARROW = 280;
    private static final Set<Integer> HISTORY_EVENTS = Set.of(65, 69, 83, 90, 92, 96, 100, 103, 106, 124, 127);
    private static final Set<Integer> HOST_EVENTS = Set.of(66, 69, 79, 104, 106, 112);
    private final List<Sample> samples;
    private final List<SoundObservation> sounds = new ArrayList<>();
    private final String page;
    private final String condition;
    private final boolean interaction;
    private final boolean hosting;
    private final boolean light;
    private SettingsSectionPage history;
    private ServerConfigPage server;
    private PageTransitionComposite composite;
    private UiLayout layout;
    private boolean strict;
    private boolean buttonResolved;
    private int tick;
    private int daysBefore;
    private boolean toggleBefore;
    private String draftBefore;
    private String focusBefore;
    private int actionCount;

    private SettingsContractScene(List<Sample> samples, String page, String condition, boolean interaction, boolean light) {
        this.samples = samples;
        this.page = page;
        this.condition = condition;
        this.interaction = interaction;
        this.hosting = page.equals("hosting");
        this.light = light;
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3 && !(arguments.length == 4 && arguments[3].equals("--frozen-original"))) {
            throw new IllegalArgumentException("Expected output, invocation and design identity");
        }
        boolean frozen = arguments.length == 4;
        if (frozen && !arguments[1].equals("frozen-original")) throw new IllegalArgumentException("Invalid reference invocation");
        System.setProperty("atomchat.fcl.scene", "true");
        Path output = Path.of(arguments[0]).toAbsolutePath();
        List<Sample> samples = new ArrayList<>();
        for (String page : List.of("history", "hosting")) {
            for (boolean light : List.of(false, true)) {
                int width = light ? WIDTH_NARROW : WIDTH_WIDE;
                String locale = light ? "en_us" : "zh_cn";
                String condition = locale + "-" + width + (light ? "-light" : "-dark");
                for (boolean interaction : List.of(false, true)) {
                    String run = page + "-" + condition + (interaction ? "-input" : "-navigation");
                    var scene = new SettingsContractScene(samples, page, condition, interaction, light);
                    SettingsTrialReplay.run(output.resolveSibling("scene-sandbox").resolve(run), page, locale,
                            width, light, interaction, !frozen, scene);
                }
            }
        }
        FixedHeaderClockAgent.requireSceneClocks();
        SceneCaptureIO.write(output, new SceneCaptureIO.Capture(arguments[1], arguments[2], samples));
        System.out.println("ATOMCHAT_SETTINGS_SCENES samples=" + samples.size() + " frozen=" + frozen);
    }

    void setup(Class<?> paneType, Object pane, boolean strict) throws Exception {
        this.strict = strict;
        history = (SettingsSectionPage) SettingsTrialReplay.field(paneType, pane, "settings");
        server = (ServerConfigPage) SettingsTrialReplay.field(paneType, pane, "server");
        layout = (UiLayout) SettingsTrialReplay.field(paneType, pane, "layout");
        composite = (PageTransitionComposite) SettingsTrialReplay.field(paneType, pane, "composite");
        if (!hosting) history.setActionHandler(action -> {
            if (!action.equals("history_clear")) throw new AssertionError("Unexpected history action " + action);
            actionCount++;
        });
        var config = AtomChatConfig.get();
        config.feedbackSoundEnabled = true;
        config.feedbackButtonEnabled = true;
        config.feedbackVolume = 0.45f;
        buttonResolved = buttonAsset();
        // The headless process observes the accepted legacy cue before the real client sound manager.
        FeedbackSoundController.observeCuesForTest(cue -> sounds.add(new SoundObservation(cue.name(),
                BUTTON, CATEGORY, volume(), 1f, clock(tick))));
        if (strict) {
            Object controller = hosting ? server : history;
            var sink = controller.getClass().getDeclaredField("settingsSound");
            sink.setAccessible(true);
            sink.set(controller, new UiSoundSink() {
                @Override public boolean supports(String channel) { return CATEGORY.equals(channel); }
                @Override public boolean available(String resource) { return buttonResolved && BUTTON.equals(resource); }
                @Override public void play(UiSoundIntent intent) {
                    if (intent.frameMillis() != clock(tick) && intent.frameMillis() != clock(tick - 1)) {
                        throw new AssertionError("Cue escaped the active frame: " + intent);
                    }
                    sounds.add(new SoundObservation(intent.event(), intent.resource(), intent.channel(),
                            intent.volume(), intent.pitch(), clock(tick)));
                }
            });
        }
    }

    void beforeInput(int tick) throws Exception {
        this.tick = tick;
        sounds.clear();
        if (!interaction) return;
        if (tick == (hosting ? 111 : 95)) AtomChatConfig.get().feedbackButtonEnabled = false;
        if (hosting) {
            toggleBefore = server.draft().enabled("hostingEnabled");
            draftBefore = server.draft().text("maxFileKb");
            focusBefore = hostingFocus();
            if (tick == 86) {
                var viewport = ServerConfigLayout.of(layout).scrollViewport();
                server.scroll(viewport.x() + viewport.w() / 2, viewport.y() + viewport.h() / 2, 20);
            }
            if (tick == 104 || tick == 106 || tick == 112) clickHostingToggle(tick == 104 ? 1 : 0);
            if (tick == 69) server.character('8');
        } else {
            var config = AtomChatConfig.get();
            daysBefore = config.historyRetentionDays;
            toggleBefore = config.chatHistoryEnabled;
            if (tick == 90) {
                var miss = history.hit(-100, -100, layout, SettingsSection.CHAT, 0);
                if (miss != null) throw new AssertionError("Off-panel history pointer hit a row");
            }
            if (tick == 92 || tick == 96) {
                clickHistory("history");
            }
            if (tick == 103 || tick == 106) clickHistory("history_clear");
            if (tick == 104) history.confirmation().key(256);
            if (tick == 124) history.confirmation().key(258);
            if (tick == 127) history.confirmation().key(257);
        }
    }

    void afterInput(int tick) {
        if (!hosting && interaction && tick == 69 && !strict && history.confirmation().hasPendingAction()) {
            // The original Screen emits the legacy button cue when its selected row accepts the click.
            FeedbackSoundController.play(FeedbackCue.BUTTON);
        }
    }

    boolean extraSample(int tick) {
        return interaction && (hosting ? HOST_EVENTS : HISTORY_EVENTS).contains(tick);
    }

    void record(int tick, Surface surface) throws Exception {
        String recipe = "fieldGroup";
        String scenario = "fieldGroup";
        Phase phase = phase(tick);
        Map<String, Double> observed = new TreeMap<>();
        long millis = clock(tick);
        observed.put("transition.alpha", (double) composite.overlayAlpha(millis));
        observed.put("transition.x", (double) composite.overlaySlideDx(millis));
        observed.put("transition.scale", (double) composite.overlayZoomScale(millis));
        observed.put("page.variant", (double) (hosting ? server.bodyVariant() : history.activeChipIndex(SettingsSection.CHAT)));
        if (interaction && hosting) {
            var scroll = (ScrollController) SettingsTrialReplay.field(ServerConfigPage.class, server, "scroll");
            observed.put("scroll.y", (double) scroll.getScrollY());
            observed.put("scroll.target", (double) scroll.getTarget());
            var folds = (Map<?, ?>) SettingsTrialReplay.field(ServerConfigPage.class, server, "folds");
            observed.put("fold.progress", ((Number) folds.get("hosting")).doubleValue());
            if (HOST_EVENTS.contains(tick) || tick == 55 || tick == 58 || tick == 62) {
                scenario = "hostingEdit";
                if (tick >= 90) {
                    recipe = "toggle";
                    observed.put("actions.committed", changed(toggleBefore != server.draft().enabled("hostingEnabled")));
                    observed.put("toggle.value", changed(server.draft().enabled("hostingEnabled")));
                } else {
                    recipe = "number";
                    String text = server.draft().text("maxFileKb");
                    var values = server.draft().values();
                    observed.put("draft.length", (double) text.length());
                    observed.put("draft.number", text.isEmpty() ? -1d : Double.parseDouble(text));
                    observed.put("draft.valid", changed(server.draft().validation() == null));
                    observed.put("focus.active", changed(hostingFocus() != null));
                    observed.put("actions.committed", changed(tick == 66 && focusBefore != null && hostingFocus() == null
                            && values != null && Integer.toString(values.maxFileKb()).equals(text)));
                    if (tick == 69) observed.put("number.ignoredInput", changed(draftBefore.equals(text)));
                }
            }
        } else if (interaction) {
            var config = AtomChatConfig.get();
            if (tick >= 32) observed.put("fold.progress", (double) history.tutorialFolds().expansion()
                    .get("label:atomchat.settings.group.history.storage"));
            if (tick >= 90 && tick <= 100) {
                recipe = "historyToggle";
                scenario = "historyEdit";
                observed.put("actions.committed", changed(toggleBefore != config.chatHistoryEnabled));
                observed.put("toggle.value", changed(config.chatHistoryEnabled));
            } else if (tick == 55 || tick == 58 || tick == 62 || tick == 65) {
                recipe = "historyNumber";
                scenario = "historyEdit";
                observed.put("actions.committed", changed(daysBefore != config.historyRetentionDays));
                observed.put("history.days", (double) config.historyRetentionDays);
                observed.put("focus.active", changed(history.isEditingNumber()));
            } else if (tick >= 69) {
                recipe = "historyCleanup";
                scenario = "historyConfirm";
                observed.put("actions.committed", (double) actionCount);
                observed.put("modal.pending", changed(history.confirmation().hasPendingAction()));
                observed.put("modal.visible", changed(history.confirmation().isActive()));
                observed.put("modal.progress", ((Number) SettingsTrialReplay.field(
                        history.confirmation().getClass(), history.confirmation(), "progress")).doubleValue());
            }
        }
        Map<String, Boolean> states = Map.of("lightCard", light, "decorativeMotion", Animations.enabled(), "buttonFeedback", volume() > 0);
        long sequenceMillis = millis + (light ? 20_000 : 0) + (interaction ? 10_000 : 0);
        try (var image = surface.makeImageSnapshot(); var png = image.encodeToData(EncodedImageFormat.PNG)) {
            samples.add(new Sample(page + "/" + condition + "/" + (interaction ? "input" : "navigation") + "/" + tick,
                    page, recipe, scenario, sequenceMillis * 1_000_000, phase, true, states,
                    ImageIO.read(new ByteArrayInputStream(png.getBytes())), observed, sounds,
                    buttonResolved ? Set.of(BUTTON) : Set.of()));
        }
    }

    private Phase phase(int tick) {
        if (!interaction) {
            return switch (tick) {
                case 0, 35, 66 -> Phase.INITIAL;
                case 4, 12, 39, 47, 70, 78 -> Phase.INTERMEDIATE;
                default -> Phase.SETTLED;
            };
        }
        if (hosting ? tick == 69 || tick == 104 : tick == 58 || tick == 83 || tick == 90) return Phase.CANCELLED;
        return switch (tick) {
            case 32 -> Phase.INITIAL;
            case 35, 39, 48, 73, 82, 106, 124 -> Phase.INTERMEDIATE;
            case 41 -> Phase.REVERSED;
            default -> Phase.SETTLED;
        };
    }

    private void clickHistory(String id) throws Exception {
        var rows = history.rows(SettingsSection.CHAT);
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            if (!(id.equals(row.actionId()) || row.item() != null && id.equals(row.item().id()))) continue;
            Method rect = SettingsSectionPage.class.getDeclaredMethod("rowRect",
                    SettingsSection.class, List.class, int.class, float.class, UiLayout.class);
            rect.setAccessible(true);
            var bounds = (UiLayout.Rect) rect.invoke(history, SettingsSection.CHAT, rows, index, 0f, layout);
            var hit = history.hit(bounds.x() + 4, bounds.y() + bounds.h() / 2, layout, SettingsSection.CHAT, 0);
            if (hit != null && history.perform(hit) && !strict) FeedbackSoundController.play(FeedbackCue.BUTTON);
            return;
        }
        throw new AssertionError("History row absent: " + id);
    }

    private void clickHostingToggle(int button) throws Exception {
        var fixture = Class.forName("com.atom.chat.config.SharedSettingsFrontendTest");
        var row = fixture.getDeclaredMethod("rowBox", ServerConfigPage.class, UiLayout.class, String.class);
        row.setAccessible(true);
        var box = row.invoke(null, server, layout, "hostingEnabled");
        var bounds = (UiLayout.Rect) SettingsTrialReplay.field(box.getClass(), box, "rect");
        var scroll = (ScrollController) SettingsTrialReplay.field(ServerConfigPage.class, server, "scroll");
        float x = bounds.x() + bounds.w() / 2;
        float y = bounds.y() + bounds.h() / 2 - scroll.getScrollY();
        if (!ServerConfigLayout.of(layout).scrollViewport().contains(x, y)) {
            throw new AssertionError("Hosting toggle not visible at tick " + tick + " offset=" + scroll.getScrollY());
        }
        server.click(x, y, button);
        server.release();
    }

    private String hostingFocus() throws Exception {
        return (String) SettingsTrialReplay.field(ServerConfigPage.class, server, "focused");
    }

    private static boolean buttonAsset() throws Exception {
        try (var manifest = SettingsContractScene.class.getResourceAsStream("/assets/atomchat/sounds.json")) {
            if (manifest == null) return false;
            var event = JsonParser.parseReader(new InputStreamReader(manifest, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("feedback_button");
            if (event == null) return false;
            for (var variant : event.getAsJsonArray("sounds")) {
                String id = variant.isJsonPrimitive() ? variant.getAsString() : variant.getAsJsonObject().get("name").getAsString();
                String[] parts = id.split(":", 2);
                String file = parts.length == 1 ? "atomchat/sounds/" + id : parts[0] + "/sounds/" + parts[1];
                if (SettingsContractScene.class.getResource("/assets/" + file + ".ogg") == null) return false;
            }
            return !event.getAsJsonArray("sounds").isEmpty();
        }
    }

    private static float volume() {
        var config = AtomChatConfig.get();
        return FeedbackSoundPolicy.volume(config.feedbackSoundEnabled, config.feedbackButtonEnabled, config.feedbackVolume);
    }

    private static double changed(boolean changed) { return changed ? 1d : 0d; }
    private static long clock(int frame) { return 1000L + frame * 16; }
}
