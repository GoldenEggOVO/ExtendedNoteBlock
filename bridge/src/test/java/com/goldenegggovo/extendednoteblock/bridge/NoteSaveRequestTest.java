package com.goldenegggovo.extendednoteblock.bridge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class NoteSaveRequestTest {
    private byte[] payload(int x, int y, int z, int... settings) {
        long pos = ((long) x & 0x3ffffffL) << 38
                | ((long) z & 0x3ffffffL) << 12 | ((long) y & 0xfffL);
        ByteBuffer buffer = ByteBuffer.allocate(36).putLong(pos);
        for (int value : settings) buffer.putInt(value);
        return buffer.array();
    }

    @Test
    void roundTripsSignedPositionsAndAllMidiNotes() throws IOException {
        for (int note = 0; note <= 127; note++) {
            var request = NoteSaveRequest.decode(payload(-30_000_000, -64, 29_999_999,
                    note, 40, 100, 20, 3560, 2, 3));
            assertEquals(new NoteSaveRequest(-30_000_000, -64, 29_999_999,
                    note, 40, 100, 20, 3560, 2, 3), request);
        }
    }

    @Test
    void rejectsTruncatedAndTrailingData() {
        for (int length : new int[]{0, 7, 8, 35, 37, 1000}) {
            assertThrows(IOException.class, () -> NoteSaveRequest.decode(new byte[length]));
        }
        assertThrows(IOException.class, () -> NoteSaveRequest.decode(null));
    }

    @Test
    void boundsClientSuppliedSettings() throws IOException {
        var request = NoteSaveRequest.decode(payload(0, 64, 0,
                -1, 999, 999, 0, Integer.MAX_VALUE, -1, 999));
        assertEquals(new NoteSaveRequest(0, 64, 0, 0, 128, 127, 1, 3_600_000, 0, 400), request);
    }

    @Test
    void rejectsRemoteAndOutOfWorldTargetsWithoutWorldAccess() throws IOException {
        var request = NoteSaveRequest.decode(payload(0, 64, 0, 60, 0, 100, 20, 0, 0, 3));
        assertTrue(request.isWithinReach(8.5, 64.5, .5, -64, 320, 8));
        assertFalse(request.isWithinReach(8.51, 64.5, .5, -64, 320, 8));
        assertFalse(request.isWithinReach(30_000_000, 64.5, .5, -64, 320, 8));
        assertFalse(request.isWithinReach(.5, 64.5, .5, 65, 320, 8));
        assertFalse(request.isWithinReach(.5, 64.5, .5, -64, 64, 8));
        assertFalse(request.isWithinReach(Double.NaN, 64.5, .5, -64, 320, 8));
    }
}
