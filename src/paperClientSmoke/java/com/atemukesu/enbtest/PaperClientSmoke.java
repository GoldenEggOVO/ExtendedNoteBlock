package com.atemukesu.enbtest;

import com.atemukesu.extendednoteblock.bridgeclient.BridgeClientPayloads;
import com.atemukesu.extendednoteblock.bridgeclient.BridgeNoteBlockScreen;
import com.atemukesu.extendednoteblock.bridgeclient.BridgeImportScreen;
import com.atemukesu.extendednoteblock.bridgeclient.LitematicImportReader;
import com.atemukesu.extendednoteblock.bridgeprotocol.ProjectionImport;
import com.atemukesu.extendednoteblock.client.gui.screen.NbsWorkshopScreen;
import com.atemukesu.extendednoteblock.nbs.NbsSong;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionOptions;
import com.atemukesu.extendednoteblock.nbs.NbsProjectionWriter;
import com.atemukesu.extendednoteblock.sound.StoppablePositionalSoundInstance;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import net.minecraft.nbt.*;

/** Runs only in CI's separate test mod; never included in a release artifact. */
public final class PaperClientSmoke implements ClientModInitializer {
    private boolean started;
    private boolean checked;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> started = true);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // CLIENT_STARTED precedes the initial resource reload. Wait until the
            // loading overlay closes, so broken/disabled packs cannot pass.
            if (!started || checked || client.gui.overlay() != null) return;
            checked = true;
            try {
                if (FabricLoader.getInstance().isModLoaded("extendednoteblock")) {
                    throw new AssertionError("Full mod leaked into the Paper test environment");
                }
                if (BuiltInRegistries.BLOCK.keySet().stream().anyMatch(id -> id.getNamespace().equals("extendednoteblock"))
                        || BuiltInRegistries.ITEM.keySet().stream().anyMatch(id -> id.getNamespace().equals("extendednoteblock"))) {
                    throw new AssertionError("Custom ENB registry IDs exist in Paper Client");
                }

                String visuals = "extendednoteblock_bridge_client:bridge_items";
                var repository = client.getResourcePackRepository();
                if (!repository.getSelectedIds().contains(visuals)
                        || !repository.getPack(visuals).getCompatibility().isCompatible()) {
                    throw new AssertionError("Built-in Paper item pack is disabled or incompatible");
                }
                var selector = Identifier.fromNamespaceAndPath("minecraft", "items/blaze_rod.json");
                try (var reader = client.getResourceManager().getResourceOrThrow(selector).openAsReader()) {
                    var model = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("model");
                    if (!model.get("property").getAsString().equals("minecraft:custom_model_data")) {
                        throw new AssertionError("Built-in item selectors were not loaded");
                    }
                }

                Object manager = client.getSoundManager();
                Field engineField = Arrays.stream(manager.getClass().getDeclaredFields())
                        .filter(field -> field.getType() == SoundEngine.class).findFirst().orElseThrow();
                engineField.setAccessible(true);
                Object engine = engineField.get(manager);
                Method calculatePitch = SoundEngine.class.getDeclaredMethod("calculatePitch", SoundInstance.class);
                calculatePitch.setAccessible(true);
                for (int note = 0; note <= 127; note++) {
                    float pitch = (float) Math.pow(2, (note - 60) / 12.0);
                    assertFloat(pitch, (float) calculatePitch.invoke(engine, sound("extendednoteblock", pitch)));
                    assertFloat(Math.max(.5f, Math.min(2f, pitch)),
                            (float) calculatePitch.invoke(engine, sound("minecraft", pitch)));
                }
                Method attenuation = Arrays.stream(SoundEngine.class.getDeclaredMethods())
                        .filter(method -> method.getName().contains("useFullAttenuationDistance"))
                        .findFirst().orElseThrow();
                attenuation.setAccessible(true);
                assertFloat(48, (float) attenuation.invoke(engine, 16f, sound("extendednoteblock", 1)));
                assertFloat(16, (float) attenuation.invoke(engine, 16f, sound("minecraft", 1)));

                client.gui.setScreen(new BridgeNoteBlockScreen(null,
                        new BridgeClientPayloads.NoteEditPayload(BlockPos.ZERO, 60, 40, 100, 20, 0, 0, 3)));
                if (!(client.gui.screen() instanceof BridgeNoteBlockScreen)) {
                    throw new AssertionError("Paper note editor did not open");
                }
                checkLitematicImport();
                client.gui.setScreen(new NbsWorkshopScreen(null));
                String importLabel = net.minecraft.network.chat.Component.translatable("gui.extendednoteblock.import.title").getString();
                if (client.gui.screen().children().stream().noneMatch(child -> child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals(importLabel))) throw new AssertionError("Workshop has no restore button");
                client.gui.setScreen(new BridgeImportScreen(null));
                if (!(client.gui.screen() instanceof BridgeImportScreen)) throw new AssertionError("Restore screen did not open");
                System.out.println("ENB_PAPER_CLIENT_SMOKE_OK: startup, resources, GUI, vanilla registries, MIDI 0-127 pitch, attenuation, Litematic export/import and bridge wire codecs");
                System.exit(0);
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static SoundInstance sound(String namespace, float pitch) {
        return new StoppablePositionalSoundInstance(SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(namespace, "smoke_test")), SoundSource.RECORDS, 1, pitch, BlockPos.ZERO);
    }

    private static void checkLitematicImport() throws Exception {
        var song = new NbsSong(5, 16, 21, 1, "Import smoke", "Test", "", "", 10.0, 4,
                NbsSong.LoopSettings.NONE,
                List.of(new NbsSong.Note(0, 0, 0, 39, 100, 100, -25),
                        new NbsSong.Note(20, 0, 5, 51, 80, 100, 50)),
                List.of(NbsSong.Layer.defaults(0)), List.of());
        var options = new NbsProjectionOptions(0, 1, 1, 20, 4, NbsProjectionOptions.OctaveRange.SIX_OCTAVES, true, 0);
        var directory = Files.createTempDirectory("enb-import-smoke");
        var file = NbsProjectionWriter.write(song, options, directory.resolve("song.litematic"), "Test").output();
        try {
            var source = LitematicImportReader.read(file);
            var planned = NbsProjectionWriter.plan(song, options);
            if (source.notes().size() != planned.size()) throw new AssertionError("Export/import lost notes");
            for (int i = 0; i < planned.size(); i++) {
                var expected = planned.get(i); var actual = source.notes().get(i);
                if (actual.midi() != expected.midiNote() || actual.instrument() != expected.gmInstrument()
                        || actual.velocity() != expected.velocity() || actual.pitchCents() != expected.pitchCents()
                        || actual.delayMs() != expected.delayMs() || actual.sustain() != 20) {
                    throw new AssertionError("Export/import changed note parameters");
                }
            }
            var destination = new ProjectionImport.Pos(-120, 64, -200);
            var placed = source.place(destination, 1, 1);
            if (!placed.begin().receiver().equals(new ProjectionImport.Pos(-120, 64, -201))) {
                throw new AssertionError("Mirrored/rotated receiver is misaligned");
            }
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            CompoundTag metadata = (CompoundTag) root.get("ExtendedNoteBlockBridge");
            for (Tag entry : (ListTag) metadata.get("Notes")) {
                ((CompoundTag) entry).remove("FadeInTicks"); ((CompoundTag) entry).remove("FadeOutTicks");
            }
            NbtIo.writeCompressed(root, file);
            if (LitematicImportReader.read(file).notes().stream().anyMatch(n -> n.fadeIn() != 0 || n.fadeOut() != 0)) {
                throw new AssertionError("2.8.x metadata compatibility failed");
            }
            byte[] begin = ProjectionImport.encode(placed.begin());
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                BridgeClientPayloads.ImportPayload.CODEC.encode(buffer, new BridgeClientPayloads.ImportPayload(begin));
                byte[] raw = new byte[buffer.readableBytes()]; buffer.getBytes(0, raw);
                if (!Arrays.equals(begin, raw)) throw new AssertionError("Fabric added a length prefix to the Paper wire payload");
                if (!placed.begin().equals(ProjectionImport.decode(BridgeClientPayloads.ImportPayload.CODEC.decode(buffer).bytes()))) {
                    throw new AssertionError("Fabric/Paper import wire mismatch");
                }
                buffer.clear();
                var status = new ProjectionImport.Status(placed.begin().id(), ProjectionImport.COMPLETE, 2, 2, "已完成");
                buffer.writeBytes(ProjectionImport.encodeStatus(status));
                if (!status.equals(ProjectionImport.decodeStatus(BridgeClientPayloads.ImportStatusPayload.CODEC.decode(buffer).bytes()))) {
                    throw new AssertionError("Paper/Fabric status wire mismatch");
                }
            } finally { buffer.release(); }
            root.remove("ExtendedNoteBlockBridge"); NbtIo.writeCompressed(root, file);
            try { LitematicImportReader.read(file); throw new AssertionError("Metadata-free schematic accepted"); }
            catch (java.io.IOException expected) { /* Ordinary Litematics cannot recover missing musical data. */ }
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
    }

    private static void assertFloat(float expected, float actual) {
        if (Math.abs(expected - actual) > 0.00001f) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
