#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src" / "client" / "java"

for path in CLIENT.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    original = text

    # Minecraft 26.2 moved screen ownership to Minecraft.gui.
    text = re.sub(r"(?<!\.gui)\.setScreen\(", ".gui.setScreen(", text)
    text = text.replace("minecraft.screen", "minecraft.gui.screen()")
    text = text.replace("this.minecraft.screen", "this.minecraft.gui.screen()")
    text = text.replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()")
    text = text.replace("context.client().screen", "context.client().gui.screen()")

    # Toast manager moved to Minecraft.gui.
    text = text.replace(".getToastManager()", ".gui.toastManager()")

    # ChatFormatting#getColor was removed in 26.2. Use the vanilla legacy red ARGB value.
    text = text.replace("ChatFormatting.RED.getColor()", "0xFFFF5555")

    # The Paper bridge client must not initialize the full content mod just to
    # obtain the namespace constant. Keep SoundPackManager registry-independent.
    if path.name == "SoundPackManager.java":
        text = text.replace("import com.atemukesu.extendednoteblock.ExtendedNoteBlock;\n", "")
        text = text.replace("ExtendedNoteBlock.MOD_ID", '"extendednoteblock"')

    if text != original:
        path.write_text(text, encoding="utf-8")
        print(f"patched {path.relative_to(ROOT)}")

# The NBS projection writer is shared by Full Fabric and the registry-safe Paper
# Client. Full Fabric may write real extendednoteblock:* states, but those IDs do
# not exist in a Paper client and Litematica resolves them to air. Keep a single
# writer with a runtime Paper-safe branch: Paper exports vanilla carriers while
# retaining ENB note/controller metadata in an ignored root NBT tag for future
# re-import/restore workflows.
projection_writer = ROOT / "src" / "main" / "java" / "com" / "atemukesu" / "extendednoteblock" / "nbs" / "NbsProjectionWriter.java"
if projection_writer.exists():
    text = projection_writer.read_text(encoding="utf-8")
    original = text

    # Remove the one otherwise unnecessary dependency on a custom block entity.
    text = text.replace(
        "import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;\n",
        "",
    )
    text = text.replace("ExtendedNoteBlockEntity.MAX_DELAY_MS", "3_600_000L")

    # Runtime edition detection is registry-neutral and available in both Fabric editions.
    if "import net.fabricmc.loader.api.FabricLoader;\n" not in text:
        text = text.replace(
            "import net.minecraft.SharedConstants;\n",
            "import net.fabricmc.loader.api.FabricLoader;\nimport net.minecraft.SharedConstants;\n",
            1,
        )

    class_anchor = "public final class NbsProjectionWriter {\n    private static final int MAX_PROJECTED_NOTES = 75_000;\n"
    class_replacement = (
        "public final class NbsProjectionWriter {\n"
        "    private static final int MAX_PROJECTED_NOTES = 75_000;\n"
        "    private static final boolean PAPER_SAFE_EXPORT = detectPaperSafeExport();\n"
    )
    if "PAPER_SAFE_EXPORT" not in text:
        if class_anchor not in text:
            raise SystemExit("Could not find NbsProjectionWriter class anchor")
        text = text.replace(class_anchor, class_replacement, 1)

    constructor_anchor = """    private NbsProjectionWriter() {\n    }\n\n"""
    helper_block = r'''    private NbsProjectionWriter() {
    }

    private static boolean detectPaperSafeExport() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            return loader.isModLoaded("extendednoteblock_bridge_client")
                    && !loader.isModLoaded("extendednoteblock");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String exportTransmitterBlock() {
        return PAPER_SAFE_EXPORT
                ? "minecraft:red_concrete"
                : "extendednoteblock:global_redstone_transmitter";
    }

    private static Map<String, String> exportTransmitterProperties() {
        return PAPER_SAFE_EXPORT ? Map.of() : Map.of("powered", "false");
    }

    private static String exportProjectionReceiverBlock() {
        return PAPER_SAFE_EXPORT
                ? "minecraft:purple_concrete"
                : "extendednoteblock:nbs_projection_receiver";
    }

    private static Map<String, String> exportProjectionReceiverProperties() {
        return PAPER_SAFE_EXPORT ? Map.of() : Map.of("powered", "false");
    }

    private static String exportNoteBlock() {
        return PAPER_SAFE_EXPORT ? "minecraft:note_block" : "extendednoteblock:extended_note_block";
    }

    private static Map<String, String> exportNoteProperties(int midiNote) {
        if (PAPER_SAFE_EXPORT) {
            // Vanilla note_block only exposes 25 note states. This state is a
            // visual/clipboard fallback; full MIDI data is retained separately
            // in ExtendedNoteBlockBridge metadata below.
            int vanillaNote = clamp(midiNote - 54, 0, 24);
            return Map.of(
                    "instrument", "harp",
                    "note", Integer.toString(vanillaNote),
                    "powered", "false");
        }
        return Map.of("pitch", pitchName(midiNote), "powered", "false");
    }

'''
    if "private static boolean detectPaperSafeExport" not in text:
        if constructor_anchor not in text:
            raise SystemExit("Could not find NbsProjectionWriter constructor anchor")
        text = text.replace(constructor_anchor, helper_block, 1)

    text = text.replace(
        'palette.id("extendednoteblock:global_redstone_transmitter", Map.of("powered", "false"))',
        'palette.id(exportTransmitterBlock(), exportTransmitterProperties())',
    )
    text = text.replace(
        'palette.id("extendednoteblock:nbs_projection_receiver", Map.of("powered", "false"))',
        'palette.id(exportProjectionReceiverBlock(), exportProjectionReceiverProperties())',
    )
    text = text.replace(
        'palette.id("extendednoteblock:extended_note_block",\n                            Map.of("pitch", pitchName(note.midiNote()), "powered", "false"))',
        'palette.id(exportNoteBlock(), exportNoteProperties(note.midiNote()))',
    )

    receiver_entity = """        tileEntities.add(createProjectionReceiverEntity(\n                controllerX + 1, 0, controllerZ, projectedNotes, options.sustainTicks()));\n"""
    receiver_entity_safe = """        if (!PAPER_SAFE_EXPORT) {\n            tileEntities.add(createProjectionReceiverEntity(\n                    controllerX + 1, 0, controllerZ, projectedNotes, options.sustainTicks()));\n        }\n"""
    if receiver_entity in text:
        text = text.replace(receiver_entity, receiver_entity_safe, 1)

    note_entity = """            tileEntities.add(createNoteBlockEntity(x, y, z, note, options.sustainTicks()));\n"""
    note_entity_safe = """            if (!PAPER_SAFE_EXPORT) {\n                tileEntities.add(createNoteBlockEntity(x, y, z, note, options.sustainTicks()));\n            }\n"""
    if note_entity in text:
        text = text.replace(note_entity, note_entity_safe, 1)

    root_anchor = """        CompoundTag root = createRoot(song, author, sizeX, sizeY, sizeZ, blocks.length,\n                projectedNotes.size() * 2 + 3, palette, packedStates, tileEntities);\n\n"""
    root_replacement = """        CompoundTag root = createRoot(song, author, sizeX, sizeY, sizeZ, blocks.length,\n                projectedNotes.size() * 2 + 3, palette, packedStates, tileEntities);\n        if (PAPER_SAFE_EXPORT) {\n            root.put("ExtendedNoteBlockBridge", createPaperBridgeMetadata(\n                    projectedNotes, options.sustainTicks(), columns, controllerX, controllerZ));\n        }\n\n"""
    if 'root.put("ExtendedNoteBlockBridge"' not in text:
        if root_anchor not in text:
            raise SystemExit("Could not find NbsProjectionWriter root anchor")
        text = text.replace(root_anchor, root_replacement, 1)

    metadata_anchor = """    private static CompoundTag curvePoint(float time, float value) {\n"""
    metadata_helper = r'''    private static CompoundTag createPaperBridgeMetadata(List<ProjectedNote> notes,
            int sustainTicks, int columns, int controllerX, int controllerZ) {
        CompoundTag bridge = new CompoundTag();
        bridge.putInt("FormatVersion", 1);
        bridge.putString("Mode", "paper-safe-carriers");
        bridge.putInt("SustainTicks", sustainTicks);
        bridge.putInt("Columns", columns);
        bridge.put("TransmitterPos", positionTag(controllerX, 0, controllerZ));
        bridge.put("ProjectionReceiverPos", positionTag(controllerX + 1, 0, controllerZ));

        int notesPerLayer = columns * columns;
        ListTag noteList = new ListTag();
        for (int index = 0; index < notes.size(); index++) {
            ProjectedNote note = notes.get(index);
            int layer = index / notesPerLayer;
            int local = index % notesPerLayer;
            int row = local / columns;
            int rowColumn = local % columns;
            int column = (row & 1) == 0 ? rowColumn : columns - 1 - rowColumn;
            int x = 2 + column * 3;
            int y = 3 + layer * 3;
            int z = 3 + row * 3;

            CompoundTag entry = positionTag(x, y, z);
            entry.putInt("MidiNote", note.midiNote());
            entry.putInt("Instrument", note.gmInstrument());
            entry.putInt("Velocity", note.velocity());
            entry.putInt("SustainTicks", sustainTicks);
            entry.putInt("PitchCents", note.pitchCents());
            entry.putLong("DelayMs", note.delayMs());
            noteList.add(entry);
        }
        bridge.put("Notes", noteList);
        return bridge;
    }

'''
    if "private static CompoundTag createPaperBridgeMetadata" not in text:
        if metadata_anchor not in text:
            raise SystemExit("Could not find NbsProjectionWriter metadata insertion anchor")
        text = text.replace(metadata_anchor, metadata_helper + metadata_anchor, 1)

    if text != original:
        projection_writer.write_text(text, encoding="utf-8")
        print(f"patched {projection_writer.relative_to(ROOT)}")

# Minecraft#setScreen no longer exists in 26.2, so this old mixin target cannot be applied.
# The pre-launch check is non-essential; disable only this mixin until it is reimplemented on Gui#setScreen.
mixins = ROOT / "src" / "client" / "resources" / "extendednoteblock.client.mixins.json"
if mixins.exists():
    text = mixins.read_text(encoding="utf-8")
    original = text
    text = re.sub(r'\s*"MinecraftClientMixin",?\n', "\n", text)
    # Repair a possible missing comma after removing the entry.
    text = text.replace('"OptionsScreenMixin"\n\t\t"SoundEventMixin"', '"OptionsScreenMixin",\n\t\t"SoundEventMixin"')
    if text != original:
        mixins.write_text(text, encoding="utf-8")
        print(f"patched {mixins.relative_to(ROOT)}")
