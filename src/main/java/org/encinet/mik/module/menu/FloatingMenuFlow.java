package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** Owns typed screen state, redraws and lifecycle for one player's menu flow. */
public final class FloatingMenuFlow<S> {
    private final Player player;
    private final FloatingMenuRenderer<S> renderer;
    private volatile S state;
    private FloatingMenuHandle handle;
    private boolean rendering;

    private FloatingMenuFlow(Player player, S initialState, FloatingMenuRenderer<S> renderer) {
        this.player = Objects.requireNonNull(player, "player");
        this.state = Objects.requireNonNull(initialState, "initialState");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public static <S> FloatingMenuFlow<S> open(Player player, S initialState,
                                                FloatingMenuRenderer<S> renderer) {
        FloatingMenuFlow<S> flow = new FloatingMenuFlow<>(player, initialState, renderer);
        flow.handle = FloatingMenus.open(player, flow.render());
        return flow;
    }

    public static <S> FloatingMenuFlow<S> openRoot(Player player, S initialState,
                                                   FloatingMenuRenderer<S> renderer) {
        FloatingMenuFlow<S> flow = new FloatingMenuFlow<>(player, initialState, renderer);
        flow.handle = FloatingMenus.openRoot(player, flow.render());
        return flow;
    }

    public Player player() { return player; }
    public S state() { return state; }
    public FloatingMenuHandle handle() { return handle; }

    public void setState(S next) {
        S value = Objects.requireNonNull(next, "next");
        FloatingMenus.execute(() -> {
            state = value;
            redrawNow();
        });
    }

    public void update(UnaryOperator<S> update) {
        Objects.requireNonNull(update, "update");
        FloatingMenus.execute(() -> {
            state = Objects.requireNonNull(update.apply(state), "updated state");
            redrawNow();
        });
    }

    public void redraw() {
        FloatingMenus.execute(this::redrawNow);
    }

    /** Repositions the active screen without replacing its typed state or hierarchy. */
    public void reanchor() {
        FloatingMenus.execute(() -> { if (handle != null) handle.reanchor(); });
    }

    private void redrawNow() {
        if (handle != null && (handle.state() == FloatingMenuState.CLOSED
                || handle.state() == FloatingMenuState.CLOSING)) return;
        FloatingMenuDefinition next = render();
        if (handle == null) {
            handle = FloatingMenus.open(player, next);
        } else {
            handle.update(next);
        }
    }

    /** Updates state and brings this flow forward if it is currently suspended. */
    void present(S next) {
        state = Objects.requireNonNull(next, "next");
        handle = FloatingMenus.present(player, render());
    }

    public void close() {
        FloatingMenus.execute(() -> { if (handle != null) handle.close(); });
    }

    public FloatingMenuHandle openChild(FloatingMenuDefinition definition) {
        if (handle == null || handle.state() == FloatingMenuState.CLOSED) {
            throw new IllegalStateException("Cannot open a child from a closed menu flow");
        }
        return handle.openChild(Objects.requireNonNull(definition, "definition"));
    }

    public void back() {
        FloatingMenus.execute(() -> { if (handle != null) handle.back(); });
    }

    public void feedback(Component message, FloatingMenuFeedbackKind kind) {
        FloatingMenus.execute(() -> { if (handle != null) handle.feedback(message, kind); });
    }

    private FloatingMenuDefinition render() {
        if (rendering) throw new IllegalStateException("Menu renderer cannot update state while rendering");
        rendering = true;
        try {
            return Objects.requireNonNull(renderer.render(new FloatingMenuContext<>(this, state)),
                    "renderer result");
        } finally {
            rendering = false;
        }
    }
}
