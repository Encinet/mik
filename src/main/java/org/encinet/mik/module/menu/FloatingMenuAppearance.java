package org.encinet.mik.module.menu;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable surface treatment for the text rendered by one floating scene.
 *
 * <p>ARGB values are kept as values rather than renderer constants so a scene
 * can choose opaque cards, translucent feedback, or no surface at all. A zero
 * alpha value is fully transparent.</p>
 */
public final class FloatingMenuAppearance {
    public static final int TRANSPARENT = 0x00000000;

    public static final FloatingMenuAppearance DEFAULT = builder().build();

    /** Keeps glyphs and icon motion while removing every text background. */
    public static final FloatingMenuAppearance NO_BACKGROUNDS = builder()
            .allBackgrounds(TRANSPARENT)
            .build();

    /**
     * Removes passive text surfaces while retaining visible hover, hold, and
     * press feedback for interactive controls.
     */
    public static final FloatingMenuAppearance SPATIAL = builder()
            .titleBackground(TRANSPARENT)
            .elementBackground(FloatingMenuElementState.NORMAL, TRANSPARENT)
            .elementBackground(FloatingMenuElementState.DISABLED, TRANSPARENT)
            .build();

    private final int titleBackground;
    private final Map<FloatingMenuElementState, Integer> elementBackgrounds;

    private FloatingMenuAppearance(int titleBackground,
                                   Map<FloatingMenuElementState, Integer> elementBackgrounds) {
        this.titleBackground = titleBackground;
        this.elementBackgrounds = Map.copyOf(elementBackgrounds);
        for (FloatingMenuElementState state : FloatingMenuElementState.values()) {
            if (!this.elementBackgrounds.containsKey(state)) {
                throw new IllegalArgumentException("Missing background for " + state);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(FloatingMenuAppearance base) {
        return new Builder(Objects.requireNonNull(base, "base"));
    }

    public int titleBackground() {
        return titleBackground;
    }

    public int elementBackground(FloatingMenuElementState state) {
        return elementBackgrounds.get(Objects.requireNonNull(state, "state"));
    }

    public static final class Builder {
        private int titleBackground = 0xD0121720;
        private final EnumMap<FloatingMenuElementState, Integer> elementBackgrounds =
                new EnumMap<>(FloatingMenuElementState.class);

        private Builder() {
            elementBackgrounds.put(FloatingMenuElementState.NORMAL, 0xB0181B22);
            elementBackgrounds.put(FloatingMenuElementState.SELECTED, 0xD02B7A58);
            elementBackgrounds.put(FloatingMenuElementState.HOVERED, 0xDB20638A);
            elementBackgrounds.put(FloatingMenuElementState.PRESSED, 0xE055AAFF);
            elementBackgrounds.put(FloatingMenuElementState.DISABLED, 0x80383B42);
        }

        private Builder(FloatingMenuAppearance base) {
            this.titleBackground = base.titleBackground;
            this.elementBackgrounds.putAll(base.elementBackgrounds);
        }

        public Builder titleBackground(int argb) {
            this.titleBackground = argb;
            return this;
        }

        public Builder elementBackground(FloatingMenuElementState state, int argb) {
            elementBackgrounds.put(Objects.requireNonNull(state, "state"), argb);
            return this;
        }

        public Builder allElementBackgrounds(int argb) {
            for (FloatingMenuElementState state : FloatingMenuElementState.values()) {
                elementBackgrounds.put(state, argb);
            }
            return this;
        }

        public Builder allBackgrounds(int argb) {
            this.titleBackground = argb;
            return allElementBackgrounds(argb);
        }

        public FloatingMenuAppearance build() {
            return new FloatingMenuAppearance(titleBackground, elementBackgrounds);
        }
    }
}
