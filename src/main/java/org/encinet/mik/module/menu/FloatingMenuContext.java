package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** Typed state and lifecycle surface passed to a menu renderer. */
public final class FloatingMenuContext<S> {
    private final FloatingMenuFlow<S> flow;
    private final S state;

    FloatingMenuContext(FloatingMenuFlow<S> flow, S state) {
        this.flow = flow;
        this.state = state;
    }

    public Player player() { return flow.player(); }
    public S state() { return state; }
    public int depth() { return flow.handle() == null ? 0 : Math.max(0, flow.handle().depth()); }
    public boolean canGoBack() { return depth() > 0; }

    /** Replaces state and redraws the same screen without growing navigation depth. */
    public void setState(S next) { flow.setState(next); }

    public void update(UnaryOperator<S> update) {
        flow.update(Objects.requireNonNull(update, "update"));
    }

    public void redraw() { flow.redraw(); }
    public FloatingMenuHandle openChild(FloatingMenuDefinition definition) {
        return flow.openChild(definition);
    }
    public void close() { flow.close(); }
    public void back() { flow.back(); }

    public void feedback(Component message, FloatingMenuFeedbackKind kind) {
        flow.feedback(message, kind);
    }
}
