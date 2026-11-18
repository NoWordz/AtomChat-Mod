package com.atom.chat.settings.contract;

import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.config.ServerConfigPage;
import com.atom.chat.config.ServerConfigValues;
import com.atom.chat.settings.SettingsRowText;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.controls.TextClipboard;
import com.atom.lib.frontend.StrictPage;
import com.atom.lib.frontend.StrictPageSession;
import com.atom.lib.frontend.StrictPageHost;
import com.atom.lib.frontend.contract.ContractCodec;
import com.atom.lib.frontend.contract.ContractDesign;
import com.atom.lib.frontend.contract.ContractRegistry;
import com.atom.lib.frontend.contract.UiSoundIntent;
import com.atom.lib.frontend.contract.UiSoundSink;
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
import org.junit.jupiter.api.Test;
import static com.atom.lib.frontend.contract.ContractDesign.*;
import static org.junit.jupiter.api.Assertions.*;

class HostingNumberComponentTest {
    private static final String PROFILE = "atomchat-settings/1";
    private static final Binding BINDING = new Binding(HostingNumberComponent.class.getName(), PROFILE);
    private static final Binding TOGGLE = new Binding(HostingToggleComponent.class.getName(), PROFILE);

    @Test void draftUsesThePaintedNumericEditorAndKeepsAnEmptyValueForValidation() {
        var page = numberContract();
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
        var view = new ServerConfigPage(new ServerConfigPage.Host() {
            public boolean administration() { return true; }
            public String label(String name) { return name.substring(name.lastIndexOf('.') + 1); }
            public void save(int version, ServerConfigValues values) { fail("A field edit must not send the draft"); }
            public void refresh() {}
            public void rescan() {}
            public void back() {}
            public String clipboard() { return ""; }
            public void clipboard(String text) {}
        }, 1, ServerConfigValues.of(new AtomChatServerConfig()));
        var intents = new ArrayList<UiSoundIntent>();
        var output = new UiSoundSink() {
            public boolean supports(String channel) { return channel.equals("MASTER"); }
            public boolean available(String resource) { return resource.equals("atomchat:feedback_button"); }
            public void play(UiSoundIntent intent) { intents.add(intent); }
        };
        var host = new StrictPageHost(numberContract(), Map.of(BINDING, new HostingNumberComponent(TextClipboard.memory()),
                TOGGLE, new HostingToggleComponent()), output);
        try (var surface = Surface.makeRasterN32Premul(600, 760);
             var session = host.create(view.hostingFields())) {
            var frame = session.render(surface.getCanvas(), new Viewport(600, 760, 1), new FrameTime(1, 0, 0), Map.of());
            var toggle = frame.box(new NodeId("settings-number/hosting/hostingEnabled")).border();
            boolean original = view.draft().enabled("hostingEnabled");
            click(session, toggle.x() + 8, toggle.y() + 8);
            assertEquals(!original, view.draft().enabled("hostingEnabled"));
            assertEquals(1, intents.size());
            assertEquals("atomchat:feedback_button", intents.get(0).resource());
            var number = frame.box(new NodeId("settings-number/hosting/maxFileKb")).border();
            var box = SettingsRowText.number(new UiLayout.Rect(number.x(), number.y(), number.width(), number.height()), false);
            click(session, box.x() + 4, box.y() + 4);
            session.key(new KeyEvent(KeyEvent.Key.A, true, false, true, false));
            session.key(new KeyEvent(KeyEvent.Key.A, false, false, true, false));
            session.text("512");
            assertEquals("512", view.draft().text("maxFileKb"));
            assertEquals(1, intents.size(), "Editing the numeric value is silent");
        } finally {
            view.dispose();
        }
    }

    private static void click(StrictPageSession session, float x, float y) {
        session.pointer(new PointerEvent(PointerEvent.Type.PRESS, x, y, 0));
        session.pointer(new PointerEvent(PointerEvent.Type.RELEASE, x, y, 0));
    }

    private static ContractRegistry.PageHandle numberContract() {
        var requirements = new EnumMap<Aspect, Requirement>(Aspect.class);
        for (var aspect : Aspect.values()) requirements.put(aspect, Requirement.notApplicable("one numeric form row"));
        requirements.put(Aspect.EDITING, Requirement.required());
        var toggle = new EnumMap<>(requirements);
        toggle.put(Aspect.SOUND, Requirement.required());
        var group = new EnumMap<>(requirements);
        group.put(Aspect.EDITING, Requirement.notApplicable("structural grouping"));
        group.put(Aspect.LAYOUT, Requirement.required());
        var zero = new Ref("space", "zero");
        var design = new ContractDesign(1, "settings-number", "1", PROFILE, Map.of("root", ""), Set.of("lightCard", "decorativeMotion"),
                Map.of("accent", color(0xff4a90e2), "textPrimary", color(0xffffffff), "textSecondary", color(0xffafb3bd),
                        "cardBase", color(0xff15191f), "cornerRadius", new Parameter(0, 25, 25, Unit.U),
                        "volume", new Parameter(0, 1, .5, Unit.RATIO)),
                Map.of("space", new Family("root", Aspect.SPACING, "", Map.of(
                                "zero", new NumberValue(0, Unit.U), "max", new NumberValue(800, Unit.U))),
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
                        "fieldGroup", new Recipe("root", "fieldGroup", Map.of(Aspect.LAYOUT, new Ref("layout", "fields")),
                                BINDING, Map.of(), Set.of("edit"))),
                Map.of("stack", new Layout("root", Axis.COLUMN, zero, zero, zero, new Ref("space", "max"))),
                Map.of("button", new Asset(AssetKind.SOUND, "atomchat:feedback_button", "installed hosting feedback")),
                Map.of("hosting", new Page("root", Set.of("number", "toggle", "fieldGroup"), "stack", Set.of("edit"))));
        byte[] bytes = ContractCodec.encode(design);
        return ContractRegistry.load(bytes, ContractRegistry.manifest(bytes, bytes), Set.of(PROFILE)).page("hosting");
    }

    private static Parameter color(int argb) {
        return new Parameter(0, 0xffffffffL, Integer.toUnsignedLong(argb), Unit.ARGB);
    }
}
