package com.atemukesu.extendednoteblock.nbs.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atemukesu.extendednoteblock.nbs.NbsSong;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class VanillaExportSmokeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsReadableLitematicStructureAndDatapack() throws Exception {
        NbsSong song = song();
        VanillaExportOptions redstoneOptions = options(VanillaExportOptions.Target.REDSTONE,
                StructureFileWriter.Format.LITEMATIC);
        var redstone = VanillaStructureGenerator.generate(song, redstoneOptions);
        assertEquals(6, redstone.noteCount());
        assertEquals(6, countBlocks(redstone.structure(), "minecraft:note_block"));
        assertTrue(countBlocks(redstone.structure(), "minecraft:repeater") > 0);
        assertTrue(redstone.structure().blocks().stream()
                .filter(block -> block.state().name().equals("minecraft:repeater"))
                .allMatch(block -> "west".equals(block.state().properties().get("facing"))));
        assertEveryNoteHasTriggerRepeater(redstone.structure());

        Path litematic = StructureFileWriter.write(redstone.structure(),
                temporaryDirectory.resolve("redstone.litematic"), StructureFileWriter.Format.LITEMATIC,
                "Smoke", "Test");
        CompoundTag litematicRoot = NbtIo.readCompressed(litematic, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        assertNotNull(litematicRoot);
        assertTrue(litematicRoot.contains("Regions"));

        VanillaExportOptions railOptions = options(VanillaExportOptions.Target.RAIL,
                StructureFileWriter.Format.STRUCTURE_NBT);
        var rail = VanillaStructureGenerator.generate(song, railOptions);
        assertEquals(6, countBlocks(rail.structure(), "minecraft:note_block"));
        assertEveryNoteHasTriggerRepeater(rail.structure());
        assertTrue(countBlocks(rail.structure(), "minecraft:detector_rail") >= 2);
        assertTrue(rail.structure().entities().size() >= 1);
        Path structure = StructureFileWriter.write(rail.structure(), temporaryDirectory.resolve("rail.nbt"),
                StructureFileWriter.Format.STRUCTURE_NBT, "Rail", "Test");
        CompoundTag structureRoot = NbtIo.readCompressed(structure, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        assertNotNull(structureRoot);
        assertTrue(structureRoot.contains("palette"));
        assertTrue(structureRoot.contains("entities"));

        VanillaExportOptions datapackOptions = options(VanillaExportOptions.Target.DATAPACK,
                StructureFileWriter.Format.LITEMATIC);
        Path zipPath = VanillaDatapackExporter.write(song, datapackOptions,
                temporaryDirectory.resolve("music.zip")).output();
        assertTrue(Files.size(zipPath) > 0);
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            Set<String> entries = zip.stream().map(java.util.zip.ZipEntry::getName).collect(Collectors.toSet());
            assertTrue(entries.contains("pack.mcmeta"));
            assertTrue(entries.contains("data/test_music/function/play.mcfunction"));
            assertTrue(entries.contains("data/test_music/function/stop.mcfunction"));
            assertTrue(entries.stream().anyMatch(value -> value.startsWith("data/test_music/function/timeline/")));
        }
    }

    @Test
    void preservesOpeningSilenceAndMapsMidiInstrumentNames() {
        NbsSong midiSong = new NbsSong(5, 16, 11, 1, "MIDI", "", "", "", 10.0, 4,
                NbsSong.LoopSettings.NONE,
                List.of(new NbsSong.Note(10, 0, 16, 39, 100, 100, 0)),
                List.of(NbsSong.Layer.defaults(0)),
                List.of(new NbsSong.CustomInstrument("Acoustic Guitar (steel)", "", 45, false)));
        VanillaExportOptions options = options(VanillaExportOptions.Target.REDSTONE,
                StructureFileWriter.Format.LITEMATIC);
        VanillaNotePlanner.Plan plan = VanillaNotePlanner.plan(midiSong, options);
        assertEquals(VanillaInstrument.GUITAR, plan.events().get(0).notes().get(0).instrument());
        var structure = VanillaStructureGenerator.generate(midiSong, options);
        assertTrue(countBlocks(structure.structure(), "minecraft:repeater") > 0);
        assertTrue(structure.events().get(0).actualTick() > 0);
    }

    @Test
    void appliesTempoChangesAndKeepsLargeDatapackChordsTogether() {
        List<NbsSong.Note> notes = new java.util.ArrayList<>();
        notes.add(new NbsSong.Note(10, 0, 16, 45, 100, 100, 300));
        for (int i = 0; i < 40; i++) {
            notes.add(new NbsSong.Note(20, i, 0, 39 + i % 12, 100, 100, 0));
        }
        List<NbsSong.Layer> layers = java.util.stream.IntStream.range(0, 40)
                .mapToObj(NbsSong.Layer::defaults).toList();
        NbsSong changed = new NbsSong(5, 16, 21, 40, "Tempo", "", "", "", 10.0, 4,
                NbsSong.LoopSettings.NONE, notes, layers,
                List.of(new NbsSong.CustomInstrument("Tempo Changer", "", 45, false)));
        VanillaExportOptions options = options(VanillaExportOptions.Target.DATAPACK,
                StructureFileWriter.Format.LITEMATIC);
        VanillaNotePlanner.Plan plan = VanillaNotePlanner.plan(changed, options);
        assertEquals(1, plan.events().size());
        assertEquals(15, plan.events().get(0).step());
        assertEquals(40, plan.events().get(0).notes().size());
        assertEquals(0, plan.shiftedNotes());
    }

    private static long countBlocks(BlockStructure structure, String id) {
        return structure.blocks().stream().filter(block -> block.state().name().equals(id)).count();
    }

    private static void assertEveryNoteHasTriggerRepeater(BlockStructure structure) {
        for (BlockStructure.PlacedBlock note : structure.blocks().stream()
                .filter(block -> block.state().name().equals("minecraft:note_block")).toList()) {
            assertTrue(structure.blocks().stream().anyMatch(block ->
                            block.x() == note.x() - 1 && block.y() == note.y() && block.z() == note.z()
                                    && block.state().name().equals("minecraft:repeater")
                                    && "west".equals(block.state().properties().get("facing"))),
                    "Each note block must have a west-facing trigger repeater on its west side");
        }
    }

    private static VanillaExportOptions options(VanillaExportOptions.Target target,
            StructureFileWriter.Format format) {
        return new VanillaExportOptions(target, format, VanillaExportOptions.Distribution.TWO_SIDED,
                VanillaExportOptions.PitchMode.OCTAVE_FOLD, 0, 100, 10, 80, 8,
                true, true, false, false, "minecraft:stone", "minecraft:smooth_stone",
                "minecraft:stone", "test_music", "record", Map.of());
    }

    private static NbsSong song() {
        List<NbsSong.Note> notes = List.of(
                new NbsSong.Note(0, 0, 0, 39, 100, 100, 0),
                new NbsSong.Note(0, 1, 1, 43, 100, 100, 0),
                new NbsSong.Note(0, 2, 2, 46, 100, 100, 0),
                new NbsSong.Note(10, 0, 5, 51, 100, 100, 0),
                new NbsSong.Note(20, 0, 6, 55, 100, 100, 0),
                new NbsSong.Note(20, 1, 15, 58, 100, 100, 0));
        List<NbsSong.Layer> layers = List.of(
                NbsSong.Layer.defaults(0), NbsSong.Layer.defaults(1), NbsSong.Layer.defaults(2));
        return new NbsSong(5, 16, 21, 3, "Smoke Song", "Test", "", "", 10.0, 4,
                NbsSong.LoopSettings.NONE, notes, layers, List.of());
    }
}
