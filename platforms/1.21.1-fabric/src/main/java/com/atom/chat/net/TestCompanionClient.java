package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.notification.NotificationBanner;
import com.atom.chat.notification.NotificationController;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Client half of {@code /atomchat test}: turns a simulated event into the same
 * client-side reaction the real one produces.
 *
 * <p>Banners go through {@link NotificationController#onTestBanner}, which is the
 * production enqueue path — so this also exercises the settings toggles and the
 * sound gate rather than bypassing them, and a tester who turned a banner off
 * gets no banner, exactly as in a real session.</p>
 */
public final class TestCompanionClient {
    private TestCompanionClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TestPayloads.TestEventS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> handle(payload)));
        AtomChat.LOGGER.info("AtomChat test companion registered (client side)");
    }

    private static void handle(TestPayloads.TestEventS2CPayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        String sender = payload.senderName().isBlank()
                ? (client.player == null ? "" : client.player.getName().getString())
                : payload.senderName();
        if (TestPayloads.KIND_POKE.equals(payload.kind())) {
            // The cue is the part that needs no panel; the wobble and the toast
            // live on the screen, so they only land while it is open.
            NotificationController.playPokeSound();
            if (client.currentScreen instanceof net.minecraft.client.gui.screen.AtomChatScreen screen) {
                screen.onTestPoked(payload.senderUuid(), sender);
            } else {
                AtomChat.LOGGER.info(
                        "[test] poke cue played; open the panel for the wobble and the toast");
            }
            return;
        }
        NotificationBanner.Type type = switch (payload.kind()) {
            case TestPayloads.KIND_QUOTE -> NotificationBanner.Type.QUOTE;
            case TestPayloads.KIND_WHISPER -> NotificationBanner.Type.WHISPER;
            default -> NotificationBanner.Type.MENTION;
        };
        // The lang keys are suffixed with the enum name verbatim (MENTION /
        // QUOTE / WHISPER): a lowercased lookup misses them and the banner would
        // render the raw key.
        String text = payload.text().isBlank()
                ? Text.translatable("atomchat.test.text." + type.name()).getString()
                : payload.text();
        AtomChat.LOGGER.info("[test] simulated {} banner from {}", type, sender);
        NotificationController.onTestBanner(type, payload.senderUuid(), sender, text);
    }
}
