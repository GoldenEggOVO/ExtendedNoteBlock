package com.atemukesu.extendednoteblock.bridgeclient.litematica.mixin;

import com.atemukesu.extendednoteblock.bridgeclient.SchematicTransferClient;
import com.atemukesu.extendednoteblock.bridgeclient.litematica.*;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.Document;
import com.google.common.collect.ImmutableMap;
import fi.dy.masa.litematica.scheduler.tasks.TaskProcessChunkBase;
import fi.dy.masa.litematica.scheduler.tasks.TaskSaveSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(value = TaskSaveSchematic.class, remap = false)
public abstract class SaveSchematicMixin extends TaskProcessChunkBase {
    @Shadow @Final private LitematicaSchematic schematic;
    @Shadow @Final private ImmutableMap<String, Box> subRegions;
    @Shadow @Final protected boolean fromSchematicWorld;
    @Unique private boolean enb$requested, enb$ready, enb$failed;
    protected SaveSchematicMixin() { super("enb"); }
    @Override public boolean canExecute() { return !enb$failed && super.canExecute(); }
    @Inject(method = "canProcessChunk", at = @At("HEAD"), cancellable = true)
    private void enb$awaitSnapshot(ChunkPos chunk, CallbackInfoReturnable<Boolean> callback) {
        if (fromSchematicWorld || !SchematicTransferClient.available()) return;
        if (!enb$requested) {
            enb$requested = true;
            try {
                Map<String, SchematicTransfer.Box> boxes = new LinkedHashMap<>();
                for (var region : subRegions.entrySet()) {
                    BlockPos a = region.getValue().getPos1(), b = region.getValue().getPos2();
                    boxes.put(region.getKey(), new SchematicTransfer.Box(
                            new Pos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                            new Pos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))));
                }
                SchematicTransferClient.snapshot(this, java.util.List.copyOf(boxes.values()), document -> {
                    try {
                        Map<String, Document> regions = new LinkedHashMap<>();
                        for (var region : boxes.entrySet()) {
                            BlockPos origin = subRegions.get(region.getKey()).getPos1();
                            regions.put(region.getKey(), new Document(document.entries().stream().filter(entry -> region.getValue().contains(entry.pos()))
                                    .map(entry -> entry.at(new Pos(entry.pos().x() - origin.getX(), entry.pos().y() - origin.getY(), entry.pos().z() - origin.getZ()))).toList()));
                        }
                        CompoundData data = new CompoundData(); SchematicMetadata.write(data, regions);
                        ((SchematicDataHolder) schematic).enb$setData(data); enb$ready = true;
                    } catch (RuntimeException invalid) { enb$fail(invalid.getMessage()); }
                }, this::enb$fail);
            } catch (RuntimeException invalid) { enb$fail(invalid.getMessage()); }
        }
        if (!enb$ready) callback.setReturnValue(false);
    }
    @Inject(method = "onStop", at = @At("HEAD"))
    private void enb$normalize(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        SchematicTransferClient.cancel(this);
        if (!finished || !enb$ready) return;
        try { SchematicCarriers.normalize(schematic); }
        catch (Exception invalid) { finished = false; enb$fail(invalid.getMessage()); }
    }
    @Unique private void enb$fail(String reason) {
        enb$failed = true; SchematicTransferClient.message("ENB: schematic save cancelled: " + reason);
    }
}
