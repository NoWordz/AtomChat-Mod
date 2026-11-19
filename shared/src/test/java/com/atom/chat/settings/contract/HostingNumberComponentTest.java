package com.atom.chat.settings.contract;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.config.ServerConfigPage;
import com.atom.chat.config.ServerConfigValues;
import com.atom.chat.settings.SettingsRowText;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.controls.TextClipboard;
import com.atom.lib.frontend.StrictPage;
import com.atom.lib.frontend.StrictPageSession;
import com.atom.lib.frontend.contract.ContractCodec;
import com.atom.lib.frontend.contract.ContractDesign;
import com.atom.lib.frontend.contract.ContractRegistry;
import com.atom.lib.frontend.contract.UiSoundIntent;
import com.atom.lib.frontend.contract.UiSoundSink;
import com.atom.lib.geometry.Rect;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.InputResult;
import com.atom.lib.input.KeyEvent;
import com.atom.lib.input.PointerEvent;
import com.atom.lib.layout.NodeId;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Surface;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.atom.lib.frontend.contract.ContractDesign.*;
import static org.junit.jupiter.api.Assertions.*;

class HostingNumberComponentTest {
    private static final String PROFILE = "atomchat-settings/1";
    private static final Binding BINDING = new Binding(HostingNumberComponent.class.getName(), PROFILE);
    private static final Binding TOGGLE = new Binding(HostingToggleComponent.class.getName(), PROFILE);

    private java.lang.reflect.Field configField;
    private Object previousConfig;

    @BeforeEach void installLocalConfig() throws Exception {
        configField = AtomChatConfig.class.getDeclaredField("instance");
        configField.setAccessible(true);
        previousConfig = configField.get(null);
        var config = new AtomChatConfig();
        config.feedbackVolume = .37f;
        configField.set(null, config);
    }

    @AfterEach void restoreConfig() throws Exception {
        configField.set(null, previousConfig);
    }

    private static final Binding CARD = new Binding(SettingsCardGroup.class.getName(), PROFILE);

    @Test void draftUsesThePaintedNumericEditorAndKeepsAnEmptyValueForValidation() {
        var page = numberContract().page("hosting");
        AtomicReference<String> draft = new AtomicReference<>("30");
        var descriptor = StrictPage.builder(page).component("retention", page.recipe("number"),
                new StrictPage.Content(Map.of(
                        "title", new StrictPage.Text("Retention"),
                        "description", new StrictPage.Text("Use a whole number"),
                        "value", new StrictPage.TextInput(draft.get(), raw -> { draft.set(raw); return true; }),
                        "unit", new StrictPage.Text("days"),
                        "invalid", new StrictPage.BooleanValue(false)), true)).build();
        var clipboard = TextClipboard.memory();
        clipboard.write("45");
        try (var surface = Surface.makeRasterN32Premul(420, 160);
             var session = new StrictPageSession(descriptor, Map.of(BINDING, new HostingNumberComponent(clipboard)))) {
            var frame = session.render(surface.getCanvas(), new Viewport(420, 160, 1), new FrameTime(1, 0, 0), Map.of());
            var rect = frame.box(new NodeId("settings-number/hosting/retention")).border();
            var row = new UiLayout.Rect(rect.x(), rect.y(), rect.width(), rect.height());
            var field = SettingsRowText.number(row, false);
            session.pointer(new PointerEvent(PointerEvent.Type.PRESS, field.x() + field.w() - 2, field.y() + 4, 0));
            session.pointer(new PointerEvent(PointerEvent.Type.RELEASE, field.x() + field.w() - 2, field.y() + 4, 0));
            assertEquals(InputResult.CONSUMED, session.key(new KeyEvent(KeyEvent.Key.A, true, false, true, false)));
            session.key(new KeyEvent(KeyEvent.Key.A, false, false, true, false));
            session.key(new KeyEvent(KeyEvent.Key.BACKSPACE, true, false, false, false));
            assertEquals("", draft.get());
            session.text("not a number");
            assertEquals("", draft.get());
            session.key(new KeyEvent(KeyEvent.Key.V, true, false, true, false));
            assertEquals("45", draft.get());
        }
    }

    @Test void realAdministrationRouteBindsBothEditsAndOneCue() {
        var systemClipboard = new AtomicReference<>("512");
        var view = new ServerConfigPage(new ServerConfigPage.Host() {
            public boolean administration() { return true; }
            public String label(String name) { return name.substring(name.lastIndexOf('.') + 1); }
            public void save(int version, ServerConfigValues values) { fail("A field edit must not send the draft"); }
            public void refresh() {}
            public void rescan() {}
            public void back() {}
            public String clipboard() { return systemClipboard.get(); }
            public void clipboard(String text) { systemClipboard.set(text); }
        }, 1, ServerConfigValues.of(new AtomChatServerConfig()));
        var intents = new ArrayList<UiSoundIntent>();
        var output = new UiSoundSink() {
            public boolean supports(String channel) { return channel.equals("MASTER"); }
            public boolean available(String resource) { return resource.equals("atomchat:feedback_button"); }
            public void play(UiSoundIntent intent) { intents.add(intent); }
        };
        view.bindSettingsContract(numberContract(), output);
        AtomChatConfig.get().animationEnabled = false;
        var layout = UiLayout.ofDetail(20, 20, 600, 760);
        try (var surface = Surface.makeRasterN32Premul(640, 800)) {
            var canvas = surface.getCanvas();
            view.renderBody(canvas, layout, -1, -1, 16);
            assertTrue(view.hostingBodyMounted());
            boolean original = view.draft().enabled("hostingEnabled");
            float y = com.atom.chat.settings.SettingsGroupFrontend.contentTop(layout,
                    com.atom.chat.settings.SettingsGroupFrontend.chipBand())
                    + com.atom.lib.design.atomchat.UiTokens.SETTINGS_LABEL_H
                    + com.atom.lib.design.atomchat.UiTokens.SETTINGS_ROW_GAP + 4;
            view.click(layout.list.x() + layout.list.w() / 2, y, 0);
            view.release();
            assertEquals(!original, view.draft().enabled("hostingEnabled"));
            assertEquals(1, intents.size());
            assertEquals("atomchat:feedback_button", intents.get(0).resource());
            assertEquals(.37f, intents.get(0).volume());

            view.key(258, 0);
            view.renderBody(canvas, layout, -1, -1, 16);
            view.key('A', 2);
            view.key('V', 2);
            assertEquals("512", view.draft().text("maxFileKb"));
            assertEquals(1, intents.size(), "Editing the numeric value is silent");
            view.key(257, 0);
            view.character('3');
            assertEquals("512", view.draft().text("maxFileKb"), "Enter clears numeric focus");
        } finally {
            view.dispose();
        }
    }

    private static void click(StrictPageSession session, float x, float y) {
        session.pointer(new PointerEvent(PointerEvent.Type.PRESS, x, y, 0));
        session.pointer(new PointerEvent(PointerEvent.Type.RELEASE, x, y, 0));
    }

    private static ContractRegistry numberContract() {
        var requirements = new EnumMap<Aspect, Requirement>(Aspect.class);
        for (var aspect : Aspect.values()) requirements.put(aspect, Requirement.notApplicable("one numeric form row"));
        requirements.put(Aspect.EDITING, Requirement.required());
        var toggle = new EnumMap<>(requirements);
        toggle.put(Aspect.SOUND, Requirement.required());
        var group = new EnumMap<>(requirements);
        group.put(Aspect.EDITING, Requirement.notApplicable("structural grouping"));
        group.put(Aspect.LAYOUT, Requirement.required());
        group.put(Aspect.MATERIAL, Requirement.required());
        var zero = new Ref("space", "zero");
        var design = new ContractDesign(1, "settings-number", "1", PROFILE, Map.of("root", ""), Set.of("lightCard", "decorativeMotion", "buttonFeedback"),
                Map.of("accent", color(0xff4a90e2), "textPrimary", color(0xffffffff), "textSecondary", color(0xffafb3bd),
                        "cardBase", color(0xff15191f), "cornerRadius", new Parameter(0, 25, 25, Unit.U),
                        "volume", new Parameter(0, 1, .5, Unit.RATIO)),
                Map.of("space", new Family("root", Aspect.SPACING, "", Map.of(
                                "zero", new NumberValue(0, Unit.U), "max", new NumberValue(800, Unit.U))),
                        "material", new Family("root", Aspect.MATERIAL, "", Map.of(
                                "merged", new TextValue("merged-settings-card/1"))),
                        "edit", new Family("root", Aspect.EDITING, "", Map.of("rawDigits", new TextValue("draft-digits"))),
                        "layout", new Family("root", Aspect.LAYOUT, "", Map.of("fields", new GroupLayoutValue("stack", false))),
                        "sound", new Family("root", Aspect.SOUND, "", Map.of(
                                "button", new SoundValue("BUTTON", "button", "MASTER", "volume", 1, 0, null, "")))),
                Map.of("number", new Role(true, requirements), "toggle", new Role(true, toggle), "fieldGroup", new Role(false, group)),
                Map.of("number", new Recipe("root", "number", Map.of(Aspect.EDITING, new Ref("edit", "rawDigits")),
                        BINDING, Map.of("title", new Slot(ContentKind.TEXT, true), "description", new Slot(ContentKind.TEXT, true),
                                "value", new Slot(ContentKind.TEXT, true), "unit", new Slot(ContentKind.TEXT, true),
                                "invalid", new Slot(ContentKind.BOOLEAN, true)), Set.of("edit")),
                        "toggle", new Recipe("root", "toggle", Map.of(Aspect.EDITING, new Ref("edit", "rawDigits"),
                                Aspect.SOUND, new Ref("sound", "button")), TOGGLE,
                                Map.of("title", new Slot(ContentKind.TEXT, true), "description", new Slot(ContentKind.TEXT, true),
                                        "value", new Slot(ContentKind.BOOLEAN, true)), Set.of("edit")),
                        "fieldGroup", new Recipe("root", "fieldGroup", Map.of(Aspect.LAYOUT, new Ref("layout", "fields"), Aspect.MATERIAL, new Ref("material", "merged")),
                                CARD, Map.of(), Set.of("edit"))),
                Map.of("stack", new Layout("root", Axis.COLUMN, zero, zero, zero, new Ref("space", "max"))),
                Map.of("button", new Asset(AssetKind.SOUND, "atomchat:feedback_button", "installed hosting feedback")),
                Map.of("hosting", new Page("root", Set.of("number", "toggle", "fieldGroup"), "stack", Set.of("edit"))));
        byte[] bytes = ContractCodec.encode(design);
        return ContractRegistry.load(bytes, ContractRegistry.manifest(bytes, bytes), Set.of(PROFILE));
    }

    private static Parameter color(int argb) {
        return new Parameter(0, 0xffffffffL, Integer.toUnsignedLong(argb), Unit.ARGB);
    }
}
