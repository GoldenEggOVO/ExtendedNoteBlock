package com.atemukesu.extendednoteblock.client.renderer;

import com.atemukesu.extendednoteblock.item.ConductorWandItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ConductorWandRenderer {
    private ConductorWandRenderer() {
    }

    public static void collectSubmits(LevelRenderContext context) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ConductorWandItem)) {
            return;
        }

        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        BlockPos pos1 = nbt.read("Pos1", BlockPos.CODEC).orElse(null);
        BlockPos pos2 = nbt.read("Pos2", BlockPos.CODEC).orElse(null);
        if (pos1 == null && pos2 == null) {
            return;
        }

        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = new PoseStack();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        if (pos1 != null) {
            submitBox(context, poseStack, new AABB(pos1), 0x66FF0000, 0xFFFF0000);
        }
        if (pos2 != null) {
            submitBox(context, poseStack, new AABB(pos2), 0x660080FF, 0xFF0080FF);
        }
        if (pos1 != null && pos2 != null) {
            AABB selection = new AABB(
                    Math.min(pos1.getX(), pos2.getX()),
                    Math.min(pos1.getY(), pos2.getY()),
                    Math.min(pos1.getZ(), pos2.getZ()),
                    Math.max(pos1.getX(), pos2.getX()) + 1,
                    Math.max(pos1.getY(), pos2.getY()) + 1,
                    Math.max(pos1.getZ(), pos2.getZ()) + 1);
            submitBox(context, poseStack, selection, 0x26FFFFFF, 0xCCFFFFFF);
        }
    }

    private static void submitBox(LevelRenderContext context, PoseStack poseStack, AABB box,
            int fillColor, int outlineColor) {
        context.submitNodeCollector().submitCustomGeometry(
                poseStack, RenderTypes.debugFilledBox(),
                (pose, vertices) -> addBoxFaces(pose, vertices, box, fillColor));
        context.submitNodeCollector().submitCustomGeometry(
                poseStack, RenderTypes.linesTranslucent(),
                (pose, vertices) -> addBoxOutline(pose, vertices, box, outlineColor));
    }

    private static void addBoxFaces(PoseStack.Pose pose, VertexConsumer vertices, AABB box, int color) {
        quad(pose, vertices, color, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ,
                box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
        quad(pose, vertices, color, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ,
                box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ);
        quad(pose, vertices, color, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ,
                box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ);
        quad(pose, vertices, color, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
                box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
        quad(pose, vertices, color, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
                box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ);
        quad(pose, vertices, color, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ,
                box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices, int color,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4) {
        vertex(pose, vertices, x1, y1, z1, color);
        vertex(pose, vertices, x2, y2, z2, color);
        vertex(pose, vertices, x3, y3, z3, color);
        vertex(pose, vertices, x4, y4, z4, color);
    }

    private static void addBoxOutline(PoseStack.Pose pose, VertexConsumer vertices, AABB box, int color) {
        line(pose, vertices, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, color);
        line(pose, vertices, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, color);
        line(pose, vertices, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
        line(pose, vertices, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, color);
        line(pose, vertices, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        line(pose, vertices, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
        line(pose, vertices, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        line(pose, vertices, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        line(pose, vertices, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, color);
        line(pose, vertices, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        line(pose, vertices, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, color);
        line(pose, vertices, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vertices,
            double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = length == 0.0F ? 0.0F : dx / length;
        float ny = length == 0.0F ? 0.0F : dy / length;
        float nz = length == 0.0F ? 0.0F : dz / length;
        vertices.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2.0F);
        vertices.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2.0F);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            double x, double y, double z, int color) {
        vertices.addVertex(pose, (float) x, (float) y, (float) z).setColor(color);
    }
}
