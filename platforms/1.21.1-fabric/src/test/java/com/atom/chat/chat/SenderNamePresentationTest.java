package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.text.Style;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SenderNamePresentationTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize(); }

    @Test void captureKeepsWireDecorationsWhileRemovingRootAndRunActions() {
        var hover = RichText.literal("server hover").toText();
        Style style = Style.EMPTY.withColor(0x55FF55).withBold(true).withUnderline(true)
                .withInsertion("Player")
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg Player "))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        var actual = captured(style);
        assertEquals("Player", actual.getString());
        assertTrue(actual.hasColor());
        assertNull(actual.toText().getStyle().getClickEvent());
        assertNull(actual.toText().getStyle().getHoverEvent());
        for (var run : actual.runs()) {
            assertEquals(style.getColor(), run.style().getColor());
            assertTrue(run.style().isBold());
            assertTrue(run.style().isUnderlined());
            assertEquals("Player", run.style().getInsertion());
            assertNull(run.style().getClickEvent());
            assertNull(run.style().getHoverEvent());
        }
    }

    @Test void colorlessWireNamesRemainColorlessAndExplicitColorsAreUnchanged() {
        assertFalse(captured(Style.EMPTY.withBold(true)).hasColor(),
                "surface defaults must not be converted into a synthetic wire color");
        Style color = Style.EMPTY.withColor(0x55AAFF);
        var actual = captured(color);
        assertTrue(actual.hasColor());
        assertEquals(color.getColor(), actual.runs().get(0).style().getColor());
    }

    private static RichText captured(Style style) {
        var sender = RichText.literal("Player").toText().copy().setStyle(style);
        var body = RichText.literal("body").toText();
        var visible = RichText.literal("<").toText().copy().append(sender).append("> ").append(body);
        var meta = ChatPipeline.nativePublicLine(visible, sender, body, new UUID(0, 2), "Player");
        assertNotNull(meta.senderComponent());
        return ChatPipeline.senderPresentation(RichText.of(meta.senderComponent()));
    }
}
