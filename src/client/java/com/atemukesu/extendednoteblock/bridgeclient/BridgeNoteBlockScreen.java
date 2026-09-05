package com.atemukesu.extendednoteblock.bridgeclient;

import com.atemukesu.extendednoteblock.client.gui.widget.ComboBoxWidget;
import com.atemukesu.extendednoteblock.map.InstrumentMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Registry-safe Paper/Purpur editor that intentionally mirrors the Full Fabric
 * ExtendedNoteBlockScreen layout. The visual/editor code is client-only; all
 * values are still owned and validated by the Paper bridge plugin.
 */
public final class BridgeNoteBlockScreen extends Screen {
    private static final int MAX_DELAY_MS = 3_600_000;

    private final Screen parent;
    private final BlockPos pos;

    private int note;
    private int velocity;
    private int sustain;
    private int instrumentId;
    private int delayedPlayingTime;
    private int fadeInTime;
    private int fadeOutTime;

    private EditBox velocityField;
    private EditBox sustainField;
    private EditBox delayField;
    private EditBox fadeInField;
    private EditBox fadeOutField;
    private ComboBoxWidget<InstrumentOption> instrumentComboBox;
    private PianoWidget pianoWidget;
    private Component hoveredKeyText = Component.empty();
    private boolean updateSent;

    public BridgeNoteBlockScreen(Screen parent, BridgeClientPayloads.NoteEditPayload initial) {
        super(Component.translatable("block.extendednoteblock.extended_note_block"));
        this.parent = parent;
        this.pos = initial.pos();
        this.note = initial.note();
        this.velocity = initial.velocity();
        this.sustain = initial.sustainTicks();
        this.instrumentId = initial.instrumentId();
        this.delayedPlayingTime = initial.delayMs();
        this.fadeInTime = initial.fadeInTicks();
        this.fadeOutTime = initial.fadeOutTicks();
    }

    @Override
    protected void init() {
        super.init();
        final int padding = 10;
        final int wideRowSpacing = 40;
        final int pianoHeight = 70;
        final int pianoBottomMargin = 40;

        int pianoWidgetWidth = (int) (this.width * 0.9);
        int pianoWidgetX = (this.width - pianoWidgetWidth) / 2;
        int pianoWidgetY = this.height - pianoBottomMargin - pianoHeight;

        this.pianoWidget = new PianoWidget(pianoWidgetX, pianoWidgetY, pianoWidgetWidth, pianoHeight,
                newNote -> this.note = newNote,
                hoveredText -> this.hoveredKeyText = hoveredText);
        this.addRenderableWidget(this.pianoWidget);

        int topControlsX = pianoWidgetX;
        int topControlsWidth = pianoWidgetWidth;
        int currentY = padding + 20;

        List<InstrumentOption> instrumentOptions = createInstrumentOptions();
        int initialIndex = findInstrumentIndex(instrumentOptions, this.instrumentId);
        this.instrumentComboBox = new ComboBoxWidget<>(topControlsX, currentY, topControlsWidth, 20,
                instrumentOptions, initialIndex, selectedIndex -> {
                    if (selectedIndex >= 0 && selectedIndex < instrumentOptions.size()) {
                        this.instrumentId = instrumentOptions.get(selectedIndex).id();
                    }
                });
        this.addRenderableWidget(this.instrumentComboBox);

        currentY += wideRowSpacing;
        int thirdWidth = (topControlsWidth - padding) / 3;
        int velocityX = topControlsX;
        int sustainX = velocityX + thirdWidth + padding;
        int delayX = sustainX + thirdWidth + padding;

        this.velocityField = new EditBox(this.font, velocityX, currentY, thirdWidth, 20,
                Component.translatable("gui.extendednoteblock.velocity"));
        this.velocityField.setMaxLength(3);
        this.velocityField.setValue(String.valueOf(this.velocity));
        this.velocityField.setResponder(text -> this.velocity = parseInteger(text, 0, 127, this.velocity));
        this.addRenderableWidget(this.velocityField);

        this.sustainField = new EditBox(this.font, sustainX, currentY, thirdWidth, 20,
                Component.translatable("gui.extendednoteblock.sustain_ticks"));
        this.sustainField.setMaxLength(3);
        this.sustainField.setValue(String.valueOf(this.sustain));
        this.sustainField.setResponder(text -> this.sustain = parseInteger(text, 1, 400, this.sustain));
        this.addRenderableWidget(this.sustainField);

        this.delayField = new EditBox(this.font, delayX, currentY, thirdWidth - 10, 20,
                Component.translatable("gui.extendednoteblock.delay_ms"));
        this.delayField.setMaxLength(7);
        this.delayField.setValue(String.valueOf(this.delayedPlayingTime));
        this.delayField.setResponder(text -> this.delayedPlayingTime = parseInteger(
                text, 0, MAX_DELAY_MS, this.delayedPlayingTime));
        this.addRenderableWidget(this.delayField);

        currentY += wideRowSpacing + 4;
        int halfWidth = (topControlsWidth - padding) / 2;
        int fadeInX = topControlsX;
        int fadeOutX = fadeInX + halfWidth + padding;

        this.fadeInField = new EditBox(this.font, fadeInX, currentY, halfWidth, 20,
                Component.translatable("gui.extendednoteblock.fadein_time"));
        this.fadeInField.setMaxLength(3);
        this.fadeInField.setValue(String.valueOf(this.fadeInTime));
        this.fadeInField.setResponder(text -> this.fadeInTime = parseInteger(text, 0, 400, this.fadeInTime));
        this.addRenderableWidget(this.fadeInField);

        this.fadeOutField = new EditBox(this.font, fadeOutX, currentY, halfWidth, 20,
                Component.translatable("gui.extendednoteblock.fadeout_time"));
        this.fadeOutField.setMaxLength(3);
        this.fadeOutField.setValue(String.valueOf(this.fadeOutTime));
        this.fadeOutField.setResponder(text -> this.fadeOutTime = parseInteger(text, 0, 400, this.fadeOutTime));
        this.addRenderableWidget(this.fadeOutField);

        // Keep the same top-right affordance as Full Fabric. Advanced curves are
        // deliberately disabled until their Paper protocol is bridged safely.
        int advBtnWidth = 80;
        Button advanced = Button.builder(
                Component.translatable("gui.extendednoteblock.advanced_settings").withStyle(ChatFormatting.GOLD),
                ignored -> {
                }).bounds(this.width - advBtnWidth - 10, 10, advBtnWidth, 20).build();
        advanced.active = false;
        this.addRenderableWidget(advanced);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.instrumentComboBox != null && this.instrumentComboBox.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (this.pianoWidget != null && this.pianoWidget.mouseClicked(event, doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.pianoWidget != null) this.pianoWidget.isDraggingScrollbar = false;
        if (this.instrumentComboBox != null && this.instrumentComboBox.mouseReleased(event)) {
            return true;
        }
        if (this.pianoWidget != null && this.pianoWidget.mouseReleased(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.instrumentComboBox != null && this.instrumentComboBox.isDraggingScrollbar()
                && this.instrumentComboBox.mouseDragged(event, deltaX, deltaY)) {
            return true;
        }
        if (this.pianoWidget != null && this.pianoWidget.isDraggingScrollbar()
                && this.pianoWidget.mouseDragged(event, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.instrumentComboBox != null
                && this.instrumentComboBox.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (this.pianoWidget != null
                && this.pianoWidget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);

        if (this.instrumentComboBox != null) {
            context.text(this.font, Component.translatable("gui.extendednoteblock.instrument"),
                    this.instrumentComboBox.getX(), this.instrumentComboBox.getY() - 10, 0xFFA0A0A0);
        }
        if (this.velocityField != null) {
            context.text(this.font, Component.translatable("gui.extendednoteblock.velocity"),
                    this.velocityField.getX(), this.velocityField.getY() - 10, 0xFFA0A0A0);
        }
        if (this.sustainField != null) {
            context.text(this.font, Component.translatable("gui.extendednoteblock.sustain_ticks"),
                    this.sustainField.getX(), this.sustainField.getY() - 10, 0xFFA0A0A0);
            context.text(this.font, Component.translatable("gui.extendednoteblock.sustain.info"),
                    this.sustainField.getX() + 4,
                    this.sustainField.getY() + this.sustainField.getHeight() + 4, 0xFF808080);
        }
        if (this.delayField != null) {
            context.text(this.font, Component.translatable("gui.extendednoteblock.delay_ms"),
                    this.delayField.getX(), this.delayField.getY() - 10, 0xFFA0A0A0);
            context.text(this.font, Component.translatable("gui.extendednoteblock.delay_ms.info"),
                    this.delayField.getX() + 4,
                    this.delayField.getY() + this.delayField.getHeight() + 4, 0xFF808080);
        }
        if (this.fadeInField != null) {
            context.text(this.font, Component.translatable("gui.extendednoteblock.fadein_time"),
                    this.fadeInField.getX(), this.fadeInField.getY() - 10, 0xFFA0A0A0);
            context.text(this.font, Component.translatable("gui.extendednoteblock.fadein_time.info"),
                    this.fadeInField.getX() + 4,
                    this.fadeInField.getY() + this.fadeInField.getHeight() + 4, 0xFF808080);
        }
        if (this.fadeOutField != null) {
            context.text(this.font, Component.translatable("gui.extendednoteblock.fadeout_time"),
                    this.fadeOutField.getX(), this.fadeOutField.getY() - 10, 0xFFA0A0A0);
            context.text(this.font, Component.translatable("gui.extendednoteblock.fadeout_time.info"),
                    this.fadeOutField.getX() + 4,
                    this.fadeOutField.getY() + this.fadeOutField.getHeight() + 4, 0xFF808080);
        }

        context.centeredText(this.font, this.hoveredKeyText, this.width / 2, this.height - 20, 0xFFFFFFFF);
        if (this.instrumentComboBox != null) {
            this.instrumentComboBox.renderDropdownOverlay(context, mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Match Full Fabric's editor: widgets/HUD are rendered directly without
        // the vanilla container background texture.
    }

    private int parseInteger(String text, int min, int max, int defaultValue) {
        try {
            return Mth.clamp(Integer.parseInt(text), min, max);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private void sendUpdatePacket() {
        if (this.updateSent) return;
        this.updateSent = true;

        int finalNote = Mth.clamp(this.note, 0, 127);
        int finalVelocity = Mth.clamp(this.velocity, 0, 127);
        int finalSustain = Mth.clamp(this.sustain, 1, 400);
        int finalDelay = Mth.clamp(this.delayedPlayingTime, 0, MAX_DELAY_MS);
        int finalInstrument = Mth.clamp(this.instrumentId, 0, 128);
        int finalFadeIn = Mth.clamp(this.fadeInTime, 0, 400);
        int finalFadeOut = Mth.clamp(this.fadeOutTime, 0, 400);

        // Mirror the Full Fabric fade safety constraint.
        int maxAllowedSum = (finalSustain * 8) / 10;
        if (finalFadeIn >= maxAllowedSum) {
            finalFadeIn = Math.max(0, maxAllowedSum - 1);
        }
        if (finalFadeIn + finalFadeOut >= maxAllowedSum) {
            finalFadeOut = Math.max(0, maxAllowedSum - finalFadeIn - 1);
        }

        ClientPlayNetworking.send(new BridgeClientPayloads.NoteSavePayload(
                this.pos, finalNote, finalInstrument, finalVelocity, finalSustain,
                finalDelay, finalFadeIn, finalFadeOut));
    }

    @Override
    public void onClose() {
        sendUpdatePacket();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    private List<InstrumentOption> createInstrumentOptions() {
        List<InstrumentOption> options = new ArrayList<>();
        Map<Integer, String> instrumentNames = InstrumentMap.GM_INSTRUMENT_ID_TO_NAME;
        for (int i = 0; i <= 128; i++) {
            options.add(new InstrumentOption(i, instrumentNames.getOrDefault(i, "Unknown Instrument")));
        }
        return options;
    }

    private int findInstrumentIndex(List<InstrumentOption> options, int selectedInstrumentId) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id() == selectedInstrumentId) return i;
        }
        return 0;
    }

    private record InstrumentOption(int id, String name) {
        @Override
        public String toString() {
            return String.format("%d - %s", id, name);
        }
    }

    /** Full Fabric-style horizontally scrollable 128-note MIDI piano. */
    public final class PianoWidget extends AbstractWidget {
        private static final String[] NOTE_NAMES = {
                "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
        };
        private static final boolean[] IS_BLACK_KEY = {
                false, true, false, true, false, false, true, false, true, false, true, false
        };
        private static final int WHITE_KEY_COUNT = 75;
        private static final int WHITE_KEY_WIDTH = 12;
        private static final int BLACK_KEY_WIDTH = 8;

        private final Consumer<Integer> onNoteSelect;
        private final Consumer<Component> onHover;
        private final int blackKeyHeight;
        private final int totalWidth = WHITE_KEY_COUNT * WHITE_KEY_WIDTH;
        private double scrollOffset;
        public boolean isDraggingScrollbar;

        private PianoWidget(int x, int y, int width, int height,
                Consumer<Integer> onNoteSelect, Consumer<Component> onHover) {
            super(x, y, width, height, Component.empty());
            this.onNoteSelect = onNoteSelect;
            this.onHover = onHover;
            this.blackKeyHeight = (int) (height * 0.65);
            scrollToNote(BridgeNoteBlockScreen.this.note);
        }

        public boolean isDraggingScrollbar() {
            return this.isDraggingScrollbar;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && isMouseOverScrollbar(event.x(), event.y())) {
                this.isDraggingScrollbar = true;
                this.mouseDragged(event, 0.0, 0.0);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (event.button() == 0 && this.isDraggingScrollbar) {
                this.isDraggingScrollbar = false;
                return true;
            }
            return super.mouseReleased(event);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
            if (!this.isDraggingScrollbar) return super.mouseDragged(event, deltaX, deltaY);

            double contentScrollRange = Math.max(0, this.totalWidth - this.width);
            if (contentScrollRange > 0) {
                int handleWidth = Math.max(10, (int) ((double) this.width / this.totalWidth * this.width));
                double trackWidth = this.width - handleWidth;
                if (trackWidth > 0) {
                    double mouseRelativeX = event.x() - this.getX();
                    double handleTargetX = mouseRelativeX - handleWidth / 2.0;
                    double percentage = Mth.clamp(handleTargetX / trackWidth, 0.0, 1.0);
                    this.scrollOffset = percentage * contentScrollRange;
                    clampScroll();
                }
            }
            return true;
        }

        private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
            int scrollbarY = getY() + this.height + 2;
            return mouseX >= getX() && mouseX < getX() + this.width
                    && mouseY >= scrollbarY && mouseY < scrollbarY + 6;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int left = getX();
            int top = getY();
            int hoveredKey = getHoveredKey(mouseX, mouseY);
            this.onHover.accept(hoveredKey != -1
                    ? Component.translatable("gui.extendednoteblock.piano.key_info",
                            getNoteName(hoveredKey), hoveredKey)
                    : Component.empty());

            context.fill(left, top, left + this.width, top + this.height, 0xFF000000);
            context.enableScissor(left, top, left + this.width, top + this.height);

            int whiteKeyIndex = 0;
            for (int i = 0; i < 128; i++) {
                if (!IS_BLACK_KEY[i % 12]) {
                    int keyX = left + (int) (whiteKeyIndex * WHITE_KEY_WIDTH - this.scrollOffset);
                    if (keyX < left + this.width && keyX + WHITE_KEY_WIDTH > left) {
                        int color = 0xFFFFFFFF;
                        if (i == BridgeNoteBlockScreen.this.note) color = 0xFF5555FF;
                        else if (i == hoveredKey) color = 0xFFCCCCCC;
                        context.fill(keyX, top, keyX + WHITE_KEY_WIDTH, top + this.height, 0xFF000000);
                        context.fill(keyX + 1, top + 1, keyX + WHITE_KEY_WIDTH - 1,
                                top + this.height - 1, color);
                    }
                    whiteKeyIndex++;
                }
            }

            whiteKeyIndex = 0;
            for (int i = 0; i < 128; i++) {
                if (!IS_BLACK_KEY[i % 12]) {
                    whiteKeyIndex++;
                } else {
                    int keyX = left + (int) (whiteKeyIndex * WHITE_KEY_WIDTH
                            - (BLACK_KEY_WIDTH / 2.0) - this.scrollOffset);
                    if (keyX < left + this.width && keyX + BLACK_KEY_WIDTH > left) {
                        int color = 0xFF202020;
                        if (i == BridgeNoteBlockScreen.this.note) color = 0xFF0000AA;
                        else if (i == hoveredKey) color = 0xFF505050;
                        context.fill(keyX, top, keyX + BLACK_KEY_WIDTH, top + this.blackKeyHeight, color);
                    }
                }
            }
            context.disableScissor();

            whiteKeyIndex = 0;
            for (int i = 0; i < 128; i++) {
                if (i % 12 == 0) {
                    int keyX = left + (int) (whiteKeyIndex * WHITE_KEY_WIDTH - this.scrollOffset);
                    if (keyX + WHITE_KEY_WIDTH > left && keyX < left + this.width) {
                        String octaveName = getNoteName(i);
                        int textWidth = font.width(octaveName);
                        int textX = keyX + (WHITE_KEY_WIDTH - textWidth) / 2;
                        context.text(font, octaveName, textX, top + this.height - 12, 0xFF000000, false);
                    }
                }
                if (!IS_BLACK_KEY[i % 12]) whiteKeyIndex++;
            }
            renderScrollbar(context);
        }

        private void renderScrollbar(GuiGraphicsExtractor context) {
            int left = getX();
            int scrollbarY = getY() + this.height + 2;
            context.fill(left, scrollbarY, left + this.width, scrollbarY + 6, 0xFF000000);
            context.fill(left + 1, scrollbarY + 1, left + this.width - 1, scrollbarY + 5, 0xFF555555);
            if (this.totalWidth > this.width) {
                int handleWidth = Math.max(10, (int) ((double) this.width / this.totalWidth * this.width));
                int scrollableWidth = this.width - 2 - handleWidth;
                int handleX = left + 1 + (int) ((this.scrollOffset / (this.totalWidth - this.width)) * scrollableWidth);
                context.fill(handleX, scrollbarY + 1, handleX + handleWidth, scrollbarY + 5, 0xFF888888);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            int key = getHoveredKey(event.x(), event.y());
            if (key != -1) {
                this.onNoteSelect.accept(key);
                BridgeNoteBlockScreen.this.onClose();
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (isHovered()) {
                this.scrollOffset -= verticalAmount * WHITE_KEY_WIDTH * 2;
                clampScroll();
                return true;
            }
            return false;
        }

        private int getHoveredKey(double mouseX, double mouseY) {
            if (!this.isHovered()) return -1;
            int left = getX();
            int top = getY();

            int whiteKeyIndex = 0;
            for (int i = 0; i < 128; i++) {
                if (!IS_BLACK_KEY[i % 12]) {
                    whiteKeyIndex++;
                } else {
                    int keyX = left + (int) (whiteKeyIndex * WHITE_KEY_WIDTH
                            - (BLACK_KEY_WIDTH / 2.0) - this.scrollOffset);
                    if (mouseX >= keyX && mouseX < keyX + BLACK_KEY_WIDTH
                            && mouseY >= top && mouseY < top + this.blackKeyHeight) {
                        return i;
                    }
                }
            }

            whiteKeyIndex = 0;
            for (int i = 0; i < 128; i++) {
                if (!IS_BLACK_KEY[i % 12]) {
                    int keyX = left + (int) (whiteKeyIndex * WHITE_KEY_WIDTH - this.scrollOffset);
                    if (mouseX >= keyX && mouseX < keyX + WHITE_KEY_WIDTH
                            && mouseY >= top && mouseY < top + this.height) {
                        return i;
                    }
                    whiteKeyIndex++;
                }
            }
            return -1;
        }

        private void scrollToNote(int midiNote) {
            int whiteKeyIndex = 0;
            for (int i = 0; i < midiNote; i++) {
                if (!IS_BLACK_KEY[i % 12]) whiteKeyIndex++;
            }
            this.scrollOffset = whiteKeyIndex * WHITE_KEY_WIDTH - this.width / 2.0;
            clampScroll();
        }

        private void clampScroll() {
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, Math.max(0, this.totalWidth - this.width));
        }

        public static String getNoteName(int midiNote) {
            if (midiNote < 0 || midiNote > 127) return "??";
            int octave = (midiNote / 12) - 1;
            return NOTE_NAMES[midiNote % 12] + octave;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            // Match Full Fabric: piano selection itself is silent.
        }
    }
}
