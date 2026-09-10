package com.atemukesu.enbtest;

import com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicDataHolder;
import com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicMetadata;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.*;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import java.util.List;
import java.util.Map;

/** Invoked only with the optional mods present; checks real transformed Litematica methods. */
final class LitematicaIntegrationSmoke {
    static void check() throws Exception {
        if (!com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicCarriers.carrier(1).is(net.minecraft.world.level.block.Blocks.CONCRETE.red())
                || !com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicCarriers.carrier(2).is(net.minecraft.world.level.block.Blocks.CONCRETE.green())
                || !com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicCarriers.carrier(3).is(net.minecraft.world.level.block.Blocks.CONCRETE.purple())) {
            throw new AssertionError("ENB non-note carriers are not normalized");
        }
        CompoundData root = new CompoundData().putInt("Version", 7).putInt("SubVersion", 1)
                .putInt("MinecraftDataVersion", net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version())
                .put("Regions", new CompoundData()).put("Metadata", new CompoundData());
        var documents = Map.of("negative region", new Document(List.of(
                new Entry(new Pos(-3, 2, -9), 3, null, List.of(new Tone(2, 60, 89, 40, -15, 1200L))))));
        SchematicMetadata.write(root, documents);
        CompoundData legacy = new CompoundData().putString("Mode", "paper-safe-carriers").putInt("FormatVersion", 1);
        root.put("ExtendedNoteBlockBridge", legacy);
        LitematicaSchematic loaded = new LitematicaSchematic(null, root, FileType.LITEMATICA_SCHEMATIC);
        if (!documents.equals(SchematicMetadata.read(((SchematicDataHolder) loaded).enb$getData()))) {
            throw new AssertionError("Litematica readFromData lost metadata (including constructor reads)");
        }
        for (CompoundData saved : List.of(loaded.writeToData(), loaded.writeToData_v6())) {
            if (!documents.equals(SchematicMetadata.read(saved)) || !legacy.equals(saved.getCompound("ExtendedNoteBlockBridge"))) {
                throw new AssertionError("Litematica save lost ENB sidecars");
            }
        }
    }
}
