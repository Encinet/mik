package org.encinet.mik.shared.menu;

@FunctionalInterface
public interface FloatingMenuRenderer<S> {
    MenuScene render(FloatingMenuContext<S> context);
}
