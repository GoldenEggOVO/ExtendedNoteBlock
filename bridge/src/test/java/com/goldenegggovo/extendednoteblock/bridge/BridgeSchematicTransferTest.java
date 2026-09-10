package com.goldenegggovo.extendednoteblock.bridge;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.*;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BridgeSchematicTransferTest {
 ServerMock server; WorldMock world; JavaPlugin plugin; PlayerMock player; BridgeSchematicTransfer transfer;
 int commits, captures; boolean accept=true;
 @BeforeEach void setup(){ server=MockBukkit.mock(); world=server.addSimpleWorld("world"); plugin=MockBukkit.createMockPlugin(); player=server.addPlayer(); player.setOp(true); player.teleport(new Location(world,0,100,0)); world.loadChunk(0,0);
 transfer=new BridgeSchematicTransfer(plugin,new BridgeSchematicTransfer.Target(){
  public Document capture(World w,List<Box> boxes){captures++;return new Document(List.of());}
  public boolean accepts(World w,Entry e){return accept;}
  public boolean commit(World w,Document d){commits++; return true;}
 }); }
 @AfterEach void cleanup(){transfer.close(); MockBukkit.unmock();}
 void upload(Document d){byte[] bytes=SchematicTransfer.encodeDocument(d); transfer.receive(player,SchematicTransfer.encode(new Part(UUID.randomUUID(),0,bytes.length,bytes)));}
 Document document(){return new Document(List.of(new Entry(new Pos(0,100,0),1,null,List.of()),new Entry(new Pos(1,100,0),2,null,List.of())));}
 @Test void rejectsEntireDocumentBeforeCommitWhenAnyCarrierFails(){accept=false; upload(document()); assertEquals(0,commits);}
 @Test void commitsOnlyAfterEveryPositionPasses(){upload(document()); assertEquals(1,commits);}
 @Test void deniesPermissionAndUnloadedChunks(){player.setOp(false);upload(document());assertEquals(0,commits);player.setOp(true);upload(new Document(List.of(new Entry(new Pos(1000,100,0),1,null,List.of()))));assertEquals(0,commits);}
 @Test void competingSessionCannotReplaceAnActiveUpload(){
  byte[] bytes=SchematicTransfer.encodeDocument(document());UUID id=UUID.randomUUID();
  transfer.receive(player,SchematicTransfer.encode(new Part(id,0,bytes.length,Arrays.copyOf(bytes,2))));
  upload(document());assertEquals(0,commits);
  transfer.receive(player,SchematicTransfer.encode(new Part(id,2,bytes.length,Arrays.copyOfRange(bytes,2,bytes.length))));assertEquals(1,commits);
 }
 @Test void worldChangeInvalidatesPendingUpload(){
  byte[] bytes=SchematicTransfer.encodeDocument(document());UUID id=UUID.randomUUID();
  transfer.receive(player,SchematicTransfer.encode(new Part(id,0,bytes.length,Arrays.copyOf(bytes,2))));
  player.teleport(new Location(server.addSimpleWorld("other"),0,100,0));
  transfer.receive(player,SchematicTransfer.encode(new Part(id,2,bytes.length,Arrays.copyOfRange(bytes,2,bytes.length))));assertEquals(0,commits);
 }
 @Test void validSelectionCapturesWithoutCommitting(){
  transfer.receive(player,SchematicTransfer.encode(new Request(UUID.randomUUID(),List.of(new Box(new Pos(0,99,0),new Pos(2,101,2))))));
  assertEquals(1,captures);server.getScheduler().performOneTick();assertEquals(0,commits);
 }
 @Test void unloadedSelectionIsRejectedBeforeCapture(){
  transfer.receive(player,SchematicTransfer.encode(new Request(UUID.randomUUID(),List.of(new Box(new Pos(0,99,0),new Pos(64,101,64))))));assertEquals(0,captures);
 }
 Request request(){return new Request(UUID.randomUUID(),List.of(new Box(new Pos(0,99,0),new Pos(2,101,2))));}
 void expireCooldown() throws Exception {var field=BridgeSchematicTransfer.class.getDeclaredField("nextSession");field.setAccessible(true);field.setLong(transfer,0);}
 @Test void samePlayerCanRestartOutgoingExportAfterCooldown() throws Exception {
  transfer.receive(player,SchematicTransfer.encode(request()));assertEquals(1,captures);
  expireCooldown();transfer.receive(player,SchematicTransfer.encode(request()));assertEquals(2,captures);
  transfer.receive(player,SchematicTransfer.encode(request()));assertEquals(2,captures,"Replacement must reset the one-second cooldown");
 }
 @Test void anotherPlayerCannotReplaceOutgoingExport() throws Exception {
  transfer.receive(player,SchematicTransfer.encode(request()));expireCooldown();
  var other=server.addPlayer();other.setOp(true);other.teleport(player.getLocation());
  transfer.receive(other,SchematicTransfer.encode(request()));assertEquals(1,captures);
 }
 @Test void requestCannotReplaceUploadOrExportFromAnotherWorld() throws Exception {
  byte[] bytes=SchematicTransfer.encodeDocument(document());UUID id=UUID.randomUUID();
  transfer.receive(player,SchematicTransfer.encode(new Part(id,0,bytes.length,Arrays.copyOf(bytes,2))));expireCooldown();
  transfer.receive(player,SchematicTransfer.encode(request()));assertEquals(0,captures);
  transfer.receive(player,SchematicTransfer.encode(new Part(id,2,bytes.length,Arrays.copyOfRange(bytes,2,bytes.length))));assertEquals(1,commits);
  expireCooldown();transfer.receive(player,SchematicTransfer.encode(request()));assertEquals(1,captures);
  player.teleport(new Location(server.addSimpleWorld("new-world"),0,100,0));expireCooldown();
  transfer.receive(player,SchematicTransfer.encode(request()));assertEquals(1,captures);
 }
}
