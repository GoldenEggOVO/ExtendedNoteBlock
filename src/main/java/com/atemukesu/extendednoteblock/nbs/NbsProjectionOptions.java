package com.atemukesu.extendednoteblock.nbs;

public record NbsProjectionOptions(
        int transpose,
        double speedMultiplier,
        double velocityMultiplier,
        int sustainTicks,
        int columns,
        OctaveRange octaveRange,
        boolean customInstrumentsEnabled,
        int customInstrumentFallback) {

    public NbsProjectionOptions {
        transpose = Math.max(-127, Math.min(127, transpose));
        speedMultiplier = Math.max(0.1, Math.min(20.0, speedMultiplier));
        velocityMultiplier = Math.max(0.0, Math.min(4.0, velocityMultiplier));
        sustainTicks = Math.max(1, Math.min(400, sustainTicks));
        columns = Math.max(4, Math.min(64, columns));
        octaveRange = octaveRange == null ? OctaveRange.SIX_OCTAVES : octaveRange;
        customInstrumentFallback = Math.max(0, Math.min(128, customInstrumentFallback));
    }

    public enum OctaveRange {
        TWO_OCTAVES(54, 78),
        SIX_OCTAVES(24, 95);

        private final int minMidi;
        private final int maxMidi;

        OctaveRange(int minMidi, int maxMidi) {
            this.minMidi = minMidi;
            this.maxMidi = maxMidi;
        }

        public int fit(int midiNote) {
            int fitted = midiNote;
            while (fitted < minMidi && fitted + 12 <= maxMidi) {
                fitted += 12;
            }
            while (fitted > maxMidi && fitted - 12 >= minMidi) {
                fitted -= 12;
            }
            return Math.max(minMidi, Math.min(maxMidi, fitted));
        }

        public OctaveRange next() {
            return this == TWO_OCTAVES ? SIX_OCTAVES : TWO_OCTAVES;
        }
    }
}
