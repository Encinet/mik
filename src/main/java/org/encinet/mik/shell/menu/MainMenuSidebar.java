package org.encinet.mik.shell.menu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.util.ShutdownSequence;

/** Ordered, independently sized widgets shared by the left and right sides of the launcher. */
final class MainMenuSidebar implements MenuPanel {
    static final double MAX_WIDTH = 300;
    private final String id;
    private final Runnable redraw;
    private int page;
    private final List<Widget> widgets;
    private boolean closed;

    /** Panels supply content and lifecycle; refresh belongs to this host or an explicit redraw callback. */
    record Widget(String id, MenuPanel panel, Supplier<?> revision) {
        Widget { UiKey.named(id); Objects.requireNonNull(panel); Objects.requireNonNull(revision); }

        /** Static and callback-driven widgets need no polling token of their own. */
        Widget(String id, MenuPanel panel) { this(id, panel, () -> null); }
    }

    MainMenuSidebar(String id, Runnable redraw, List<Widget> widgets) {
        UiKey.named(id);
        this.id = id;
        this.redraw = Objects.requireNonNull(redraw);
        this.widgets = List.copyOf(widgets);
        var keys = new HashSet<String>();
        for (var widget : this.widgets) if (!keys.add(widget.id()))
            throw new IllegalArgumentException("Duplicate main menu widget: " + widget.id());
    }

    @Override public MenuScene render() {
        if (closed) throw new IllegalStateException("Main menu sidebar is closed");
        var cards = new ArrayList<UiSurface>();
        FloatingMenuLifecycle lifecycle = FloatingMenuLifecycle.NONE;
        for (var widget : widgets) {
            var scene = widget.panel().render();
            cards.add(UiTemplates.sidebar(widget.id(), scene.content()));
            lifecycle = FloatingMenuLifecycle.combine(lifecycle, scene.lifecycle());
        }
        return MenuScene.screen(id).content(column(id, page, selected -> { if (!closed) { page = selected; redraw.run(); } }, cards)).lifecycle(lifecycle).build();
    }

    static UiNode column(String id, int page, java.util.function.IntConsumer select, List<UiSurface> cards) {
        var column = Ui.pagedList().key("widgets").gap(UiLength.ui(16)).items(cards.stream().map(card -> card.withFacing(UiSurfaceFacing.INHERIT)).toList(), new LinearSlot(0, LinearSlot.Alignment.START)).page(page, select);
        return Ui.surface(column.build()).key(id).style(UiStyle.PLAIN).facing(UiSurfaceFacing.HORIZONTAL).build()
                .withSizing(UiSizing.width(UiLength.ZERO, UiLength.ui(MAX_WIDTH)));
    }

    @Override public void attach(FloatingMenuHandle handle) {
        if (!closed) for (var widget : widgets) widget.panel().attach(handle);
    }

    List<?> revision() {
        return widgets.stream().map(widget -> widget.revision().get()).toList();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        var shutdown = new ShutdownSequence();
        for (var widget : widgets.reversed()) shutdown.attempt(widget.id(), widget.panel()::close);
        shutdown.finish("main menu sidebar " + id);
    }
}
