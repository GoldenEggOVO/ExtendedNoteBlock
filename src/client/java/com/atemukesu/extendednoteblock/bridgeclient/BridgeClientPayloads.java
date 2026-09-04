package com.atemukesu.extendednoteblock.bridgeclient;

import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Registry-safe clientbound payload set used by the Paper/Purpur bridge.
 * Channel IDs and byte layouts intentionally match the full mod.
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
}
