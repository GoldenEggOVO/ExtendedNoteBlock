package com.atemukesu.extendednoteblock.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.atemukesu.extendednoteblock.block.ExtendedNoteBlockBlock;
import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.item.ConductorWandItem;
import com.atemukesu.extendednoteblock.map.InstrumentMap;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class ModMessages {

    public static void registerC2SPackets() {
        // Register C2S payload types
        PayloadTypeRegistry.serverboundPlay().register(ModPayloads.UpdateNoteBlockPayload.ID, ModPayloads.UpdateNoteBlockPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ModPayloads.AdvancedSettingsPayload.ID, ModPayloads.AdvancedSettingsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ModPayloads.ScanRequestPayload.ID, ModPayloads.ScanRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ModPayloads.BulkUpdatePayload.ID, ModPayloads.BulkUpdatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ModPayloads.SetWandPosPayload.ID, ModPayloads.SetWandPosPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ModPayloads.PreviewRequestPayload.ID, ModPayloads.PreviewRequestPayload.CODEC);

        // ============== Update Note Block ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.UpdateNoteBlockPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                var world = player.level();

                int note = Mth.clamp(payload.note(), 0, 127);
                int velocity = Mth.clamp(payload.velocity(), 0, 127);
                int sustain = Mth.clamp(payload.sustain(), 0, 400);
                int delay = Mth.clamp(payload.delay(), 0, ExtendedNoteBlockEntity.MAX_DELAY_MS);
                int fadeIn = Mth.clamp(payload.fadeIn(), 0, 400);
                int fadeOut = Mth.clamp(payload.fadeOut(), 0, 400);
                int instrumentId = payload.instrumentId();

                if (world.getBlockEntity(payload.pos()) instanceof ExtendedNoteBlockEntity entity) {
                    entity.updateValues(note, velocity, sustain, delay, fadeIn, fadeOut);
                    updateInstrumentBlock(player, world, payload.pos(), instrumentId);
                } else {
                    System.err.println("在位置 " + payload.pos() + " 未找到 ExtendedNoteBlockEntity");
                }
            });
        });

        // ============== Advanced Settings v1.4.0 ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.AdvancedSettingsPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                Level world = player.level();
                if (world.getBlockEntity(payload.pos()) instanceof ExtendedNoteBlockEntity entity) {
                    entity.setVolumePoints(payload.volumePoints());
                    entity.setPitchBendPoints(payload.pitchBendPoints());
                    entity.setSoundPath(payload.soundPath());
                    entity.setStoredExpressionX(payload.storedExprX());
                    entity.setStoredExpressionY(payload.storedExprY());
                    entity.setStoredExpressionZ(payload.storedExprZ());
                }
            });
        });

        // ============== Conductor's Wand: Scan Request ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.ScanRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                handleScanRequest(player, payload.pos1(), payload.pos2());
            });
        });

        // ============== Conductor's Wand: Bulk Update ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.BulkUpdatePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                ServerLevel world = player.level();
                BlockPos min = new BlockPos(
                        Math.min(payload.p1().getX(), payload.p2().getX()),
                        Math.min(payload.p1().getY(), payload.p2().getY()),
                        Math.min(payload.p1().getZ(), payload.p2().getZ()));
                BlockPos max = new BlockPos(
                        Math.max(payload.p1().getX(), payload.p2().getX()),
                        Math.max(payload.p1().getY(), payload.p2().getY()),
                        Math.max(payload.p1().getZ(), payload.p2().getZ()));

                // Parse updatesJson
                List<Triple<String, Integer, String>> updates = new ArrayList<>();
                try {
                    List<Map<String, Object>> updatesList = new Gson().fromJson(
                            payload.updatesJson(),
                            new TypeToken<List<Map<String, Object>>>() {}.getType());
                    for (Map<String, Object> entry : updatesList) {
                        String path = (String) entry.get("path");
                        int mode = ((Number) entry.get("mode")).intValue();
                        String value = (String) entry.get("value");
                        updates.add(new Triple<>(path, mode, value));
                    }
                } catch (Exception e) {
                    System.err.println("解析 updatesJson 失败: " + e.getMessage());
                }

                CompoundTag advancedPatch = payload.hasAdvanced() ? payload.advancedPatch() : null;

                int updatedCount = 0;
                for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                    net.minecraft.world.level.chunk.ChunkAccess chunk = world.getChunk(p.getX() >> 4, p.getZ() >> 4,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
                    BlockEntity be = chunk.getBlockEntity(p);
                    if (be != null) {
                        String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
                        if (id.equals(payload.targetBlockId())) {
                            CompoundTag original = be.saveWithoutMetadata(world.registryAccess());

                            for (Triple<String, Integer, String> entry : updates) {
                                com.atemukesu.extendednoteblock.util.NbtPathUtil.apply(original, entry.getA(),
                                        entry.getC(), entry.getB());
                            }

                            if (advancedPatch != null && !advancedPatch.isEmpty()) {
                                applyNbtPatch(original, advancedPatch, 0);
                            }

                            recalculateSoundPath(original);

                            be.loadWithComponents(TagValueInput.create(
                                    ProblemReporter.DISCARDING, world.registryAccess(), original));
                            be.setChanged();
                            world.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
                            updatedCount++;
                        }
                    }
                }
                player.sendSystemMessage(Component.translatable("gui.extendednoteblock.conductor.update_result", updatedCount));
            });
        });

        // ============== Conductor's Wand: Set Wand Pos ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SetWandPosPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof ConductorWandItem) {
                    CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    if (payload.pointIndex() == 0) {
                        nbt.remove("Pos1");
                        nbt.remove("Pos2");
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                        player.sendOverlayMessage(Component.translatable("gui.extendednoteblock.conductor.selection_cleared"));
                    } else {
                        nbt.store("Pos" + payload.pointIndex(), BlockPos.CODEC, payload.pos());
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                        player.sendOverlayMessage(Component.translatable("gui.extendednoteblock.conductor.pos_set",
                                payload.pointIndex(), payload.pos().toShortString()));
                    }
                }
            });
        });

        // ============== Preview Request ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.PreviewRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                var world = player.level();
                var pos = payload.pos();
                if (world.getBlockState(pos).getBlock() instanceof ExtendedNoteBlockBlock block) {
                    block.previewNote(world, pos);
                }
            });
        });
    }

    public static void registerS2CPackets() {
        PayloadTypeRegistry.clientboundPlay().register(ModPayloads.StartSoundPayload.ID, ModPayloads.StartSoundPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModPayloads.UpdateVolumePayload.ID, ModPayloads.UpdateVolumePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModPayloads.StopSoundPayload.ID, ModPayloads.StopSoundPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModPayloads.SmoothMovePayload.ID, ModPayloads.SmoothMovePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModPayloads.AdvancedUpdatePayload.ID, ModPayloads.AdvancedUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModPayloads.StartAdvancedSoundPayload.ID, ModPayloads.StartAdvancedSoundPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModPayloads.ScanResponsePayload.ID, ModPayloads.ScanResponsePayload.CODEC);
    }

    // Helper class for Triple
    private static class Triple<A, B, C> {
        private final A a;
        private final B b;
        private final C c;

        public Triple(A a, B b, C c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }

        public A getA() {
            return a;
        }

        public B getB() {
            return b;
        }

        public C getC() {
            return c;
        }
    }

    // ============== S2C Send Methods ==============

    public static void sendStartSoundToClients(ServerLevel world, BlockPos pos, UUID soundId, int instrumentId,
            int note, int velocity, float initialVolume) {
        var payload = new ModPayloads.StartSoundPayload(pos, soundId, instrumentId, note, velocity, initialVolume);
        for (ServerPlayer player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendUpdateVolumeToClients(ServerLevel world, BlockPos pos, UUID soundId, float volume) {
        var payload = new ModPayloads.UpdateVolumePayload(soundId, volume);
        for (ServerPlayer player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendStopSoundToClients(ServerLevel world, BlockPos pos, UUID soundId) {
        var payload = new ModPayloads.StopSoundPayload(soundId);
        for (ServerPlayer player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendSmoothMoveToClient(ServerPlayer player, Vec3 pos, boolean isStop) {
        var payload = new ModPayloads.SmoothMovePayload(pos.x, pos.y, pos.z, isStop);
        ServerPlayNetworking.send(player, payload);
    }

    // ============== Advanced Features v1.4.0 ==============
    public static void sendAdvancedUpdateToClients(ServerLevel world, BlockPos pos, UUID soundId, float vol,
            float pitchMul, double x, double y, double z) {
        var payload = new ModPayloads.AdvancedUpdatePayload(soundId, vol, pitchMul, x, y, z);
        for (ServerPlayer player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendStartAdvancedSoundToClients(ServerLevel world, BlockPos pos, UUID soundId,
            int instrumentId, int note,
            float initialVolume, float initialPitchMul,
            double x, double y, double z) {
        var payload = new ModPayloads.StartAdvancedSoundPayload(pos, soundId, instrumentId, note,
                initialVolume, initialPitchMul, x, y, z);
        for (ServerPlayer player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    // ============== Conductor's Wand Methods ==============
    public static void sendScanRequest(ServerPlayer player, BlockPos pos1, BlockPos pos2) {
        handleScanRequest(player, pos1, pos2);
    }

    // ============== Internal Handler Methods ==============

    private static void handleScanRequest(ServerPlayer player, BlockPos pos1, BlockPos pos2) {
        ServerLevel world = player.level();
        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        if (volume > Integer.MAX_VALUE) {
            player.sendSystemMessage(Component.translatable("gui.extendednoteblock.conductor.selection_too_large", volume));
            return;
        }

        player.sendOverlayMessage(Component.translatable("gui.extendednoteblock.conductor.scanning_area"));

        java.util.Map<String, Integer> countMap = new java.util.HashMap<>();
        java.util.Map<String, CompoundTag> sampleNbtMap = new java.util.HashMap<>();

        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            net.minecraft.world.level.chunk.ChunkAccess chunk = world.getChunk(p.getX() >> 4, p.getZ() >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
            BlockEntity be = chunk.getBlockEntity(p);

            if (be != null) {
                String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
                countMap.put(id, countMap.getOrDefault(id, 0) + 1);

                if (!sampleNbtMap.containsKey(id)) {
                    sampleNbtMap.put(id, be.saveWithoutMetadata(world.registryAccess()));
                }
            }
        }

        if (countMap.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gui.extendednoteblock.conductor.no_entities_found"));
            return;
        }

        // Send scan response using CustomPayload
        var payload = new ModPayloads.ScanResponsePayload(min, max, countMap, sampleNbtMap);
        ServerPlayNetworking.send(player, payload);
    }

    private static void updateInstrumentBlock(ServerPlayer player, Level world, BlockPos noteBlockPos,
            int instrumentId) {
        BlockPos belowPos = noteBlockPos.below();
        String targetBlockId = InstrumentMap.GM_INSTRUMENT_TO_BLOCK.get(instrumentId);

        if (targetBlockId != null) {
            try {
                Block targetBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse(targetBlockId));
                Block currentBlockBelow = world.getBlockState(belowPos).getBlock();

                if (targetBlock != null && targetBlock != currentBlockBelow) {
                    if (world.mayInteract(player, belowPos)) {
                        world.setBlock(belowPos, targetBlock.defaultBlockState(), 3);
                    } else {
                        player.sendOverlayMessage(Component.translatable("gui.extendednoteblock.error.no_permission"));
                    }
                }
            } catch (Exception e) {
                System.err.println("更换方块时出错: " + e.getMessage());
            }
        }
    }

    private static void applyNbtPatch(CompoundTag original, CompoundTag patch, int op) {
        for (String key : patch.keySet()) {
            if (key.equals("AdvancedData") && patch.get("AdvancedData") instanceof CompoundTag) {
                if (!(original.get("AdvancedData") instanceof CompoundTag))
                    original.put("AdvancedData", new CompoundTag());
                applyNbtPatch(original.getCompoundOrEmpty("AdvancedData"), patch.getCompoundOrEmpty("AdvancedData"), op);
                continue;
            }

            if (op != 0 && original.get(key) instanceof net.minecraft.nbt.NumericTag
                    && patch.get(key) instanceof net.minecraft.nbt.NumericTag) {
                net.minecraft.nbt.Tag originalElement = original.get(key);
                net.minecraft.nbt.Tag patchElement = patch.get(key);

                if (originalElement instanceof net.minecraft.nbt.NumericTag origNum &&
                        patchElement instanceof net.minecraft.nbt.NumericTag patchNum) {
                    double oldVal = origNum.doubleValue();
                    double patchVal = patchNum.doubleValue();
                    double newVal = oldVal;

                    if (op == 1)
                        newVal += patchVal;
                    else if (op == 2)
                        newVal *= patchVal;
                    else if (op == 3)
                        newVal /= (patchVal == 0 ? 1 : patchVal);
                    else if (op == 4)
                        newVal -= patchVal;

                    if (originalElement instanceof net.minecraft.nbt.IntTag)
                        original.putInt(key, (int) newVal);
                    else if (originalElement instanceof net.minecraft.nbt.FloatTag)
                        original.putFloat(key, (float) newVal);
                    else if (originalElement instanceof net.minecraft.nbt.DoubleTag)
                        original.putDouble(key, newVal);
                    else if (originalElement instanceof net.minecraft.nbt.ShortTag)
                        original.putShort(key, (short) newVal);
                    else if (originalElement instanceof net.minecraft.nbt.ByteTag)
                        original.putByte(key, (byte) newVal);
                    else if (originalElement instanceof net.minecraft.nbt.LongTag)
                        original.putLong(key, (long) newVal);
                    else
                        original.putInt(key, (int) newVal);
                }
            } else {
                original.put(key, patch.get(key));
            }
        }
    }

    private static void recalculateSoundPath(CompoundTag nbt) {
        if (!(nbt.get("AdvancedData") instanceof CompoundTag))
            return;
        CompoundTag adv = nbt.getCompoundOrEmpty("AdvancedData");

        String ex = adv.getStringOr("ExpressionX", "");
        String ey = adv.getStringOr("ExpressionY", "");
        String ez = adv.getStringOr("ExpressionZ", "");

        if (ex.isEmpty() && ey.isEmpty() && ez.isEmpty())
            return;

        int sustain = nbt.getIntOr("sustainTime", 0);
        if (sustain <= 0)
            sustain = 40;

        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        try {
            net.objecthunter.exp4j.Expression eX = new net.objecthunter.exp4j.ExpressionBuilder(ex.isEmpty() ? "0" : ex)
                    .variables("t", "d").build();
            net.objecthunter.exp4j.Expression eY = new net.objecthunter.exp4j.ExpressionBuilder(ey.isEmpty() ? "0" : ey)
                    .variables("t", "d").build();
            net.objecthunter.exp4j.Expression eZ = new net.objecthunter.exp4j.ExpressionBuilder(ez.isEmpty() ? "0" : ez)
                    .variables("t", "d").build();

            for (int i = 0; i < sustain; i++) {
                double t = (double) i / Math.max(1, sustain);
                CompoundTag pos = new CompoundTag();
                pos.putDouble("x", eX.setVariable("t", t).setVariable("d", i).evaluate());
                pos.putDouble("y", eY.setVariable("t", t).setVariable("d", i).evaluate());
                pos.putDouble("z", eZ.setVariable("t", t).setVariable("d", i).evaluate());
                list.add(pos);
            }
            adv.put("SoundPath", list);
        } catch (Exception e) {
            // If failed, keep old path
        }
    }
}
