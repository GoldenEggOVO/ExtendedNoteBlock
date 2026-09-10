package com.goldenegggovo.extendednoteblock.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnbCombinedPackTest {
    @TempDir Path dir;
    Path pack(boolean sounds)throws IOException {
        Path path=dir.resolve("pack.zip");
        try(var zip=new ZipOutputStream(Files.newOutputStream(path))){
            for(String name:List.of("pack.mcmeta","assets/extendednoteblock/models/block/c.json","assets/extendednoteblock_listener/sounds.json")){
                if(!sounds && name.endsWith("sounds.json"))continue;
                zip.putNextEntry(new ZipEntry(name));zip.write("{}".getBytes());zip.closeEntry();
            }
        }return path;
    }
    @Test void acceptsCompletePackAndUsesActualBytes()throws Exception {
        var path=pack(true);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path))),EnbCombinedPack.verify(path));
    }
    @Test void rejectsPackWithoutListenerAudio()throws Exception {assertThrows(IOException.class,()->EnbCombinedPack.verify(pack(false)));}
    @Test void rejectsIncompleteDownload()throws Exception {
        var path=pack(true);byte[] bytes=Files.readAllBytes(path);Files.write(path,Arrays.copyOf(bytes,bytes.length/2));
        assertThrows(IOException.class,()->EnbCombinedPack.verify(path));
    }
    @Test void usesOnlyHostedPackMatchingGeneratedBytesAndPreservesSignedUrl() {
        String hash = "a".repeat(40);
        var correct = new net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData(
                "https://packs.example/ce.zip?signature=abc", UUID.randomUUID(), hash);
        var old = new net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData(
                "https://packs.example/old.zip", UUID.randomUUID(), "b".repeat(40));
        assertEquals(correct, EnbCombinedPack.selectDownload(List.of(old, correct), hash));
        assertNull(EnbCombinedPack.selectDownload(List.of(old), hash));
        assertNull(EnbCombinedPack.selectDownload(List.of(), hash));
    }
    @Test void rejectsInvalidHostedUrlEvenWithMatchingHash() {
        String hash = "a".repeat(40);
        var invalid = new net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData(
                "file:///private/ce.zip", UUID.randomUUID(), hash);
        assertNull(EnbCombinedPack.selectDownload(List.of(invalid), hash));
    }
}
