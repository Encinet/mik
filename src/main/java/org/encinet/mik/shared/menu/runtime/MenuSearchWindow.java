package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** One search surface remains in navigation history while the selected object opens its own screen. */
final class MenuSearchWindow<T> {
    record Labels(Component loading, Component empty, Component failed, Component retry, Component back,
                  java.util.function.BiFunction<Integer,Integer,Component> limit) { }
    private final FloatingMenuService menus;
    private final Player viewer;
    private final MenuSearch<T> definition;
    private final Labels labels;
    private final Consumer<Throwable> failure;
    private final MenuSearchState<T> state = new MenuSearchState<>();
    private final MenuQueryEditor editor;
    private final String screen = "search:" + UUID.randomUUID();
    private FloatingMenuHandle handle;
    private CompletableFuture<?> request;
    private boolean closed;

    MenuSearchWindow(FloatingMenuService menus, Player viewer, MenuSearch<T> definition, Labels labels, Consumer<Throwable> failure) {
        this.menus = menus; this.viewer = viewer; this.definition = definition; this.labels = labels; this.failure = failure;
        editor = menus.queryEditor(viewer, definition.placeholder(), definition.maximumLength(), this::search, this::redraw, () -> {});
    }

    void open() { handle = menus.present(viewer, scene()); search(""); }

    private void search(String query) {
        if (closed) return;
        long version = state.begin(query);
        if (request != null) request.cancel(false);
        redraw();
        try {
            // A copy owns cancellation without cancelling a feature's shared request.
            var next = definition.find().apply(state.query).thenApply(java.util.List::copyOf).toCompletableFuture().copy();
            request = next;
            next.whenComplete((found,error) -> menus.execute(() -> {
                if (!state.complete(version,found,error)) return;
                request = null;
                if (error != null) failure.accept(error);
                redraw();
            }));
        } catch (RuntimeException error) {
            if (state.complete(version,null,error)) { failure.accept(error); redraw(); }
        }
    }

    private MenuScene scene() {
        return scene(screen,definition,editor.content(state.query),state,labels,
                        selected -> { if (!closed) { state.page = selected; redraw(); } },
                        (_,_) -> search(state.query), (_,current) -> current.back(),
                        (player,value) -> { editor.stopInput(); definition.selected().accept(player,value); }).toBuilder()
                .lifecycle((_,current,previous,next,_) -> {
                    if (next == FloatingMenuState.CLOSING || next == FloatingMenuState.CLOSED) close();
                    else if (next == FloatingMenuState.SUSPENDED) editor.stopInput();
                    else if (next == FloatingMenuState.OPENING && previous == FloatingMenuState.SUSPENDED) {
                        handle = current; redraw();
                    }
                }).build();
    }

    static <T> MenuScene scene(String screen, MenuSearch<T> definition, UiNode query, MenuSearchState<T> state, Labels labels,
                              java.util.function.IntConsumer page, FloatingMenuCommand retry, FloatingMenuCommand back,
                              java.util.function.BiConsumer<Player,T> selected) {
        var header = Ui.column().key("header").gap(UiLength.ui(8))
                .child(UiTemplates.titleBar(Ui.heading(definition.title()).key("title").build(),
                        Ui.button(labels.back()).key("back").keepAccessible().primary(back).build())).child(query, LinearSlot.fit());
        if (state.total > state.results.size()) header.child(Ui.text(labels.limit().apply(state.results.size(),state.total))
                .key("limit").textWidth(4096).build(),LinearSlot.fit(LinearSlot.Alignment.CENTER));
        var status = new ArrayList<UiNode>();
        if (state.pending || state.failed || state.results.isEmpty()) status.add(Ui.text(state.pending ? labels.loading()
                : state.failed ? labels.failed() : labels.empty()).key("status").build());
        if (state.failed) status.add(Ui.button(labels.retry()).key("retry").primary(retry).build());
        // Capture this immutable result snapshot; later queries cannot retarget an existing row factory.
        var results = state.results;
        java.util.function.IntFunction<UiNode> item = index -> {
            T value = results.get(index);
            return Ui.button(definition.label().apply(value)).key(definition.key().apply(value))
                    .textWidth(4096).singleLine().primary((player,_) -> selected.accept(player,value)).build();
        };
        UiNode content;
        for (var notice : status) header.child(notice);
        if (definition.flowResults().isPresent()) {
            var flow = definition.flowResults().orElseThrow();
            var items = new ArrayList<UiNode>();
            for (int index = 0; index < results.size(); index++) items.add(item.apply(index));
            content = Ui.pagedFlow(flow.rows()).key("results").items(items).page(state.page, page)
                    .sizing(UiSizing.width(UiLength.ZERO, flow.maximumWidth())).build();
        } else {
            var rowTemplate = Ui.button(Component.text("M")).textWidth(4096).singleLine().primary((_, _) -> {}).build();
            content = Ui.windowedList(results.size(), rowTemplate, item).key("results")
                    .page(state.page, page).build();
        }
        double maximumWidth = definition.flowResults().map(flow -> flow.maximumWidth().value() + UiTheme.WINDOW.padding().horizontal()).orElse(4096.0);
        var pageContent = UiTemplates.focus(Ui.layout(UiLayouts.headerBody(UiLength.ui(12))).key("search-content")
                .sizing(UiSizing.width(UiLength.ZERO, UiLength.ui(maximumWidth)))
                .child(UiKey.named("header"), UiTemplates.toolbar("search-tools", header.build()).stretchingContent())
                .child(UiKey.named("results"), UiTemplates.window("search-results", content)).build(), UiKey.named("results"));
        return MenuScene.screen(screen).viewport(new UiSize(640,560)).loading(state.initializing())
                .content(pageContent).build();
    }

    private void redraw() {
        if (!closed && handle != null && (handle.state() == FloatingMenuState.ACTIVE || handle.state() == FloatingMenuState.OPENING)) handle.update(scene());
    }

    private void close() {
        if (closed) return;
        closed = true; state.close(); editor.close();
        if (request != null) request.cancel(false);
        request = null;
    }
}
