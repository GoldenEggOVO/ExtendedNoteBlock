package com.atemukesu.extendednoteblock.bridgeclient;

import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.Pos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Restore settings onto a pasted projection, anchored on its red transmitter. */
public final class BridgeImportScreen extends Screen {
    private final Screen parent;
    private final String[] coordinates = {"0", "64", "0"};
    private String filename = "";
    private LitematicImportReader.Source source;
    private Component message = text("choose");
    private boolean loading, showingTransfer;
    private int rotation, mirror, generation;
    private Button restore;
    private EditBox fileField;

    public BridgeImportScreen(Screen parent) {
        super(text("title")); this.parent = parent;
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.hitResult instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            if (client.level.getBlockState(pos).is(Blocks.LEVER)) pos = pos.below();
            coordinates[0] = Integer.toString(pos.getX());
            coordinates[1] = Integer.toString(pos.getY());
            coordinates[2] = Integer.toString(pos.getZ());
        }
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.extendednoteblock.import." + key, args);
    }

    @Override protected void init() {
        int w = Math.min(460, width - 24), x = (width - w) / 2;
        fileField = new EditBox(font, x, 48, w - 136, 20, text("file"));
        fileField.setMaxLength(2048); fileField.setValue(filename);
        fileField.setResponder(value -> { filename = value; source = null; showingTransfer = false; generation++; loading = false; });
        addRenderableWidget(fileField);
        addRenderableWidget(Button.builder(text("browse"), b -> minecraft.gui.setScreen(new FilePicker(this)))
                .bounds(x + w - 132, 48, 64, 20).build());
        addRenderableWidget(Button.builder(text("load"), b -> loadFile())
                .bounds(x + w - 64, 48, 64, 20).build());
        for (int axis = 0; axis < 3; axis++) {
            int index = axis;
            EditBox field = new EditBox(font, x + axis * ((w + 6) / 3), 89, (w - 12) / 3, 20,
                    Component.literal("XYZ".substring(axis, axis + 1)));
            field.setMaxLength(10); field.setValue(coordinates[axis]);
            field.setResponder(value -> coordinates[index] = value); addRenderableWidget(field);
        }
        addRenderableWidget(Button.builder(text("rotation", rotation * 90), b -> {
            rotation = (rotation + 1) % 4; b.setMessage(text("rotation", rotation * 90));
        }).bounds(x, 116, (w - 6) / 2, 20).build());
        addRenderableWidget(Button.builder(mirrorText(), b -> {
            mirror = (mirror + 1) % 4; b.setMessage(mirrorText());
        }).bounds(x + (w + 6) / 2, 116, (w - 6) / 2, 20).build());
        restore = addRenderableWidget(Button.builder(text("restore"), b -> restore())
                .bounds(x, height - 28, (w - 6) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x + (w + 6) / 2, height - 28, (w - 6) / 2, 20).build());
        tick();
    }

    private Component mirrorText() { return text("mirror", mirror == 0 ? text("none") : Component.literal(mirror == 1 ? "X" : mirror == 2 ? "Z" : "X + Z")); }

    private void loadFile() {
        if (BridgeImportManager.busy()) return;
        final Path path;
        try { path = Path.of(filename); }
        catch (RuntimeException invalid) { message = text("failed", invalid.getMessage()); return; }
        int request = ++generation;
        loading = true; source = null; showingTransfer = false; message = text("loading");
        CompletableFuture.supplyAsync(() -> {
            try { return LitematicImportReader.read(path); }
            catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }).whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            if (request != generation) return;
            loading = false;
            if (failure != null) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                message = text("failed", cause.getMessage());
            } else { source = result; message = text("loaded", source.notes().size()); }
        }));
    }

    private void restore() {
        if (source == null || loading || BridgeImportManager.busy()) return;
        try {
            Pos destination = new Pos(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), Integer.parseInt(coordinates[2]));
            BridgeImportManager.start(source.place(destination, rotation, mirror)); showingTransfer = true;
        } catch (RuntimeException invalid) { message = text("failed", invalid.getMessage()); showingTransfer = false; }
    }

    @Override public void tick() {
        boolean active = BridgeImportManager.busy();
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.active = !active;
        // Done also cancels an unfinished upload; the server commits only after all checks.
        if (!children().isEmpty() && children().getLast() instanceof Button done) done.active = true;
        restore.active = !active && !loading && source != null && BridgeImportManager.available();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int w = Math.min(460, width - 24), x = (width - w) / 2;
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.text(font, text("hint"), x, 28, 0xFFCCCCCC);
        graphics.text(font, text("anchor"), x, 76, 0xFFCCCCCC);
        Component current = showingTransfer ? BridgeImportManager.status() : message;
        int y = 143;
        if (!BridgeImportManager.available() && !showingTransfer) {
            for (var line : font.split(text("requires_server"), w)) { graphics.text(font, line, x, y, 0xFFFFCC55); y += 10; }
        }
        for (var line : font.split(current, w)) {
            if (y + 10 > height - 32) break;
            graphics.text(font, line, x, y, showingTransfer && BridgeImportManager.successful() ? 0xFF55FF55 : 0xFFDDDDDD); y += 10;
        }
    }
    @Override public void onClose() { generation++; BridgeImportManager.cancel(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    /** Lists local schematic files without introducing a native file-dialog dependency. */
    private static final class FilePicker extends Screen {
        private final BridgeImportScreen parent;
        private final Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
        private List<Path> files = List.of();
        private Component status = text("loading");
        private int page;
        FilePicker(BridgeImportScreen parent) {
            super(text("file")); this.parent = parent;
            CompletableFuture.supplyAsync(() -> {
                try {
                    Files.createDirectories(directory);
                    try (var stream = Files.walk(directory, 8)) {
                        return stream.filter(Files::isRegularFile)
                                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".litematic"))
                                .limit(2000).sorted().toList();
                    }
                } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
            }).whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
                if (failure == null) { files = result; status = text("file_hint"); }
                else status = text("failed", failure.getCause().getMessage());
                if (Minecraft.getInstance().gui.screen() == this) rebuildWidgets();
            }));
        }
        private int pageSize() { return Math.max(1, (height - 100) / 24); }
        @Override protected void init() {
            int w = Math.min(520, width - 24), x = (width - w) / 2;
            for (int i = page * pageSize(); i < Math.min(files.size(), (page + 1) * pageSize()); i++) {
                Path path = files.get(i);
                addRenderableWidget(Button.builder(Component.literal(directory.relativize(path).toString()), b -> {
                    parent.filename = path.toString(); minecraft.gui.setScreen(parent); parent.loadFile();
                }).bounds(x, 50 + (i % pageSize()) * 24, w, 20).build());
            }
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 28, 40, 20).build());
            previous.active = page > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + w - 40, height - 28, 40, 20).build());
            next.active = (page + 1) * pageSize() < files.size();
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width / 2 - 60, height - 28, 120, 20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
            int y = 25;
            for (var line : font.split(status, width - 24)) { graphics.text(font, line, 12, y, 0xFFCCCCCC); y += 10; }
        }
        @Override public void onClose() { minecraft.gui.setScreen(parent); }
        @Override public boolean isPauseScreen() { return false; }
    }
}
