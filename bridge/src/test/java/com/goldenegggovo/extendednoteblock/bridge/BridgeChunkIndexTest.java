package com.goldenegggovo.extendednoteblock.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BridgeChunkIndexTest {
    @Test void cachedPollingKeysTrackChangesAcrossWorldsAndSameSizedReplacements() {
        var index = new BridgeChunkIndex();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        index.add("a", a, 0, 0, true);
        index.add("b", b, 0, 0, true);
        String[] original = index.pollingKeys();
        assertEquals(Set.of("a", "b"), Set.of(original));
        index.add("a", a, 0, 0, true);
        assertSame(original, index.pollingKeys(), "Unchanged membership must reuse the polling snapshot");
        index.add("a", a, 0, 0, false);
        assertEquals(Set.of("b"), Set.of(index.pollingKeys()));
        index.add("a", a, 0, 0, true);
        assertEquals(Set.of("a", "b"), Set.of(index.pollingKeys()));
        index.setLoaded(a, 0, 0, false);
        assertEquals(Set.of("b"), Set.of(index.pollingKeys()));
        index.setLoaded(a, 0, 0, true);
        assertEquals(Set.of("a", "b"), Set.of(index.pollingKeys()));
        index.remove("a");
        index.add("c", a, 0, 0, true);
        assertEquals(Set.of("b", "c"), Set.of(index.pollingKeys()));
        index.add("c", a, 16, 0, false);
        index.setLoaded(a, 0, 0, true);
        assertEquals(Set.of("b"), Set.of(index.pollingKeys()));
        index.setLoaded(a, 1, 0, true);
        assertEquals(Set.of("b", "c"), Set.of(index.pollingKeys()));
        index.unloadWorld(b);
        assertEquals(Set.of("c"), Set.of(index.pollingKeys()));
        index.clear();
        assertEquals(Set.of(), Set.of(index.pollingKeys()));
        index.add("d", a, 0, 0, true);
        index.setLoaded(a, 1, 0, true);
        assertEquals(Set.of("d"), Set.of(index.pollingKeys()));
    }

    @Test void negativeCoordinatesAndWorldsStayIndependent() {
        var index = new BridgeChunkIndex();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        index.add("a", a, -1, -17, false);
        index.add("b", b, -1, -17, true);
        assertEquals(Set.of("a"), index.setLoaded(a, -1, -2, true));
        assertEquals(Set.of("a", "b"), index.activeKeys());
        index.setLoaded(a, -1, -2, false);
        assertEquals(Set.of("b"), index.activeKeys());
        index.remove("a");
        assertTrue(index.worldKeys(a).isEmpty());
        assertTrue(index.setLoaded(a, -1, -2, true).isEmpty());
    }

    @Test void rebuildingAndMovingAnEntryDoNotRetainStalePositions() {
        var index = new BridgeChunkIndex();
        UUID world = UUID.randomUUID();
        index.add("key", world, 0, 0, true);
        index.add("key", world, 32, 0, false);
        assertTrue(index.activeKeys().isEmpty());
        assertTrue(index.setLoaded(world, 0, 0, true).isEmpty());
        index.setLoaded(world, 2, 0, true);
        assertEquals(Set.of("key"), index.activeKeys());
        index.unloadWorld(world);
        assertTrue(index.activeKeys().isEmpty());
        assertEquals(Set.of("key"), index.worldKeys(world));
        index.clear();
        assertTrue(index.worldKeys(world).isEmpty());
    }
}
