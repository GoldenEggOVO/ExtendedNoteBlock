#!/usr/bin/env python3
"""Inject Paper/Purpur placed-block interactions for the registry-safe bridge.

The Paper Client already contains a standalone Extended Note Block editor screen
and bridge_note_edit / bridge_note_save payload definitions. This patch wires the
Paper plugin to those channels and gives every placed bridge object a useful
right-click interaction without introducing custom registries.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "bridge" / "src" / "main" / "java" / "com" / "goldenegggovo" / "extendednoteblock" / "bridge" / "ExtendedNoteBlockBridge.java"

text = SOURCE.read_text(encoding="utf-8")
original = text

# ---------------------------------------------------------------------------
# Imports
# ---------------------------------------------------------------------------
if "import java.io.ByteArrayInputStream;" not in text:
    text = text.replace(
        "import java.io.ByteArrayOutputStream;\n",
        "import java.io.ByteArrayInputStream;\nimport java.io.ByteArrayOutputStream;\nimport java.io.DataInputStream;\n",
        1,
    )

# ---------------------------------------------------------------------------
# Bridge-only editor channels
# ---------------------------------------------------------------------------
constant_anchor = '    private static final String ADV_UPDATE = "extendednoteblock:adv_update";\n'
constant_replacement = (
    constant_anchor
    + '    private static final String NOTE_EDIT = "extendednoteblock:bridge_note_edit";\n'
    + '    private static final String NOTE_SAVE = "extendednoteblock:bridge_note_save";\n'
)
if "bridge_note_edit" not in text:
    if constant_anchor not in text:
        raise SystemExit("Could not find bridge channel constant anchor")
    text = text.replace(constant_anchor, constant_replacement, 1)

# ---------------------------------------------------------------------------
# Register outgoing editor channel and incoming save channel.
# ---------------------------------------------------------------------------
enable_anchor = """        getServer().getPluginManager().registerEvents(this, this);\n        for (String channel : List.of(START_SOUND, UPDATE_VOLUME, STOP_SOUND, START_ADV_SOUND, ADV_UPDATE)) {\n            getServer().getMessenger().registerOutgoingPluginChannel(this, channel);\n        }\n\n        long pollPeriod = Math.max(1L, getConfig().getLong(\"wireless-redstone.poll-period-ticks\", 1L));\n"""
enable_replacement = """        getServer().getPluginManager().registerEvents(this, this);\n        for (String channel : List.of(START_SOUND, UPDATE_VOLUME, STOP_SOUND, START_ADV_SOUND, ADV_UPDATE, NOTE_EDIT)) {\n            getServer().getMessenger().registerOutgoingPluginChannel(this, channel);\n        }\n        getServer().getMessenger().registerIncomingPluginChannel(this, NOTE_SAVE,\n                (channel, player, message) -> handleNoteSave(player, message));\n\n        long pollPeriod = Math.max(1L, getConfig().getLong(\"wireless-redstone.poll-period-ticks\", 1L));\n"""
if "registerIncomingPluginChannel(this, NOTE_SAVE" not in text:
    if enable_anchor not in text:
        raise SystemExit("Could not find onEnable plugin-messaging anchor")
    text = text.replace(enable_anchor, enable_replacement, 1)

# ---------------------------------------------------------------------------
# Replace the wand-only PlayerInteractEvent handler with wand + placed objects.
# ---------------------------------------------------------------------------
interact_anchor = """    @EventHandler(ignoreCancelled = true)\n    public void onPlayerInteract(PlayerInteractEvent event) {\n        if (event.getHand() != EquipmentSlot.HAND) return;\n        ItemStack held = event.getItem();\n        if (getBridgeItemType(held) != BridgeItemType.CONDUCTOR_WAND) return;\n        if (event.getClickedBlock() == null) return;\n\n        Action action = event.getAction();\n        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;\n\n        event.setCancelled(true);\n        Player player = event.getPlayer();\n        BlockRef pos = BlockRef.of(event.getClickedBlock());\n        WandSelection old = wandSelections.getOrDefault(player.getUniqueId(), new WandSelection(null, null));\n        WandSelection updated;\n        if (action == Action.LEFT_CLICK_BLOCK) {\n            updated = new WandSelection(pos, old.pos2());\n            player.sendMessage(\"ENB Conductor: Pos1 = \" + pos.shortText());\n        } else {\n            updated = new WandSelection(old.pos1(), pos);\n            player.sendMessage(\"ENB Conductor: Pos2 = \" + pos.shortText());\n        }\n        wandSelections.put(player.getUniqueId(), updated);\n    }\n"""
interact_replacement = """    @EventHandler(ignoreCancelled = true)\n    public void onPlayerInteract(PlayerInteractEvent event) {\n        if (event.getHand() != EquipmentSlot.HAND) return;\n        if (event.getClickedBlock() == null) return;\n\n        Player player = event.getPlayer();\n        Action action = event.getAction();\n        ItemStack held = event.getItem();\n\n        // Conductor Wand keeps its original two-point selection behavior and\n        // takes priority over normal right-click interaction with bridge blocks.\n        if (getBridgeItemType(held) == BridgeItemType.CONDUCTOR_WAND\n                && (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)) {\n            event.setCancelled(true);\n            BlockRef pos = BlockRef.of(event.getClickedBlock());\n            WandSelection old = wandSelections.getOrDefault(player.getUniqueId(), new WandSelection(null, null));\n            WandSelection updated;\n            if (action == Action.LEFT_CLICK_BLOCK) {\n                updated = new WandSelection(pos, old.pos2());\n                player.sendMessage(\"ENB Conductor: Pos1 = \" + pos.shortText());\n            } else {\n                updated = new WandSelection(old.pos1(), pos);\n                player.sendMessage(\"ENB Conductor: Pos2 = \" + pos.shortText());\n            }\n            wandSelections.put(player.getUniqueId(), updated);\n            return;\n        }\n\n        if (action != Action.RIGHT_CLICK_BLOCK) return;\n        Block block = event.getClickedBlock();\n        BridgeItemType type = objects.get(key(block));\n        if (type == null) return;\n\n        // Prevent the vanilla Note Block right-click pitch change and make all\n        // managed bridge objects behave consistently as ENB interactables.\n        event.setCancelled(true);\n        handlePlacedBridgeInteraction(player, block, type);\n    }\n"""
if "handlePlacedBridgeInteraction(player, block, type);" not in text:
    if interact_anchor not in text:
        raise SystemExit("Could not find PlayerInteractEvent anchor")
    text = text.replace(interact_anchor, interact_replacement, 1)

# ---------------------------------------------------------------------------
# Insert interaction helpers before normal note playback.
# ---------------------------------------------------------------------------
helper_anchor = """    @EventHandler(ignoreCancelled = true)\n    public void onNotePlay(NotePlayEvent event) {\n"""
helper_block = r'''    private void handlePlacedBridgeInteraction(Player player, Block block, BridgeItemType type) {
        String blockKey = key(block);
        switch (type) {
            case EXTENDED_NOTE_BLOCK -> {
                NoteConfig cfg = notes.computeIfAbsent(blockKey, ignored -> defaultNoteConfig());
                if (player.getListeningPluginChannels().contains(NOTE_EDIT)) {
                    player.sendPluginMessage(this, NOTE_EDIT, PayloadCodec.noteEdit(
                            block.getX(), block.getY(), block.getZ(), cfg));
                } else {
                    player.sendMessage("Extended Note Block: " + cfg);
                    player.sendMessage("Install ExtendedNoteBlock Paper Client to edit this block by right-click GUI.");
                }
            }
            case GLOBAL_REDSTONE_TRANSMITTER -> {
                boolean powered = transmitterPower.getOrDefault(blockKey, block.getBlockPower() > 0);
                String projectionTarget = findAdjacentProjectionKey(block);
                if (projectionTarget != null) {
                    player.sendMessage("Global Redstone Transmitter: dedicated Projection trigger / "
                            + (powered ? "ON" : "OFF"));
                    BlockRef target = parseKey(projectionTarget);
                    if (target != null) player.sendMessage("Projection Receiver: " + target.shortText());
                } else {
                    player.sendMessage("Global Redstone Transmitter: global wireless mode / "
                            + (powered ? "ON" : "OFF"));
                    player.sendMessage("Power this block with vanilla redstone to drive every global receiver in this world.");
                }
            }
            case GLOBAL_REDSTONE_RECEIVER -> {
                boolean powered = globalPower.getOrDefault(block.getWorld().getUID(),
                        block.getType() == Material.REDSTONE_BLOCK);
                player.sendMessage("Global Redstone Receiver: output " + (powered ? "15 (ON)" : "0 (OFF)"));
                player.sendMessage("It follows any non-projection Global Redstone Transmitter in this world.");
            }
            case NBS_PROJECTION_RECEIVER -> {
                int noteCount = projectionNotes.getOrDefault(blockKey, List.of()).size();
                boolean playing = projectionSessions.containsKey(blockKey);
                if (player.isSneaking()) {
                    if (noteCount == 0) {
                        player.sendMessage("NBS Projection Receiver: no projection timeline is loaded yet.");
                    } else {
                        startProjection(blockKey, block);
                        player.sendMessage("NBS Projection Receiver: test playback started (" + noteCount + " notes).");
                    }
                } else {
                    player.sendMessage("NBS Projection Receiver: " + noteCount + " notes / "
                            + (playing ? "PLAYING" : "IDLE"));
                    player.sendMessage("Place a Global Redstone Transmitter directly beside it, then power that transmitter to play.");
                    player.sendMessage("Shift + right-click this receiver to test the currently loaded projection.");
                }
            }
            case CONDUCTOR_WAND -> {
                // Not placeable; kept for enum completeness.
            }
        }
    }

    private void handleNoteSave(Player player, byte[] message) {
        if (!player.hasPermission("extendednoteblockbridge.use")) {
            player.sendMessage("You do not have permission to edit Extended Note Blocks.");
            return;
        }
        try {
            NoteSaveRequest request = NoteSaveRequest.decode(message);
            World world = player.getWorld();
            Location location = player.getLocation();
            // Check reach, world height and loaded chunks BEFORE getBlockAt:
            // client-supplied coordinates must never cause a distant chunk load.
            if (!request.isWithinReach(location.getX(), location.getY(), location.getZ(),
                    world.getMinHeight(), world.getMaxHeight(), interactionRange() + 2.0)
                    || !world.isChunkLoaded(request.x() >> 4, request.z() >> 4)) {
                player.sendMessage("Could not save ENB settings: that block is out of reach or not loaded.");
                return;
            }

            Block block = world.getBlockAt(request.x(), request.y(), request.z());
            String blockKey = key(block);
            if (objects.get(blockKey) != BridgeItemType.EXTENDED_NOTE_BLOCK || block.getType() != Material.NOTE_BLOCK) {
                player.sendMessage("Could not save ENB settings: that block is no longer an Extended Note Block.");
                return;
            }

            NoteConfig cfg = new NoteConfig(
                    request.note(), request.instrument(), request.velocity(), request.sustain(),
                    request.delay(), request.fadeIn(), request.fadeOut(),
                    notes.getOrDefault(blockKey, defaultNoteConfig()).pitchCents());
            notes.put(blockKey, cfg);
            saveNotes();
            player.sendMessage("Extended Note Block settings saved: " + cfg);
        } catch (IOException | RuntimeException e) {
            getLogger().warning("Invalid bridge_note_save payload from " + player.getName() + ": " + e.getMessage());
        }
    }

'''
if "private void handlePlacedBridgeInteraction" not in text:
    if helper_anchor not in text:
        raise SystemExit("Could not find note-play helper insertion anchor")
    text = text.replace(helper_anchor, helper_block + helper_anchor, 1)

# ---------------------------------------------------------------------------
# Add server -> client NoteEdit encoder to the existing raw payload codec.
# ---------------------------------------------------------------------------
payload_anchor = """    private static final class PayloadCodec {\n        private static byte[] startSound(int x, int y, int z, UUID id, int instrument, int note, int velocity, float volume) {\n"""
payload_replacement = """    private static final class PayloadCodec {\n        private static byte[] noteEdit(int x, int y, int z, NoteConfig cfg) {\n            return write(out -> {\n                out.writeLong(packBlockPos(x, y, z));\n                out.writeInt(cfg.note);\n                out.writeInt(cfg.instrumentId);\n                out.writeInt(cfg.velocity);\n                out.writeInt(cfg.sustainTicks);\n                out.writeInt(cfg.delayMs);\n                out.writeInt(cfg.fadeInTicks);\n                out.writeInt(cfg.fadeOutTicks);\n            });\n        }\n\n        private static byte[] startSound(int x, int y, int z, UUID id, int instrument, int note, int velocity, float volume) {\n"""
if "private static byte[] noteEdit" not in text:
    if payload_anchor not in text:
        raise SystemExit("Could not find PayloadCodec insertion anchor")
    text = text.replace(payload_anchor, payload_replacement, 1)

if text != original:
    SOURCE.write_text(text, encoding="utf-8")
    print(f"patched {SOURCE.relative_to(ROOT)}")
else:
    print("Paper placed interactions already prepared")
