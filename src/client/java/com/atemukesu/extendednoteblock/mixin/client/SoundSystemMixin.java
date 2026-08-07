package com.atemukesu.extendednoteblock.mixin.client;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundSystemMixin {
    @ModifyVariable(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At(value = "STORE"),
            ordinal = 1)
    private float modifyAttenuationDistance(float attenuationDistance, SoundInstance soundInstance) {
        if (soundInstance.getAttenuation() == SoundInstance.Attenuation.LINEAR) {
            return 48.0F;
        }
        return attenuationDistance;
    }

    @Inject(
            method = "calculatePitch(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("HEAD"),
            cancellable = true)
    private void extendedNoteBlock$getAdjustedPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (ExtendedNoteBlock.MOD_ID.equals(sound.getIdentifier().getNamespace())) {
            cir.setReturnValue(sound.getPitch());
        }
    }
}
