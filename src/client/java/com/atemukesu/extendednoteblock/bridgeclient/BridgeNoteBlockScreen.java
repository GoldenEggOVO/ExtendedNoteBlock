package com.atemukesu.extendednoteblock.bridgeclient;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Client-only editor for a Paper/Purpur bridge-managed vanilla note block.
 * No custom container/menu or registry entry is used: Paper owns the values and
 * sends them through bridge_note_edit, then this screen returns bridge_note_save.
 */
public final class BridgeNoteBlockScreen extends Screen {
    private final Screen parent;
    private final BlockPos pos;
    private final BridgeClientPayloads.NoteEditPayload initial;

    private EditBox noteField;
    private EditBox instrumentField;
    private EditBox velocityField;
    private EditBox sustainField;
    private EditBox delayField;
    private EditBox fadeInField;
    private EditBox fadeOutField;
    private Button saveButton;
    private Component status = Component.empty();

    public BridgeNoteBlockScreen(Screen parent, BridgeClientPayloads.NoteEditPayload initial) {
        super(Component.literal("Extended Note Block"));
        this.parent = parent;
        this.pos = initial.pos();
        this.initial = initial;
    }

    @Override
    protected void init() {
        super.init();

        int totalWidth = Math.min(420, Math.max(280, this.width - 40));
        int gap = 12;
        int columnWidth = (totalWidth - gap) / 2;
        int left = (this.width - totalWidth) / 2;
        int right = left + columnWidth + gap;
        int y = Math.max(42, this.height / 2 - 92);
        int row = 42;

        this.noteField = numberBox(left, y, columnWidth, "MIDI Note (0-127)", initial.note(), 3);
        this.instrumentField = numberBox(right, y, columnWidth, "Instrument ID (0-128)", initial.instrumentId(), 3);

        y += row;
        this.velocityField = numberBox(left, y, columnWidth, "Velocity (0-127)", initial.velocity(), 3);
        this.sustainField = numberBox(right, y, columnWidth, "Sustain (ticks)", initial.sustainTicks(), 3);

        y += row;
        this.delayField = numberBox(left, y, totalWidth, "Playback delay (ms)", initial.delayMs(), 7);

        y += row;
        this.fadeInField = numberBox(left, y, columnWidth, "Fade in (ticks)", initial.fadeInTicks(), 3);
        this.fadeOutField = numberBox(right, y, columnWidth, "Fade out (ticks)", initial.fadeOutTicks(), 3);

        int buttonsY = Math.min(this.height - 28, y + 40);
        this.saveButton = this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(left, buttonsY, columnWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> closeToParent())
                .bounds(right, buttonsY, columnWidth, 20).build());

        Runnable validator = this::validateFields;
        this.noteField.setResponder(ignored -> validator.run());
        this.instrumentField.setResponder(ignored -> validator.run());
        this.velocityField.setResponder(ignored -> validator.run());
        this.sustainField.setResponder(ignored -> validator.run());
        this.delayField.setResponder(ignored -> validator.run());
        this.fadeInField.setResponder(ignored -> validator.run());
        this.fadeOutField.setResponder(ignored -> validator.run());
        validateFields();
    }

    private EditBox numberBox(int x, int y, int width, String hint, int value, int maxLength) {
        EditBox field = new EditBox(this.font, x, y, width, 20, Component.literal(hint));
        field.setMaxLength(maxLength);
        field.setValue(Integer.toString(value));
        this.addRenderableWidget(field);
        return field;
    }

    private void validateFields() {
        boolean valid = parse(noteField, 0, 127) != null
                && parse(instrumentField, 0, 128) != null
                && parse(velocityField, 0, 127) != null
                && parse(sustainField, 1, 400) != null
                && parse(delayField, 0, 3_600_000) != null
                && parse(fadeInField, 0, 400) != null
                && parse(fadeOutField, 0, 400) != null;
        if (saveButton != null) saveButton.active = valid;
        status = valid ? Component.empty() : Component.literal("One or more values are outside the allowed range.");
    }

    private Integer parse(EditBox field, int min, int max) {
        if (field == null) return null;
        try {
            int value = Integer.parseInt(field.getValue().trim());
            return value >= min && value <= max ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void save() {
        Integer note = parse(noteField, 0, 127);
        Integer instrument = parse(instrumentField, 0, 128);
        Integer velocity = parse(velocityField, 0, 127);
        Integer sustain = parse(sustainField, 1, 400);
        Integer delay = parse(delayField, 0, 3_600_000);
        Integer fadeIn = parse(fadeInField, 0, 400);
        Integer fadeOut = parse(fadeOutField, 0, 400);
        if (note == null || instrument == null || velocity == null || sustain == null
                || delay == null || fadeIn == null || fadeOut == null) {
            validateFields();
            return;
        }

        ClientPlayNetworking.send(new BridgeClientPayloads.NoteSavePayload(
                pos, note, instrument, velocity, sustain, delay, fadeIn, fadeOut));
        closeToParent();
    }

    private void closeToParent() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        context.centeredText(this.font,
                Component.literal("Block: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                this.width / 2, 27, 0xFFAAAAAA);

        drawLabel(context, noteField, "MIDI Note (0-127)");
        drawLabel(context, instrumentField, "Instrument ID (0-128)");
        drawLabel(context, velocityField, "Velocity (0-127)");
        drawLabel(context, sustainField, "Sustain (1-400 ticks)");
        drawLabel(context, delayField, "Playback delay (0-3600000 ms)");
        drawLabel(context, fadeInField, "Fade in (0-400 ticks)");
        drawLabel(context, fadeOutField, "Fade out (0-400 ticks)");

        if (!status.getString().isEmpty()) {
            context.centeredText(this.font, status, this.width / 2, this.height - 40, 0xFFFF5555);
        }
    }

    private void drawLabel(GuiGraphicsExtractor context, EditBox field, String label) {
        if (field != null) {
            context.text(this.font, Component.literal(label), field.getX(), field.getY() - 11, 0xFFA0A0A0);
        }
    }
}
