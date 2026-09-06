package com.goldenegggovo.extendednoteblock.bridge;

/** Resolves ENB notes to events included in the vanilla-client listener pack. */
final class ListenerSoundResolver {
    static final String NAMESPACE = "extendednoteblock_listener";
    static final int VOICE_ALIASES = 8;
    static final int ANCHOR_STEP = 6;
    static final int HIGHEST_ANCHOR = 126;
    static final int LOWEST_DRUM = 35;
    static final int HIGHEST_DRUM = 81;

    private ListenerSoundResolver() {
    }

    static Resolved resolve(int instrumentId, int midiNote, int pitchCents, int voiceSequence) {
        int instrument = clamp(instrumentId, 0, 128);
        int voice = Math.floorMod(voiceSequence, VOICE_ALIASES);
        if (instrument == 128) {
            int drum = clamp(midiNote, LOWEST_DRUM, HIGHEST_DRUM);
            return new Resolved(
                    NAMESPACE + ":notes.128." + drum + ".v" + voice,
                    1.0f,
                    drum,
                    voice);
        }

        int note = clamp(midiNote, 0, 127);
        double effectiveNote = note + pitchCents / 100.0;
        int anchor = clamp((int) Math.round(effectiveNote / ANCHOR_STEP) * ANCHOR_STEP, 0, HIGHEST_ANCHOR);
        float pitch = (float) Math.pow(2.0, (effectiveNote - anchor) / 12.0);
        // Pitch cents can intentionally move beyond MIDI 0-127. Vanilla cannot
        // represent those last edge cases, so keep the packet inside its limit.
        pitch = Math.max(0.5f, Math.min(2.0f, pitch));
        return new Resolved(
                NAMESPACE + ":notes." + instrument + "." + anchor + ".v" + voice,
                pitch,
                anchor,
                voice);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record Resolved(String event, float pitch, int anchor, int voice) {
    }
}
