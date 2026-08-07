package com.atemukesu.extendednoteblock.nbs;

import java.util.List;

public record NbsSong(
        int version,
        int vanillaInstrumentCount,
        int declaredLengthTicks,
        int layerCount,
        String name,
        String author,
        String originalAuthor,
        String description,
        double initialTempo,
        int timeSignature,
        LoopSettings loop,
        List<Note> notes,
        List<Layer> layers,
        List<CustomInstrument> customInstruments) {

    public NbsSong {
        notes = List.copyOf(notes);
        layers = List.copyOf(layers);
        customInstruments = List.copyOf(customInstruments);
    }

    public int lengthTicks() {
        return Math.max(declaredLengthTicks, notes.stream().mapToInt(Note::tick).max().orElse(0));
    }

    public boolean isTempoChanger(Note note) {
        int customIndex = note.instrument() - vanillaInstrumentCount;
        return customIndex >= 0
                && customIndex < customInstruments.size()
                && customInstruments.get(customIndex).name().equalsIgnoreCase("Tempo Changer");
    }

    public record Note(int tick, int layer, int instrument, int key, int velocity, int panning, int pitchCents) {
    }

    public record Layer(String name, boolean locked, int volume, int panning) {
        public static Layer defaults(int index) {
            return new Layer("Layer " + (index + 1), false, 100, 100);
        }
    }

    public record CustomInstrument(String name, String fileName, int key, boolean pressKey) {
    }

    public record LoopSettings(boolean enabled, int maxLoops, int startTick) {
        public static final LoopSettings NONE = new LoopSettings(false, 0, 0);
    }
}
