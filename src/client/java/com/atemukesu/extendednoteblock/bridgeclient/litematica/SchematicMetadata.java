package com.atemukesu.extendednoteblock.bridgeclient.litematica;

import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.Document;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SchematicMetadata {
    public static final String KEY = "ExtendedNoteBlockSchematic";
    private SchematicMetadata() { }
    public static Map<String, Document> read(CompoundData root) throws IOException {
        Map<String, Document> result = new LinkedHashMap<>();
        if (!root.containsLenient(KEY)) return result;
        if (!root.contains(KEY, 10)) throw new IOException("Invalid ENB schematic metadata");
        CompoundData regions = root.getCompound(KEY);
        if (regions.size() > SchematicTransfer.MAX_BOXES) throw new IOException("Too many ENB regions");
        long bytes = 0;
        for (String name : regions.getKeys()) {
            if (!regions.contains(name, 7)) throw new IOException("Invalid ENB region data");
            byte[] data = regions.getByteArray(name);
            bytes += data.length;
            if (bytes > SchematicTransfer.MAX_DOCUMENT_BYTES) throw new IOException("ENB metadata too large");
            result.put(name, SchematicTransfer.decodeDocument(data));
        }
        return result;
    }
    public static void write(CompoundData root, Map<String, Document> documents) {
        if (documents.isEmpty()) return;
        if (documents.size() > SchematicTransfer.MAX_BOXES) throw new IllegalArgumentException("Too many ENB regions");
        CompoundData regions = new CompoundData();
        long bytes = 0;
        for (var entry : documents.entrySet()) {
            byte[] data = SchematicTransfer.encodeDocument(entry.getValue());
            bytes += data.length;
            if (bytes > SchematicTransfer.MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("ENB metadata too large");
            regions.putByteArray(entry.getKey(), data);
        }
        root.put(KEY, regions);
    }
}
