package com.atemukesu.extendednoteblock.bridgeclient;

import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.*;
import net.minecraft.nbt.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reads the original Paper projection export, including 2.8.x metadata. */
public final class LitematicImportReader {
    private LitematicImportReader() { }

    public record Source(Pos transmitter, Pos receiver, List<Note> notes) {
        public Source { notes = List.copyOf(notes); }
        public Plan place(Pos destination, int rotation, int mirror) {
            UUID id = UUID.randomUUID();
            Begin begin = new Begin(id, destination,
                    ProjectionImport.transform(receiver, transmitter, destination, rotation, mirror), notes.size());
            Assembly assembly = new Assembly(begin);
            List<Note> transformed = notes.stream().map(n -> n.at(
                    ProjectionImport.transform(n.pos(), transmitter, destination, rotation, mirror))).toList();
            for (int offset = 0; offset < transformed.size(); offset += ProjectionImport.BATCH_SIZE) {
                assembly.add(new Batch(id, offset, transformed.subList(offset,
                        Math.min(transformed.size(), offset + ProjectionImport.BATCH_SIZE))));
            }
            return assembly.finish(id);
        }
    }

    public static Source read(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > 64L * 1024 * 1024) {
            throw new IOException("Choose a .litematic file smaller than 64 MiB");
        }
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(128L * 1024 * 1024));
            CompoundTag metadata = compound(root, "ExtendedNoteBlockBridge");
            if (integer(metadata, "FormatVersion") != 1
                    || !metadata.getString("Mode").orElse("").equals("paper-safe-carriers")) {
                throw new IOException("Unsupported ENB metadata; export again from Paper Client's NBS Workshop");
            }
            Pos transmitter = position(compound(metadata, "TransmitterPos"));
            Pos receiver = position(compound(metadata, "ProjectionReceiverPos"));
            if (!(metadata.get("Notes") instanceof ListTag entries) || entries.isEmpty()
                    || entries.size() > ProjectionImport.MAX_NOTES) throw new IOException("Invalid ENB note list");
            List<Note> notes = new ArrayList<>(entries.size());
            for (Tag tag : entries) {
                if (!(tag instanceof CompoundTag entry)) throw new IOException("Invalid ENB note entry");
                long delay = entry.getLong("DelayMs").orElseThrow(() -> new IllegalArgumentException("Missing DelayMs"));
                notes.add(new Note(position(entry), integer(entry, "MidiNote"), integer(entry, "Instrument"),
                        integer(entry, "Velocity"), integer(entry, "SustainTicks"), Math.toIntExact(delay),
                        optionalInteger(entry, "FadeInTicks", 0), optionalInteger(entry, "FadeOutTicks", 0),
                        integer(entry, "PitchCents")));
            }
            Source source = new Source(transmitter, receiver, notes);
            source.place(transmitter, 0, 0); // Also reject duplicate/overlapping/out-of-range source coordinates.
            return source;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            throw new IOException("Invalid ENB metadata: " + invalid.getMessage(), invalid);
        }
    }

    private static CompoundTag compound(CompoundTag parent, String key) throws IOException {
        if (parent.get(key) instanceof CompoundTag value) return value;
        throw new IOException("Missing " + key + "; select the original ENB Paper projection export (2.8.0 or newer)");
    }
    private static Pos position(CompoundTag tag) { return new Pos(integer(tag, "x"), integer(tag, "y"), integer(tag, "z")); }
    private static int integer(CompoundTag tag, String key) {
        return tag.getInt(key).orElseThrow(() -> new IllegalArgumentException("Missing integer: " + key));
    }
    private static int optionalInteger(CompoundTag tag, String key, int fallback) {
        return tag.contains(key) ? integer(tag, key) : fallback;
    }
}
