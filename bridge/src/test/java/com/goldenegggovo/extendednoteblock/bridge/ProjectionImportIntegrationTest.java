package com.goldenegggovo.extendednoteblock.bridge;

import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Drives the real plugin's registered C2S handler and YAML persistence. */
class ProjectionImportIntegrationTest {
    private ServerMock server;
    private WorldMock world;
    private ExtendedNoteBlockBridge plugin;
    private ImportPlayer player;
    private final Pos transmitter = new Pos(0, 100, 0), receiver = new Pos(1, 100, 0);

    @BeforeEach void setup() {
        server = MockBukkit.mock(); world = server.addSimpleWorld("music");
        plugin = MockBukkit.load(ExtendedNoteBlockBridge.class);
        player = new ImportPlayer(server, "Conductor"); server.addPlayer(player); player.setOp(true);
        player.teleport(new Location(world, .5, 101, .5));
        carrier(transmitter, Material.RED_CONCRETE); carrier(receiver, Material.PURPLE_CONCRETE);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private void carrier(Pos p, Material material) {
        world.loadChunk(p.x() >> 4, p.z() >> 4); world.getBlockAt(p.x(), p.y(), p.z()).setType(material);
    }
    private Note note(int x, int midi) { return new Note(new Pos(x, 103, 3), midi, 40, 100, 20, 3560, 2, 3, -25); }
    private void send(Packet packet) { send(player, packet); }
    private void send(ImportPlayer sender, Packet packet) {
        server.getMessenger().dispatchIncomingMessage(sender, ProjectionImport.CHANNEL, ProjectionImport.encode(packet));
    }
    private UUID upload(List<Note> notes) {
        UUID id = UUID.randomUUID(); send(new Begin(id, transmitter, receiver, notes.size()));
        assertEquals(ProjectionImport.READY, player.last().stage());
        for (int i = 0; i < notes.size(); i += ProjectionImport.BATCH_SIZE) {
            send(new Batch(id, i, notes.subList(i, Math.min(i + ProjectionImport.BATCH_SIZE, notes.size()))));
            assertEquals(ProjectionImport.RECEIVED, player.last().stage());
        }
        send(new Finish(id)); return id;
    }
    private YamlConfiguration yaml(String name) { return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name + ".yml")); }
    private String key(Pos p) { return world.getUID() + ":" + p.x() + ":" + p.y() + ":" + p.z(); }

    @Test void restoresAllMidiNotesAndPersistsSettingsAndTimelineAcrossReload() throws Exception {
        List<Note> notes = new ArrayList<>();
        for (int midi = 0; midi < 128; midi++) { Note n = note(midi * 3, midi); carrier(n.pos(), Material.NOTE_BLOCK); notes.add(n); }
        upload(notes);
        assertFalse(new File(plugin.getDataFolder(), "notes.yml").exists(), "Upload cannot commit before preflight");
        server.getScheduler().performOneTick();
        assertEquals(ProjectionImport.COMPLETE, player.last().stage());
        YamlConfiguration saved = yaml("notes");
        for (Note n : notes) {
            String k = key(n.pos());
            assertEquals("extended_note_block", yaml("objects").getString(k));
            assertEquals(n.midi(), saved.getInt(k + ".note"));
            assertEquals(n.instrument(), saved.getInt(k + ".instrument"));
            assertEquals(n.velocity(), saved.getInt(k + ".velocity"));
            assertEquals(n.sustain(), saved.getInt(k + ".sustain"));
            assertEquals(n.delayMs(), saved.getInt(k + ".delayMs"));
            assertEquals(n.fadeIn(), saved.getInt(k + ".fadeIn"));
            assertEquals(n.fadeOut(), saved.getInt(k + ".fadeOut"));
            assertEquals(n.pitchCents(), saved.getInt(k + ".pitchCents"));
        }
        assertEquals("global_redstone_transmitter", yaml("objects").getString(key(transmitter)));
        assertEquals("nbs_projection_receiver", yaml("objects").getString(key(receiver)));
        assertEquals(128, yaml("projections").getInt(key(receiver) + ".count"));
        String notesBefore = saved.saveToString(), projectionsBefore = yaml("projections").saveToString();
        // Reload from disk into emptied authoritative maps, then save them again.
        for (String method : List.of("loadObjects", "loadNotes", "loadProjections", "saveObjects", "saveNotes", "saveProjections")) {
            var loader = ExtendedNoteBlockBridge.class.getDeclaredMethod(method); loader.setAccessible(true); loader.invoke(plugin);
        }
        assertEquals(notesBefore, yaml("notes").saveToString());
        assertEquals(projectionsBefore, yaml("projections").saveToString());
    }

    @Test void rejectsMissingNoteWithoutRegisteringAnyObjects() {
        Note first = note(2, 60), missing = note(5, 72); carrier(first.pos(), Material.NOTE_BLOCK);
        upload(List.of(first, missing)); server.getScheduler().performOneTick();
        assertEquals(ProjectionImport.REJECTED, player.last().stage());
        assertTrue(yaml("objects").getKeys(false).isEmpty());
        assertTrue(yaml("notes").getKeys(false).isEmpty());
    }

    @Test void rejectsUnloadedChunkWithoutLoadingIt() {
        Note unloaded = note(640, 60); carrier(unloaded.pos(), Material.NOTE_BLOCK); world.unloadChunk(40, 0);
        upload(List.of(unloaded)); server.getScheduler().performOneTick();
        assertEquals(ProjectionImport.REJECTED, player.last().stage());
        assertFalse(world.isChunkLoaded(40, 0)); assertTrue(yaml("objects").getKeys(false).isEmpty());
    }

    @Test void rechecksPreviouslyValidatedBlocksBeforeCommit() {
        plugin.getConfig().set("litematic-import.checks-per-tick", 1);
        Note first = note(2, 60), second = note(5, 72);
        carrier(first.pos(), Material.NOTE_BLOCK); carrier(second.pos(), Material.NOTE_BLOCK);
        upload(List.of(first, second)); server.getScheduler().performOneTick();
        carrier(first.pos(), Material.STONE); server.getScheduler().performOneTick();
        assertEquals(ProjectionImport.REJECTED, player.last().stage());
        assertTrue(yaml("notes").getKeys(false).isEmpty());
    }

    @Test void requiresPermissionAndNearbyAnchor() {
        player.setOp(false); send(new Begin(UUID.randomUUID(), transmitter, receiver, 1));
        assertEquals(ProjectionImport.REJECTED, player.last().stage());
        player.setOp(true); player.teleport(new Location(world, 1000, 100, 0));
        send(new Begin(UUID.randomUUID(), transmitter, receiver, 1));
        assertEquals(ProjectionImport.REJECTED, player.last().stage());
        assertTrue(yaml("objects").getKeys(false).isEmpty());
    }

    @Test void cancelsOnWorldChangeOrExplicitCancel() {
        Note note = note(2, 60); carrier(note.pos(), Material.NOTE_BLOCK);
        UUID id = upload(List.of(note)); send(new Cancel(id)); server.getScheduler().performOneTick();
        assertEquals(ProjectionImport.REJECTED, player.last().stage());
        upload(List.of(note));
        player.teleport(new Location(server.addSimpleWorld("other"), 0, 100, 0)); server.getScheduler().performOneTick();
        assertEquals(ProjectionImport.REJECTED, player.last().stage());
        assertTrue(yaml("objects").getKeys(false).isEmpty());
    }

    @Test void anotherPlayerCannotCancelOwnersSession() {
        Note note = note(2, 60); carrier(note.pos(), Material.NOTE_BLOCK);
        UUID id = upload(List.of(note));
        ImportPlayer other = new ImportPlayer(server, "Other"); server.addPlayer(other); other.setOp(true);
        send(other, new Cancel(id)); assertEquals(ProjectionImport.REJECTED, other.last().stage());
        server.getScheduler().performOneTick(); assertEquals(ProjectionImport.COMPLETE, player.last().stage());
    }

    private static final class ImportPlayer extends PlayerMock {
        private final List<Status> replies = new ArrayList<>();
        ImportPlayer(ServerMock server, String name) { super(server, name, UUID.randomUUID()); }
        @Override public Set<String> getListeningPluginChannels() { return Set.of(ProjectionImport.STATUS_CHANNEL); }
        @Override public void sendPluginMessage(Plugin source, String channel, byte[] message) {
            if (channel.equals(ProjectionImport.STATUS_CHANNEL)) {
                try { replies.add(ProjectionImport.decodeStatus(message)); }
                catch (IOException invalid) { throw new AssertionError(invalid); }
            }
        }
        Status last() { assertFalse(replies.isEmpty(), "Server must reply to the import request"); return replies.getLast(); }
    }
}
