package org.encinet.mik.feature.entertainment.music.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.util.ShutdownSequence;

/** Local task navigation belongs to one companion; the host keeps its collection and playback state. */
final class MusicTaskWindow implements MenuPanel {
    private record Page(UUID scope, MenuPanel content) { }
    private final Deque<Page> pages = new ArrayDeque<>();
    private final Runnable redraw;
    private final Component backLabel;
    private FloatingMenuHandle handle;
    private boolean active = true;
    private long revision;

    MusicTaskWindow(Runnable redraw, Component backLabel, Function<MusicTaskWindow, MenuPanel> root) {
        this.redraw = redraw;
        this.backLabel = backLabel;
        pages.push(new Page(UUID.randomUUID(), root.apply(this)));
    }

    BooleanSupplier current() {
        long expected = revision;
        return () -> active && expected == revision;
    }

    void push(MenuPanel page) {
        if (!active) { page.close(); return; }
        revision++;
        pages.push(new Page(UUID.randomUUID(), page));
        redraw.run();
        if (active && handle != null) page.attach(handle);
    }

    void back() {
        if (!active || pages.size() <= 1) return;
        revision++;
        pages.pop().content().close();
        redraw.run();
    }

    void input(Player player, MenuInputForm form, String focus) {
        var input = new MusicInputPanel(player, form, focus, MenuFormSession.Actions.SUBMIT_AND_CANCEL,
                redraw, _ -> back());
        push(input);
    }

    @Override public MenuScene render() {
        var page = pages.getFirst();
        var scene = page.content().render();
        return scene.toBuilder().content(MusicWorkspaceComposition.task(page.scope(), scene.content())).build();
    }

    @Override public UiNode headerActions() {
        if (pages.size() <= 1) return null;
        var owned = current();
        return Ui.button(UiIcons.CHEVRON_LEFT, backLabel).key("back-task")
                .primary((_, _) -> { if (owned.getAsBoolean()) back(); }).build();
    }

    @Override public void attach(FloatingMenuHandle handle) {
        this.handle = handle;
        pages.getFirst().content().attach(handle);
    }

    @Override public void close() {
        if (!active) return;
        active = false;
        var shutdown = new ShutdownSequence();
        while (!pages.isEmpty()) {
            var page = pages.pop();
            shutdown.attempt("music task page",page.content()::close);
        }
        shutdown.finish("music task window");
    }
}
