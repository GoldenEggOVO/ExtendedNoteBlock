package com.atemukesu.extendednoteblock.block;

import com.atemukesu.extendednoteblock.util.RedstoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

public class TransmitterBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public TransmitterBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, Orientation orientation,
            boolean notify) {
        if (world.isClientSide())
            return;

        boolean isBeingPowered = world.hasNeighborSignal(pos);
        boolean wasPowered = state.getValue(POWERED);

        if (isBeingPowered != wasPowered) {
            world.setBlock(pos, state.setValue(POWERED, isBeingPowered), 3);
            if (!routeToProjectionReceiver(world, pos, isBeingPowered)) {
                RedstoneManager.transmitterChanged(world, pos, isBeingPowered);
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClientSide()) {
            boolean isBeingPowered = world.hasNeighborSignal(pos);
            if (isBeingPowered != state.getValue(POWERED)) {
                world.setBlock(pos, state.setValue(POWERED, isBeingPowered), 3);
            }
            if (!routeToProjectionReceiver(world, pos, isBeingPowered)) {
                RedstoneManager.addTransmitter(world, pos);
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        boolean projection = routeToProjectionReceiver(world, pos, false);
        if (state.getValue(POWERED) && !projection) {
            RedstoneManager.transmitterChanged(world, pos, false);
        }
        RedstoneManager.removeTransmitter(world, pos);
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }

    public static boolean hasProjectionReceiver(Level world, BlockPos transmitterPos) {
        return findProjectionReceiver(world, transmitterPos) != null;
    }

    private static boolean routeToProjectionReceiver(Level world, BlockPos transmitterPos, boolean powered) {
        BlockPos receiverPos = findProjectionReceiver(world, transmitterPos);
        if (receiverPos == null || !(world instanceof ServerLevel serverLevel)) {
            return false;
        }
        RedstoneManager.removeTransmitter(serverLevel, transmitterPos);
        if (world.getBlockState(receiverPos).getBlock() instanceof NbsProjectionReceiverBlock receiver) {
            receiver.setProjectionPowered(serverLevel, receiverPos, transmitterPos, powered);
        }
        return true;
    }

    private static BlockPos findProjectionReceiver(Level world, BlockPos transmitterPos) {
        for (Direction direction : Direction.values()) {
            BlockPos candidate = transmitterPos.relative(direction);
            if (world.getBlockState(candidate).getBlock() instanceof NbsProjectionReceiverBlock) {
                return candidate;
            }
        }
        return null;
    }
}
