package com.atemukesu.extendednoteblock.nbs.vanilla;

import java.util.Locale;

public enum VanillaInstrument {
    HARP("harp", "minecraft:dirt"),
    BASS("bass", "minecraft:oak_planks"),
    BASEDRUM("basedrum", "minecraft:stone"),
    SNARE("snare", "minecraft:sand"),
    HAT("hat", "minecraft:glass"),
    GUITAR("guitar", "minecraft:white_wool"),
    FLUTE("flute", "minecraft:clay"),
    BELL("bell", "minecraft:gold_block"),
    CHIME("chime", "minecraft:packed_ice"),
    XYLOPHONE("xylophone", "minecraft:bone_block"),
    IRON_XYLOPHONE("iron_xylophone", "minecraft:iron_block"),
    COW_BELL("cow_bell", "minecraft:soul_sand"),
    DIDGERIDOO("didgeridoo", "minecraft:pumpkin"),
    BIT("bit", "minecraft:emerald_block"),
    BANJO("banjo", "minecraft:hay_block"),
    PLING("pling", "minecraft:glowstone");

    private final String id;
    private final String defaultSupportBlock;

    VanillaInstrument(String id, String defaultSupportBlock) {
        this.id = id;
        this.defaultSupportBlock = defaultSupportBlock;
    }

    public String id() {
        return id;
    }

    public String soundId() {
        return "minecraft:block.note_block." + id;
    }

    public String defaultSupportBlock() {
        return defaultSupportBlock;
    }

    public static VanillaInstrument fromNbs(int instrument) {
        if (instrument >= 0 && instrument < values().length) {
            return values()[instrument];
        }
        int gm = Math.max(0, instrument - 16);
        if (gm >= 128) return BASEDRUM;
        if (gm >= 112) return HAT;
        if (gm >= 104) return BANJO;
        if (gm >= 96) return BIT;
        if (gm >= 88) return CHIME;
        if (gm >= 80) return PLING;
        if (gm >= 72) return FLUTE;
        if (gm >= 64) return FLUTE;
        if (gm >= 56) return COW_BELL;
        if (gm >= 48) return HARP;
        if (gm >= 40) return HARP;
        if (gm >= 32) return BASS;
        if (gm >= 24) return GUITAR;
        if (gm >= 16) return PLING;
        if (gm == 14) return BELL;
        if (gm == 13 || gm == 12) return XYLOPHONE;
        if (gm == 11) return IRON_XYLOPHONE;
        if (gm >= 8) return CHIME;
        return HARP;
    }

    public static VanillaInstrument parse(String value, VanillaInstrument fallback) {
        if (value == null) return fallback;
        String normalized = value.toLowerCase(Locale.ROOT).replace("minecraft:block.note_block.", "");
        for (VanillaInstrument instrument : values()) {
            if (instrument.id.equals(normalized)) return instrument;
        }
        return fallback;
    }
}
