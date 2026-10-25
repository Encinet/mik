package org.encinet.mik.shared.menu;

@FunctionalInterface
public interface FloatingMenuRenderer<S> {
    FloatingMenuDefinition render(FloatingMenuContext<S> context);
}
