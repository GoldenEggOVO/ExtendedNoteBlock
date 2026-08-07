package com.atemukesu.extendednoteblock.nbs;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class NbsWriter {
    private static final int VERSION = 5;

    private NbsWriter() {
    }

    public static void write(NbsSong song, Path output) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<NbsSong.Note> notes = song.notes().stream()
                .sorted(Comparator.comparingInt(NbsSong.Note::tick).thenComparingInt(NbsSong.Note::layer))
                .toList();
        int layerCount = Math.max(1, Math.max(song.layerCount(),
                notes.stream().mapToInt(NbsSong.Note::layer).max().orElse(0) + 1));

        try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(output))) {
            LittleEndianOutput out = new LittleEndianOutput(stream);
            out.writeShort(0);
            out.writeByte(VERSION);
            out.writeByte(Math.max(16, song.vanillaInstrumentCount()));
            out.writeShort(clamp(song.lengthTicks(), 0, 0xFFFF));
            out.writeShort(clamp(layerCount, 1, 0xFFFF));
            out.writeString(song.name());
            out.writeString(song.author());
            out.writeString(song.originalAuthor());
            out.writeString(song.description());
            out.writeShort(clamp((int) Math.round(song.initialTempo() * 100.0), 1, 0xFFFF));
            out.writeByte(0);
            out.writeByte(10);
            out.writeByte(clamp(song.timeSignature(), 1, 16));
            for (int i = 0; i < 5; i++) {
                out.writeInt(0);
            }
            out.writeString("");
            out.writeByte(song.loop().enabled() ? 1 : 0);
            out.writeByte(clamp(song.loop().maxLoops(), 0, 0xFF));
            out.writeShort(clamp(song.loop().startTick(), 0, 0xFFFF));

            writeNotes(out, notes);
            for (int layer = 0; layer < layerCount; layer++) {
                NbsSong.Layer value = layer < song.layers().size()
                        ? song.layers().get(layer)
                        : NbsSong.Layer.defaults(layer);
                out.writeString(value.name());
                out.writeByte(value.locked() ? 1 : 0);
                out.writeByte(clamp(value.volume(), 0, 100));
                out.writeByte(clamp(value.panning(), 0, 200));
            }

            int customCount = Math.min(0xFF, song.customInstruments().size());
            out.writeByte(customCount);
            for (int i = 0; i < customCount; i++) {
                NbsSong.CustomInstrument instrument = song.customInstruments().get(i);
                out.writeString(instrument.name());
                out.writeString(instrument.fileName());
                out.writeByte(clamp(instrument.key(), 0, 87));
                out.writeByte(instrument.pressKey() ? 1 : 0);
            }
        }
    }

    private static void writeNotes(LittleEndianOutput out, List<NbsSong.Note> notes) throws IOException {
        int previousTick = -1;
        int index = 0;
        while (index < notes.size()) {
            int tick = notes.get(index).tick();
            int tickJump = tick - previousTick;
            if (tickJump <= 0 || tickJump > 0xFFFF) {
                throw new IOException("NBS tick delta is out of range: " + tickJump);
            }
            out.writeShort(tickJump);
            previousTick = tick;

            int previousLayer = -1;
            while (index < notes.size() && notes.get(index).tick() == tick) {
                NbsSong.Note note = notes.get(index++);
                int layerJump = note.layer() - previousLayer;
                if (layerJump <= 0 || layerJump > 0xFFFF) {
                    throw new IOException("NBS layer delta is out of range: " + layerJump);
                }
                out.writeShort(layerJump);
                previousLayer = note.layer();
                out.writeByte(clamp(note.instrument(), 0, 0xFF));
                out.writeByte(clamp(note.key(), 0, 87));
                out.writeByte(clamp(note.velocity(), 0, 100));
                out.writeByte(clamp(note.panning(), 0, 200));
                out.writeShort(note.pitchCents());
            }
            out.writeShort(0);
        }
        out.writeShort(0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class LittleEndianOutput {
        private final OutputStream output;

        private LittleEndianOutput(OutputStream output) {
            this.output = output;
        }

        void writeByte(int value) throws IOException {
            output.write(value & 0xFF);
        }

        void writeShort(int value) throws IOException {
            writeByte(value);
            writeByte(value >>> 8);
        }

        void writeInt(int value) throws IOException {
            writeByte(value);
            writeByte(value >>> 8);
            writeByte(value >>> 16);
            writeByte(value >>> 24);
        }

        void writeString(String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeInt(bytes.length);
            output.write(bytes);
        }
    }
}
