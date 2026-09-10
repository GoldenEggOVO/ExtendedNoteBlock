package com.atemukesu.extendednoteblock.bridgeclient.litematica;

import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;

public final class SchematicCarriers {
    private SchematicCarriers() { }
    public static Pos index(Pos relative, Pos size) {
        return new Pos(relative.x() - Math.min(0, size.x() + 1), relative.y() - Math.min(0, size.y() + 1),
                relative.z() - Math.min(0, size.z() + 1));
    }
    public static BlockState carrier(int type) {
        return switch (type) {
            case 0 -> Blocks.NOTE_BLOCK.defaultBlockState();
            case 1 -> Blocks.CONCRETE.red().defaultBlockState();
            case 2 -> Blocks.CONCRETE.green().defaultBlockState();
            case 3 -> Blocks.CONCRETE.purple().defaultBlockState();
            default -> throw new IllegalArgumentException("Invalid ENB carrier type");
        };
    }
    public static void normalize(LitematicaSchematic schematic) throws IOException {
        var regions = SchematicMetadata.read(((SchematicDataHolder) schematic).enb$getData());
        for (var region : regions.entrySet()) {
            var size = LitematicaPlacementData.pos(schematic.getAreaSize(region.getKey()));
            var container = schematic.getSubRegionContainer(region.getKey());
            for (var entry : region.getValue().entries()) {
                Pos index = index(entry.pos(), size);
                if (index.x() < 0 || index.y() < 0 || index.z() < 0 || index.x() >= Math.abs(size.x())
                        || index.y() >= Math.abs(size.y()) || index.z() >= Math.abs(size.z())) {
                    throw new IOException("ENB entry outside schematic region");
                }
                container.set(index.x(), index.y(), index.z(), carrier(entry.type()));
            }
        }
    }
}
