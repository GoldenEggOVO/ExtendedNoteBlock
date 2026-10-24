package com.goldenegggovo.extendednoteblock.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class OptionalAudioLanguageTest {
    private ExtendedNoteBlockBridge plugin;

    @BeforeEach void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(ExtendedNoteBlockBridge.class);
    }

    @AfterEach void tearDown() {
        MockBukkit.unmock();
    }

    @Test void optionalAudioDefaultsToEnglish() {
        assertEquals("Optional ENB audio", EnbLanguage.text("Optional ENB audio"));
        assertEquals("ENB optional audio: enabled; load status: REQUESTED",
                EnbLanguage.text("ENB optional audio: enabled; load status: REQUESTED"));
        assertEquals("ENB audio disabled; the base resource pack is retained.",
                EnbLanguage.text("ENB audio disabled; the base resource pack is retained."));
    }

    @Test void chineseChoiceTranslatesNestedStatusAndPrompt() {
        plugin.getConfig().set("language", "zh_cn");
        EnbLanguage.load(plugin);
        assertEquals("可选 ENB 音频", EnbLanguage.text("Optional ENB audio"));
        assertEquals("ENB 可选音频：已开启；加载状态：REQUESTED",
                EnbLanguage.text("ENB optional audio: enabled; load status: REQUESTED"));
        assertEquals("ENB 可选音频：已关闭；加载状态：NOT_REQUESTED",
                EnbLanguage.text("ENB optional audio: disabled; load status: NOT_REQUESTED"));
        assertEquals("无法保存音频偏好，请联系管理员。",
                EnbLanguage.text("Could not save audio preferences. Contact the server owner."));
    }
}
