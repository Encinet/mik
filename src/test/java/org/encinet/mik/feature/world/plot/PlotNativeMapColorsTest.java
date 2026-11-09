package org.encinet.mik.feature.world.plot;

import org.bukkit.map.MapPalette;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PlotNativeMapColorsTest {
    @Test void everyOpaquePaletteColorSurvivesEncodingAndUnsignedIndices() {
        int[] colors = PlotMapTestSupport.nativeColors();
        var encoder = new PlotNativeMapColors(colors);
        for (int index = 4; index < colors.length; index++) {
            if (colors[index] >>> 24 == 0) continue;
            int encoded = Byte.toUnsignedInt(encoder.encode(colors[index]));
            assertTrue(encoded >= 4);
            assertEquals(colors[index], colors[encoded]);
        }
        assertTrue(Byte.toUnsignedInt(encoder.encode(colors[200])) >= 128);
    }

    @Test void terrainColorsMatchTheCurrentColorApiWithoutProducingTransparentPixels() {
        int[] colors = PlotMapTestSupport.nativeColors();
        var encoder = new PlotNativeMapColors(colors);
        var random = new Random(0x4d494b);
        for (int sample = 0; sample < 3000; sample++) {
            int rgb = random.nextInt(1 << 24);
            int encoded = Byte.toUnsignedInt(encoder.encode(rgb));
            assertEquals(MapPalette.getNearestColor(new Color(rgb)).getRGB(), colors[encoded]);
        }
    }

    @Test void serverPaletteIsAnImmutableSnapshotAndInvalidDataIsRejected() {
        int[] colors = PlotMapTestSupport.nativeColors();
        var encoder = new PlotNativeMapColors(colors);
        int expected = colors[200];
        java.util.Arrays.fill(colors, 0);
        int encoded = Byte.toUnsignedInt(encoder.encode(expected));
        assertEquals(expected, PlotMapTestSupport.nativeColors()[encoded]);
        assertThrows(IllegalArgumentException.class, () -> new PlotNativeMapColors(new int[255]));
        assertThrows(IllegalStateException.class, () -> new PlotNativeMapColors(colors).encode(0));
    }
}
