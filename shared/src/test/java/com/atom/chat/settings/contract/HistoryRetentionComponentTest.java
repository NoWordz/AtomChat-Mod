package com.atom.chat.settings.contract;

import com.atom.lib.frontend.StrictPage;
import com.atom.lib.frontend.StrictPageSession;
import com.atom.lib.frontend.contract.ContractCodec;
import com.atom.lib.frontend.contract.ContractDesign;
import com.atom.lib.frontend.contract.ContractRegistry;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.InputResult;
import com.atom.lib.input.KeyEvent;
import com.atom.lib.input.PointerEvent;
import com.atom.lib.layout.NodeId;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Surface;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.atom.lib.frontend.contract.ContractDesign.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoryRetentionComponentTest {
    private static final String PROFILE = "atomchat-settings/1";
    private static final Binding BINDING = new Binding(HistoryRetentionComponent.class.getName(), PROFILE);

    @Test void draftsCommitOnOutsideClickAndBlockedNavigationDoesNotStealInput() {
        List<Integer> committed = new ArrayList<>();
        var contract = contract();
        var page = StrictPage.builder(contract).component("retention", contract.recipe("number"),
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

    private static ContractRegistry.PageHandle contract() {
        var role = new EnumMap<Aspect, Requirement>(Aspect.class);
        for (var aspect : Aspect.values()) role.put(aspect, Requirement.notApplicable("bound history number field"));
        role.put(Aspect.EDITING, Requirement.required());
        var zero = new Ref("space", "zero");
        var design = new ContractDesign(1, "chat-history", "1", PROFILE, Map.of("root", ""), Set.of(),
                Map.of("accent", color(0xff4a90e2), "textPrimary", color(0xffffffff), "textSecondary", color(0xffafb3bd),
                        "cardBase", color(0xff15191f), "cornerRadius", new Parameter(0, 25, 25, Unit.U)),
                Map.of("space", new Family("root", Aspect.SPACING, "", Map.of("zero", new NumberValue(0, Unit.U),
                                "max", new NumberValue(800, Unit.U))),
                        "edit", new Family("root", Aspect.EDITING, "", Map.of("historyDays", new TextValue("history-days")))),
                Map.of("historyDays", new Role(true, role)),
                Map.of("number", new Recipe("root", "historyDays", Map.of(Aspect.EDITING, new Ref("edit", "historyDays")),
                        BINDING, Map.of("title", new Slot(ContentKind.TEXT, true), "description", new Slot(ContentKind.TEXT, true),
                                "value", new Slot(ContentKind.NUMBER, true), "unit", new Slot(ContentKind.TEXT, true)), Set.of("edit"))),
                Map.of("stack", new Layout("root", Axis.COLUMN, zero, zero, zero, new Ref("space", "max"))), Map.of(),
                Map.of("history", new Page("root", Set.of("number"), "stack", Set.of("edit"))));
        byte[] bytes = ContractCodec.encode(design);
        return ContractRegistry.load(bytes, ContractRegistry.manifest(bytes, bytes), Set.of(PROFILE)).page("history");
    }

    private static Parameter color(int argb) {
        return new Parameter(0, 0xffffffffL, Integer.toUnsignedLong(argb), Unit.ARGB);
    }
}
