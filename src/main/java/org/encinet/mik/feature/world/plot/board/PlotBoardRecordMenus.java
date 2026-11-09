package org.encinet.mik.feature.world.plot.board;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import org.encinet.mik.shared.util.ShutdownSequence;

/** Owns each record's navigation, asynchronous reads and retired callbacks. The board supplies business actions. */
final class PlotBoardRecordMenus implements AutoCloseable {
    @FunctionalInterface interface Loader { PlotBoardRecord load(String id, UUID viewer) throws SQLException; }
    @FunctionalInterface interface Renderer { MenuScene.Builder render(FloatingMenuContext<PlotBoardRecordState> context, Reader reader); }
    private record EntryKey(UUID viewer, String id) { }
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final LongSupplier revision;
    private final Executor executor;
    private final Loader loader;
    private final Renderer renderer;
    private final Map<EntryKey, Reader> readers = new HashMap<>();
    private volatile boolean closed;

    PlotBoardRecordMenus(JavaPlugin plugin, LanguageService language, LongSupplier revision, Executor executor, Loader loader, Renderer renderer) {
        this.plugin = plugin; this.language = language; this.revision = revision; this.executor = executor;
        this.loader = loader; this.renderer = renderer;
    }

    void open(Player player, PlotBoardRecord record) {
        if (closed) return;
        var key = new EntryKey(player.getUniqueId(), record.id());
        var reader = readers.computeIfAbsent(key, _ -> new Reader(player, key));
        var position = reader.screen.state(player).map(PlotBoardRecordState::position).orElse(UiReadingPosition.START);
        reader.cancelPending();
        var state = new PlotBoardRecordState(record, position, revision.getAsLong(), false);
        reader.screen.open(player, state);
        if (record.ownership().isEmpty()) reader.reload(state);
    }

    private PlotBoardRecord load(String id, UUID viewer) throws SQLException { return loader.load(id, viewer); }

    @Override public void close() {
        closed = true;
        var shutdown = new ShutdownSequence();
        for (var reader : List.copyOf(readers.values())) shutdown.attempt("record reader", reader::close);
        shutdown.finish("board record menus");
    }

    /** Each record keeps its own navigation lifetime, including when a related record is opened above it. */
    final class Reader {
        private final EntryKey key;
        private final Player viewer;
        private final FloatingMenuScreen<PlotBoardRecordState> screen;
        private CompletableFuture<PlotBoardRecord> pending;
        private CompletableFuture<PlotBoardRecord> related;
        private volatile boolean disposed;

        Reader(Player viewer, EntryKey key) {
            this.viewer = viewer; this.key = key;
            screen = new FloatingMenuScreen<>("plot-board-entry:" + key.id(), context -> renderer.render(context, this)
                    .lifecycle((_, _, _, after, _) -> {
                        if (after == FloatingMenuState.CLOSED) dispose();
                    }).build());
        }

        boolean current(PlotBoardRecordState source) {
            return !closed && !disposed && viewer.isOnline() && screen.flow(viewer)
                    .filter(flow -> flow.state() == source && flow.handle().state() != FloatingMenuState.CLOSED
                            && flow.handle().state() != FloatingMenuState.CLOSING).isPresent();
        }

        boolean actionable(PlotBoardRecordState source) {
            return current(source) && source.revision() == revision.getAsLong() && !source.failed() && source.source().ownership().isPresent();
        }

        FloatingMenuCommand action(PlotBoardRecordState source, FloatingMenuCommand command) {
            return (player, handle) -> { if (actionable(source)) command.execute(player, handle); };
        }

        MenuScene.NodeBuilder control(PlotBoardRecordState source, MenuScene.Builder menu, String id, Component label) {
            var node = menu.control(id, label).region("actions");
            if (source.failed() || source.source().ownership().isEmpty() || source.revision() != revision.getAsLong())
                node.disabled(Component.text(language.t(viewer, source.failed() ? Message.PLOT_BOARD_UNAVAILABLE : Message.PLOT_BOARD_LOADING), NamedTextColor.WHITE));
            return node;
        }

        void openRelated(PlotBoardRecordState source, String id) {
            if (!actionable(source) || related != null) return;
            var query = CompletableFuture.supplyAsync(() -> {
                try { return load(id, key.viewer()); }
                catch (SQLException error) { throw new java.util.concurrent.CompletionException(error); }
            }, executor);
            related = query;
            query.whenComplete((fresh, error) -> {
                if (disposed || closed) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (disposed || closed || related != query) return;
                    related = null;
                    var latest = screen.state(viewer).orElse(null);
                    if (latest == null || latest.source() != source.source() || !actionable(latest)
                            || screen.flow(viewer).filter(flow -> flow.handle().state() == FloatingMenuState.ACTIVE
                                    || flow.handle().state() == FloatingMenuState.OPENING).isEmpty()) return;
                    if (error != null) plugin.getLogger().log(Level.WARNING, "Could not read related community board record", error);
                    if (error == null && fresh != null) open(viewer, fresh);
                    else {
                        viewer.sendMessage(language.text(viewer, Message.PLOT_BOARD_RECORD_INVALID, NamedTextColor.RED));
                        screen.update(viewer, current -> current);
                    }
                });
            });
            screen.update(viewer, current -> current);
        }

        void read(FloatingMenuContext<PlotBoardRecordState> context, PlotBoardRecordState source, UiReadingPosition next) {
            if (current(source)) context.update(latest -> PlotBoardRecordState.read(latest, source, next));
        }

        void reload(PlotBoardRecordState source) {
            if (!current(source) || pending != null) return;
            long requestedRevision = revision.getAsLong();
            var query = CompletableFuture.supplyAsync(() -> {
                try { return load(key.id(), key.viewer()); }
                catch (SQLException error) { throw new java.util.concurrent.CompletionException(error); }
            }, executor);
            pending = query;
            query.whenComplete((fresh, error) -> {
                if (disposed || closed) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (disposed || closed || pending != query) return;
                    pending = null;
                    var latest = screen.state(viewer).orElse(null);
                    // Paging preserves the source snapshot; another open or refresh replaces it.
                    if (latest == null || latest.source() != source.source() || !current(latest)) return;
                    if (error != null) plugin.getLogger().log(Level.WARNING, "Could not refresh community board record", error);
                    screen.update(viewer, current -> error == null
                            ? PlotBoardRecordState.complete(current, source.source(), fresh, requestedRevision)
                            : PlotBoardRecordState.fail(current, source.source(), requestedRevision));
                });
            });
            screen.update(viewer, current -> current);
        }

        void dispose() {
            disposed = true;
            readers.remove(key, this);
            cancelPending();
        }

        void cancelPending() {
            var query = pending;
            pending = null;
            if (query != null) query.cancel(false);
            var link = related;
            related = null;
            if (link != null) link.cancel(false);
        }

        boolean loading() { return pending != null || related != null; }
        void close() { dispose(); screen.forgetAll(); }
    }

}
