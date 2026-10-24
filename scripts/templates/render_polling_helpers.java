    private void indexRenderObject(String key, BlockRef ref) {
        if (ref == null) return;
        World world = Bukkit.getWorld(ref.worldId());
        renderIndex.add(key, ref.worldId(), ref.x(), ref.z(),
                world != null && world.isChunkLoaded(ref.x() >> 4, ref.z() >> 4));
        markRenderRouteDirty(key);
    }

    private void markRenderRouteDirty(String key) {
        dirtyRenderObjects.add(key);
        String previousTarget = transmitterProjectionTarget.get(key);
        if (previousTarget != null) dirtyRenderObjects.add(previousTarget);
    }

    private void refreshRenderObject(String key, boolean active) {
        BridgeItemType type = objects.get(key);
        BlockRef ref = blockRefs.get(key);
        if (type == null) {
            RenderObjectState previous = lastRenderStates.remove(key);
            if (previous != null) {
                if (ref == null) ref = parseKey(key);
                if (ref != null) broadcastRenderRemove(ref);
            }
            if (craftEngine != null) craftEngine.forget(key);
            return;
        }
        if (!type.placeable) return;
        Block block = active ? getLoadedBlock(key) : null;
        RenderObjectState previous = lastRenderStates.get(key);
        RenderObjectState next = currentRenderState(key, type, ref, block, previous);
        if (craftEngine != null) craftEngine.sync(key, type, next, block);
        if (next != previous) {
            lastRenderStates.put(key, next);
            if (ref != null) broadcastRenderUpsert(ref, next);
        }
    }

    private RenderObjectState currentRenderState(String key, BridgeItemType type, BlockRef ref,
            Block block, RenderObjectState previous) {
        int typeId = switch (type) {
            case EXTENDED_NOTE_BLOCK -> 0;
            case GLOBAL_REDSTONE_TRANSMITTER -> 1;
            case GLOBAL_REDSTONE_RECEIVER -> 2;
            case NBS_PROJECTION_RECEIVER -> 3;
            case CONDUCTOR_WAND -> -1;
        };
        boolean powered;
        if (type == BridgeItemType.GLOBAL_REDSTONE_TRANSMITTER) {
            Boolean known = transmitterPower.get(key);
            powered = known != null ? known : block != null && block.getBlockPower() > 0;
        } else if (type == BridgeItemType.GLOBAL_REDSTONE_RECEIVER) {
            Boolean known = ref == null ? null : globalPower.get(ref.worldId());
            powered = known != null ? known : block != null && block.getType() == Material.REDSTONE_BLOCK;
        } else if (type == BridgeItemType.NBS_PROJECTION_RECEIVER) {
            powered = isProjectionPowered(key);
        } else {
            powered = type == BridgeItemType.EXTENDED_NOTE_BLOCK && block != null && block.getBlockPower() > 0;
        }
        int variant = 0;
        if (type == BridgeItemType.EXTENDED_NOTE_BLOCK) {
            NoteConfig cfg = notes.get(key);
            variant = Math.floorMod(cfg == null ? 60 : cfg.note(), 12);
        }
        if (previous != null && previous.typeId() == typeId && previous.powered() == powered
                && previous.variant() == variant) return previous;
        return new RenderObjectState(typeId, powered, variant);
    }

    @EventHandler
    public void onRenderChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        var chunk = event.getChunk();
        dirtyRenderObjects.addAll(renderIndex.setLoaded(event.getWorld().getUID(), chunk.getX(), chunk.getZ(), true));
    }

    @EventHandler
    public void onRenderChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        var chunk = event.getChunk();
        Set<String> keys = renderIndex.setLoaded(event.getWorld().getUID(), chunk.getX(), chunk.getZ(), false);
        dirtyRenderObjects.addAll(keys);
        if (craftEngine != null) for (String key : keys) craftEngine.forget(key);
    }

    @EventHandler
    public void onRenderWorldLoad(org.bukkit.event.world.WorldLoadEvent event) {
        for (var chunk : event.getWorld().getLoadedChunks()) {
            dirtyRenderObjects.addAll(renderIndex.setLoaded(event.getWorld().getUID(), chunk.getX(), chunk.getZ(), true));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.MONITOR)
    public void onRenderWorldUnload(org.bukkit.event.world.WorldUnloadEvent event) {
        Set<String> keys = renderIndex.unloadWorld(event.getWorld().getUID());
        dirtyRenderObjects.addAll(keys);
        if (craftEngine != null) for (String key : keys) craftEngine.forget(key);
    }
