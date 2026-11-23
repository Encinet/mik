package org.encinet.mik.shared.menu.runtime.window;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Consumer;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiFrame;

/** Owns feature content and late callbacks while the host keeps its own reading frame and refresh policy. */
public final class MenuPopoverPanel implements AutoCloseable {
    private final String screen;
    private final UiKey source;
    private final Consumer<MenuPopoverPanel> redraw;
    private final boolean modal;
    private final FloatingMenuCommand dismissed;
    private final net.kyori.adventure.text.Component closeLabel;
    private volatile MenuPanel content;
    private volatile boolean active = true;

    public MenuPopoverPanel(String screen, UiKey source, net.kyori.adventure.text.Component closeLabel, Consumer<MenuPopoverPanel> redraw) {
        this(screen, source, closeLabel, redraw, false, (_,_) -> {});
    }

    public MenuPopoverPanel(String screen, UiKey source, net.kyori.adventure.text.Component closeLabel,
                     Consumer<MenuPopoverPanel> redraw, boolean modal, FloatingMenuCommand dismissed) {
        this.screen = screen; this.source = Objects.requireNonNull(source); this.redraw = Objects.requireNonNull(redraw);
        this.modal = modal;
        this.closeLabel = Objects.requireNonNull(closeLabel);
        Objects.requireNonNull(dismissed);
        // Identifies every published snapshot from this scope, including a retained loading frame.
        this.dismissed = (p,h) -> dismissed.execute(p,h);
    }

    public void initialize(Function<Runnable, MenuPanel> create) {
        if (content != null || !active) throw new IllegalStateException("Popover content already initialized or closed");
        content = Objects.requireNonNull(create.apply(() -> { if (active && content != null) redraw.accept(this); }));
    }

    /** Deferred collections must validate attachment against the realized window before reading panel content. */
    public MenuScene compose(MenuScene host, UiFrame frame) {
        return compose(host, frame, null);
    }

    public MenuScene compose(MenuScene host, UiFrame frame, String scope) {
        var composed = compose(host, frame.elements().stream().anyMatch(element -> element.node().key().equals(source)
                && (scope == null || MenuWorkspaceSession.segment(element.path(), scope))));
        return composed.popover() == null || scope == null || composed.popover().dismissed() != dismissed
                ? composed : composed.toBuilder().popover(composed.popover().within(scope)).build();
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

    public boolean active() { return active; }
    public void attach(FloatingMenuHandle handle) { if (active) content.attach(handle); }

    @Override public void close() {
        if (!active) return;
        active = false;
        MenuPanel previous = content;
        content = null;
        if (previous != null) previous.close();
    }
}
