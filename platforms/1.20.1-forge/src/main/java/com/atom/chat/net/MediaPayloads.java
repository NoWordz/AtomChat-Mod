package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/**
 * Server-media companion packets (0.2.7). Same-jar dual entrypoint: the
 * channel and its handlers are registered from the common mod constructor so
 * both the integrated server of a double-open client and a dedicated server
 * speak the protocol.
 *
 * <p>Protocol:
 * <ul>
 *   <li>C2S {@code state request} - S2C {@code media state}: is hosting on and
 *       what is the per-file cap.</li>
 *   <li>C2S {@code upload start} / {@code upload chunk} / {@code upload finish}
 *       - S2C {@code upload result}: chunked upload; the server answers with a
 *       content-addressed {@code mediaId} (or an error).</li>
 *   <li>C2S {@code fetch} - S2C {@code data} chunks (or {@code missing}):
 *       chunked download over the game connection; no HTTP port involved.</li>
 * </ul>
 *
 * <p>The channel accepts connections that never negotiated it
 * ({@link NetworkRegistry#acceptMissingOr}), so a vanilla or non-mod server
 * simply ignores the unknown packets and the client silently falls back to the
 * external image host.
 */
public final class MediaPayloads {
    private MediaPayloads() {
    }

    /** Bumped when the packet layout changes; the channel rejects mismatches. */
    private static final String PROTOCOL = "1";

    /** Wire contract: the channel name and packet ids must never be renamed or reordered. */
    public static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media");
    public static final int ID_STATE_REQUEST = 0;
    public static final int ID_MEDIA_STATE = 1;
    public static final int ID_UPLOAD_START = 2;
    public static final int ID_UPLOAD_CHUNK = 3;
    public static final int ID_UPLOAD_FINISH = 4;
    public static final int ID_UPLOAD_RESULT = 5;
    public static final int ID_FETCH = 6;
    public static final int ID_DATA = 7;
    public static final int ID_MISSING = 8;

    public static SimpleChannel CHANNEL;

    /** Builds the channel and registers every packet id. Common setup. */
    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL));

        CHANNEL.messageBuilder(StateRequest.class, ID_STATE_REQUEST)
                .encoder(StateRequest::encode)
                .decoder(StateRequest::decode)
                .consumerMainThread(MediaCompanionServer::handleStateRequest)
                .add();

        CHANNEL.messageBuilder(UploadStart.class, ID_UPLOAD_START)
                .encoder(UploadStart::encode)
                .decoder(UploadStart::decode)
                .consumerMainThread(MediaCompanionServer::handleUploadStart)
                .add();

        CHANNEL.messageBuilder(UploadChunk.class, ID_UPLOAD_CHUNK)
                .encoder(UploadChunk::encode)
                .decoder(UploadChunk::decode)
                .consumerMainThread(MediaCompanionServer::handleUploadChunk)
                .add();

        CHANNEL.messageBuilder(UploadFinish.class, ID_UPLOAD_FINISH)
                .encoder(UploadFinish::encode)
                .decoder(UploadFinish::decode)
                .consumerMainThread(MediaCompanionServer::handleUploadFinish)
                .add();

        CHANNEL.messageBuilder(Fetch.class, ID_FETCH)
                .encoder(Fetch::encode)
                .decoder(Fetch::decode)
                .consumerMainThread(MediaCompanionServer::handleFetch)
                .add();

        // The S2C handlers touch client-only classes; the body only runs on a
        // client, but the dist guard keeps class loading explicit.
        CHANNEL.messageBuilder(MediaState.class, ID_MEDIA_STATE)
                .encoder(MediaState::encode)
                .decoder(MediaState::decode)
                .consumerNetworkThread((payload, ctx) -> {
                    var context = ctx.get();
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        MediaCompanionClient.receive(context.getNetworkManager(), context::enqueueWork,
                                () -> MediaCompanionClient.onState(payload.enabled(), payload.maxBytes()));
                    }
                    context.setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(UploadResult.class, ID_UPLOAD_RESULT)
                .encoder(UploadResult::encode)
                .decoder(UploadResult::decode)
                .consumerNetworkThread((payload, ctx) -> {
                    var context = ctx.get();
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        MediaCompanionClient.receive(context.getNetworkManager(), context::enqueueWork,
                                () -> MediaCompanionClient.onUploadResult(payload));
                    }
                    context.setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Data.class, ID_DATA)
                .encoder(Data::encode)
                .decoder(Data::decode)
                .consumerNetworkThread((payload, ctx) -> {
                    var context = ctx.get();
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        MediaCompanionClient.receive(context.getNetworkManager(), context::enqueueWork,
                                () -> MediaCompanionClient.onData(payload));
                    }
                    context.setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Missing.class, ID_MISSING)
                .encoder(Missing::encode)
                .decoder(Missing::decode)
                .consumerNetworkThread((payload, ctx) -> {
                    var context = ctx.get();
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        MediaCompanionClient.receive(context.getNetworkManager(), context::enqueueWork,
                                () -> MediaCompanionClient.onMissing(payload));
                    }
                    context.setPacketHandled(true);
                })
                .add();
    }

    /** The protocol never carries null strings; both sides map null to "". */
    private static void writeString(FriendlyByteBuf buf, String value) {
        buf.writeUtf(value == null ? "" : value);
    }

    public record StateRequest() {
        public static void encode(StateRequest payload, FriendlyByteBuf buf) {
        }

        public static StateRequest decode(FriendlyByteBuf buf) {
            return new StateRequest();
        }
    }

    public record MediaState(boolean enabled, int maxBytes) {
        public static void encode(MediaState payload, FriendlyByteBuf buf) {
            buf.writeBoolean(payload.enabled());
            buf.writeInt(payload.maxBytes());
        }

        public static MediaState decode(FriendlyByteBuf buf) {
            return new MediaState(buf.readBoolean(), buf.readInt());
        }
    }

    public record UploadStart(UUID uploadId, String name, int totalBytes) {
        public static void encode(UploadStart payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.uploadId());
            writeString(buf, payload.name());
            buf.writeInt(payload.totalBytes());
        }

        public static UploadStart decode(FriendlyByteBuf buf) {
            return new UploadStart(buf.readUUID(), buf.readUtf(), buf.readInt());
        }
    }

    public record UploadChunk(UUID uploadId, int offset, byte[] data) {
        public static void encode(UploadChunk payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.uploadId());
            buf.writeInt(payload.offset());
            buf.writeByteArray(payload.data());
        }

        public static UploadChunk decode(FriendlyByteBuf buf) {
            return new UploadChunk(buf.readUUID(), buf.readInt(), buf.readByteArray());
        }
    }

    public record UploadFinish(UUID uploadId) {
        public static void encode(UploadFinish payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.uploadId());
        }

        public static UploadFinish decode(FriendlyByteBuf buf) {
            return new UploadFinish(buf.readUUID());
        }
    }

    /** {@code mediaId} and {@code error} are empty strings rather than null. */
    public record UploadResult(UUID uploadId, String mediaId, String error) {
        public static void encode(UploadResult payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.uploadId());
            writeString(buf, payload.mediaId());
            writeString(buf, payload.error());
        }

        public static UploadResult decode(FriendlyByteBuf buf) {
            return new UploadResult(buf.readUUID(), buf.readUtf(), buf.readUtf());
        }
    }

    public record Fetch(String mediaId) {
        public static void encode(Fetch payload, FriendlyByteBuf buf) {
            writeString(buf, payload.mediaId());
        }

        public static Fetch decode(FriendlyByteBuf buf) {
            return new Fetch(buf.readUtf());
        }
    }

    /** One streamed slice; {@code offset + data.length == totalBytes} marks the end. */
    public record Data(String mediaId, int offset, int totalBytes, byte[] data) {
        public static void encode(Data payload, FriendlyByteBuf buf) {
            writeString(buf, payload.mediaId());
            buf.writeInt(payload.offset());
            buf.writeInt(payload.totalBytes());
            buf.writeByteArray(payload.data());
        }

        public static Data decode(FriendlyByteBuf buf) {
            return new Data(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readByteArray());
        }
    }

    public record Missing(String mediaId) {
        public static void encode(Missing payload, FriendlyByteBuf buf) {
            writeString(buf, payload.mediaId());
        }

        public static Missing decode(FriendlyByteBuf buf) {
            return new Missing(buf.readUtf());
        }
    }
}
