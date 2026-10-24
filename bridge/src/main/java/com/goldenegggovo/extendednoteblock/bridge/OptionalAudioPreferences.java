package com.goldenegggovo.extendednoteblock.bridge;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class OptionalAudioPreferences {
    private final Path file;
    private final Set<UUID> enabled = new HashSet<>();
    OptionalAudioPreferences(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) for (String line : Files.readAllLines(file)) {
            if (!line.isBlank()) enabled.add(UUID.fromString(line.trim()));
        }
    }
    boolean enabled(UUID id) { return enabled.contains(id); }
    void set(UUID id, boolean value) throws IOException {
        var updated = new HashSet<>(enabled);
        if (value) updated.add(id); else updated.remove(id);
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "audio-preferences-", ".tmp");
        try {
            Files.write(temp, updated.stream().map(UUID::toString).sorted().toList());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            enabled.clear(); enabled.addAll(updated);
        } finally { Files.deleteIfExists(temp); }
    }
}
