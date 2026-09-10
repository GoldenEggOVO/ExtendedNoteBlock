package com.atemukesu.extendednoteblock.bridgeclient;

import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.*;
import com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicMetadata;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SchematicMetadataTest {
    @Test void negativeRegionSizesMapRelativePositionsToContainerIndices() {
        assertEquals(new Pos(0, 2, 0), com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicCarriers.index(
                new Pos(-3, 2, -9), new Pos(-4, 3, -10)));
        assertEquals(new Pos(3, 0, 9), com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicCarriers.index(
                new Pos(0, 0, 0), new Pos(-4, 3, -10)));
    }

    @Test void roundTripsNegativeCoordinatesAndTimeline() throws Exception {
        Entry entry = new Entry(new Pos(-3, 2, -9), 3, null,
                List.of(new Tone(2, 60, 89, 40, -15, 1200L)));
        CompoundData root = new CompoundData();
        SchematicMetadata.write(root, Map.of("region", new Document(List.of(entry))));
        assertEquals(List.of(entry), SchematicMetadata.read(root).get("region").entries());
    }
    @Test void rejectsDamagedMetadataRatherThanSilentlyDroppingIt() {
        CompoundData root = new CompoundData();
        root.put("ExtendedNoteBlockSchematic", new CompoundData().putByteArray("region", new byte[] {1}));
        assertThrows(java.io.IOException.class, () -> SchematicMetadata.read(root));
    }
}
