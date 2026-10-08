package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Server side of the poke companion. Authoritative on rate limiting: a poke is
 * cheap but trivially spammable, and a client-side gate would not stop a
 * modified client from flooding a target, so the limit lives here.
 *
 * <p>Delivery is a no-op when the target is offline or has not negotiated the
 * channel — that is the companion's normal, silent degradation.</p>
 */
public final class PokeCompanionServer {
    private PokeCompanionServer() {
    }

    /** Minimum gap between two pokes from the same sender, in ms. */
    private static final long POKE_INTERVAL_MS = 3_000L;
    private static final Map<UUID, Long> lastPokeMs = new ConcurrentHashMap<>();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PokePayloads.PokeC2SPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity sender = context.player();
                    UUID target = payload.target();
                    if (target == null || target.equals(sender.getUuid())) {
                        return;   // no self-poke, no spoofed sender
                    }
                    if (!com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    Long last = lastPokeMs.get(sender.getUuid());
                    if (last != null && now - last < POKE_INTERVAL_MS) {
                        return;   // sender is poking too fast
                    }
                    lastPokeMs.put(sender.getUuid(), now);

                    ServerPlayerEntity receiver = context.server().getPlayerManager().getPlayer(target);
                    if (receiver != null && ServerPlayNetworking.canSend(receiver, PokePayloads.PokeS2CPayload.ID)) {
                        ServerPlayNetworking.send(receiver, new PokePayloads.PokeS2CPayload(sender.getUuid()));
                    }
                });
        AtomChat.LOGGER.info("AtomChat poke companion registered (server side)");
    }

    /** Drops a disconnected player's timestamp so the map cannot grow forever. */
    public static void forgetPlayer(UUID uuid) {
        lastPokeMs.remove(uuid);
    }
}
