package com.atemukesu.extendednoteblock.bridgeclient;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Paper-safe counterpart of the Full Fabric SoundSystemMixin.
 *
 * Minecraft normally clamps the final sound pitch in SoundEngine. ExtendedNoteBlock
 * intentionally uses pitch factors outside that vanilla range so one sampled note
 * can cover the complete MIDI range. The Full Fabric client already bypasses that
 * clamp for the extendednoteblock namespace; the Paper companion must do the same
 * or low notes collapse to the same audible pitch.
 *
 * This mixin deliberately depends only on vanilla/Fabric classes and a literal
 * namespace. It must never reference the Full mod initializer or custom registries.
 */
@Mixin(SoundEngine.class)
public abstract class BridgeSoundEngineMixin {
    @ModifyVariable(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At(value = "STORE"),
            ordinal = 1)
    private float extendedNoteBlock$useFullAttenuationDistance(float attenuationDistance,
            SoundInstance soundInstance) {
        if (soundInstance.getAttenuation() == SoundInstance.Attenuation.LINEAR) {
            return 48.0F;
        }
        return attenuationDistance;
    }

    @Inject(
            method = "calculatePitch(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("HEAD"),
            cancellable = true)
    private void extendedNoteBlock$allowFullPitchRange(SoundInstance sound,
            CallbackInfoReturnable<Float> cir) {
        if ("extendednoteblock".equals(sound.getIdentifier().getNamespace())) {
            cir.setReturnValue(sound.getPitch());
        }
    }
}
