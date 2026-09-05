package com.atemukesu.extendednoteblock.bridgeprotocol;

import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionImportTest {
    private final UUID id = UUID.randomUUID();
    private final Pos anchor = new Pos(-10, 64, -20);
    private Begin begin(int count) { return new Begin(id, anchor, new Pos(-9, 64, -20), count); }
    private Note note(int midi) { return new Note(new Pos(-10 + midi, 67, -17), midi, 40, 100, 20, 3560, 2, 3, -25); }

    @Test void roundTripsPacketsAndFullMidiRangeWithinPluginMessageLimit() throws Exception {
        List<Note> notes = java.util.stream.IntStream.range(0, 128).mapToObj(this::note).toList();
        for (Packet packet : List.of(begin(128), new Batch(id, 0, notes), new Finish(id), new Cancel(id))) {
            byte[] wire = ProjectionImport.encode(packet);
            assertTrue(wire.length < 32766);
            assertEquals(packet, ProjectionImport.decode(wire));
        }
        Status status = new Status(id, ProjectionImport.REJECTED, 12, 128, "方块位置不匹配：-10, 64, -20");
        assertEquals(status, ProjectionImport.decodeStatus(ProjectionImport.encodeStatus(status)));
    }

    @Test void rejectsTruncationTrailingDataInvalidVersionsAndUnboundedCounts() {
        byte[] valid = ProjectionImport.encode(new Batch(id, 0, List.of(note(0))));
        for (int length = 0; length < valid.length; length++) {
            byte[] bytes = Arrays.copyOf(valid, length);
            assertThrows(IOException.class, () -> ProjectionImport.decode(bytes));
        }
        assertThrows(IOException.class, () -> ProjectionImport.decode(Arrays.copyOf(valid, valid.length + 1)));
        byte[] badVersion = valid.clone(); badVersion[0] = 99;
        assertThrows(IOException.class, () -> ProjectionImport.decode(badVersion));
        byte[] badCount = valid.clone(); ByteBuffer.wrap(badCount).putInt(22, Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> ProjectionImport.decode(badCount));
        assertThrows(IOException.class, () -> ProjectionImport.decode(new byte[ProjectionImport.MAX_PACKET_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> begin(ProjectionImport.MAX_NOTES + 1));
    }

    @Test void assemblyRejectsInvalidBatchesWithoutPartialMutation() {
        Assembly assembly = new Assembly(begin(3));
        assertThrows(IllegalArgumentException.class, () -> assembly.add(new Batch(id, 0, List.of(note(0), note(0)))));
        assertEquals(0, assembly.size());
        assertThrows(IllegalArgumentException.class, () -> assembly.add(new Batch(id, 0, List.of(note(0).at(anchor)))));
        assertThrows(IllegalArgumentException.class, () -> assembly.add(new Batch(id, 1, List.of(note(0)))));
        assertThrows(IllegalArgumentException.class, () -> assembly.add(new Batch(UUID.randomUUID(), 0, List.of(note(0)))));
        assembly.add(new Batch(id, 0, List.of(note(0))));
        assertThrows(IllegalArgumentException.class, () -> assembly.add(new Batch(id, 1, List.of(note(1), note(0)))));
        assertEquals(1, assembly.size());
        assertThrows(IllegalArgumentException.class, () -> assembly.finish(id));
        assembly.add(new Batch(id, 1, List.of(note(1), note(2))));
        assertEquals(List.of(note(0), note(1), note(2)), assembly.finish(id).notes());
    }

    @Test void rotatesAndMirrorsAroundTransmitterInsteadOfAssumingSchematicOrigin() {
        Pos source = new Pos(40, 0, 55), point = new Pos(42, 3, 60), destination = new Pos(-100, -60, -200);
        assertEquals(new Pos(-98, -57, -195), ProjectionImport.transform(point, source, destination, 0, 0));
        assertEquals(new Pos(-105, -57, -198), ProjectionImport.transform(point, source, destination, 1, 0));
        assertEquals(new Pos(-102, -57, -205), ProjectionImport.transform(point, source, destination, 2, 0));
        assertEquals(new Pos(-95, -57, -202), ProjectionImport.transform(point, source, destination, 3, 0));
        assertEquals(new Pos(-105, -57, -202), ProjectionImport.transform(point, source, destination, 1, 1));
        assertEquals(new Pos(-95, -57, -198), ProjectionImport.transform(point, source, destination, 1, 2));
        assertEquals(new Pos(-95, -57, -202), ProjectionImport.transform(point, source, destination, 1, 3));
        for (int rotation = 0; rotation < 4; rotation++) for (int mirror = 0; mirror < 4; mirror++) {
            assertEquals(destination, ProjectionImport.transform(source, source, destination, rotation, mirror));
        }
        assertThrows(IllegalArgumentException.class, () -> ProjectionImport.transform(point, source,
                new Pos(30_000_000, 0, 0), 0, 0));
    }
}
