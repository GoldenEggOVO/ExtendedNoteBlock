package com.atemukesu.extendednoteblock.block.entity;

import com.atemukesu.extendednoteblock.nbs.NbsProjectionPlaybackManager;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionPlaybackManager.ProjectionNote;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class NbsProjectionReceiverBlockEntity extends BlockEntity {
    private List<ProjectionNote> notes = List.of();

    public NbsProjectionReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NBS_PROJECTION_RECEIVER_BLOCK_ENTITY, pos, state);
    }

    public List<ProjectionNote> getNotes() {
        return notes;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        output.store("ProjectionData", CompoundTag.CODEC, encode(notes));
        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        notes = decode(input.read("ProjectionData", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    private static CompoundTag encode(List<ProjectionNote> notes) {
        int[] packed = new int[notes.size()];
        int[] pitchCents = new int[notes.size()];
        long[] delays = new long[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            ProjectionNote note = notes.get(i);
            packed[i] = (note.instrumentId() & 0xFF)
                    | ((note.midiNote() & 0x7F) << 8)
                    | ((note.velocity() & 0x7F) << 15)
                    | ((note.sustainTicks() & 0x1FF) << 22);
            pitchCents[i] = note.pitchCents();
            delays[i] = note.delayMs();
        }
        CompoundTag data = new CompoundTag();
        data.putIntArray("Notes", packed);
        data.putIntArray("PitchCents", pitchCents);
        data.putLongArray("Delays", delays);
        return data;
    }

    private static List<ProjectionNote> decode(CompoundTag data) {
        int[] packed = data.getIntArray("Notes").orElseGet(() -> new int[0]);
        int[] pitchCents = data.getIntArray("PitchCents").orElseGet(() -> new int[0]);
        long[] delays = data.getLongArray("Delays").orElseGet(() -> new long[0]);
        int count = Math.min(packed.length, delays.length);
        List<ProjectionNote> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int value = packed[i];
            decoded.add(new ProjectionNote(
                    value & 0xFF,
                    (value >>> 8) & 0x7F,
                    (value >>> 15) & 0x7F,
                    (value >>> 22) & 0x1FF,
                    i < pitchCents.length ? pitchCents[i] : 0,
                    Math.max(0L, delays[i])));
        }
        return List.copyOf(decoded);
    }
}
