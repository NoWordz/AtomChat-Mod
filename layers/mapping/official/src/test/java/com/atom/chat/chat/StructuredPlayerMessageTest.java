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

    @Test void literalWrapperRetainsNestedNativeChatArguments() {
        Component name = Component.literal("[VIP]Alice");
        Component body = Component.literal("hello");
        Component line = Component.literal("[Global] ").append(Component.translatable("chat.type.text", name, body));
        SenderMeta meta = ChatPipeline.structuredPlayerLine(line);
        assertNotNull(meta);
        assertSame(body, meta.contentComponent());
        assertTrue(meta.senderComponent().getString().contains("[Global]"));
        assertTrue(meta.senderComponent().getString().contains("[VIP]Alice"));
    }
    @Test void multipleNestedChatsAreAmbiguousAndCannotInventASender() {
        Component line = Component.literal("")
            .append(Component.translatable("chat.type.text", "Alice", "one"))
            .append(Component.translatable("chat.type.text", "Bob", "two"));
        assertNull(ChatPipeline.structuredPlayerLine(line));
    }
    @Test void rawNameOnlyRendererCannotPutTheEntireBodyIntoTheSenderLabel() {
        Component rendered=Component.literal("[Global] [VIP]Alice: hello");
        SenderMeta meta=ChatPipeline.nativePublicLine(rendered,rendered,Component.literal("hello"),
                java.util.UUID.randomUUID(),"Alice");
        assertEquals("hello",meta.contentText());
        assertFalse(meta.senderName().contains("hello"));
        assertTrue(meta.senderName().contains("[Global]"));
        assertEquals(IdentityEvidence.NATIVE,meta.evidence());
    }
    @Test void rendererOmittedBodyIsNeverRestoredFromNativeMessage() {
        Component rendered=Component.literal("[hidden]");
        SenderMeta meta=ChatPipeline.nativePublicLine(rendered,rendered,Component.literal("secret"),
                java.util.UUID.randomUUID(),"Alice");
        assertEquals("[hidden]",meta.contentText());
        assertFalse(meta.contentComponent().getString().contains("secret"));
    }
    @Test void nestedChatRetainsItsOwnTrailingRichComponents() {
        var tail=Component.literal(" [item]").setStyle(net.minecraft.network.chat.Style.EMPTY.withBold(true));
        var nested=Component.translatable("chat.type.text","Alice","hello").append(tail);
        SenderMeta meta=ChatPipeline.structuredPlayerLine(Component.literal("[Global] ").append(nested));
        assertNotNull(meta);assertEquals("hello [item]",meta.contentComponent().getString());
        assertTrue(meta.contentComponent().getSiblings().stream().anyMatch(c->c.getStyle().isBold()));
    }
    @Test void inheritedWrapperStyleSurvivesStructuralExtraction() {
        var nested=Component.translatable("chat.type.text","Alice","hello");
        var line=Component.literal("[Global] ").setStyle(net.minecraft.network.chat.Style.EMPTY.withBold(true)).append(nested);
        SenderMeta meta=ChatPipeline.structuredPlayerLine(line);
        assertTrue(meta.contentComponent().getStyle().isBold());
    }
    @Test void nativeChannelDecorationOutsideTheSenderArgumentStaysInTheLabel() {
        Component name=Component.literal("BlueFox"), body=Component.literal("BlueFox says hi");
        Component visible=Component.literal("[Global] <").append(name).append(Component.literal("> ")).append(body);
        SenderMeta meta=ChatPipeline.nativePublicLine(visible,name,body,java.util.UUID.randomUUID(),"Remote_7");
        assertEquals("[Global] BlueFox",meta.senderComponent().getString());
        assertSame(body,meta.contentComponent());assertEquals("Remote_7",meta.profileName());
        assertEquals("BlueFox",meta.senderName(),"the native nickname remains separate from channel decoration");
    }
}
