    // METHOD: syncChangedRenderStates
    private void syncChangedRenderStates() {
        if (craftEngine != null) craftEngine.beginTick();
        for (String key : renderIndex.activeKeys()) refreshRenderObject(key, true);
        var dirty = dirtyRenderObjects.iterator();
        while (dirty.hasNext()) {
            String key = dirty.next();
            dirty.remove();
            if (!renderIndex.activeKeys().contains(key)) refreshRenderObject(key, false);
        }
    }
    // METHOD: currentRenderState
    RenderObjectState currentRenderState(String objectKey, BridgeItemType type) {
        return currentRenderState(objectKey, type, parseKey(objectKey), getLoadedBlock(objectKey),
                lastRenderStates.get(objectKey));
    }
    // METHOD: sendRenderSnapshot
    private void sendRenderSnapshot(Player player) {
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(OBJECT_SYNC)) return;
        player.sendPluginMessage(this, OBJECT_SYNC, PayloadCodec.objectSync(0, 0, 0, 0, 0, false, 0));
        for (String key : renderIndex.worldKeys(player.getWorld().getUID())) {
            BridgeItemType type = objects.get(key);
            BlockRef ref = blockRefs.get(key);
            if (type == null || !type.placeable || ref == null) continue;
            Block block = renderIndex.activeKeys().contains(key) ? getLoadedBlock(key) : null;
            RenderObjectState state = currentRenderState(key, type, ref, block, lastRenderStates.get(key));
            sendRenderUpsert(player, ref, state);
        }
    }
