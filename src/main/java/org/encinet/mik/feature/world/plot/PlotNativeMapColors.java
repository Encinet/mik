package org.encinet.mik.feature.world.plot;

import java.util.HashMap;
import java.util.Map;

/** Encodes RGB pixels using a snapshot of the current server's native map palette. */
final class PlotNativeMapColors {
    private final int[] colors;
    private final Map<Integer, Byte> cache = new HashMap<>();

    PlotNativeMapColors(int[] colors) {
        if (colors.length != 256) throw new IllegalArgumentException("A native map palette has 256 packed indices");
        this.colors = colors.clone();
    }

    byte encode(int rgb) {
        if (cache.size() >= 2048) cache.clear();
        return cache.computeIfAbsent(rgb & 0xffffff, this::nearest);
    }

    private byte nearest(int rgb) {
        int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
        int best = -1, minimum = Integer.MAX_VALUE;
        for (int index = 4; index < colors.length; index++) {
            int color = colors[index];
            if (color >>> 24 == 0) continue;
            int otherRed = color >> 16 & 255;
            int r = red - otherRed, g = green - (color >> 8 & 255), b = blue - (color & 255);
            int distance = (1024 + red + otherRed) * r * r + 2048 * g * g
                    + (1534 - red - otherRed) * b * b;
            if (distance < minimum) { minimum = distance; best = index; }
        }
        if (best < 0) throw new IllegalStateException("The native map palette has no opaque colors");
        return (byte) best;
    }
}
