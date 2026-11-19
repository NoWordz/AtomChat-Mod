package com.atom.chat.settings.contract;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.frontend.StrictPage;
import com.atom.lib.frontend.StrictPageSession;
import com.atom.lib.frontend.contract.ContractCodec;
import com.atom.lib.frontend.contract.ContractDesign;
import com.atom.lib.frontend.contract.ContractRegistry;
import com.atom.lib.frontend.contract.NamedCondition;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.atom.lib.frontend.contract.ContractDesign.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoryRetentionComponentTest {
    private static final String PROFILE = "atomchat-settings/1";
    private static final Binding BINDING = new Binding(HistoryRetentionComponent.class.getName(), PROFILE);

    private static final Binding ACTION = new Binding(HistoryActionComponent.class.getName(), PROFILE);

    private static final Binding CARD = new Binding(SettingsCardGroup.class.getName(), PROFILE);

    @Test void actualHistoryRouteOpensTheOriginalConfirmationAndDoesNotClearOnPress(@TempDir Path temp)
            throws Exception {
        var configField = AtomChatConfig.class.getDeclaredField("instance");
        var providerField = Platform.class.getDeclaredField("provider");
        configField.setAccessible(true);
        providerField.setAccessible(true);
        var previous = configField.get(null);
        var previousProvider = providerField.get(null);
        var config = new AtomChatConfig();
        configField.set(null, config);
        Platform.install(new Platform.Provider() {
            public Path configDir() { return temp; }
            public Path gameDir() { return temp; }
            public boolean isModLoaded(String id) { return false; }
        });
        var history = new SettingsSectionPage();
        try {
            int historyChip = 0;
            for (var chip : history.chips(SettingsSection.CHAT)) {
                if (chip.id().equals("history")) break;
                historyChip++;
            }
            history.selectChip(SettingsSection.CHAT, historyChip);
            var actions = new ArrayList<String>();
            history.setActionHandler(actions::add);
            var sounds = new ArrayList<UiSoundIntent>();
            var output = new UiSoundSink() {
                public boolean supports(String channel) { return channel.equals("MASTER"); }
                public boolean available(String resource) { return resource.equals("atomchat:feedback_button"); }
                public void play(UiSoundIntent intent) { sounds.add(intent); }
            };
            history.bindSettingsContract(registry(), output);
            var layout = UiLayout.ofDetail(20, 20, 420, 720);
            try (var surface = Surface.makeRasterN32Premul(460, 760)) {
                var canvas = surface.getCanvas();
                history.renderBody(canvas, layout, SettingsSection.CHAT, -1, -1, 0, config.accentColor);
                assertTrue(history.historyBodyMounted());
                float x = layout.list.x() + layout.list.w() / 2;
                SettingsSectionPage.RowHit toggle = null;
                for (float y = layout.list.y(); y < layout.list.bottom(); y += 4) {
                    var hit = history.hit(x, y, layout, SettingsSection.CHAT, 0);
                    if (hit != null && hit.row().kind() == SettingsSectionPage.RowKind.SWITCH) {
                        toggle = hit;
                        break;
                    }
                }
                assertNotNull(toggle);
                boolean before = config.chatHistoryEnabled;
                assertTrue(history.perform(toggle));
                assertEquals(!before, config.chatHistoryEnabled);
                assertEquals(1, sounds.size());
                assertTrue(history.historyOwnsFeedback(toggle));

                var cleanup = history.actionAnchor(SettingsSection.CHAT, "history_clear", layout, 0);
                var hit = history.hit(cleanup.x() + 4, cleanup.y() + cleanup.h() / 2, layout, SettingsSection.CHAT, 0);
                assertTrue(history.perform(hit));
                var dialog = history.confirmation();
                assertTrue(dialog.hasPendingAction());
                assertTrue(actions.isEmpty());
                assertEquals(2, sounds.size(), "Exactly one request to open the confirmation");
                dialog.key(256);
                assertFalse(dialog.hasPendingAction());
                assertTrue(actions.isEmpty());

                history.renderBody(canvas, layout, SettingsSection.CHAT, -1, -1, 0, config.accentColor);
                assertTrue(history.perform(hit));
                var panel = new UiLayout.Rect(0, 0, 420, 380);
                for (int i = 0; i < 30; i++) dialog.render(canvas, panel, -1, -1, 16);
                dialog.key(258);
                dialog.key(257);
                assertEquals(List.of("history_clear"), actions);
                assertEquals(3, sounds.size());

                config.feedbackButtonEnabled = false;
                history.renderBody(canvas, layout, SettingsSection.CHAT, -1, -1, 0, config.accentColor);
                assertTrue(history.perform(toggle));
                assertEquals(before, config.chatHistoryEnabled, "Muting feedback preserves the action");
                assertEquals(3, sounds.size(), "The configured per-cue mute is silent");
                history.selectChip(SettingsSection.CHAT, 0);
                assertFalse(history.historyBodyMounted());
            }
        } finally {
            history.reset();
            configField.set(null, previous);
            providerField.set(null, previousProvider);
        }
    }

    @Test void draftsCommitOnOutsideClickAndBlockedNavigationDoesNotStealInput() {
        List<Integer> committed = new ArrayList<>();
        var contract = registry().page("history");
        var page = StrictPage.builder(contract).component("retention", contract.recipe("historyNumber"),
                new StrictPage.Content(Map.of("title", new StrictPage.Text("History retention"),
                        "description", new StrictPage.Text("Number of days to retain"),
                        "unit", new StrictPage.Text("days"),
                        "value", new StrictPage.NumberInput(30, next -> { committed.add((int) next); return true; })), true)).build();
        try (var surface = Surface.makeRasterN32Premul(420, 160);
             var session = new StrictPageSession(page, Map.of(BINDING, new HistoryRetentionComponent()))) {
            var presented = session.render(surface.getCanvas(), new Viewport(420, 160, 1), new FrameTime(1, 0, 0), Map.of());
            var row = presented.box(new NodeId("chat-history/history/retention")).border();
            click(session, row.x() + 6, row.y() + 6);
            assertEquals(InputResult.CONSUMED, session.character('9'));
            assertTrue(committed.isEmpty());
            assertEquals(InputResult.CONSUMED, session.wheel(200, 155, 0, -1));
            key(session, KeyEvent.Key.TAB);
            click(session, row.x() + 6, row.y() + 6);
            assertTrue(committed.isEmpty(), "An inside click must keep the whole draft");
            click(session, 15, 155);
            assertEquals(List.of(309), committed);
            click(session, row.x() + 6, row.y() + 6);
            assertEquals(InputResult.CONSUMED, session.text("8"));
            key(session, KeyEvent.Key.ESCAPE);
            assertEquals(List.of(309), committed, "Escape discards the uncommitted edit");
            click(session, row.x() + 6, row.y() + 6);
            for (int i = 0; i < 3; i++) key(session, KeyEvent.Key.BACKSPACE);
            key(session, KeyEvent.Key.ENTER);
            assertEquals(List.of(309, 0), committed);
        }
    }

    private static void click(StrictPageSession session, float x, float y) {
        session.pointer(new PointerEvent(PointerEvent.Type.PRESS, x, y, 0));
        session.pointer(new PointerEvent(PointerEvent.Type.RELEASE, x, y, 0));
    }

    private static void key(StrictPageSession session, KeyEvent.Key key) {
        assertEquals(InputResult.CONSUMED, session.key(new KeyEvent(key, true, false, false, false)));
        session.key(new KeyEvent(key, false, false, false, false));
    }

    private static ContractRegistry registry() {
        var roles = new EnumMap<Aspect, Requirement>(Aspect.class);
        for (var aspect : Aspect.values()) roles.put(aspect, Requirement.notApplicable("history fields fixture"));
        var number = new EnumMap<>(roles);
        number.put(Aspect.EDITING, Requirement.required());
        var action = new EnumMap<>(roles);
        action.put(Aspect.SOUND, Requirement.required());
        var toggle = new EnumMap<>(action);
        toggle.put(Aspect.EDITING, Requirement.required());
        var group = new EnumMap<>(roles);
        group.put(Aspect.LAYOUT, Requirement.required());
        group.put(Aspect.MATERIAL, Requirement.required());
        var zero = new Ref("space", "zero");
        var text = new Slot(ContentKind.TEXT, true);
        var cue = new Ref("sound", "button");
        var design = new ContractDesign(1, "chat-history", "1", PROFILE, Map.of("root", ""),
                Set.of("lightCard", "decorativeMotion", "buttonFeedback"),
                Map.of("accent", color(0xff4a90e2), "textPrimary", color(0xffffffff), "textSecondary", color(0xffafb3bd),
                        "cardBase", color(0xff15191f), "cornerRadius", new Parameter(0, 25, 25, Unit.U),
                        "volume", new Parameter(0, 1, .5, Unit.RATIO)),
                Map.of("space", new Family("root", Aspect.SPACING, "", Map.of("zero", new NumberValue(0, Unit.U),
                                "max", new NumberValue(800, Unit.U))),
                        "layout", new Family("root", Aspect.LAYOUT, "", Map.of("stack", new GroupLayoutValue("stack", false))),
                        "material", new Family("root", Aspect.MATERIAL, "", Map.of(
                                "merged", new TextValue("merged-settings-card/1"))),
                        "edit", new Family("root", Aspect.EDITING, "", Map.of("historyDays", new TextValue("history-days"))),
                        "sound", new Family("root", Aspect.SOUND, "",
                                Map.of("button", new SoundValue("BUTTON", "button", "MASTER", "volume", 1, 0,
                                        new NamedCondition.State("buttonFeedback"), "")))),
                Map.of("historyDays", new Role(true, number), "historySwitch", new Role(true, toggle),
                        "clear", new Role(true, action), "section", new Role(false, group)),
                Map.of("historyNumber", new Recipe("root", "historyDays", Map.of(Aspect.EDITING, new Ref("edit", "historyDays")),
                                BINDING, Map.of("title", text, "description", text, "value", new Slot(ContentKind.NUMBER, true),
                                        "unit", text), Set.of("edit")),
                        "historyToggle", new Recipe("root", "historySwitch",
                                Map.of(Aspect.SOUND, cue, Aspect.EDITING, new Ref("edit", "historyDays")), ACTION,
                                Map.of("title", text, "description", text, "value", new Slot(ContentKind.BOOLEAN, true)), Set.of("edit")),
                        "historyCleanup", new Recipe("root", "clear", Map.of(Aspect.SOUND, cue), ACTION,
                                Map.of("title", text, "description", text, "verb", text,
                                        "activate", new Slot(ContentKind.ACTION, true)), Set.of("edit")),
                        "fieldGroup", new Recipe("root", "section", Map.of(Aspect.LAYOUT, new Ref("layout", "stack"), Aspect.MATERIAL, new Ref("material", "merged")),
                                CARD, Map.of(), Set.of("edit"))),
                Map.of("stack", new Layout("root", Axis.COLUMN, zero, zero, zero, new Ref("space", "max"))),
                Map.of("button", new Asset(AssetKind.SOUND, "atomchat:feedback_button", "history feedback")),
                Map.of("history", new Page("root", Set.of("historyNumber", "historyToggle", "historyCleanup", "fieldGroup"),
                        "stack", Set.of("edit"))));
        byte[] bytes = ContractCodec.encode(design);
        return ContractRegistry.load(bytes, ContractRegistry.manifest(bytes, bytes), Set.of(PROFILE));
    }

    private static Parameter color(int argb) {
        return new Parameter(0, 0xffffffffL, Integer.toUnsignedLong(argb), Unit.ARGB);
    }
}
