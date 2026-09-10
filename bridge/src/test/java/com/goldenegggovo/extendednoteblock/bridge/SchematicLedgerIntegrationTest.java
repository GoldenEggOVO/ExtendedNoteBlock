package com.goldenegggovo.extendednoteblock.bridge;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.*;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Note;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SchematicLedgerIntegrationTest {
 ExtendedNoteBlockBridge plugin; WorldMock world; EnbCraftEngine ce;
 @BeforeEach void setup(){var server=MockBukkit.mock();world=server.addSimpleWorld("copy");plugin=MockBukkit.load(ExtendedNoteBlockBridge.class);ce=mock(EnbCraftEngine.class);plugin.craftEngine=ce;
  when(ce.acceptsCopy(any(),any())).thenReturn(true);when(ce.placeCopy(any(),any(),anyInt())).thenReturn(true);world.loadChunk(0,0);
 }
 @AfterEach void cleanup(){MockBukkit.unmock();}
 Document source(){Pos p=new Pos(1,100,1);return new Document(List.of(new Entry(p,0,new Note(p,127,128,99,400,3560,2,3,-123),List.of()),new Entry(new Pos(2,100,1),2,null,List.of()),new Entry(new Pos(3,100,1),3,null,List.of(new Tone(12,0,70,2,20,0),new Tone(128,127,100,400,-32000,3599000)))));}
 Object invoke(String name,Class<?>[] types,Object...args) throws Exception {Method m=ExtendedNoteBlockBridge.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(plugin,args);}
 boolean restore(Document d) throws Exception{return (boolean)invoke("restoreSchematic",new Class[]{World.class,Document.class},world,d);}
 Document capture() throws Exception{return (Document)invoke("captureSchematic",new Class[]{World.class,List.class},world,List.of(new Box(new Pos(0,99,0),new Pos(4,101,2))));}
 @Test void roundTripsStandaloneNoteWirelessReceiverAndFullTimelineThroughPersistence() throws Exception {
  Document d=source();assertTrue(restore(d)); assertEquals(new HashSet<>(d.entries()),new HashSet<>(capture().entries()));
  invoke("loadObjects",new Class[]{});invoke("loadNotes",new Class[]{});invoke("loadProjections",new Class[]{});
  assertEquals(new HashSet<>(d.entries()),new HashSet<>(capture().entries()));
 }
 @Test void failedConversionRollsBackPhysicalBlocksAndLeavesLedgerUntouched() throws Exception {
  var first=world.getBlockAt(1,100,1);var second=world.getBlockAt(2,100,1);first.setType(Material.NOTE_BLOCK);second.setType(Material.REDSTONE_BLOCK);
  when(ce.placeCopy(any(),any(),anyInt())).thenAnswer(call->{org.bukkit.block.Block b=call.getArgument(0);b.setType(Material.STONE);return b.getX()!=2;});
  assertFalse(restore(source()));assertEquals(Material.NOTE_BLOCK,first.getType());assertEquals(Material.REDSTONE_BLOCK,second.getType());
  assertTrue(plugin.objects.isEmpty());assertTrue(plugin.notes.isEmpty());assertTrue(plugin.projectionNotes.isEmpty());
 }
 @Test void rejectsMismatchedCarrierBeforeChangingAnyMetadata() throws Exception {when(ce.acceptsCopy(any(),any())).thenReturn(false);assertFalse(restore(source()));verify(ce,never()).placeCopy(any(),any(),anyInt());assertTrue(plugin.objects.isEmpty());}
 @Test void failedPersistenceRetainsPendingSaveAndRetriesAfterDiskRecovers() throws Exception {
  // This test drives persistence timers only; MockBukkit does not implement redstone power queries.
  var logic=ExtendedNoteBlockBridge.class.getDeclaredField("logicTask");logic.setAccessible(true);((org.bukkit.scheduler.BukkitTask)logic.get(plugin)).cancel();
  var blocked=plugin.getDataFolder().toPath().resolve("notes.yml");java.nio.file.Files.createDirectory(blocked);var obstacle=blocked.resolve("keep");java.nio.file.Files.writeString(obstacle,"blocked");
  assertFalse(restore(source()));var field=ExtendedNoteBlockBridge.class.getDeclaredField("pendingSaves");field.setAccessible(true);
  assertTrue(((Set<?>)field.get(plugin)).contains("notes"));
  java.nio.file.Files.delete(obstacle);java.nio.file.Files.delete(blocked);MockBukkit.getMock().getScheduler().performTicks(11);
  assertTrue(java.nio.file.Files.isRegularFile(blocked));assertTrue(((Set<?>)field.get(plugin)).isEmpty());
 }
}
