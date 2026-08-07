package com.atemukesu.extendednoteblock.nbs.vanilla;

import java.util.EnumMap;
import java.util.Map;

public record VanillaExportOptions(
        Target target,
        StructureFileWriter.Format format,
        Distribution distribution,
        PitchMode pitchMode,
        int transpose,
        int musicSpeedPercent,
        int stepsPerSecond,
        int railSpeedTenths,
        int poweredRailInterval,
        boolean includeCommandBlock,
        boolean includeMinecart,
        boolean loop,
        boolean sharedPlayback,
        String floorBlock,
        String circuitBlock,
        String railBaseBlock,
        String namespace,
        String soundCategory,
        Map<VanillaInstrument, String> supportBlocks) {

    public VanillaExportOptions {
        target = target == null ? Target.REDSTONE : target;
        format = format == null ? StructureFileWriter.Format.LITEMATIC : format;
        distribution = distribution == null ? Distribution.TWO_SIDED : distribution;
        pitchMode = pitchMode == null ? PitchMode.OCTAVE_FOLD : pitchMode;
        transpose = Math.max(-48, Math.min(48, transpose));
        musicSpeedPercent = Math.max(10, Math.min(400, musicSpeedPercent));
        stepsPerSecond = stepsPerSecond == 4 || stepsPerSecond == 5 || stepsPerSecond == 10
                ? stepsPerSecond : 10;
        railSpeedTenths = Math.max(10, Math.min(80, railSpeedTenths));
        poweredRailInterval = Math.max(1, Math.min(32, poweredRailInterval));
        floorBlock = validBlock(floorBlock, "minecraft:stone");
        circuitBlock = validBlock(circuitBlock, "minecraft:smooth_stone");
        railBaseBlock = validBlock(railBaseBlock, "minecraft:stone");
        namespace = validNamespace(namespace);
        soundCategory = validCategory(soundCategory);
        EnumMap<VanillaInstrument, String> mappings = new EnumMap<>(VanillaInstrument.class);
        for (VanillaInstrument instrument : VanillaInstrument.values()) {
            mappings.put(instrument, validBlock(supportBlocks == null ? null : supportBlocks.get(instrument),
                    instrument.defaultSupportBlock()));
        }
        supportBlocks = Map.copyOf(mappings);
    }

    public static VanillaExportOptions defaults(Target target) {
        return new VanillaExportOptions(target, StructureFileWriter.Format.LITEMATIC,
                Distribution.TWO_SIDED, PitchMode.OCTAVE_FOLD, 0, 100, 10, 80, 8,
                true, true, false, false, "minecraft:stone", "minecraft:smooth_stone",
                "minecraft:stone", "extendednoteblock_music", "record", Map.of());
    }

    public String supportBlock(VanillaInstrument instrument) {
        return supportBlocks.getOrDefault(instrument, instrument.defaultSupportBlock());
    }

    private static String validBlock(String value, String fallback) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return fallback;
        return value;
    }

    private static String validNamespace(String value) {
        if (value == null) return "extendednoteblock_music";
        String normalized = value.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
        return normalized.isBlank() ? "extendednoteblock_music" : normalized;
    }

    private static String validCategory(String value) {
        if (value == null) return "record";
        return switch (value) {
            case "master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice" -> value;
            default -> "record";
        };
    }

    public enum Target { REDSTONE, RAIL, DATAPACK;
        public Target next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public enum Distribution { ONE_SIDED, TWO_SIDED;
        public Distribution next() { return this == ONE_SIDED ? TWO_SIDED : ONE_SIDED; }
    }
    public enum PitchMode { OCTAVE_FOLD, CLAMP;
        public PitchMode next() { return this == OCTAVE_FOLD ? CLAMP : OCTAVE_FOLD; }
    }
}
