package com.atemukesu.extendednoteblock;

import com.atemukesu.extendednoteblock.item.ConductorWandItem;
import com.atemukesu.extendednoteblock.network.ModPayloads;
import com.atemukesu.extendednoteblock.screen.ModScreenHandlers;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import com.atemukesu.extendednoteblock.sound.ClientSoundManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import com.atemukesu.extendednoteblock.bridgeclient.SoundPackOptionsButton;
import com.atemukesu.extendednoteblock.client.gui.screen.ExtendedNoteBlockScreen;
import com.atemukesu.extendednoteblock.client.gui.screen.NbsWorkshopScreen;
import com.atemukesu.extendednoteblock.client.gui.screen.SoundPackManagerScreen;
import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.network.ClientModMessages;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ExtendedNoteBlockClient implements ClientModInitializer {

    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(ExtendedNoteBlock.MOD_ID, "controls"));
    private static KeyMapping openWandGuiKey;
    private static KeyMapping clearSelectionKey;
    private static KeyMapping openNbsWorkshopKey;

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModScreenHandlers.EXTENDED_NOTE_BLOCK_SCREEN_HANDLER, ExtendedNoteBlockScreen::new);
        ConfigManager.initialize();
        SoundPackManager.getInstance().scanPacks();
        ClientModMessages.registerS2CPackets();
        com.atemukesu.extendednoteblock.util.ClientSmoothMoveManager.init();

        // Minecraft 26.2: use Fabric's screen event API instead of injecting into
        // OptionsScreen's footer. This keeps the entry button compact and avoids
        // the oversized button caused by copying vanilla's Done-button width.
        SoundPackOptionsButton.register(SoundPackManagerScreen::new);

        openWandGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.extendednoteblock.open_wand_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_ENTER,
                KEY_CATEGORY));

        clearSelectionKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.extendednoteblock.clear_selection",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_BACKSPACE,
                KEY_CATEGORY));

        openNbsWorkshopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.extendednoteblock.open_nbs_workshop",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientSoundManager.tickPauseRecovery(client);
            if (client.player == null)
                return;

            while (openWandGuiKey.consumeClick()) {
                if (client.player.getMainHandItem().getItem() instanceof ConductorWandItem) {
                    CompoundTag nbt = client.player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    if (nbt != null && nbt.contains("Pos1") && nbt.contains("Pos2")) {
                        BlockPos p1 = nbt.read("Pos1", BlockPos.CODEC).orElse(null);
                        BlockPos p2 = nbt.read("Pos2", BlockPos.CODEC).orElse(null);
                        ClientModMessages.sendScanRequestToServer(p1, p2);
                    } else {
                        client.player.sendOverlayMessage(
                                Component.translatable("gui.extendednoteblock.conductor.incomplete"));
                    }
                }
            }

            while (clearSelectionKey.consumeClick()) {
                if (client.player.getMainHandItem().getItem() instanceof ConductorWandItem) {
                    ClientModMessages.sendClearSelectionToServer();

                    var stack = client.player.getMainHandItem();
                    CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    nbt.remove("Pos1");
                    nbt.remove("Pos2");
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                    client.player.sendOverlayMessage(
                            Component.translatable("gui.extendednoteblock.conductor.selection_cleared"));
                }
            }

            while (openNbsWorkshopKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new NbsWorkshopScreen(null));
                }
            }
        });

        AttackBlockCallback.EVENT.register(this::onAttackBlock);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.getItemInHand(hand).getItem() instanceof ConductorWandItem) {
                return InteractionResult.PASS;
            }
            return InteractionResult.PASS;
        });

        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS
                .register(com.atemukesu.extendednoteblock.client.renderer.ConductorWandRenderer::collectSubmits);
    }

    private InteractionResult onAttackBlock(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof ConductorWandItem) {
            if (world.isClientSide()) {
                ClientPlayNetworking.send(new ModPayloads.SetWandPosPayload(1, pos));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
