package com.atemukesu.extendednoteblock.bridgeclient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Position-aware model replacement for Paper/Purpur vanilla carrier blocks.
 *
 * The world still contains only minecraft:* blocks. During chunk model baking we
 * wrap only the five possible carrier block models. At render time the wrapper
 * checks the server-synchronized BlockPos cache: ordinary vanilla coordinates
 * delegate untouched, while managed coordinates emit the original Full Fabric
 * ExtendedNoteBlock model.
 */
public final class BridgeBlockRenderModels {
    private static final String[] NOTE_PITCHES = {
            "c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b"
    };

    private static final String RED_CONCRETE = "minecraft:red_concrete";
    private static final String GREEN_CONCRETE = "minecraft:green_concrete";
    private static final String PURPLE_CONCRETE = "minecraft:purple_concrete";

    private static final Map<String, ExtraModelKey<BlockStateModel>> EXTRA_MODELS = new LinkedHashMap<>();
    private static boolean registered;

    static {
        for (String pitch : NOTE_PITCHES) {
            addExtra(pitch);
            addExtra(pitch + "_on");
        }
        addExtra("global_redstone_transmitter");
        addExtra("global_redstone_transmitter_on");
        addExtra("global_redstone_receiver");
        addExtra("global_redstone_receiver_on");
        addExtra("nbs_projection_receiver");
        addExtra("nbs_projection_receiver_on");
    }

    private BridgeBlockRenderModels() {
    }

    private static void addExtra(String path) {
        EXTRA_MODELS.put(path, ExtraModelKey.create(() -> "extendednoteblock:block/" + path));
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        ModelLoadingPlugin.register(context -> {
            for (Map.Entry<String, ExtraModelKey<BlockStateModel>> entry : EXTRA_MODELS.entrySet()) {
                Identifier id = Identifier.fromNamespaceAndPath("extendednoteblock", "block/" + entry.getKey());
                context.addModel(entry.getValue(), SimpleUnbakedExtraModel.blockStateModel(id));
            }

            context.modifyBlockModelAfterBake().register((model, modifierContext) -> {
                Block block = modifierContext.state().getBlock();
                String id = blockId(block);
                if (block == Blocks.NOTE_BLOCK
                        || RED_CONCRETE.equals(id)
                        || GREEN_CONCRETE.equals(id)
                        || PURPLE_CONCRETE.equals(id)
                        || block == Blocks.REDSTONE_BLOCK) {
                    return new CarrierModel(model);
                }
                return model;
            });
        });
    }

    private static BlockStateModel replacementFor(BlockPos pos, BlockState state) {
        BridgeWorldObjects.Entry entry = BridgeWorldObjects.get(pos);
        if (entry == null || !carrierMatches(entry.typeId(), state)) return null;

        String path = switch (entry.typeId()) {
            case BridgeWorldObjects.TYPE_EXTENDED_NOTE_BLOCK ->
                    NOTE_PITCHES[Math.floorMod(entry.variant(), NOTE_PITCHES.length)]
                            + (entry.powered() ? "_on" : "");
            case BridgeWorldObjects.TYPE_TRANSMITTER ->
                    "global_redstone_transmitter" + (entry.powered() ? "_on" : "");
            case BridgeWorldObjects.TYPE_RECEIVER ->
                    "global_redstone_receiver" + (entry.powered() ? "_on" : "");
            case BridgeWorldObjects.TYPE_PROJECTION_RECEIVER ->
                    "nbs_projection_receiver" + (entry.powered() ? "_on" : "");
            default -> null;
        };
        if (path == null) return null;

        ExtraModelKey<BlockStateModel> key = EXTRA_MODELS.get(path);
        if (key == null) return null;
        FabricModelManager manager = (FabricModelManager) (Object) Minecraft.getInstance().getModelManager();
        return manager.getModel(key);
    }

    private static boolean carrierMatches(int typeId, BlockState state) {
        String id = blockId(state.getBlock());
        return switch (typeId) {
            case BridgeWorldObjects.TYPE_EXTENDED_NOTE_BLOCK -> state.is(Blocks.NOTE_BLOCK);
            case BridgeWorldObjects.TYPE_TRANSMITTER -> RED_CONCRETE.equals(id);
            case BridgeWorldObjects.TYPE_RECEIVER -> GREEN_CONCRETE.equals(id) || state.is(Blocks.REDSTONE_BLOCK);
            case BridgeWorldObjects.TYPE_PROJECTION_RECEIVER -> PURPLE_CONCRETE.equals(id);
            default -> false;
        };
    }

    private static String blockId(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "" : id.toString();
    }

    private static final class CarrierModel extends WrapperBlockStateModel {
        private CarrierModel(BlockStateModel wrapped) {
            super(wrapped);
        }

        @Override
        public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                              RandomSource random, Predicate<Direction> cullTest) {
            BlockStateModel replacement = replacementFor(pos, state);
            if (replacement == null) {
                super.emitQuads(emitter, level, pos, state, random, cullTest);
                return;
            }
            ((FabricBlockStateModel) (Object) replacement)
                    .emitQuads(emitter, level, pos, state, random, cullTest);
        }

        @Override
        public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            BridgeWorldObjects.Entry entry = BridgeWorldObjects.get(pos);
            if (entry == null || !carrierMatches(entry.typeId(), state)) {
                return super.createGeometryKey(level, pos, state, random);
            }
            return new BridgeGeometryKey(entry.typeId(), entry.powered(), entry.variant());
        }
    }

    private record BridgeGeometryKey(int typeId, boolean powered, int variant) {
    }
}
