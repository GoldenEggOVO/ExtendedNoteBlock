package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.nbs.NbsSong;
import com.atemukesu.extendednoteblock.nbs.vanilla.StructureFileWriter;
import com.atemukesu.extendednoteblock.nbs.vanilla.VanillaDatapackExporter;
import com.atemukesu.extendednoteblock.nbs.vanilla.VanillaExportOptions;
import com.atemukesu.extendednoteblock.nbs.vanilla.VanillaInstrument;
import com.atemukesu.extendednoteblock.nbs.vanilla.VanillaStructureGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class VanillaExportScreen extends Screen {
    private static final String[] CATEGORIES = {"master", "music", "record", "block", "player", "ambient"};
    private final Screen parent;
    private final NbsSong song;
    private final Path structuresDirectory;
    private final Path datapacksDirectory;
    private final EnumMap<VanillaInstrument, String> mappings = new EnumMap<>(VanillaInstrument.class);
    private VanillaExportOptions.Target target = VanillaExportOptions.Target.REDSTONE;
    private StructureFileWriter.Format format = StructureFileWriter.Format.LITEMATIC;
    private VanillaExportOptions.Distribution distribution = VanillaExportOptions.Distribution.TWO_SIDED;
    private VanillaExportOptions.PitchMode pitchMode = VanillaExportOptions.PitchMode.OCTAVE_FOLD;
    private int transpose;
    private int musicSpeedPercent;
    private int stepsPerSecond = 10;
    private int railSpeedTenths = 80;
    private int poweredRailInterval = 8;
    private boolean includeCommandBlock = true;
    private boolean includeMinecart = true;
    private boolean loop;
    private boolean sharedPlayback;
    private String floorBlock = "minecraft:stone";
    private String circuitBlock = "minecraft:smooth_stone";
    private String railBaseBlock = "minecraft:stone";
    private String namespace = "extendednoteblock_music";
    private String soundCategory = "record";
    private String outputName;
    private boolean blocksPage;
    private boolean busy;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;
    private EditBox transposeField;
    private EditBox speedField;
    private EditBox railSpeedField;
    private EditBox intervalField;
    private EditBox outputField;
    private EditBox floorField;
    private EditBox circuitField;
    private EditBox railBaseField;
    private EditBox namespaceField;

    public VanillaExportScreen(Screen parent, NbsSong song, String outputName, int transpose,
            int musicSpeedPercent) {
        super(Component.translatable("gui.extendednoteblock.vanilla.title"));
        this.parent = parent;
        this.song = song;
        this.outputName = outputName + "_vanilla";
        this.transpose = transpose;
        this.musicSpeedPercent = musicSpeedPercent;
        for (VanillaInstrument instrument : VanillaInstrument.values()) {
            mappings.put(instrument, instrument.defaultSupportBlock());
        }
        Path game = Minecraft.getInstance().gameDirectory.toPath();
        structuresDirectory = game.resolve("schematics").resolve("extendednoteblock").resolve("vanilla");
        datapacksDirectory = game.resolve("extendednoteblock").resolve("datapacks");
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = Math.min(620, width - 30);
        int left = width / 2 - panelWidth / 2;
        int gap = 8;
        int column = (panelWidth - gap * 2) / 3;
        int middle = left + column + gap;
        int right = middle + column + gap;
        if (blocksPage) initBlocks(left, middle, right, column);
        else initGeneral(left, middle, right, column);

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left, bottom, column, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.extendednoteblock.vanilla.open_output"), button -> {
            ensureDirectories();
            Util.getPlatform().openFile(currentDirectory().toFile());
        }).bounds(middle, bottom, column, 20).build());
        Button export = Button.builder(Component.translatable("gui.extendednoteblock.vanilla.export"),
                button -> startExport()).bounds(right, bottom, column, 20).build();
        export.active = !busy;
        addRenderableWidget(export);
    }

    private void initGeneral(int left, int middle, int right, int column) {
        addRenderableWidget(Button.builder(targetText(), button -> {
            target = target.next(); rebuildWidgets();
        }).bounds(left, 55, column, 20).build());
        Button formatButton = Button.builder(formatText(), button -> {
            format = format.next(); button.setMessage(formatText());
        }).bounds(middle, 55, column, 20).build();
        formatButton.active = target != VanillaExportOptions.Target.DATAPACK;
        addRenderableWidget(formatButton);
        addRenderableWidget(Button.builder(distributionText(), button -> {
            distribution = distribution.next(); button.setMessage(distributionText());
        }).bounds(right, 55, column, 20).build());

        addRenderableWidget(Button.builder(pitchText(), button -> {
            pitchMode = pitchMode.next(); button.setMessage(pitchText());
        }).bounds(left, 81, column, 20).build());
        addRenderableWidget(Button.builder(resolutionText(), button -> {
            stepsPerSecond = stepsPerSecond == 10 ? 5 : stepsPerSecond == 5 ? 4 : 10;
            button.setMessage(resolutionText());
        }).bounds(middle, 81, column, 20).build());
        addRenderableWidget(Button.builder(toggleText("command", includeCommandBlock), button -> {
            includeCommandBlock = !includeCommandBlock;
            button.setMessage(toggleText("command", includeCommandBlock));
        }).bounds(right, 81, column, 20).build());

        Button minecart = Button.builder(toggleText("minecart", includeMinecart), button -> {
            includeMinecart = !includeMinecart;
            button.setMessage(toggleText("minecart", includeMinecart));
        }).bounds(left, 107, column, 20).build();
        minecart.active = target == VanillaExportOptions.Target.RAIL;
        addRenderableWidget(minecart);
        Button loopButton = Button.builder(toggleText("loop", loop), button -> {
            loop = !loop; button.setMessage(toggleText("loop", loop));
        }).bounds(middle, 107, column, 20).build();
        loopButton.active = target == VanillaExportOptions.Target.DATAPACK;
        addRenderableWidget(loopButton);
        Button sharedButton = Button.builder(toggleText("shared", sharedPlayback), button -> {
            sharedPlayback = !sharedPlayback; button.setMessage(toggleText("shared", sharedPlayback));
        }).bounds(right, 107, column, 20).build();
        sharedButton.active = target == VanillaExportOptions.Target.DATAPACK;
        addRenderableWidget(sharedButton);

        transposeField = numberField(left, 143, column, transpose, -48, 48, value -> transpose = value);
        speedField = numberField(middle, 143, column, musicSpeedPercent, 10, 400, value -> musicSpeedPercent = value);
        railSpeedField = numberField(right, 143, column, railSpeedTenths, 10, 80, value -> railSpeedTenths = value);
        railSpeedField.active = target == VanillaExportOptions.Target.RAIL;
        intervalField = numberField(left, 176, column, poweredRailInterval, 1, 32,
                value -> poweredRailInterval = value);
        intervalField.active = target == VanillaExportOptions.Target.RAIL;
        outputField = textField(middle, 176, column, outputName, value -> outputName = value);
        addRenderableWidget(Button.builder(Component.translatable("gui.extendednoteblock.vanilla.blocks_page"), button -> {
            blocksPage = true; rebuildWidgets();
        }).bounds(right, 176, column, 20).build());
    }

    private void initBlocks(int left, int middle, int right, int column) {
        floorField = textField(left, 64, column, floorBlock, value -> floorBlock = value);
        circuitField = textField(middle, 64, column, circuitBlock, value -> circuitBlock = value);
        railBaseField = textField(right, 64, column, railBaseBlock, value -> railBaseBlock = value);
        namespaceField = textField(left, 105, column, namespace, value -> namespace = value);
        addRenderableWidget(Button.builder(categoryText(), button -> {
            int index = java.util.Arrays.asList(CATEGORIES).indexOf(soundCategory);
            soundCategory = CATEGORIES[(index + 1) % CATEGORIES.length];
            button.setMessage(categoryText());
        }).bounds(middle, 105, column, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.extendednoteblock.vanilla.instrument_blocks"),
                button -> minecraft.setScreen(new VanillaBlockMappingScreen(this, mappings)))
                .bounds(right, 105, column, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.extendednoteblock.vanilla.general_page"), button -> {
            blocksPage = false; rebuildWidgets();
        }).bounds(left, 146, panelWidth(column), 20).build());
    }

    private int panelWidth(int column) {
        return column * 3 + 16;
    }

    private EditBox numberField(int x, int y, int width, int initial, int min, int max,
            java.util.function.IntConsumer setter) {
        EditBox field = new EditBox(font, x, y, width, 20, Component.empty());
        field.setValue(Integer.toString(initial));
        field.setResponder(value -> {
            try {
                int parsed = Integer.parseInt(value);
                boolean valid = parsed >= min && parsed <= max;
                field.setTextColor(valid ? 0xFFFFFFFF : ChatFormatting.RED.getColor());
                if (valid) setter.accept(parsed);
            } catch (NumberFormatException exception) {
                field.setTextColor(ChatFormatting.RED.getColor());
            }
        });
        addRenderableWidget(field);
        return field;
    }

    private EditBox textField(int x, int y, int width, String initial,
            java.util.function.Consumer<String> setter) {
        EditBox field = new EditBox(font, x, y, width, 20, Component.empty());
        field.setMaxLength(100);
        field.setValue(initial);
        field.setResponder(setter);
        addRenderableWidget(field);
        return field;
    }

    private VanillaExportOptions options() {
        return new VanillaExportOptions(target, format, distribution, pitchMode, transpose,
                musicSpeedPercent, stepsPerSecond, railSpeedTenths, poweredRailInterval,
                includeCommandBlock, includeMinecart, loop, sharedPlayback, floorBlock, circuitBlock,
                railBaseBlock, namespace, soundCategory, Map.copyOf(mappings));
    }

    private void startExport() {
        if (busy) return;
        String safeName = outputName.replaceAll("[<>:\"/\\\\|?*]", "_").trim();
        if (safeName.isBlank()) {
            status = Component.translatable("gui.extendednoteblock.nbs.invalid_output");
            statusColor = 0xFFFF5555;
            return;
        }
        busy = true;
        status = Component.translatable("gui.extendednoteblock.vanilla.exporting");
        statusColor = 0xFFFFFF55;
        rebuildWidgets();
        VanillaExportOptions current = options();
        String author = minecraft.player == null ? "ExtendedNoteBlock" : minecraft.player.getName().getString();
        CompletableFuture.runAsync(() -> {
            try {
                ensureDirectories();
                Path output;
                int notes;
                int shifted;
                int timing;
                if (current.target() == VanillaExportOptions.Target.DATAPACK) {
                    var result = VanillaDatapackExporter.write(song, current,
                            datapacksDirectory.resolve(safeName + ".zip"));
                    output = result.output(); notes = result.noteCount(); shifted = result.shiftedNotes(); timing = 0;
                } else {
                    var result = VanillaStructureGenerator.generate(song, current);
                    Path requested = structuresDirectory.resolve(safeName + current.format().extension());
                    output = StructureFileWriter.write(result.structure(), requested, current.format(),
                            song.name().isBlank() ? safeName : song.name(), author);
                    notes = result.noteCount(); shifted = result.shiftedNotes(); timing = result.timingShifts();
                }
                Path finalOutput = output;
                int finalNotes = notes, finalShifted = shifted, finalTiming = timing;
                minecraft.execute(() -> {
                    busy = false;
                    status = Component.translatable("gui.extendednoteblock.vanilla.exported",
                            finalOutput.getFileName().toString(), finalNotes, finalShifted, finalTiming);
                    statusColor = finalShifted + finalTiming > 0 ? 0xFFFFAA55 : 0xFF55FF55;
                    if (minecraft.screen == this) rebuildWidgets();
                });
            } catch (Exception exception) {
                minecraft.execute(() -> {
                    busy = false;
                    String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                    status = Component.translatable("gui.extendednoteblock.nbs.export_failed", message);
                    statusColor = 0xFFFF5555;
                    if (minecraft.screen == this) rebuildWidgets();
                });
            }
        });
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(structuresDirectory);
            Files.createDirectories(datapacksDirectory);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Path currentDirectory() {
        return target == VanillaExportOptions.Target.DATAPACK ? datapacksDirectory : structuresDirectory;
    }

    private Component targetText() { return Component.translatable("gui.extendednoteblock.vanilla.target", target.name()); }
    private Component formatText() { return Component.translatable("gui.extendednoteblock.vanilla.format", format.name()); }
    private Component distributionText() { return Component.translatable("gui.extendednoteblock.vanilla.distribution", distribution.name()); }
    private Component pitchText() { return Component.translatable("gui.extendednoteblock.vanilla.pitch", pitchMode.name()); }
    private Component resolutionText() { return Component.translatable("gui.extendednoteblock.vanilla.resolution", stepsPerSecond); }
    private Component categoryText() { return Component.translatable("gui.extendednoteblock.vanilla.category", soundCategory); }
    private Component toggleText(String key, boolean enabled) {
        return Component.translatable("gui.extendednoteblock.vanilla." + key,
                Component.translatable(enabled ? "gui.extendednoteblock.nbs.enabled" : "gui.extendednoteblock.nbs.disabled"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 13, 0xFFFFFFFF);
        String summary = song.name() + " · " + song.notes().size() + " notes";
        context.centeredText(font, font.plainSubstrByWidth(summary, width - 30), width / 2, 28, 0xFFAAAAAA);
        if (!status.getString().isEmpty()) {
            context.centeredText(font, font.plainSubstrByWidth(status.getString(), width - 30), width / 2, 40, statusColor);
        }
        if (blocksPage) drawBlockLabels(context); else drawGeneralLabels(context);
    }

    private void drawGeneralLabels(GuiGraphicsExtractor context) {
        label(context, transposeField, "gui.extendednoteblock.nbs.transpose");
        label(context, speedField, "gui.extendednoteblock.nbs.speed");
        label(context, railSpeedField, "gui.extendednoteblock.vanilla.rail_speed");
        label(context, intervalField, "gui.extendednoteblock.vanilla.power_interval");
        label(context, outputField, "gui.extendednoteblock.nbs.output_name");
    }

    private void drawBlockLabels(GuiGraphicsExtractor context) {
        label(context, floorField, "gui.extendednoteblock.vanilla.floor_block");
        label(context, circuitField, "gui.extendednoteblock.vanilla.circuit_block");
        label(context, railBaseField, "gui.extendednoteblock.vanilla.rail_block");
        label(context, namespaceField, "gui.extendednoteblock.vanilla.namespace");
    }

    private void label(GuiGraphicsExtractor context, EditBox field, String key) {
        if (field != null) context.text(font, Component.translatable(key), field.getX(), field.getY() - 11, 0xFFCCCCCC);
    }

    @Override
    public void onClose() {
        if (!busy && minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
