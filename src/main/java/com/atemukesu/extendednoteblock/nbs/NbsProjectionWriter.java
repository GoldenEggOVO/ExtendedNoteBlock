package com.atemukesu.extendednoteblock.nbs;

import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.map.InstrumentMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

public final class NbsProjectionWriter {
    private static final int MAX_PROJECTED_NOTES = 75_000;
    private static final int[] BUILTIN_TO_GM = {
            0, 43, 128, 128, 115, 24, 73, 14, 8, 13,
            12, 113, 111, 80, 105, 9, 56, 56, 56, 56
    };

    private NbsProjectionWriter() {
    }

    public static ProjectionResult write(NbsSong song, NbsProjectionOptions options, Path requestedOutput,
            String author) throws IOException {
        List<ProjectedNote> projectedNotes = plan(song, options);
        if (projectedNotes.isEmpty()) {
            throw new IOException("The song has no exportable notes");
        }
        if (projectedNotes.size() > MAX_PROJECTED_NOTES) {
            throw new IOException("Projection note count exceeds " + MAX_PROJECTED_NOTES);
        }
        if (projectedNotes.stream().anyMatch(ProjectedNote::delayClamped)) {
            throw new IOException("Song duration exceeds the one-hour projection limit");
        }

        int columns = options.columns();
        int rows = Math.min(columns, (projectedNotes.size() + columns - 1) / columns);
        int notesPerLayer = columns * columns;
        int layers = (projectedNotes.size() + notesPerLayer - 1) / notesPerLayer;
        int sizeX = columns * 3 + 1;
        int sizeY = Math.max(4, layers * 3 + 2);
        int sizeZ = rows * 3 + 2;
        long volumeLong = (long) sizeX * sizeY * sizeZ;
        if (volumeLong > Integer.MAX_VALUE) {
            throw new IOException("Projection volume is too large: " + volumeLong);
        }

        Palette palette = new Palette();
        int[] blocks = new int[(int) volumeLong];
        ListTag tileEntities = new ListTag();

        int controllerX = sizeX / 2;
        int controllerZ = sizeZ / 2;
        setBlock(blocks, sizeX, sizeZ, controllerX, 0, controllerZ,
                palette.id("extendednoteblock:global_redstone_transmitter", Map.of("powered", "false")));
        setBlock(blocks, sizeX, sizeZ, controllerX + 1, 0, controllerZ,
                palette.id("extendednoteblock:nbs_projection_receiver", Map.of("powered", "false")));
        setBlock(blocks, sizeX, sizeZ, controllerX, 1, controllerZ,
                palette.id("minecraft:lever", Map.of("face", "floor", "facing", "north", "powered", "false")));
        tileEntities.add(createProjectionReceiverEntity(
                controllerX + 1, 0, controllerZ, projectedNotes, options.sustainTicks()));

        for (int index = 0; index < projectedNotes.size(); index++) {
            ProjectedNote note = projectedNotes.get(index);
            int layer = index / notesPerLayer;
            int local = index % notesPerLayer;
            int row = local / columns;
            int rowColumn = local % columns;
            int column = (row & 1) == 0 ? rowColumn : columns - 1 - rowColumn;
            int x = 2 + column * 3;
            int y = 3 + layer * 3;
            int z = 3 + row * 3;

            String instrumentBlock = InstrumentMap.GM_INSTRUMENT_TO_BLOCK.getOrDefault(note.gmInstrument(),
                    "minecraft:dirt");
            setBlock(blocks, sizeX, sizeZ, x, y - 1, z, palette.id(instrumentBlock, Map.of()));
            setBlock(blocks, sizeX, sizeZ, x, y, z,
                    palette.id("extendednoteblock:extended_note_block",
                            Map.of("pitch", pitchName(note.midiNote()), "powered", "false")));
            tileEntities.add(createNoteBlockEntity(x, y, z, note, options.sustainTicks()));
        }

        long[] packedStates = pack(blocks, palette.size());
        CompoundTag root = createRoot(song, author, sizeX, sizeY, sizeZ, blocks.length,
                projectedNotes.size() * 2 + 3, palette, packedStates, tileEntities);

        Path output = nextAvailablePath(requestedOutput);
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        NbtIo.writeCompressed(root, temporary);
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temporary, output);
        }

        long durationMs = projectedNotes.stream().mapToLong(ProjectedNote::delayMs).max().orElse(0);
        long customFallbacks = projectedNotes.stream().filter(ProjectedNote::customFallback).count();
        long clampedPitches = projectedNotes.stream().filter(ProjectedNote::pitchClamped).count();
        return new ProjectionResult(output, projectedNotes.size(), durationMs, customFallbacks, clampedPitches,
                sizeX, sizeY, sizeZ);
    }

    public static List<ProjectedNote> plan(NbsSong song, NbsProjectionOptions options) {
        TempoTimeline timeline = TempoTimeline.from(song);
        List<ProjectedNote> result = new ArrayList<>();
        List<NbsSong.Note> sorted = song.notes().stream()
                .sorted(Comparator.comparingInt(NbsSong.Note::tick).thenComparingInt(NbsSong.Note::layer))
                .toList();

        for (NbsSong.Note note : sorted) {
            if (song.isTempoChanger(note)) {
                continue;
            }

            int rawMidi = note.key() + 21 + options.transpose();
            int midi = options.octaveRange().fit(rawMidi);
            int layerVolume = note.layer() >= 0 && note.layer() < song.layers().size()
                    ? song.layers().get(note.layer()).volume()
                    : 100;
            int velocity = clamp((int) Math.round(note.velocity() * (layerVolume / 100.0)
                    * options.velocityMultiplier()), 0, 127);
            if (velocity == 0) {
                continue;
            }

            InstrumentResult instrument = mapInstrument(song, note.instrument(), options);
            long rawDelay = Math.round(timeline.millisAt(note.tick()) / options.speedMultiplier());
            long delay = Math.min(rawDelay, ExtendedNoteBlockEntity.MAX_DELAY_MS);
            result.add(new ProjectedNote(midi, velocity, instrument.gmId(), note.pitchCents(), delay,
                    instrument.customFallback(), rawMidi != midi, rawDelay != delay));
        }
        return result;
    }

    public static ProjectionLayout previewLayout(NbsSong song, NbsProjectionOptions options) {
        List<ProjectedNote> projectedNotes = plan(song, options);
        if (projectedNotes.size() > MAX_PROJECTED_NOTES) {
            projectedNotes = projectedNotes.subList(0, MAX_PROJECTED_NOTES);
        }
        int columns = options.columns();
        int rows = Math.min(columns, Math.max(1, (projectedNotes.size() + columns - 1) / columns));
        int notesPerLayer = columns * columns;
        int layers = Math.max(1, (projectedNotes.size() + notesPerLayer - 1) / notesPerLayer);
        int sizeX = columns * 3 + 1;
        int sizeY = Math.max(4, layers * 3 + 2);
        int sizeZ = rows * 3 + 2;
        List<PreviewBlock> blocks = new ArrayList<>(projectedNotes.size() * 2 + 3);

        int controllerX = sizeX / 2;
        int controllerZ = sizeZ / 2;
        blocks.add(new PreviewBlock(controllerX, 0, controllerZ, PreviewBlockKind.TRANSMITTER, 0, 0));
        blocks.add(new PreviewBlock(controllerX + 1, 0, controllerZ, PreviewBlockKind.RECEIVER, 0, 0));
        blocks.add(new PreviewBlock(controllerX, 1, controllerZ, PreviewBlockKind.LEVER, 0, 0));

        for (int index = 0; index < projectedNotes.size(); index++) {
            ProjectedNote note = projectedNotes.get(index);
            int layer = index / notesPerLayer;
            int local = index % notesPerLayer;
            int row = local / columns;
            int rowColumn = local % columns;
            int column = (row & 1) == 0 ? rowColumn : columns - 1 - rowColumn;
            int x = 2 + column * 3;
            int y = 3 + layer * 3;
            int z = 3 + row * 3;
            blocks.add(new PreviewBlock(x, y - 1, z, PreviewBlockKind.INSTRUMENT,
                    note.gmInstrument(), note.midiNote()));
            blocks.add(new PreviewBlock(x, y, z, PreviewBlockKind.NOTE_BLOCK,
                    note.gmInstrument(), note.midiNote()));
        }
        return new ProjectionLayout(sizeX, sizeY, sizeZ, List.copyOf(blocks));
    }

    private static InstrumentResult mapInstrument(NbsSong song, int instrument, NbsProjectionOptions options) {
        if (instrument >= 0 && instrument < song.vanillaInstrumentCount()) {
            int gmId = instrument < BUILTIN_TO_GM.length ? BUILTIN_TO_GM[instrument] : 0;
            return new InstrumentResult(gmId, false);
        }

        int customIndex = instrument - song.vanillaInstrumentCount();
        if (!options.customInstrumentsEnabled()) {
            return new InstrumentResult(options.customInstrumentFallback(), true);
        }
        if (customIndex < 0 || customIndex >= song.customInstruments().size()) {
            return new InstrumentResult(options.customInstrumentFallback(), true);
        }

        String name = normalize(song.customInstruments().get(customIndex).name());
        Integer alias = switch (name) {
            case "piano", "harp" -> 0;
            case "doublebass", "contrabass" -> 43;
            case "bassdrum", "snaredrum", "drums", "drumkit" -> 128;
            case "guitar" -> 24;
            case "flute" -> 73;
            case "bell" -> 14;
            case "xylophone" -> 13;
            case "banjo" -> 105;
            case "trumpet" -> 56;
            default -> null;
        };
        if (alias != null) {
            return new InstrumentResult(alias, false);
        }

        for (Map.Entry<Integer, String> entry : InstrumentMap.GM_INSTRUMENT_ID_TO_NAME.entrySet()) {
            if (normalize(entry.getValue()).equals(name)) {
                return new InstrumentResult(entry.getKey(), false);
            }
        }
        return new InstrumentResult(options.customInstrumentFallback(), true);
    }

    private static CompoundTag createNoteBlockEntity(int x, int y, int z, ProjectedNote note, int sustainTicks) {
        CompoundTag tag = positionTag(x, y, z);
        tag.putString("id", "extendednoteblock:extended_note_block_entity");
        tag.putInt("note", note.midiNote());
        tag.putInt("velocity", note.velocity());
        tag.putInt("sustainTime", sustainTicks);
        tag.putInt("delayedPlayingTime", (int) note.delayMs());
        tag.putInt("fadeInTime", 0);
        tag.putInt("fadeOutTime", 0);
        tag.putBoolean("nbsProjectionPlayback", true);

        if (note.pitchCents() != 0) {
            float semitones = note.pitchCents() / 100.0f;
            ListTag points = new ListTag();
            points.add(curvePoint(0.0f, semitones));
            points.add(curvePoint(1.0f, semitones));
            CompoundTag advanced = new CompoundTag();
            advanced.put("PitchBendPoints", points);
            tag.put("AdvancedData", advanced);
        }
        return tag;
    }

    private static CompoundTag createProjectionReceiverEntity(int x, int y, int z,
            List<ProjectedNote> notes, int sustainTicks) {
        int[] packed = new int[notes.size()];
        int[] pitchCents = new int[notes.size()];
        long[] delays = new long[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            ProjectedNote note = notes.get(i);
            packed[i] = (note.gmInstrument() & 0xFF)
                    | ((note.midiNote() & 0x7F) << 8)
                    | ((note.velocity() & 0x7F) << 15)
                    | ((sustainTicks & 0x1FF) << 22);
            pitchCents[i] = note.pitchCents();
            delays[i] = note.delayMs();
        }

        CompoundTag data = new CompoundTag();
        data.putIntArray("Notes", packed);
        data.putIntArray("PitchCents", pitchCents);
        data.putLongArray("Delays", delays);

        CompoundTag tag = positionTag(x, y, z);
        tag.putString("id", "extendednoteblock:nbs_projection_receiver_block_entity");
        tag.put("ProjectionData", data);
        return tag;
    }

    private static CompoundTag curvePoint(float time, float value) {
        CompoundTag point = new CompoundTag();
        point.putFloat("t", time);
        point.putFloat("v", value);
        return point;
    }

    private static CompoundTag createRoot(NbsSong song, String author, int sizeX, int sizeY, int sizeZ,
            int volume, int totalBlocks, Palette palette, long[] blockStates, ListTag tileEntities) {
        CompoundTag metadata = new CompoundTag();
        String displayName = song.name().isBlank() ? "NBS Projection" : song.name();
        long now = System.currentTimeMillis();
        metadata.putString("Name", displayName);
        metadata.putString("Author", author == null || author.isBlank() ? "ExtendedNoteBlock" : author);
        metadata.putString("Description", "Generated from NBS by ExtendedNoteBlock");
        metadata.putInt("RegionCount", 1);
        metadata.putInt("TotalVolume", volume);
        metadata.putInt("TotalBlocks", totalBlocks);
        metadata.putLong("TimeCreated", now);
        metadata.putLong("TimeModified", now);
        metadata.put("EnclosingSize", positionTag(sizeX, sizeY, sizeZ));

        CompoundTag region = new CompoundTag();
        region.put("Position", positionTag(0, 0, 0));
        region.put("Size", positionTag(sizeX, sizeY, sizeZ));
        region.put("BlockStatePalette", palette.toNbt());
        region.putLongArray("BlockStates", blockStates);
        region.put("TileEntities", tileEntities);
        region.put("Entities", new ListTag());
        region.put("PendingBlockTicks", new ListTag());
        region.put("PendingFluidTicks", new ListTag());

        CompoundTag regions = new CompoundTag();
        regions.put("NBS Music", region);

        CompoundTag root = new CompoundTag();
        root.putInt("MinecraftDataVersion", SharedConstants.WORLD_VERSION);
        root.putInt("Version", 7);
        root.putInt("SubVersion", 1);
        root.put("Metadata", metadata);
        root.put("Regions", regions);
        return root;
    }

    private static long[] pack(int[] values, int paletteSize) {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
        long mask = (1L << bits) - 1L;
        long[] packed = new long[(int) ((((long) values.length * bits) + 63L) / 64L)];

        for (int index = 0; index < values.length; index++) {
            long bitIndex = (long) index * bits;
            int firstLong = (int) (bitIndex >>> 6);
            int bitOffset = (int) (bitIndex & 63L);
            long value = values[index] & mask;
            packed[firstLong] = (packed[firstLong] & ~(mask << bitOffset)) | (value << bitOffset);
            if (bitOffset + bits > 64) {
                int spill = 64 - bitOffset;
                packed[firstLong + 1] = (packed[firstLong + 1] >>> (bits - spill) << (bits - spill))
                        | (value >>> spill);
            }
        }
        return packed;
    }

    private static void setBlock(int[] blocks, int sizeX, int sizeZ, int x, int y, int z, int paletteId) {
        blocks[y * sizeX * sizeZ + z * sizeX + x] = paletteId;
    }

    private static CompoundTag positionTag(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    private static String pitchName(int midiNote) {
        return switch (Math.floorMod(midiNote, 12)) {
            case 0 -> "c";
            case 1 -> "cs";
            case 2 -> "d";
            case 3 -> "ds";
            case 4 -> "e";
            case 5 -> "f";
            case 6 -> "fs";
            case 7 -> "g";
            case 8 -> "gs";
            case 9 -> "a";
            case 10 -> "as";
            default -> "b";
        };
    }

    private static Path nextAvailablePath(Path requested) {
        if (!Files.exists(requested)) {
            return requested;
        }
        String fileName = requested.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 2; i < 10_000; i++) {
            Path candidate = requested.resolveSibling(base + "-" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return requested.resolveSibling(base + "-" + System.currentTimeMillis() + extension);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record InstrumentResult(int gmId, boolean customFallback) {
    }

    public record ProjectedNote(int midiNote, int velocity, int gmInstrument, int pitchCents, long delayMs,
            boolean customFallback, boolean pitchClamped, boolean delayClamped) {
    }

    public record ProjectionLayout(int sizeX, int sizeY, int sizeZ, List<PreviewBlock> blocks) {
    }

    public record PreviewBlock(int x, int y, int z, PreviewBlockKind kind, int gmInstrument, int midiNote) {
    }

    public enum PreviewBlockKind {
        INSTRUMENT,
        NOTE_BLOCK,
        TRANSMITTER,
        RECEIVER,
        LEVER
    }

    private record TempoPoint(int tick, double ticksPerSecond, double startMillis) {
    }

    private record TempoTimeline(List<TempoPoint> points) {
        static TempoTimeline from(NbsSong song) {
            TreeMap<Integer, Double> changes = new TreeMap<>();
            changes.put(0, song.initialTempo() > 0 ? song.initialTempo() : 10.0);
            for (NbsSong.Note note : song.notes()) {
                if (song.isTempoChanger(note)) {
                    double tempo = Math.abs(note.pitchCents() / 15.0);
                    if (tempo >= 0.1) {
                        changes.put(note.tick(), tempo);
                    }
                }
            }

            List<TempoPoint> points = new ArrayList<>();
            int previousTick = 0;
            double previousTempo = changes.firstEntry().getValue();
            double millis = 0.0;
            for (Map.Entry<Integer, Double> change : changes.entrySet()) {
                if (change.getKey() > previousTick) {
                    millis += (change.getKey() - previousTick) * 1000.0 / previousTempo;
                }
                points.add(new TempoPoint(change.getKey(), change.getValue(), millis));
                previousTick = change.getKey();
                previousTempo = change.getValue();
            }
            return new TempoTimeline(List.copyOf(points));
        }

        double millisAt(int tick) {
            TempoPoint active = points.getFirst();
            for (int i = 1; i < points.size(); i++) {
                if (points.get(i).tick() > tick) {
                    break;
                }
                active = points.get(i);
            }
            return active.startMillis() + (tick - active.tick()) * 1000.0 / active.ticksPerSecond();
        }
    }

    private static final class Palette {
        private final Map<BlockStateSpec, Integer> ids = new LinkedHashMap<>();

        private Palette() {
            id("minecraft:air", Map.of());
        }

        int id(String name, Map<String, String> properties) {
            return ids.computeIfAbsent(new BlockStateSpec(name, Map.copyOf(properties)), ignored -> ids.size());
        }

        int size() {
            return ids.size();
        }

        ListTag toNbt() {
            ListTag list = new ListTag();
            ids.keySet().forEach(state -> {
                CompoundTag tag = new CompoundTag();
                tag.putString("Name", state.name());
                if (!state.properties().isEmpty()) {
                    CompoundTag properties = new CompoundTag();
                    state.properties().forEach(properties::putString);
                    tag.put("Properties", properties);
                }
                list.add(tag);
            });
            return list;
        }
    }

    private record BlockStateSpec(String name, Map<String, String> properties) {
    }

    public record ProjectionResult(Path output, int noteCount, long durationMs, long customInstrumentFallbacks,
            long clampedPitches, int sizeX, int sizeY, int sizeZ) {
    }
}
