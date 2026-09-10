package com.goldenegggovo.extendednoteblock.bridge;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BridgePerformanceIntegrationTest {
    ServerMock server;
    CountingBridge plugin;
    @BeforeEach void setup() { server = MockBukkit.mock(); server.addSimpleWorld("music"); plugin = MockBukkit.load(CountingBridge.class); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    public static class CountingBridge extends ExtendedNoteBlockBridge {
        int wirelessTicks, musicTicks;
        @Override void tickBridgeLogic() { wirelessTicks++; }
        @Override void tickProjectionSessions() { musicTicks++; }
    }
    Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = ExtendedNoteBlockBridge.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(plugin, args);
    }
    @SuppressWarnings("unchecked") <T> T field(String name) throws Exception {
        Field field = ExtendedNoteBlockBridge.class.getDeclaredField(name); field.setAccessible(true); return (T) field.get(plugin);
    }
    void dirtyNote(String key) throws Exception {
        Map<String, Object> notes = field("notes"); notes.put(key, invoke("defaultNoteConfig", new Class<?>[0]));
        invoke("requestSave", new Class<?>[]{String.class}, "notes");
    }
    @Test void wirelessIntervalDoesNotSlowMusicOrDuplicateTimers() {
        plugin.getConfig().set("wireless-redstone.poll-period-ticks", 5);
        plugin.startTickers(); plugin.startTickers();
        server.getScheduler().performTicks(10);
        assertEquals(10, plugin.musicTicks); assertEquals(2, plugin.wirelessTicks);
    }
    @Test void editsAreCoalescedAndLatestSnapshotIsSaved() throws Exception {
        String root = server.getWorld("music").getUID().toString();
        dirtyNote(root + ":0:64:0"); dirtyNote(root + ":1:64:0");
        Path file = plugin.getDataFolder().toPath().resolve("notes.yml");
        assertFalse(Files.exists(file)); server.getScheduler().performTicks(10);
        assertEquals(2, YamlConfiguration.loadConfiguration(file.toFile()).getKeys(false).size());
        assertTrue(this.<Set<String>>field("pendingSaves").isEmpty());
    }
    @Test void normalDisableFlushesEditsBeforeDelayExpires() throws Exception {
        dirtyNote(server.getWorld("music").getUID() + ":1:64:0");
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(1, YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), "notes.yml")).getKeys(false).size());
    }
    @Test void failedWriteRetainsDirtyDataAndRetries() throws Exception {
        Path blocked = plugin.getDataFolder().toPath().resolve("notes.yml");
        Files.createDirectories(blocked); Path obstacle = blocked.resolve("keep"); Files.writeString(obstacle, "blocked");
        dirtyNote(server.getWorld("music").getUID() + ":1:64:0");
        server.getScheduler().performTicks(10);
        assertTrue(this.<Set<String>>field("pendingSaves").contains("notes"));
        Files.delete(obstacle); Files.delete(blocked);
        server.getScheduler().performTicks(10);
        assertTrue(Files.isRegularFile(blocked)); assertTrue(this.<Set<String>>field("pendingSaves").isEmpty());
    }
    @Test void indexesRemoveWorldsAndDoNotCacheArbitraryLookups() throws Exception {
        Class<?> type = Class.forName(ExtendedNoteBlockBridge.class.getName() + "$BridgeItemType");
        Object receiver = Arrays.stream(type.getEnumConstants()).filter(v -> v.toString().equals("GLOBAL_REDSTONE_RECEIVER")).findFirst().orElseThrow();
        UUID world = server.getWorld("music").getUID(); String key = world + ":1:64:0";
        invoke("indexObject", new Class<?>[]{String.class, type}, key, receiver);
        assertEquals(Set.of(key), this.<Map<UUID, Set<String>>>field("receiversByWorld").get(world));
        invoke("parseKey", new Class<?>[]{String.class}, world + ":200:64:0");
        assertEquals(1, this.<Map<?, ?>>field("blockRefs").size());
        invoke("unindexObject", new Class<?>[]{String.class, type}, key, receiver);
        assertTrue(this.<Map<?, ?>>field("blockRefs").isEmpty()); assertTrue(this.<Map<?, ?>>field("receiversByWorld").isEmpty());
    }
    @Test void failedReloadKeepsEditsAndRearmsSaveRetry() throws Exception {
        Path blocked = plugin.getDataFolder().toPath().resolve("notes.yml");
        Files.createDirectories(blocked); Path obstacle = blocked.resolve("keep"); Files.writeString(obstacle, "blocked");
        dirtyNote(server.getWorld("music").getUID() + ":1:64:0");
        plugin.onCommand(server.getConsoleSender(), plugin.getCommand("enb"), "enb", new String[]{"reload"});
        assertEquals(1, this.<Map<?, ?>>field("notes").size());
        Files.delete(obstacle); Files.delete(blocked);
        server.getScheduler().performTicks(10);
        assertTrue(Files.isRegularFile(blocked)); assertTrue(this.<Set<String>>field("pendingSaves").isEmpty());
    }
    @Test void staleIndependentPackConfigNeverReactivatesOldDownloads() throws Exception {
        plugin.getConfig().set("resource-pack.enabled", true);
        plugin.getConfig().set("resource-pack.use-official-release", false);
        plugin.getConfig().set("resource-pack.url", "https://example.com/old-enb.zip");
        plugin.getConfig().set("resource-pack.sha1", "a".repeat(40));
        plugin.getConfig().set("resource-pack.combined-file", "old-server-pack.zip");
        invoke("loadListenerResourcePackSettings", new Class<?>[0]);
        assertFalse(plugin.listenerPackEnabled);
        assertEquals("", plugin.listenerPackUrl);
        assertNull(plugin.listenerPackId);
        assertTrue(plugin.listenerPackSource.startsWith("CraftEngine combined pack"));
    }
}
