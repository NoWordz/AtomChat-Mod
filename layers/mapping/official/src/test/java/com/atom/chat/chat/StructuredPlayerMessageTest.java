package com.atom.chat.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructuredPlayerMessageTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapVanilla() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void directHudItemShareRetainsTheOriginalBodyAndStyleWithoutOnlineNameHeuristics() {
        var hover = new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(new ItemStack(Items.DIAMOND)));
        Component body = Component.literal("   [Diamond]").setStyle(Style.EMPTY.withHoverEvent(hover));
        Component name = Component.literal("[VIP]Alice");
        SenderMeta meta = ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text", name, body));
        assertNotNull(meta);
        assertSame(body, meta.contentComponent());
        assertSame(name, meta.senderComponent());
        assertEquals("[VIP]Alice", meta.senderName());
        assertEquals(hover, meta.contentComponent().getStyle().getHoverEvent());
        assertFalse(meta.system());
    }
    @Test void teamTextUsesSenderAndBodyArgumentsInsteadOfTeamName() {
        Component body = Component.literal("hello");
        SenderMeta meta = ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.team.text", "Team", "Alice", body));
        assertNotNull(meta);
        assertEquals("Alice", meta.senderName());
        assertSame(body, meta.contentComponent());
    }
    @Test void unknownAndMalformedTranslationsCannotClaimPlayers() {
        assertNull(ChatPipeline.structuredPlayerLine(Component.translatable("ftbteams.message.joined", "Alice")));
        assertNull(ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text", "Alice")));
        assertNull(ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text", " ", "hello")));
    }
}
