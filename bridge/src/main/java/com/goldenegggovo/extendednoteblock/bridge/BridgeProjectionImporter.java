package com.goldenegggovo.extendednoteblock.bridge;

import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.UUID;

/** One bounded upload at a time; carrier preflight never loads/generates chunks. */
final class BridgeProjectionImporter implements AutoCloseable {
    interface Target {
        boolean accepts(World world, Pos pos, int type);
        boolean commit(World world, Plan plan);
    }

    private final JavaPlugin plugin;
    private final Target target;
    private final BukkitTask task;
    private Pending pending;

    BridgeProjectionImporter(JavaPlugin plugin, Target target) {
        this.plugin = plugin; this.target = target;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, ProjectionImport.STATUS_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, ProjectionImport.CHANNEL,
                (channel, player, bytes) -> receive(player, bytes));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void receive(Player player, byte[] bytes) {
        Packet packet;
        try { packet = ProjectionImport.decode(bytes); }
        catch (IOException invalid) {
            if (pending != null && pending.player.equals(player.getUniqueId())) fail("Malformed import packet");
            return;
        }
        if (!player.hasPermission("extendednoteblockbridge.import")) {
            if (owns(player, packet.id())) pending = null;
            reply(player, new Status(packet.id(), ProjectionImport.REJECTED, 0, 0,
                    "Missing permission: extendednoteblockbridge.import (OP by default)"));
            return;
        }
        try {
            if (packet instanceof Begin begin) {
                if (pending != null) throw new IllegalArgumentException("Another ENB restore is in progress; try again after it finishes");
                int limit = Math.max(1, Math.min(ProjectionImport.MAX_NOTES, plugin.getConfig().getInt("litematic-import.max-notes", ProjectionImport.MAX_NOTES)));
                if (begin.total() > limit) throw new IllegalArgumentException("Projection exceeds server note limit: " + limit);
                double range = Math.max(1, Math.min(256, plugin.getConfig().getDouble("litematic-import.anchor-range", 64)));
                Pos pos = begin.transmitter();
                Location anchor = new Location(player.getWorld(), pos.x() + .5, pos.y() + .5, pos.z() + .5);
                if (player.getLocation().distanceSquared(anchor) > range * range) {
                    throw new IllegalArgumentException("Move closer to the pasted transmitter (within " + (int) range + " blocks)");
                }
                checkCarrier(player.getWorld(), begin.transmitter(), 1);
                checkCarrier(player.getWorld(), begin.receiver(), 3);
                pending = new Pending(player.getUniqueId(), player.getWorld().getUID(), new Assembly(begin));
                reply(player, new Status(begin.id(), ProjectionImport.READY, 0, begin.total(), ""));
                return;
            }
            if (!owns(player, packet.id())) throw new IllegalArgumentException("Import session expired or does not exist");
            if (!pending.world.equals(player.getWorld().getUID())) throw new IllegalArgumentException("World changed during restore");
            pending.lastMessage = System.nanoTime();
            if (packet instanceof Cancel) { fail("Restore cancelled; no ENB data changed"); return; }
            if (pending.plan != null) throw new IllegalArgumentException("Import already being validated");
            if (packet instanceof Batch batch) {
                pending.assembly.add(batch);
                reply(player, new Status(packet.id(), ProjectionImport.RECEIVED, pending.assembly.size(),
                        pending.assembly.begin().total(), ""));
            } else if (packet instanceof Finish) {
                pending.plan = pending.assembly.finish(packet.id());
                reply(player, new Status(packet.id(), ProjectionImport.VALIDATING, 0, pending.plan.notes().size(), ""));
            }
        } catch (IllegalArgumentException invalid) {
            if (owns(player, packet.id())) fail(invalid.getMessage());
            else reply(player, new Status(packet.id(), ProjectionImport.REJECTED, 0, 0, shortMessage(invalid.getMessage())));
        }
    }

    private boolean owns(Player player, UUID request) {
        return pending != null && pending.player.equals(player.getUniqueId()) && pending.assembly.begin().id().equals(request);
    }

    private void tick() {
        if (pending == null) return;
        Player player = Bukkit.getPlayer(pending.player);
        if (player == null || !player.isOnline() || !player.getWorld().getUID().equals(pending.world)
                || !player.hasPermission("extendednoteblockbridge.import")) {
            fail("Player disconnected, changed world or lost import permission"); return;
        }
        long now = System.nanoTime();
        if (now - pending.lastMessage > 60_000_000_000L || now - pending.started > 900_000_000_000L) {
            fail("Restore timed out; no ENB data changed"); return;
        }
        if (pending.plan == null) return;
        World world = player.getWorld();
        try {
            int batch = Math.max(1, Math.min(2048, plugin.getConfig().getInt("litematic-import.checks-per-tick", 512)));
            int end = Math.min(pending.plan.notes().size(), pending.checked + batch);
            for (; pending.checked < end; pending.checked++) checkCarrier(world, pending.plan.notes().get(pending.checked).pos(), 0);
            pending.lastMessage = now;
            if (pending.checked < pending.plan.notes().size()) {
                if (++pending.progressTicks % 10 == 0) reply(player, new Status(pending.plan.begin().id(),
                        ProjectionImport.VALIDATING, pending.checked, pending.plan.notes().size(), ""));
                return;
            }
            // World edits/chunk unloads can occur between preflight ticks. Recheck
            // immediately before the single main-thread metadata commit.
            checkCarrier(world, pending.plan.begin().transmitter(), 1);
            checkCarrier(world, pending.plan.begin().receiver(), 3);
            for (Note note : pending.plan.notes()) checkCarrier(world, note.pos(), 0);
            Plan plan = pending.plan;
            boolean saved = target.commit(world, plan);
            pending = null;
            reply(player, new Status(plan.begin().id(), saved ? ProjectionImport.COMPLETE : ProjectionImport.REJECTED,
                    plan.notes().size(), plan.notes().size(), saved ? "" :
                    "ENB data applied, but saving failed. Check the server log and disk before restarting."));
        } catch (RuntimeException invalid) {
            plugin.getLogger().warning("ENB restore stopped: " + invalid.getMessage());
            fail(shortMessage(invalid.getMessage()));
        }
    }

    private void checkCarrier(World world, Pos pos, int type) {
        if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()
                || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            throw new IllegalArgumentException("Target is unloaded or outside world height: " + pos.display()
                    + ". Load the pasted area and retry.");
        }
        if (!world.getWorldBorder().isInside(new Location(world, pos.x() + .5, pos.y() + .5, pos.z() + .5))) {
            throw new IllegalArgumentException("Target is outside the world border: " + pos.display());
        }
        Material expected = type == 0 ? Material.NOTE_BLOCK : type == 1 ? Material.RED_CONCRETE : Material.PURPLE_CONCRETE;
        if (world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() != expected || !target.accepts(world, pos, type)) {
            throw new IllegalArgumentException("Expected " + expected + " at " + pos.display()
                    + ". Check transmitter coordinates, rotation, mirror and original file.");
        }
    }

    private void fail(String message) {
        if (pending == null) return;
        Pending previous = pending; pending = null;
        Player player = Bukkit.getPlayer(previous.player);
        if (player != null && player.isOnline()) reply(player, new Status(previous.assembly.begin().id(),
                ProjectionImport.REJECTED, previous.checked, previous.assembly.begin().total(), shortMessage(message)));
    }

    private void reply(Player player, Status status) {
        if (player.getListeningPluginChannels().contains(ProjectionImport.STATUS_CHANNEL)) {
            player.sendPluginMessage(plugin, ProjectionImport.STATUS_CHANNEL, ProjectionImport.encodeStatus(status));
        }
    }
    private static String shortMessage(String message) {
        if (message == null) return "Restore failed; see server log";
        return message.substring(0, Math.min(message.length(), 384));
    }
    @Override public void close() { task.cancel(); fail("ENB plugin stopped; restore cancelled"); }

    private static final class Pending {
        final UUID player, world;
        final Assembly assembly;
        final long started = System.nanoTime();
        long lastMessage = started;
        Plan plan;
        int checked, progressTicks;
        Pending(UUID player, UUID world, Assembly assembly) { this.player = player; this.world = world; this.assembly = assembly; }
    }
}
