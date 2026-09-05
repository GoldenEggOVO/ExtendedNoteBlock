package com.atemukesu.extendednoteblock.bridgeclient;

import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Registry-safe bridge payloads used by the Paper/Purpur companion.
 * Existing audio channel IDs intentionally match the full mod. Bridge-only
 * editor/render channels use dedicated IDs so Paper can own authoritative state.
 */
public final class BridgeClientPayloads {
    private BridgeClientPayloads() {
    }

    public static void registerTypes() {
        PayloadTypeRegistry.clientboundPlay().register(StartSoundPayload.ID, StartSoundPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateVolumePayload.ID, UpdateVolumePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StopSoundPayload.ID, StopSoundPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StartAdvancedSoundPayload.ID, StartAdvancedSoundPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdvancedUpdatePayload.ID, AdvancedUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NoteEditPayload.ID, NoteEditPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObjectSyncPayload.ID, ObjectSyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(NoteSavePayload.ID, NoteSavePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ImportPayload.ID, ImportPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ImportStatusPayload.ID, ImportStatusPayload.CODEC);
    }

    public record StartSoundPayload(
            BlockPos pos,
            UUID soundId,
            int instrumentId,
            int note,
            int velocity,
            float initialVolume) implements CustomPacketPayload {

        public static final Type<StartSoundPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("extendednoteblock", "start_sound"));

        public static final StreamCodec<FriendlyByteBuf, StartSoundPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUUID(payload.soundId);
                    buf.writeInt(payload.instrumentId);
                    buf.writeInt(payload.note);
                    buf.writeInt(payload.velocity);
                    buf.writeFloat(payload.initialVolume);
                },
                buf -> new StartSoundPayload(
                        buf.readBlockPos(), buf.readUUID(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record UpdateVolumePayload(UUID soundId, float volume) implements CustomPacketPayload {
        public static final Type<UpdateVolumePayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("extendednoteblock", "update_volume"));

        public static final StreamCodec<FriendlyByteBuf, UpdateVolumePayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeUUID(payload.soundId);
                    buf.writeFloat(payload.volume);
                },
                buf -> new UpdateVolumePayload(buf.readUUID(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record StopSoundPayload(UUID soundId) implements CustomPacketPayload {
        public static final Type<StopSoundPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("extendednoteblock", "stop_sound"));

        public static final StreamCodec<FriendlyByteBuf, StopSoundPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> buf.writeUUID(payload.soundId),
                buf -> new StopSoundPayload(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record StartAdvancedSoundPayload(
            BlockPos pos,
            UUID soundId,
            int instrumentId,
            int note,
            float initialVolume,
            float initialPitchMul,
            double x,
            double y,
            double z) implements CustomPacketPayload {

        public static final Type<StartAdvancedSoundPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("extendednoteblock", "start_adv_sound"));

        public static final StreamCodec<FriendlyByteBuf, StartAdvancedSoundPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUUID(payload.soundId);
                    buf.writeInt(payload.instrumentId);
                    buf.writeInt(payload.note);
                    buf.writeFloat(payload.initialVolume);
                    buf.writeFloat(payload.initialPitchMul);
                    buf.writeDouble(payload.x);
                    buf.writeDouble(payload.y);
                    buf.writeDouble(payload.z);
                },
                buf -> new StartAdvancedSoundPayload(
                        buf.readBlockPos(), buf.readUUID(), buf.readInt(), buf.readInt(),
                        buf.readFloat(), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record AdvancedUpdatePayload(
            UUID soundId,
            float volume,
            float pitchMultiplier,
            double x,
            double y,
            double z) implements CustomPacketPayload {

        public static final Type<AdvancedUpdatePayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("extendednoteblock", "adv_update"));

        public static final StreamCodec<FriendlyByteBuf, AdvancedUpdatePayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeUUID(payload.soundId);
                    buf.writeFloat(payload.volume);
                    buf.writeFloat(payload.pitchMultiplier);
                    buf.writeDouble(payload.x);
                    buf.writeDouble(payload.y);
                    buf.writeDouble(payload.z);
                },
                buf -> new AdvancedUpdatePayload(
                        buf.readUUID(), buf.readFloat(), buf.readFloat(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Paper -> Fabric: one placed-object render cache operation. */
    public record ObjectSyncPayload(
            int operation,
            BlockPos pos,
            int typeId,
            boolean powered,
            int variant) implements CustomPacketPayload {

        public static final Type<ObjectSyncPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("extendednoteblock", "bridge_object_sync"));

        public static final StreamCodec<FriendlyByteBuf, ObjectSyncPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeInt(payload.operation);
                    buf.writeBlockPos(payload.pos);
                    buf.writeInt(payload.typeId);
                    buf.writeBoolean(payload.powered);
                    buf.writeInt(payload.variant);
                },
                buf -> new ObjectSyncPayload(
                        buf.readInt(), buf.readBlockPos(), buf.readInt(), buf.readBoolean(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Paper -> Fabric: authoritative settings for one bridge-managed note block. */
    public record NoteEditPayload(
            BlockPos pos,
            int note,
            int instrumentId,
            int velocity,
            int sustainTicks,
            int delayMs,
            int fadeInTicks,
            int fadeOutTicks) implements CustomPacketPayload {

        public static final Type<NoteEditPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("extendednoteblock", "bridge_note_edit"));

        public static final StreamCodec<FriendlyByteBuf, NoteEditPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> writeNoteSettings(buf, payload.pos, payload.note, payload.instrumentId,
                        payload.velocity, payload.sustainTicks, payload.delayMs,
                        payload.fadeInTicks, payload.fadeOutTicks),
                buf -> new NoteEditPayload(buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Fabric -> Paper: save edited settings for one bridge-managed note block. */
    public record NoteSavePayload(
            BlockPos pos,
            int note,
            int instrumentId,
            int velocity,
            int sustainTicks,
            int delayMs,
            int fadeInTicks,
            int fadeOutTicks) implements CustomPacketPayload {

        public static final Type<NoteSavePayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("extendednoteblock", "bridge_note_save"));

        public static final StreamCodec<FriendlyByteBuf, NoteSavePayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> writeNoteSettings(buf, payload.pos, payload.note, payload.instrumentId,
                        payload.velocity, payload.sustainTicks, payload.delayMs,
                        payload.fadeInTicks, payload.fadeOutTicks),
                buf -> new NoteSavePayload(buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ImportPayload(byte[] bytes) implements CustomPacketPayload {
        public static final Type<ImportPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("extendednoteblock", "bridge_import"));
        public static final StreamCodec<FriendlyByteBuf, ImportPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> buf.writeBytes(payload.bytes), buf -> new ImportPayload(readImportBytes(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ImportStatusPayload(byte[] bytes) implements CustomPacketPayload {
        public static final Type<ImportStatusPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("extendednoteblock", "bridge_import_status"));
        public static final StreamCodec<FriendlyByteBuf, ImportStatusPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> buf.writeBytes(payload.bytes), buf -> new ImportStatusPayload(readImportBytes(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    private static byte[] readImportBytes(FriendlyByteBuf buf) {
        int size = buf.readableBytes();
        if (size > com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Import payload too large");
        }
        byte[] bytes = new byte[size]; buf.readBytes(bytes); return bytes;
    }

    private static void writeNoteSettings(FriendlyByteBuf buf, BlockPos pos, int note, int instrumentId,
                                          int velocity, int sustainTicks, int delayMs,
                                          int fadeInTicks, int fadeOutTicks) {
        buf.writeBlockPos(pos);
        buf.writeInt(note);
        buf.writeInt(instrumentId);
        buf.writeInt(velocity);
        buf.writeInt(sustainTicks);
        buf.writeInt(delayMs);
        buf.writeInt(fadeInTicks);
        buf.writeInt(fadeOutTicks);
    }
}
