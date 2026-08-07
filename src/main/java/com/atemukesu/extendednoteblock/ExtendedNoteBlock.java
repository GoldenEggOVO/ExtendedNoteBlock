package com.atemukesu.extendednoteblock;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atemukesu.extendednoteblock.block.ModBlocks;
import com.atemukesu.extendednoteblock.block.entity.ModBlockEntities;
import com.atemukesu.extendednoteblock.item.ConductorWandItem;
import com.atemukesu.extendednoteblock.item.ModItemGroups;
import com.atemukesu.extendednoteblock.network.ModMessages;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionPlaybackManager;
import com.atemukesu.extendednoteblock.screen.ModScreenHandlers;
import com.atemukesu.extendednoteblock.sound.ServerSoundManager;

public class ExtendedNoteBlock implements ModInitializer {
	public static final String MOD_ID = "extendednoteblock";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ResourceKey<Item> CONDUCTOR_WAND_KEY = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "conductor_wand"));
	public static final Item CONDUCTOR_WAND = new ConductorWandItem(
			new Item.Properties().setId(CONDUCTOR_WAND_KEY).stacksTo(1));

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();
		ModBlockEntities.registerBlockEntities();
		ModScreenHandlers.registerScreenHandlers();
		ModMessages.registerC2SPackets();
		ModMessages.registerS2CPackets();
		ServerSoundManager.initialize();
		NbsProjectionPlaybackManager.initialize();
		com.atemukesu.extendednoteblock.command.ModCommands.registerCommands();
		com.atemukesu.extendednoteblock.util.SmoothMoveManager.init();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((world, chunk, generated) ->
				com.atemukesu.extendednoteblock.util.RedstoneManager.discoverChunk(world, chunk));

		// Register Conductor Wand
		Registry.register(BuiltInRegistries.ITEM, CONDUCTOR_WAND_KEY, CONDUCTOR_WAND);

		// 存档重载后，重新同步所有接收器与发射器的实时状态
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.LOAD.register((server, world) -> {
			if (!world.isClientSide()) {
				com.atemukesu.extendednoteblock.util.RedstoneManager.syncOnWorldLoad(world);
			}
		});

		LOGGER.info("Extended Note Block Loaded.");
	}
}
