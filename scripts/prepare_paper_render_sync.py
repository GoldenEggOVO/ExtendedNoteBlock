#!/usr/bin/env python3
"""Inject Paper -> Fabric placed bridge object render synchronization.

The Paper world remains 100% vanilla-registry. The plugin only tells Paper Client
which vanilla carrier coordinates represent ENB objects and their small visual
state (type, powered, note pitch class). Paper Client then swaps the baked model
only at those coordinates.

Run after prepare_paper_interactions.py.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "bridge" / "src" / "main" / "java" / "com" / "goldenegggovo" / "extendednoteblock" / "bridge" / "ExtendedNoteBlockBridge.java"

text = SOURCE.read_text(encoding="utf-8")
original = text

# ---------------------------------------------------------------------------
# Imports
# ---------------------------------------------------------------------------
import_anchor = "import org.bukkit.event.player.PlayerInteractEvent;\n"
import_replacement = (
    import_anchor
    + "import org.bukkit.event.player.PlayerChangedWorldEvent;\n"
    + "import org.bukkit.event.player.PlayerJoinEvent;\n"
    + "import org.bukkit.event.player.PlayerRegisterChannelEvent;\n"
)
if "PlayerRegisterChannelEvent" not in text:
    if import_anchor not in text:
        raise SystemExit("Could not find player-event import anchor")
    text = text.replace(import_anchor, import_replacement, 1)

# ---------------------------------------------------------------------------
# Channel + previous-state cache
# ---------------------------------------------------------------------------
constant_anchor = '    private static final String NOTE_SAVE = "extendednoteblock:bridge_note_save";\n'
constant_replacement = constant_anchor + '    private static final String OBJECT_SYNC = "extendednoteblock:bridge_object_sync";\n'
if "bridge_object_sync" not in text:
    if constant_anchor not in text:
        raise SystemExit("Could not find NOTE_SAVE constant anchor; run interaction patch first")
    text = text.replace(constant_anchor, constant_replacement, 1)

field_anchor = "    private final Map<UUID, BukkitTask> projectionStopTasks = new HashMap<>();\n"
field_replacement = field_anchor + "    private final Map<String, RenderObjectState> lastRenderStates = new HashMap<>();\n"
if "lastRenderStates" not in text:
    if field_anchor not in text:
        raise SystemExit("Could not find render-state field anchor")
    text = text.replace(field_anchor, field_replacement, 1)

# ---------------------------------------------------------------------------
# Register outgoing render channel.
# ---------------------------------------------------------------------------
outgoing_old = "List.of(START_SOUND, UPDATE_VOLUME, STOP_SOUND, START_ADV_SOUND, ADV_UPDATE, NOTE_EDIT)"
outgoing_new = "List.of(START_SOUND, UPDATE_VOLUME, STOP_SOUND, START_ADV_SOUND, ADV_UPDATE, NOTE_EDIT, OBJECT_SYNC)"
if outgoing_new not in text:
    if outgoing_old not in text:
        raise SystemExit("Could not find outgoing channel list after interaction patch")
    text = text.replace(outgoing_old, outgoing_new, 1)

# ---------------------------------------------------------------------------
# Join/world/channel events. Channel registration is the most reliable first
# snapshot because Fabric advertises custom payload channels after login.
# ---------------------------------------------------------------------------
event_anchor = """    @EventHandler(ignoreCancelled = true)\n    public void onPlayerInteract(PlayerInteractEvent event) {\n"""
event_block = r'''    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> sendRenderSnapshot(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> sendRenderSnapshot(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onPlayerRegisterChannel(PlayerRegisterChannelEvent event) {
        if (OBJECT_SYNC.equals(event.getChannel())) {
            Bukkit.getScheduler().runTask(this, () -> sendRenderSnapshot(event.getPlayer()));
        }
    }

'''
if "public void onPlayerRegisterChannel" not in text:
    if event_anchor not in text:
        raise SystemExit("Could not find PlayerInteractEvent insertion anchor")
    text = text.replace(event_anchor, event_block + event_anchor, 1)

# ---------------------------------------------------------------------------
# State helpers before wireless-redstone section.
# ---------------------------------------------------------------------------
helper_anchor = """    // -------------------------------------------------------------------------\n    // Wireless redstone + dedicated projection routing\n    // -------------------------------------------------------------------------\n"""
helper_block = r'''    // -------------------------------------------------------------------------
    // Paper Client placed-object render synchronization
    // -------------------------------------------------------------------------

    private void sendRenderSnapshot(Player player) {
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(OBJECT_SYNC)) return;

        // A snapshot is dimension-local. Clear first so old-dimension coordinates
        // can never leak into the new world's render cache.
        player.sendPluginMessage(this, OBJECT_SYNC,
                PayloadCodec.objectSync(0, 0, 0, 0, 0, false, 0));

        UUID worldId = player.getWorld().getUID();
        for (Map.Entry<String, BridgeItemType> entry : objects.entrySet()) {
            if (entry.getValue() == BridgeItemType.CONDUCTOR_WAND) continue;
            BlockRef ref = parseKey(entry.getKey());
            if (ref == null || !ref.worldId().equals(worldId)) continue;
            RenderObjectState state = currentRenderState(entry.getKey(), entry.getValue());
            sendRenderUpsert(player, ref, state);
        }
    }

    private void syncChangedRenderStates() {
        // Anything that disappeared from objects.yml/runtime must disappear from
        // client meshes as well, including /enb remove and block breaks.
        for (String staleKey : new HashSet<>(lastRenderStates.keySet())) {
            if (objects.containsKey(staleKey)) continue;
            BlockRef ref = parseKey(staleKey);
            lastRenderStates.remove(staleKey);
            if (ref != null) broadcastRenderRemove(ref);
        }

        for (Map.Entry<String, BridgeItemType> entry : objects.entrySet()) {
            BridgeItemType type = entry.getValue();
            if (type == BridgeItemType.CONDUCTOR_WAND) continue;

            RenderObjectState next = currentRenderState(entry.getKey(), type);
            RenderObjectState previous = lastRenderStates.put(entry.getKey(), next);
            if (!next.equals(previous)) {
                BlockRef ref = parseKey(entry.getKey());
                if (ref != null) broadcastRenderUpsert(ref, next);
            }
        }
    }

    private RenderObjectState currentRenderState(String objectKey, BridgeItemType type) {
        BlockRef ref = parseKey(objectKey);
        Block block = getLoadedBlock(objectKey);
        int typeId = switch (type) {
            case EXTENDED_NOTE_BLOCK -> 0;
            case GLOBAL_REDSTONE_TRANSMITTER -> 1;
            case GLOBAL_REDSTONE_RECEIVER -> 2;
            case NBS_PROJECTION_RECEIVER -> 3;
            case CONDUCTOR_WAND -> -1;
        };

        boolean powered = switch (type) {
            case EXTENDED_NOTE_BLOCK -> block != null && block.getBlockPower() > 0;
            case GLOBAL_REDSTONE_TRANSMITTER -> transmitterPower.getOrDefault(
                    objectKey, block != null && block.getBlockPower() > 0);
            case GLOBAL_REDSTONE_RECEIVER -> ref != null && globalPower.getOrDefault(
                    ref.worldId(), block != null && block.getType() == Material.REDSTONE_BLOCK);
            case NBS_PROJECTION_RECEIVER -> isProjectionPowered(objectKey);
            case CONDUCTOR_WAND -> false;
        };

        int variant = 0;
        if (type == BridgeItemType.EXTENDED_NOTE_BLOCK) {
            NoteConfig cfg = notes.getOrDefault(objectKey, defaultNoteConfig());
            variant = Math.floorMod(cfg.note(), 12);
        }
        return new RenderObjectState(typeId, powered, variant);
    }

    private boolean isProjectionPowered(String receiverKey) {
        for (Map.Entry<String, String> route : transmitterProjectionTarget.entrySet()) {
            if (!receiverKey.equals(route.getValue())) continue;
            if (transmitterPower.getOrDefault(route.getKey(), false)) return true;
        }
        return false;
    }

    private void broadcastRenderUpsert(BlockRef ref, RenderObjectState state) {
        World world = Bukkit.getWorld(ref.worldId());
        if (world == null) return;
        byte[] payload = PayloadCodec.objectSync(
                1, ref.x(), ref.y(), ref.z(), state.typeId(), state.powered(), state.variant());
        for (Player player : world.getPlayers()) {
            if (player.getListeningPluginChannels().contains(OBJECT_SYNC)) {
                player.sendPluginMessage(this, OBJECT_SYNC, payload);
            }
        }
    }

    private void broadcastRenderRemove(BlockRef ref) {
        World world = Bukkit.getWorld(ref.worldId());
        if (world == null) return;
        byte[] payload = PayloadCodec.objectSync(2, ref.x(), ref.y(), ref.z(), 0, false, 0);
        for (Player player : world.getPlayers()) {
            if (player.getListeningPluginChannels().contains(OBJECT_SYNC)) {
                player.sendPluginMessage(this, OBJECT_SYNC, payload);
            }
        }
    }

    private void sendRenderUpsert(Player player, BlockRef ref, RenderObjectState state) {
        player.sendPluginMessage(this, OBJECT_SYNC, PayloadCodec.objectSync(
                1, ref.x(), ref.y(), ref.z(), state.typeId(), state.powered(), state.variant()));
    }

'''
if "private void sendRenderSnapshot" not in text:
    if helper_anchor not in text:
        raise SystemExit("Could not find wireless-redstone helper anchor")
    text = text.replace(helper_anchor, helper_block + helper_anchor, 1)

# Sync after each bridge logic pass so redstone/pitch/placement state changes are
# reflected in client chunk meshes on the next tick.
tick_anchor = "        tickProjectionSessions();\n    }\n"
tick_replacement = "        tickProjectionSessions();\n        syncChangedRenderStates();\n    }\n"
if "        syncChangedRenderStates();" not in text:
    if tick_anchor not in text:
        raise SystemExit("Could not find tickBridgeLogic tail")
    text = text.replace(tick_anchor, tick_replacement, 1)

# ---------------------------------------------------------------------------
# Render state record near other bridge records.
# ---------------------------------------------------------------------------
record_anchor = """    private record ProjectionSession(String receiverKey, String playbackKey, List<ProjectionNote> notes,\n                                     long startedNanos, int nextNote) {\n    }\n\n"""
record_replacement = record_anchor + """    private record RenderObjectState(int typeId, boolean powered, int variant) {\n    }\n\n"""
if "private record RenderObjectState" not in text:
    if record_anchor not in text:
        raise SystemExit("Could not find record insertion anchor")
    text = text.replace(record_anchor, record_replacement, 1)

# ---------------------------------------------------------------------------
# Raw payload encoder. Format mirrors Fabric FriendlyByteBuf codec exactly:
# int op, packed BlockPos long, int type, boolean powered, int variant.
# ---------------------------------------------------------------------------
payload_anchor = """    private static final class PayloadCodec {\n        private static byte[] noteEdit(int x, int y, int z, NoteConfig cfg) {\n"""
payload_replacement = """    private static final class PayloadCodec {\n        private static byte[] objectSync(int operation, int x, int y, int z, int typeId, boolean powered, int variant) {\n            return write(out -> {\n                out.writeInt(operation);\n                out.writeLong(packBlockPos(x, y, z));\n                out.writeInt(typeId);\n                out.writeBoolean(powered);\n                out.writeInt(variant);\n            });\n        }\n\n        private static byte[] noteEdit(int x, int y, int z, NoteConfig cfg) {\n"""
if "private static byte[] objectSync" not in text:
    if payload_anchor not in text:
        raise SystemExit("Could not find PayloadCodec noteEdit anchor; run interaction patch first")
    text = text.replace(payload_anchor, payload_replacement, 1)

if text != original:
    SOURCE.write_text(text, encoding="utf-8")
    print(f"patched {SOURCE.relative_to(ROOT)}")
else:
    print("Paper placed render sync already prepared")
