package com.atemukesu.extendednoteblock.bridgeclient;

import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.sound.ClientSoundManager;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only companion for Paper/Purpur servers running ExtendedNoteBlockBridge.
 *
 * IMPORTANT: this initializer intentionally contains no custom block, item,
 * block-entity, menu or server lifecycle registration. The bridge can therefore
 * be used against a vanilla-registry Paper/Purpur server.
 */
public final class PaperBridgeClient implements ClientModInitializer {
    public static final String MOD_ID = "extendednoteblock_bridge_client";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ConfigManager.initialize();

        SoundPackManager packs = SoundPackManager.getInstance();
        packs.scanPacks();
        if (packs.getActivePackInfo() == null) {
            packs.setActivePack(SoundPackManager.DEFAULT_PACK_ZIP_NAME);
        }

        SoundPackOptionsButton.register(BridgeSoundPackScreen::new);
        BridgeClientPayloads.registerTypes();

        ClientPlayNetworking.registerGlobalReceiver(BridgeClientPayloads.StartSoundPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientSoundManager.playSound(
                        payload.pos(), payload.soundId(), payload.instrumentId(), payload.note(),
                        payload.velocity(), payload.initialVolume())));

        ClientPlayNetworking.registerGlobalReceiver(BridgeClientPayloads.UpdateVolumePayload.ID,
                (payload, context) -> context.client().execute(
                        () -> ClientSoundManager.updateVolume(payload.soundId(), payload.volume())));

        ClientPlayNetworking.registerGlobalReceiver(BridgeClientPayloads.StopSoundPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> ClientSoundManager.stopSound(payload.soundId())));

        ClientPlayNetworking.registerGlobalReceiver(BridgeClientPayloads.StartAdvancedSoundPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientSoundManager.playAdvancedSound(
                        payload.pos(), payload.soundId(), payload.instrumentId(), payload.note(),
                        payload.initialVolume(), payload.initialPitchMul(),
                        payload.x(), payload.y(), payload.z())));

        ClientPlayNetworking.registerGlobalReceiver(BridgeClientPayloads.AdvancedUpdatePayload.ID,
                (payload, context) -> context.client().execute(() -> ClientSoundManager.updateAdvanced(
                        payload.soundId(), payload.volume(), payload.pitchMultiplier(),
                        payload.x(), payload.y(), payload.z())));

        ClientTickEvents.END_CLIENT_TICK.register(ClientSoundManager::tickPauseRecovery);
        LOGGER.info("ExtendedNoteBlock Paper Bridge Client loaded (client-only registry-safe mode).");
    }
}
