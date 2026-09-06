#!/usr/bin/env python3
"""Inject entity-free placed ENB visuals for resource-pack-only players.

Run after prepare_paper_listener_pack.py. Paper Client keeps its existing
position-aware renderer. Unmodified clients that loaded the combined server
pack receive two reserved Note Block states with sendBlockChange(s); the real
world block and every ordinary vanilla block remain untouched.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "bridge" / "src" / "main" / "java" / "com" / "goldenegggovo"
          / "extendednoteblock" / "bridge" / "ExtendedNoteBlockBridge.java")

text = SOURCE.read_text(encoding="utf-8")
original = text

# ---------------------------------------------------------------------------
# Paper API imports
# ---------------------------------------------------------------------------
if "import io.papermc.paper.event.packet.PlayerChunkLoadEvent;" not in text:
    text = text.replace(
        "import net.kyori.adventure.text.Component;\n\n",
        "import net.kyori.adventure.text.Component;\n\n"
        "import io.papermc.paper.event.packet.PlayerChunkLoadEvent;\n\n",
        1,
    )

if "import org.bukkit.Chunk;" not in text:
    text = text.replace("import org.bukkit.Bukkit;\n", "import org.bukkit.Bukkit;\nimport org.bukkit.Chunk;\n", 1)

if "import org.bukkit.Instrument;" not in text:
    text = text.replace("import org.bukkit.GameMode;\n", "import org.bukkit.GameMode;\nimport org.bukkit.Instrument;\n", 1)

if "import org.bukkit.Note;" not in text:
    text = text.replace("import org.bukkit.NamespacedKey;\n", "import org.bukkit.NamespacedKey;\nimport org.bukkit.Note;\n", 1)

if "import org.bukkit.block.BlockState;" not in text:
    text = text.replace("import org.bukkit.block.Block;\n", "import org.bukkit.block.Block;\nimport org.bukkit.block.BlockState;\n", 1)

if "import org.bukkit.block.data.BlockData;" not in text:
    text = text.replace(
        "import org.bukkit.block.BlockState;\n",
        "import org.bukkit.block.BlockState;\nimport org.bukkit.block.data.BlockData;\n",
        1,
    )

# ---------------------------------------------------------------------------
# Chunk index and the two resource-pack state selectors
# ---------------------------------------------------------------------------
field_anchor = "    private Component listenerPackPrompt;\n"
field_block = field_anchor + r'''    private final Map<UUID, Map<Long, Set<String>>> vanillaEnbChunks = new HashMap<>();
    private final Set<BlockRef> pendingVanillaEnbUpdates = new HashSet<>();
    private BlockData vanillaEnbOffState;
    private BlockData vanillaEnbOnState;
    private BukkitTask vanillaEnbFlushTask;
'''
if "vanillaEnbChunks" not in text:
    if field_anchor not in text:
        raise SystemExit("Could not find listener-pack field anchor; run prepare_paper_listener_pack.py first")
    text = text.replace(field_anchor, field_block, 1)

enable_anchor = "        bridgeTypeKey = new NamespacedKey(this, \"enb_type\");\n"
enable_replacement = enable_anchor + r'''        // These states are never written into the world. They are reserved by
        // the combined resource pack for coordinate-local, entity-free ENB visuals.
        vanillaEnbOffState = createVanillaEnbState(false);
        vanillaEnbOnState = createVanillaEnbState(true);
'''
if "vanillaEnbOffState = createVanillaEnbState(false)" not in text:
    if enable_anchor not in text:
        raise SystemExit("Could not find onEnable bridge key anchor")
    text = text.replace(enable_anchor, enable_replacement, 1)

disable_anchor = "        projectionStopTasks.values().forEach(BukkitTask::cancel);\n"
disable_replacement = disable_anchor + r'''        if (vanillaEnbFlushTask != null) vanillaEnbFlushTask.cancel();
        vanillaEnbFlushTask = null;
        pendingVanillaEnbUpdates.clear();
        // Undo client-only substitutions while the plugin can still read the
        // authoritative world state. No world block is changed by this operation.
        for (Player player : Bukkit.getOnlinePlayers()) restoreVanillaEnbVisuals(player);
'''
if "pendingVanillaEnbUpdates.clear();\n        // Undo client-only substitutions" not in text:
    if disable_anchor not in text:
        raise SystemExit("Could not find onDisable task anchor")
    text = text.replace(disable_anchor, disable_replacement, 1)

# ---------------------------------------------------------------------------
# Resource-pack lifecycle and Paper Client separation
# ---------------------------------------------------------------------------
status_head = r'''        String status = event.getStatus().name();
        listenerPackStates.put(event.getPlayer().getUniqueId(), status);
        if ("SUCCESSFULLY_LOADED".equals(status)) {
            listenerPackReady.add(event.getPlayer().getUniqueId());
            getLogger().info("ENB listener resource pack loaded by " + event.getPlayer().getName());
            event.getPlayer().sendMessage("ENB 资源包已加载：原版客户端 MIDI 0-127 聆听模式已启用。");
'''
status_replacement = r'''        Player player = event.getPlayer();
        String status = event.getStatus().name();
        if (isPaperClient(player)) {
            restoreVanillaEnbVisuals(player);
            listenerPackReady.remove(player.getUniqueId());
            listenerPackStates.put(player.getUniqueId(), "MOD_CLIENT");
            if ("SUCCESSFULLY_LOADED".equals(status)) player.removeResourcePack(listenerPackId);
            return;
        }
        listenerPackStates.put(player.getUniqueId(), status);
        if ("SUCCESSFULLY_LOADED".equals(status)) {
            listenerPackReady.add(player.getUniqueId());
            getLogger().info("ENB listener resource pack loaded by " + player.getName());
            player.sendMessage("ENB 资源包已加载：MIDI 0-127 聆听与 ENB 方块材质已启用。");
            Bukkit.getScheduler().runTask(this, () -> syncVanillaEnbVisuals(player));
'''
if "MIDI 0-127 聆听与 ENB 方块材质已启用" not in text:
    if status_head not in text:
        raise SystemExit("Could not find resource-pack status head")
    text = text.replace(status_head, status_replacement, 1)

failure_anchor = r'''        } else if (status.equals("DECLINED") || status.startsWith("FAILED")
                || status.equals("INVALID_URL") || status.equals("DISCARDED")) {
            listenerPackReady.remove(event.getPlayer().getUniqueId());
'''
failure_replacement = r'''        } else if (status.equals("DECLINED") || status.startsWith("FAILED")
                || status.equals("INVALID_URL") || status.equals("DISCARDED")) {
            restoreVanillaEnbVisuals(player);
            listenerPackReady.remove(player.getUniqueId());
'''
if failure_replacement not in text:
    if failure_anchor not in text:
        raise SystemExit("Could not find resource-pack failure branch")
    text = text.replace(failure_anchor, failure_replacement, 1)

send_head = r'''    private void sendListenerResourcePack(Player player, boolean announce) {
        if (!listenerPackEnabled || !player.isOnline()) return;
        listenerPackReady.remove(player.getUniqueId());
'''
send_replacement = r'''    private void sendListenerResourcePack(Player player, boolean announce) {
        if (!listenerPackEnabled || !player.isOnline()) return;
        if (isPaperClient(player)) {
            restoreVanillaEnbVisuals(player);
            listenerPackReady.remove(player.getUniqueId());
            listenerPackStates.put(player.getUniqueId(), "MOD_CLIENT");
            if (announce) player.sendMessage("已检测到 Paper Client：保留完整 ENB 材质，不下发原版客户端资源包。");
            return;
        }
        restoreVanillaEnbVisuals(player);
        listenerPackReady.remove(player.getUniqueId());
'''
if "已检测到 Paper Client：保留完整 ENB 材质" not in text:
    if send_head not in text:
        raise SystemExit("Could not find resource-pack send method head")
    text = text.replace(send_head, send_replacement, 1)

# ---------------------------------------------------------------------------
# Per-player chunk load/world/channel events
# ---------------------------------------------------------------------------
world_event = r'''    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> sendRenderSnapshot(event.getPlayer()), 1L);
    }
'''
world_replacement = r'''    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            sendRenderSnapshot(event.getPlayer());
            syncVanillaEnbVisuals(event.getPlayer());
        }, 1L);
    }
'''
if "syncVanillaEnbVisuals(event.getPlayer());" not in text:
    if world_event not in text:
        raise SystemExit("Could not find PlayerChangedWorldEvent handler")
    text = text.replace(world_event, world_replacement, 1)

channel_event = r'''    @EventHandler
    public void onPlayerRegisterChannel(PlayerRegisterChannelEvent event) {
        if (OBJECT_SYNC.equals(event.getChannel())) {
            Bukkit.getScheduler().runTask(this, () -> sendRenderSnapshot(event.getPlayer()));
        }
    }
'''
channel_replacement = r'''    @EventHandler
    public void onPlayerRegisterChannel(PlayerRegisterChannelEvent event) {
        if (OBJECT_SYNC.equals(event.getChannel())) {
            Bukkit.getScheduler().runTask(this, () -> {
                Player player = event.getPlayer();
                boolean hadListenerPack = listenerPackStates.containsKey(player.getUniqueId());
                restoreVanillaEnbVisuals(player);
                listenerPackReady.remove(player.getUniqueId());
                listenerPackStates.put(player.getUniqueId(), "MOD_CLIENT");
                if (hadListenerPack && listenerPackId != null) player.removeResourcePack(listenerPackId);
                sendRenderSnapshot(player);
            });
        }
    }

    @EventHandler
    public void onPlayerChunkLoad(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        UUID worldId = event.getChunk().getWorld().getUID();
        long chunkKey = event.getChunk().getChunkKey();
        // Run after the chunk packet itself so this coordinate-local overlay is
        // always the last block state the vanilla client receives.
        Bukkit.getScheduler().runTask(this,
                () -> sendVanillaEnbChunk(player, worldId, chunkKey));
    }
'''
if "public void onPlayerChunkLoad" not in text:
    if channel_event not in text:
        raise SystemExit("Could not find PlayerRegisterChannelEvent handler")
    text = text.replace(channel_event, channel_replacement, 1)

# ---------------------------------------------------------------------------
# Entity-free vanilla visual helpers
# ---------------------------------------------------------------------------
helper_anchor = r'''    // -------------------------------------------------------------------------
    // Paper Client placed-object render synchronization
    // -------------------------------------------------------------------------
'''
helper_block = r'''    // -------------------------------------------------------------------------
    // Resource-pack-only placed ENB rendering (no display entities)
    // -------------------------------------------------------------------------

    private BlockData createVanillaEnbState(boolean powered) {
        org.bukkit.block.data.type.NoteBlock state =
                (org.bukkit.block.data.type.NoteBlock) Material.NOTE_BLOCK.createBlockData();
        state.setInstrument(Instrument.CUSTOM_HEAD);
        state.setNote(new Note(24));
        state.setPowered(powered);
        return state;
    }

    private boolean isPaperClient(Player player) {
        Set<String> channels = player.getListeningPluginChannels();
        return channels.contains(OBJECT_SYNC) || channels.contains(START_SOUND);
    }

    private boolean isVanillaEnbViewer(Player player) {
        return player.isOnline() && listenerPackReady.contains(player.getUniqueId())
                && !isPaperClient(player);
    }

    private void syncVanillaEnbVisuals(Player player) {
        if (!isVanillaEnbViewer(player)) return;
        UUID worldId = player.getWorld().getUID();
        for (long chunkKey : player.getSentChunkKeys()) {
            sendVanillaEnbChunk(player, worldId, chunkKey);
        }
    }

    private void sendVanillaEnbChunk(Player player, UUID worldId, long chunkKey) {
        if (!isVanillaEnbViewer(player) || !player.getWorld().getUID().equals(worldId)
                || !player.isChunkSent(chunkKey)) return;
        World world = Bukkit.getWorld(worldId);
        Map<Long, Set<String>> chunks = vanillaEnbChunks.get(worldId);
        if (world == null || chunks == null) return;
        Set<String> keys = chunks.get(chunkKey);
        if (keys == null || keys.isEmpty()) return;
        if (!world.isChunkLoaded((int) chunkKey, (int) (chunkKey >> 32))) return;

        List<BlockState> states = new ArrayList<>(keys.size());
        for (String objectKey : keys) {
            if (objects.get(objectKey) != BridgeItemType.EXTENDED_NOTE_BLOCK) continue;
            BlockRef ref = parseKey(objectKey);
            if (ref == null || ref.y() < world.getMinHeight() || ref.y() >= world.getMaxHeight()) continue;
            Block block = world.getBlockAt(ref.x(), ref.y(), ref.z());
            if (block.getType() != Material.NOTE_BLOCK) continue;
            BlockState state = block.getState();
            state.setBlockData((block.getBlockPower() > 0 ? vanillaEnbOnState : vanillaEnbOffState).clone());
            states.add(state);
        }
        if (!states.isEmpty()) player.sendBlockChanges(states);
    }

    private void restoreVanillaEnbVisuals(Player player) {
        if (!player.isOnline() || !listenerPackReady.contains(player.getUniqueId())) return;
        UUID worldId = player.getWorld().getUID();
        World world = Bukkit.getWorld(worldId);
        Map<Long, Set<String>> chunks = vanillaEnbChunks.get(worldId);
        if (world == null || chunks == null) return;

        for (long chunkKey : player.getSentChunkKeys()) {
            Set<String> keys = chunks.get(chunkKey);
            if (keys == null || keys.isEmpty()
                    || !world.isChunkLoaded((int) chunkKey, (int) (chunkKey >> 32))) continue;
            List<BlockState> states = new ArrayList<>(keys.size());
            for (String objectKey : keys) {
                BlockRef ref = parseKey(objectKey);
                if (ref == null || ref.y() < world.getMinHeight() || ref.y() >= world.getMaxHeight()) continue;
                states.add(world.getBlockAt(ref.x(), ref.y(), ref.z()).getState());
            }
            if (!states.isEmpty()) player.sendBlockChanges(states);
        }
    }

    private void broadcastVanillaEnbVisual(BlockRef ref, boolean powered) {
        queueVanillaEnbUpdate(ref);
    }

    private void broadcastVanillaEnbRestore(BlockRef ref) {
        queueVanillaEnbUpdate(ref);
    }

    private void queueVanillaEnbUpdate(BlockRef ref) {
        pendingVanillaEnbUpdates.add(ref);
        if (vanillaEnbFlushTask != null) return;
        vanillaEnbFlushTask = Bukkit.getScheduler().runTask(this, () -> {
            vanillaEnbFlushTask = null;
            flushVanillaEnbUpdates();
        });
    }

    private void flushVanillaEnbUpdates() {
        if (pendingVanillaEnbUpdates.isEmpty()) return;
        Map<UUID, List<BlockRef>> updatesByWorld = new HashMap<>();
        for (BlockRef ref : pendingVanillaEnbUpdates) {
            updatesByWorld.computeIfAbsent(ref.worldId(), ignored -> new ArrayList<>()).add(ref);
        }
        pendingVanillaEnbUpdates.clear();

        for (Map.Entry<UUID, List<BlockRef>> update : updatesByWorld.entrySet()) {
            World world = Bukkit.getWorld(update.getKey());
            if (world == null) continue;
            for (Player player : world.getPlayers()) {
                if (!isVanillaEnbViewer(player)) continue;
                List<BlockState> states = new ArrayList<>(update.getValue().size());
                for (BlockRef ref : update.getValue()) {
                    if (ref.y() < world.getMinHeight() || ref.y() >= world.getMaxHeight()) continue;
                    long chunkKey = Chunk.getChunkKey(ref.x() >> 4, ref.z() >> 4);
                    if (!player.isChunkSent(chunkKey)
                            || !world.isChunkLoaded(ref.x() >> 4, ref.z() >> 4)) continue;

                    Block block = world.getBlockAt(ref.x(), ref.y(), ref.z());
                    BlockState state = block.getState();
                    if (objects.get(key(block)) == BridgeItemType.EXTENDED_NOTE_BLOCK
                            && block.getType() == Material.NOTE_BLOCK) {
                        state.setBlockData((block.getBlockPower() > 0
                                ? vanillaEnbOnState : vanillaEnbOffState).clone());
                    }
                    states.add(state);
                }
                if (!states.isEmpty()) player.sendBlockChanges(states);
            }
        }
    }

'''
if "private boolean isVanillaEnbViewer" not in text:
    if helper_anchor not in text:
        raise SystemExit("Could not find Paper Client render helper anchor")
    text = text.replace(helper_anchor, helper_block + helper_anchor, 1)

# ---------------------------------------------------------------------------
# Keep vanilla viewers synchronized with the same authoritative state used by
# the Paper Client payload.
# ---------------------------------------------------------------------------
upsert_head = r'''    private void broadcastRenderUpsert(BlockRef ref, RenderObjectState state) {
        World world = Bukkit.getWorld(ref.worldId());
'''
upsert_replacement = r'''    private void broadcastRenderUpsert(BlockRef ref, RenderObjectState state) {
        if (state.typeId() == 0) broadcastVanillaEnbVisual(ref, state.powered());
        else broadcastVanillaEnbRestore(ref);
        World world = Bukkit.getWorld(ref.worldId());
'''
if "if (state.typeId() == 0) broadcastVanillaEnbVisual" not in text:
    if upsert_head not in text:
        raise SystemExit("Could not find render upsert method")
    text = text.replace(upsert_head, upsert_replacement, 1)

remove_head = r'''    private void broadcastRenderRemove(BlockRef ref) {
        World world = Bukkit.getWorld(ref.worldId());
'''
remove_replacement = r'''    private void broadcastRenderRemove(BlockRef ref) {
        broadcastVanillaEnbRestore(ref);
        World world = Bukkit.getWorld(ref.worldId());
'''
if "private void broadcastRenderRemove(BlockRef ref) {\n        broadcastVanillaEnbRestore(ref);" not in text:
    if remove_head not in text:
        raise SystemExit("Could not find render remove method")
    text = text.replace(remove_head, remove_replacement, 1)

# ---------------------------------------------------------------------------
# Maintain an O(visible ENBs) chunk index, including 75k-note imports.
# ---------------------------------------------------------------------------
rebuild_head = r'''    private void rebuildObjectIndexes() {
        transmitterKeys.clear();
'''
rebuild_replacement = r'''    private void rebuildObjectIndexes() {
        vanillaEnbChunks.clear();
        transmitterKeys.clear();
'''
if "private void rebuildObjectIndexes() {\n        vanillaEnbChunks.clear();" not in text:
    if rebuild_head not in text:
        raise SystemExit("Could not find object-index rebuild method")
    text = text.replace(rebuild_head, rebuild_replacement, 1)

index_case = r'''        switch (type) {
            case GLOBAL_REDSTONE_TRANSMITTER -> transmitterKeys.add(key);
'''
index_replacement = r'''        switch (type) {
            case EXTENDED_NOTE_BLOCK -> {
                BlockRef ref = parseKey(key);
                if (ref != null) vanillaEnbChunks
                        .computeIfAbsent(ref.worldId(), ignored -> new HashMap<>())
                        .computeIfAbsent(Chunk.getChunkKey(ref.x() >> 4, ref.z() >> 4), ignored -> new HashSet<>())
                        .add(key);
            }
            case GLOBAL_REDSTONE_TRANSMITTER -> transmitterKeys.add(key);
'''
if "case EXTENDED_NOTE_BLOCK -> {\n                BlockRef ref = parseKey(key);" not in text:
    if index_case not in text:
        raise SystemExit("Could not find indexObject switch")
    text = text.replace(index_case, index_replacement, 1)

unindex_method = r'''    private void unindexObject(String key, BridgeItemType type) {
        switch (type) {
            case GLOBAL_REDSTONE_TRANSMITTER -> transmitterKeys.remove(key);
'''
unindex_replacement = r'''    private void unindexObject(String key, BridgeItemType type) {
        switch (type) {
            case EXTENDED_NOTE_BLOCK -> {
                BlockRef ref = parseKey(key);
                if (ref != null) {
                    Map<Long, Set<String>> chunks = vanillaEnbChunks.get(ref.worldId());
                    if (chunks != null) {
                        long chunkKey = Chunk.getChunkKey(ref.x() >> 4, ref.z() >> 4);
                        Set<String> keys = chunks.get(chunkKey);
                        if (keys != null && keys.remove(key) && keys.isEmpty()) chunks.remove(chunkKey);
                        if (chunks.isEmpty()) vanillaEnbChunks.remove(ref.worldId());
                    }
                }
            }
            case GLOBAL_REDSTONE_TRANSMITTER -> transmitterKeys.remove(key);
'''
if "Map<Long, Set<String>> chunks = vanillaEnbChunks.get(ref.worldId());" not in text.split("private void unindexObject", 1)[-1]:
    if unindex_method not in text:
        raise SystemExit("Could not find unindexObject switch")
    text = text.replace(unindex_method, unindex_replacement, 1)

# Restore the client overlays before reload clears pack readiness and rebuilds
# the authoritative object/chunk indexes.
reload_head = r'''            case "reload" -> {
                reloadConfig();
                loadListenerResourcePackSettings();
'''
reload_replacement = r'''            case "reload" -> {
                for (Player player : Bukkit.getOnlinePlayers()) restoreVanillaEnbVisuals(player);
                reloadConfig();
                loadListenerResourcePackSettings();
'''
if "case \"reload\" -> {\n                for (Player player : Bukkit.getOnlinePlayers()) restoreVanillaEnbVisuals(player);" not in text:
    if reload_head not in text:
        raise SystemExit("Could not find listener-pack reload branch")
    text = text.replace(reload_head, reload_replacement, 1)

if text != original:
    SOURCE.write_text(text, encoding="utf-8")
    print(f"patched {SOURCE.relative_to(ROOT)}")
else:
    print("Paper vanilla placed visual source already prepared")
