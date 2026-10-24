package com.goldenegggovo.extendednoteblock.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.goldenegggovo.extendednoteblock.bridge.ExtendedNoteBlockBridge.BridgeItemType.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;

class BridgeRenderPollingTest {
    private ServerMock server;
    private WorldMock world;
    private CountingBridge plugin;

    public static class CountingBridge extends ExtendedNoteBlockBridge {
        int blockQueries, defaultConfigs, sounds;
        final java.util.Map<String, Block> loadedBlocks = new java.util.HashMap<>();
        @Override Block getLoadedBlock(String key) {
            blockQueries++;
            return loadedBlocks.containsKey(key) ? loadedBlocks.get(key) : super.getLoadedBlock(key);
        }
        @Override NoteConfig defaultNoteConfig() { defaultConfigs++; return super.defaultNoteConfig(); }
        @Override void startConfiguredSound(Block block, NoteConfig config) { sounds++; }
    }

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("render");
        plugin = MockBukkit.load(CountingBridge.class);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private String note(int x) {
        String key = world.getUID() + ":" + x + ":64:0";
        plugin.objects.put(key, EXTENDED_NOTE_BLOCK);
        plugin.notes.put(key, new ExtendedNoteBlockBridge.NoteConfig(61, 9, 90, 25, 50, 2, 4, 13));
        plugin.indexObject(key, EXTENDED_NOTE_BLOCK);
        if (world.isChunkLoaded(x >> 4, 0)) plugin.loadedBlocks.put(key, mock(Block.class));
        return key;
    }
    private void render() throws Exception {
        Method method = ExtendedNoteBlockBridge.class.getDeclaredMethod("syncChangedRenderStates");
        method.setAccessible(true);
        method.invoke(plugin);
    }

    @Test void unloadedLedgerDoesNotCreatePerTickWorldQueries() throws Exception {
        for (int i = 0; i < 8879; i++) note(100_000 + i);
        render();
        plugin.blockQueries = 0;
        for (int i = 0; i < 20; i++) render();
        assertEquals(0, plugin.blockQueries, "Unloaded objects must leave the recurring work set");
        assertEquals(8879, plugin.objects.size());
        assertEquals(8879, plugin.notes.size());
    }

    @Test void stableLoadedNotesReuseStateAndDoNotConstructFallbackConfigs() throws Exception {
        world.loadChunk(0, 0);
        world.getBlockAt(0, 64, 0).setType(Material.NOTE_BLOCK);
        String key = note(0);
        render();
        var previous = plugin.lastRenderStates.get(key);
        plugin.defaultConfigs = 0;
        for (int i = 0; i < 20; i++) render();
        assertEquals(0, plugin.defaultConfigs);
        assertSame(previous, plugin.lastRenderStates.get(key));
        assertEquals(1, previous.variant());
        assertEquals(13, plugin.notes.get(key).pitchCents());
    }

    @Test void renderAndCraftEngineShareOneBlockQuery() throws Exception {
        world.loadChunk(0, 0);
        String key = note(0);
        Block block = plugin.loadedBlocks.get(key);
        plugin.craftEngine = new EnbCraftEngine(plugin);
        Field ready = EnbCraftEngine.class.getDeclaredField("ready");
        ready.setAccessible(true);
        ready.set(plugin.craftEngine, true);
        plugin.getConfig().set("craftengine.migrate-known-blocks", false);
        try (var api = mockStatic(CraftEngineBlocks.class)) {
            api.when(() -> CraftEngineBlocks.getCustomBlockState(block)).thenReturn(null);
            plugin.blockQueries = 0;
            render();
            assertEquals(1, plugin.blockQueries);
        }
    }

    @Test void unloadSuspendsQueriesAndReloadReactivatesWithoutChangingNotes() throws Exception {
        world.loadChunk(0, 0);
        String key = note(0);
        Block block = plugin.loadedBlocks.get(key);
        when(block.getBlockPower()).thenReturn(15);
        render();
        assertTrue(plugin.lastRenderStates.get(key).powered());
        var config = plugin.notes.get(key);
        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(0, 0)));
        plugin.blockQueries = 0;
        for (int i = 0; i < 20; i++) render();
        assertEquals(0, plugin.blockQueries);
        assertFalse(plugin.lastRenderStates.get(key).powered());
        assertSame(config, plugin.notes.get(key));
        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        render();
        assertEquals(1, plugin.blockQueries);
        assertTrue(plugin.lastRenderStates.get(key).powered());
    }

    @Test void unloadRetainsKnownWirelessPowerAndProjectionAppearance() throws Exception {
        world.loadChunk(0, 0);
        String tx = world.getUID() + ":0:64:0";
        String receiver = world.getUID() + ":1:64:0";
        String projection = world.getUID() + ":2:64:0";
        plugin.objects.put(tx, GLOBAL_REDSTONE_TRANSMITTER);
        plugin.objects.put(receiver, GLOBAL_REDSTONE_RECEIVER);
        plugin.objects.put(projection, NBS_PROJECTION_RECEIVER);
        plugin.indexObject(tx, GLOBAL_REDSTONE_TRANSMITTER);
        plugin.indexObject(receiver, GLOBAL_REDSTONE_RECEIVER);
        plugin.indexObject(projection, NBS_PROJECTION_RECEIVER);
        plugin.transmitterPower.put(tx, true);
        plugin.transmitterProjectionTarget.put(tx, projection);
        plugin.globalPower.put(world.getUID(), true);
        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(0, 0)));
        render();
        assertTrue(plugin.lastRenderStates.get(tx).powered());
        assertTrue(plugin.lastRenderStates.get(receiver).powered());
        assertTrue(plugin.lastRenderStates.get(projection).powered());
        assertEquals(Boolean.TRUE, plugin.transmitterPower.get(tx));
    }

    @Test void removalAndLedgerReloadDiscardStaleRenderState() throws Exception {
        String key = note(100_000);
        render();
        assertNotNull(plugin.lastRenderStates.get(key));
        plugin.objects.remove(key);
        plugin.unindexObject(key, EXTENDED_NOTE_BLOCK);
        render();
        assertFalse(plugin.lastRenderStates.containsKey(key));
        String old = note(100_001);
        render();
        plugin.objects.clear();
        Method rebuild = ExtendedNoteBlockBridge.class.getDeclaredMethod("rebuildObjectIndexes");
        rebuild.setAccessible(true);
        rebuild.invoke(plugin);
        render();
        assertFalse(plugin.lastRenderStates.containsKey(old));
    }

    @Test void removingTransmitterClearsUnloadedProjectionAppearance() throws Exception {
        assertUnloadedProjectionTurnsOffAfterRouteReset(true);
    }

    @Test void reimportingTransmitterClearsUnloadedProjectionAppearance() throws Exception {
        assertUnloadedProjectionTurnsOffAfterRouteReset(false);
    }

    private void assertUnloadedProjectionTurnsOffAfterRouteReset(boolean remove) throws Exception {
        String tx = world.getUID() + ":100000:64:0";
        String receiver = world.getUID() + ":100001:64:0";
        plugin.objects.put(tx, GLOBAL_REDSTONE_TRANSMITTER);
        plugin.objects.put(receiver, NBS_PROJECTION_RECEIVER);
        plugin.indexObject(tx, GLOBAL_REDSTONE_TRANSMITTER);
        plugin.indexObject(receiver, NBS_PROJECTION_RECEIVER);
        plugin.transmitterProjectionTarget.put(tx, receiver);
        plugin.transmitterPower.put(tx, true);
        render();
        assertTrue(plugin.lastRenderStates.get(receiver).powered());
        if (remove) {
            plugin.objects.remove(tx);
            plugin.unindexObject(tx, GLOBAL_REDSTONE_TRANSMITTER);
        } else plugin.indexObject(tx, GLOBAL_REDSTONE_TRANSMITTER);
        plugin.transmitterProjectionTarget.remove(tx);
        plugin.transmitterPower.remove(tx);
        render();
        assertFalse(plugin.lastRenderStates.get(receiver).powered());
    }

    @Test void cancelledWorldUnloadKeepsPollingAndActualUnloadSuspendsIt() throws Exception {
        world.loadChunk(0, 0);
        note(0);
        render();
        WorldUnloadEvent cancelled = new WorldUnloadEvent(world);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        plugin.blockQueries = 0;
        render();
        assertEquals(1, plugin.blockQueries);
        server.getPluginManager().callEvent(new WorldUnloadEvent(world));
        plugin.blockQueries = 0;
        render();
        assertEquals(0, plugin.blockQueries);
    }

    @Test void craftEngineFirstLoadedHighDoesNotPlayButNextRisingEdgeDoes() throws Exception {
        world.loadChunk(0, 0);
        String key = note(0);
        Block block = plugin.loadedBlocks.get(key);
        plugin.craftEngine = new EnbCraftEngine(plugin);
        Field ready = EnbCraftEngine.class.getDeclaredField("ready");
        ready.setAccessible(true);
        ready.set(plugin.craftEngine, true);
        try (var api = mockStatic(CraftEngineBlocks.class)) {
            var custom = mock(net.momirealms.craftengine.core.block.ImmutableBlockState.class, RETURNS_DEEP_STUBS);
            when(custom.owner().value().id()).thenReturn(net.momirealms.craftengine.core.util.Key.of("enb:extended_note_block"));
            api.when(() -> CraftEngineBlocks.getCustomBlockState(block)).thenReturn(custom);
            when(block.getBlockPower()).thenReturn(15);
            render();
            server.getScheduler().performTicks(2);
            assertEquals(0, plugin.sounds);
            when(block.getBlockPower()).thenReturn(0);
            render();
            when(block.getBlockPower()).thenReturn(15);
            render();
            server.getScheduler().performTicks(2);
            assertEquals(1, plugin.sounds);
            server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(0, 0)));
            render();
            server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
            render();
            server.getScheduler().performTicks(2);
            assertEquals(1, plugin.sounds, "Reloading a powered chunk must not synthesize a rising edge");
        }
    }
}
