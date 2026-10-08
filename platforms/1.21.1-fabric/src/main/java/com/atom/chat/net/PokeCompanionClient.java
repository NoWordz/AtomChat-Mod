package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client side of the poke companion: sends a poke and hands incoming pokes to
 * the open screen.
 *
 * <p>Like the avatar companion, sending is skipped unless the server actually
 * negotiated the channel; without it the local wobble still plays, so the
 * player gets feedback either way.</p>
 */
public final class PokeCompanionClient {
    private PokeCompanionClient() {
    }

    /** Set by the screen while it is open; cleared when it closes. */
    private static volatile Consumer<UUID> onPoked;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PokePayloads.PokeS2CPayload.ID,
                (payload, context) -> {
                    UUID from = payload.from();
                    Consumer<UUID> handler = onPoked;
                    if (from != null && handler != null) {
                        // Marshal to the client thread: the screen mutates state.
                        context.client().execute(() -> {
                            Consumer<UUID> h = onPoked;
                            if (h != null) {
                                h.accept(from);
                            }
                        });
                    }
                });
        AtomChat.LOGGER.info("AtomChat poke companion registered (client side)");
    }

    public static void setHandler(Consumer<UUID> handler) {
        onPoked = handler;
    }

    /** Sends a poke when the server supports it; returns whether it was sent. */
    public static boolean send(UUID target) {
        if (target == null || MinecraftClient.getInstance().player == null) {
            return false;
        }
        if (!ClientPlayNetworking.canSend(PokePayloads.PokeC2SPayload.ID)) {
            return false;   // no companion: local wobble only, silently
        }
        ClientPlayNetworking.send(new PokePayloads.PokeC2SPayload(target));
        return true;
    }
}
