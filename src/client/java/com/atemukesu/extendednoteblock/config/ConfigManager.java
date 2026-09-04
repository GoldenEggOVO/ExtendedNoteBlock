package com.atemukesu.extendednoteblock.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import com.atemukesu.extendednoteblock.sound.SoundPackInfo;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Keep this class safe for the Paper bridge client: do not touch the full
    // ExtendedNoteBlock initializer merely to obtain a constant.
    private static final String MOD_ID = "extendednoteblock";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID + " Config");
    private static File configFile;
    private static ModConfig config;

    public static void initialize() {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        configFile = new File(configDir, MOD_ID + ".json");
        if (configFile.exists()) {
            loadConfig();
        } else {
            LOGGER.info("Config file not found, creating a new one.");
            config = new ModConfig();
            saveConfig();
        }
    }

    private static void loadConfig() {
        try (FileReader reader = new FileReader(configFile)) {
            config = GSON.fromJson(reader, ModConfig.class);
            if (config == null) {
                throw new IOException("Config file is empty or corrupted.");
            }
            LOGGER.info("Successfully loaded config file.");
            saveConfig();
        } catch (IOException e) {
            LOGGER.error("Failed to load config file, using default values.", e);
            config = new ModConfig();
            saveConfig();
        }
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file.", e);
        }
    }

    public static ModConfig getConfig() {
        if (config == null) {
            initialize();
        }
        return config;
    }

    public static boolean isActiveSoundPackReady() {
        SoundPackManager manager = SoundPackManager.getInstance();
        manager.scanPacks();
        SoundPackInfo activePack = manager.getActivePackInfo();
        if (activePack == null) {
            return false;
        }
        return activePack.status() == SoundPackInfo.Status.OK;
    }
}
