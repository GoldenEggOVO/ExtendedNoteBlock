package com.atemukesu.extendednoteblock.nbs.vanilla;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

public record BlockStructure(int sizeX, int sizeY, int sizeZ, List<PlacedBlock> blocks,
        List<PlacedEntity> entities) {
    public BlockStructure {
        blocks = List.copyOf(blocks);
        entities = List.copyOf(entities);
    }

    public record BlockStateSpec(String name, Map<String, String> properties) {
        public BlockStateSpec {
            properties = Map.copyOf(properties);
        }

        public static BlockStateSpec of(String name) {
            return new BlockStateSpec(name, Map.of());
        }
    }

    public record PlacedBlock(int x, int y, int z, BlockStateSpec state, CompoundTag blockEntity) {
    }

    public record PlacedEntity(double x, double y, double z, CompoundTag nbt) {
    }

    public static final class Builder {
        private final Map<Position, BlockValue> blocks = new LinkedHashMap<>();
        private final List<PlacedEntity> entities = new ArrayList<>();

        public Builder put(int x, int y, int z, String blockId) {
            return put(x, y, z, new BlockStateSpec(blockId, Map.of()), null);
        }

        public Builder put(int x, int y, int z, String blockId, Map<String, String> properties) {
            return put(x, y, z, new BlockStateSpec(blockId, properties), null);
        }

        public Builder put(int x, int y, int z, String blockId, Map<String, String> properties,
                CompoundTag blockEntity) {
            return put(x, y, z, new BlockStateSpec(blockId, properties), blockEntity);
        }

        public Builder put(int x, int y, int z, BlockStateSpec state, CompoundTag blockEntity) {
            Position position = new Position(x, y, z);
            if (state.name().equals("minecraft:air")) {
                blocks.remove(position);
            } else {
                blocks.put(position, new BlockValue(state, blockEntity));
            }
            return this;
        }

        public Builder addEntity(double x, double y, double z, CompoundTag nbt) {
            entities.add(new PlacedEntity(x, y, z, nbt));
            return this;
        }

        public BlockStructure build() {
            if (blocks.isEmpty()) {
                throw new IllegalStateException("Cannot build an empty structure");
            }
            int minX = blocks.keySet().stream().mapToInt(Position::x).min().orElse(0);
            int minY = blocks.keySet().stream().mapToInt(Position::y).min().orElse(0);
            int minZ = blocks.keySet().stream().mapToInt(Position::z).min().orElse(0);
            int maxX = blocks.keySet().stream().mapToInt(Position::x).max().orElse(0);
            int maxY = blocks.keySet().stream().mapToInt(Position::y).max().orElse(0);
            int maxZ = blocks.keySet().stream().mapToInt(Position::z).max().orElse(0);

            List<PlacedBlock> normalizedBlocks = new ArrayList<>(blocks.size());
            blocks.forEach((position, value) -> normalizedBlocks.add(new PlacedBlock(
                    position.x() - minX, position.y() - minY, position.z() - minZ,
                    value.state(), value.blockEntity())));
            List<PlacedEntity> normalizedEntities = entities.stream()
                    .map(entity -> new PlacedEntity(entity.x() - minX, entity.y() - minY,
                            entity.z() - minZ, entity.nbt()))
                    .toList();
            return new BlockStructure(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1,
                    normalizedBlocks, normalizedEntities);
        }
    }

    private record Position(int x, int y, int z) {
    }

    private record BlockValue(BlockStateSpec state, CompoundTag blockEntity) {
    }
}
