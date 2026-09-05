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

# ---------------------------------------------------------------------------
# Fields and startup/shutdown
# ---------------------------------------------------------------------------
field_anchor = "    private final Map<String, RenderObjectState> lastRenderStates = new HashMap<>();\n"
field_block = field_anchor + r'''    private final Set<UUID> listenerPackReady = new HashSet<>();
    private final Map<UUID, String> listenerPackStates = new HashMap<>();
    private final Map<UUID, ListenerPlayback> listenerSounds = new HashMap<>();
    private int listenerVoiceSequence;

    private boolean listenerPackEnabled;
    private boolean listenerPackRequired;
    private UUID listenerPackId;
    private String listenerPackUrl;
    private byte[] listenerPackSha1;
    private String listenerPackSha1Hex;
    private String listenerPackSource;
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
disable_replacement = disable_anchor + "        for (UUID soundId : List.copyOf(listenerSounds.keySet())) stopListenerSound(soundId);\n        listenerPackReady.clear();\n        listenerPackStates.clear();\n"
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
        listenerPackStates.remove(event.getPlayer().getUniqueId());
        // Give login, configuration and other server-provided packs time to settle.
        Bukkit.getScheduler().runTaskLater(this, () -> sendListenerResourcePack(event.getPlayer(), true), 40L);
        Bukkit.getScheduler().runTaskLater(this, () -> sendRenderSnapshot(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (listenerPackId == null || !listenerPackId.equals(event.getID())) return;
        String status = event.getStatus().name();
        listenerPackStates.put(event.getPlayer().getUniqueId(), status);
        if ("SUCCESSFULLY_LOADED".equals(status)) {
            listenerPackReady.add(event.getPlayer().getUniqueId());
            getLogger().info("ENB listener resource pack loaded by " + event.getPlayer().getName());
            event.getPlayer().sendMessage("ENB 资源包已加载：原版客户端 MIDI 0-127 聆听模式已启用。");
        } else if (status.equals("DECLINED") || status.startsWith("FAILED")
                || status.equals("INVALID_URL") || status.equals("DISCARDED")) {
            listenerPackReady.remove(event.getPlayer().getUniqueId());
            getLogger().warning("ENB listener resource pack " + status.toLowerCase(Locale.ROOT)
                    + " for " + event.getPlayer().getName());
            event.getPlayer().sendMessage("ENB 资源包未加载（" + status + "），当前只能使用受限的原版音符盒回退。");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        listenerPackReady.remove(event.getPlayer().getUniqueId());
        listenerPackStates.remove(event.getPlayer().getUniqueId());
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
        listenerPackStates.clear();
        listenerPackEnabled = getConfig().getBoolean("resource-pack.enabled", true);
        listenerPackRequired = getConfig().getBoolean("resource-pack.required", true);
        boolean useOfficialRelease = getConfig().getBoolean("resource-pack.use-official-release", true);
        String configuredUrl = getConfig().getString("resource-pack.url", "");
        String configuredId = getConfig().getString("resource-pack.id", "");
        String configuredSha1 = getConfig().getString("resource-pack.sha1", "");
        String prompt = getConfig().getString("resource-pack.prompt",
                "ExtendedNoteBlock requires its visual and listener sound pack.");
        listenerPackPrompt = Component.text(prompt);
        listenerPackId = null;
        listenerPackUrl = "";
        listenerPackSha1 = null;
        listenerPackSha1Hex = "";
        listenerPackSource = useOfficialRelease ? "official release" : "custom config";

        if (!listenerPackEnabled) return;
        try (var officialMetadata = getResource("enb-release-pack.properties")) {
            ListenerResourcePackConfig.Resolved resolved = ListenerResourcePackConfig.resolve(
                    useOfficialRelease, officialMetadata, configuredId, configuredUrl, configuredSha1);
            listenerPackId = resolved.id();
            listenerPackUrl = resolved.url();
            listenerPackSha1 = resolved.sha1Bytes();
            listenerPackSha1Hex = resolved.sha1();
            listenerPackSource = resolved.source();
            getLogger().info("Automatic ENB resource pack enabled from " + listenerPackSource
                    + " (required=" + listenerPackRequired + ", sha1=" + listenerPackSha1Hex + ").");
        } catch (IOException | IllegalArgumentException invalid) {
            listenerPackEnabled = false;
            getLogger().severe("Automatic ENB resource pack disabled: " + invalid.getMessage());
        }
    }

    private void sendListenerResourcePack(Player player) {
        sendListenerResourcePack(player, true);
    }

    private void sendListenerResourcePack(Player player, boolean announce) {
        if (!listenerPackEnabled || !player.isOnline()) return;
        listenerPackReady.remove(player.getUniqueId());
        listenerPackStates.put(player.getUniqueId(), "REQUESTED");
        if (announce) {
            player.sendMessage("ENB 正在下发材质与音乐资源包；若服务器资源包设为‘启用’，客户端会静默下载。");
        }
        player.setResourcePack(listenerPackId, listenerPackUrl, listenerPackSha1,
                listenerPackPrompt, listenerPackRequired);
        getLogger().info("Sent ENB listener resource-pack request to " + player.getName());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || listenerPackReady.contains(player.getUniqueId())) return;
            String state = listenerPackStates.getOrDefault(player.getUniqueId(), "NO_RESPONSE");
            if (!state.equals("DECLINED") && !state.startsWith("FAILED")
                    && !state.equals("INVALID_URL") && !state.equals("DISCARDED")) {
                listenerPackStates.put(player.getUniqueId(), "NO_SUCCESS_AFTER_15S");
                player.sendMessage("ENB 尚未收到资源包加载成功状态。请检查：多人游戏 → 编辑服务器 → 服务器资源包设为‘提示’或‘启用’，然后执行 /enb pack resend。");
                getLogger().warning("No successful ENB resource-pack status from " + player.getName()
                        + " after 15 seconds (last=" + state + ").");
            }
        }, 300L);
    }

    private void handlePackCommand(CommandSender sender, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        if (action.equals("resend")) {
            if (sender instanceof Player player) {
                sendListenerResourcePack(player, true);
                sender.sendMessage("ENB 资源包请求已重新发送。");
            } else {
                for (Player player : Bukkit.getOnlinePlayers()) sendListenerResourcePack(player, true);
                sender.sendMessage("ENB resource-pack request resent to all online players.");
            }
            return;
        }
        if (action.equals("test")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This resource-pack sound test requires a player.");
                return;
            }
            if (!listenerPackReady.contains(player.getUniqueId())) {
                sender.sendMessage("ENB 资源包尚未成功加载；先执行 /enb pack status 或 /enb pack resend。");
                return;
            }
            try {
                int midiNote = args.length >= 3 ? clamp(Integer.parseInt(args[2]), 0, 127) : 60;
                int instrument = args.length >= 4 ? clamp(Integer.parseInt(args[3]), 0, 127) : 0;
                ListenerSoundResolver.Resolved listener = ListenerSoundResolver.resolve(
                        instrument, midiNote, 0, listenerVoiceSequence++);
                player.playSound(player.getLocation(), listener.event(), SoundCategory.RECORDS, 1.0f, listener.pitch());
                sender.sendMessage("ENB pack test: MIDI " + midiNote + ", instrument " + instrument
                        + ", anchor " + listener.anchor() + ", pitch " + listener.pitch());
            } catch (NumberFormatException invalid) {
                sender.sendMessage("Usage: /enb pack test <MIDI 0-127> [instrument 0-127]");
            }
            return;
        }
        if (!action.equals("status")) {
            sender.sendMessage("Usage: /enb pack <status|resend|test> [MIDI] [instrument]");
            return;
        }
        sender.sendMessage("ENB resource pack: enabled=" + listenerPackEnabled
                + ", required=" + listenerPackRequired + ", source=" + listenerPackSource);
        if (listenerPackEnabled) {
            sender.sendMessage("URL: " + listenerPackUrl);
            sender.sendMessage("SHA-1: " + listenerPackSha1Hex);
        }
        if (sender instanceof Player player) {
            String state = listenerPackStates.getOrDefault(player.getUniqueId(), "NOT_REQUESTED");
            sender.sendMessage("Your pack state: " + state
                    + (listenerPackReady.contains(player.getUniqueId()) ? " (MIDI 0-127 listener enabled)" : ""));
        }
    }

'''
if "private void loadListenerResourcePackSettings" not in text:
    if helper_anchor not in text:
        raise SystemExit("Could not find render-sync helper insertion anchor")
    text = text.replace(helper_anchor, helper_block + helper_anchor, 1)

# ---------------------------------------------------------------------------
# Resource-pack diagnostics command
# ---------------------------------------------------------------------------
console_guard = '''        if (!(sender instanceof Player) && (args.length == 0 || !args[0].equalsIgnoreCase("reload"))) {
'''
console_guard_replacement = '''        if (!(sender instanceof Player) && (args.length == 0
                || (!args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("pack")))) {
'''
if "!args[0].equalsIgnoreCase(\"pack\")" not in text:
    if console_guard not in text:
        raise SystemExit("Could not find console command guard")
    text = text.replace(console_guard, console_guard_replacement, 1)

pack_case_anchor = '''            case "give" -> handleGive((Player) sender, args);
'''
if 'case "pack" -> handlePackCommand(sender, args);' not in text:
    if pack_case_anchor not in text:
        raise SystemExit("Could not find command switch insertion point")
    text = text.replace(
        pack_case_anchor,
        '            case "pack" -> handlePackCommand(sender, args);\n' + pack_case_anchor,
        1,
    )

text = text.replace(
    '"help", "give", "set", "info", "remove", "play", "wand", "projection", "list", "reload"',
    '"help", "give", "set", "info", "remove", "play", "wand", "projection", "pack", "list", "reload"',
)
text = text.replace(
    '"give", "set", "info", "remove", "play", "wand", "projection", "list", "reload"',
    '"give", "set", "info", "remove", "play", "wand", "projection", "pack", "list", "reload"',
)
pack_completion_anchor = '''            case "wand" -> completeWand(args);
'''
if 'case "pack" -> switch (args.length)' not in text:
    if pack_completion_anchor not in text:
        raise SystemExit("Could not find tab-completion insertion point")
    text = text.replace(
        pack_completion_anchor,
        '''            case "pack" -> switch (args.length) {
                case 2 -> complete(args[1], "status", "resend", "test");
                case 3 -> args[1].equalsIgnoreCase("test")
                        ? complete(args[2], "0", "12", "24", "60", "96", "120", "127") : List.of();
                case 4 -> args[1].equalsIgnoreCase("test")
                        ? complete(args[3], "0", "4", "24", "40", "80", "124") : List.of();
                default -> List.of();
            };
'''
        + pack_completion_anchor,
        1,
    )

help_pack_anchor = '''                case "reload" -> {
                    sender.sendMessage("/enb reload");
'''
if 'sender.sendMessage("/enb pack status | resend | test <MIDI 0-127> [instrument 0-127]");' not in text:
    if help_pack_anchor not in text:
        raise SystemExit("Could not find command help insertion point")
    text = text.replace(
        help_pack_anchor,
        '''                case "pack" -> {
                    sender.sendMessage("/enb pack status | resend | test <MIDI 0-127> [instrument 0-127]");
                    sender.sendMessage("Shows/resends the current pack or directly test-plays one listener-pack note.");
                    return;
                }
''' + help_pack_anchor,
        1,
    )

help_list_anchor = '''        sender.sendMessage("/enb list - List logical objects and vanilla carriers");
'''
if 'sender.sendMessage("/enb pack <status|resend|test> - Diagnose or test the listener pack");' not in text:
    if help_list_anchor not in text:
        raise SystemExit("Could not find command-list help insertion point")
    text = text.replace(
        help_list_anchor,
        '        sender.sendMessage("/enb pack <status|resend|test> - Diagnose or test the listener pack");\n'
        + help_list_anchor,
        1,
    )

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
reload_tail_replacement = "                rebuildObjectIndexes();\n                for (Player player : Bukkit.getOnlinePlayers()) sendListenerResourcePack(player, true);\n                sender.sendMessage(\"ExtendedNoteBlockBridge reloaded.\");\n"
if "rebuildObjectIndexes();\n                for (Player player : Bukkit.getOnlinePlayers()) sendListenerResourcePack" not in text:
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
