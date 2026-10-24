package com.goldenegggovo.extendednoteblock.bridge;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class OptionalAudioPackTest {
 @TempDir Path dir;
 @Test void splitPreservesAssetsAndSeparatesOnlyEnbAudio() throws Exception {
  Class<?> type; try { type=Class.forName("com.goldenegggovo.extendednoteblock.bridge.OptionalAudioPack"); } catch(ClassNotFoundException e) { fail("Optional audio splitter is missing"); return; }
  Path full=dir.resolve("full.zip");
  var names=List.of("pack.mcmeta","assets/extendednoteblock/models/block/c.json","assets/extendednoteblock_listener/sounds.json","assets/extendednoteblock_listener/sounds/piano.ogg","assets/other/sounds/test.ogg");
  try(var out=new ZipOutputStream(Files.newOutputStream(full))) { for(String n:names) { out.putNextEntry(new ZipEntry(n));out.write(n.getBytes());out.closeEntry(); } }
  var split=type.getDeclaredMethod("split",Path.class,Path.class).invoke(null,full,dir.resolve("out"));
  Path base=(Path)split.getClass().getDeclaredMethod("base").invoke(split), audio=(Path)split.getClass().getDeclaredMethod("audio").invoke(split);
  try(var b=new ZipFile(base.toFile());var a=new ZipFile(audio.toFile())) { for(String n:names) { boolean sound=n.startsWith("assets/extendednoteblock_listener/");var z=sound?a:b;assertArrayEquals(n.getBytes(),z.getInputStream(z.getEntry(n)).readAllBytes());if(sound)assertNull(b.getEntry(n)); }assertNotNull(a.getEntry("pack.mcmeta")); }
  var again=type.getDeclaredMethod("split",Path.class,Path.class).invoke(null,full,dir.resolve("out")); assertEquals(split,again);
 }
 @Test void audioEditsDoNotInvalidateBaseAndBrokenZipIsRejected() throws Exception {
  Path source=dir.resolve("input.zip");
  OptionalAudioPack.Published first=null;
  for(int version=0;version<2;version++) {
   try(var out=new ZipOutputStream(Files.newOutputStream(source))) {
    for(String n:List.of("pack.mcmeta","assets/extendednoteblock/models/block/c.json","assets/extendednoteblock_listener/sounds.json","assets/extendednoteblock_listener/sounds/piano.ogg")) {
     out.putNextEntry(new ZipEntry(n));out.write((n+(n.endsWith("ogg")?version:0)).getBytes());out.closeEntry();
    }
   }
   var current=OptionalAudioPack.split(source,dir.resolve("split"));
   if(first==null)first=current;else {assertEquals(first.baseHash(),current.baseHash());assertNotEquals(first.audioHash(),current.audioHash());}
  }
  Files.write(source,new byte[]{1,2,3});assertThrows(java.io.IOException.class,()->OptionalAudioPack.split(source,dir.resolve("broken")));
 }
}
