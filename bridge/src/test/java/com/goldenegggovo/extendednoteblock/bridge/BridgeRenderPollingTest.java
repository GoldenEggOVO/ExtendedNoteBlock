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

    private static final class RenderPlayer extends org.mockbukkit.mockbukkit.entity.PlayerMock {
        final java.util.List<byte[]> packets = new java.util.ArrayList<>();
        RenderPlayer(ServerMock server) { super(server, "Listener", java.util.UUID.randomUUID()); }
        @Override public java.util.Set<String> getListeningPluginChannels() {
            return java.util.Set.of("extendednoteblock:bridge_object_sync");
        }
        @Override public void sendPluginMessage(org.bukkit.plugin.Plugin source, String channel, byte[] message) {
            if (channel.equals("extendednoteblock:bridge_object_sync")) packets.add(message.clone());
        }
    }

    private void assertUpserts(RenderPlayer player, boolean powered, int noteVariant, int... types) throws Exception {
        var received = new java.util.HashSet<Integer>();
        assertEquals(types.length, player.packets.size());
        for (byte[] packet : player.packets) {
            var input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(packet));
            assertEquals(1, input.readInt());
            long position = input.readLong();
            int type = input.readInt();
            assertTrue(received.add(type), "Only one update per object");
            assertEquals(((long) type << 38) | 64L, position);
            assertEquals(powered, input.readBoolean());
            assertEquals(type == 0 ? noteVariant : 0, input.readInt());
            assertEquals(0, input.available());
        }
        assertEquals(java.util.Arrays.stream(types).boxed().collect(java.util.stream.Collectors.toSet()), received);
        player.packets.clear();
    }

    @Test void renderChangesDeletionAndUnloadedRoutesKeepTheirPacketCoordinates() throws Exception {
        world.loadChunk(0, 0);
        var player = new RenderPlayer(server);
        server.addPlayer(player);
        player.teleport(new org.bukkit.Location(world, 0, 65, 0));
        String note = note(0);
        var types = new ExtendedNoteBlockBridge.BridgeItemType[] {
            EXTENDED_NOTE_BLOCK, GLOBAL_REDSTONE_TRANSMITTER, GLOBAL_REDSTONE_RECEIVER, NBS_PROJECTION_RECEIVER
        };
        var keys = new String[4];
        keys[0] = note;
        for (int x = 1; x < 4; x++) {
            keys[x] = world.getUID() + ":" + x + ":64:0";
            plugin.objects.put(keys[x], types[x]);
            plugin.indexObject(keys[x], types[x]);
            plugin.loadedBlocks.put(keys[x], mock(Block.class));
        }
        when(plugin.loadedBlocks.get(keys[2]).getType()).thenReturn(Material.REDSTONE_BLOCK);
        plugin.transmitterPower.put(keys[1], false);
        plugin.globalPower.put(world.getUID(), false);
        plugin.transmitterProjectionTarget.put(keys[1], keys[3]);
        player.packets.clear();
        render();
        assertUpserts(player, false, 1, 0, 1, 2, 3);
        render();
        assertTrue(player.packets.isEmpty(), "Stable objects must not be broadcast again");

        plugin.notes.put(note, new ExtendedNoteBlockBridge.NoteConfig(65, 9, 90, 25, 50, 2, 4, 13));
        when(plugin.loadedBlocks.get(note).getBlockPower()).thenReturn(15);
        plugin.transmitterPower.put(keys[1], true);
        plugin.globalPower.put(world.getUID(), true);
        render();
        assertUpserts(player, true, 5, 0, 1, 2, 3);

        plugin.objects.remove(note);
        plugin.notes.remove(note);
        plugin.unindexObject(note, EXTENDED_NOTE_BLOCK);
        render();
        assertEquals(1, player.packets.size());
        var removed = new java.io.DataInputStream(new java.io.ByteArrayInputStream(player.packets.getFirst()));
        assertEquals(2, removed.readInt());
        assertEquals(64L, removed.readLong(), "Removal retains coordinates after unindex clears the cache");
        player.packets.clear();

        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(0, 0)));
        plugin.transmitterPower.put(keys[1], false);
        plugin.globalPower.put(world.getUID(), false);
        plugin.transmitterProjectionTarget.remove(keys[1]);
        plugin.blockQueries = 0;
        render();
        assertEquals(0, plugin.blockQueries);
        assertUpserts(player, false, 0, 1, 2, 3);
        assertEquals(3, plugin.objects.size());
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

    @Test void repeatedRegistrationUsesTheCurrentTypeWithoutChangingNoteData() throws Exception {
        world.loadChunk(0, 0);
        String key = note(0);
        var config = plugin.notes.get(key);
        render();
        plugin.objects.put(key, GLOBAL_REDSTONE_TRANSMITTER);
        plugin.transmitterPower.put(key, true);
        plugin.indexObject(key, GLOBAL_REDSTONE_TRANSMITTER);
        plugin.indexObject(key, GLOBAL_REDSTONE_TRANSMITTER);
        plugin.blockQueries = 0;
        render();
        assertEquals(1, plugin.blockQueries);
        assertEquals(1, plugin.lastRenderStates.get(key).typeId());
        assertTrue(plugin.lastRenderStates.get(key).powered());
        assertSame(config, plugin.notes.get(key));
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

    @Test void chunkChurnAndReregistrationPollExactlyTheLoadedObjects() throws Exception {
        world.loadChunk(0, 0);
        world.loadChunk(1, 0);
        for (int x = 0; x < 32; x++) note(x);
        render();
        var saved = java.util.Map.copyOf(plugin.notes);
        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(1, 0)));
        plugin.blockQueries = 0;
        render();
        assertEquals(16, plugin.blockQueries);

        String replaced = world.getUID() + ":0:64:0";
        plugin.objects.remove(replaced);
        plugin.unindexObject(replaced, EXTENDED_NOTE_BLOCK);
        plugin.notes.remove(replaced);
        note(0);
        note(0);
        plugin.blockQueries = 0;
        render();
        assertEquals(16, plugin.blockQueries, "Repeated registration must not duplicate active work");

        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(0, 0)));
        plugin.blockQueries = 0;
        render();
        assertEquals(0, plugin.blockQueries);
        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(1, 0), false));
        plugin.blockQueries = 0;
        render();
        assertEquals(16, plugin.blockQueries);
        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        plugin.blockQueries = 0;
        render();
        assertEquals(32, plugin.blockQueries);
        assertEquals(32, plugin.objects.size());
        assertEquals(saved, plugin.notes);
        assertEquals(32, plugin.lastRenderStates.size());
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
