package com.goldenegggovo.extendednoteblock.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class EnbLanguageTest {
    private ExtendedNoteBlockBridge plugin;
    private ServerMock server;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ExtendedNoteBlockBridge.class);
    }

    @AfterEach void tearDown() {
        MockBukkit.unmock();
    }

    @Test void defaultsToEnglishAndCopiesEditableFiles() {
        assertEquals("ENB resource pack accepted; downloading.",
                EnbLanguage.text("ENB resource pack accepted; downloading."));
        assertTrue(new File(plugin.getDataFolder(), "lang/en_us.yml").isFile());
        assertTrue(new File(plugin.getDataFolder(), "lang/zh_cn.yml").isFile());
    }

    @Test void editableEnglishYamlIsReloaded() throws java.io.IOException {
        File file = new File(plugin.getDataFolder(), "lang/en_us.yml");
        String original = java.nio.file.Files.readString(file.toPath());
        String edited = original.replace(
                ": \"ENB resource pack accepted; downloading.\"",
                ": \"Custom download text\"");
        java.nio.file.Files.writeString(file.toPath(), edited);
        EnbLanguage.load(plugin);
        assertEquals("Custom download text",
                EnbLanguage.text("ENB resource pack accepted; downloading."));
    }

    @Test void reloadsChineseTemplatesWithoutLosingValues() {
        plugin.getConfig().set("language", "zh_cn");
        EnbLanguage.load(plugin);
        assertEquals("已接受 ENB 资源包，正在下载。",
                EnbLanguage.text("ENB resource pack accepted; downloading."));
        assertEquals("已给予 3 个指挥棒。",
                EnbLanguage.text("Given 3x Conductor Wand."));
        assertEquals("指挥棒", plugin.createBridgeItem(
                ExtendedNoteBlockBridge.BridgeItemType.CONDUCTOR_WAND, 1)
                .getItemMeta().getDisplayName());
        assertEquals("Unknown source text", EnbLanguage.text("Unknown source text"));
        assertEquals("ENB CraftEngine：就绪=true，原始方块记录=5，本次迁移=2，已恢复=1，回滚=false",
                EnbLanguage.text("ENB CraftEngine: ready=true, original blocks journal=5, migrated this run=2, restored=1, rollback=false"));
    }

    @Test void reloadCommandAppliesLanguageChange() {
        plugin.getConfig().set("language", "zh_cn");
        plugin.saveConfig();
        plugin.onCommand(server.getConsoleSender(), plugin.getCommand("enb"),
                "enb", new String[]{"reload"});
        assertEquals("已接受 ENB 资源包，正在下载。",
                EnbLanguage.text("ENB resource pack accepted; downloading."));
    }
}
