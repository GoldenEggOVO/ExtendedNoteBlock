package com.atemukesu.extendednoteblock.screen;

import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class ExtendedNoteBlockScreenHandler extends AbstractContainerMenu {
    public final ExtendedNoteBlockEntity blockEntity;
    private final ContainerData propertyDelegate;
    public final BlockPos blockPos;

    public static final StreamCodec<RegistryFriendlyByteBuf, FriendlyByteBuf> PACKET_CODEC = StreamCodec.ofMember(
            ExtendedNoteBlockScreenHandler::writeToBuf,
            buf -> new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))
    );

    private static void writeToBuf(FriendlyByteBuf data, RegistryFriendlyByteBuf buf) {
        buf.writeBytes(data.readBytes(data.readableBytes()));
    }

    public ExtendedNoteBlockScreenHandler(int syncId, Inventory inventory, ExtendedNoteBlockEntity entity,
                                          ContainerData delegate) {
        super(ModScreenHandlers.EXTENDED_NOTE_BLOCK_SCREEN_HANDLER, syncId);
        this.blockEntity = entity;
        this.propertyDelegate = delegate;
        this.blockPos = entity.getBlockPos();
        addDataSlots(delegate);
    }

    public ExtendedNoteBlockScreenHandler(int syncId, Inventory inventory, FriendlyByteBuf buf) {
        this(syncId, inventory,
                (ExtendedNoteBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(7));
        this.propertyDelegate.set(0, buf.readInt()); // note
        this.propertyDelegate.set(1, buf.readInt()); // velocity
        this.propertyDelegate.set(2, buf.readInt()); // sustain
        this.propertyDelegate.set(3, buf.readInt()); // delayedPlayingTime
        this.propertyDelegate.set(4, buf.readInt()); // fadeInTime
        this.propertyDelegate.set(5, buf.readInt()); // fadeOutTime
        this.propertyDelegate.set(6, buf.readInt()); // instrumentId

        // ============== Advanced Features v1.4.0 ==============
        // Read advanced settings data from buffer
        // 读取弯音关键点
        int pitchBendPointsSize = buf.readInt();
        List<CurvePoint> pitchBendPoints = new ArrayList<>();
        for (int i = 0; i < pitchBendPointsSize; i++) {
            float t = buf.readFloat();
            float v = buf.readFloat();
            pitchBendPoints.add(new CurvePoint(t, v));
        }

        // 读取音量关键点
        int volumePointsSize = buf.readInt();
        List<CurvePoint> volumePoints = new ArrayList<>();
        for (int i = 0; i < volumePointsSize; i++) {
            float t = buf.readFloat();
            float v = buf.readFloat();
            volumePoints.add(new CurvePoint(t, v));
        }

        int soundPathSize = buf.readInt();
        List<Vec3> soundPath = new ArrayList<>();
        for (int i = 0; i < soundPathSize; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            soundPath.add(new Vec3(x, y, z));
        }

        String storedExpressionX = buf.readUtf();
        String storedExpressionY = buf.readUtf();
        String storedExpressionZ = buf.readUtf();

        // 在主线程中设置这些值，以确保它们在GUI打开时可用
        // 在客户端，直接设置值（客户端方块实体是只读副本）
        // 在服务器端，也直接设置值（已在正确的线程中）
        this.blockEntity.setPitchBendPoints(pitchBendPoints);
        this.blockEntity.setVolumePoints(volumePoints);
        this.blockEntity.setSoundPath(soundPath);
        this.blockEntity.setStoredExpressionX(storedExpressionX);
        this.blockEntity.setStoredExpressionY(storedExpressionY);
        this.blockEntity.setStoredExpressionZ(storedExpressionZ);
    }

    public int getNote() {
        return this.propertyDelegate.get(0);
    }

    public int getVelocity() {
        return this.propertyDelegate.get(1);
    }

    public int getSustain() {
        return this.propertyDelegate.get(2);
    }

    public int getDelayedPlayingTime() {
        return this.propertyDelegate.get(3);
    }

    public int getFadeInTime() {
        return this.propertyDelegate.get(4);
    }

    public int getFadeOutTime() {
        return this.propertyDelegate.get(5);
    }

    public int getInstrumentId() {
        return this.propertyDelegate.get(6);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.blockEntity.getLevel().getBlockEntity(this.blockPos) == this.blockEntity &&
                player.distanceToSqr(this.blockPos.getCenter()) < 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // previewNote 已由 PreviewRequestPayload 处理，此处不再重复触发
    }
}