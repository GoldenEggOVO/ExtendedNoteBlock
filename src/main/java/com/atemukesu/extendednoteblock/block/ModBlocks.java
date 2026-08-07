package com.atemukesu.extendednoteblock.block;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

public class ModBlocks {

    public static final Block EXTENDED_NOTE_BLOCK = registerBlock("extended_note_block",
            ExtendedNoteBlockBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.NOTE_BLOCK)
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) ? 15 : 0));

    public static final Block GLOBAL_REDSTONE_TRANSMITTER = registerBlock("global_redstone_transmitter",
            TransmitterBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(1.5f));

    public static final Block GLOBAL_REDSTONE_RECEIVER = registerBlock("global_redstone_receiver",
            ReceiverBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(1.5f)
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) ? 15 : 0));

    public static final Block NBS_PROJECTION_RECEIVER = registerBlock("nbs_projection_receiver",
            NbsProjectionReceiverBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(1.5f)
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) ? 15 : 0));

    /**
     * 辅助方法，用于注册方块
     *
     * @param name  方块的名称
     * @param block 方块实例
     * @return 注册后的方块
     */
    private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory,
            BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(ExtendedNoteBlock.MOD_ID, name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        Block block = factory.apply(properties.setId(blockKey));
        registerBlockItem(name, block);
        return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
    }

    /**
     * 辅助方法，用于注册方块对应的物品
     *
     * @param name  物品的名称
     * @param block 对应的方块
     */
    private static void registerBlockItem(String name, Block block) {
        Identifier id = Identifier.fromNamespaceAndPath(ExtendedNoteBlock.MOD_ID, name);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
    }

    /**
     * 在主类中调用的初始化方法
     */
    public static void registerModBlocks() {
        ExtendedNoteBlock.LOGGER.info("Registering ModBlocks for " + ExtendedNoteBlock.MOD_ID);
    }
}
