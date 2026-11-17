package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.ServerConfigValues;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

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
 * {@link Wire}; this file keeps the payload ids and the codec registration.
 */
public final class ConfigPayloads {
    private ConfigPayloads() {
    }

    /** The pack summary line: what the server would hand out right now. */
    public record PackStatus(int files, long bytes, String packHash) {
    }

    // ------------------------------------------------------------ value codec

    private static void writeValues(ServerConfigValues values, RegistryByteBuf buf) {
        Wire.writeValues(new FabricWireIo(buf), values);
    }

    private static ServerConfigValues readValues(RegistryByteBuf buf) {
        return Wire.readValues(new FabricWireIo(buf));
    }

    private static void writeStatus(PackStatus status, RegistryByteBuf buf) {
        Wire.writePackSummary(new FabricWireIo(buf), summary(status));
    }

    private static PackStatus readStatus(RegistryByteBuf buf) {
        Wire.PackSummary summary = Wire.readPackSummary(new FabricWireIo(buf));
        return new PackStatus(summary.files(), summary.bytes(), summary.packHash());
    }

    private static Wire.PackSummary summary(PackStatus status) {
        return new Wire.PackSummary(status.files(), status.bytes(), status.packHash());
    }

    // ---------------------------------------------------------------- records

    /** "Here is my current configuration and pack." */
    public record Snapshot(int configVersion, ServerConfigValues values, PackStatus pack)
            implements CustomPayload {
        public static final CustomPayload.Id<Snapshot> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "config_snapshot"));
        public static final PacketCodec<RegistryByteBuf, Snapshot> CODEC =
                PacketCodec.of(Snapshot::write, Snapshot::read);

        private static void write(Snapshot payload, RegistryByteBuf buf) {
            buf.writeVarInt(payload.configVersion());
            writeValues(payload.values(), buf);
            writeStatus(payload.pack(), buf);
        }

        private static Snapshot read(RegistryByteBuf buf) {
            int version = buf.readVarInt();
            ServerConfigValues values = readValues(buf);
            return new Snapshot(version, values, readStatus(buf));
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** "Store these values" - one shot, the version guards against a stale screen. */
    public record Save(int configVersion, ServerConfigValues values) implements CustomPayload {
        public static final CustomPayload.Id<Save> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "config_save"));
        public static final PacketCodec<RegistryByteBuf, Save> CODEC =
                PacketCodec.of(Save::write, Save::read);

        private static void write(Save payload, RegistryByteBuf buf) {
            buf.writeVarInt(payload.configVersion());
            writeValues(payload.values(), buf);
        }

        private static Save read(RegistryByteBuf buf) {
            int version = buf.readVarInt();
            return new Save(version, readValues(buf));
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** "Re-scan the emote folder and tell me what the pack looks like now." */
    public record Refresh() implements CustomPayload {
        public static final CustomPayload.Id<Refresh> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "config_refresh"));
        public static final PacketCodec<RegistryByteBuf, Refresh> CODEC =
                PacketCodec.of(Refresh::write, Refresh::read);

        private static void write(Refresh payload, RegistryByteBuf buf) {
        }

        private static Refresh read(RegistryByteBuf buf) {
            return new Refresh();
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** The pack summary on its own, so a rescan cannot clobber unsaved edits. */
    public record Status(PackStatus pack) implements CustomPayload {
        public static final CustomPayload.Id<Status> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "config_status"));
        public static final PacketCodec<RegistryByteBuf, Status> CODEC =
                PacketCodec.of(Status::write, Status::read);

        private static void write(Status payload, RegistryByteBuf buf) {
            writeStatus(payload.pack(), buf);
        }

        private static Status read(RegistryByteBuf buf) {
            return new Status(readStatus(buf));
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** "Saved" or "refused, and why" - the error is a lang key fragment. */
    public record Result(boolean ok, String error) implements CustomPayload {
        public static final CustomPayload.Id<Result> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "config_result"));
        public static final PacketCodec<RegistryByteBuf, Result> CODEC =
                PacketCodec.of(Result::write, Result::read);

        private static void write(Result payload, RegistryByteBuf buf) {
            buf.writeBoolean(payload.ok());
            buf.writeString(payload.error() == null ? "" : payload.error(), 64);
        }

        private static Result read(RegistryByteBuf buf) {
            return new Result(buf.readBoolean(), buf.readString(64));
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Registers every codec; must run on both logical sides (common init). */
    public static void register() {
        ConfigAdminPayloads.register();
        PayloadTypeRegistry.playC2S().register(Save.ID, Save.CODEC);
        PayloadTypeRegistry.playC2S().register(Refresh.ID, Refresh.CODEC);
        PayloadTypeRegistry.playS2C().register(Snapshot.ID, Snapshot.CODEC);
        PayloadTypeRegistry.playS2C().register(Status.ID, Status.CODEC);
        PayloadTypeRegistry.playS2C().register(Result.ID, Result.CODEC);
    }
}
