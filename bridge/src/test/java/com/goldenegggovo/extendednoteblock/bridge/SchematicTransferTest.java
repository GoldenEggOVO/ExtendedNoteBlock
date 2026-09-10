package com.goldenegggovo.extendednoteblock.bridge;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.*;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Note;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SchematicTransferTest {
 @Test void preservesEveryParameterAndTimeline() throws Exception {
  Pos p = new Pos(-20,100,40);
  Document d = new Document(List.of(new Entry(p,0,new Note(p,127,128,126,400,3600000,399,398,-32768),List.of()),new Entry(new Pos(1,2,3),3,null,List.of(new Tone(128,127,126,400,32767,3600000)))));
  assertEquals(d,SchematicTransfer.decodeDocument(SchematicTransfer.encodeDocument(d)));
  Pos target=new Pos(0,0,0); assertEquals(target,d.entries().getFirst().at(target).note().pos());
 }
 @Test void rejectsDuplicateAndInvalidMetadata() {
  Entry e=new Entry(new Pos(0,0,0),1,null,List.of());
  assertThrows(IllegalArgumentException.class,()->new Document(List.of(e,e)));
  assertThrows(IllegalArgumentException.class,()->new Entry(e.pos(),0,null,List.of()));
  assertThrows(IllegalArgumentException.class,()->new Tone(129,0,0,1,0,0));
 }
 @Test void assemblyRequiresOrderedCompleteSameSession() throws Exception {
  UUID id=UUID.randomUUID(); byte[] data=SchematicTransfer.encodeDocument(new Document(List.of())); Assembly a=new Assembly(id);
  assertThrows(IllegalArgumentException.class,()->a.add(new Part(id,1,data.length,new byte[]{0})));
  assertFalse(a.complete()); a.add(new Part(id,0,data.length,data)); assertTrue(a.complete()); assertEquals(new Document(List.of()),a.document());
  assertThrows(IllegalArgumentException.class,()->a.add(new Part(id,0,data.length,data)));
 }
 @Test void refusesTrailingAndOversizeData() throws Exception {
  byte[] valid=SchematicTransfer.encode(new Request(UUID.randomUUID(),List.of(new Box(new Pos(0,0,0),new Pos(4,4,4)))));
  assertInstanceOf(Request.class,SchematicTransfer.decode(valid));
  assertThrows(IOException.class,()->SchematicTransfer.decode(Arrays.copyOf(valid,valid.length+1)));
  assertThrows(IOException.class,()->SchematicTransfer.decodeDocument(new byte[SchematicTransfer.MAX_DOCUMENT_BYTES+1]));
 }
}
