package com.atemukesu.extendednoteblock.client.gui.widget;

import com.atemukesu.extendednoteblock.nbs.NbsProjectionWriter.PreviewBlock;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionWriter.PreviewBlockKind;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionWriter.ProjectionLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class NbsProjectionPreviewWidget extends AbstractWidget {
    private static final int MAX_OVERVIEW_BLOCKS = 6_000;
    private ProjectionLayout layout;
    private double yaw = -0.72;
    private double pitch = 0.58;
    private double zoom = 1.0;
    private double panX;
    private double panY;

    public NbsProjectionPreviewWidget(int x, int y, int width, int height, ProjectionLayout layout) {
        super(x, y, width, height, Component.translatable("gui.extendednoteblock.nbs.title"));
        this.layout = layout;
    }

    public void setLayout(ProjectionLayout layout) {
        this.layout = layout;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xE00A0E12);
        graphics.outline(getX(), getY(), width, height, isHovered() ? 0xFF8EA9B7 : 0xFF3B4850);
        if (layout == null || layout.blocks().isEmpty()) {
            return;
        }

        double centerX = layout.sizeX() / 2.0;
        double centerY = (layout.sizeY() - 1) / 2.0;
        double centerZ = layout.sizeZ() / 2.0;
        double fitX = Math.max(1.0, Math.hypot(layout.sizeX(), layout.sizeZ()));
        double fitY = Math.max(1.0, layout.sizeY() + Math.max(layout.sizeX(), layout.sizeZ()) * 0.42);
        double baseScale = Math.min((width - 16) / fitX, (height - 12) / fitY);
        double scale = Math.max(0.08, baseScale * zoom);
        double screenX = getX() + width / 2.0 + panX;
        double screenY = getY() + height / 2.0 + layout.sizeY() * scale * 0.12 + panY;
        int blockSize = Mth.clamp((int) Math.round(scale * 0.82), 1, 18);
        boolean detailed = blockSize >= 2;
        int step = detailed ? 1 : Math.max(1, (layout.blocks().size() + MAX_OVERVIEW_BLOCKS - 1)
                / MAX_OVERVIEW_BLOCKS);

        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        List<RenderBlock> visible = new ArrayList<>();
        for (int index = 0; index < layout.blocks().size(); index += step) {
            PreviewBlock block = layout.blocks().get(index);
            RenderBlock projected = project(block, centerX, centerY, centerZ, screenX, screenY, scale,
                    cosYaw, sinYaw, cosPitch, sinPitch);
            int margin = Math.max(3, blockSize);
            if (projected.x() >= getX() - margin && projected.x() <= getRight() + margin
                    && projected.y() >= getY() - margin && projected.y() <= getBottom() + margin) {
                visible.add(projected);
            }
        }

        visible.sort(Comparator.comparingDouble(RenderBlock::depth));
        graphics.enableScissor(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1);
        for (RenderBlock block : visible) {
            drawBlock(graphics, block, blockSize, detailed);
        }
        graphics.disableScissor();
    }

    private RenderBlock project(PreviewBlock block, double centerX, double centerY, double centerZ,
            double screenX, double screenY, double scale, double cosYaw, double sinYaw,
            double cosPitch, double sinPitch) {
        double dx = block.x() - centerX;
        double dy = block.y() - centerY;
        double dz = block.z() - centerZ;
        double rotatedX = dx * cosYaw - dz * sinYaw;
        double rotatedZ = dx * sinYaw + dz * cosYaw;
        double projectedY = dy * cosPitch - rotatedZ * sinPitch;
        double depth = dy * sinPitch + rotatedZ * cosPitch;
        return new RenderBlock(screenX + rotatedX * scale, screenY - projectedY * scale,
                depth, color(block), block.kind());
    }

    private void drawBlock(GuiGraphicsExtractor graphics, RenderBlock block, int baseSize, boolean detailed) {
        int size = switch (block.kind()) {
            case TRANSMITTER, RECEIVER -> Math.max(3, baseSize + 1);
            case LEVER -> Math.max(2, baseSize);
            default -> baseSize;
        };
        int x = (int) Math.round(block.x()) - size / 2;
        int y = (int) Math.round(block.y()) - size / 2;
        if (detailed && size >= 4) {
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xE9000000);
        }
        graphics.fill(x, y, x + size, y + size, block.color());
        if (detailed && size >= 5) {
            graphics.fill(x + 1, y + 1, x + size - 1, y + 2, brighten(block.color(), 34));
            graphics.fill(x + size - 2, y + 2, x + size - 1, y + size - 1, darken(block.color(), 42));
        }
        if (block.kind() == PreviewBlockKind.LEVER && size >= 4) {
            int center = x + size / 2;
            graphics.fill(center, y, center + 1, y + size, 0xFFF1E7C8);
        }
    }

    private static int color(PreviewBlock block) {
        return switch (block.kind()) {
            case TRANSMITTER -> 0xFFFFB52E;
            case RECEIVER -> 0xFFE0524D;
            case LEVER -> 0xFFD9D0B8;
            case NOTE_BLOCK -> pitchColor(block.midiNote());
            case INSTRUMENT -> instrumentColor(block.gmInstrument());
        };
    }

    private static int pitchColor(int midi) {
        int[] colors = { 0xFF54B8C5, 0xFF6BC489, 0xFFD3B64F, 0xFFD07B78, 0xFFB879C7, 0xFF718FD3 };
        return colors[Math.floorMod(midi, colors.length)];
    }

    private static int instrumentColor(int instrument) {
        if (instrument == 128) {
            return 0xFF8E8E8E;
        }
        int[] colors = { 0xFF7B5B3E, 0xFF936E45, 0xFF5C744B, 0xFF6E5C86, 0xFF4E6D78 };
        return colors[Math.floorMod(instrument / 8, colors.length)];
    }

    private static int brighten(int color, int amount) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, int amount) {
        int r = Math.max(0, ((color >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((color >> 8) & 0xFF) - amount);
        int b = Math.max(0, (color & 0xFF) - amount);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        setFocused(true);
        if (doubleClick) {
            zoom = 1.0;
            panX = 0.0;
            panY = 0.0;
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() == 1 || event.button() == 2) {
            panX += deltaX;
            panY += deltaY;
            return;
        }
        yaw += deltaX * 0.018;
        pitch = Mth.clamp(pitch - deltaY * 0.012, -1.25, 1.25);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        zoom = Mth.clamp(zoom * Math.pow(1.16, verticalAmount), 0.25, 32.0);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private record RenderBlock(double x, double y, double depth, int color, PreviewBlockKind kind) {
    }
}
