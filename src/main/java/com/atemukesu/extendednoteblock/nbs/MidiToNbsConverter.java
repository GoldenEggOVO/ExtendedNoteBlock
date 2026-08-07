package com.atemukesu.extendednoteblock.nbs;

import com.atemukesu.extendednoteblock.map.InstrumentMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

public final class MidiToNbsConverter {
    private static final int TICKS_PER_SECOND = 20;
    private static final int MAX_NOTES = 2_000_000;

    private MidiToNbsConverter() {
    }

    public static boolean supports(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mid") || name.endsWith(".midi");
    }

    public static ConversionResult convert(Path input, Path songsDirectory) throws IOException {
        try {
            Sequence sequence = MidiSystem.getSequence(input.toFile());
            String baseName = sanitizeFileName(stripExtension(input.getFileName().toString()));
            NbsSong song = convertSequence(sequence, baseName);
            Files.createDirectories(songsDirectory);
            Path output = uniqueOutput(songsDirectory, baseName + "_converted");
            NbsWriter.write(song, output);
            return new ConversionResult(song, output);
        } catch (InvalidMidiDataException exception) {
            throw new IOException("Invalid MIDI file", exception);
        }
    }

    static NbsSong convertSequence(Sequence sequence, String fallbackName) throws IOException {
        List<OrderedEvent> events = collectEvents(sequence);
        TempoMap tempoMap = TempoMap.from(sequence, events);
        int[] programs = new int[16];
        int[] panning = new int[16];
        Arrays.fill(panning, 64);
        Map<Integer, Integer> customInstrumentIndexes = new LinkedHashMap<>();
        List<RawNote> rawNotes = new ArrayList<>();
        String songName = findSongName(events, fallbackName);

        for (OrderedEvent event : events) {
            MidiMessage message = event.event().getMessage();
            if (!(message instanceof ShortMessage shortMessage)) {
                continue;
            }
            int channel = shortMessage.getChannel();
            if (shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                programs[channel] = shortMessage.getData1();
                continue;
            }
            if (shortMessage.getCommand() == ShortMessage.CONTROL_CHANGE && shortMessage.getData1() == 10) {
                panning[channel] = shortMessage.getData2();
                continue;
            }
            if (shortMessage.getCommand() != ShortMessage.NOTE_ON || shortMessage.getData2() <= 0) {
                continue;
            }
            if (rawNotes.size() >= MAX_NOTES) {
                throw new IOException("MIDI note count exceeds " + MAX_NOTES);
            }

            int midi = shortMessage.getData1();
            if (midi < 21 || midi > 108) {
                continue;
            }
            int gmInstrument = channel == 9 ? 128 : programs[channel];
            int customIndex = customInstrumentIndexes.computeIfAbsent(gmInstrument,
                    ignored -> customInstrumentIndexes.size());
            int tick = Math.max(0, (int) Math.round(tempoMap.secondsAt(event.event().getTick()) * TICKS_PER_SECOND));
            int velocity = Math.max(1, Math.min(100, (int) Math.round(shortMessage.getData2() * 100.0 / 127.0)));
            int nbsPanning = Math.max(0, Math.min(200, (int) Math.round(panning[channel] * 200.0 / 127.0)));
            rawNotes.add(new RawNote(tick, 16 + customIndex, midi - 21, velocity, nbsPanning, event.order()));
        }
        if (rawNotes.isEmpty()) {
            throw new IOException("MIDI contains no notes in the supported piano range");
        }

        rawNotes.sort(Comparator.comparingInt(RawNote::tick).thenComparingLong(RawNote::order));
        List<NbsSong.Note> notes = new ArrayList<>(rawNotes.size());
        int currentTick = -1;
        int layer = 0;
        int maximumLayer = 0;
        for (RawNote note : rawNotes) {
            if (note.tick() != currentTick) {
                currentTick = note.tick();
                layer = 0;
            }
            notes.add(new NbsSong.Note(note.tick(), layer, note.instrument(), note.key(),
                    note.velocity(), note.panning(), 0));
            maximumLayer = Math.max(maximumLayer, layer);
            layer++;
        }

        List<NbsSong.Layer> layers = new ArrayList<>(maximumLayer + 1);
        for (int i = 0; i <= maximumLayer; i++) {
            layers.add(new NbsSong.Layer("MIDI Voice " + (i + 1), false, 100, 100));
        }
        List<NbsSong.CustomInstrument> customInstruments = new ArrayList<>(customInstrumentIndexes.size());
        customInstrumentIndexes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> customInstruments.add(new NbsSong.CustomInstrument(
                        InstrumentMap.GM_INSTRUMENT_ID_TO_NAME.getOrDefault(entry.getKey(), "GM " + entry.getKey()),
                        "", 45, false)));
        int length = notes.stream().mapToInt(NbsSong.Note::tick).max().orElse(0) + 1;
        return new NbsSong(5, 16, length, layers.size(), songName, "ExtendedNoteBlock", "",
                "Converted from MIDI", TICKS_PER_SECOND, 4, NbsSong.LoopSettings.NONE,
                notes, layers, customInstruments);
    }

    private static List<OrderedEvent> collectEvents(Sequence sequence) {
        List<OrderedEvent> events = new ArrayList<>();
        long order = 0;
        Track[] tracks = sequence.getTracks();
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            Track track = tracks[trackIndex];
            for (int eventIndex = 0; eventIndex < track.size(); eventIndex++) {
                events.add(new OrderedEvent(track.get(eventIndex), order++, priority(track.get(eventIndex).getMessage())));
            }
        }
        events.sort(Comparator.comparingLong((OrderedEvent value) -> value.event().getTick())
                .thenComparingInt(OrderedEvent::priority)
                .thenComparingLong(OrderedEvent::order));
        return events;
    }

    private static int priority(MidiMessage message) {
        if (message instanceof MetaMessage meta && meta.getType() == 0x51) {
            return 0;
        }
        if (message instanceof ShortMessage shortMessage) {
            if (shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE
                    || shortMessage.getCommand() == ShortMessage.CONTROL_CHANGE) {
                return 1;
            }
            if (shortMessage.getCommand() == ShortMessage.NOTE_ON) {
                return 2;
            }
        }
        return 3;
    }

    private static String findSongName(List<OrderedEvent> events, String fallback) {
        for (OrderedEvent event : events) {
            if (event.event().getMessage() instanceof MetaMessage meta && meta.getType() == 0x03) {
                String name = new String(meta.getData(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!name.isBlank()) {
                    return name;
                }
            }
        }
        return fallback;
    }

    private static Path uniqueOutput(Path directory, String baseName) {
        Path candidate = directory.resolve(baseName + ".nbs");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(baseName + "_" + suffix++ + ".nbs");
        }
        return candidate;
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String sanitizeFileName(String value) {
        String sanitized = value.replaceAll("[<>:\"/\\\\|?*]", "_").trim();
        return sanitized.isBlank() ? "converted_midi" : sanitized;
    }

    public record ConversionResult(NbsSong song, Path output) {
    }

    private record RawNote(int tick, int instrument, int key, int velocity, int panning, long order) {
    }

    private record OrderedEvent(MidiEvent event, long order, int priority) {
    }

    private static final class TempoMap {
        private final Sequence sequence;
        private final long[] ticks;
        private final double[] seconds;
        private final int[] microsPerQuarter;

        private TempoMap(Sequence sequence, long[] ticks, double[] seconds, int[] microsPerQuarter) {
            this.sequence = sequence;
            this.ticks = ticks;
            this.seconds = seconds;
            this.microsPerQuarter = microsPerQuarter;
        }

        static TempoMap from(Sequence sequence, List<OrderedEvent> events) {
            if (sequence.getDivisionType() != Sequence.PPQ) {
                return new TempoMap(sequence, new long[0], new double[0], new int[0]);
            }
            List<TempoEvent> tempoEvents = new ArrayList<>();
            tempoEvents.add(new TempoEvent(0, 500_000));
            for (OrderedEvent event : events) {
                if (event.event().getMessage() instanceof MetaMessage meta
                        && meta.getType() == 0x51 && meta.getData().length == 3) {
                    byte[] data = meta.getData();
                    int micros = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                    if (micros > 0) {
                        if (!tempoEvents.isEmpty()
                                && tempoEvents.getLast().tick() == event.event().getTick()) {
                            tempoEvents.set(tempoEvents.size() - 1, new TempoEvent(event.event().getTick(), micros));
                        } else {
                            tempoEvents.add(new TempoEvent(event.event().getTick(), micros));
                        }
                    }
                }
            }

            long[] ticks = new long[tempoEvents.size()];
            double[] seconds = new double[tempoEvents.size()];
            int[] micros = new int[tempoEvents.size()];
            for (int i = 0; i < tempoEvents.size(); i++) {
                TempoEvent event = tempoEvents.get(i);
                ticks[i] = event.tick();
                micros[i] = event.microsPerQuarter();
                if (i > 0) {
                    seconds[i] = seconds[i - 1] + (ticks[i] - ticks[i - 1])
                            * micros[i - 1] / (sequence.getResolution() * 1_000_000.0);
                }
            }
            return new TempoMap(sequence, ticks, seconds, micros);
        }

        double secondsAt(long tick) {
            if (sequence.getDivisionType() != Sequence.PPQ) {
                return tick / (sequence.getDivisionType() * sequence.getResolution());
            }
            int index = Arrays.binarySearch(ticks, tick);
            if (index < 0) {
                index = -index - 2;
            }
            index = Math.max(0, index);
            return seconds[index] + (tick - ticks[index]) * microsPerQuarter[index]
                    / (sequence.getResolution() * 1_000_000.0);
        }
    }

    private record TempoEvent(long tick, int microsPerQuarter) {
    }
}
