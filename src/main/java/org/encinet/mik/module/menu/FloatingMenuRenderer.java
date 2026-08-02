package org.encinet.mik.module.menu;

@FunctionalInterface
public interface FloatingMenuRenderer<S> {
    FloatingMenuDefinition render(FloatingMenuContext<S> context);
}
