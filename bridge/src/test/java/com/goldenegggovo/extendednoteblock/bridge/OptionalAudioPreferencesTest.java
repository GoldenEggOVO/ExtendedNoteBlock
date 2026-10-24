package com.goldenegggovo.extendednoteblock.bridge;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class OptionalAudioPreferencesTest {
 @TempDir Path dir;
 @Test void defaultsOffAndPersistsExplicitChoice() throws Exception {
  Class<?> t;try{t=Class.forName("com.goldenegggovo.extendednoteblock.bridge.OptionalAudioPreferences");}catch(ClassNotFoundException e){fail("Optional audio preferences missing");return;}
  var c=t.getDeclaredConstructor(Path.class);var f=dir.resolve("prefs.txt");var p=c.newInstance(f);var id=UUID.randomUUID();var get=t.getDeclaredMethod("enabled",UUID.class);var set=t.getDeclaredMethod("set",UUID.class,boolean.class);
  assertEquals(false,get.invoke(p,id));set.invoke(p,id,true);assertEquals(true,get.invoke(c.newInstance(f),id));set.invoke(p,id,false);assertEquals(false,get.invoke(c.newInstance(f),id));
 }
}
