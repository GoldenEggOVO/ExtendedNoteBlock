#!/usr/bin/env python3
"""Inject automatic listener-pack delivery and vanilla-client ENB audio.

Run after prepare_paper_render_sync.py so the join hook and the final Paper
source shape already exist.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "bridge" / "src" / "main" / "java" / "com" / "goldenegggovo" / "extendednoteblock" / "bridge" / "ExtendedNoteBlockBridge.java"

text = SOURCE.read_text(encoding="utf-8")
original = text

# ---------------------------------------------------------------------------
# Imports
# ---------------------------------------------------------------------------
if "import net.kyori.adventure.text.Component;" not in text:
    text = text.replace(
        "import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport;\n\n",
        "import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport;\n\n"
        "import net.kyori.adventure.text.Component;\n\n",
        1,
    )

event_import_anchor = "import org.bukkit.event.player.PlayerRegisterChannelEvent;\n"
if "PlayerResourcePackStatusEvent" not in text:
    if event_import_anchor not in text:
        raise SystemExit("Could not find render-sync player imports; run prepare_paper_render_sync.py first")
    text = text.replace(
        event_import_anchor,
        event_import_anchor
        + "import org.bukkit.event.player.PlayerQuitEvent;\n"
        + "import org.bukkit.event.player.PlayerResourcePackStatusEvent;\n",
        1,
    )

if "import java.net.URI;" not in text:
    text = text.replace(
        "import java.io.IOException;\n",
        "import java.io.IOException;\nimport java.net.URI;\nimport java.nio.charset.StandardCharsets;\n",
        1,
    )

# ---------------------------------------------------------------------------
# Fields and startup/shutdown
# ---------------------------------------------------------------------------
field_anchor = "    private final Map<String, RenderObjectState> lastRenderStates = new HashMap<>();\n"
field_block = field_anchor + r'''    private final Set<UUID> listenerPackReady = new HashSet<>();
    private final Map<UUID, ListenerPlayback> listenerSounds = new HashMap<>();
    private int listenerVoiceSequence;

    private boolean listenerPackEnabled;
    private boolean listenerPackRequired;
    private UUID listenerPackId;
    private String listenerPackUrl;
    private byte[] listenerPackSha1;
    private Component listenerPackPrompt;
'''
if "listenerPackReady" not in text:
    if field_anchor not in text:
        raise SystemExit("Could not find render-state field anchor")
    text = text.replace(field_anchor, field_block, 1)

enable_anchor = "        saveDefaultConfig();\n        bridgeTypeKey = new NamespacedKey(this, \"enb_type\");\n"
enable_replacement = "        saveDefaultConfig();\n        loadListenerResourcePackSettings();\n        bridgeTypeKey = new NamespacedKey(this, \"enb_type\");\n"
if "        loadListenerResourcePackSettings();" not in text:
    if enable_anchor not in text:
        raise SystemExit("Could not find onEnable configuration anchor")
    text = text.replace(enable_anchor, enable_replacement, 1)

disable_anchor = "        activeTasks.values().forEach(BukkitTask::cancel);\n        projectionStopTasks.values().forEach(BukkitTask::cancel);\n"
disable_replacement = disable_anchor + "        for (UUID soundId : List.copyOf(listenerSounds.keySet())) stopListenerSound(soundId);\n        listenerPackReady.clear();\n"
if "List.copyOf(listenerSounds.keySet())" not in text:
    if disable_anchor not in text:
        raise SystemExit("Could not find onDisable sound cleanup anchor")
    text = text.replace(disable_anchor, disable_replacement, 1)

# ---------------------------------------------------------------------------
# Join/status hooks
# ---------------------------------------------------------------------------
join_anchor = r'''    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> sendRenderSnapshot(event.getPlayer()), 20L);
    }
'''
join_replacement = r'''    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        listenerPackReady.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> sendListenerResourcePack(event.getPlayer()), 1L);
        Bukkit.getScheduler().runTaskLater(this, () -> sendRenderSnapshot(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (listenerPackId == null || !listenerPackId.equals(event.getID())) return;
        String status = event.getStatus().name();
        if ("SUCCESSFULLY_LOADED".equals(status)) {
            listenerPackReady.add(event.getPlayer().getUniqueId());
            getLogger().fine("ENB listener resource pack loaded by " + event.getPlayer().getName());
        } else if (status.equals("DECLINED") || status.startsWith("FAILED") || status.equals("DISCARDED")) {
            listenerPackReady.remove(event.getPlayer().getUniqueId());
            getLogger().warning("ENB listener resource pack " + status.toLowerCase(Locale.ROOT)
                    + " for " + event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        listenerPackReady.remove(event.getPlayer().getUniqueId());
    }
'''
if "public void onPlayerResourcePackStatus" not in text:
    if join_anchor not in text:
        raise SystemExit("Could not find render-sync join hook")
    text = text.replace(join_anchor, join_replacement, 1)

# ---------------------------------------------------------------------------
# Playback routing
# ---------------------------------------------------------------------------
text = text.replace("        playVanillaFallback(origin, cfg);\n", "        playNonModSound(origin, cfg.instrumentId, cfg.note, cfg.velocity, cfg.pitchCents, soundId);\n", 1)
text = text.replace("        playVanillaProjectionFallback(origin, note);\n", "        playNonModSound(origin, note.instrumentId(), note.midiNote(), note.velocity(), note.pitchCents(), soundId);\n", 1)

old_fallback = r'''    private void playVanillaFallback(Location origin, NoteConfig cfg) {
        Sound sound = vanillaSoundForInstrument(cfg.instrumentId);
        float volume = Math.max(0.0f, Math.min(1.0f, cfg.velocity / 127.0f));
        float pitch = Math.max(.5f, Math.min(2f, vanillaPitchForMidi(cfg.note)
                * (float) Math.pow(2, cfg.pitchCents / 1200.0)));
        for (Player player : origin.getWorld().getPlayers()) {
            if (supportsExtendedNoteBlock(player)) continue;
            player.playSound(origin, sound, SoundCategory.RECORDS, volume, pitch);
        }
    }
'''
new_fallback = r'''    private void playNonModSound(Location origin, int instrumentId, int midiNote, int velocity,
                                 int pitchCents, UUID soundId) {
        float volume = Math.max(0.0f, Math.min(1.0f, velocity / 127.0f));
        ListenerSoundResolver.Resolved listener = ListenerSoundResolver.resolve(
                instrumentId, midiNote, pitchCents, listenerVoiceSequence++);
        Set<UUID> listenerRecipients = new HashSet<>();
        double radius = Math.max(1.0, getConfig().getDouble("audible-radius", 64.0));
        double radiusSq = radius * radius;

        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) > radiusSq || supportsExtendedNoteBlock(player)) continue;
            if (listenerPackReady.contains(player.getUniqueId())) {
                player.playSound(origin, listener.event(), SoundCategory.RECORDS, volume, listener.pitch());
                listenerRecipients.add(player.getUniqueId());
            } else {
                Sound fallback = vanillaSoundForInstrument(instrumentId);
                float fallbackPitch = Math.max(.5f, Math.min(2f, vanillaPitchForMidi(midiNote)
                        * (float) Math.pow(2, pitchCents / 1200.0)));
                player.playSound(origin, fallback, SoundCategory.RECORDS, volume, fallbackPitch);
            }
        }
        if (!listenerRecipients.isEmpty()) {
            listenerSounds.put(soundId, new ListenerPlayback(listener.event(), Set.copyOf(listenerRecipients)));
        }
    }

    private void stopListenerSound(UUID soundId) {
        ListenerPlayback playback = listenerSounds.remove(soundId);
        if (playback == null) return;
        for (UUID playerId : playback.players()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.stopSound(playback.event(), SoundCategory.RECORDS);
            }
        }
    }
'''
if "private void playNonModSound" not in text:
    if old_fallback not in text:
        raise SystemExit("Could not find vanilla fallback method")
    text = text.replace(old_fallback, new_fallback, 1)

projection_fallback = r'''    private void playVanillaProjectionFallback(Location origin, ProjectionNote note) {
        Sound sound = vanillaSoundForInstrument(note.instrumentId());
        float volume = Math.max(0.0f, Math.min(1.0f, note.velocity() / 127.0f));
        float pitch = vanillaPitchForMidi(note.midiNote()) * (float) Math.pow(2.0, note.pitchCents() / 1200.0);
        pitch = Math.max(0.5f, Math.min(2.0f, pitch));
        for (Player player : origin.getWorld().getPlayers()) {
            if (supportsExtendedNoteBlock(player)) continue;
            player.playSound(origin, sound, SoundCategory.RECORDS, volume, pitch);
        }
    }

'''
text = text.replace(projection_fallback, "", 1)

stop_active_anchor = r'''        UUID id = activeSounds.remove(key);
        Block block = getLoadedBlock(key);
        if (id != null && block != null) {
'''
stop_active_replacement = r'''        UUID id = activeSounds.remove(key);
        if (id != null) stopListenerSound(id);
        Block block = getLoadedBlock(key);
        if (id != null && block != null) {
'''
if "if (id != null) stopListenerSound(id);" not in text:
    if stop_active_anchor not in text:
        raise SystemExit("Could not find regular sound stop anchor")
    text = text.replace(stop_active_anchor, stop_active_replacement, 1)

projection_stop_anchor = "            if (stopTask != null) stopTask.cancel();\n            if (origin != null) sendToListeningPlayers(origin, STOP_SOUND, PayloadCodec.stopSound(id));\n"
projection_stop_replacement = "            if (stopTask != null) stopTask.cancel();\n            stopListenerSound(id);\n            if (origin != null) sendToListeningPlayers(origin, STOP_SOUND, PayloadCodec.stopSound(id));\n"
if "            stopListenerSound(id);" not in text:
    if projection_stop_anchor not in text:
        raise SystemExit("Could not find projection stop anchor")
    text = text.replace(projection_stop_anchor, projection_stop_replacement, 1)

scheduled_stop_anchor = "        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {\n            sendToListeningPlayers(origin, STOP_SOUND, PayloadCodec.stopSound(soundId));\n"
scheduled_stop_replacement = "        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {\n            stopListenerSound(soundId);\n            sendToListeningPlayers(origin, STOP_SOUND, PayloadCodec.stopSound(soundId));\n"
if "runTaskLater(this, () -> {\n            stopListenerSound(soundId);" not in text:
    if scheduled_stop_anchor not in text:
        raise SystemExit("Could not find scheduled projection stop anchor")
    text = text.replace(scheduled_stop_anchor, scheduled_stop_replacement, 1)

# ---------------------------------------------------------------------------
# Configuration and download request helpers
# ---------------------------------------------------------------------------
helper_anchor = r'''    // -------------------------------------------------------------------------
    // Paper Client placed-object render synchronization
    // -------------------------------------------------------------------------
'''
helper_block = r'''    // -------------------------------------------------------------------------
    // Automatic server resource pack + unmodified-client listener mode
    // -------------------------------------------------------------------------

    private void loadListenerResourcePackSettings() {
        listenerPackReady.clear();
        listenerPackEnabled = getConfig().getBoolean("resource-pack.enabled", true);
        listenerPackRequired = getConfig().getBoolean("resource-pack.required", true);
        listenerPackUrl = getConfig().getString("resource-pack.url", "").trim();
        String id = getConfig().getString("resource-pack.id", "").trim();
        String sha1 = getConfig().getString("resource-pack.sha1", "").trim();
        String prompt = getConfig().getString("resource-pack.prompt",
                "ExtendedNoteBlock requires its visual and listener sound pack.");
        listenerPackPrompt = Component.text(prompt);
        listenerPackId = null;
        listenerPackSha1 = null;

        if (!listenerPackEnabled) return;
        if (listenerPackUrl.isBlank() || listenerPackUrl.startsWith("__ENB_")
                || sha1.isBlank() || sha1.startsWith("__ENB_")) {
            listenerPackEnabled = false;
            getLogger().warning("Automatic ENB resource pack is not configured in this development build.");
            return;
        }
        try {
            listenerPackId = UUID.fromString(id);
            URI parsed = URI.create(listenerPackUrl);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null
                    || !StandardCharsets.US_ASCII.newEncoder().canEncode(listenerPackUrl)) {
                throw new IllegalArgumentException("resource-pack.url must be an ASCII HTTPS URL");
            }
            listenerPackSha1 = decodeSha1(sha1);
        } catch (IllegalArgumentException invalid) {
            listenerPackEnabled = false;
            getLogger().severe("Automatic ENB resource pack disabled: " + invalid.getMessage());
        }
    }

    private void sendListenerResourcePack(Player player) {
        if (!listenerPackEnabled || !player.isOnline()) return;
        player.setResourcePack(listenerPackId, listenerPackUrl, listenerPackSha1,
                listenerPackPrompt, listenerPackRequired);
    }

    private static byte[] decodeSha1(String hex) {
        if (!hex.matches("(?i)[0-9a-f]{40}")) {
            throw new IllegalArgumentException("resource-pack.sha1 must contain exactly 40 hexadecimal characters");
        }
        byte[] decoded = new byte[20];
        for (int i = 0; i < decoded.length; i++) {
            decoded[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return decoded;
    }

'''
if "private void loadListenerResourcePackSettings" not in text:
    if helper_anchor not in text:
        raise SystemExit("Could not find render-sync helper insertion anchor")
    text = text.replace(helper_anchor, helper_block + helper_anchor, 1)

reload_anchor = r'''            case "reload" -> {
                loadNotes();
'''
reload_replacement = r'''            case "reload" -> {
                reloadConfig();
                loadListenerResourcePackSettings();
                loadNotes();
'''
if "                reloadConfig();\n                loadListenerResourcePackSettings();" not in text:
    if reload_anchor not in text:
        raise SystemExit("Could not find /enb reload anchor")
    text = text.replace(reload_anchor, reload_replacement, 1)

reload_tail_anchor = "                rebuildObjectIndexes();\n                sender.sendMessage(\"ExtendedNoteBlockBridge reloaded.\");\n"
reload_tail_replacement = "                rebuildObjectIndexes();\n                for (Player player : Bukkit.getOnlinePlayers()) sendListenerResourcePack(player);\n                sender.sendMessage(\"ExtendedNoteBlockBridge reloaded.\");\n"
if "Bukkit.getOnlinePlayers()) sendListenerResourcePack" not in text:
    if reload_tail_anchor not in text:
        raise SystemExit("Could not find /enb reload completion anchor")
    text = text.replace(reload_tail_anchor, reload_tail_replacement, 1)

# ---------------------------------------------------------------------------
# Playback record
# ---------------------------------------------------------------------------
record_anchor = r'''    private record RenderObjectState(int typeId, boolean powered, int variant) {
    }

'''
record_replacement = record_anchor + r'''    private record ListenerPlayback(String event, Set<UUID> players) {
    }

'''
if "private record ListenerPlayback" not in text:
    if record_anchor not in text:
        raise SystemExit("Could not find render-state record anchor")
    text = text.replace(record_anchor, record_replacement, 1)

if text != original:
    SOURCE.write_text(text, encoding="utf-8")
    print(f"patched {SOURCE.relative_to(ROOT)}")
else:
    print("Paper listener resource pack source already prepared")
