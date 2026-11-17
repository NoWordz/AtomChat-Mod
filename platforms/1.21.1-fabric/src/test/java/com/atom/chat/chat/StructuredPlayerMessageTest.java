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

    @Test void literalWrapperRetainsNestedNativeChatArguments() {
        Text name = Text.literal("[VIP]Alice");
        Text body = Text.literal("hello");
        Text line = Text.literal("[Global] ").append(Text.translatable("chat.type.text", name, body));
        SenderMeta meta = ChatPipeline.structuredPlayerLine(line);
        assertNotNull(meta);
        assertSame(body, meta.contentComponent());
        assertTrue(meta.senderComponent().getString().contains("[Global]"));
        assertTrue(meta.senderComponent().getString().contains("[VIP]Alice"));
    }
    @Test void multipleNestedChatsAreAmbiguousAndCannotInventASender() {
        Text line = Text.literal("")
            .append(Text.translatable("chat.type.text", "Alice", "one"))
            .append(Text.translatable("chat.type.text", "Bob", "two"));
        assertNull(ChatPipeline.structuredPlayerLine(line));
    }
    @Test void rawNameOnlyRendererCannotPutTheEntireBodyIntoTheSenderLabel() {
        Text rendered=Text.literal("[Global] [VIP]Alice: hello");
        SenderMeta meta=ChatPipeline.nativePublicLine(rendered,rendered,Text.literal("hello"),
                java.util.UUID.randomUUID(),"Alice");
        assertEquals("hello",meta.contentText());
        assertFalse(meta.senderName().contains("hello"));
        assertTrue(meta.senderName().contains("[Global]"));
        assertEquals(IdentityEvidence.NATIVE,meta.evidence());
    }
    @Test void rendererOmittedBodyIsNeverRestoredFromNativeMessage() {
        Text rendered=Text.literal("[hidden]");
        SenderMeta meta=ChatPipeline.nativePublicLine(rendered,rendered,Text.literal("secret"),
                java.util.UUID.randomUUID(),"Alice");
        assertEquals("[hidden]",meta.contentText());
        assertFalse(meta.contentComponent().getString().contains("secret"));
    }
    @Test void nestedChatRetainsItsOwnTrailingRichComponents() {
        var tail=Text.literal(" [item]").setStyle(net.minecraft.text.Style.EMPTY.withBold(true));
        var nested=Text.translatable("chat.type.text","Alice","hello").append(tail);
        SenderMeta meta=ChatPipeline.structuredPlayerLine(Text.literal("[Global] ").append(nested));
        assertNotNull(meta);assertEquals("hello [item]",meta.contentComponent().getString());
        assertTrue(meta.contentComponent().getSiblings().stream().anyMatch(c->c.getStyle().isBold()));
    }
    @Test void inheritedWrapperStyleSurvivesStructuralExtraction() {
        var nested=Text.translatable("chat.type.text","Alice","hello");
        var line=Text.literal("[Global] ").setStyle(net.minecraft.text.Style.EMPTY.withBold(true)).append(nested);
        SenderMeta meta=ChatPipeline.structuredPlayerLine(line);
        assertTrue(meta.contentComponent().getStyle().isBold());
    }
    @Test void nativeChannelDecorationOutsideTheSenderArgumentStaysInTheLabel() {
        Text name=Text.literal("BlueFox"), body=Text.literal("BlueFox says hi");
        Text visible=Text.literal("[Global] <").append(name).append(Text.literal("> ")).append(body);
        SenderMeta meta=ChatPipeline.nativePublicLine(visible,name,body,java.util.UUID.randomUUID(),"Remote_7");
        assertEquals("[Global] BlueFox",meta.senderComponent().getString());
        assertSame(body,meta.contentComponent());assertEquals("Remote_7",meta.profileName());
        assertEquals("BlueFox",meta.senderName(),"the native nickname remains separate from channel decoration");
    }
}
