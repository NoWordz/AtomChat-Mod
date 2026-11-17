package com.atom.chat.chat;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ComponentIdentityTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @AfterEach void clear() { SeenPlayers.clear(); LearnedChatFormats.clear(); }
    private HoverEvent.EntityTooltipInfo entity(UUID id, String account) {
        for (var constructor : HoverEvent.EntityTooltipInfo.class.getConstructors()) {
            if (constructor.getParameterCount() != 3) continue;
            Object name = constructor.getParameterTypes()[2] == Optional.class
                    ? Optional.of(Component.literal(account)) : Component.literal(account);
            try { return (HoverEvent.EntityTooltipInfo) constructor.newInstance(EntityType.PLAYER, id, name); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        throw new AssertionError("Missing entity tooltip constructor");
    }
    private Component nickname(UUID id) {
        return Component.literal("BlueFox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 "))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,entity(id, "Remote_7"))));
    }
    @Test void firstUnseenOfflineRemotePlayerUsesWireUuidAndRealAccountWithDecoratedName() {
        UUID id=UUID.nameUUIDFromBytes("OfflinePlayer:Remote_7".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Component body=Component.literal("hello").setStyle(Style.EMPTY.withBold(true));
        Component line=Component.literal("[Global] [VIP] ").append(nickname(id)).append(Component.literal(": ")).append(body);
        SenderMeta meta=ComponentIdentity.detect(line);
        assertNotNull(meta);
        assertEquals(id,meta.senderUuid()); assertEquals("Remote_7",meta.profileName());
        assertTrue(meta.senderName().contains("[Global]")); assertTrue(meta.senderName().contains("BlueFox"));
        assertEquals("hello",meta.contentText());
        assertTrue(meta.contentComponent().getStyle().isBold() || meta.contentComponent().getSiblings().stream().anyMatch(c -> c.getStyle().isBold()));
        assertEquals(IdentityEvidence.ENTITY,meta.evidence());
    }
    @Test void ordinaryBodyButtonCannotImpersonateItsTarget() {
        Component line=Component.literal("Click here to contact ").append(nickname(UUID.randomUUID())).append(Component.literal(": help"));
        assertNull(ComponentIdentity.detect(line));
    }
    @Test void wholeLineClickAndTranslatedJoinCannotBecomePlayerChat() {
        var style=Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 "));
        assertNull(ComponentIdentity.detect(Component.literal("Remote_7 joined the game").setStyle(style)));
        assertNull(ComponentIdentity.detect(Component.translatable("multiplayer.player.joined",nickname(UUID.randomUUID()))));
    }
    @Test void contradictorySenderCommandsAreAConflictAndNotAMiss() {
        Component one=Component.literal("Blue").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Alice ")));
        Component two=Component.literal("Fox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Bob ")));
        var result=ComponentIdentity.inspect(Component.literal("").append(one).append(two).append(Component.literal(": hi")));
        assertTrue(result.conflict());assertNull(result.meta());
    }
    @Test void translatedChatUsesWireIdentityBeforeANicknameLookup() {
        UUID id=UUID.randomUUID();
        var label=nickname(id);
        SenderMeta meta=ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text",label,"hi"));
        assertEquals(id,meta.senderUuid());assertEquals("Remote_7",meta.profileName());
    }
    @Test void accountOnlySenderCannotBorrowTheUuidOfItsVisibleNickname() {
        SeenPlayers.remember(UUID.randomUUID(),"BlueFox","BlueFox");
        SeenPlayers.remember(UUID.randomUUID(),"Other","Remote_7");
        Component label=Component.literal("BlueFox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 ")));
        var publicMeta=ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text",label,"hello"));
        assertEquals("Remote_7",publicMeta.profileName());assertNull(publicMeta.senderUuid());
        var privateMeta=PrivateChatParser.tryParse(Component.translatable("commands.message.display.incoming",label,"hello"));
        assertEquals("Remote_7",privateMeta.profileName());assertNull(privateMeta.senderUuid());
    }
}
