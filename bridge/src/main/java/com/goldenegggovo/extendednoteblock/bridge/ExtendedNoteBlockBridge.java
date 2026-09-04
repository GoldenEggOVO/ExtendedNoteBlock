package com.goldenegggovo.extendednoteblock.bridge;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ExtendedNoteBlockBridge extends JavaPlugin implements Listener {
    private static final String START_SOUND = "extendednoteblock:start_sound";
    private static final String UPDATE_VOLUME = "extendednoteblock:update_volume";
    private static final String STOP_SOUND = "extendednoteblock:stop_sound";

    private final Map<String, NoteConfig> notes = new HashMap<>();
    private final Map<String, BridgeItemType> objects = new HashMap<>();
    private final Map<String, UUID> activeSounds = new HashMap<>();
    private final Map<String, BukkitTask> activeTasks = new HashMap<>();
    private NamespacedKey bridgeTypeKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bridgeTypeKey = new NamespacedKey(this, "enb_type");
        loadNotes();
        loadObjects();

        boolean migrated = false;
        for (String key : notes.keySet()) {
            if (!objects.containsKey(key)) {
                objects.put(key, BridgeItemType.EXTENDED_NOTE_BLOCK);
                migrated = true;
            }
        }
        if (migrated) saveObjects();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, START_SOUND);
        getServer().getMessenger().registerOutgoingPluginChannel(this, UPDATE_VOLUME);
        getServer().getMessenger().registerOutgoingPluginChannel(this, STOP_SOUND);
        getLogger().info("ExtendedNoteBlockBridge enabled for Paper/Purpur 26.2");
        getLogger().info("Vanilla carriers enabled for all ExtendedNoteBlock logical items.");
    }

    @Override
    public void onDisable() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
        activeSounds.clear();
        saveNotes();
        saveObjects();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        BridgeItemType type = getBridgeItemType(event.getItemInHand());
        if (type == null || !type.placeable) return;

        Block block = event.getBlockPlaced();
        if (block.getType() != type.carrier) return;

        String key = key(block);
        objects.put(key, type);
        if (type == BridgeItemType.EXTENDED_NOTE_BLOCK) {
            notes.putIfAbsent(key, defaultNoteConfig());
            saveNotes();
        }
        saveObjects();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = key(block);
        BridgeItemType type = objects.remove(key);
        if (type == null) return;

        if (type == BridgeItemType.EXTENDED_NOTE_BLOCK) {
            notes.remove(key);
            stopActive(key);
            saveNotes();
        }
        saveObjects();

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), createBridgeItem(type, 1));
        }
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
        activeSounds.remove(key);
    }

    private void sendToListeningPlayers(Location origin, String channel, byte[] payload) {
        for (Player player : origin.getWorld().getPlayers()) {
            if (!player.getListeningPluginChannels().contains(channel)) continue;
            player.sendPluginMessage(this, channel, payload);
        }
    }

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
                for (String key : notes.keySet()) objects.putIfAbsent(key, BridgeItemType.EXTENDED_NOTE_BLOCK);
                sender.sendMessage("ExtendedNoteBlockBridge reloaded.");
            }
            case "give" -> {
                Player player = (Player) sender;
                if (args.length < 2) {
                    sender.sendMessage("Usage: /enb give <note|transmitter|receiver|projection|wand|all> [amount]");
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = clamp(Integer.parseInt(args[2]), 1, 64);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Amount must be an integer.");
                        return true;
                    }
                }
                if (args[1].equalsIgnoreCase("all")) {
                    for (BridgeItemType type : BridgeItemType.values()) {
                        player.getInventory().addItem(createBridgeItem(type, amount));
                    }
                    sender.sendMessage("Given all ExtendedNoteBlock vanilla carriers.");
                    return true;
                }
                BridgeItemType type = BridgeItemType.fromToken(args[1]);
                if (type == null) {
                    sender.sendMessage("Unknown type. Use note, transmitter, receiver, projection, wand, or all.");
                    return true;
                }
                player.getInventory().addItem(createBridgeItem(type, amount));
                sender.sendMessage("Given " + amount + "x " + type.displayName + ".");
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /enb set <note> <instrument> [velocity] [sustainTicks] [delayMs] [fadeIn] [fadeOut]");
                    return true;
                }
                Player player = (Player) sender;
                Block block = targetNoteBlock(player, true);
                if (block == null) return true;
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
                    notes.put(key, new NoteConfig(note, instrument, velocity, sustain, delay, fadeIn, fadeOut));
                    saveObjects();
                    saveNotes();
                    sender.sendMessage("Configured ENB note block: MIDI " + note + ", instrument " + instrument);
                } catch (NumberFormatException e) {
                    sender.sendMessage("All values must be integers.");
                }
            }
            case "info" -> {
                Player player = (Player) sender;
                Block block = player.getTargetBlockExact(6);
                if (block == null) {
                    sender.sendMessage("Look at an ENB bridge block within 6 blocks.");
                    return true;
                }
                String key = key(block);
                BridgeItemType type = objects.get(key);
                if (type == null) {
                    sender.sendMessage("This is not an ExtendedNoteBlock bridge object.");
                    return true;
                }
                sender.sendMessage(type.displayName + " -> vanilla carrier " + type.carrier.name());
                if (type == BridgeItemType.EXTENDED_NOTE_BLOCK) {
                    NoteConfig cfg = notes.get(key);
                    if (cfg != null) sender.sendMessage(cfg.toString());
                }
            }
            case "remove" -> {
                Player player = (Player) sender;
                Block block = player.getTargetBlockExact(6);
                if (block == null) {
                    sender.sendMessage("Look at an ENB bridge block within 6 blocks.");
                    return true;
                }
                String key = key(block);
                BridgeItemType removed = objects.remove(key);
                notes.remove(key);
                stopActive(key);
                saveObjects();
                saveNotes();
                sender.sendMessage(removed == null ? "This block was not managed by ENB." : "Removed ENB identity from this vanilla block.");
            }
            case "play" -> {
                Player player = (Player) sender;
                Block block = targetNoteBlock(player, false);
                if (block == null) return true;
                NoteConfig cfg = notes.get(key(block));
                if (cfg == null) sender.sendMessage("This note block is not configured.");
                else startConfiguredSound(block, cfg);
            }
            case "list" -> {
                for (BridgeItemType type : BridgeItemType.values()) {
                    sender.sendMessage(type.id + " -> " + type.carrier.name() + " (" + type.displayName + ")");
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/enb give <note|transmitter|receiver|projection|wand|all> [amount]");
        sender.sendMessage("/enb set <note 0-127> <instrument 0-128> [velocity] [sustainTicks] [delayMs] [fadeIn] [fadeOut]");
        sender.sendMessage("/enb info | /enb remove | /enb play | /enb list | /enb reload");
    }

    private Block targetNoteBlock(Player player, boolean autoConvert) {
        Block block = player.getTargetBlockExact(6);
        if (block == null || block.getType() != Material.NOTE_BLOCK) {
            player.sendMessage("Look at a note block within 6 blocks.");
            return null;
        }
        String key = key(block);
        if (objects.get(key) != BridgeItemType.EXTENDED_NOTE_BLOCK) {
            if (!autoConvert) {
                player.sendMessage("This note block is not an Extended Note Block bridge object.");
                return null;
            }
            objects.put(key, BridgeItemType.EXTENDED_NOTE_BLOCK);
            notes.putIfAbsent(key, defaultNoteConfig());
            saveObjects();
            saveNotes();
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
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(new File(getDataFolder(), "objects.yml"));
        } catch (IOException e) {
            getLogger().severe("Failed to save objects.yml: " + e.getMessage());
        }
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
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(new File(getDataFolder(), "notes.yml"));
        } catch (IOException e) {
            getLogger().severe("Failed to save notes.yml: " + e.getMessage());
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
