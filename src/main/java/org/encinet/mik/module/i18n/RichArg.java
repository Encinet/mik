package org.encinet.mik.module.i18n;

import net.kyori.adventure.text.Component;

public record RichArg(String name, Component component, String fallback) {

    public static RichArg component(String name, Component component, String fallback) {
        return new RichArg(name, component, fallback);
    }
}
