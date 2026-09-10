package com.atemukesu.extendednoteblock.bridgeclient.litematica.mixin;

import com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicDataHolder;
import com.atemukesu.extendednoteblock.bridgeclient.litematica.SchematicMetadata;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LitematicaSchematic.class, remap = false)
public abstract class SchematicMixin implements SchematicDataHolder {
    @Unique private CompoundData enb$data;
    public CompoundData enb$getData() { if (enb$data == null) enb$data = new CompoundData(); return enb$data; }
    public void enb$setData(CompoundData data) { enb$data = data.copy(); }
    @Inject(method = "readFromData", at = @At("RETURN"))
    private void enb$read(CompoundData root, CallbackInfoReturnable<Boolean> callback) {
        enb$data = new CompoundData();
        if (callback.getReturnValue()) {
            for (String key : new String[] { SchematicMetadata.KEY, "ExtendedNoteBlockBridge" }) {
                root.getData(key).ifPresent(value -> enb$data.put(key, value.copy()));
            }
        }
    }
    @Inject(method = {"writeToData", "writeToData_v6"}, at = @At("RETURN"))
    private void enb$write(CallbackInfoReturnable<CompoundData> callback) {
        for (String key : enb$getData().getKeys()) callback.getReturnValue().put(key, enb$data.getData(key).orElseThrow().copy());
    }
}
