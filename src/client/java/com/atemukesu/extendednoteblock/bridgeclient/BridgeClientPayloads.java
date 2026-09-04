package com.atemukesu.extendednoteblock.bridgeclient;

import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Minimal clientbound payload set used by the Paper/Purpur bridge.
 *
 * This deliberately does not reference the full mod networking bootstrap or
 * any custom block/item registry classes. The channel IDs and byte layout are
 * identical to ExtendedNoteBlock's normal S2C payloads so the Paper plugin can
 * talk to either client implementation.
 */
public final class BridgeClientPayloads {
    private BridgeClientPayloads() {
    }

    public static void registerTypes() {
        PayloadTypeRegistry.clientboundPlay().register(StartSoundPayload.ID, StartSoundPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateVolumePayload.ID, UpdateVolumePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StopSoundPayload.ID, StopSoundPayload.CODEC);
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
                        buf.readBlockPos(),
                        buf.readUUID(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readFloat()));

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
}
