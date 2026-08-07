package com.atemukesu.extendednoteblock.util;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import com.atemukesu.extendednoteblock.block.ReceiverBlock;
import com.atemukesu.extendednoteblock.block.TransmitterBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class RedstoneManager {
    private static final TicketType RECEIVER_PLAYBACK_TICKET = new TicketType(
            TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private static final Map<ServerLevel, Set<Long>> HELD_RECEIVER_CHUNKS = new WeakHashMap<>();
    private static final Set<ServerLevel> LOADING_RECEIVER_CHUNKS = java.util.Collections
            .newSetFromMap(new WeakHashMap<>());

    private RedstoneManager() {
    }

    public static final class RedstoneData extends SavedData {
        private final Set<BlockPos> activeTransmitters = new HashSet<>();
        private final Set<BlockPos> receivers = new HashSet<>();

        private static final Codec<RedstoneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.listOf().optionalFieldOf("activeTransmittersList", List.of())
                        .forGetter(data -> List.copyOf(data.activeTransmitters)),
                BlockPos.CODEC.listOf().optionalFieldOf("receivers", List.of())
                        .forGetter(data -> List.copyOf(data.receivers)))
                .apply(instance, RedstoneData::new));

        private static final SavedDataType<RedstoneData> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(ExtendedNoteBlock.MOD_ID, "redstone"),
                RedstoneData::new,
                CODEC,
                null);

        public RedstoneData() {
        }

        private RedstoneData(List<BlockPos> activeTransmitters, List<BlockPos> receivers) {
            this.activeTransmitters.addAll(activeTransmitters);
            this.receivers.addAll(receivers);
        }

        public static RedstoneData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(TYPE);
        }

        public boolean isGlobalPowered() {
            return !activeTransmitters.isEmpty();
        }

        public void discoverChunk(ServerLevel level, LevelChunk chunk) {
            int minBlockX = chunk.getPos().getMinBlockX();
            int minBlockZ = chunk.getPos().getMinBlockZ();
            LevelChunkSection[] sections = chunk.getSections();
            boolean changed = false;

            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (!section.maybeHas(state -> state.getBlock() instanceof ReceiverBlock
                        || state.getBlock() instanceof TransmitterBlock)) {
                    continue;
                }

                int minBlockY = level.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int localX = 0; localX < 16; localX++) {
                            BlockState state = section.getBlockState(localX, localY, localZ);
                            BlockPos pos = new BlockPos(minBlockX + localX, minBlockY + localY,
                                    minBlockZ + localZ);
                            if (state.getBlock() instanceof ReceiverBlock) {
                                changed |= receivers.add(pos);
                            } else if (state.getBlock() instanceof TransmitterBlock) {
                                if (state.getValue(TransmitterBlock.POWERED)
                                        && !TransmitterBlock.hasProjectionReceiver(level, pos)) {
                                    changed |= activeTransmitters.add(pos);
                                } else {
                                    changed |= activeTransmitters.remove(pos);
                                }
                            }
                        }
                    }
                }
            }

            if (changed) {
                setDirty();
            }
        }

        public void updateReceivers(ServerLevel level) {
            boolean powered = isGlobalPowered();
            var iterator = receivers.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                if (!level.hasChunkAt(pos)) {
                    continue;
                }

                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof ReceiverBlock)) {
                    iterator.remove();
                    setDirty();
                    continue;
                }

                if (state.getValue(ReceiverBlock.POWERED) != powered) {
                    level.setBlock(pos, state.setValue(ReceiverBlock.POWERED, powered), 3);
                    level.updateNeighborsAt(pos, state.getBlock(), null);
                }
            }
        }
    }

    public static void transmitterChanged(Level level, BlockPos pos, boolean powered) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        RedstoneData data = RedstoneData.get(serverLevel);
        boolean wasGlobalPowered = data.isGlobalPowered();
        boolean changed = powered
                ? data.activeTransmitters.add(pos.immutable())
                : data.activeTransmitters.remove(pos);
        if (changed) {
            data.setDirty();
            if (powered) {
                discoverLoadedChunks(serverLevel, data);
            }
            boolean isGlobalPowered = data.isGlobalPowered();
            if (!wasGlobalPowered && isGlobalPowered) {
                loadAndHoldReceiverChunks(serverLevel, data);
            } else if (wasGlobalPowered && !isGlobalPowered) {
                data.updateReceivers(serverLevel);
                releaseReceiverChunks(serverLevel);
            } else if (!LOADING_RECEIVER_CHUNKS.contains(serverLevel)) {
                data.updateReceivers(serverLevel);
            }
        }
    }

    private static void loadAndHoldReceiverChunks(ServerLevel level, RedstoneData data) {
        Set<Long> heldChunks = HELD_RECEIVER_CHUNKS.computeIfAbsent(level, ignored -> new HashSet<>());
        Set<Long> receiverChunks = new HashSet<>();
        for (BlockPos receiver : List.copyOf(data.receivers)) {
            receiverChunks.add(ChunkPos.pack(receiver));
        }

        List<CompletableFuture<?>> loads = new java.util.ArrayList<>();
        for (long packed : receiverChunks) {
            if (heldChunks.add(packed)) {
                loads.add(level.getChunkSource().addTicketAndLoadWithRadius(
                        RECEIVER_PLAYBACK_TICKET, ChunkPos.unpack(packed), 0));
            }
        }

        LOADING_RECEIVER_CHUNKS.add(level);
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) ->
                level.getServer().execute(() -> {
                    LOADING_RECEIVER_CHUNKS.remove(level);
                    if (!data.isGlobalPowered()) {
                        return;
                    }
                    discoverLoadedChunks(level, data);
                    data.updateReceivers(level);
                }));
    }

    private static void holdReceiverChunk(ServerLevel level, BlockPos receiver) {
        Set<Long> heldChunks = HELD_RECEIVER_CHUNKS.computeIfAbsent(level, ignored -> new HashSet<>());
        long packed = ChunkPos.pack(receiver);
        if (heldChunks.add(packed)) {
            level.getChunkSource().addTicketWithRadius(
                    RECEIVER_PLAYBACK_TICKET, ChunkPos.unpack(packed), 0);
        }
    }

    private static void releaseReceiverChunks(ServerLevel level) {
        Set<Long> heldChunks = HELD_RECEIVER_CHUNKS.remove(level);
        LOADING_RECEIVER_CHUNKS.remove(level);
        if (heldChunks == null) {
            return;
        }
        for (long packed : heldChunks) {
            level.getChunkSource().removeTicketWithRadius(
                    RECEIVER_PLAYBACK_TICKET, ChunkPos.unpack(packed), 0);
        }
    }

    private static void discoverLoadedChunks(ServerLevel level, RedstoneData data) {
        level.getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> data.discoverChunk(level, chunk));
    }

    public static void discoverChunk(ServerLevel level, LevelChunk chunk) {
        RedstoneData.get(level).discoverChunk(level, chunk);
    }

    public static void addTransmitter(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        boolean powered = state.getBlock() instanceof TransmitterBlock
                && state.getValue(TransmitterBlock.POWERED);
        transmitterChanged(serverLevel, pos, powered);
    }

    public static void removeTransmitter(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        RedstoneData data = RedstoneData.get(serverLevel);
        if (data.activeTransmitters.remove(pos)) {
            data.setDirty();
            data.updateReceivers(serverLevel);
        }
    }

    public static boolean isGlobalPowered(Level level) {
        return level instanceof ServerLevel serverLevel
                && RedstoneData.get(serverLevel).isGlobalPowered();
    }

    public static BlockPos getNearestActiveTransmitter(Level level, BlockPos origin) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return origin;
        }

        BlockPos nearest = origin;
        long nearestDistance = Long.MAX_VALUE;
        for (BlockPos candidate : RedstoneData.get(serverLevel).activeTransmitters) {
            if (!serverLevel.hasChunkAt(candidate)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(candidate);
            if (!(state.getBlock() instanceof TransmitterBlock)
                    || !state.getValue(TransmitterBlock.POWERED)) {
                continue;
            }

            long dx = (long) candidate.getX() - origin.getX();
            long dy = (long) candidate.getY() - origin.getY();
            long dz = (long) candidate.getZ() - origin.getZ();
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public static void addReceiver(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        RedstoneData data = RedstoneData.get(serverLevel);
        if (data.receivers.add(pos.immutable())) {
            data.setDirty();
        }

        boolean powered = data.isGlobalPowered();
        if (powered) {
            holdReceiverChunk(serverLevel, pos);
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ReceiverBlock
                && state.getValue(ReceiverBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(ReceiverBlock.POWERED, powered), 3);
        }
    }

    public static void removeReceiver(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        RedstoneData data = RedstoneData.get(serverLevel);
        if (data.receivers.remove(pos)) {
            data.setDirty();
        }
    }

    public static void syncOnWorldLoad(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        serverLevel.getServer().execute(() -> serverLevel.getServer().execute(() -> {
            RedstoneData data = RedstoneData.get(serverLevel);
            discoverLoadedChunks(serverLevel, data);
            if (data.isGlobalPowered()) {
                loadAndHoldReceiverChunks(serverLevel, data);
            } else {
                data.updateReceivers(serverLevel);
            }
        }));
    }
}
