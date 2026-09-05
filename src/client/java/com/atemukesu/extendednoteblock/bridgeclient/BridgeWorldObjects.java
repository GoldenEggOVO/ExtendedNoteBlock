package com.atemukesu.extendednoteblock.bridgeclient;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Client-side authoritative snapshot of Paper bridge objects in the current
 * dimension. The Paper server owns the data; this cache exists only so block
 * models can choose the Full Fabric appearance for specific vanilla-carrier
 * coordinates.
 */
public final class BridgeWorldObjects {
    public static final int OP_CLEAR = 0;
    public static final int OP_UPSERT = 1;
    public static final int OP_REMOVE = 2;

    public static final int TYPE_EXTENDED_NOTE_BLOCK = 0;
    public static final int TYPE_TRANSMITTER = 1;
    public static final int TYPE_RECEIVER = 2;
    public static final int TYPE_PROJECTION_RECEIVER = 3;

    private static final Map<BlockPos, Entry> OBJECTS = new ConcurrentHashMap<>();

    private BridgeWorldObjects() {
    }

    public static Entry get(BlockPos pos) {
        return OBJECTS.get(pos);
    }

    public static void apply(BridgeClientPayloads.ObjectSyncPayload payload) {
        switch (payload.operation()) {
            case OP_CLEAR -> clear();
            case OP_UPSERT -> {
                BlockPos pos = payload.pos().immutable();
                Entry next = new Entry(payload.typeId(), payload.powered(), Math.floorMod(payload.variant(), 12));
                Entry previous = OBJECTS.put(pos, next);
                if (!next.equals(previous)) markDirty(pos);
            }
            case OP_REMOVE -> {
                BlockPos pos = payload.pos().immutable();
                if (OBJECTS.remove(pos) != null) markDirty(pos);
            }
            default -> {
                // Ignore unknown future operations for forward compatibility.
            }
        }
    }

    public static void clear() {
        if (OBJECTS.isEmpty()) return;
        ArrayList<BlockPos> oldPositions = new ArrayList<>(OBJECTS.keySet());
        OBJECTS.clear();
        for (BlockPos pos : oldPositions) markDirty(pos);
    }

    private static void markDirty(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.levelExtractor != null) {
            client.levelExtractor.setBlocksDirty(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public record Entry(int typeId, boolean powered, int variant) {
    }
}
