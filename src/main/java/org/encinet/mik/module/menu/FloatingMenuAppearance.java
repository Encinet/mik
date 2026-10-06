package org.encinet.mik.module.menu;

import net.kyori.adventure.text.format.TextColor;
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
    public static final TextColor MUSIC_ACCENT = TextColor.color(0x69D8E8);
    public static final TextColor RHYTHM_ACCENT = TextColor.color(0xFF8FBA);

    public static final FloatingMenuAppearance DEFAULT = builder().build();

    /** Keeps glyphs and icon motion while removing every text background. */
    public static final FloatingMenuAppearance NO_BACKGROUNDS = builder()
            .allBackgrounds(TRANSPARENT)
            .build();

    /** Translucent reading surfaces keep labels legible against varied blocks and lighting. */
    public static final FloatingMenuAppearance SPATIAL = builder()
            .titleBackground(0xB8121720)
            .elementBackground(FloatingMenuElementState.NORMAL, 0xA8181B22)
            .elementBackground(FloatingMenuElementState.DISABLED, 0xA8383B42)
            .build();

    /** A cool, shallow gateway for the few destinations on the main screen. */
    public static final FloatingMenuAppearance HUB = themed(
            0xE8132635, 0xE817242F, 0xF8203034, 0xF8212E3C, 0xFC263240);

    /** Waypoints keep the selected destination distinct from nearby homes. */
    public static final FloatingMenuAppearance WAYPOINT = themed(
            0xE8122C30, 0xE8172727, 0xF820302C, 0xF8213033, 0xFC283631);

    /** Earth tones connect land actions with the nearby selection preview. */
    public static final FloatingMenuAppearance SURVEY = themed(
            0xE81D2B22, 0xE81B2820, 0xF8243225, 0xF8253329, 0xFC2B3829);

    /** Cool blue reading surfaces for the music library and playback controls. */
    public static final FloatingMenuAppearance MUSIC = themed(
            0xE8122C38, 0xE8162730, 0xF8203D4A, 0xF8214350, 0xFC28505A);

    /** Warm pink reading surfaces for rhythm setup and results. */
    public static final FloatingMenuAppearance RHYTHM = themed(
            0xE8372033, 0xE82D202C, 0xF8462A43, 0xF84D2B48, 0xFC57314F);

    /** A restrained violet surface for other console-style controls. */
    public static final FloatingMenuAppearance CONSOLE = themed(
            0xE8232134, 0xE8211E2B, 0xF82D2838, 0xF82B2B3C, 0xFC342D40);

    /** More opaque reading cards for notices, records, and long descriptions. */
    public static final FloatingMenuAppearance ARCHIVE = themed(
            0xE82B281E, 0xE827251F, 0xF8342F26, 0xF8333029, 0xFC3C3329);

    /** Dark red confirmation surface; red and green action labels remain legible. */
    public static final FloatingMenuAppearance CAUTION = themed(
            0xE8302023, 0xE8292023, 0xF8352528, 0xF836272B, 0xFC402C2E);

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

    private static FloatingMenuAppearance themed(int title, int normal, int selected,
                                                  int hovered, int pressed) {
        return builder(SPATIAL)
                .titleBackground(title)
                .elementBackground(FloatingMenuElementState.NORMAL, normal)
                .elementBackground(FloatingMenuElementState.SELECTED, selected)
                .elementBackground(FloatingMenuElementState.HOVERED, hovered)
                .elementBackground(FloatingMenuElementState.PRESSED, pressed)
                .elementBackground(FloatingMenuElementState.DISABLED, 0xE82D3034)
                .build();
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
