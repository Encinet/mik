package org.encinet.mik.shared.menu.runtime;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Consumer;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiFrame;

/** Owns feature content and late callbacks while the host keeps its own reading frame and refresh policy. */
final class MenuPopoverPanel implements AutoCloseable {
    private final String screen;
    private final UiKey source;
    private final Consumer<MenuPopoverPanel> redraw;
    private final boolean modal;
    private final FloatingMenuCommand dismissed;
    private final net.kyori.adventure.text.Component closeLabel;
    private volatile MenuPanel content;
    private volatile boolean active = true;

    MenuPopoverPanel(String screen, UiKey source, net.kyori.adventure.text.Component closeLabel, Consumer<MenuPopoverPanel> redraw) {
        this(screen, source, closeLabel, redraw, false, (_,_) -> {});
    }

    MenuPopoverPanel(String screen, UiKey source, net.kyori.adventure.text.Component closeLabel,
                     Consumer<MenuPopoverPanel> redraw, boolean modal, FloatingMenuCommand dismissed) {
        this.screen = screen; this.source = Objects.requireNonNull(source); this.redraw = Objects.requireNonNull(redraw);
        this.modal = modal;
        this.closeLabel = Objects.requireNonNull(closeLabel);
        Objects.requireNonNull(dismissed);
        // Identifies every published snapshot from this scope, including a retained loading frame.
        this.dismissed = (p,h) -> dismissed.execute(p,h);
    }

    void initialize(Function<Runnable, MenuPanel> create) {
        if (content != null || !active) throw new IllegalStateException("Popover content already initialized or closed");
        content = Objects.requireNonNull(create.apply(() -> { if (active && content != null) redraw.accept(this); }));
    }

    /** Deferred collections must validate attachment against the realized window before reading panel content. */
    MenuScene compose(MenuScene host, UiFrame frame) {
        return compose(host, frame.elements().stream().anyMatch(element -> element.node().key().equals(source)));
    }

    private MenuScene compose(MenuScene host, boolean sourcePresent) {
        if (!active) return host;
        boolean ownSnapshot = host.popover() != null && host.popover().dismissed() == dismissed && host.popover().source().equals(source);
        if (!Objects.equals(screen, host.screenId()) || host.popover() != null && !ownSnapshot || !sourcePresent) {
            close();
            return ownSnapshot ? host.toBuilder().popover(null).build() : host;
        }
        MenuScene body = content.render();
        if (body.popover() != null) throw new IllegalArgumentException("Nested popovers require a separate page");
        return host.toBuilder().popover(new MenuPopover(source, body.title(), body.content(), content.headerActions(), closeLabel, dismissed, modal)).build();
    }

    boolean active() { return active; }
    void attach(FloatingMenuHandle handle) { if (active) content.attach(handle); }

    @Override public void close() {
        if (!active) return;
        active = false;
        MenuPanel previous = content;
        content = null;
        if (previous != null) previous.close();
    }
}
