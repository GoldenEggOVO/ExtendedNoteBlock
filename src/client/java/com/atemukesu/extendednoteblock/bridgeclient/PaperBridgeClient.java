package com.atemukesu.extendednoteblock.bridgeclient;

import com.atemukesu.extendednoteblock.client.gui.screen.NbsWorkshopScreen;
import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.sound.ClientSoundManager;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
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
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
    private static KeyMapping openNbsWorkshopKey;

    @Override
    public void onInitializeClient() {
        registerBuiltInVisualPack();
        ConfigManager.initialize();

        SoundPackManager packs = SoundPackManager.getInstance();
        packs.scanPacks();
        if (packs.getActivePackInfo() == null) {
            packs.setActivePack(SoundPackManager.DEFAULT_PACK_ZIP_NAME);
        }

        SoundPackOptionsButton.register(BridgeSoundPackScreen::new);
        BridgeClientPayloads.registerTypes();

        openNbsWorkshopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.extendednoteblock.open_nbs_workshop",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEY_CATEGORY));

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

        ClientPlayNetworking.registerGlobalReceiver(BridgeClientPayloads.NoteEditPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Screen parent = context.client().gui.screen();
                    context.client().gui.setScreen(new BridgeNoteBlockScreen(parent, payload));
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientSoundManager.tickPauseRecovery(client);
            while (openNbsWorkshopKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new NbsWorkshopScreen(null));
                }
            }
        });

        LOGGER.info("ExtendedNoteBlock Paper Client loaded (registry-safe mode, built-in visuals, NBS workshop and note editor).");
    }

    private static void registerBuiltInVisualPack() {
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container -> {
            boolean registered = ResourceLoader.registerBuiltinPack(
                    Identifier.fromNamespaceAndPath(MOD_ID, "bridge_visuals"),
                    container,
                    Component.literal("ExtendedNoteBlock Paper Client Visuals"),
                    PackActivationType.ALWAYS_ENABLED);
            if (!registered) {
                LOGGER.warn("Could not register the built-in ExtendedNoteBlock visual resource pack; root assets remain available as fallback.");
            }
        });
    }
}
