package com.atemukesu.extendednoteblock.bridgeclient;

import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer;
import com.atemukesu.extendednoteblock.bridgeprotocol.SchematicTransfer.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** One bounded transfer at a time; no Litematica types, so the mod remains optional. */
public final class SchematicTransferClient {
    private static UUID id;
    private static Object world;
    private static Object owner;
    private static long lastReply;
    private static Assembly incoming;
    private static Consumer<Document> completed;
    private static Consumer<String> failed;
    private static byte[] outgoing;
    private static int offset;
    private SchematicTransferClient() { }
    public static boolean available() {
        return Minecraft.getInstance().level != null && ClientPlayNetworking.canSend(BridgeClientPayloads.SchematicPayload.ID);
    }
    public static void snapshot(Object task, List<Box> boxes, Consumer<Document> success, Consumer<String> failure) {
        if (!start(failure)) return;
        owner = task; incoming = new Assembly(id); completed = success;
        try { send(new Request(id, boxes)); }
        catch (RuntimeException invalid) { fail(invalid.getMessage()); }
    }
    public static void paste(Document document) {
        if (document.entries().isEmpty()) return;
        if (!start(SchematicTransferClient::message)) return;
        try { outgoing = SchematicTransfer.encodeDocument(document); }
        catch (RuntimeException invalid) { fail(invalid.getMessage()); }
    }
    private static boolean start(Consumer<String> failure) {
        if (id != null || !available()) { failure.accept("ENB: transfer busy or server does not support schematic metadata."); return false; }
        id = UUID.randomUUID(); world = Minecraft.getInstance().level;
        lastReply = System.nanoTime(); failed = failure; offset = 0; return true;
    }
    public static void receive(byte[] bytes) {
        if (id == null) return;
        try {
            Packet packet = SchematicTransfer.decode(bytes);
            if (!id.equals(packet.id())) return;
            lastReply = System.nanoTime();
            if (packet instanceof Result result) {
                if (!result.success()) { fail(result.message()); return; }
                if (incoming != null || outgoing == null || offset != outgoing.length) throw new IllegalArgumentException("Unexpected ENB transfer result");
                clear(); message(result.message());
            } else if (packet instanceof Part part && incoming != null) {
                incoming.add(part);
                if (incoming.complete()) {
                    Document document = incoming.document(); Consumer<Document> callback = completed;
                    clear(); callback.accept(document);
                }
            } else throw new IllegalArgumentException("Unexpected ENB transfer packet");
        } catch (Exception invalid) { fail("ENB: " + invalid.getMessage()); }
    }
    public static void tick(Minecraft client) {
        if (id == null) return;
        if (world != client.level || !available() || System.nanoTime() - lastReply > 60_000_000_000L) {
            fail("ENB: schematic transfer interrupted or timed out; metadata was not copied."); return;
        }
        if (outgoing != null && offset < outgoing.length) {
            int end = Math.min(outgoing.length, offset + SchematicTransfer.PART_SIZE);
            try { send(new Part(id, offset, outgoing.length, Arrays.copyOfRange(outgoing, offset, end))); offset = end; }
            catch (RuntimeException invalid) { fail("ENB: " + invalid.getMessage()); }
        }
    }
    public static void cancel(Object task) { if (owner == task) clear(); }
    public static void disconnect() { if (id != null) fail("ENB: disconnected during schematic transfer."); }
    private static void fail(String reason) {
        Consumer<String> callback = failed; clear(); if (callback != null) callback.accept(reason);
    }
    private static void clear() { id = null; world = null; owner = null; incoming = null; outgoing = null; completed = null; failed = null; }
    public static void message(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.sendSystemMessage(Component.literal(message));
    }
    private static void send(Packet packet) {
        ClientPlayNetworking.send(new BridgeClientPayloads.SchematicPayload(SchematicTransfer.encode(packet)));
    }
}
