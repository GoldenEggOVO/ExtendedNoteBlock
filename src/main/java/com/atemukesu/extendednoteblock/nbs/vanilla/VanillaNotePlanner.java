package com.atemukesu.extendednoteblock.nbs.vanilla;

import com.atemukesu.extendednoteblock.nbs.NbsSong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class VanillaNotePlanner {
    private VanillaNotePlanner() {
    }

    public static Plan plan(NbsSong song, VanillaExportOptions options) {
        List<NbsSong.Note> sorted = song.notes().stream()
                .filter(note -> !song.isTempoChanger(note))
                .filter(note -> note.velocity() > 0 && layerVolume(song, note.layer()) > 0)
                .sorted(Comparator.comparingInt(NbsSong.Note::tick).thenComparingInt(NbsSong.Note::layer))
                .toList();
        Map<Integer, List<PlannedNote>> byStep = new LinkedHashMap<>();
        int capacity = options.target() == VanillaExportOptions.Target.DATAPACK
                ? Integer.MAX_VALUE
                : options.distribution() == VanillaExportOptions.Distribution.TWO_SIDED ? 30 : 15;
        TempoTimeline timeline = TempoTimeline.from(song);
        int overflow = 0;
        for (NbsSong.Note note : sorted) {
            double seconds = timeline.secondsAt(note.tick())
                    / (options.musicSpeedPercent() / 100.0);
            int requestedStep = Math.max(0, (int) Math.round(seconds * options.stepsPerSecond()));
            PlannedNote planned = createNote(song, note, options);
            int step = requestedStep;
            while (byStep.computeIfAbsent(step, ignored -> new ArrayList<>()).size() >= capacity) {
                step++;
                overflow++;
            }
            byStep.get(step).add(planned);
        }
        List<Event> events = byStep.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new Event(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new Plan(events, sorted.size(), overflow,
                events.isEmpty() ? 0 : events.get(events.size() - 1).step());
    }

    private static PlannedNote createNote(NbsSong song, NbsSong.Note note, VanillaExportOptions options) {
        int midi = note.key() + 21 + options.transpose();
        int vanilla;
        if (options.pitchMode() == VanillaExportOptions.PitchMode.CLAMP) {
            vanilla = Math.max(0, Math.min(24, midi - 54));
        } else {
            vanilla = midi - 54;
            while (vanilla < 0) vanilla += 12;
            while (vanilla > 24) vanilla -= 12;
        }
        return new PlannedNote(resolveInstrument(song, note.instrument()), vanilla,
                note.velocity(), note.layer());
    }

    private static VanillaInstrument resolveInstrument(NbsSong song, int instrumentId) {
        if (instrumentId < song.vanillaInstrumentCount()) {
            return VanillaInstrument.fromNbs(instrumentId);
        }
        int customIndex = instrumentId - song.vanillaInstrumentCount();
        if (customIndex < 0 || customIndex >= song.customInstruments().size()) {
            return VanillaInstrument.HARP;
        }
        String name = song.customInstruments().get(customIndex).name();
        for (Map.Entry<Integer, String> entry
                : com.atemukesu.extendednoteblock.map.InstrumentMap.GM_INSTRUMENT_ID_TO_NAME.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return VanillaInstrument.fromNbs(entry.getKey() + 16);
            }
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("drum") || lower.contains("percussion")) return VanillaInstrument.BASEDRUM;
        if (lower.contains("guitar")) return VanillaInstrument.GUITAR;
        if (lower.contains("bass")) return VanillaInstrument.BASS;
        if (lower.contains("flute") || lower.contains("pipe") || lower.contains("whistle")) return VanillaInstrument.FLUTE;
        if (lower.contains("bell")) return VanillaInstrument.BELL;
        if (lower.contains("xylophone") || lower.contains("marimba")) return VanillaInstrument.XYLOPHONE;
        if (lower.contains("banjo")) return VanillaInstrument.BANJO;
        return VanillaInstrument.HARP;
    }

    private static int layerVolume(NbsSong song, int layer) {
        return layer >= 0 && layer < song.layers().size() ? song.layers().get(layer).volume() : 100;
    }

    private record TempoPoint(int tick, double ticksPerSecond, double startSeconds) {
    }

    private record TempoTimeline(List<TempoPoint> points) {
        static TempoTimeline from(NbsSong song) {
            TreeMap<Integer, Double> changes = new TreeMap<>();
            changes.put(0, song.initialTempo() > 0 ? song.initialTempo() : 10.0);
            for (NbsSong.Note note : song.notes()) {
                if (song.isTempoChanger(note)) {
                    double tempo = Math.abs(note.pitchCents() / 15.0);
                    if (tempo >= 0.1) changes.put(note.tick(), tempo);
                }
            }
            List<TempoPoint> points = new ArrayList<>();
            int previousTick = 0;
            double previousTempo = changes.firstEntry().getValue();
            double seconds = 0.0;
            for (Map.Entry<Integer, Double> change : changes.entrySet()) {
                if (change.getKey() > previousTick) {
                    seconds += (change.getKey() - previousTick) / previousTempo;
                }
                points.add(new TempoPoint(change.getKey(), change.getValue(), seconds));
                previousTick = change.getKey();
                previousTempo = change.getValue();
            }
            return new TempoTimeline(List.copyOf(points));
        }

        double secondsAt(int tick) {
            TempoPoint active = points.getFirst();
            for (int i = 1; i < points.size(); i++) {
                if (points.get(i).tick() > tick) break;
                active = points.get(i);
            }
            return active.startSeconds() + (tick - active.tick()) / active.ticksPerSecond();
        }
    }

    public record Plan(List<Event> events, int noteCount, int shiftedNotes, int durationSteps) {
    }
    public record Event(int step, List<PlannedNote> notes) {
    }
    public record PlannedNote(VanillaInstrument instrument, int vanillaPitch, int velocity, int layer) {
    }
}
