package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.UUID;

/**
 * Server-media companion payloads (0.2.7). Same-jar dual entrypoint: the
 * records and codecs are registered from the common {@code @Mod} constructor
 * so a dedicated server and the integrated server of a double-open client both
 * speak the protocol.
 *
 * <p>Protocol:
 * <ul>
 *   <li>C2S {@code StateRequest} - S2C {@code MediaState}: is hosting on and
 *       what is the per-file cap.</li>
 *   <li>C2S {@code UploadStart} / {@code UploadChunk} / {@code UploadFinish} -
 *       S2C {@code UploadResult}: chunked upload; the server answers with a
 *       content-addressed {@code mediaId} (or an error).</li>
 *   <li>C2S {@code Fetch} - S2C {@code Data} chunks (or {@code Missing}):
 *       chunked download over the game connection; no HTTP port involved.</li>
 * </ul>
 *
 * <p>The registrar is marked {@code optional()}, so a server without AtomChat
 * never rejects the client: callers probe the negotiated channels first and
 * silently fall back to the external image host.
 */
public final class MediaPayloads {
    private MediaPayloads() {
    }

    private interface Writer<T> {
        void write(T payload, RegistryFriendlyByteBuf buf);
    }

    private interface Reader<T> {
        T read(RegistryFriendlyByteBuf buf);
    }

    /** Same call shape as Fabric's {@code PacketCodec.of(writer, reader)}. */
    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> codec(Writer<T> writer, Reader<T> reader) {
        return new StreamCodec<>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buf) {
                return reader.read(buf);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, T payload) {
                writer.write(payload, buf);
            }
        };
    }

    /** The protocol never carries null strings; both sides map null to "". */
    private static void writeString(RegistryFriendlyByteBuf buf, String value) {
        buf.writeUtf(value == null ? "" : value);
    }

    public record StateRequest() implements CustomPacketPayload {
        public static final Type<StateRequest> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_state_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StateRequest> STREAM_CODEC =
                codec((payload, buf) -> {
                }, buf -> new StateRequest());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MediaState(boolean enabled, int maxBytes) implements CustomPacketPayload {
        public static final Type<MediaState> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MediaState> STREAM_CODEC =
                codec((payload, buf) -> {
                    buf.writeBoolean(payload.enabled());
                    buf.writeInt(payload.maxBytes());
                }, buf -> new MediaState(buf.readBoolean(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UploadStart(UUID uploadId, String name, int totalBytes) implements CustomPacketPayload {
        public static final Type<UploadStart> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_upload_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadStart> STREAM_CODEC =
                codec((payload, buf) -> {
                    buf.writeUUID(payload.uploadId());
                    writeString(buf, payload.name());
                    buf.writeInt(payload.totalBytes());
                }, buf -> new UploadStart(buf.readUUID(), buf.readUtf(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UploadChunk(UUID uploadId, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<UploadChunk> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_upload_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadChunk> STREAM_CODEC =
                codec((payload, buf) -> {
                    buf.writeUUID(payload.uploadId());
                    buf.writeInt(payload.offset());
                    buf.writeByteArray(payload.data());
                }, buf -> new UploadChunk(buf.readUUID(), buf.readInt(), buf.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UploadFinish(UUID uploadId) implements CustomPacketPayload {
        public static final Type<UploadFinish> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_upload_finish"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadFinish> STREAM_CODEC =
                codec((payload, buf) -> buf.writeUUID(payload.uploadId()),
                        buf -> new UploadFinish(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** {@code mediaId} and {@code error} are empty strings rather than null. */
    public record UploadResult(UUID uploadId, String mediaId, String error) implements CustomPacketPayload {
        public static final Type<UploadResult> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_upload_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadResult> STREAM_CODEC =
                codec((payload, buf) -> {
                    buf.writeUUID(payload.uploadId());
                    writeString(buf, payload.mediaId());
                    writeString(buf, payload.error());
                }, buf -> new UploadResult(buf.readUUID(), buf.readUtf(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Fetch(String mediaId) implements CustomPacketPayload {
        public static final Type<Fetch> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_fetch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fetch> STREAM_CODEC =
                codec((payload, buf) -> writeString(buf, payload.mediaId()),
                        buf -> new Fetch(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One streamed slice; {@code offset + data.length == totalBytes} marks the end. */
    public record Data(String mediaId, int offset, int totalBytes, byte[] data) implements CustomPacketPayload {
        public static final Type<Data> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC =
                codec((payload, buf) -> {
                    writeString(buf, payload.mediaId());
                    buf.writeInt(payload.offset());
                    buf.writeInt(payload.totalBytes());
                    buf.writeByteArray(payload.data());
                }, buf -> new Data(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Missing(String mediaId) implements CustomPacketPayload {
        public static final Type<Missing> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_missing"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Missing> STREAM_CODEC =
                codec((payload, buf) -> writeString(buf, payload.mediaId()),
                        buf -> new Missing(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Registers every payload on the NeoForge payload bus (common entry). */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(StateRequest.TYPE, StateRequest.STREAM_CODEC,
                MediaCompanionServer::handleStateRequest);
        registrar.playToServer(UploadStart.TYPE, UploadStart.STREAM_CODEC,
                MediaCompanionServer::handleUploadStart);
        registrar.playToServer(UploadChunk.TYPE, UploadChunk.STREAM_CODEC,
                MediaCompanionServer::handleUploadChunk);
        registrar.playToServer(UploadFinish.TYPE, UploadFinish.STREAM_CODEC,
                MediaCompanionServer::handleUploadFinish);
        registrar.playToServer(Fetch.TYPE, Fetch.STREAM_CODEC,
                MediaCompanionServer::handleFetch);
        // The S2C receivers touch client-only classes; keep them in dist-guarded
        // lambdas so a dedicated server never loads them.
        PayloadRegistrar receivers = registrar.executesOn(HandlerThread.NETWORK);
        receivers.playToClient(MediaState.TYPE, MediaState.STREAM_CODEC, (payload, ctx) -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                MediaCompanionClient.receive(ctx.connection(), ctx::enqueueWork,
                        () -> MediaCompanionClient.onState(payload.enabled(), payload.maxBytes()));
            }
        });
        receivers.playToClient(UploadResult.TYPE, UploadResult.STREAM_CODEC, (payload, ctx) -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                MediaCompanionClient.receive(ctx.connection(), ctx::enqueueWork,
                        () -> MediaCompanionClient.onUploadResult(payload));
            }
        });
        receivers.playToClient(Data.TYPE, Data.STREAM_CODEC, (payload, ctx) -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                MediaCompanionClient.receive(ctx.connection(), ctx::enqueueWork,
                        () -> MediaCompanionClient.onData(payload));
            }
        });
        receivers.playToClient(Missing.TYPE, Missing.STREAM_CODEC, (payload, ctx) -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                MediaCompanionClient.receive(ctx.connection(), ctx::enqueueWork,
                        () -> MediaCompanionClient.onMissing(payload));
            }
        });
    }
}
