package com.goldenegggovo.extendednoteblock.bridge;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ExtendedNoteBlockBridge extends JavaPlugin implements Listener {
    private static final String START_SOUND = "extendednoteblock:start_sound";
    private static final String UPDATE_VOLUME = "extendednoteblock:update_volume";
    private static final String STOP_SOUND = "extendednoteblock:stop_sound";
    private static final String START_ADV_SOUND = "extendednoteblock:start_adv_sound";
    private static final String ADV_UPDATE = "extendednoteblock:adv_update";

    private final Map<String, NoteConfig> notes = new HashMap<>();
    private final Map<String, BridgeItemType> objects = new HashMap<>();
    private final Set<String> transmitterKeys = new HashSet<>();
    private final Set<String> receiverKeys = new HashSet<>();
    private final Set<String> projectionReceiverKeys = new HashSet<>();

    private final Map<String, UUID> activeSounds = new HashMap<>();
    private final Map<String, BukkitTask> activeTasks = new HashMap<>();

    private final Map<UUID, WandSelection> wandSelections = new HashMap<>();

    private final Map<String, Boolean> transmitterPower = new HashMap<>();
    private final Map<String, String> transmitterProjectionTarget = new HashMap<>();
    private final Map<UUID, Boolean> globalPower = new HashMap<>();

    private final Map<String, List<ProjectionNote>> projectionNotes = new HashMap<>();
    private final Map<String, ProjectionSession> projectionSessions = new HashMap<>();
    private final Map<String, Set<UUID>> projectionActiveSounds = new HashMap<>();
    private final Map<UUID, BukkitTask> projectionStopTasks = new HashMap<>();

    private NamespacedKey bridgeTypeKey;
    private BukkitTask logicTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bridgeTypeKey = new NamespacedKey(this, "enb_type");
        loadNotes();
        loadObjects();
        loadProjections();

        boolean migrated = false;
        for (String key : notes.keySet()) {
            if (!objects.containsKey(key)) {
                objects.put(key, BridgeItemType.EXTENDED_NOTE_BLOCK);
                migrated = true;
            }
        }
        if (migrated) saveObjects();
        rebuildObjectIndexes();

        getServer().getPluginManager().registerEvents(this, this);
        for (String channel : List.of(START_SOUND, UPDATE_VOLUME, STOP_SOUND, START_ADV_SOUND, ADV_UPDATE)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
        }

        long pollPeriod = Math.max(1L, getConfig().getLong("wireless-redstone.poll-period-ticks", 1L));
        logicTask = Bukkit.getScheduler().runTaskTimer(this, this::tickBridgeLogic, 1L, pollPeriod);

        getLogger().info("ExtendedNoteBlockBridge enabled for Paper/Purpur 26.2");
        getLogger().info("Core bridge features: vanilla carriers, conductor selection, wireless redstone, projection playback.");
    }

    @Override
    public void onDisable() {
        if (logicTask != null) logicTask.cancel();
        activeTasks.values().forEach(BukkitTask::cancel);
        projectionStopTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
        projectionStopTasks.clear();
        activeSounds.clear();
        projectionSessions.clear();
        projectionActiveSounds.clear();
        saveNotes();
        saveObjects();
        saveProjections();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        BridgeItemType type = getBridgeItemType(event.getItemInHand());
        if (type == null || !type.placeable) return;

        Block block = event.getBlockPlaced();
        if (block.getType() != type.carrier) return;

        String key = key(block);
        objects.put(key, type);
        indexObject(key, type);
        if (type == BridgeItemType.EXTENDED_NOTE_BLOCK) {
            notes.putIfAbsent(key, defaultNoteConfig());
            saveNotes();
        } else if (type == BridgeItemType.NBS_PROJECTION_RECEIVER) {
            projectionNotes.putIfAbsent(key, new ArrayList<>());
            saveProjections();
        } else if (type == BridgeItemType.GLOBAL_REDSTONE_RECEIVER) {
            Bukkit.getScheduler().runTask(this,
                    () -> setReceiverPowered(block, globalPower.getOrDefault(block.getWorld().getUID(), false)));
        }
        saveObjects();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = key(block);
        BridgeItemType type = objects.remove(key);
        if (type == null) return;

        unindexObject(key, type);
        transmitterPower.remove(key);
        transmitterProjectionTarget.remove(key);

        if (type == BridgeItemType.EXTENDED_NOTE_BLOCK) {
            notes.remove(key);
            stopActive(key);
            saveNotes();
        } else if (type == BridgeItemType.NBS_PROJECTION_RECEIVER) {
            stopProjection(key);
            projectionNotes.remove(key);
            saveProjections();
        }
        saveObjects();

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), createBridgeItem(type, 1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack held = event.getItem();
        if (getBridgeItemType(held) != BridgeItemType.CONDUCTOR_WAND) return;
        if (event.getClickedBlock() == null) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        BlockRef pos = BlockRef.of(event.getClickedBlock());
        WandSelection old = wandSelections.getOrDefault(player.getUniqueId(), new WandSelection(null, null));
        WandSelection updated;
        if (action == Action.LEFT_CLICK_BLOCK) {
            updated = new WandSelection(pos, old.pos2());
            player.sendMessage("ENB Conductor: Pos1 = " + pos.shortText());
        } else {
            updated = new WandSelection(old.pos1(), pos);
            player.sendMessage("ENB Conductor: Pos2 = " + pos.shortText());
        }
        wandSelections.put(player.getUniqueId(), updated);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        String key = key(event.getBlock());
        if (objects.get(key) != BridgeItemType.EXTENDED_NOTE_BLOCK) return;
        NoteConfig cfg = notes.get(key);
        if (cfg == null) return;

        event.setCancelled(true);
        stopActive(key);
        long delayTicks = Math.max(0L, Math.round(cfg.delayMs / 50.0));
        Bukkit.getScheduler().runTaskLater(this, () -> startConfiguredSound(event.getBlock(), cfg), delayTicks);
    }

    private void startConfiguredSound(Block block, NoteConfig cfg) {
        String key = key(block);
        UUID soundId = UUID.randomUUID();
        activeSounds.put(key, soundId);

        float initialVolume = cfg.fadeInTicks <= 1 ? cfg.velocity / 127.0f : 0.0001f;
        Location origin = block.getLocation().add(0.5, 0.5, 0.5);

        sendToListeningPlayers(origin, START_SOUND,
                PayloadCodec.startSound(block.getX(), block.getY(), block.getZ(), soundId,
                        cfg.instrumentId, cfg.note, cfg.velocity, initialVolume));
        playVanillaFallback(origin, cfg);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int tick = 0;

            @Override
            public void run() {
                UUID current = activeSounds.get(key);
                if (!soundId.equals(current)) return;

                if (tick >= cfg.sustainTicks) {
                    sendToListeningPlayers(origin, STOP_SOUND, PayloadCodec.stopSound(soundId));
                    stopActive(key);
                    return;
                }

                float volume = volumeAtTick(cfg, tick);
                sendToListeningPlayers(origin, UPDATE_VOLUME, PayloadCodec.updateVolume(soundId, volume));
                tick++;
            }
        }, 1L, 1L);

        activeTasks.put(key, task);
    }

    private void playVanillaFallback(Location origin, NoteConfig cfg) {
        Sound sound = vanillaSoundForInstrument(cfg.instrumentId);
        float volume = Math.max(0.0f, Math.min(1.0f, cfg.velocity / 127.0f));
        float pitch = vanillaPitchForMidi(cfg.note);
        for (Player player : origin.getWorld().getPlayers()) {
            if (supportsExtendedNoteBlock(player)) continue;
            player.playSound(origin, sound, SoundCategory.RECORDS, volume, pitch);
        }
    }

    private boolean supportsExtendedNoteBlock(Player player) {
        return player.getListeningPluginChannels().contains(START_SOUND);
    }

    private float vanillaPitchForMidi(int midiNote) {
        int vanillaMidi = clamp(midiNote, 54, 78);
        return (float) Math.pow(2.0, (vanillaMidi - 66) / 12.0);
    }

    private Sound vanillaSoundForInstrument(int instrumentId) {
        int instrument = clamp(instrumentId, 0, 128);
        if (instrument == 128) return Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
        if (instrument <= 7) return Sound.BLOCK_NOTE_BLOCK_HARP;
        if (instrument <= 15) return Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
        if (instrument <= 23) return Sound.BLOCK_NOTE_BLOCK_FLUTE;
        if (instrument <= 31) return Sound.BLOCK_NOTE_BLOCK_GUITAR;
        if (instrument <= 39) return Sound.BLOCK_NOTE_BLOCK_BASS;
        if (instrument <= 47) return Sound.BLOCK_NOTE_BLOCK_HARP;
        if (instrument <= 55) return Sound.BLOCK_NOTE_BLOCK_CHIME;
        if (instrument <= 63) return Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
        if (instrument <= 71) return Sound.BLOCK_NOTE_BLOCK_FLUTE;
        if (instrument <= 79) return Sound.BLOCK_NOTE_BLOCK_FLUTE;
        if (instrument <= 87) return Sound.BLOCK_NOTE_BLOCK_BIT;
        if (instrument <= 95) return Sound.BLOCK_NOTE_BLOCK_CHIME;
        if (instrument <= 103) return Sound.BLOCK_NOTE_BLOCK_PLING;
        if (instrument <= 111) return Sound.BLOCK_NOTE_BLOCK_BANJO;
        if (instrument <= 119) return Sound.BLOCK_NOTE_BLOCK_COW_BELL;
        return Sound.BLOCK_NOTE_BLOCK_PLING;
    }

    private float volumeAtTick(NoteConfig cfg, int tick) {
        float base = cfg.velocity / 127.0f;
        float multiplier = 1.0f;
        if (cfg.fadeInTicks > 0 && tick < cfg.fadeInTicks) {
            multiplier = Math.min(multiplier, tick / (float) cfg.fadeInTicks);
        }
        if (cfg.fadeOutTicks > 0) {
            int fadeStart = Math.max(0, cfg.sustainTicks - cfg.fadeOutTicks);
            if (tick >= fadeStart) {
                float remain = (cfg.sustainTicks - tick) / (float) cfg.fadeOutTicks;
                multiplier = Math.min(multiplier, Math.max(0.0f, remain));
            }
        }
        return Math.max(0.0001f, base * multiplier);
    }

    private void stopActive(String key) {
        BukkitTask task = activeTasks.remove(key);
        if (task != null) task.cancel();
        UUID id = activeSounds.remove(key);
        Block block = getLoadedBlock(key);
        if (id != null && block != null) {
            sendToListeningPlayers(block.getLocation(), STOP_SOUND, PayloadCodec.stopSound(id));
        }
    }

    private void sendToListeningPlayers(Location origin, String channel, byte[] payload) {
        double radius = Math.max(1.0, getConfig().getDouble("audible-radius", 64.0));
        double radiusSq = radius * radius;
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) > radiusSq) continue;
            if (!player.getListeningPluginChannels().contains(channel)) continue;
            player.sendPluginMessage(this, channel, payload);
        }
    }

    // -------------------------------------------------------------------------
    // Wireless redstone + dedicated projection routing
    // -------------------------------------------------------------------------

    private void tickBridgeLogic() {
        Map<UUID, Boolean> poweredByWorld = new HashMap<>();
        Set<UUID> knownWorlds = new HashSet<>(globalPower.keySet());

        for (String transmitterKey : List.copyOf(transmitterKeys)) {
            BlockRef ref = parseKey(transmitterKey);
            if (ref == null) continue;
            knownWorlds.add(ref.worldId());

            Block transmitter = getLoadedBlock(transmitterKey);
            boolean powered = transmitterPower.getOrDefault(transmitterKey, false);
            String projectionTarget = transmitterProjectionTarget.get(transmitterKey);

            if (transmitter != null) {
                if (transmitter.getType() != Material.RED_CONCRETE) {
                    powered = false;
                } else {
                    powered = transmitter.getBlockPower() > 0;
                }
                projectionTarget = findAdjacentProjectionKey(transmitter);
                transmitterPower.put(transmitterKey, powered);
                if (projectionTarget == null) transmitterProjectionTarget.remove(transmitterKey);
                else transmitterProjectionTarget.put(transmitterKey, projectionTarget);
            }

            boolean previous = transmitterPower.getOrDefault(transmitterKey, false);
            if (projectionTarget != null) {
                ProjectionSession session = projectionSessions.get(projectionTarget);
                boolean currentlyRunning = session != null;
                if (powered && !currentlyRunning && transmitter != null) {
                    startProjection(projectionTarget, transmitter);
                } else if (!powered && currentlyRunning) {
                    stopProjection(projectionTarget);
                }
            } else if (powered) {
                poweredByWorld.put(ref.worldId(), true);
            }

            transmitterPower.put(transmitterKey, powered);
            if (previous && !powered && projectionTarget != null) {
                stopProjection(projectionTarget);
            }
        }

        for (UUID worldId : knownWorlds) {
            boolean newPower = poweredByWorld.getOrDefault(worldId, false);
            boolean oldPower = globalPower.getOrDefault(worldId, false);
            if (newPower != oldPower) {
                globalPower.put(worldId, newPower);
                updateReceivers(worldId, newPower);
            }
        }

        tickProjectionSessions();
    }

    private void updateReceivers(UUID worldId, boolean powered) {
        for (String receiverKey : List.copyOf(receiverKeys)) {
            BlockRef ref = parseKey(receiverKey);
            if (ref == null || !ref.worldId().equals(worldId)) continue;
            Block block = getLoadedBlock(receiverKey);
            if (block != null) setReceiverPowered(block, powered);
        }
    }

    private void setReceiverPowered(Block block, boolean powered) {
        Material desired = powered ? Material.REDSTONE_BLOCK : Material.GREEN_CONCRETE;
        if (block.getType() != desired) {
            block.setType(desired, true);
        }
    }

    private String findAdjacentProjectionKey(Block transmitter) {
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            Block candidate = transmitter.getRelative(offset[0], offset[1], offset[2]);
            String candidateKey = key(candidate);
            if (projectionReceiverKeys.contains(candidateKey)
                    && objects.get(candidateKey) == BridgeItemType.NBS_PROJECTION_RECEIVER) {
                return candidateKey;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // NBS projection playback core
    // -------------------------------------------------------------------------

    private void startProjection(String receiverKey, Block playbackBlock) {
        stopProjection(receiverKey);
        List<ProjectionNote> timeline = projectionNotes.getOrDefault(receiverKey, List.of());
        if (timeline.isEmpty()) return;
        List<ProjectionNote> sorted = new ArrayList<>(timeline);
        sorted.sort(java.util.Comparator.comparingLong(ProjectionNote::delayMs));
        projectionSessions.put(receiverKey,
                new ProjectionSession(receiverKey, key(playbackBlock), List.copyOf(sorted), System.nanoTime(), 0));
    }

    private void stopProjection(String receiverKey) {
        projectionSessions.remove(receiverKey);
        Set<UUID> ids = projectionActiveSounds.remove(receiverKey);
        if (ids == null || ids.isEmpty()) return;
        Block receiver = getLoadedBlock(receiverKey);
        Location origin = receiver == null ? null : receiver.getLocation();
        for (UUID id : List.copyOf(ids)) {
            BukkitTask stopTask = projectionStopTasks.remove(id);
            if (stopTask != null) stopTask.cancel();
            if (origin != null) sendToListeningPlayers(origin, STOP_SOUND, PayloadCodec.stopSound(id));
        }
    }

    private void tickProjectionSessions() {
        long now = System.nanoTime();
        for (Map.Entry<String, ProjectionSession> entry : List.copyOf(projectionSessions.entrySet())) {
            String receiverKey = entry.getKey();
            ProjectionSession session = entry.getValue();
            if (!projectionReceiverKeys.contains(receiverKey)) {
                stopProjection(receiverKey);
                continue;
            }
            Block playback = getLoadedBlock(session.playbackKey());
            if (playback == null) continue;

            long elapsedMs = Math.max(0L, (now - session.startedNanos()) / 1_000_000L);
            int next = session.nextNote();
            while (next < session.notes().size() && session.notes().get(next).delayMs() <= elapsedMs) {
                playProjectionNote(receiverKey, playback, session.notes().get(next));
                next++;
            }
            if (next >= session.notes().size()) {
                projectionSessions.remove(receiverKey);
            } else if (next != session.nextNote()) {
                projectionSessions.put(receiverKey,
                        new ProjectionSession(receiverKey, session.playbackKey(), session.notes(), session.startedNanos(), next));
            }
        }
    }

    private void playProjectionNote(String receiverKey, Block playback, ProjectionNote note) {
        Location origin = playback.getLocation().add(0.5, 0.5, 0.5);
        UUID soundId = UUID.randomUUID();
        float volume = Math.max(0.0001f, note.velocity() / 127.0f);

        if (note.pitchCents() == 0) {
            sendToListeningPlayers(origin, START_SOUND,
                    PayloadCodec.startSound(playback.getX(), playback.getY(), playback.getZ(), soundId,
                            note.instrumentId(), note.midiNote(), note.velocity(), volume));
        } else {
            float pitchMul = (float) Math.pow(2.0, note.pitchCents() / 1200.0);
            sendToListeningPlayers(origin, START_ADV_SOUND,
                    PayloadCodec.startAdvancedSound(playback.getX(), playback.getY(), playback.getZ(), soundId,
                            note.instrumentId(), note.midiNote(), volume, pitchMul,
                            origin.getX(), origin.getY(), origin.getZ()));
        }

        playVanillaProjectionFallback(origin, note);
        projectionActiveSounds.computeIfAbsent(receiverKey, ignored -> new HashSet<>()).add(soundId);
        long sustain = Math.max(1L, note.sustainTicks());
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            sendToListeningPlayers(origin, STOP_SOUND, PayloadCodec.stopSound(soundId));
            projectionStopTasks.remove(soundId);
            Set<UUID> ids = projectionActiveSounds.get(receiverKey);
            if (ids != null) {
                ids.remove(soundId);
                if (ids.isEmpty()) projectionActiveSounds.remove(receiverKey);
            }
        }, sustain);
        projectionStopTasks.put(soundId, task);
    }

    private void playVanillaProjectionFallback(Location origin, ProjectionNote note) {
        Sound sound = vanillaSoundForInstrument(note.instrumentId());
        float volume = Math.max(0.0f, Math.min(1.0f, note.velocity() / 127.0f));
        float pitch = vanillaPitchForMidi(note.midiNote()) * (float) Math.pow(2.0, note.pitchCents() / 1200.0);
        pitch = Math.max(0.5f, Math.min(2.0f, pitch));
        for (Player player : origin.getWorld().getPlayers()) {
            if (supportsExtendedNoteBlock(player)) continue;
            player.playSound(origin, sound, SoundCategory.RECORDS, volume, pitch);
        }
    }

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) && (args.length == 0 || !args[0].equalsIgnoreCase("reload"))) {
            sender.sendMessage("This subcommand requires a player.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                loadNotes();
                loadObjects();
                loadProjections();
                for (String key : notes.keySet()) objects.putIfAbsent(key, BridgeItemType.EXTENDED_NOTE_BLOCK);
                rebuildObjectIndexes();
                sender.sendMessage("ExtendedNoteBlockBridge reloaded.");
            }
            case "give" -> handleGive((Player) sender, args);
            case "set" -> handleSet((Player) sender, args);
            case "info" -> handleInfo((Player) sender);
            case "remove" -> handleRemove((Player) sender);
            case "play" -> handlePlay((Player) sender);
            case "list" -> {
                for (BridgeItemType type : BridgeItemType.values()) {
                    sender.sendMessage(type.id + " -> " + type.carrier.name() + " (" + type.displayName + ")");
                }
                sender.sendMessage("Receiver active state -> REDSTONE_BLOCK (real vanilla redstone output)");
            }
            case "wand" -> handleWand((Player) sender, args);
            case "projection" -> handleProjection((Player) sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleGive(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /enb give <note|transmitter|receiver|projection|wand|all> [amount]");
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = clamp(Integer.parseInt(args[2]), 1, 64);
            } catch (NumberFormatException e) {
                player.sendMessage("Amount must be an integer.");
                return;
            }
        }
        if (args[1].equalsIgnoreCase("all")) {
            for (BridgeItemType type : BridgeItemType.values()) {
                player.getInventory().addItem(createBridgeItem(type, amount));
            }
            player.sendMessage("Given all ExtendedNoteBlock vanilla carriers.");
            return;
        }
        BridgeItemType type = BridgeItemType.fromToken(args[1]);
        if (type == null) {
            player.sendMessage("Unknown type. Use note, transmitter, receiver, projection, wand, or all.");
            return;
        }
        player.getInventory().addItem(createBridgeItem(type, amount));
        player.sendMessage("Given " + amount + "x " + type.displayName + ".");
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("Usage: /enb set <note> <instrument> [velocity] [sustainTicks] [delayMs] [fadeIn] [fadeOut]");
            return;
        }
        Block block = targetNoteBlock(player, true);
        if (block == null) return;
        try {
            int note = clamp(Integer.parseInt(args[1]), 0, 127);
            int instrument = clamp(Integer.parseInt(args[2]), 0, 128);
            int velocity = args.length > 3 ? clamp(Integer.parseInt(args[3]), 0, 127) : 100;
            int sustain = args.length > 4 ? clamp(Integer.parseInt(args[4]), 1, 400) : 20;
            int delay = args.length > 5 ? clamp(Integer.parseInt(args[5]), 0, 600000) : 0;
            int fadeIn = args.length > 6 ? clamp(Integer.parseInt(args[6]), 0, 400) : 0;
            int fadeOut = args.length > 7 ? clamp(Integer.parseInt(args[7]), 0, 400) : 3;
            String key = key(block);
            objects.put(key, BridgeItemType.EXTENDED_NOTE_BLOCK);
            indexObject(key, BridgeItemType.EXTENDED_NOTE_BLOCK);
            notes.put(key, new NoteConfig(note, instrument, velocity, sustain, delay, fadeIn, fadeOut));
            saveObjects();
            saveNotes();
            player.sendMessage("Configured ENB note block: MIDI " + note + ", instrument " + instrument);
        } catch (NumberFormatException e) {
            player.sendMessage("All values must be integers.");
        }
    }

    private void handleInfo(Player player) {
        Block block = player.getTargetBlockExact(interactionRange());
        if (block == null) {
            player.sendMessage("Look at an ENB bridge block within range.");
            return;
        }
        String key = key(block);
        BridgeItemType type = objects.get(key);
        if (type == null) {
            player.sendMessage("This is not an ExtendedNoteBlock bridge object.");
            return;
        }
        player.sendMessage(type.displayName + " -> vanilla carrier " + type.carrier.name());
        if (type == BridgeItemType.EXTENDED_NOTE_BLOCK) {
            NoteConfig cfg = notes.get(key);
            if (cfg != null) player.sendMessage(cfg.toString());
        } else if (type == BridgeItemType.NBS_PROJECTION_RECEIVER) {
            player.sendMessage("Projection notes: " + projectionNotes.getOrDefault(key, List.of()).size());
        }
    }

    private void handleRemove(Player player) {
        Block block = player.getTargetBlockExact(interactionRange());
        if (block == null) {
            player.sendMessage("Look at an ENB bridge block within range.");
            return;
        }
        String key = key(block);
        BridgeItemType removed = objects.remove(key);
        if (removed != null) unindexObject(key, removed);
        notes.remove(key);
        projectionNotes.remove(key);
        stopActive(key);
        stopProjection(key);
        saveObjects();
        saveNotes();
        saveProjections();
        player.sendMessage(removed == null ? "This block was not managed by ENB." : "Removed ENB identity from this vanilla block.");
    }

    private void handlePlay(Player player) {
        Block block = targetNoteBlock(player, false);
        if (block == null) return;
        NoteConfig cfg = notes.get(key(block));
        if (cfg == null) player.sendMessage("This note block is not configured.");
        else startConfiguredSound(block, cfg);
    }

    private void handleWand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("/enb wand info | clear | set <note|instrument|velocity|sustain|delay|fadein|fadeout> <value>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("clear")) {
            wandSelections.remove(player.getUniqueId());
            player.sendMessage("Conductor selection cleared.");
            return;
        }
        WandSelection selection = wandSelections.get(player.getUniqueId());
        if (action.equals("info")) {
            if (selection == null || !selection.complete()) {
                player.sendMessage("Selection incomplete. Left-click sets Pos1, right-click sets Pos2.");
            } else {
                player.sendMessage("Pos1 " + selection.pos1().shortText() + " / Pos2 " + selection.pos2().shortText()
                        + " / volume " + selection.volume());
            }
            return;
        }
        if (!action.equals("set") || args.length < 4) {
            player.sendMessage("Usage: /enb wand set <note|instrument|velocity|sustain|delay|fadein|fadeout> <value>");
            return;
        }
        if (selection == null || !selection.complete()) {
            player.sendMessage("Selection incomplete. Left-click sets Pos1, right-click sets Pos2.");
            return;
        }
        if (!selection.sameWorld()) {
            player.sendMessage("Both selection points must be in the same world.");
            return;
        }
        long maxVolume = Math.max(1L, getConfig().getLong("wand.max-selection-volume", 262144L));
        if (selection.volume() > maxVolume) {
            player.sendMessage("Selection is too large: " + selection.volume() + " > " + maxVolume);
            return;
        }
        try {
            int value = Integer.parseInt(args[3]);
            int changed = bulkSet(selection, args[2].toLowerCase(Locale.ROOT), value);
            saveNotes();
            player.sendMessage("Updated " + changed + " Extended Note Block(s).");
        } catch (IllegalArgumentException e) {
            player.sendMessage(e.getMessage());
        }
    }

    private int bulkSet(WandSelection selection, String property, int rawValue) {
        int value = switch (property) {
            case "note" -> clamp(rawValue, 0, 127);
            case "instrument" -> clamp(rawValue, 0, 128);
            case "velocity" -> clamp(rawValue, 0, 127);
            case "sustain" -> clamp(rawValue, 1, 400);
            case "delay" -> clamp(rawValue, 0, 600000);
            case "fadein", "fadeout" -> clamp(rawValue, 0, 400);
            default -> throw new IllegalArgumentException("Unknown property: " + property);
        };

        int changed = 0;
        for (Map.Entry<String, NoteConfig> entry : new ArrayList<>(notes.entrySet())) {
            BlockRef ref = parseKey(entry.getKey());
            if (ref == null || !selection.contains(ref)) continue;
            NoteConfig old = entry.getValue();
            NoteConfig updated = switch (property) {
                case "note" -> new NoteConfig(value, old.instrumentId, old.velocity, old.sustainTicks, old.delayMs, old.fadeInTicks, old.fadeOutTicks);
                case "instrument" -> new NoteConfig(old.note, value, old.velocity, old.sustainTicks, old.delayMs, old.fadeInTicks, old.fadeOutTicks);
                case "velocity" -> new NoteConfig(old.note, old.instrumentId, value, old.sustainTicks, old.delayMs, old.fadeInTicks, old.fadeOutTicks);
                case "sustain" -> new NoteConfig(old.note, old.instrumentId, old.velocity, value, old.delayMs, old.fadeInTicks, old.fadeOutTicks);
                case "delay" -> new NoteConfig(old.note, old.instrumentId, old.velocity, old.sustainTicks, value, old.fadeInTicks, old.fadeOutTicks);
                case "fadein" -> new NoteConfig(old.note, old.instrumentId, old.velocity, old.sustainTicks, old.delayMs, value, old.fadeOutTicks);
                case "fadeout" -> new NoteConfig(old.note, old.instrumentId, old.velocity, old.sustainTicks, old.delayMs, old.fadeInTicks, value);
                default -> old;
            };
            notes.put(entry.getKey(), updated);
            changed++;
        }
        return changed;
    }

    private void handleProjection(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("/enb projection info | clear | test | add <delayMs> <note> <instrument> [velocity] [sustainTicks] [pitchCents]");
            return;
        }
        Block receiver = targetBridgeBlock(player, BridgeItemType.NBS_PROJECTION_RECEIVER);
        if (receiver == null) return;
        String receiverKey = key(receiver);
        String action = args[1].toLowerCase(Locale.ROOT);

        switch (action) {
            case "info" -> player.sendMessage("Projection notes: " + projectionNotes.getOrDefault(receiverKey, List.of()).size());
            case "clear" -> {
                stopProjection(receiverKey);
                projectionNotes.put(receiverKey, new ArrayList<>());
                saveProjections();
                player.sendMessage("Projection timeline cleared.");
            }
            case "test" -> {
                startProjection(receiverKey, receiver);
                player.sendMessage("Projection test started.");
            }
            case "add" -> {
                if (args.length < 5) {
                    player.sendMessage("Usage: /enb projection add <delayMs> <note> <instrument> [velocity] [sustainTicks] [pitchCents]");
                    return;
                }
                try {
                    long delayMs = Math.max(0L, Long.parseLong(args[2]));
                    int note = clamp(Integer.parseInt(args[3]), 0, 127);
                    int instrument = clamp(Integer.parseInt(args[4]), 0, 128);
                    int velocity = args.length > 5 ? clamp(Integer.parseInt(args[5]), 0, 127) : 100;
                    int sustain = args.length > 6 ? clamp(Integer.parseInt(args[6]), 1, 400) : 20;
                    int pitchCents = args.length > 7 ? clamp(Integer.parseInt(args[7]), -2400, 2400) : 0;
                    projectionNotes.computeIfAbsent(receiverKey, ignored -> new ArrayList<>())
                            .add(new ProjectionNote(instrument, note, velocity, sustain, pitchCents, delayMs));
                    saveProjections();
                    player.sendMessage("Added projection note at " + delayMs + "ms.");
                } catch (NumberFormatException e) {
                    player.sendMessage("Projection values must be integers.");
                }
            }
            default -> player.sendMessage("Unknown projection action.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/enb give <note|transmitter|receiver|projection|wand|all> [amount]");
        sender.sendMessage("/enb set <note 0-127> <instrument 0-128> [velocity] [sustainTicks] [delayMs] [fadeIn] [fadeOut]");
        sender.sendMessage("/enb wand info|clear|set ...");
        sender.sendMessage("/enb projection info|clear|test|add ...");
        sender.sendMessage("/enb info | /enb remove | /enb play | /enb list | /enb reload");
    }

    // -------------------------------------------------------------------------
    // Object / persistence helpers
    // -------------------------------------------------------------------------

    private int interactionRange() {
        return Math.max(1, getConfig().getInt("interaction-range", 6));
    }

    private Block targetNoteBlock(Player player, boolean autoConvert) {
        Block block = player.getTargetBlockExact(interactionRange());
        if (block == null || block.getType() != Material.NOTE_BLOCK) {
            player.sendMessage("Look at a note block within range.");
            return null;
        }
        String key = key(block);
        if (objects.get(key) != BridgeItemType.EXTENDED_NOTE_BLOCK) {
            if (!autoConvert) {
                player.sendMessage("This note block is not an Extended Note Block bridge object.");
                return null;
            }
            objects.put(key, BridgeItemType.EXTENDED_NOTE_BLOCK);
            indexObject(key, BridgeItemType.EXTENDED_NOTE_BLOCK);
            notes.putIfAbsent(key, defaultNoteConfig());
            saveObjects();
            saveNotes();
        }
        return block;
    }

    private Block targetBridgeBlock(Player player, BridgeItemType expected) {
        Block block = player.getTargetBlockExact(interactionRange());
        if (block == null || objects.get(key(block)) != expected) {
            player.sendMessage("Look at a " + expected.displayName + " within range.");
            return null;
        }
        return block;
    }

    private ItemStack createBridgeItem(BridgeItemType type, int amount) {
        ItemStack stack = new ItemStack(type.carrier, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(type.displayName);
        meta.setLore(List.of(
                "ExtendedNoteBlock Bridge item",
                "Vanilla carrier: minecraft:" + type.carrier.name().toLowerCase(Locale.ROOT)));
        meta.getPersistentDataContainer().set(bridgeTypeKey, PersistentDataType.STRING, type.id);
        stack.setItemMeta(meta);
        return stack;
    }

    private BridgeItemType getBridgeItemType(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        String id = meta.getPersistentDataContainer().get(bridgeTypeKey, PersistentDataType.STRING);
        return BridgeItemType.fromId(id);
    }

    private NoteConfig defaultNoteConfig() {
        return new NoteConfig(60, 0, 100, 20, 0, 0, 3);
    }

    private String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private BlockRef parseKey(String key) {
        try {
            String[] parts = key.split(":");
            if (parts.length != 4) return null;
            return new BlockRef(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Block getLoadedBlock(String key) {
        BlockRef ref = parseKey(key);
        if (ref == null) return null;
        World world = Bukkit.getWorld(ref.worldId());
        if (world == null || !world.isChunkLoaded(ref.x() >> 4, ref.z() >> 4)) return null;
        return world.getBlockAt(ref.x(), ref.y(), ref.z());
    }

    private void rebuildObjectIndexes() {
        transmitterKeys.clear();
        receiverKeys.clear();
        projectionReceiverKeys.clear();
        for (Map.Entry<String, BridgeItemType> entry : objects.entrySet()) {
            indexObject(entry.getKey(), entry.getValue());
        }
    }

    private void indexObject(String key, BridgeItemType type) {
        switch (type) {
            case GLOBAL_REDSTONE_TRANSMITTER -> transmitterKeys.add(key);
            case GLOBAL_REDSTONE_RECEIVER -> receiverKeys.add(key);
            case NBS_PROJECTION_RECEIVER -> projectionReceiverKeys.add(key);
            default -> {
            }
        }
    }

    private void unindexObject(String key, BridgeItemType type) {
        switch (type) {
            case GLOBAL_REDSTONE_TRANSMITTER -> transmitterKeys.remove(key);
            case GLOBAL_REDSTONE_RECEIVER -> receiverKeys.remove(key);
            case NBS_PROJECTION_RECEIVER -> projectionReceiverKeys.remove(key);
            default -> {
            }
        }
    }

    private void loadObjects() {
        objects.clear();
        File file = new File(getDataFolder(), "objects.yml");
        if (!file.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            BridgeItemType type = BridgeItemType.fromId(yml.getString(key));
            if (type != null) objects.put(key, type);
        }
    }

    private void saveObjects() {
        org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, BridgeItemType> entry : objects.entrySet()) {
            yml.set(entry.getKey(), entry.getValue().id);
        }
        saveYaml(yml, "objects.yml");
    }

    private void loadNotes() {
        notes.clear();
        File file = new File(getDataFolder(), "notes.yml");
        if (!file.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            notes.put(key, new NoteConfig(
                    yml.getInt(key + ".note", 60),
                    yml.getInt(key + ".instrument", 0),
                    yml.getInt(key + ".velocity", 100),
                    yml.getInt(key + ".sustain", 20),
                    yml.getInt(key + ".delayMs", 0),
                    yml.getInt(key + ".fadeIn", 0),
                    yml.getInt(key + ".fadeOut", 3)));
        }
    }

    private void saveNotes() {
        org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, NoteConfig> entry : notes.entrySet()) {
            String p = entry.getKey();
            NoteConfig c = entry.getValue();
            yml.set(p + ".note", c.note);
            yml.set(p + ".instrument", c.instrumentId);
            yml.set(p + ".velocity", c.velocity);
            yml.set(p + ".sustain", c.sustainTicks);
            yml.set(p + ".delayMs", c.delayMs);
            yml.set(p + ".fadeIn", c.fadeInTicks);
            yml.set(p + ".fadeOut", c.fadeOutTicks);
        }
        saveYaml(yml, "notes.yml");
    }

    private void loadProjections() {
        projectionNotes.clear();
        File file = new File(getDataFolder(), "projections.yml");
        if (!file.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            int count = Math.max(0, yml.getInt(key + ".count", 0));
            List<ProjectionNote> timeline = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String p = key + ".notes." + i;
                timeline.add(new ProjectionNote(
                        yml.getInt(p + ".instrument", 0),
                        yml.getInt(p + ".note", 60),
                        yml.getInt(p + ".velocity", 100),
                        yml.getInt(p + ".sustain", 20),
                        yml.getInt(p + ".pitchCents", 0),
                        Math.max(0L, yml.getLong(p + ".delayMs", 0L))));
            }
            projectionNotes.put(key, timeline);
        }
    }

    private void saveProjections() {
        org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, List<ProjectionNote>> entry : projectionNotes.entrySet()) {
            String key = entry.getKey();
            List<ProjectionNote> timeline = entry.getValue();
            yml.set(key + ".count", timeline.size());
            for (int i = 0; i < timeline.size(); i++) {
                ProjectionNote note = timeline.get(i);
                String p = key + ".notes." + i;
                yml.set(p + ".instrument", note.instrumentId());
                yml.set(p + ".note", note.midiNote());
                yml.set(p + ".velocity", note.velocity());
                yml.set(p + ".sustain", note.sustainTicks());
                yml.set(p + ".pitchCents", note.pitchCents());
                yml.set(p + ".delayMs", note.delayMs());
            }
        }
        saveYaml(yml, "projections.yml");
    }

    private void saveYaml(org.bukkit.configuration.file.YamlConfiguration yml, String filename) {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(new File(getDataFolder(), filename));
        } catch (IOException e) {
            getLogger().severe("Failed to save " + filename + ": " + e.getMessage());
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum BridgeItemType {
        EXTENDED_NOTE_BLOCK("extended_note_block", "Extended Note Block", Material.NOTE_BLOCK, true),
        GLOBAL_REDSTONE_TRANSMITTER("global_redstone_transmitter", "Global Redstone Transmitter", Material.RED_CONCRETE, true),
        GLOBAL_REDSTONE_RECEIVER("global_redstone_receiver", "Global Redstone Receiver", Material.GREEN_CONCRETE, true),
        NBS_PROJECTION_RECEIVER("nbs_projection_receiver", "NBS Projection Receiver", Material.PURPLE_CONCRETE, true),
        CONDUCTOR_WAND("conductor_wand", "Conductor Wand", Material.BLAZE_ROD, false);

        private final String id;
        private final String displayName;
        private final Material carrier;
        private final boolean placeable;

        BridgeItemType(String id, String displayName, Material carrier, boolean placeable) {
            this.id = id;
            this.displayName = displayName;
            this.carrier = carrier;
            this.placeable = placeable;
        }

        static BridgeItemType fromId(String id) {
            if (id == null) return null;
            for (BridgeItemType type : values()) {
                if (type.id.equalsIgnoreCase(id)) return type;
            }
            return null;
        }

        static BridgeItemType fromToken(String token) {
            String value = token.toLowerCase(Locale.ROOT);
            return switch (value) {
                case "note", "noteblock", "extended_note_block", "extended" -> EXTENDED_NOTE_BLOCK;
                case "transmitter", "tx", "global_redstone_transmitter" -> GLOBAL_REDSTONE_TRANSMITTER;
                case "receiver", "rx", "global_redstone_receiver" -> GLOBAL_REDSTONE_RECEIVER;
                case "projection", "nbs", "nbs_projection_receiver" -> NBS_PROJECTION_RECEIVER;
                case "wand", "conductor", "conductor_wand" -> CONDUCTOR_WAND;
                default -> fromId(value);
            };
        }
    }

    private record NoteConfig(int note, int instrumentId, int velocity, int sustainTicks,
                              int delayMs, int fadeInTicks, int fadeOutTicks) {
        @Override
        public String toString() {
            return "MIDI=" + note + ", instrument=" + instrumentId + ", velocity=" + velocity
                    + ", sustain=" + sustainTicks + "t, delay=" + delayMs + "ms, fadeIn=" + fadeInTicks
                    + "t, fadeOut=" + fadeOutTicks + "t";
        }
    }

    private record ProjectionNote(int instrumentId, int midiNote, int velocity, int sustainTicks,
                                  int pitchCents, long delayMs) {
    }

    private record ProjectionSession(String receiverKey, String playbackKey, List<ProjectionNote> notes,
                                     long startedNanos, int nextNote) {
    }

    private record BlockRef(UUID worldId, int x, int y, int z) {
        static BlockRef of(Block block) {
            return new BlockRef(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        String shortText() {
            return x + ", " + y + ", " + z;
        }
    }

    private record WandSelection(BlockRef pos1, BlockRef pos2) {
        boolean complete() {
            return pos1 != null && pos2 != null;
        }

        boolean sameWorld() {
            return complete() && pos1.worldId().equals(pos2.worldId());
        }

        long volume() {
            if (!sameWorld()) return Long.MAX_VALUE;
            return (long) (Math.abs(pos1.x() - pos2.x()) + 1)
                    * (Math.abs(pos1.y() - pos2.y()) + 1)
                    * (Math.abs(pos1.z() - pos2.z()) + 1);
        }

        boolean contains(BlockRef ref) {
            if (!sameWorld() || !pos1.worldId().equals(ref.worldId())) return false;
            int minX = Math.min(pos1.x(), pos2.x());
            int maxX = Math.max(pos1.x(), pos2.x());
            int minY = Math.min(pos1.y(), pos2.y());
            int maxY = Math.max(pos1.y(), pos2.y());
            int minZ = Math.min(pos1.z(), pos2.z());
            int maxZ = Math.max(pos1.z(), pos2.z());
            return ref.x() >= minX && ref.x() <= maxX
                    && ref.y() >= minY && ref.y() <= maxY
                    && ref.z() >= minZ && ref.z() <= maxZ;
        }
    }

    private static final class PayloadCodec {
        private static byte[] startSound(int x, int y, int z, UUID id, int instrument, int note, int velocity, float volume) {
            return write(out -> {
                out.writeLong(packBlockPos(x, y, z));
                out.writeLong(id.getMostSignificantBits());
                out.writeLong(id.getLeastSignificantBits());
                out.writeInt(instrument);
                out.writeInt(note);
                out.writeInt(velocity);
                out.writeFloat(volume);
            });
        }

        private static byte[] startAdvancedSound(int x, int y, int z, UUID id, int instrument, int note,
                                                 float volume, float pitchMultiplier,
                                                 double soundX, double soundY, double soundZ) {
            return write(out -> {
                out.writeLong(packBlockPos(x, y, z));
                out.writeLong(id.getMostSignificantBits());
                out.writeLong(id.getLeastSignificantBits());
                out.writeInt(instrument);
                out.writeInt(note);
                out.writeFloat(volume);
                out.writeFloat(pitchMultiplier);
                out.writeDouble(soundX);
                out.writeDouble(soundY);
                out.writeDouble(soundZ);
            });
        }

        private static byte[] updateVolume(UUID id, float volume) {
            return write(out -> {
                out.writeLong(id.getMostSignificantBits());
                out.writeLong(id.getLeastSignificantBits());
                out.writeFloat(volume);
            });
        }

        private static byte[] stopSound(UUID id) {
            return write(out -> {
                out.writeLong(id.getMostSignificantBits());
                out.writeLong(id.getLeastSignificantBits());
            });
        }

        private static long packBlockPos(int x, int y, int z) {
            return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
        }

        private static byte[] write(IoWriter writer) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                writer.write(out);
                out.flush();
                return bytes.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @FunctionalInterface
        private interface IoWriter {
            void write(DataOutputStream out) throws IOException;
        }
    }
}
