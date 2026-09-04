package com.goldenegggovo.extendednoteblock.bridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExtendedNoteBlockBridge extends JavaPlugin implements Listener {
    private static final String START_SOUND = "extendednoteblock:start_sound";
    private static final String UPDATE_VOLUME = "extendednoteblock:update_volume";
    private static final String STOP_SOUND = "extendednoteblock:stop_sound";

    private final Map<String, NoteConfig> notes = new HashMap<>();
    private final Map<String, UUID> activeSounds = new HashMap<>();
    private final Map<String, BukkitTask> activeTasks = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadNotes();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, START_SOUND);
        getServer().getMessenger().registerOutgoingPluginChannel(this, UPDATE_VOLUME);
        getServer().getMessenger().registerOutgoingPluginChannel(this, STOP_SOUND);
        getLogger().info("ExtendedNoteBlockBridge enabled for Paper/Purpur 26.2");
    }

    @Override
    public void onDisable() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
        activeSounds.clear();
        saveNotes();
    }

    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        String key = key(event.getBlock());
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

        // Clients without ExtendedNoteBlock still hear an approximate vanilla note-block sound.
        // Modded clients are deliberately excluded to avoid double audio.
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

    /**
     * Vanilla note blocks cover MIDI F#3..F#5 (54..78) when represented as
     * Bukkit sound pitch 0.5..2.0. Notes outside that range are clamped to the
     * nearest note the vanilla client can reproduce.
     */
    private float vanillaPitchForMidi(int midiNote) {
        int vanillaMidi = clamp(midiNote, 54, 78);
        return (float) Math.pow(2.0, (vanillaMidi - 66) / 12.0);
    }

    /**
     * General MIDI families mapped to the closest useful vanilla note-block
     * instrument. The goal is musical recognisability for clients without the
     * Fabric mod, not a bit-perfect replacement for the ExtendedNoteBlock pack.
     */
    private Sound vanillaSoundForInstrument(int instrumentId) {
        int instrument = clamp(instrumentId, 0, 128);

        if (instrument == 128) return Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
        if (instrument <= 7) return Sound.BLOCK_NOTE_BLOCK_HARP;           // Piano
        if (instrument <= 15) return Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;     // Chromatic percussion
        if (instrument <= 23) return Sound.BLOCK_NOTE_BLOCK_FLUTE;         // Organ
        if (instrument <= 31) return Sound.BLOCK_NOTE_BLOCK_GUITAR;        // Guitar
        if (instrument <= 39) return Sound.BLOCK_NOTE_BLOCK_BASS;          // Bass
        if (instrument <= 47) return Sound.BLOCK_NOTE_BLOCK_HARP;          // Strings
        if (instrument <= 55) return Sound.BLOCK_NOTE_BLOCK_CHIME;         // Ensemble
        if (instrument <= 63) return Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;    // Brass
        if (instrument <= 71) return Sound.BLOCK_NOTE_BLOCK_FLUTE;         // Reed
        if (instrument <= 79) return Sound.BLOCK_NOTE_BLOCK_FLUTE;         // Pipe
        if (instrument <= 87) return Sound.BLOCK_NOTE_BLOCK_BIT;           // Synth lead
        if (instrument <= 95) return Sound.BLOCK_NOTE_BLOCK_CHIME;         // Synth pad
        if (instrument <= 103) return Sound.BLOCK_NOTE_BLOCK_PLING;        // Synth effects
        if (instrument <= 111) return Sound.BLOCK_NOTE_BLOCK_BANJO;        // Ethnic
        if (instrument <= 119) return Sound.BLOCK_NOTE_BLOCK_COW_BELL;     // Percussive
        return Sound.BLOCK_NOTE_BLOCK_PLING;                               // Sound effects
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
        if (!(sender instanceof Player player) && (args.length == 0 || !args[0].equalsIgnoreCase("reload"))) {
            sender.sendMessage("This subcommand requires a player.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/enb set <note 0-127> <instrument 0-128> [velocity] [sustainTicks] [delayMs] [fadeIn] [fadeOut]");
            sender.sendMessage("/enb info | /enb remove | /enb play | /enb reload");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                loadNotes();
                sender.sendMessage("ExtendedNoteBlockBridge reloaded.");
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /enb set <note> <instrument> [velocity] [sustainTicks] [delayMs] [fadeIn] [fadeOut]");
                    return true;
                }
                Block block = targetNoteBlock((Player) sender);
                if (block == null) return true;
                try {
                    int note = clamp(Integer.parseInt(args[1]), 0, 127);
                    int instrument = clamp(Integer.parseInt(args[2]), 0, 128);
                    int velocity = args.length > 3 ? clamp(Integer.parseInt(args[3]), 0, 127) : 100;
                    int sustain = args.length > 4 ? clamp(Integer.parseInt(args[4]), 1, 400) : 20;
                    int delay = args.length > 5 ? clamp(Integer.parseInt(args[5]), 0, 600000) : 0;
                    int fadeIn = args.length > 6 ? clamp(Integer.parseInt(args[6]), 0, 400) : 0;
                    int fadeOut = args.length > 7 ? clamp(Integer.parseInt(args[7]), 0, 400) : 0;
                    notes.put(key(block), new NoteConfig(note, instrument, velocity, sustain, delay, fadeIn, fadeOut));
                    saveNotes();
                    sender.sendMessage("Configured ENB note block: MIDI " + note + ", instrument " + instrument);
                } catch (NumberFormatException e) {
                    sender.sendMessage("All values must be integers.");
                }
            }
            case "info" -> {
                Block block = targetNoteBlock((Player) sender);
                if (block == null) return true;
                NoteConfig cfg = notes.get(key(block));
                sender.sendMessage(cfg == null ? "This note block is not configured." : cfg.toString());
            }
            case "remove" -> {
                Block block = targetNoteBlock((Player) sender);
                if (block == null) return true;
                notes.remove(key(block));
                stopActive(key(block));
                saveNotes();
                sender.sendMessage("Removed ENB configuration.");
            }
            case "play" -> {
                Block block = targetNoteBlock((Player) sender);
                if (block == null) return true;
                NoteConfig cfg = notes.get(key(block));
                if (cfg == null) sender.sendMessage("This note block is not configured.");
                else startConfiguredSound(block, cfg);
            }
            default -> sender.sendMessage("Unknown subcommand.");
        }
        return true;
    }

    private Block targetNoteBlock(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null || block.getType() != Material.NOTE_BLOCK) {
            player.sendMessage("Look at a vanilla note block within 6 blocks.");
            return null;
        }
        return block;
    }

    private String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
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
                    yml.getInt(key + ".fadeOut", 0)));
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
