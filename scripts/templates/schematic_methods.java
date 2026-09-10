    private static BridgeItemType schematicType(int type) {
        return switch (type) {
            case 0 -> BridgeItemType.EXTENDED_NOTE_BLOCK;
            case 1 -> BridgeItemType.GLOBAL_REDSTONE_TRANSMITTER;
            case 2 -> BridgeItemType.GLOBAL_REDSTONE_RECEIVER;
            case 3 -> BridgeItemType.NBS_PROJECTION_RECEIVER;
            default -> throw new IllegalArgumentException("Unsupported schematic object type");
        };
    }

    private SchematicTransfer.Document captureSchematic(World world, List<SchematicTransfer.Box> boxes) {
        if (craftEngine == null) throw new IllegalArgumentException("CraftEngine ENB is unavailable");
        List<SchematicTransfer.Entry> result = new ArrayList<>();
        for (var object : objects.entrySet()) {
            BlockRef ref = parseKey(object.getKey());
            if (ref == null || !ref.worldId().equals(world.getUID())) continue;
            var pos = new ProjectionImport.Pos(ref.x(), ref.y(), ref.z());
            boolean included = boxes.stream().anyMatch(box ->
                    pos.x() >= box.min().x() && pos.x() <= box.max().x()
                    && pos.y() >= box.min().y() && pos.y() <= box.max().y()
                    && pos.z() >= box.min().z() && pos.z() <= box.max().z());
            if (!included) continue;
            if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4))
                throw new IllegalArgumentException("ENB source chunk is not loaded");
            BridgeItemType type = object.getValue();
            if (!type.placeable || !craftEngine.acceptsCopy(world.getBlockAt(pos.x(), pos.y(), pos.z()), type)) continue;
            int id = switch (type) {
                case EXTENDED_NOTE_BLOCK -> 0;
                case GLOBAL_REDSTONE_TRANSMITTER -> 1;
                case GLOBAL_REDSTONE_RECEIVER -> 2;
                case NBS_PROJECTION_RECEIVER -> 3;
                default -> throw new IllegalArgumentException("Non-placeable ENB object");
            };
            ProjectionImport.Note note = null;
            if (id == 0) {
                NoteConfig n = notes.get(object.getKey());
                if (n == null) throw new IllegalArgumentException("ENB note settings are missing at " + pos.display());
                note = new ProjectionImport.Note(pos, n.note(), n.instrumentId(), n.velocity(), n.sustainTicks(),
                        n.delayMs(), n.fadeInTicks(), n.fadeOutTicks(), n.pitchCents());
            }
            List<SchematicTransfer.Tone> timeline = id == 3
                    ? projectionNotes.getOrDefault(object.getKey(), List.of()).stream().map(n ->
                        new SchematicTransfer.Tone(n.instrumentId(), n.midiNote(), n.velocity(), n.sustainTicks(),
                                n.pitchCents(), n.delayMs())).toList()
                    : List.of();
            result.add(new SchematicTransfer.Entry(pos, id, note, timeline));
        }
        return new SchematicTransfer.Document(result);
    }

    private boolean acceptsSchematic(World world, SchematicTransfer.Entry entry) {
        var pos = entry.pos();
        if (craftEngine == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) return false;
        BridgeItemType type = schematicType(entry.type());
        BridgeItemType existing = objects.get(importKey(world, pos));
        return (existing == null || existing == type)
                && craftEngine.acceptsCopy(world.getBlockAt(pos.x(), pos.y(), pos.z()), type);
    }

    private boolean restoreSchematic(World world, SchematicTransfer.Document document) {
        // Recheck the complete target before changing either physical blocks or the ledger.
        for (var entry : document.entries()) if (!acceptsSchematic(world, entry)) return false;
        Map<Block, org.bukkit.block.data.BlockData> previousBlocks = new java.util.LinkedHashMap<>();
        try {
            for (var entry : document.entries()) {
                var p = entry.pos();
                Block block = world.getBlockAt(p.x(), p.y(), p.z());
                previousBlocks.put(block, block.getBlockData().clone());
                if (!craftEngine.placeCopy(block, schematicType(entry.type()), entry.note() == null ? 0 : entry.note().midi()))
                    throw new IllegalStateException("CraftEngine rejected copied block at " + p.display());
            }
        } catch (RuntimeException failed) {
            List<Block> blocks = new ArrayList<>(previousBlocks.keySet());
            java.util.Collections.reverse(blocks);
            for (Block block : blocks) block.setBlockData(previousBlocks.get(block), false);
            getLogger().warning("Schematic conversion rolled back: " + failed.getMessage());
            return false;
        }
        for (var entry : document.entries()) {
            String key = importKey(world, entry.pos());
            BridgeItemType type = schematicType(entry.type());
            stopActive(key);
            stopProjection(key);
            objects.put(key, type);
            indexObject(key, type);
            transmitterPower.remove(key);
            transmitterProjectionTarget.remove(key);
            if (entry.note() != null) {
                var n = entry.note();
                notes.put(key, new NoteConfig(n.midi(), n.instrument(), n.velocity(), n.sustain(), n.delayMs(),
                        n.fadeIn(), n.fadeOut(), n.pitchCents()));
            }
            if (entry.type() == 3) projectionNotes.put(key, entry.timeline().stream().map(n ->
                    new ProjectionNote(n.instrument(), n.midi(), n.velocity(), n.sustain(), n.pitchCents(), n.delayMs())).toList());
        }
        requestSave("objects");
        requestSave("notes");
        requestSave("projections");
        boolean saved = flushPendingSaves();
        if (!saved) requestSave(pendingSaves.iterator().next());
        return saved;
    }
