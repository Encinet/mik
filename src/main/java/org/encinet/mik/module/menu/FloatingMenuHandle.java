package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import java.util.UUID;

/** Stable external control surface for one player's menu session. */
public interface FloatingMenuHandle {
    UUID id();

    UUID playerId();

    FloatingMenuState state();

    int depth();

    void refresh();

    /** Repositions this active spatial screen from the player's current view. */
    void reanchor();

    /** Reconciles a new definition into this screen without reopening its lifecycle. */
    void update(FloatingMenuDefinition definition);

    FloatingMenuHandle openChild(FloatingMenuDefinition definition);

    void back();

    void close();

    void feedback(Component message, FloatingMenuFeedbackKind kind);
}
