package com.atom.chat.settings.contract;

import com.atom.chat.audio.FeedbackSoundPolicy;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.settings.SettingsNumberEditor;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.lib.controls.TextClipboard;
import com.atom.lib.design.atomchat.Design;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.frontend.ContractPage;
import com.atom.lib.frontend.StrictPageHost;
import com.atom.lib.frontend.StrictPageSession;
import com.atom.lib.frontend.contract.ContractDesign.Binding;
import com.atom.lib.frontend.contract.ContractRegistry;
import com.atom.lib.frontend.contract.UiSoundSink;
import com.atom.lib.geometry.Rect;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.layout.PresentationFrame;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Canvas;
import java.util.Map;
import java.util.function.Supplier;

/** Mounts the selected route's business fields with one appearance and preference snapshot per frame. */
public final class SettingsContractMount implements AutoCloseable {
    private static final String PROFILE = "atomchat-settings/1";
    private final ContractRegistry.PageHandle page;
    private final Supplier<? extends ContractPage> fields;
    private final StrictPageSession session;
    private final StrictPageSession.EmbeddedBody embedded;
    private long frameId;

    public static SettingsContractMount history(ContractRegistry registry, Supplier<HistoryFieldsPage> fields,
                                                UiSoundSink output) {
        return history(registry, fields, output, null);
    }

    public static SettingsContractMount history(ContractRegistry registry, Supplier<HistoryFieldsPage> fields,
                                                UiSoundSink output, SettingsNumberEditor editor) {
        boolean composed = editor != null;
        return new SettingsContractMount(registry.page("history"), fields, Map.of(
                binding(HistoryActionComponent.class), new HistoryActionComponent(composed),
                binding(HistoryRetentionComponent.class), new HistoryRetentionComponent(editor),
                binding(SettingsCardGroup.class), new SettingsCardGroup()), output, composed);
    }

    public static SettingsContractMount hosting(ContractRegistry registry, Supplier<HostingFieldsPage> fields,
                                                TextClipboard clipboard, UiSoundSink output) {
        return hosting(registry, fields, clipboard, output, false);
    }

    public static SettingsContractMount hosting(ContractRegistry registry, Supplier<HostingFieldsPage> fields,
                                                TextClipboard clipboard, UiSoundSink output, boolean composed) {
        return new SettingsContractMount(registry.page("hosting"), fields, Map.of(
                binding(HostingToggleComponent.class), new HostingToggleComponent(composed),
                binding(HostingNumberComponent.class), new HostingNumberComponent(clipboard),
                binding(SettingsCardGroup.class), new SettingsCardGroup()), output, composed);
    }

    private static Binding binding(Class<? extends StrictPageSession.Implementation> implementation) {
        return new Binding(implementation.getName(), PROFILE);
    }

    private SettingsContractMount(ContractRegistry.PageHandle page, Supplier<? extends ContractPage> fields,
                                  Map<Binding, StrictPageSession.Implementation> implementations, UiSoundSink output, boolean composed) {
        this.page = page;
        this.fields = fields;
        session = new StrictPageHost(page, implementations, output).create(fields.get());
        embedded = composed ? session.embeddedBody() : null;
    }

    public PresentationFrame render(Canvas canvas, Viewport screen, Rect body, FrameTime time) {
        session.update(fields.get().build(page));
        var bindings = bindings();
        return session.renderIn(canvas, screen, body, time, bindings.parameters(), bindings.states());
    }

    public void begin(long millis, float deltaMs) {
        session.update(fields.get().build(page));
        embedded.begin(new FrameTime(++frameId, Math.multiplyExact(millis, 1_000_000), deltaMs / 1000d), bindings());
    }

    public StrictPageSession.EmbeddedBody embedded() { return embedded; }

    public void paintGroup(Canvas canvas, String id, com.atom.chat.ui.UiLayout.Rect box) {
        embedded.paintGroup(canvas, id, new Rect(box.x(), box.y(), box.w(), box.h()));
    }

    public void paint(Canvas canvas, String id, com.atom.chat.ui.UiLayout.Rect box, float x, float y,
                      boolean focused, boolean pressed) {
        embedded.paint(canvas, id, new Rect(box.x(), box.y(), box.w(), box.h()), x, y, focused, pressed);
    }

    private static StrictPageSession.FrameBindings bindings() {
        var config = AtomChatConfig.get();
        var theme = AtomChatAppearance.theme();
        int cardBase = UiTokens.cardCutout(theme);
        float volume = FeedbackSoundPolicy.volume(config.feedbackSoundEnabled, config.feedbackButtonEnabled,
                config.feedbackVolume);
        return new StrictPageSession.FrameBindings(Map.of(
                "accent", (double) Integer.toUnsignedLong(theme.accentColor()),
                "textPrimary", (double) Integer.toUnsignedLong(config.textPrimaryColor),
                "textSecondary", (double) Integer.toUnsignedLong(theme.textSecondaryColor()),
                "cardBase", (double) Integer.toUnsignedLong(cardBase),
                "cornerRadius", (double) theme.cornerRadius(),
                "volume", (double) volume), Map.of(
                "lightCard", Design.isLight(cardBase),
                "decorativeMotion", Animations.enabled(),
                "buttonFeedback", volume > 0));
    }

    public StrictPageSession input() {
        return session;
    }

    @Override public void close() {
        session.close();
    }
}
