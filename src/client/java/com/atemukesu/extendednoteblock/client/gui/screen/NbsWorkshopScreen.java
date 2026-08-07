package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.client.gui.widget.NbsProjectionPreviewWidget;
import com.atemukesu.extendednoteblock.map.InstrumentMap;
import com.atemukesu.extendednoteblock.nbs.AudioFileDecoder;
import com.atemukesu.extendednoteblock.nbs.AudioToNbsConverter;
import com.atemukesu.extendednoteblock.nbs.MidiToNbsConverter;
import com.atemukesu.extendednoteblock.nbs.NbsPreviewPlayer;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionOptions;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionOptions.OctaveRange;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionWriter;
import com.atemukesu.extendednoteblock.nbs.NbsReader;
import com.atemukesu.extendednoteblock.nbs.NbsSong;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public class NbsWorkshopScreen extends Screen {
    private static final int[] COMPATIBLE_SPEEDS = { 25, 50, 100, 125, 200, 250, 400, 500, 1000, 2000 };
    private final Screen parent;
    private final Path songsDirectory;
    private final Path schematicsDirectory;
    private final NbsPreviewPlayer previewPlayer = new NbsPreviewPlayer();
    private List<Path> availableFiles = List.of();
    private int filePage;
    private String searchQuery = "";
    private boolean searchDirty;
    private Path sourcePath;
    private NbsSong song;
    private boolean busy;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    private int transpose;
    private int speedPercent = 100;
    private int velocityPercent = 100;
    private int sustainTicks = 20;
    private int columns = 24;
    private boolean compatibleSpeed;
    private OctaveRange octaveRange = OctaveRange.SIX_OCTAVES;
    private boolean customInstrumentsEnabled = true;
    private int customInstrumentFallback;
    private String outputName = "nbs_projection";

    private EditBox transposeField;
    private EditBox speedField;
    private EditBox velocityField;
    private EditBox sustainField;
    private EditBox columnsField;
    private EditBox customFallbackField;
    private EditBox outputField;
    private EditBox searchField;
    private Button speedModeButton;
    private Button octaveRangeButton;
    private Button customInstrumentsButton;
    private Button previewButton;
    private NbsProjectionPreviewWidget projectionPreview;

    public NbsWorkshopScreen(Screen parent) {
        super(Component.translatable("gui.extendednoteblock.nbs.title"));
        this.parent = parent;
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
        this.songsDirectory = gameDirectory.resolve("extendednoteblock").resolve("songs");
        this.schematicsDirectory = gameDirectory.resolve("schematics").resolve("extendednoteblock");
    }

    @Override
    protected void init() {
        super.init();
        if (song == null) {
            initFilePicker();
        } else {
            initEditor();
        }
    }

    private void initFilePicker() {
        refreshFiles();
        int center = width / 2;
        int top = 48;
        int rowWidth = Math.min(360, width - 40);

        addRenderableWidget(Button.builder(Component.translatable("gui.extendednoteblock.nbs.open_folder"), button -> {
            ensureDirectories();
            Util.getPlatform().openFile(songsDirectory.toFile());
        }).bounds(center - rowWidth / 2, top, rowWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.extendednoteblock.nbs.refresh"), button -> {
            refreshFiles();
            rebuildWidgets();
        }).bounds(center + 2, top, rowWidth / 2 - 2, 20).build());

        int searchY = top + 28;
        searchField = new EditBox(font, center - rowWidth / 2, searchY, rowWidth, 20,
                Component.translatable("gui.extendednoteblock.nbs.search"));
        searchField.setHint(Component.translatable("gui.extendednoteblock.nbs.search_hint"));
        searchField.setMaxLength(120);
        searchField.setValue(searchQuery);
        searchField.setResponder(value -> {
            searchQuery = value;
            filePage = 0;
            searchDirty = true;
        });
        addRenderableWidget(searchField);

        List<Path> visibleFiles = filteredFiles();
        int listTop = searchY + 28;
        int filesPerPage = filesPerPage();
        int start = filePage * filesPerPage;
        int end = Math.min(visibleFiles.size(), start + filesPerPage);
        for (int i = start; i < end; i++) {
            Path path = visibleFiles.get(i);
            String fileName = path.getFileName().toString();
            addRenderableWidget(Button.builder(Component.literal(fileName), button -> startLoad(path))
                    .bounds(center - rowWidth / 2, listTop + (i - start) * 24, rowWidth, 20).build());
        }

        int pageY = listTop + filesPerPage * 24;
        Button previous = Button.builder(Component.literal("<"), button -> {
            filePage--;
            rebuildWidgets();
        }).bounds(center - 52, pageY, 24, 20).build();
        previous.active = filePage > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), button -> {
            filePage++;
            rebuildWidgets();
        }).bounds(center + 28, pageY, 24, 20).build();
        next.active = (filePage + 1) * filesPerPage < visibleFiles.size();
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(center - 100, height - 28, 200, 20).build());
    }

    private void initEditor() {
        projectionPreview = null;
        int center = width / 2;
        int panelWidth = Math.min(620, width - 30);
        int left = center - panelWidth / 2;
        int gap = 12;
        int columnWidth = (panelWidth - gap * 2) / 3;
        int middle = left + columnWidth + gap;
        int right = middle + columnWidth + gap;
        boolean compact = height < 300;
        int fieldY = compact ? 96 : 108;
        int fieldGap = compact ? 34 : 42;

        transposeField = numberField(left, fieldY, columnWidth, String.valueOf(transpose), -127, 127,
                value -> transpose = value);
        speedField = numberField(middle, fieldY, columnWidth, String.valueOf(speedPercent), 10, 2000,
                value -> speedPercent = value);
        velocityField = numberField(right, fieldY, columnWidth, String.valueOf(velocityPercent), 0, 400,
                value -> velocityPercent = value);

        int secondFieldY = fieldY + fieldGap;
        sustainField = numberField(left, secondFieldY, columnWidth, String.valueOf(sustainTicks), 1, 400,
                value -> sustainTicks = value);
        columnsField = numberField(middle, secondFieldY, columnWidth, String.valueOf(columns), 4, 64,
                value -> columns = value);
        outputField = new EditBox(font, right, secondFieldY, columnWidth, 20,
                Component.translatable("gui.extendednoteblock.nbs.output_name"));
        outputField.setMaxLength(80);
        outputField.setValue(outputName);
        outputField.setResponder(value -> outputName = value);
        addRenderableWidget(outputField);

        int modeY = secondFieldY + 34;
        customFallbackField = numberField(left, modeY, columnWidth, String.valueOf(customInstrumentFallback), 0, 128,
                value -> customInstrumentFallback = value);

        speedModeButton = Button.builder(speedModeText(), button -> {
            compatibleSpeed = !compatibleSpeed;
            if (compatibleSpeed) {
                speedPercent = nearestCompatibleSpeed(speedPercent);
                speedField.setValue(String.valueOf(speedPercent));
            }
            stopPreview();
            button.setMessage(speedModeText());
        }).bounds(middle, modeY, columnWidth, 20).build();
        addRenderableWidget(speedModeButton);

        octaveRangeButton = Button.builder(octaveRangeText(), button -> {
            octaveRange = octaveRange.next();
            stopPreview();
            button.setMessage(octaveRangeText());
        }).bounds(right, modeY, columnWidth, 20).build();
        addRenderableWidget(octaveRangeButton);

        int actionY = modeY + 24;
        customInstrumentsButton = Button.builder(customInstrumentsText(), button -> {
            customInstrumentsEnabled = !customInstrumentsEnabled;
            stopPreview();
            button.setMessage(customInstrumentsText());
        }).bounds(left, actionY, columnWidth, 20).build();
        addRenderableWidget(customInstrumentsButton);

        previewButton = Button.builder(previewText(), button -> togglePreview())
                .bounds(middle, actionY, columnWidth, 20).build();
        previewButton.active = !busy;
        addRenderableWidget(previewButton);

        Button export = Button.builder(Component.translatable("gui.extendednoteblock.nbs.export"),
                button -> startExport()).bounds(right, actionY, columnWidth, 20).build();
        export.active = !busy;
        addRenderableWidget(export);

        Button vanillaExport = Button.builder(Component.translatable("gui.extendednoteblock.nbs.vanilla_export"),
                button -> {
                    stopPreview();
                    minecraft.setScreen(new VanillaExportScreen(this, song, outputName, transpose, speedPercent));
                }).bounds(left, actionY + 24, panelWidth, 20).build();
        vanillaExport.active = !busy;
        addRenderableWidget(vanillaExport);

        int bottomY = height - 28;
        int projectionTop = actionY + 51;
        int projectionHeight = bottomY - projectionTop - 6;
        if (projectionHeight >= 24) {
            projectionPreview = new NbsProjectionPreviewWidget(
                    left, projectionTop, panelWidth, projectionHeight,
                    NbsProjectionWriter.previewLayout(song, currentOptions()));
            addRenderableWidget(projectionPreview);
        }
        Button otherSong = Button.builder(Component.translatable("gui.extendednoteblock.nbs.other_song"), button -> {
            stopPreview();
            song = null;
            sourcePath = null;
            status = Component.empty();
            rebuildWidgets();
        }).bounds(left, bottomY, columnWidth, 20).build();
        otherSong.active = !busy;
        addRenderableWidget(otherSong);
        addRenderableWidget(Button.builder(Component.translatable("gui.extendednoteblock.nbs.open_output"), button -> {
            ensureDirectories();
            Util.getPlatform().openFile(schematicsDirectory.toFile());
        }).bounds(middle, bottomY, columnWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(right, bottomY, columnWidth, 20).build());
    }

    private EditBox numberField(int x, int y, int width, String initial, int min, int max,
            java.util.function.IntConsumer setter) {
        EditBox field = new EditBox(font, x, y, width, 20, Component.empty());
        field.setMaxLength(8);
        field.setValue(initial);
        field.setResponder(text -> {
            try {
                int value = Integer.parseInt(text);
                boolean valid = value >= min && value <= max;
                field.setTextColor(valid ? 0xFFFFFFFF : ChatFormatting.RED.getColor());
                if (valid) {
                    setter.accept(value);
                    stopPreview();
                    updateProjectionPreview();
                }
            } catch (NumberFormatException exception) {
                field.setTextColor(ChatFormatting.RED.getColor());
            }
        });
        addRenderableWidget(field);
        return field;
    }

    private void updateProjectionPreview() {
        if (projectionPreview != null) {
            projectionPreview.setLayout(NbsProjectionWriter.previewLayout(song, currentOptions()));
        }
    }

    private int exportableNoteCount() {
        if (song == null || velocityPercent <= 0) {
            return 0;
        }
        int count = 0;
        for (NbsSong.Note note : song.notes()) {
            if (song.isTempoChanger(note)) {
                continue;
            }
            int layerVolume = note.layer() >= 0 && note.layer() < song.layers().size()
                    ? song.layers().get(note.layer()).volume()
                    : 100;
            if (note.velocity() > 0 && layerVolume > 0) {
                count++;
            }
        }
        return count;
    }

    private NbsProjectionOptions currentOptions() {
        if (compatibleSpeed) {
            int adjusted = nearestCompatibleSpeed(speedPercent);
            if (adjusted != speedPercent) {
                speedPercent = adjusted;
                if (speedField != null) {
                    speedField.setValue(String.valueOf(speedPercent));
                }
            }
        }
        return new NbsProjectionOptions(transpose, speedPercent / 100.0, velocityPercent / 100.0,
                sustainTicks, columns, octaveRange, customInstrumentsEnabled, customInstrumentFallback);
    }

    private static int nearestCompatibleSpeed(int speed) {
        int nearest = COMPATIBLE_SPEEDS[0];
        int distance = Math.abs(speed - nearest);
        for (int candidate : COMPATIBLE_SPEEDS) {
            int candidateDistance = Math.abs(speed - candidate);
            if (candidateDistance < distance) {
                nearest = candidate;
                distance = candidateDistance;
            }
        }
        return nearest;
    }

    private Component speedModeText() {
        return Component.translatable("gui.extendednoteblock.nbs.speed_mode",
                Component.translatable(compatibleSpeed
                        ? "gui.extendednoteblock.nbs.speed_compatible"
                        : "gui.extendednoteblock.nbs.speed_free"));
    }

    private Component octaveRangeText() {
        return Component.translatable("gui.extendednoteblock.nbs.octave_range",
                Component.translatable(octaveRange == OctaveRange.TWO_OCTAVES
                        ? "gui.extendednoteblock.nbs.octave_two"
                        : "gui.extendednoteblock.nbs.octave_six"));
    }

    private Component customInstrumentsText() {
        return Component.translatable("gui.extendednoteblock.nbs.custom_instruments",
                Component.translatable(customInstrumentsEnabled
                        ? "gui.extendednoteblock.nbs.enabled"
                        : "gui.extendednoteblock.nbs.disabled"));
    }

    private Component previewText() {
        return Component.translatable(previewPlayer.isPlaying()
                ? "gui.extendednoteblock.nbs.preview_stop"
                : "gui.extendednoteblock.nbs.preview_start");
    }

    private void togglePreview() {
        if (previewPlayer.isPlaying()) {
            stopPreview();
            setStatus(Component.translatable("gui.extendednoteblock.nbs.preview_stopped"), 0xFFAAAAAA);
            return;
        }
        if (song == null) {
            return;
        }
        var activePack = SoundPackManager.getInstance().getActivePackInfo();
        if (activePack == null || activePack.availableNotes().isEmpty()) {
            setStatus(Component.translatable("gui.extendednoteblock.nbs.preview_no_pack"), 0xFFFF5555);
            return;
        }

        previewPlayer.start(song, currentOptions());
        if (!previewPlayer.isPlaying()) {
            setStatus(Component.translatable("gui.extendednoteblock.nbs.preview_empty"), 0xFFFF5555);
        } else {
            setStatus(Component.translatable("gui.extendednoteblock.nbs.preview_started",
                    previewPlayer.totalNotes()), 0xFF55FF55);
        }
        updatePreviewButton();
    }

    private void stopPreview() {
        previewPlayer.stop();
        updatePreviewButton();
    }

    private void updatePreviewButton() {
        if (previewButton != null) {
            previewButton.setMessage(previewText());
        }
    }

    private void startLoad(Path path) {
        if (busy) {
            return;
        }
        if (AudioFileDecoder.supports(path)) {
            startAudioConversion(path);
            return;
        }
        if (MidiToNbsConverter.supports(path)) {
            startMidiConversion(path);
            return;
        }
        stopPreview();
        busy = true;
        setStatus(Component.translatable("gui.extendednoteblock.nbs.loading"), 0xFFFFFF55);
        rebuildWidgets();

        CompletableFuture.runAsync(() -> {
            try {
                NbsSong loaded = NbsReader.read(path);
                minecraft.execute(() -> {
                    sourcePath = path;
                    song = loaded;
                    outputName = sanitizeFileName(stripExtension(path.getFileName().toString()));
                    busy = false;
                    setStatus(Component.translatable("gui.extendednoteblock.nbs.loaded"), 0xFF55FF55);
                    if (minecraft.screen == this) {
                        rebuildWidgets();
                    }
                });
            } catch (Exception exception) {
                minecraft.execute(() -> {
                    busy = false;
                    setStatus(Component.translatable("gui.extendednoteblock.nbs.load_failed", shortError(exception)),
                            0xFFFF5555);
                    if (minecraft.screen == this) {
                        rebuildWidgets();
                    }
                });
            }
        });
    }

    private void startAudioConversion(Path path) {
        stopPreview();
        busy = true;
        setStatus(Component.translatable("gui.extendednoteblock.nbs.audio_converting"), 0xFFFFFF55);
        rebuildWidgets();

        CompletableFuture.runAsync(() -> {
            try {
                AudioToNbsConverter.ConversionResult result = AudioToNbsConverter.convert(path, songsDirectory);
                minecraft.execute(() -> {
                    sourcePath = result.output();
                    song = result.song();
                    outputName = sanitizeFileName(stripExtension(path.getFileName().toString()));
                    busy = false;
                    setStatus(Component.translatable("gui.extendednoteblock.nbs.audio_converted",
                            result.output().getFileName().toString(), result.song().notes().size()), 0xFF55FF55);
                    if (minecraft.screen == this) {
                        rebuildWidgets();
                    }
                });
            } catch (Exception exception) {
                minecraft.execute(() -> {
                    busy = false;
                    setStatus(Component.translatable("gui.extendednoteblock.nbs.audio_convert_failed",
                            shortError(exception)), 0xFFFF5555);
                    if (minecraft.screen == this) {
                        rebuildWidgets();
                    }
                });
            }
        });
    }

    private void startMidiConversion(Path path) {
        stopPreview();
        busy = true;
        setStatus(Component.translatable("gui.extendednoteblock.nbs.midi_converting"), 0xFFFFFF55);
        rebuildWidgets();

        CompletableFuture.runAsync(() -> {
            try {
                MidiToNbsConverter.ConversionResult result = MidiToNbsConverter.convert(path, songsDirectory);
                minecraft.execute(() -> {
                    sourcePath = result.output();
                    song = result.song();
                    outputName = sanitizeFileName(stripExtension(path.getFileName().toString()));
                    busy = false;
                    setStatus(Component.translatable("gui.extendednoteblock.nbs.midi_converted",
                            result.output().getFileName().toString(), result.song().notes().size()), 0xFF55FF55);
                    if (minecraft.screen == this) {
                        rebuildWidgets();
                    }
                });
            } catch (Exception exception) {
                minecraft.execute(() -> {
                    busy = false;
                    setStatus(Component.translatable("gui.extendednoteblock.nbs.midi_convert_failed",
                            shortError(exception)), 0xFFFF5555);
                    if (minecraft.screen == this) {
                        rebuildWidgets();
                    }
                });
            }
        });
    }

    private void startExport() {
        if (busy || song == null) {
            return;
        }
        stopPreview();
        String safeName = sanitizeFileName(stripExtension(outputName));
        if (safeName.isBlank()) {
            setStatus(Component.translatable("gui.extendednoteblock.nbs.invalid_output"), 0xFFFF5555);
            return;
        }

        busy = true;
        setStatus(Component.translatable("gui.extendednoteblock.nbs.exporting"), 0xFFFFFF55);
        rebuildWidgets();
        NbsProjectionOptions options = currentOptions();
        Path output = schematicsDirectory.resolve(safeName + ".litematic");
        String author = minecraft.player == null ? "ExtendedNoteBlock" : minecraft.player.getName().getString();
        NbsSong exportSong = song;

        CompletableFuture.runAsync(() -> {
            try {
                NbsProjectionWriter.ProjectionResult result = NbsProjectionWriter.write(exportSong, options, output,
                        author);
                minecraft.execute(() -> {
                    busy = false;
                    if (result.customInstrumentFallbacks() > 0 || result.clampedPitches() > 0) {
                        setStatus(Component.translatable("gui.extendednoteblock.nbs.exported_warnings",
                                result.output().getFileName().toString(), result.noteCount(),
                                result.customInstrumentFallbacks(), result.clampedPitches()), 0xFFFFAA55);
                    } else {
                        setStatus(Component.translatable("gui.extendednoteblock.nbs.exported",
                                result.output().getFileName().toString(), result.noteCount()), 0xFF55FF55);
                    }
                    if (minecraft.screen == this) {
                        rebuildWidgets();
                    }
                });
            } catch (Exception exception) {
                minecraft.execute(() -> {
                    busy = false;
                    setStatus(Component.translatable("gui.extendednoteblock.nbs.export_failed", shortError(exception)),
                            0xFFFF5555);
                    if (minecraft.screen == this) {
                        rebuildWidgets();
                    }
                });
            }
        });
    }

    private void refreshFiles() {
        ensureDirectories();
        try (Stream<Path> files = Files.list(songsDirectory)) {
            availableFiles = files
                    .filter(Files::isRegularFile)
                    .filter(NbsWorkshopScreen::isSupportedSongFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException exception) {
            availableFiles = List.of();
            setStatus(Component.translatable("gui.extendednoteblock.nbs.folder_failed", shortError(exception)),
                    0xFFFF5555);
        }
        int maxPage = Math.max(0, (availableFiles.size() - 1) / filesPerPage());
        filePage = Math.min(filePage, maxPage);
    }

    private int filesPerPage() {
        return Math.max(1, Math.min(18, (height - 160) / 24));
    }

    private List<Path> filteredFiles() {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return availableFiles;
        }
        return availableFiles.stream()
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(songsDirectory);
            Files.createDirectories(schematicsDirectory);
        } catch (IOException exception) {
            setStatus(Component.translatable("gui.extendednoteblock.nbs.folder_failed", shortError(exception)),
                    0xFFFF5555);
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        paths.stream()
                .filter(Files::isRegularFile)
                .filter(NbsWorkshopScreen::isSupportedSongFile)
                .findFirst()
                .ifPresentOrElse(this::startLoad,
                        () -> setStatus(Component.translatable("gui.extendednoteblock.nbs.drop_invalid"), 0xFFFF5555));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 13, 0xFFFFFFFF);

        if (song == null) {
            context.centeredText(font, Component.translatable("gui.extendednoteblock.nbs.drop_hint"),
                    width / 2, 30, 0xFFAAAAAA);
            if (filteredFiles().isEmpty() && !busy) {
                context.centeredText(font, Component.translatable(searchQuery.isBlank()
                                ? "gui.extendednoteblock.nbs.no_files"
                                : "gui.extendednoteblock.nbs.no_search_results"),
                        width / 2, 110, 0xFF888888);
            }
            if (busy) {
                context.centeredText(font, status,
                        width / 2, height / 2, 0xFFFFFF55);
            }
        } else {
            drawSongSummary(context);
            drawEditorLabels(context);
        }

        if (!status.getString().isEmpty()) {
            String text = font.plainSubstrByWidth(status.getString(), Math.max(80, width - 30));
            int statusY = song == null ? height - 42 : (height < 300 ? 72 : 84);
            context.centeredText(font, text, width / 2, statusY, statusColor);
        }
    }

    private void drawSongSummary(GuiGraphicsExtractor context) {
        String name = song.name().isBlank() && sourcePath != null ? sourcePath.getFileName().toString() : song.name();
        name = font.plainSubstrByWidth(name, Math.max(80, width - 40));
        context.centeredText(font, name, width / 2, 30, 0xFFFFFFFF);
        int tempoChanges = (int) song.notes().stream().filter(song::isTempoChanger).count();
        Component details = Component.translatable("gui.extendednoteblock.nbs.summary",
                song.version(), song.notes().size(), song.layerCount(), String.format(Locale.ROOT, "%.2f", song.initialTempo()));
        drawCenteredClamped(context, details, 44, 0xFFAAAAAA);
        Component compatibility = Component.translatable("gui.extendednoteblock.nbs.compatibility",
                song.customInstruments().size(), tempoChanges, song.lengthTicks());
        drawCenteredClamped(context, compatibility, 57, 0xFFAAAAAA);
        if (height >= 300) {
            drawCenteredClamped(context, Component.translatable("gui.extendednoteblock.nbs.global_warning"),
                    72, 0xFFFFAA55);
        }
    }

    private void drawCenteredClamped(GuiGraphicsExtractor context, Component text, int y, int color) {
        String visible = font.plainSubstrByWidth(text.getString(), Math.max(80, width - 30));
        context.centeredText(font, visible, width / 2, y, color);
    }

    private void drawEditorLabels(GuiGraphicsExtractor context) {
        drawLabel(context, transposeField, "gui.extendednoteblock.nbs.transpose");
        drawLabel(context, speedField, "gui.extendednoteblock.nbs.speed");
        drawLabel(context, velocityField, "gui.extendednoteblock.nbs.velocity");
        drawLabel(context, sustainField, "gui.extendednoteblock.nbs.sustain");
        drawLabel(context, columnsField, "gui.extendednoteblock.nbs.columns");
        drawLabel(context, outputField, "gui.extendednoteblock.nbs.output_name");
        if (customFallbackField != null) {
            String instrument = InstrumentMap.GM_INSTRUMENT_ID_TO_NAME.getOrDefault(customInstrumentFallback,
                    "Unknown");
            Component label = Component.translatable("gui.extendednoteblock.nbs.custom_fallback",
                    customInstrumentFallback, instrument);
            String visible = font.plainSubstrByWidth(label.getString(), customFallbackField.getWidth());
            context.text(font, visible, customFallbackField.getX(), customFallbackField.getY() - 11, 0xFFCCCCCC);
        }
    }

    private void drawLabel(GuiGraphicsExtractor context, EditBox field, String translationKey) {
        if (field != null) {
            context.text(font, Component.translatable(translationKey), field.getX(), field.getY() - 11, 0xFFCCCCCC);
        }
    }

    private void setStatus(Component status, int color) {
        this.status = status;
        this.statusColor = color;
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static boolean isSupportedSongFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".nbs") || AudioFileDecoder.supports(path) || MidiToNbsConverter.supports(path);
    }

    private static String sanitizeFileName(String value) {
        return value.replaceAll("[<>:\"/\\\\|?*]", "_").trim();
    }

    private static String shortError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 100 ? message.substring(0, 100) : message;
    }

    @Override
    public void tick() {
        super.tick();
        if (searchDirty && song == null) {
            searchDirty = false;
            rebuildWidgets();
            if (searchField != null) {
                setFocused(searchField);
                searchField.setFocused(true);
                searchField.setCursorPosition(searchQuery.length());
            }
        }
        boolean wasPlaying = previewPlayer.isPlaying();
        previewPlayer.tick();
        if (wasPlaying && !previewPlayer.isPlaying()) {
            setStatus(Component.translatable("gui.extendednoteblock.nbs.preview_finished"), 0xFF55FF55);
            updatePreviewButton();
        }
    }

    @Override
    public void onClose() {
        stopPreview();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
