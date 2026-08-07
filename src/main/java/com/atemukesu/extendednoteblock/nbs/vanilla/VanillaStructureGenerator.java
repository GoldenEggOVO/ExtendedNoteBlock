package com.atemukesu.extendednoteblock.nbs.vanilla;

import com.atemukesu.extendednoteblock.nbs.NbsSong;
import com.atemukesu.extendednoteblock.nbs.vanilla.VanillaNotePlanner.Event;
import com.atemukesu.extendednoteblock.nbs.vanilla.VanillaNotePlanner.PlannedNote;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;

public final class VanillaStructureGenerator {
    private VanillaStructureGenerator() {
    }

    public static GenerationResult generate(NbsSong song, VanillaExportOptions options) {
        VanillaNotePlanner.Plan plan = VanillaNotePlanner.plan(song, options);
        return options.target() == VanillaExportOptions.Target.RAIL
                ? generateRail(plan, options)
                : generateRedstone(plan, options);
    }

    private static GenerationResult generateRedstone(VanillaNotePlanner.Plan plan,
            VanillaExportOptions options) {
        BlockStructure.Builder builder = new BlockStructure.Builder();
        List<EventPosition> positions = new ArrayList<>();
        int x = 0;
        int actualRedstoneTick = 0;
        int timingShifts = 0;

        builder.put(0, 0, 0, options.floorBlock());
        builder.put(0, 1, 0, options.circuitBlock());
        builder.put(0, 2, 0, "minecraft:lever", Map.of(
                "face", "floor", "facing", "east", "powered", "false"));

        for (int eventIndex = 0; eventIndex < plan.events().size(); eventIndex++) {
            Event event = plan.events().get(eventIndex);
            int requestedTick = toRedstoneTicks(event.step(), options.stepsPerSecond());
            if (eventIndex > 0 || requestedTick > actualRedstoneTick) {
                int delta = Math.max(3, requestedTick - actualRedstoneTick);
                if (actualRedstoneTick + delta != requestedTick) timingShifts++;
                List<Integer> delays = repeaterDelays(delta);
                for (int delay : delays) {
                    x++;
                    builder.put(x, 0, 0, options.floorBlock());
                    builder.put(x, 1, 0, "minecraft:repeater", Map.of(
                            "delay", Integer.toString(delay), "facing", "west",
                            "locked", "false", "powered", "false"));
                }
                x++;
                builder.put(x, 0, 0, options.floorBlock());
                builder.put(x, 1, 0, options.circuitBlock());
                actualRedstoneTick += delta;
            }
            placeChord(builder, x, event.notes(), options);
            positions.add(new EventPosition(x, actualRedstoneTick, event.notes().size()));
        }

        if (options.includeCommandBlock()) {
            CompoundTag command = commandBlock("setblock ~2 ~ ~ minecraft:redstone_block");
            builder.put(-2, 0, 0, options.floorBlock());
            builder.put(-2, 1, 0, "minecraft:command_block",
                    Map.of("conditional", "false", "facing", "east"), command);
            builder.put(-2, 2, 0, "minecraft:stone_button",
                    Map.of("face", "floor", "facing", "east", "powered", "false"));
        }
        BlockStructure structure = builder.build();
        return new GenerationResult(structure, plan.noteCount(), plan.shiftedNotes(), timingShifts,
                positions.isEmpty() ? 0 : positions.get(positions.size() - 1).actualTick(), positions);
    }

    private static GenerationResult generateRail(VanillaNotePlanner.Plan plan,
            VanillaExportOptions options) {
        BlockStructure.Builder builder = new BlockStructure.Builder();
        List<EventPosition> positions = new ArrayList<>();
        int previousX = -4;
        int timingShifts = 0;
        Map<Integer, Event> eventsByX = new java.util.LinkedHashMap<>();
        for (Event event : plan.events()) {
            double seconds = event.step() / (double) options.stepsPerSecond();
            int requestedX = (int) Math.round(seconds * options.railSpeedTenths() / 10.0);
            int x = Math.max(requestedX, previousX + 4);
            if (x != requestedX) timingShifts++;
            eventsByX.put(x, event);
            positions.add(new EventPosition(x, event.step(), event.notes().size()));
            previousX = x;
        }
        int endX = Math.max(8, previousX + 4);
        for (int x = 0; x <= endX; x++) {
            boolean event = eventsByX.containsKey(x);
            boolean powered = !event && x % options.poweredRailInterval() == 0;
            builder.put(x, 0, 0, powered ? "minecraft:redstone_block" : options.railBaseBlock());
            String rail = event ? "minecraft:detector_rail" : powered ? "minecraft:powered_rail" : "minecraft:rail";
            Map<String, String> properties = rail.equals("minecraft:rail")
                    ? Map.of("shape", "east_west", "waterlogged", "false")
                    : Map.of("shape", "east_west", "powered", powered ? "true" : "false", "waterlogged", "false");
            builder.put(x, 1, 0, rail, properties);
            if (event) placeChord(builder, x, eventsByX.get(x).notes(), options);
        }
        if (options.includeMinecart()) {
            CompoundTag minecart = new CompoundTag();
            minecart.putString("id", "minecraft:minecart");
            minecart.put("Motion", doubles(options.railSpeedTenths() / 200.0, 0.0, 0.0));
            builder.addEntity(0.5, 1.1, 0.5, minecart);
        }
        if (options.includeCommandBlock()) {
            double motion = options.railSpeedTenths() / 200.0;
            CompoundTag command = commandBlock("summon minecraft:minecart ~2 ~ ~ {Motion:[" + motion + "d,0.0d,0.0d]}");
            builder.put(-2, 0, 0, options.floorBlock());
            builder.put(-2, 1, 0, "minecraft:command_block",
                    Map.of("conditional", "false", "facing", "east"), command);
            builder.put(-2, 2, 0, "minecraft:stone_button",
                    Map.of("face", "floor", "facing", "east", "powered", "false"));
        }
        BlockStructure structure = builder.build();
        return new GenerationResult(structure, plan.noteCount(), plan.shiftedNotes(), timingShifts,
                plan.durationSteps(), positions);
    }

    private static void placeChord(BlockStructure.Builder builder, int x, List<PlannedNote> notes,
            VanillaExportOptions options) {
        int perSide = 15;
        for (int index = 0; index < notes.size(); index++) {
            int side = options.distribution() == VanillaExportOptions.Distribution.TWO_SIDED && index >= perSide ? -1 : 1;
            int distance = index % perSide + 1;
            int z = side * distance;
            PlannedNote note = notes.get(index);
            builder.put(x, 0, z, options.floorBlock());
            builder.put(x, 1, z, "minecraft:redstone_wire", Map.of(
                    "east", "side", "north", "side", "power", "0", "south", "side", "west", "side"));
            builder.put(x + 1, 0, z, options.floorBlock());
            builder.put(x + 1, 1, z, "minecraft:repeater", Map.of(
                    "delay", "1", "facing", "west", "locked", "false", "powered", "false"));
            builder.put(x + 2, 0, z, options.supportBlock(note.instrument()));
            builder.put(x + 2, 1, z, "minecraft:note_block", Map.of(
                    "instrument", note.instrument().id(), "note", Integer.toString(note.vanillaPitch()),
                    "powered", "false"));
        }
    }

    private static int toRedstoneTicks(int step, int stepsPerSecond) {
        return Math.max(0, (int) Math.round(step * 10.0 / stepsPerSecond));
    }

    private static List<Integer> repeaterDelays(int total) {
        int count = Math.max(3, (total + 3) / 4);
        int remaining = Math.max(total, count);
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int slotsAfter = count - i - 1;
            int value = Math.min(4, remaining - slotsAfter);
            values.add(Math.max(1, value));
            remaining -= value;
        }
        return values;
    }

    private static CompoundTag commandBlock(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:command_block");
        tag.putString("Command", value);
        tag.putByte("auto", (byte) 0);
        tag.putByte("conditionMet", (byte) 0);
        tag.putByte("powered", (byte) 0);
        tag.putString("CustomName", "{\"text\":\"ExtendedNoteBlock\"}");
        return tag;
    }

    private static ListTag doubles(double... values) {
        ListTag result = new ListTag();
        for (double value : values) result.add(DoubleTag.valueOf(value));
        return result;
    }

    public record GenerationResult(BlockStructure structure, int noteCount, int shiftedNotes,
            int timingShifts, int durationUnits, List<EventPosition> events) {
    }
    public record EventPosition(int x, int actualTick, int noteCount) {
    }
}
