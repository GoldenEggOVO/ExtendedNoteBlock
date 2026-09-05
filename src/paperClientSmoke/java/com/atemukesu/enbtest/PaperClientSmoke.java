package com.atemukesu.enbtest;

import com.atemukesu.extendednoteblock.bridgeclient.BridgeClientPayloads;
import com.atemukesu.extendednoteblock.bridgeclient.BridgeNoteBlockScreen;
import com.atemukesu.extendednoteblock.sound.StoppablePositionalSoundInstance;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Runs only in CI's separate test mod; never included in a release artifact. */
public final class PaperClientSmoke implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                if (FabricLoader.getInstance().isModLoaded("extendednoteblock")) {
                    throw new AssertionError("Full mod leaked into the Paper test environment");
                }
                if (BuiltInRegistries.BLOCK.keySet().stream().anyMatch(id -> id.getNamespace().equals("extendednoteblock"))
                        || BuiltInRegistries.ITEM.keySet().stream().anyMatch(id -> id.getNamespace().equals("extendednoteblock"))) {
                    throw new AssertionError("Custom ENB registry IDs exist in Paper Client");
                }

                Object manager = client.getSoundManager();
                Field engineField = Arrays.stream(manager.getClass().getDeclaredFields())
                        .filter(field -> field.getType() == SoundEngine.class).findFirst().orElseThrow();
                engineField.setAccessible(true);
                Object engine = engineField.get(manager);
                Method calculatePitch = SoundEngine.class.getDeclaredMethod("calculatePitch", SoundInstance.class);
                calculatePitch.setAccessible(true);
                for (int note = 0; note <= 127; note++) {
                    float pitch = (float) Math.pow(2, (note - 60) / 12.0);
                    assertFloat(pitch, (float) calculatePitch.invoke(engine, sound("extendednoteblock", pitch)));
                    assertFloat(Math.max(.5f, Math.min(2f, pitch)),
                            (float) calculatePitch.invoke(engine, sound("minecraft", pitch)));
                }
                Method attenuation = Arrays.stream(SoundEngine.class.getDeclaredMethods())
                        .filter(method -> method.getName().contains("useFullAttenuationDistance"))
                        .findFirst().orElseThrow();
                attenuation.setAccessible(true);
                assertFloat(48, (float) attenuation.invoke(engine, 16f, sound("extendednoteblock", 1)));
                assertFloat(16, (float) attenuation.invoke(engine, 16f, sound("minecraft", 1)));

                client.gui.setScreen(new BridgeNoteBlockScreen(null,
                        new BridgeClientPayloads.NoteEditPayload(BlockPos.ZERO, 60, 40, 100, 20, 0, 0, 3)));
                if (!(client.gui.screen() instanceof BridgeNoteBlockScreen)) {
                    throw new AssertionError("Paper note editor did not open");
                }
                System.out.println("ENB_PAPER_CLIENT_SMOKE_OK: startup, GUI, vanilla registries, MIDI 0-127 pitch and attenuation");
                System.exit(0);
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static SoundInstance sound(String namespace, float pitch) {
        return new StoppablePositionalSoundInstance(SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(namespace, "smoke_test")), SoundSource.RECORDS, 1, pitch, BlockPos.ZERO);
    }

    private static void assertFloat(float expected, float actual) {
        if (Math.abs(expected - actual) > 0.00001f) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
