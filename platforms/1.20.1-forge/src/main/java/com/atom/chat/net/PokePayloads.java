package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/**
 * Poke-companion payloads on their own Forge channel, mirroring the avatar
 * channel's shape ({@link NetworkRegistry#acceptMissingOr}). A vanilla or
 * companion-less server rejects the channel, so the client never sends and the
 * poke silently degrades to the local wobble.
 */
public final class PokePayloads {
    private PokePayloads() {
    }

    /** Bumped when the packet layout changes; the channel rejects mismatches. */
    private static final String PROTOCOL = "1";

    /** Wire contract: the channel name and packet ids must never be renamed or reordered. */
    public static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "poke");
    public static final int ID_POKE = 0;
    public static final int ID_POKED = 1;

    public static SimpleChannel CHANNEL;

    public record PokeC2SPayload(UUID target) {
        public static void encode(PokeC2SPayload payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.target());
        }

        public static PokeC2SPayload decode(FriendlyByteBuf buf) {
            return new PokeC2SPayload(buf.readUUID());
        }
    }

    public record PokeS2CPayload(UUID from) {
        public static void encode(PokeS2CPayload payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.from());
        }

        public static PokeS2CPayload decode(FriendlyByteBuf buf) {
            return new PokeS2CPayload(buf.readUUID());
        }
    }

    /** Builds the channel and registers every packet id. Common setup. */
    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL));

        CHANNEL.messageBuilder(PokeC2SPayload.class, ID_POKE)
                .encoder(PokeC2SPayload::encode)
                .decoder(PokeC2SPayload::decode)
                .consumerMainThread((payload, ctx) -> PokeCompanionServer.handlePoke(payload, ctx.get()))
                .add();

        // The S2C handler touches client-only screen classes; run it on the
        // client thread only.
        CHANNEL.messageBuilder(PokeS2CPayload.class, ID_POKED)
                .encoder(PokeS2CPayload::encode)
                .decoder(PokeS2CPayload::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (net.minecraftforge.fml.loading.FMLEnvironment.dist
                            == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                        ctx.get().enqueueWork(() -> PokeCompanionClient.onPoked(payload));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }
}
