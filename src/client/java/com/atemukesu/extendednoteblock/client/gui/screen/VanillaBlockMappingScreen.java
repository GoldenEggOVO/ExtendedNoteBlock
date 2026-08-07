package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.nbs.vanilla.VanillaInstrument;
import java.util.EnumMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VanillaBlockMappingScreen extends Screen {
    private static final int PER_PAGE = 7;
    private final Screen parent;
    private final EnumMap<VanillaInstrument, String> mappings;
    private int page;

    public VanillaBlockMappingScreen(Screen parent, EnumMap<VanillaInstrument, String> mappings) {
        super(Component.translatable("gui.extendednoteblock.vanilla.mapping_title"));
        this.parent = parent;
        this.mappings = mappings;
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = Math.min(500, width - 30);
        int left = width / 2 - panelWidth / 2;
        VanillaInstrument[] instruments = VanillaInstrument.values();
        int start = page * PER_PAGE;
        int end = Math.min(instruments.length, start + PER_PAGE);
        for (int i = start; i < end; i++) {
            VanillaInstrument instrument = instruments[i];
            int y = 38 + (i - start) * 23;
            EditBox field = new EditBox(font, left + 130, y, panelWidth - 130, 20,
                    Component.literal(instrument.id()));
            field.setMaxLength(80);
            field.setValue(mappings.getOrDefault(instrument, instrument.defaultSupportBlock()));
            field.setResponder(value -> mappings.put(instrument, value));
            addRenderableWidget(field);
        }
        int bottom = height - 28;
        Button previous = Button.builder(Component.literal("<"), button -> {
            page--;
            rebuildWidgets();
        }).bounds(left, bottom, 24, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), button -> {
            page++;
            rebuildWidgets();
        }).bounds(left + 28, bottom, 24, 20).build();
        next.active = end < instruments.length;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 75, bottom, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 13, 0xFFFFFFFF);
        int panelWidth = Math.min(500, width - 30);
        int left = width / 2 - panelWidth / 2;
        VanillaInstrument[] instruments = VanillaInstrument.values();
        int start = page * PER_PAGE;
        int end = Math.min(instruments.length, start + PER_PAGE);
        for (int i = start; i < end; i++) {
            String name = instruments[i].id();
            context.text(font, font.plainSubstrByWidth(name, 120), left, 44 + (i - start) * 23,
                    0xFFCCCCCC);
        }
        context.centeredText(font, Component.literal((page + 1) + " / 3"), width / 2, height - 42,
                0xFF888888);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
