package com.atom.chat.chat;

import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructuredPlayerMessageTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapVanilla() {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
    }
    @Test void directHudItemShareRetainsTheOriginalBodyAndStyleWithoutOnlineNameHeuristics() {
        var hover = new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(new ItemStack(Items.DIAMOND)));
        Text body = Text.literal("   [Diamond]").setStyle(Style.EMPTY.withHoverEvent(hover));
        Text name = Text.literal("[VIP]Alice");
        SenderMeta meta = ChatPipeline.structuredPlayerLine(Text.translatable("chat.type.text", name, body));
        assertNotNull(meta);
        assertSame(body, meta.contentComponent());
        assertSame(name, meta.senderComponent());
        assertEquals("[VIP]Alice", meta.senderName());
        assertEquals(hover, meta.contentComponent().getStyle().getHoverEvent());
        assertFalse(meta.system());
    }
    @Test void teamTextUsesSenderAndBodyArgumentsInsteadOfTeamName() {
        Text body = Text.literal("hello");
        SenderMeta meta = ChatPipeline.structuredPlayerLine(Text.translatable("chat.type.team.text", "Team", "Alice", body));
        assertNotNull(meta);
        assertEquals("Alice", meta.senderName());
        assertSame(body, meta.contentComponent());
    }
    @Test void unknownAndMalformedTranslationsCannotClaimPlayers() {
        assertNull(ChatPipeline.structuredPlayerLine(Text.translatable("ftbteams.message.joined", "Alice")));
        assertNull(ChatPipeline.structuredPlayerLine(Text.translatable("chat.type.text", "Alice")));
        assertNull(ChatPipeline.structuredPlayerLine(Text.translatable("chat.type.text", " ", "hello")));
    }
}
