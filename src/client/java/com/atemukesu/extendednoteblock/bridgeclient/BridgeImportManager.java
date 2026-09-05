package com.atemukesu.extendednoteblock.bridgeclient;

import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/** Main-thread, stop-and-wait upload. Never sends the entire song as one payload. */
public final class BridgeImportManager {
    private static Plan active;
    private static ClientLevel world;
    private static int acknowledged, awaiting;
    private static boolean readyToSend;
    private static long lastReply;
    private static Component status = Component.empty();
    private static boolean successful;
    private BridgeImportManager() { }

    public static boolean available() {
        return Minecraft.getInstance().getConnection() != null
                && ClientPlayNetworking.canSend(BridgeClientPayloads.ImportPayload.ID);
    }
    public static boolean busy() { return active != null; }
    public static Component status() { return status; }
    public static boolean successful() { return successful; }

    public static void start(Plan plan) {
        if (busy() || !available() || Minecraft.getInstance().level == null) throw new IllegalStateException("ENB import is unavailable");
        active = plan; world = Minecraft.getInstance().level;
        acknowledged = 0; awaiting = 0; readyToSend = false; successful = false;
        lastReply = System.nanoTime();
        status = Component.translatable("gui.extendednoteblock.import.connecting");
        send(plan.begin());
    }

    public static void receive(byte[] bytes) {
        if (active == null) return;
        try {
            Status reply = ProjectionImport.decodeStatus(bytes);
            if (!reply.id().equals(active.begin().id())) return;
            lastReply = System.nanoTime();
            if (reply.stage() == ProjectionImport.REJECTED) {
                status = Component.translatable("gui.extendednoteblock.import.failed", reply.message()); active = null; return;
            }
            if (reply.total() != active.notes().size()) throw new IOException("Unexpected import size");
            switch (reply.stage()) {
                case ProjectionImport.READY, ProjectionImport.RECEIVED -> {
                    if (reply.processed() != awaiting || (reply.stage() == ProjectionImport.READY && awaiting != 0)) {
                        throw new IOException("Unexpected import acknowledgement");
                    }
                    acknowledged = reply.processed(); readyToSend = true;
                    status = Component.translatable("gui.extendednoteblock.import.uploading", acknowledged, reply.total());
                }
                case ProjectionImport.VALIDATING -> status = Component.translatable(
                        "gui.extendednoteblock.import.validating", reply.processed(), reply.total());
                case ProjectionImport.COMPLETE -> {
                    status = Component.translatable("gui.extendednoteblock.import.complete", reply.total());
                    successful = true; active = null;
                }
                default -> throw new IOException("Unexpected import status");
            }
        } catch (IOException invalid) {
            cancel(); status = Component.translatable("gui.extendednoteblock.import.failed", invalid.getMessage());
        }
    }

    public static void tick(Minecraft client) {
        if (active == null) return;
        if (world != client.level || !available() || System.nanoTime() - lastReply > 60_000_000_000L) {
            cancel(); status = Component.translatable("gui.extendednoteblock.import.interrupted"); return;
        }
        if (!readyToSend) return;
        readyToSend = false;
        if (acknowledged == active.notes().size()) {
            send(new Finish(active.begin().id()));
        } else {
            awaiting = Math.min(acknowledged + ProjectionImport.BATCH_SIZE, active.notes().size());
            send(new Batch(active.begin().id(), acknowledged, active.notes().subList(acknowledged, awaiting)));
        }
    }

    public static void cancel() {
        if (active == null) return;
        if (available() && Minecraft.getInstance().level == world) send(new Cancel(active.begin().id()));
        active = null; readyToSend = false; successful = false;
        status = Component.translatable("gui.extendednoteblock.import.interrupted");
    }
    private static void send(Packet packet) {
        ClientPlayNetworking.send(new BridgeClientPayloads.ImportPayload(ProjectionImport.encode(packet)));
    }
}
