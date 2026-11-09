package org.encinet.mik.shared.menu;

import java.util.Objects;
import org.encinet.mik.shared.menu.ui.UiPalette;
import org.encinet.mik.shared.menu.ui.UiThemeColors;
import org.encinet.mik.shared.menu.ui.UiCustomTheme;

public record FloatingMenuPreferences(FloatingMenuScale layout, FloatingMenuTextScale text,
                                      FloatingMenuFieldOfView fieldOfView, boolean reducedMotion, UiThemeColors theme) {
    public static final FloatingMenuPreferences DEFAULT = new FloatingMenuPreferences(
            FloatingMenuScale.NORMAL, FloatingMenuTextScale.NORMAL, FloatingMenuFieldOfView.DEFAULT);

    public FloatingMenuPreferences(FloatingMenuScale layout, FloatingMenuTextScale text) {
        this(layout, text, FloatingMenuFieldOfView.DEFAULT);
    }

    public FloatingMenuPreferences(FloatingMenuScale layout, FloatingMenuTextScale text, FloatingMenuFieldOfView fieldOfView) {
        this(layout, text, fieldOfView, false);
    }

    public FloatingMenuPreferences(FloatingMenuScale layout, FloatingMenuTextScale text, FloatingMenuFieldOfView fieldOfView, boolean reducedMotion) {
        this(layout, text, fieldOfView, reducedMotion, UiPalette.DEFAULT);
    }

    public FloatingMenuPreferences {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(fieldOfView, "fieldOfView");
        Objects.requireNonNull(theme, "theme");
    }

    public FloatingMenuPreferences withTheme(UiThemeColors selected) {
        return new FloatingMenuPreferences(layout, text, fieldOfView, reducedMotion, selected);
    }

    public FloatingMenuPreferences customizeTheme() {
        return theme instanceof UiCustomTheme ? this : withTheme(UiCustomTheme.copyOf(theme));
    }

    public double typographyFactor() {
        return text.factor();
    }

    public FloatingMenuPreferences withLayout(FloatingMenuScale selected) {
        return new FloatingMenuPreferences(selected, text, fieldOfView, reducedMotion, theme);
    }

    public FloatingMenuPreferences withText(FloatingMenuTextScale selected) {
        return new FloatingMenuPreferences(layout, selected, fieldOfView, reducedMotion, theme);
    }

    public FloatingMenuPreferences withFieldOfView(FloatingMenuFieldOfView selected) {
        return new FloatingMenuPreferences(layout, text, selected, reducedMotion, theme);
    }

    public FloatingMenuPreferences withReducedMotion(boolean selected) {
        return new FloatingMenuPreferences(layout, text, fieldOfView, selected, theme);
    }
}
