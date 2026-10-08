package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/**
 * Payload behind the {@code /atomchat test} command: the server tells the client
 * to behave as if it had just seen something only a real session can produce.
 *
 * <p>Both simulated events are client-side reactions — a notification banner and
 * a received poke — so the wire only carries which reaction plus the few fields
 * the renderer would otherwise have read off a captured message. The sender's
 * real UUID travels along so a test banner shows that player's actual face
 * rather than a placeholder.</p>
 *
 * <p>Its own optional channel, mirroring the poke channel's shape: a
 * companion-less server rejects it, so the command simply finds nothing to send
 * to and the client is never handed an unknown packet.</p>
 */
public final class TestPayloads {
    private TestPayloads() {
    }

    public static final String KIND_MENTION = "mention";
    public static final String KIND_QUOTE = "quote";
    public static final String KIND_WHISPER = "whisper";
    /** Not a banner: the receiver plays a poke cue and asks the open panel to react. */
    public static final String KIND_POKE = "poke";

    /** Bumped when the packet layout changes; the channel rejects mismatches. */
    private static final String PROTOCOL = "1";

    /** Wire contract: the channel name and packet ids must never be renamed or reordered. */
    public static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "test");
    public static final int ID_TEST_EVENT = 0;

    public static SimpleChannel CHANNEL;

    /**
     * S2C: "as if you had just received this". Empty {@code text} or
     * {@code senderName} mean "use the client's own default" — the text is then
     * resolved client-side, so it lands in the client's language rather than the
     * server's.
     */
    public record TestEventS2CPayload(String kind, UUID senderUuid, String senderName, String text) {
        public TestEventS2CPayload {
            kind = kind == null ? "" : kind;
            senderName = senderName == null ? "" : senderName;
            text = text == null ? "" : text;
        }

        public static void encode(TestEventS2CPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.kind());
            buf.writeUUID(payload.senderUuid());
            buf.writeUtf(payload.senderName());
            buf.writeUtf(payload.text());
        }

        public static TestEventS2CPayload decode(FriendlyByteBuf buf) {
            return new TestEventS2CPayload(buf.readUtf(), buf.readUUID(), buf.readUtf(), buf.readUtf());
        }
    }

    /** Builds the channel and registers the packet; common setup. */
    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL));

        // The receiver touches client-only screen classes; run it on the client
        // thread, dist-guarded.
        CHANNEL.messageBuilder(TestEventS2CPayload.class, ID_TEST_EVENT)
                .encoder(TestEventS2CPayload::encode)
                .decoder(TestEventS2CPayload::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (net.minecraftforge.fml.loading.FMLEnvironment.dist
                            == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                        ctx.get().enqueueWork(() -> TestCompanionClient.onTestEvent(payload));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }
}
