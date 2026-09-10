package com.goldenegggovo.extendednoteblock.bridge;

import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.*;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.util.*;

/** One globally bounded copy/restore session. All world access is on the server thread. */
final class BridgeSchematicTransfer implements AutoCloseable {
    interface Target {
        Document capture(World world, List<Box> boxes);
        boolean accepts(World world, Entry entry);
        boolean commit(World world, Document document);
    }
    private final JavaPlugin plugin;
    private final Target target;
    private final BukkitTask task;
    private Pending pending;
    private long nextSession;
    private int tickPackets;

    BridgeSchematicTransfer(JavaPlugin plugin, Target target) {
        this.plugin = plugin; this.target = target;
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, SchematicTransfer.CHANNEL, (channel, player, bytes) -> receive(player, bytes));
        messenger.registerOutgoingPluginChannel(plugin, SchematicTransfer.CHANNEL);
        messenger.registerOutgoingPluginChannel(plugin, SchematicTransfer.STATUS_CHANNEL);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    void receive(Player player, byte[] bytes) {
        // Bound parsing and inbound bandwidth even for malformed messages.
        if (++tickPackets > 64) return;
        Packet packet;
        try { packet = SchematicTransfer.decode(bytes); }
        catch (IOException invalid) { if (pending != null && pending.player.equals(player.getUniqueId())) fail("Malformed schematic packet"); return; }
        if (packet instanceof Result) return;
        try {
            if (!player.hasPermission("extendednoteblockbridge.import")) throw new IllegalArgumentException("Missing permission: extendednoteblockbridge.import");
            boolean restartExport = pending != null && pending.outgoing != null && packet instanceof Request
                    && pending.player.equals(player.getUniqueId()) && pending.world.equals(player.getWorld().getUID())
                    && !pending.id.equals(packet.id());
            if (pending == null || restartExport) {
                if (System.nanoTime() < nextSession) throw new IllegalArgumentException("Please wait before starting another ENB transfer");
                if (packet instanceof Part p && p.offset() != 0) throw new IllegalArgumentException("Schematic session expired");
                nextSession = System.nanoTime() + 1_000_000_000L;
                pending = new Pending(player.getUniqueId(), player.getWorld().getUID(), packet.id());
            } else if (!pending.player.equals(player.getUniqueId()) || !pending.id.equals(packet.id())) {
                throw new IllegalArgumentException("Another ENB copy or restore is in progress");
            }
            if (!pending.world.equals(player.getWorld().getUID())) throw new IllegalArgumentException("World changed during schematic transfer");
            pending.lastMessage = System.nanoTime();
            if (packet instanceof Request request) {
                if (pending.startedTransfer) throw new IllegalArgumentException("Schematic session already started");
                pending.startedTransfer = true;
                checkBoxes(player, request.boxes());
                Document document = target.capture(player.getWorld(), request.boxes());
                for (Entry entry : document.entries()) {
                    if (request.boxes().stream().noneMatch(box -> box.contains(entry.pos()))) throw new IllegalArgumentException("Exported object outside selection");
                    checkPosition(player, entry.pos());
                }
                pending.outgoing = SchematicTransfer.encodeDocument(document);
            } else if (packet instanceof Part part) {
                if (pending.outgoing != null) throw new IllegalArgumentException("Cannot upload during export");
                pending.startedTransfer = true;
                pending.assembly.add(part);
                if (pending.assembly.complete()) {
                    Document document = pending.assembly.document();
                    // Entire preflight and commit happen in one main-thread callback, with no partial mutations.
                    for (Entry entry : document.entries()) {
                        checkPosition(player, entry.pos());
                        if (!target.accepts(player.getWorld(), entry)) throw new IllegalArgumentException("Pasted ENB carrier does not match at " + entry.pos().display());
                    }
                    boolean saved = target.commit(player.getWorld(), document);
                    UUID id = pending.id; pending = null;
                    reply(player, new Result(id, saved, saved ? "ENB metadata restored" : "Restore failed or changes were applied but could not be saved; check server log before restarting"));
                }
            }
        } catch (IOException | RuntimeException invalid) {
            if (pending != null && pending.player.equals(player.getUniqueId()) && pending.id.equals(packet.id())) fail(message(invalid));
            else reply(player, new Result(packet.id(), false, message(invalid)));
        }
    }

    private void checkBoxes(Player player, List<Box> boxes) {
        long chunks = 0;
        for (Box box : boxes) {
            checkPosition(player, box.min()); checkPosition(player, box.max());
            chunks += (long) ((box.max().x() >> 4) - (box.min().x() >> 4) + 1) * ((box.max().z() >> 4) - (box.min().z() >> 4) + 1);
            if (chunks > 16_384) throw new IllegalArgumentException("Selection covers too many chunks");
            for (int x = box.min().x() >> 4; x <= box.max().x() >> 4; x++) {
                for (int z = box.min().z() >> 4; z <= box.max().z() >> 4; z++) {
                    if (!player.getWorld().isChunkLoaded(x,z)) throw new IllegalArgumentException("Load every selected chunk before copying");
                }
            }
        }
    }

    private void checkPosition(Player player, Pos pos) {
        World world = player.getWorld();
        if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight() || !world.isChunkLoaded(pos.x() >> 4,pos.z() >> 4))
            throw new IllegalArgumentException("ENB position is unloaded or outside world height: " + pos.display());
        Location location = new Location(world,pos.x()+.5,pos.y()+.5,pos.z()+.5);
        if (!world.getWorldBorder().isInside(location)) throw new IllegalArgumentException("ENB position is outside world border");
        double range = Math.max(1,Math.min(2048,plugin.getConfig().getDouble("litematic-import.schematic-range",256)));
        if (player.getLocation().distanceSquared(location) > range * range) throw new IllegalArgumentException("Move closer to the selected or pasted ENB objects");
    }

    private void tick() {
        tickPackets = 0;
        if (pending == null) return;
        Player player = plugin.getServer().getPlayer(pending.player);
        long now = System.nanoTime();
        if (player == null || !player.isOnline() || !player.getWorld().getUID().equals(pending.world)
                || !player.hasPermission("extendednoteblockbridge.import")) { fail("Player disconnected, changed world or lost permission"); return; }
        if (now - pending.lastMessage > 30_000_000_000L || now - pending.started > 120_000_000_000L) { fail("Schematic transfer timed out"); return; }
        if (pending.outgoing == null) return;
        try {
            for (int i=0;i<4 && pending.offset < pending.outgoing.length;i++) {
                int end=Math.min(pending.outgoing.length,pending.offset+SchematicTransfer.PART_SIZE);
                Part part=new Part(pending.id,pending.offset,pending.outgoing.length,Arrays.copyOfRange(pending.outgoing,pending.offset,end));
                player.sendPluginMessage(plugin,SchematicTransfer.CHANNEL,SchematicTransfer.encode(part));
                pending.offset=end; pending.lastMessage=now;
            }
            if (pending.offset == pending.outgoing.length) pending=null;
        } catch (RuntimeException failed) { fail(message(failed)); }
    }

    private void fail(String reason) {
        if (pending == null) return;
        Pending old=pending; pending=null; Player player=plugin.getServer().getPlayer(old.player);
        if (player!=null && player.isOnline()) reply(player,new Result(old.id,false,reason));
    }
    private void reply(Player player,Result result) { player.sendPluginMessage(plugin,SchematicTransfer.STATUS_CHANNEL,SchematicTransfer.encode(result)); }
    private static String message(Exception exception) { String text=exception.getMessage(); if(text==null) text="Schematic transfer failed"; return text.substring(0,Math.min(384,text.length())); }
    @Override public void close() {
        task.cancel(); pending=null;
        var messenger=plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin,SchematicTransfer.CHANNEL);
        messenger.unregisterOutgoingPluginChannel(plugin,SchematicTransfer.CHANNEL);
        messenger.unregisterOutgoingPluginChannel(plugin,SchematicTransfer.STATUS_CHANNEL);
    }
    private static final class Pending {
        final UUID player,world,id; final Assembly assembly;
        final long started=System.nanoTime(); long lastMessage=started;
        boolean startedTransfer; byte[] outgoing; int offset;
        Pending(UUID player,UUID world,UUID id){this.player=player;this.world=world;this.id=id;assembly=new Assembly(id);}
    }
}
