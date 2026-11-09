package org.encinet.mik.shared.menu;

import net.kyori.adventure.text.format.TextColor;
import org.encinet.mik.shared.menu.ui.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable surface treatment for the text rendered by one floating scene.
 *
 * <p>Default surfaces retain semantic palette references until presentation. Explicit
 * ARGB overrides and zero-alpha surfaces remain literal across theme changes.</p>
 */
public final class FloatingMenuAppearance {
    public static final int TRANSPARENT = 0x00000000;
    public static final TextColor MUSIC_ACCENT = UiPalette.DEFAULT.color(UiColorRole.MUSIC_ACCENT);
    public static final TextColor RHYTHM_ACCENT = UiPalette.DEFAULT.color(UiColorRole.RHYTHM_ACCENT);

    public static final FloatingMenuAppearance DEFAULT = builder().build();

    /** Keeps glyphs and icon motion while removing every text background. */
    public static final FloatingMenuAppearance NO_BACKGROUNDS = builder()
            .allBackgrounds(TRANSPARENT)
            .build();

    private final UiColor titleBackground;
    private final Map<FloatingMenuElementState, UiColor> elementBackgrounds;

    private FloatingMenuAppearance(UiColor titleBackground,
                                   Map<FloatingMenuElementState, UiColor> elementBackgrounds) {
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
        return titleBackground.argb();
    }

    public int titleBackground(UiThemeColors palette) { return titleBackground.argb(palette); }

    public int elementBackground(FloatingMenuElementState state) { return elementBackground(state, UiPalette.DEFAULT); }
    public int elementBackground(FloatingMenuElementState state, UiThemeColors palette) {
        return elementBackgrounds.get(Objects.requireNonNull(state, "state")).argb(palette);
    }

    /** Literal overrides keep their exact color; themed inputs retain feedback without losing explicit alpha. */
    public int elementBackground(FloatingMenuElementState state, UiThemeColors palette, UiStyle style) {
        Objects.requireNonNull(state, "state");
        if (style == null || style.background().isEmpty()) return elementBackground(state, palette);
        UiColor color = style.background().orElseThrow();
        if (color instanceof UiColor.Themed themed && themed.role() == UiColorRole.INPUT && state != FloatingMenuElementState.NORMAL)
            color = elementBackgrounds.get(state).withAlpha(color.alpha(palette));
        return color.argb(palette);
    }

    public static final class Builder {
        private UiColor titleBackground = UiColor.themed(UiColorRole.WINDOW);
        private final EnumMap<FloatingMenuElementState, UiColor> elementBackgrounds =
                new EnumMap<>(FloatingMenuElementState.class);

        private Builder() {
            elementBackgrounds.put(FloatingMenuElementState.NORMAL, UiColor.themed(UiColorRole.BUTTON));
            elementBackgrounds.put(FloatingMenuElementState.SELECTED, UiColor.themed(UiColorRole.BUTTON_SELECTED));
            elementBackgrounds.put(FloatingMenuElementState.HOVERED, UiColor.themed(UiColorRole.BUTTON_HOVERED));
            elementBackgrounds.put(FloatingMenuElementState.PRESSED, UiColor.themed(UiColorRole.BUTTON_PRESSED));
            elementBackgrounds.put(FloatingMenuElementState.DISABLED, UiColor.themed(UiColorRole.BUTTON_DISABLED));
        }

        private Builder(FloatingMenuAppearance base) {
            this.titleBackground = base.titleBackground;
            this.elementBackgrounds.putAll(base.elementBackgrounds);
        }

        public Builder titleBackground(int argb) {
            this.titleBackground = UiColor.fromArgb(argb);
            return this;
        }

        public Builder elementBackground(FloatingMenuElementState state, int argb) {
            elementBackgrounds.put(Objects.requireNonNull(state, "state"), UiColor.fromArgb(argb));
            return this;
        }

        public Builder allElementBackgrounds(int argb) {
            for (FloatingMenuElementState state : FloatingMenuElementState.values()) {
                elementBackgrounds.put(state, UiColor.fromArgb(argb));
            }
            return this;
        }

        public Builder allBackgrounds(int argb) {
            this.titleBackground = UiColor.fromArgb(argb);
            return allElementBackgrounds(argb);
        }

        public FloatingMenuAppearance build() {
            return new FloatingMenuAppearance(titleBackground, elementBackgrounds);
        }
    }
}
