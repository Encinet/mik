package org.encinet.mik.module.music.catalog.nbs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NbsInstrumentMappingTest {

    @Test
    void mapsAllMinecraftTrumpetOxidationStages() {
        assertEquals("minecraft:block.note_block.trumpet",
                NbsInstruments.minecraftSound(NbsInstruments.TRUMPET));
        assertEquals("minecraft:block.note_block.trumpet_exposed",
                NbsInstruments.minecraftSound(NbsInstruments.TRUMPET_EXPOSED));
        assertEquals("minecraft:block.note_block.trumpet_weathered",
                NbsInstruments.minecraftSound(NbsInstruments.TRUMPET_WEATHERED));
        assertEquals("minecraft:block.note_block.trumpet_oxidized",
                NbsInstruments.minecraftSound(NbsInstruments.TRUMPET_OXIDIZED));
    }

    @Test
    void unknownInstrumentFallsBackToHarp() {
        assertEquals("minecraft:block.note_block.harp", NbsInstruments.minecraftSound(255));
    }

    @Test
    void resolvesEnglishAndChineseTrumpetCustomInstrumentNames() {
        assertCustomInstrument(NbsInstruments.TRUMPET, "trumpet", "trumpet.ogg");
        assertCustomInstrument(NbsInstruments.TRUMPET_EXPOSED, "exposed trumpet", "custom.ogg");
        assertCustomInstrument(NbsInstruments.TRUMPET_WEATHERED, "锈蚀小号", "custom.ogg");
        assertCustomInstrument(NbsInstruments.TRUMPET_WEATHERED, "鏽蝕小號", "custom.ogg");
        assertCustomInstrument(NbsInstruments.TRUMPET_OXIDIZED, "氧化小号", "custom.ogg");
    }

    private static void assertCustomInstrument(int expected, String name, String fileName) {
        assertEquals(expected, NbsInstruments.resolve(
                NbsInstruments.TRUMPET,
                16,
                List.of(new NbsInstruments.CustomInstrument(name, fileName))
        ));
    }
}
