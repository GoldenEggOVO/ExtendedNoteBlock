package com.atemukesu.extendednoteblock.nbs;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NbsReader {
    private static final long MAX_FILE_SIZE = 128L * 1024L * 1024L;
    private static final int MAX_STRING_BYTES = 1_048_576;
    private static final int MAX_NOTES = 2_000_000;
    private static final int MAX_SUPPORTED_VERSION = 6;

    private NbsReader() {
    }

    public static NbsSong read(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new IOException("Invalid NBS file size: " + size);
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            LittleEndianInput in = new LittleEndianInput(input);
            int firstShort = in.readUnsignedShort();
            int version;
            int vanillaInstrumentCount;
            int declaredLength;
            int layerCount;

            if (firstShort == 0) {
                version = in.readUnsignedByte();
                if (version < 1 || version > MAX_SUPPORTED_VERSION) {
                    throw new IOException("Unsupported NBS version: " + version);
                }
                vanillaInstrumentCount = in.readUnsignedByte();
                declaredLength = version >= 3 ? in.readUnsignedShort() : 0;
                layerCount = in.readUnsignedShort();
            } else {
                version = 0;
                vanillaInstrumentCount = 10;
                declaredLength = firstShort;
                layerCount = in.readUnsignedShort();
            }

            String name = in.readString();
            String author = in.readString();
            String originalAuthor = in.readString();
            String description = in.readString();
            double initialTempo = in.readUnsignedShort() / 100.0;
            in.readUnsignedByte(); // deprecated auto-save flag
            in.readUnsignedByte(); // deprecated auto-save interval
            int timeSignature = in.readUnsignedByte();
            for (int i = 0; i < 5; i++) {
                in.readInt(); // editor statistics
            }
            in.readString(); // source MIDI filename

            NbsSong.LoopSettings loop = NbsSong.LoopSettings.NONE;
            if (version >= 4) {
                loop = new NbsSong.LoopSettings(
                        in.readUnsignedByte() != 0,
                        in.readUnsignedByte(),
                        in.readUnsignedShort());
            }

            List<NbsSong.Note> notes = readNotes(in, version);
            List<NbsSong.Layer> layers = in.hasRemaining()
                    ? readLayers(in, version, layerCount)
                    : defaultLayers(layerCount);
            List<NbsSong.CustomInstrument> customInstruments = readCustomInstruments(in);

            return new NbsSong(version, vanillaInstrumentCount, declaredLength, layerCount,
                    name, author, originalAuthor, description, initialTempo, timeSignature,
                    loop, notes, layers, customInstruments);
        }
    }

    private static List<NbsSong.Note> readNotes(LittleEndianInput in, int version) throws IOException {
        List<NbsSong.Note> notes = new ArrayList<>();
        int tick = -1;

        while (true) {
            int tickJump = in.readUnsignedShort();
            if (tickJump == 0) {
                break;
            }
            tick += tickJump;
            int layer = -1;

            while (true) {
                int layerJump = in.readUnsignedShort();
                if (layerJump == 0) {
                    break;
                }
                layer += layerJump;

                int instrument = in.readUnsignedByte();
                int key = in.readUnsignedByte();
                int velocity = 100;
                int panning = 100;
                int pitch = 0;
                if (version >= 4) {
                    velocity = in.readUnsignedByte();
                    panning = in.readUnsignedByte();
                    pitch = in.readShort();
                }

                if (notes.size() >= MAX_NOTES) {
                    throw new IOException("NBS note count exceeds " + MAX_NOTES);
                }
                notes.add(new NbsSong.Note(tick, layer, instrument, key, velocity, panning, pitch));
            }
        }

        return notes;
    }

    private static List<NbsSong.Layer> readLayers(LittleEndianInput in, int version, int layerCount)
            throws IOException {
        List<NbsSong.Layer> layers = new ArrayList<>(layerCount);
        for (int i = 0; i < layerCount; i++) {
            String name = in.readString();
            boolean locked = version >= 4 && in.readUnsignedByte() != 0;
            int volume = Math.min(100, in.readUnsignedByte());
            int panning = version >= 2 ? in.readUnsignedByte() : 100;
            layers.add(new NbsSong.Layer(name, locked, volume, panning));
        }
        return layers;
    }

    private static List<NbsSong.Layer> defaultLayers(int layerCount) {
        List<NbsSong.Layer> layers = new ArrayList<>(layerCount);
        for (int i = 0; i < layerCount; i++) {
            layers.add(NbsSong.Layer.defaults(i));
        }
        return layers;
    }

    private static List<NbsSong.CustomInstrument> readCustomInstruments(LittleEndianInput in) throws IOException {
        if (!in.hasRemaining()) {
            return List.of();
        }

        int count = in.readUnsignedByte();
        List<NbsSong.CustomInstrument> instruments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            instruments.add(new NbsSong.CustomInstrument(
                    in.readString(),
                    in.readString(),
                    in.readUnsignedByte(),
                    in.readUnsignedByte() != 0));
        }
        return instruments;
    }

    private static final class LittleEndianInput {
        private final InputStream input;

        private LittleEndianInput(InputStream input) {
            this.input = input;
        }

        int readUnsignedByte() throws IOException {
            int value = input.read();
            if (value < 0) {
                throw new EOFException("Unexpected end of NBS file");
            }
            return value;
        }

        int readUnsignedShort() throws IOException {
            return readUnsignedByte() | (readUnsignedByte() << 8);
        }

        short readShort() throws IOException {
            return (short) readUnsignedShort();
        }

        int readInt() throws IOException {
            return readUnsignedByte()
                    | (readUnsignedByte() << 8)
                    | (readUnsignedByte() << 16)
                    | (readUnsignedByte() << 24);
        }

        String readString() throws IOException {
            int length = readInt();
            if (length < 0 || length > MAX_STRING_BYTES) {
                throw new IOException("Invalid NBS string length: " + length);
            }
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) {
                throw new EOFException("Unexpected end of NBS string");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        boolean hasRemaining() throws IOException {
            return input.available() > 0;
        }
    }
}
