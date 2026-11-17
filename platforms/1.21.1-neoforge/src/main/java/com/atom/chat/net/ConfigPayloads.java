package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.ServerConfigValues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payloads behind {@code /atomchat gui} (0.2.9): the server hands a client its
 * settings, the client sends an edited copy back, and the server answers.
 *
 * <ul>
 *   <li>C2S {@code Save(version, values)} - an edit. The server re-validates
 *       every field (it trusts nothing from the client) and refuses the whole
 *       save when the values are out of range or the {@code version} is stale.</li>
 *   <li>C2S {@code Refresh} - re-scan the emote folder and report the pack again.</li>
 *   <li>S2C {@code Snapshot(version, values, packStatus)} - opened on the player's
 *       own client, which is the only place the screen is ever built.</li>
 *   <li>S2C {@code PackStatus} - the "rescan" answer, deliberately without the
 *       values so it cannot clobber edits that are still on screen.</li>
 *   <li>S2C {@code Result(ok, error)} - the verdict, so a refused save stays on
 *       screen with a reason instead of closing.</li>
 * </ul>
 *
 * <p>The value list, its limits, its truncation and its validation live in
 * {@link Wire}; this file keeps the payload types and the channel wiring.
 */
public final class ConfigPayloads {
    public static void sendSave(Save payload) { net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload); }
    public static void sendRefresh() { var payload = new Refresh(); net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload); }

    private ConfigPayloads() {
    }

    /** The pack summary line: what the server would hand out right now. */
    public record PackStatus(int files, long bytes, String packHash) {
    }

    // ------------------------------------------------------------ value codec

    private static void writeValues(ServerConfigValues values, RegistryFriendlyByteBuf buf) {
        Wire.writeValues(new NeoForgeWireIo(buf), values);
    }

    private static ServerConfigValues readValues(RegistryFriendlyByteBuf buf) {
        return Wire.readValues(new NeoForgeWireIo(buf));
    }

    private static void writeStatus(PackStatus status, RegistryFriendlyByteBuf buf) {
        Wire.writePackSummary(new NeoForgeWireIo(buf), summary(status));
    }

    private static PackStatus readStatus(RegistryFriendlyByteBuf buf) {
        Wire.PackSummary summary = Wire.readPackSummary(new NeoForgeWireIo(buf));
        return new PackStatus(summary.files(), summary.bytes(), summary.packHash());
    }

    private static Wire.PackSummary summary(PackStatus status) {
        return new Wire.PackSummary(status.files(), status.bytes(), status.packHash());
    }

    // ---------------------------------------------------------------- records

    /** "Here is my current configuration and pack." */
    public record Snapshot(int configVersion, ServerConfigValues values, PackStatus pack)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Snapshot> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "config_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC =
                StreamCodec.of(Snapshot::write, Snapshot::read);

        private static void write(RegistryFriendlyByteBuf buf, Snapshot payload) {
            buf.writeVarInt(payload.configVersion());
            writeValues(payload.values(), buf);
            writeStatus(payload.pack(), buf);
        }

        private static Snapshot read(RegistryFriendlyByteBuf buf) {
            int version = buf.readVarInt();
            ServerConfigValues values = readValues(buf);
            return new Snapshot(version, values, readStatus(buf));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "Store these values" - one shot, the version guards against a stale screen. */
    public record Save(int configVersion, ServerConfigValues values) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Save> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "config_save"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Save> STREAM_CODEC =
                StreamCodec.of(Save::write, Save::read);

        private static void write(RegistryFriendlyByteBuf buf, Save payload) {
            buf.writeVarInt(payload.configVersion());
            writeValues(payload.values(), buf);
        }

        private static Save read(RegistryFriendlyByteBuf buf) {
            int version = buf.readVarInt();
            return new Save(version, readValues(buf));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "Re-scan the emote folder and tell me what the pack looks like now." */
    public record Refresh() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Refresh> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "config_refresh"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Refresh> STREAM_CODEC =
                StreamCodec.of(Refresh::write, Refresh::read);

        private static void write(RegistryFriendlyByteBuf buf, Refresh payload) {
        }

        private static Refresh read(RegistryFriendlyByteBuf buf) {
            return new Refresh();
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The pack summary on its own, so a rescan cannot clobber unsaved edits. */
    public record Status(PackStatus pack) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Status> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "config_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> STREAM_CODEC =
                StreamCodec.of(Status::write, Status::read);

        private static void write(RegistryFriendlyByteBuf buf, Status payload) {
            writeStatus(payload.pack(), buf);
        }

        private static Status read(RegistryFriendlyByteBuf buf) {
            return new Status(readStatus(buf));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "Saved" or "refused, and why" - the error is a lang key fragment. */
    public record Result(boolean ok, String error) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Result> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "config_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC =
                StreamCodec.of(Result::write, Result::read);

        private static void write(RegistryFriendlyByteBuf buf, Result payload) {
            buf.writeBoolean(payload.ok());
            buf.writeUtf(payload.error() == null ? "" : payload.error(), 64);
        }

        private static Result read(RegistryFriendlyByteBuf buf) {
            return new Result(buf.readBoolean(), buf.readUtf(64));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Registers every payload on the NeoForge payload bus (common entry). */
    public static void register(RegisterPayloadHandlersEvent event) {
        ConfigAdminPayloads.register(event);
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(Save.TYPE, Save.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ConfigScreenServer.onSave(ctx.player(), payload)));
        registrar.playToServer(Refresh.TYPE, Refresh.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ConfigScreenServer.onRefresh(ctx.player())));
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, (payload, ctx) -> {
            if (dist() == Dist.CLIENT) {
                ctx.enqueueWork(() -> ConfigScreenClient.onSnapshot(payload));
            }
        });
        registrar.playToClient(Status.TYPE, Status.STREAM_CODEC, (payload, ctx) -> {
            if (dist() == Dist.CLIENT) {
                ctx.enqueueWork(() -> ConfigScreenClient.onStatus(payload.pack()));
            }
        });
        registrar.playToClient(Result.TYPE, Result.STREAM_CODEC, (payload, ctx) -> {
            if (dist() == Dist.CLIENT) {
                ctx.enqueueWork(() -> ConfigScreenClient.onResult(payload));
            }
        });
    }

    private static Dist dist() {
        return FMLEnvironment.dist;
    }
}
