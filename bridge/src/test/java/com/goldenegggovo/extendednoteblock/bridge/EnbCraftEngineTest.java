package com.goldenegggovo.extendednoteblock.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.goldenegggovo.extendednoteblock.bridge.ExtendedNoteBlockBridge.BridgeItemType.*;
import java.nio.file.Files;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;

class EnbCraftEngineTest {
    private ExtendedNoteBlockBridge plugin;
    private Block block;
    @BeforeEach void setup() {
        var server=MockBukkit.mock();
        block=server.addSimpleWorld("ce").getBlockAt(0,64,0);
        plugin=MockBukkit.load(ExtendedNoteBlockBridge.class);
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}

    @Test void acceptsOnlyMatchingVanillaCarriersAndRefusesForeignCustomIdentity() {
        var ce=new EnbCraftEngine(plugin);
        try(var api=mockStatic(CraftEngineBlocks.class)) {
            block.setType(Material.NOTE_BLOCK);
            assertTrue(ce.acceptsCopy(block,EXTENDED_NOTE_BLOCK));
            assertFalse(ce.acceptsCopy(block,GLOBAL_REDSTONE_RECEIVER));
            block.setType(Material.REDSTONE_BLOCK);
            assertTrue(ce.acceptsCopy(block,GLOBAL_REDSTONE_RECEIVER));
            var custom=mock(ImmutableBlockState.class,RETURNS_DEEP_STUBS);
            when(custom.owner().value().id()).thenReturn(Key.of("foreign:note"));
            api.when(()->CraftEngineBlocks.getCustomBlockState(block)).thenReturn(custom);
            block.setType(Material.NOTE_BLOCK);
            assertFalse(ce.acceptsCopy(block,EXTENDED_NOTE_BLOCK));
            when(custom.owner().value().id()).thenReturn(Key.of("enb:extended_note_block"));
            assertTrue(ce.acceptsCopy(block,EXTENDED_NOTE_BLOCK));
            assertFalse(ce.placeCopy(block,EXTENDED_NOTE_BLOCK,60),"Definitions are not ready");
        }
    }

    @Test void automaticMigrationDoesNotTouchForeignCraftEngineBlocksAtStaleLedgerCoordinates() throws Exception {
        var ce=new EnbCraftEngine(plugin);
        var ready=EnbCraftEngine.class.getDeclaredField("ready"); ready.setAccessible(true); ready.set(ce,true);
        var journal=mock(EnbMigrationJournal.class);
        var journalField=EnbCraftEngine.class.getDeclaredField("journal"); journalField.setAccessible(true); journalField.set(ce,journal);
        block.setType(Material.NOTE_BLOCK);
        try(var api=mockStatic(CraftEngineBlocks.class)) {
            var foreign=mock(ImmutableBlockState.class,RETURNS_DEEP_STUBS);
            when(foreign.owner().value().id()).thenReturn(Key.of("foreign:note"));
            api.when(()->CraftEngineBlocks.getCustomBlockState(block)).thenReturn(foreign);
            block.getWorld().loadChunk(0,0);
            assertNotNull(plugin.getLoadedBlock(plugin.key(block)));
            ce.beginTick();
            ce.sync(plugin.key(block),EXTENDED_NOTE_BLOCK,new ExtendedNoteBlockBridge.RenderObjectState(0,false,0));
            verifyNoInteractions(journal);
        }
    }

    @Test void migrationRequiresDurableJournalAndNeverOverwritesTheFirstBackup() throws Exception {
        var data=plugin.getDataFolder().toPath();
        Files.writeString(data.resolve("notes.yml"),"original notes");
        try(var journal=new EnbMigrationJournal(plugin)) {
            assertFalse(journal.record("ce,0,64,0","minecraft:note_block"));
            assertNull(journal.original("ce,0,64,0"));
        }
        Files.writeString(data.resolve("notes.yml"),"new notes");
        try(var journal=new EnbMigrationJournal(plugin)) {
            assertEquals("minecraft:note_block",journal.original("ce,0,64,0"));
            assertTrue(journal.record("ce,0,64,0","minecraft:air"));
            assertEquals("original notes",Files.readString(data.resolve("before-craftengine/notes.yml")));
            assertEquals("new notes",Files.readString(data.resolve("notes.yml")));
        }
    }
}
