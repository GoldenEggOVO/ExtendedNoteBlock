package com.atemukesu.extendednoteblock.bridgeclient;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import com.atemukesu.extendednoteblock.sound.SoundPackInfo;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;

/**
 * Registry-safe sound-pack manager for the Paper/Purpur Bridge Client.
 *
 * This screen intentionally contains no block/item/menu references. It only
 * manages the client-side ExtendedNoteBlock sound packs used by bridge payloads.
 */
public final class BridgeSoundPackScreen extends Screen {
    private final Screen parent;
    private PackList list;

    public BridgeSoundPackScreen(Screen parent) {
        super(Component.translatable("gui.extendednoteblock.pack_manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        SoundPackManager.getInstance().scanPacks();

        int listTop = 32;
        int listBottom = Math.max(listTop + 35, this.height - 36);
        this.list = new PackList(this.width, listBottom - listTop, listTop);
        this.addRenderableWidget(this.list);

        String activeId = SoundPackManager.getInstance().getActivePackId();
        if (activeId != null) {
            this.list.children().stream()
                    .filter(entry -> Objects.equals(entry.pack.id(), activeId))
                    .findFirst()
                    .ifPresent(entry -> this.list.setSelected(entry, true));
        }

        int availableWidth = Math.max(240, this.width - 16);
        int rowWidth = Math.min(308, availableWidth);
        int gap = 8;
        int buttonWidth = (rowWidth - gap) / 2;
        int startX = (this.width - rowWidth) / 2;
        int buttonY = this.height - 28;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.extendednoteblock.pack_manager.button.open_folder"),
                button -> {
                    Path packDir = SoundPackManager.getInstance().getPacksDirectory();
                    Util.getPlatform().openFile(packDir.toFile());
                })
                .bounds(startX, buttonY, buttonWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(this.parent);
                    }
                })
                .bounds(startX + buttonWidth + gap, buttonY, buttonWidth, 20)
                .build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 13, 0xFFFFFFFF);
    }

    private final class PackList extends ContainerObjectSelectionList<PackList.PackEntry> {
        PackList(int width, int height, int top) {
            super(BridgeSoundPackScreen.this.minecraft, width, height, top, 35);
            reloadEntries();
        }

        private void reloadEntries() {
            this.clearEntries();
            SoundPackManager.getInstance().getAvailablePacks().stream()
                    .sorted(Comparator.comparing(SoundPackInfo::displayName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(pack -> this.addEntry(new PackEntry(pack)));
        }

        @Override
        public void setSelected(PackEntry entry) {
            setSelected(entry, false);
        }

        void setSelected(PackEntry entry, boolean silent) {
            super.setSelected(entry);
            if (entry != null && !silent
                    && (entry.pack.status() == SoundPackInfo.Status.OK
                            || entry.pack.status() == SoundPackInfo.Status.EMPTY)) {
                SoundPackManager.getInstance().setActivePack(entry.pack.id());
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(320, Math.max(220, this.width - 24));
        }

        @Override
        protected int scrollBarX() {
            return this.width / 2 + getRowWidth() / 2 + 4;
        }

        final class PackEntry extends ContainerObjectSelectionList.Entry<PackEntry> {
            private final SoundPackInfo pack;
            private final Minecraft client = Minecraft.getInstance();

            PackEntry(SoundPackInfo pack) {
                this.pack = pack;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                    boolean hovered, float tickDelta) {
                int x = getContentX();
                int y = getContentY();
                int height = getContentHeight();

                Component name = Component.literal(pack.displayName());
                if (pack.isZip()) {
                    name = name.copy().append(Component.literal(" (.zip)").withStyle(ChatFormatting.GRAY));
                }

                long sampleCount = pack.availableNotes().values().stream().mapToLong(List::size).sum();
                Component status = switch (pack.status()) {
                    case OK -> Component.translatable(
                            "gui.extendednoteblock.pack_manager.status.ok", sampleCount)
                            .withStyle(ChatFormatting.GREEN);
                    case EMPTY -> Component.translatable(
                            "gui.extendednoteblock.pack_manager.status.empty")
                            .withStyle(ChatFormatting.YELLOW);
                    default -> Component.translatable(
                            "gui.extendednoteblock.pack_manager.status.invalid")
                            .withStyle(ChatFormatting.RED);
                };

                int textX = x + 10;
                if (Objects.equals(pack.id(), SoundPackManager.getInstance().getActivePackId())) {
                    graphics.text(client.font, "▶", x, y + height / 2 - 4, 0xFF55FF55);
                }

                graphics.text(client.font, name, textX, y + 4, 0xFFFFFFFF);
                graphics.text(client.font, status, textX, y + 18, 0xFFA0A0A0);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() == 0) {
                    PackList.this.setSelected(this);
                    return true;
                }
                return false;
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return Collections.emptyList();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return Collections.emptyList();
            }
        }
    }
}
