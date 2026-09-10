package com.atemukesu.extendednoteblock.bridgeclient.litematica;

import com.atemukesu.extendednoteblock.bridgeclient.LitematicImportReader;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.*;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt;
import fi.dy.masa.malilib.util.position.LayerRange;
import net.minecraft.core.BlockPos;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LitematicaPlacementData {
    private LitematicaPlacementData() { }
    public static Pos pos(BlockPos pos) { return new Pos(pos.getX(), pos.getY(), pos.getZ()); }
    public static Document collect(Collection<SchematicPlacement> placements, LayerRange range) throws IOException {
        Map<Pos, Entry> all = new LinkedHashMap<>();
        for (SchematicPlacement placement : placements) {
            if (!placement.isEnabled()) continue;
            CompoundData root = ((SchematicDataHolder) placement.getSchematic()).enb$getData();
            Map<String, Document> documents = SchematicMetadata.read(root);
            if (documents.isEmpty() && root.containsLenient("ExtendedNoteBlockBridge")) {
                var source = LitematicImportReader.readMetadata(DataConverterNbt.toVanillaCompound(root.getCompound("ExtendedNoteBlockBridge")));
                List<Entry> entries = new ArrayList<>();
                source.notes().forEach(note -> entries.add(new Entry(note.pos(), 0, note, List.of())));
                entries.add(new Entry(source.transmitter(), 1, null, List.of()));
                entries.add(new Entry(source.receiver(), 3, null, source.notes().stream().map(note ->
                        new Tone(note.instrument(), note.midi(), note.velocity(), note.sustain(), note.pitchCents(), note.delayMs())).toList()));
                // Original workshop exports have exactly one region. Account for its stored region origin.
                if (placement.getSchematic().getAreaSizes().size() != 1) throw new IOException("Ambiguous legacy ENB region");
                String name = placement.getSchematic().getAreaSizes().keySet().iterator().next();
                BlockPos origin = placement.getSchematic().getSubRegionPosition(name);
                documents.put(name, new Document(entries.stream().map(entry -> entry.at(new Pos(
                        entry.pos().x() - origin.getX(), entry.pos().y() - origin.getY(), entry.pos().z() - origin.getZ()))).toList()));
            }
            for (var region : documents.entrySet()) {
                SubRegionPlacement sub = placement.getRelativeSubRegionPlacement(region.getKey());
                if (sub == null || !sub.isEnabled()) continue;
                BlockPos origin = PositionUtils.getTransformedBlockPos(sub.getPos(), placement.getMirror(), placement.getRotation()).offset(placement.getOrigin());
                BlockPos size = placement.getSchematic().getAreaSize(region.getKey());
                var container = placement.getSchematic().getSubRegionContainer(region.getKey());
                if (size == null || container == null) throw new IOException("Missing ENB schematic region");
                for (Entry entry : region.getValue().entries()) {
                    Pos index = SchematicCarriers.index(entry.pos(), pos(size));
                    if (index.x() < 0 || index.y() < 0 || index.z() < 0 || index.x() >= Math.abs(size.getX())
                            || index.y() >= Math.abs(size.getY()) || index.z() >= Math.abs(size.getZ())) {
                        throw new IOException("ENB metadata outside schematic region");
                    }
                    if (container.get(index.x(), index.y(), index.z()).getBlock() != SchematicCarriers.carrier(entry.type()).getBlock()) {
                        throw new IOException("ENB metadata does not match schematic carrier");
                    }
                    BlockPos relative = new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
                    relative = PositionUtils.getTransformedBlockPos(relative, placement.getMirror(), placement.getRotation());
                    BlockPos destination = PositionUtils.getTransformedBlockPos(relative, sub.getMirror(), sub.getRotation()).offset(origin);
                    if (!range.isPositionWithinRange(destination)) continue;
                    Entry transformed = entry.at(pos(destination));
                    Entry previous = all.putIfAbsent(transformed.pos(), transformed);
                    if (previous != null && !previous.equals(transformed)) throw new IOException("Conflicting ENB placements overlap");
                }
            }
        }
        return new Document(List.copyOf(all.values()));
    }
}
