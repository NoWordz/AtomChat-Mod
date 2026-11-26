package com.atom.chat.card;

import com.atom.chat.chat.ChatMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShareCardClientPolicyTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;
    private java.lang.reflect.Field platformField, configField;
    private Object previousPlatform, previousConfig;

    @org.junit.jupiter.api.BeforeEach void installConfig() throws Exception {
        platformField = com.atom.chat.platform.Platform.class.getDeclaredField("provider");
        configField = com.atom.chat.config.AtomChatConfig.class.getDeclaredField("instance");
        platformField.setAccessible(true);
        configField.setAccessible(true);
        previousPlatform = platformField.get(null);
        previousConfig = configField.get(null);
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider() {
            public java.nio.file.Path configDir() { return root.resolve("config"); }
            public java.nio.file.Path gameDir() { return root; }
            public boolean isModLoaded(String id) { return false; }
        });
        com.atom.chat.config.AtomChatConfig.reload();
    }

    @org.junit.jupiter.api.AfterEach void restoreConfig() throws Exception {
        configField.set(null, previousConfig);
        platformField.set(null, previousPlatform);
    }

    @Test void visibleLegacyCredentialIsRemovedFromEveryMessagePresentation() {
        String credential = "private-code-7bd1";
        var original = Component.literal("[红包] LabPlayer 发了一个普通红包，口令：" + credential);
        var card = BuiltInCardAdapter.adapt(original, null, true);
        assertNotNull(card);
        assertTrue(card.hasPrivateRedPacketPassword());
        assertNull(card.bodyAction());
        assertEquals("", card.redPacket().password());

        var message = new ChatMessage(original, false).withCard(card);
        assertFalse(message.getRawText().contains(credential));
        assertFalse(message.getContentText().contains(credential));
        assertFalse(message.getContentRich().getString().contains(credential));
        assertFalse(message.getComponent().getString().contains(credential));
        assertTrue(message.getComponent().getString().contains("手动输入"));

        var unrelated = Component.literal("这里写一下口令：" + credential);
        assertFalse(BuiltInCardAdapter.isLegacyCredentialAnnouncement(unrelated, unrelated.getString()));
    }

    @Test void realAdaptersPreserveTargetedNativeButtonsAndRejectPlayerChat() {
        var nativeRequest = Component.translatable("stellarrtp.tpa.request_received_to_player", "[Rank] Alice")
                .append(Component.literal(" [Accept]").withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tpyes Alice"))))
                .append(Component.literal(" [Deny]").withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tpno Alice"))));
        com.atom.chat.api.card.TeleportRequestSession.reset();
        try {
            var card = BuiltInCardAdapter.adapt(nativeRequest, null, true);
            assertNotNull(card);
            assertEquals("StellarRTP", card.sourceLabel());
            assertEquals("Alice", card.general().avatarPlayerName());
            assertEquals(java.util.List.of("/tpyes Alice", "/tpno Alice"),
                    card.actions().stream().map(com.atom.chat.api.card.ShareCard.CardAction::value).toList());
            assertTrue(card.isNativeClickCommand("/tpyes Alice"));
            assertNull(BuiltInCardAdapter.adapt(nativeRequest, "Alice", false));
        } finally {
            com.atom.chat.api.card.TeleportRequestSession.reset();
        }
    }

    @Test void legacyTeleportRepliesNeverGuessWhichRequestChanged() {
        var first = com.atom.chat.api.card.BuiltInCardParsers
                .buildTpaRequestCard("LabPlayer", false, "/tpaccept", "/tpdeny", "StellarRTP");
        var second = com.atom.chat.api.card.BuiltInCardParsers
                .buildTpaRequestCard("LabPlayer", true, "/tpaccept", "/tpdeny", "StellarRTP");
        assertNotEquals(first.cardId(), second.cardId());
        com.atom.chat.api.card.CardStateStore.clear();
        try {
            BuiltInCardAdapter.adapt(Component.translatable("stellarrtp.tpa.request_denied", "LabPlayer"), null, true);
            BuiltInCardAdapter.adapt(Component.translatable("stellarrtp.failure.request_expired"), null, true);
            BuiltInCardAdapter.adapt(Component.literal("Unknown command. No pending request."), null, true);
            assertNull(com.atom.chat.api.card.CardStateStore.get(first.cardId()));
            assertNull(com.atom.chat.api.card.CardStateStore.get(second.cardId()));
        } finally {
            com.atom.chat.api.card.CardStateStore.clear();
        }
    }
}
