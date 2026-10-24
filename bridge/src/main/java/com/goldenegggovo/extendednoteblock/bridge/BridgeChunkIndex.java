package com.goldenegggovo.extendednoteblock.bridge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runtime working set only; never holds chunks or changes the persisted ledger. */
final class BridgeChunkIndex {
    private record Chunk(UUID world, int x, int z) {}
    private final Map<Chunk, Set<String>> chunks = new HashMap<>();
    private final Map<String, Chunk> positions = new HashMap<>();
    private final Set<String> active = new HashSet<>();
    private String[] pollingKeys;

    void add(String key, UUID world, int x, int z, boolean loaded) {
        Chunk chunk = new Chunk(world, x >> 4, z >> 4);
        Chunk old = positions.get(key);
        if (old != null && !old.equals(chunk)) remove(key);
        positions.put(key, chunk);
        chunks.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(key);
        if (loaded ? active.add(key) : active.remove(key)) pollingKeys = null;
    }

    void remove(String key) {
        Chunk chunk = positions.remove(key);
        if (active.remove(key)) pollingKeys = null;
        if (chunk == null) return;
        Set<String> keys = chunks.get(chunk);
        keys.remove(key);
        if (keys.isEmpty()) chunks.remove(chunk);
    }

    Set<String> setLoaded(UUID world, int x, int z, boolean loaded) {
        Set<String> keys = chunks.getOrDefault(new Chunk(world, x, z), Set.of());
        if (loaded ? active.addAll(keys) : active.removeAll(keys)) pollingKeys = null;
        return keys;
    }

    Set<String> worldKeys(UUID world) {
        Set<String> result = new HashSet<>();
        for (var entry : chunks.entrySet()) {
            if (entry.getKey().world().equals(world)) result.addAll(entry.getValue());
        }
        return result;
    }

    Set<String> unloadWorld(UUID world) {
        Set<String> keys = worldKeys(world);
        if (active.removeAll(keys)) pollingKeys = null;
        return keys;
    }

    Set<String> activeKeys() { return active; }

    String[] pollingKeys() {
        if (pollingKeys == null) pollingKeys = active.toArray(String[]::new);
        return pollingKeys;
    }

    void clear() {
        chunks.clear();
        positions.clear();
        active.clear();
        pollingKeys = null;
    }
}
