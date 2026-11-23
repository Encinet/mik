package org.encinet.mik.feature.entertainment.music.jukebox;

/** The v1 tone vocabulary maps only to known note-block sounds. */
final class NoteOutputTone {
    private NoteOutputTone() { }

    static String sound(String tone) {
        return "minecraft:block.note_block." + switch (tone) {
            case "bass", "basedrum", "snare", "hat", "guitar", "flute", "bell", "chime", "xylophone",
                    "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "trumpet",
                    "trumpet_exposed", "trumpet_weathered", "trumpet_oxidized" -> tone;
            default -> "harp";
        };
    }
}
