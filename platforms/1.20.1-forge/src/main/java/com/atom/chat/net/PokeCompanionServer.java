package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the poke companion. Authoritative on rate limiting: a poke is
 * cheap but trivially spammable, and a client-side gate would not stop a
 * modified client from flooding a target, so the limit lives here.
 */
public final class PokeCompanionServer {
    private PokeCompanionServer() {
    }

    /** Minimum gap between two pokes from the same sender, in ms. */
    private static final long POKE_INTERVAL_MS = 3_000L;
    private static final Map<UUID, Long> lastPokeMs = new ConcurrentHashMap<>();

    /** C2S handler; runs on the server thread. */
    public static void handlePoke(PokePayloads.PokeC2SPayload payload, NetworkEvent.Context ctx) {
        ServerPlayer sender = ctx.getSender();
        if (sender == null) {
            ctx.setPacketHandled(true);
            return;
        }
        UUID target = payload.target();
        if (target == null || target.equals(sender.getUUID())) {
            ctx.setPacketHandled(true);
            return;   // no self-poke, no spoofed sender
        }
        if (!com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) {
            ctx.setPacketHandled(true);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastPokeMs.get(sender.getUUID());
        if (last != null && now - last < POKE_INTERVAL_MS) {
            ctx.setPacketHandled(true);
            return;   // sender is poking too fast
        }
        lastPokeMs.put(sender.getUUID(), now);

        ServerPlayer receiver = sender.server.getPlayerList().getPlayer(target);
        if (receiver != null) {
            PokePayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver),
                    new PokePayloads.PokeS2CPayload(sender.getUUID()));
        }
        ctx.setPacketHandled(true);
    }

    /** Drops a disconnected player's timestamp so the map cannot grow forever. */
    public static void forgetPlayer(UUID uuid) {
        lastPokeMs.remove(uuid);
    }
}
