package com.goldenegggovo.extendednoteblock.bridge;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Deterministic archives: all non-listener resources stay byte-for-byte in the base pack. */
final class OptionalAudioPack {
    record Published(Path base, Path audio, String baseHash, String audioHash) {}
    static Published split(Path source, Path directory) throws IOException {
        EnbCombinedPack.verify(source);
        Files.createDirectories(directory);
        Path base = Files.createTempFile(directory, "base-", ".tmp");
        Path audio = Files.createTempFile(directory, "audio-", ".tmp");
        try {
            try (var zip = new ZipFile(source.toFile());
                 var b = new ZipOutputStream(Files.newOutputStream(base));
                 var a = new ZipOutputStream(Files.newOutputStream(audio))) {
                var entries = Collections.list(zip.entries());
                entries.sort(Comparator.comparing(ZipEntry::getName));
                Set<String> seen = new HashSet<>();
                for (var entry : entries) {
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    if (!seen.add(name)) throw new IOException("Duplicate pack entry: " + name);
                    boolean sound = name.equals("assets/extendednoteblock_listener/sounds.json")
                            || (name.startsWith("assets/extendednoteblock_listener/sounds/") && name.endsWith(".ogg"));
                    copy(zip, entry, sound ? a : b);
                    if (name.equals("pack.mcmeta")) copy(zip, entry, a);
                }
            }
            String bh = hash(base), ah = hash(audio);
            Path bp = publish(base, directory.resolve("base-" + bh + ".zip"));
            Path ap = publish(audio, directory.resolve("audio-" + ah + ".zip"));
            return new Published(bp, ap, bh, ah);
        } finally { Files.deleteIfExists(base); Files.deleteIfExists(audio); }
    }
    private static void copy(ZipFile zip, ZipEntry source, ZipOutputStream output) throws IOException {
        ZipEntry entry = new ZipEntry(source.getName()); entry.setTime(0);
        output.putNextEntry(entry);
        try (var in = zip.getInputStream(source)) { in.transferTo(output); }
        output.closeEntry();
    }
    private static Path publish(Path temp, Path target) throws IOException {
        if (Files.exists(target)) {
            if (!hash(temp).equals(hash(target))) throw new IOException("Immutable pack corrupted: " + target);
        } else Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }
    static String hash(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            try (var in = new DigestInputStream(Files.newInputStream(file), digest)) { in.transferTo(OutputStream.nullOutputStream()); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
