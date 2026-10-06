package org.encinet.mik.module.menu;

import java.util.List;
import java.util.stream.IntStream;

public record FloatingMenuFieldOfView(int degrees) {
    public static final int MINIMUM = 30;
    public static final int MAXIMUM = 110;
    public static final FloatingMenuFieldOfView DEFAULT = new FloatingMenuFieldOfView(70);
    public static final List<FloatingMenuFieldOfView> PRESETS = IntStream.rangeClosed(3, 11)
            .map(value -> value * 10).mapToObj(FloatingMenuFieldOfView::new).toList();

    public FloatingMenuFieldOfView {
        if (degrees < MINIMUM || degrees > MAXIMUM) throw new IllegalArgumentException("FOV must be between 30 and 110");
    }

    public FloatingMenuFraming readingFraming() {
        return new FloatingMenuFraming(62, degrees / 2.0);
    }

    public double projectionFactor() {
        return Math.tan(Math.toRadians(degrees / 2.0)) / Math.tan(Math.toRadians(DEFAULT.degrees() / 2.0));
    }

    public FloatingMenuFieldOfView step(int direction) {
        return new FloatingMenuFieldOfView(Math.clamp(degrees + Integer.signum(direction) * 5, MINIMUM, MAXIMUM));
    }

    public static FloatingMenuFieldOfView fromDegrees(int degrees) {
        return degrees < MINIMUM || degrees > MAXIMUM ? DEFAULT : new FloatingMenuFieldOfView(degrees);
    }
}
