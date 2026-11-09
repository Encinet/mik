package org.encinet.mik.feature.world.plot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.util.ShutdownSequence;

/** A permission choice owns its reading state; its invoking table keeps its own navigation and page. */
final class PlotAccessChoiceMenus implements AutoCloseable {
    sealed interface Target permits Setting, Subject {
        UUID plotId();
        PlotAccessPolicy.Subject subject();
        PlotPermission.Category category();
        default String scope() {
            return "plot-access-choice:" + plotId() + ":" + subject().key("") + ":" + category() + ":"
                    + (this instanceof Setting setting ? setting.permission().key() : "subject");
        }
    }
    record Setting(UUID plotId, PlotAccessPolicy.Subject subject, PlotPermission permission, PlotPermission.Category category) implements Target {
        Setting { Objects.requireNonNull(plotId); Objects.requireNonNull(subject); Objects.requireNonNull(permission); Objects.requireNonNull(category); }
    }
    record Subject(UUID plotId, PlotAccessPolicy.Subject subject, PlotPermission.Category category) implements Target {
        Subject { Objects.requireNonNull(plotId); Objects.requireNonNull(subject); Objects.requireNonNull(category); }
    }
    record State(Target target, int page, int parentPage) {
        State { Objects.requireNonNull(target); if (page < 0 || parentPage < 0) throw new IllegalArgumentException("Negative reading page"); }
        State reading(int next) { return new State(target, next, parentPage); }
        int tablePage(PlotAccessPolicy.Subject selected) { return target.subject().equals(selected) ? parentPage : 0; }
    }
    @FunctionalInterface interface Renderer { MenuScene.Builder render(FloatingMenuContext<State> context, Reader reader); }
    private record Key(UUID viewer, Target target) { }
    private final Renderer renderer;
    private final Map<Key, Reader> readers = new HashMap<>();
    private boolean closed;

    PlotAccessChoiceMenus(Renderer renderer) { this.renderer = Objects.requireNonNull(renderer); }

    void open(Player player, Target target, FloatingMenuHandle parent, int parentPage) {
        if (closed) return;
        Objects.requireNonNull(target); Objects.requireNonNull(parent);
        if (!parent.playerId().equals(player.getUniqueId())) throw new IllegalArgumentException("A choice must belong to its invoking player");
        if (parentPage < 0) throw new IllegalArgumentException("Negative invoking page");
        var key = new Key(player.getUniqueId(), target);
        var reader = readers.computeIfAbsent(key, _ -> new Reader(player, key));
        reader.parent = parent;
        int page = reader.screen.state(player).map(State::page).orElse(0);
        reader.screen.open(player, new State(target, page, parentPage));
    }

    void forget(Player player) {
        for (var reader : List.copyOf(readers.values())) if (reader.key.viewer().equals(player.getUniqueId())) reader.close();
    }

    @Override public void close() {
        closed = true;
        var shutdown = new ShutdownSequence();
        for (var reader : List.copyOf(readers.values())) shutdown.attempt("permission choices", reader::close);
        shutdown.finish("plot permission choices");
    }

    final class Reader {
        private final Player player;
        private final Key key;
        private final FloatingMenuScreen<State> screen;
        private FloatingMenuHandle parent;
        private boolean disposed;

        Reader(Player player, Key key) {
            this.player = player; this.key = key;
            screen = new FloatingMenuScreen<>(key.target().scope(), context -> renderer.render(context, this)
                    .lifecycle((_, _, _, after, _) -> {
                        if (after == FloatingMenuState.CLOSED) { disposed = true; readers.remove(key, this); }
                    }).build());
        }

        boolean current(State source) {
            return !closed && !disposed && player.isOnline() && screen.flow(player)
                    .filter(flow -> flow.state() == source && flow.handle().state() != FloatingMenuState.CLOSING
                            && flow.handle().state() != FloatingMenuState.CLOSED).isPresent();
        }

        void page(FloatingMenuContext<State> context, int page) {
            if (current(context.state())) context.setState(context.state().reading(page));
        }

        void returnTo(FloatingMenuContext<State> context, MenuScene definition) {
            if (!current(context.state()) || screen.flow(player).filter(flow -> flow.handle().state() == FloatingMenuState.ACTIVE
                    || flow.handle().state() == FloatingMenuState.OPENING).isEmpty()) return;
            if (parent != null && parent.state() != FloatingMenuState.CLOSING && parent.state() != FloatingMenuState.CLOSED)
                parent.update(definition);
            context.back();
        }

        void back(FloatingMenuContext<State> context) { if (current(context.state())) context.back(); }
        void close() { disposed = true; readers.remove(key, this); screen.forgetAll(); }
    }
}
