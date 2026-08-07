package com.atemukesu.extendednoteblock.block;

import com.atemukesu.extendednoteblock.block.entity.NbsProjectionReceiverBlockEntity;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionPlaybackManager;
import com.atemukesu.extendednoteblock.util.RedstoneManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public final class NbsProjectionReceiverBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public NbsProjectionReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    public void setProjectionPowered(ServerLevel level, BlockPos receiverPos, BlockPos transmitterPos,
            boolean powered) {
        BlockState state = level.getBlockState(receiverPos);
        if (!(state.getBlock() instanceof NbsProjectionReceiverBlock)) {
            return;
        }
        boolean wasPowered = state.getValue(POWERED);
        if (wasPowered == powered) {
            return;
        }
        level.setBlock(receiverPos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        if (powered && level.getBlockEntity(receiverPos) instanceof NbsProjectionReceiverBlockEntity receiver) {
            NbsProjectionPlaybackManager.start(level, receiverPos, transmitterPos, receiver.getNotes());
        } else if (!powered) {
            NbsProjectionPlaybackManager.stop(level, receiverPos);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean notify) {
        if (level instanceof ServerLevel serverLevel) {
            for (Direction direction : Direction.values()) {
                BlockPos transmitterPos = pos.relative(direction);
                BlockState transmitter = level.getBlockState(transmitterPos);
                if (transmitter.getBlock() instanceof TransmitterBlock
                        && transmitter.getValue(TransmitterBlock.POWERED)) {
                    RedstoneManager.removeTransmitter(serverLevel, transmitterPos);
                    setProjectionPowered(serverLevel, pos, transmitterPos, true);
                    break;
                }
            }
        }
        super.onPlace(state, level, pos, oldState, notify);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        NbsProjectionPlaybackManager.stop(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NbsProjectionReceiverBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public MapCodec<NbsProjectionReceiverBlock> codec() {
        return simpleCodec(NbsProjectionReceiverBlock::new);
    }
}
