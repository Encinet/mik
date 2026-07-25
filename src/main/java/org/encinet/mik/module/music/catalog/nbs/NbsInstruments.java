package org.encinet.mik.module.music.catalog.nbs;

import java.util.Locale;

public final class NbsInstruments {

    public static final int HARP = 0;
    public static final int TRUMPET = 16;
    public static final int TRUMPET_EXPOSED = 17;
    public static final int TRUMPET_WEATHERED = 18;
    public static final int TRUMPET_OXIDIZED = 19;
    public static final int VANILLA_INSTRUMENT_COUNT = 20;

    private NbsInstruments() {
    }

    static int resolve(int instrument, int declaredVanillaCount,
                       java.util.List<CustomInstrument> customInstruments) {
        if (instrument < Math.min(declaredVanillaCount, VANILLA_INSTRUMENT_COUNT)) {
            return instrument;
        }
        int customIndex = instrument - declaredVanillaCount;
        if (customIndex < 0 || customIndex >= customInstruments.size()) {
            return HARP;
        }
        return fromCustomInstrument(customInstruments.get(customIndex));
    }

    public static String minecraftSound(int instrument) {
        return switch (instrument) {
            case 1 -> "minecraft:block.note_block.bass";
            case 2 -> "minecraft:block.note_block.basedrum";
            case 3 -> "minecraft:block.note_block.snare";
            case 4 -> "minecraft:block.note_block.hat";
            case 5 -> "minecraft:block.note_block.guitar";
            case 6 -> "minecraft:block.note_block.flute";
            case 7 -> "minecraft:block.note_block.bell";
            case 8 -> "minecraft:block.note_block.chime";
            case 9 -> "minecraft:block.note_block.xylophone";
            case 10 -> "minecraft:block.note_block.iron_xylophone";
            case 11 -> "minecraft:block.note_block.cow_bell";
            case 12 -> "minecraft:block.note_block.didgeridoo";
            case 13 -> "minecraft:block.note_block.bit";
            case 14 -> "minecraft:block.note_block.banjo";
            case 15 -> "minecraft:block.note_block.pling";
            case TRUMPET -> "minecraft:block.note_block.trumpet";
            case TRUMPET_EXPOSED -> "minecraft:block.note_block.trumpet_exposed";
            case TRUMPET_WEATHERED -> "minecraft:block.note_block.trumpet_weathered";
            case TRUMPET_OXIDIZED -> "minecraft:block.note_block.trumpet_oxidized";
            default -> "minecraft:block.note_block.harp";
        };
    }

    private static int fromCustomInstrument(CustomInstrument instrument) {
        String text = (instrument.name() + " " + instrument.fileName())
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (!containsAny(text, "trumpet", "小号", "小號")) {
            return HARP;
        }
        if (containsAny(text, "exposed", "斑驳", "斑駁")) {
            return TRUMPET_EXPOSED;
        }
        if (containsAny(text, "oxidized", "oxidised", "氧化")) {
            return TRUMPET_OXIDIZED;
        }
        if (containsAny(text, "weathered", "锈蚀", "鏽蝕")) {
            return TRUMPET_WEATHERED;
        }
        return TRUMPET;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    record CustomInstrument(String name, String fileName) {
    }
}
