package com.atemukesu.extendednoteblock.mixin.client;

import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEvent.class)
public abstract class SoundEventMixin {
    @Inject(method = "getRange(F)F", at = @At("HEAD"), cancellable = true)
    private void onGetDistanceToTravel(float volume, CallbackInfoReturnable<Float> cir) {
        SoundEvent soundEvent = (SoundEvent) (Object) this;
        if (soundEvent.fixedRange().isEmpty()) {
            cir.setReturnValue(48.0F);
        }
    }
}
