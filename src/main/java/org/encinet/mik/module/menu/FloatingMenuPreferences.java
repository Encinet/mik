package org.encinet.mik.module.menu;

import java.util.Objects;

public record FloatingMenuPreferences(FloatingMenuScale layout, FloatingMenuTextScale text,
                                      FloatingMenuFieldOfView fieldOfView) {
    public static final FloatingMenuPreferences DEFAULT = new FloatingMenuPreferences(
            FloatingMenuScale.NORMAL, FloatingMenuTextScale.NORMAL, FloatingMenuFieldOfView.DEFAULT);

    public FloatingMenuPreferences(FloatingMenuScale layout, FloatingMenuTextScale text) {
        this(layout, text, FloatingMenuFieldOfView.DEFAULT);
    }

    public FloatingMenuPreferences {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(fieldOfView, "fieldOfView");
    }

    public double typographyFactor() {
        return text.factor();
    }

    public FloatingMenuPreferences withLayout(FloatingMenuScale selected) {
        return new FloatingMenuPreferences(selected, text, fieldOfView);
    }

    public FloatingMenuPreferences withText(FloatingMenuTextScale selected) {
        return new FloatingMenuPreferences(layout, selected, fieldOfView);
    }

    public FloatingMenuPreferences withFieldOfView(FloatingMenuFieldOfView selected) {
        return new FloatingMenuPreferences(layout, text, selected);
    }
}
