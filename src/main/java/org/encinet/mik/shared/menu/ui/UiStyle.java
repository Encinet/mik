package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import java.util.Optional;

/** An absent background uses the theme. An explicit zero alpha remains fully transparent. */
public record UiStyle(Optional<UiColor> background, double contentAlpha, UiInsets padding,
                      double textScale, boolean controlSurface) {
    private static final UiColor PANEL_BACKGROUND = UiColor.themed(UiColorRole.WINDOW);
    public static final UiStyle PLAIN = new UiStyle(Optional.empty(), 1, UiInsets.NONE, 1, false);
    public static final UiStyle CONTROL = PLAIN.withControlSurface(true);
    public static final UiStyle PANEL = PLAIN.withBackground(PANEL_BACKGROUND)
            .withPadding(UiInsets.all(UiLength.ui(12)));

    public UiStyle {
        background = Objects.requireNonNull(background, "background");
        padding = Objects.requireNonNull(padding, "padding");
        if (!Double.isFinite(contentAlpha) || contentAlpha < 0 || contentAlpha > 1)
            throw new IllegalArgumentException("Invalid content opacity");
        if (!Double.isFinite(textScale) || textScale < .25 || textScale > 4)
            throw new IllegalArgumentException("Text scale must be between .25 and 4");
    }
    public UiStyle withBackground(UiColor value) { return new UiStyle(Optional.of(value), contentAlpha, padding, textScale, controlSurface); }
    public UiStyle withBackgroundAlpha(double value) { return withBackground(background.orElse(PANEL_BACKGROUND).withAlpha(value)); }
    public UiStyle withPadding(UiInsets value) { return new UiStyle(background, contentAlpha, value, textScale, controlSurface); }
    public UiStyle withContentAlpha(double value) { return new UiStyle(background, value, padding, textScale, controlSurface); }
    public UiStyle withTextScale(double value) { return new UiStyle(background, contentAlpha, padding, value, controlSurface); }
    public UiStyle withControlSurface(boolean value) { return new UiStyle(background, contentAlpha, padding, textScale, value); }
    public double backgroundAlpha() { return background.map(UiColor::alpha).orElse(0.0); }
    public double backgroundAlpha(UiThemeColors palette) { return background.map(color -> color.alpha(palette)).orElse(0.0); }
    public int backgroundArgb() { return backgroundArgb(UiPalette.DEFAULT); }
    public int backgroundArgb(UiThemeColors palette) { return background.map(color -> color.argb(palette)).orElse(0); }
}
