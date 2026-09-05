package com.goldenegggovo.extendednoteblock.bridge;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Fixed-width bridge_note_save wire format, decoded before accessing a world. */
record NoteSaveRequest(int x, int y, int z, int note, int instrument, int velocity,
                       int sustain, int delay, int fadeIn, int fadeOut) {
    static NoteSaveRequest decode(byte[] message) throws IOException {
        if (message == null || message.length != Long.BYTES + 7 * Integer.BYTES) {
            throw new IOException("Expected a 36-byte note settings payload");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            long pos = in.readLong();
            return new NoteSaveRequest((int) (pos >> 38), (int) (pos << 52 >> 52),
                    (int) (pos << 26 >> 38), clamp(in.readInt(), 0, 127),
                    clamp(in.readInt(), 0, 128), clamp(in.readInt(), 0, 127),
                    clamp(in.readInt(), 1, 400), clamp(in.readInt(), 0, 3_600_000),
                    clamp(in.readInt(), 0, 400), clamp(in.readInt(), 0, 400));
        }
    }

    boolean isWithinReach(double playerX, double playerY, double playerZ,
                          int minHeight, int maxHeight, double reach) {
        double dx = playerX - (x + 0.5);
        double dy = playerY - (y + 0.5);
        double dz = playerZ - (z + 0.5);
        return y >= minHeight && y < maxHeight && reach >= 0
                && dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
