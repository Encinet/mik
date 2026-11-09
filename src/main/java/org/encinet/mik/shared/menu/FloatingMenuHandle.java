package org.encinet.mik.shared.menu;

import net.kyori.adventure.text.Component;
import java.util.UUID;
import java.util.OptionalDouble;
import java.util.function.Function;
import org.encinet.mik.shared.menu.ui.UiKey;

/** Stable external control surface for one player's menu session. */
public interface FloatingMenuHandle {
    UUID id();

    UUID playerId();

    FloatingMenuState state();

    int depth();

    /** Horizontal aim position, from left (0) to right (1), on the activated node. */
    default OptionalDouble horizontalFraction() {
        return OptionalDouble.empty();
    }

    void refresh();

    /** Repositions this active spatial screen from the player's current view. */
    void reanchor();

    /** Reconciles a new definition into this screen without reopening its lifecycle. */
    void update(MenuScene definition);

    FloatingMenuHandle openChild(MenuScene definition);

    /** A lightweight companion retained across host redraws; rendering uses the panel's embeddable content. */
    void openPopover(UiKey source, Function<Runnable, MenuPanel> content);

    /** A static companion, optionally exclusive, with the same managed lifetime. */
    void openPopover(MenuPopover definition);

    /** Dismisses the current companion without navigating away from its host. */
    void closePopover();

    /** A late close from a replaced companion cannot dismiss its successor. */
    void closePopover(MenuPopover expected);

    void back();

    void close();

    void feedback(Component message, FloatingMenuFeedbackKind kind);
}
