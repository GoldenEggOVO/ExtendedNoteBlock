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
 * block-entity, menu, command, C2S or server lifecycle registration. A jar using
 * this entrypoint is safe to use against an unmodded Paper/Purpur registry.
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

        // 26.2-safe GUI entry. This uses Fabric ScreenEvents rather than a mixin,
        // so it does not depend on vanilla OptionsScreen footer dimensions.
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

        ClientTickEvents.END_CLIENT_TICK.register(ClientSoundManager::tickPauseRecovery);

        LOGGER.info("ExtendedNoteBlock Paper Bridge Client loaded (client-only registry-safe mode).");
    }
}
