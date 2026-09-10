package com.atemukesu.extendednoteblock.bridgeclient.litematica.mixin;

import com.atemukesu.extendednoteblock.bridgeclient.SchematicTransferClient;
import com.atemukesu.extendednoteblock.bridgeclient.litematica.LitematicaPlacementData;
import fi.dy.masa.litematica.scheduler.tasks.TaskPasteSchematicPerChunkBase;
import fi.dy.masa.litematica.scheduler.tasks.TaskPasteSchematicPerChunkCommand;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.util.position.LayerRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;

@Mixin(value = TaskPasteSchematicPerChunkCommand.class, remap = false)
public abstract class PasteSchematicMixin extends TaskPasteSchematicPerChunkBase {
    protected PasteSchematicMixin(Collection<SchematicPlacement> placements, LayerRange range, boolean changed) { super(placements, range, changed); }
    @org.spongepowered.asm.mixin.Unique private boolean enb$rejected;
    @Override public boolean canExecute() { return !enb$rejected && super.canExecute(); }
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void enb$validateMode(CallbackInfo callback) {
        if (ignoreBlocks) return;
        try {
            if (!LitematicaPlacementData.collect(placements, layerRange).entries().isEmpty()
                    && (changedBlockOnly || replace != fi.dy.masa.litematica.util.ReplaceBehavior.ALL)) {
                throw new IllegalArgumentException("ENB requires Replace All with changed-block-only disabled; paste cancelled before sending commands.");
            }
        } catch (Exception invalid) {
            enb$rejected = true; callback.cancel(); SchematicTransferClient.message("ENB: " + invalid.getMessage());
        }
    }
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void enb$skipRejected(net.minecraft.util.profiling.ProfilerFiller profiler,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callback) {
        if (enb$rejected) callback.setReturnValue(true);
    }
    @Inject(method = "onStop", at = @At("TAIL"))
    private void enb$pasteMetadata(CallbackInfo callback) {
        if (!finished || ignoreBlocks || enb$rejected) return;
        try { SchematicTransferClient.paste(LitematicaPlacementData.collect(placements, layerRange)); }
        catch (Exception invalid) { SchematicTransferClient.message("ENB: schematic metadata paste failed: " + invalid.getMessage()); }
    }
}
