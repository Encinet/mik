package org.encinet.mik.shared.i18n;

import net.kyori.adventure.text.Component;

/** A display component with an optional numeric value for Fluent selectors. */
public record RichArg(String name, Component component, Number numericValue) {

    public static RichArg component(String name, Component component) {
        return new RichArg(name, component, null);
    }

    /** The component remains $name; $nameValue is a number, never formatted text. */
    public static RichArg number(String name, Component component, Number value) {
        java.util.Objects.requireNonNull(value, "value");
        return new RichArg(name, component, value);
    }
}
