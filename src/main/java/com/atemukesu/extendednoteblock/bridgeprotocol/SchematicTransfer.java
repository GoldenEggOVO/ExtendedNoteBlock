package com.atemukesu.extendednoteblock.bridgeprotocol;

import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Note;
import java.io.*;
import java.util.*;

/** Registry-independent, bounded metadata sidecar for Litematica selections. */
public final class SchematicTransfer {
    public static final String CHANNEL = "extendednoteblock:schematic";
    public static final String STATUS_CHANNEL = "extendednoteblock:schematic_result";
    public static final int PART_SIZE = 16_000, MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;
    public static final int MAX_ENTRIES = 75_000, MAX_TONES = 150_000, MAX_BOXES = 128;
    private SchematicTransfer() { }
    public record Box(Pos min, Pos max) {
        public Box { Objects.requireNonNull(min); Objects.requireNonNull(max);
            check(min.x() <= max.x() && min.y() <= max.y() && min.z() <= max.z() && min.near(max), "Invalid selection bounds"); }
        public boolean contains(Pos p) { return p.x() >= min.x() && p.x() <= max.x() && p.y() >= min.y() && p.y() <= max.y() && p.z() >= min.z() && p.z() <= max.z(); }
    }
    public record Tone(int instrument, int midi, int velocity, int sustain, int pitchCents, long delayMs) {
        public Tone { check(instrument >= 0 && instrument <= 128 && midi >= 0 && midi <= 127 && velocity >= 0 && velocity <= 127
                && sustain >= 1 && sustain <= 400 && pitchCents >= Short.MIN_VALUE && pitchCents <= Short.MAX_VALUE
                && delayMs >= 0 && delayMs <= 3_600_000, "Invalid timeline tone"); }
    }
    public record Entry(Pos pos, int type, Note note, List<Tone> timeline) {
        public Entry { Objects.requireNonNull(pos); timeline = List.copyOf(timeline);
            check(type >= 0 && type <= 3, "Invalid object type");
            check(type == 0 ? note != null && pos.equals(note.pos()) : note == null, "Invalid note metadata");
            check(timeline.size() <= MAX_TONES && (type == 3 || timeline.isEmpty()), "Invalid projection timeline"); }
        public Entry at(Pos p) { return new Entry(p, type, note == null ? null : note.at(p), timeline); }
    }
    public record Document(List<Entry> entries) {
        public Document { entries = List.copyOf(entries); check(entries.size() <= MAX_ENTRIES, "Too many ENB objects");
            Set<Pos> positions = new HashSet<>(); long tones = 0;
            for (Entry e : entries) { check(positions.add(e.pos()), "Duplicate ENB position"); tones += e.timeline().size(); }
            check(tones <= MAX_TONES, "Too many timeline tones"); }
    }
    public sealed interface Packet permits Request, Part, Result { UUID id(); }
    public record Request(UUID id, List<Box> boxes) implements Packet {
        public Request { Objects.requireNonNull(id); boxes = List.copyOf(boxes); check(!boxes.isEmpty() && boxes.size() <= MAX_BOXES, "Invalid selection count"); }
    }
    public record Part(UUID id, int offset, int total, byte[] data) implements Packet {
        public Part { Objects.requireNonNull(id); data = data.clone();
            check(total > 0 && total <= MAX_DOCUMENT_BYTES && offset >= 0 && data.length > 0 && data.length <= PART_SIZE
                    && (long) offset + data.length <= total, "Invalid document part"); }
        @Override public byte[] data() { return data.clone(); }
    }
    public record Result(UUID id, boolean success, String message) implements Packet {
        public Result { Objects.requireNonNull(id); check(message != null && message.length() <= 384, "Invalid result message"); }
    }
    public static final class Assembly {
        private final UUID id; private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(); private int total = -1;
        public Assembly(UUID id) { this.id = Objects.requireNonNull(id); }
        public void add(Part part) {
            check(id.equals(part.id()) && part.offset() == bytes.size() && (total == -1 || total == part.total()), "Mismatched or out-of-order document part");
            total = part.total(); bytes.writeBytes(part.data);
        }
        public boolean complete() { return total >= 0 && bytes.size() == total; }
        public Document document() throws IOException { check(complete(), "Incomplete document"); return decodeDocument(bytes.toByteArray()); }
    }
    public static byte[] encode(Packet packet) {
        return write(out -> { out.writeByte(1); out.writeByte(packet instanceof Request ? 0 : packet instanceof Part ? 1 : 2);
            out.writeLong(packet.id().getMostSignificantBits()); out.writeLong(packet.id().getLeastSignificantBits());
            switch (packet) {
                case Request r -> { out.writeInt(r.boxes().size()); for (Box b : r.boxes()) { pos(out,b.min()); pos(out,b.max()); } }
                case Part p -> { out.writeInt(p.offset()); out.writeInt(p.total()); out.writeInt(p.data.length); out.write(p.data); }
                case Result r -> { out.writeBoolean(r.success()); out.writeUTF(r.message()); }
            }
        }, PART_SIZE + 1024);
    }
    public static Packet decode(byte[] bytes) throws IOException {
        try (DataInputStream in = input(bytes, PART_SIZE + 1024)) {
            version(in); int kind = in.readUnsignedByte(); UUID id = new UUID(in.readLong(), in.readLong());
            Packet packet = switch (kind) {
                case 0 -> { int n = count(in,MAX_BOXES); List<Box> boxes = new ArrayList<>(); for (int i=0;i<n;i++) boxes.add(new Box(pos(in),pos(in))); yield new Request(id,boxes); }
                case 1 -> { int offset=in.readInt(), total=in.readInt(), n=count(in,PART_SIZE); byte[] data = new byte[n]; in.readFully(data); yield new Part(id,offset,total,data); }
                case 2 -> new Result(id,in.readBoolean(),in.readUTF());
                default -> throw new IOException("Unknown schematic packet");
            }; end(in); return packet;
        } catch (IllegalArgumentException e) { throw new IOException(e.getMessage(),e); }
    }
    public static byte[] encodeDocument(Document document) {
        return write(out -> { out.writeByte(1); out.writeInt(document.entries().size());
            for (Entry e : document.entries()) { pos(out,e.pos()); out.writeByte(e.type());
                if (e.note()!=null) { Note n=e.note(); for (int v : new int[]{n.midi(),n.instrument(),n.velocity(),n.sustain(),n.delayMs(),n.fadeIn(),n.fadeOut(),n.pitchCents()}) out.writeInt(v); }
                out.writeInt(e.timeline().size()); for (Tone t : e.timeline()) { out.writeInt(t.instrument()); out.writeInt(t.midi()); out.writeInt(t.velocity()); out.writeInt(t.sustain()); out.writeInt(t.pitchCents()); out.writeLong(t.delayMs()); }
            }
        },MAX_DOCUMENT_BYTES);
    }
    public static Document decodeDocument(byte[] bytes) throws IOException {
        try (DataInputStream in = input(bytes,MAX_DOCUMENT_BYTES)) {
            version(in); int n=count(in,MAX_ENTRIES), totalTones=0; List<Entry> entries=new ArrayList<>();
            for(int i=0;i<n;i++) { Pos p=pos(in); int type=in.readUnsignedByte();
                Note note=type==0 ? new Note(p,in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt()) : null;
                int size=count(in,MAX_TONES); totalTones+=size; check(totalTones<=MAX_TONES,"Too many timeline tones"); List<Tone> tones=new ArrayList<>();
                for(int j=0;j<size;j++) tones.add(new Tone(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readLong()));
                entries.add(new Entry(p,type,note,tones));
            } end(in); return new Document(entries);
        } catch (IllegalArgumentException e) { throw new IOException(e.getMessage(),e); }
    }
    private static void pos(DataOutputStream out,Pos p) throws IOException { out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z()); }
    private static Pos pos(DataInputStream in) throws IOException { return new Pos(in.readInt(),in.readInt(),in.readInt()); }
    private static int count(DataInputStream in,int max) throws IOException { int n=in.readInt(); check(n>=0 && n<=max,"Invalid count"); return n; }
    private static void version(DataInputStream in) throws IOException { check(in.readUnsignedByte()==1,"Unsupported schematic version"); }
    private static void end(DataInputStream in) throws IOException { check(in.available()==0,"Trailing schematic data"); }
    private static DataInputStream input(byte[] bytes,int max) throws IOException { if(bytes==null || bytes.length>max) throw new IOException("Schematic data too large"); return new DataInputStream(new ByteArrayInputStream(bytes)); }
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static byte[] write(Writer writer,int max) { try { ByteArrayOutputStream bytes=new ByteArrayOutputStream(); try(DataOutputStream out=new DataOutputStream(bytes)){ writer.write(out); } check(bytes.size()<=max,"Schematic data too large"); return bytes.toByteArray(); } catch(IOException e){ throw new UncheckedIOException(e); } }
    private static void check(boolean ok,String message) { if(!ok) throw new IllegalArgumentException(message); }
}
