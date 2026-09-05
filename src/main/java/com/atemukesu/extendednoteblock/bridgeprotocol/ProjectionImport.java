package com.atemukesu.extendednoteblock.bridgeprotocol;

import java.io.*;
import java.util.*;

/** Shared, registry-independent wire format for restoring already pasted carriers. */
public final class ProjectionImport {
    public static final String CHANNEL = "extendednoteblock:bridge_import";
    public static final String STATUS_CHANNEL = "extendednoteblock:bridge_import_status";
    public static final int VERSION = 1;
    public static final int MAX_NOTES = 75_000;
    public static final int BATCH_SIZE = 128;
    public static final int MAX_PACKET_BYTES = 16_384;
    public static final int MAX_SPAN = 2048;
    public static final int READY = 0, RECEIVED = 1, VALIDATING = 2, COMPLETE = 3, REJECTED = 4;

    private ProjectionImport() { }

    public record Pos(int x, int y, int z) {
        public Pos {
            check(Math.abs((long) x) <= 30_000_000 && Math.abs((long) z) <= 30_000_000
                    && y >= -2048 && y <= 2047, "Coordinates outside Minecraft limits");
        }
        public boolean near(Pos other) {
            return Math.abs((long) x - other.x) <= MAX_SPAN
                    && Math.abs((long) y - other.y) <= MAX_SPAN
                    && Math.abs((long) z - other.z) <= MAX_SPAN;
        }
        public String display() { return x + ", " + y + ", " + z; }
    }

    public record Note(Pos pos, int midi, int instrument, int velocity, int sustain,
                       int delayMs, int fadeIn, int fadeOut, int pitchCents) {
        public Note {
            Objects.requireNonNull(pos);
            check(midi >= 0 && midi <= 127, "MIDI must be 0-127");
            check(instrument >= 0 && instrument <= 128, "Instrument must be 0-128");
            check(velocity >= 0 && velocity <= 127, "Velocity must be 0-127");
            check(sustain >= 1 && sustain <= 400, "Sustain must be 1-400 ticks");
            check(delayMs >= 0 && delayMs <= 3_600_000, "Delay exceeds one hour");
            check(fadeIn >= 0 && fadeIn <= 400 && fadeOut >= 0 && fadeOut <= 400, "Invalid fade length");
            check(pitchCents >= Short.MIN_VALUE && pitchCents <= Short.MAX_VALUE, "Invalid NBS pitch cents");
        }
        public Note at(Pos destination) {
            return new Note(destination, midi, instrument, velocity, sustain, delayMs, fadeIn, fadeOut, pitchCents);
        }
    }

    public sealed interface Packet permits Begin, Batch, Finish, Cancel { UUID id(); }
    public record Begin(UUID id, Pos transmitter, Pos receiver, int total) implements Packet {
        public Begin {
            Objects.requireNonNull(id); Objects.requireNonNull(transmitter); Objects.requireNonNull(receiver);
            check(total > 0 && total <= MAX_NOTES, "Invalid projection note count");
            check(Math.abs((long) transmitter.x - receiver.x) + Math.abs((long) transmitter.y - receiver.y)
                    + Math.abs((long) transmitter.z - receiver.z) == 1, "Projection receiver must adjoin transmitter");
        }
    }
    public record Batch(UUID id, int offset, List<Note> notes) implements Packet {
        public Batch {
            notes = List.copyOf(notes);
            check(offset >= 0 && offset < MAX_NOTES, "Invalid batch offset");
            check(!notes.isEmpty() && notes.size() <= BATCH_SIZE, "Invalid batch length");
        }
    }
    public record Finish(UUID id) implements Packet { }
    public record Cancel(UUID id) implements Packet { }
    public record Status(UUID id, int stage, int processed, int total, String message) {
        public Status {
            check(stage >= READY && stage <= REJECTED, "Invalid import status");
            check(processed >= 0 && processed <= MAX_NOTES && total >= 0 && total <= MAX_NOTES, "Invalid progress");
            check(message != null && message.length() <= 384, "Status message too long");
        }
    }
    public record Plan(Begin begin, List<Note> notes) {
        public Plan { notes = List.copyOf(notes); check(notes.size() == begin.total, "Incomplete projection"); }
    }

    /** Mirror original X/Z axes, then rotate clockwise around the transmitter. */
    public static Pos transform(Pos point, Pos sourceAnchor, Pos destinationAnchor, int quarterTurns, int mirror) {
        check(quarterTurns >= 0 && quarterTurns <= 3 && mirror >= 0 && mirror <= 3, "Invalid transform");
        long x = (long) point.x - sourceAnchor.x, y = (long) point.y - sourceAnchor.y;
        long z = (long) point.z - sourceAnchor.z;
        if ((mirror & 1) != 0) x = -x;
        if ((mirror & 2) != 0) z = -z;
        for (int i = 0; i < quarterTurns; i++) { long previous = x; x = -z; z = previous; }
        return new Pos(Math.toIntExact(destinationAnchor.x + x), Math.toIntExact(destinationAnchor.y + y),
                Math.toIntExact(destinationAnchor.z + z));
    }

    /** Bounded, ordered assembly. A rejected batch never partially changes this state. */
    public static final class Assembly {
        private final Begin begin;
        private final List<Note> notes = new ArrayList<>();
        private final Set<Pos> positions = new HashSet<>();
        public Assembly(Begin begin) {
            this.begin = begin;
            positions.add(begin.transmitter); positions.add(begin.receiver);
        }
        public int size() { return notes.size(); }
        public Begin begin() { return begin; }
        public void add(Batch batch) {
            check(begin.id.equals(batch.id), "Import session mismatch");
            check(batch.offset == notes.size(), "Out-of-order or repeated batch");
            check(notes.size() + batch.notes.size() <= begin.total, "Too many notes");
            Set<Pos> incoming = new HashSet<>();
            for (Note note : batch.notes) {
                check(note.pos.near(begin.transmitter), "Note is too far from transmitter");
                check(!positions.contains(note.pos) && incoming.add(note.pos), "Duplicate or overlapping note position");
            }
            positions.addAll(incoming); notes.addAll(batch.notes);
        }
        public Plan finish(UUID id) {
            check(begin.id.equals(id), "Import session mismatch");
            return new Plan(begin, notes);
        }
    }

    public static byte[] encode(Packet packet) {
        return write(out -> {
            out.writeByte(VERSION);
            out.writeByte(packet instanceof Begin ? 0 : packet instanceof Batch ? 1 : packet instanceof Finish ? 2 : 3);
            writeId(out, packet.id());
            switch (packet) {
                case Begin b -> { writePos(out, b.transmitter); writePos(out, b.receiver); out.writeInt(b.total); }
                case Batch b -> { out.writeInt(b.offset); out.writeInt(b.notes.size()); for (Note n : b.notes) writeNote(out, n); }
                case Finish ignored -> { }
                case Cancel ignored -> { }
            }
        });
    }

    public static Packet decode(byte[] bytes) throws IOException {
        try (DataInputStream in = input(bytes)) {
            requireVersion(in);
            int operation = in.readUnsignedByte(); UUID id = readId(in);
            Packet packet = switch (operation) {
                case 0 -> new Begin(id, readPos(in), readPos(in), in.readInt());
                case 1 -> {
                    int offset = in.readInt(), count = in.readInt();
                    check(count > 0 && count <= BATCH_SIZE, "Invalid batch length");
                    List<Note> notes = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) notes.add(readNote(in));
                    yield new Batch(id, offset, notes);
                }
                case 2 -> new Finish(id);
                case 3 -> new Cancel(id);
                default -> throw new IOException("Unknown import operation");
            };
            check(in.available() == 0, "Trailing import data");
            return packet;
        } catch (IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
    }

    public static byte[] encodeStatus(Status status) {
        return write(out -> {
            out.writeByte(VERSION); writeId(out, status.id); out.writeByte(status.stage);
            out.writeInt(status.processed); out.writeInt(status.total); out.writeUTF(status.message);
        });
    }
    public static Status decodeStatus(byte[] bytes) throws IOException {
        try (DataInputStream in = input(bytes)) {
            requireVersion(in);
            Status result = new Status(readId(in), in.readUnsignedByte(), in.readInt(), in.readInt(), in.readUTF());
            check(in.available() == 0, "Trailing status data");
            return result;
        } catch (IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
    }

    private static void writeNote(DataOutputStream out, Note n) throws IOException {
        writePos(out, n.pos);
        for (int value : new int[]{n.midi, n.instrument, n.velocity, n.sustain, n.delayMs, n.fadeIn, n.fadeOut, n.pitchCents}) out.writeInt(value);
    }
    private static Note readNote(DataInputStream in) throws IOException {
        return new Note(readPos(in), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                in.readInt(), in.readInt(), in.readInt());
    }
    private static void writePos(DataOutputStream out, Pos p) throws IOException { out.writeInt(p.x); out.writeInt(p.y); out.writeInt(p.z); }
    private static Pos readPos(DataInputStream in) throws IOException { return new Pos(in.readInt(), in.readInt(), in.readInt()); }
    private static void writeId(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID readId(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void requireVersion(DataInputStream in) throws IOException { if (in.readUnsignedByte() != VERSION) throw new IOException("Unsupported import protocol version"); }
    private static DataInputStream input(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_PACKET_BYTES) throw new IOException("Import packet too large");
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static byte[] write(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) { writer.write(out); }
            check(bytes.size() <= MAX_PACKET_BYTES, "Import packet too large");
            return bytes.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
